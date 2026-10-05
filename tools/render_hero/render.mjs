// Renders a .glb character into the game's 16 directional hero sprites.
//
//   node render.mjs path/to/model.glb [outDir] [--yaw-offset=DEG] [--size=256]
//
// Frame i shows the character facing i * 22.5 degrees clockwise from screen-right, seen from a
// Brawl Stars style tilted top-down camera. The ground point under the model lands at 62% of
// the image height, which is where HeroArt anchors the hero's position.
// Use --yaw-offset if the model's "forward" is not +Z (try 90, 180 or 270).
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';

const here = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const flags = Object.fromEntries(args.filter(a => a.startsWith('--')).map(a => a.slice(2).split('=')));
const positional = args.filter(a => !a.startsWith('--'));
const model = positional[0] === 'TEST' ? 'TEST' : path.resolve(positional[0] ?? '');
const outDir = path.resolve(positional[1] ?? path.join(here, '../../app/src/main/assets/hero'));
const size = Number(flags.size ?? 256);
const yawOffset = Number(flags['yaw-offset'] ?? 0);
const frames = 16;

const page = `<!doctype html><html><body style="margin:0;background:transparent">
<script type="importmap">{"imports":{"three":"/node_modules/three/build/three.module.js",
"three/addons/":"/node_modules/three/examples/jsm/"}}</script>
<script type="module">
import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
const SIZE = ${size}, FRAMES = ${frames}, YAW_OFFSET = ${yawOffset};
const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, preserveDrawingBuffer: true });
renderer.setPixelRatio(1);
renderer.setSize(SIZE, SIZE);
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.setClearColor(0x000000, 0);
document.body.appendChild(renderer.domElement);
const scene = new THREE.Scene();
scene.add(new THREE.HemisphereLight(0xffffff, 0x445566, 2.2));
const sun = new THREE.DirectionalLight(0xffffff, 2.0);
sun.position.set(-2, 5, 3);
scene.add(sun);

function testModel() {
  // Simple figure with a yellow nose on +Z so orientation can be checked
  const g = new THREE.Group();
  const body = new THREE.Mesh(new THREE.CapsuleGeometry(0.35, 0.8, 8, 16), new THREE.MeshStandardMaterial({ color: 0x3fa0ff }));
  body.position.y = 0.75;
  const nose = new THREE.Mesh(new THREE.ConeGeometry(0.15, 0.4, 12), new THREE.MeshStandardMaterial({ color: 0xffd23f }));
  nose.rotation.x = Math.PI / 2;
  nose.position.set(0, 1.2, 0.45);
  g.add(body, nose);
  return g;
}

async function load() {
  if (${JSON.stringify(model)} === 'TEST') return testModel();
  const gltf = await new GLTFLoader().loadAsync('/model.glb');
  return gltf.scene;
}

window.renderAll = async () => {
  const inner = await load();
  // Normalise: centre on the ground, height 1
  const box = new THREE.Box3().setFromObject(inner);
  const sz = box.getSize(new THREE.Vector3());
  const scale = 1 / Math.max(sz.y, 1e-6);
  inner.scale.setScalar(scale);
  const box2 = new THREE.Box3().setFromObject(inner);
  const c = box2.getCenter(new THREE.Vector3());
  inner.position.sub(new THREE.Vector3(c.x, box2.min.y, c.z));
  const pivot = new THREE.Group();
  pivot.add(inner);
  scene.add(pivot);

  const cam = new THREE.OrthographicCamera(-1, 1, 1, -1, 0.01, 100);
  const tilt = THREE.MathUtils.degToRad(50);
  cam.position.set(0, Math.sin(tilt) * 10, Math.cos(tilt) * 10);
  cam.lookAt(0, 0, 0);
  cam.updateMatrixWorld();
  // Size the view to fit any rotation, then shift so the ground point sits at 62% height
  const radius = Math.max(sz.x, sz.z) * scale * 0.75 + 0.15;
  const half = Math.max(0.85, radius + 0.2) * 1.0;
  cam.left = -half; cam.right = half; cam.top = half; cam.bottom = -half;
  cam.updateProjectionMatrix();
  const ground = new THREE.Vector3(0, 0, 0).project(cam);
  const wantY = 1 - 2 * 0.62;
  const shift = (ground.y - wantY) * half;
  cam.top += shift; cam.bottom += shift;
  cam.updateProjectionMatrix();

  const out = [];
  for (let i = 0; i < FRAMES; i++) {
    const a = i * 2 * Math.PI / FRAMES;            // screen angle, clockwise from right
    pivot.rotation.y = Math.PI / 2 - a + THREE.MathUtils.degToRad(YAW_OFFSET);
    renderer.render(scene, cam);
    out.push(renderer.domElement.toDataURL('image/png'));
  }
  return out;
};
window.ready = true;
</script></body></html>`;

const server = http.createServer((req, res) => {
  const url = decodeURIComponent(req.url.split('?')[0]);
  if (url === '/') {
    res.writeHead(200, { 'content-type': 'text/html' });
    return res.end(page);
  }
  const file = url === '/model.glb' ? model : path.join(here, url);
  fs.readFile(file, (err, data) => {
    if (err) {
      res.writeHead(404);
      return res.end();
    }
    const type = file.endsWith('.js') ? 'text/javascript' : 'application/octet-stream';
    res.writeHead(200, { 'content-type': type });
    res.end(data);
  });
});
await new Promise(r => server.listen(0, '127.0.0.1', r));
const port = server.address().port;

const browser = await chromium.launch({
  executablePath: process.env.CHROMIUM ?? '/opt/pw-browsers/chromium',
  args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'],
});
const tab = await browser.newPage();
tab.on('pageerror', e => console.error('page error:', e.message));
await tab.goto(`http://127.0.0.1:${port}/`);
await tab.waitForFunction('window.ready === true');
const images = await tab.evaluate(() => window.renderAll());
fs.mkdirSync(outDir, { recursive: true });
images.forEach((d, i) => {
  const name = `hero_${String(i).padStart(2, '0')}.png`;
  fs.writeFileSync(path.join(outDir, name), Buffer.from(d.split(',')[1], 'base64'));
});
console.log(`wrote ${images.length} frames to ${outDir}`);
await browser.close();
server.close();
