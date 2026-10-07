// Offline cache: after the first visit the game also works without internet.
var CACHE = 'snakebrawl-v3.1';
self.addEventListener('install', function (e) {
  e.waitUntil(caches.open(CACHE).then(function (c) {
    return c.addAll(['./', 'index.html', 'sb.js', 'sb-net.js', 'vendor/peerjs.min.js', 'classes.js', 'classes.wasm', 'wasm-runtime.js', 'LilitaOne-Regular.ttf', 'manifest.json', 'icon-192.png']);
  }));
  self.skipWaiting();
});
self.addEventListener('activate', function (e) {
  e.waitUntil(caches.keys().then(function (keys) {
    return Promise.all(keys.filter(function (k) { return k !== CACHE; }).map(function (k) { return caches.delete(k); }));
  }));
});
self.addEventListener('fetch', function (e) {
  e.respondWith(caches.match(e.request).then(function (r) {
    return r || fetch(e.request).then(function (resp) {
      var copy = resp.clone();
      caches.open(CACHE).then(function (c) { c.put(e.request, copy); });
      return resp;
    });
  }));
});
