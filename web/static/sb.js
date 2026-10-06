// Browser host for Snake Brawl: canvas drawing, touch input, sound and storage.
// The game (compiled from Java by TeaVM) calls the SB functions below.
(function () {
  'use strict';
  var canvas, ctx, dpr = 1, handler = null;
  var fillStyle = '#000', curFont = '', curSize = -1;
  var colorCache = new Map();
  var gradCache = new Map();
  var audio = null, buffers = [], soundsLoading = false;
  var SOUNDS = ['shoot', 'shotgun', 'bolt', 'throw', 'explode', 'flame', 'hit', 'eat', 'power',
    'super_ready', 'super', 'death', 'kill', 'victory', 'defeat', 'click', 'box'];

  function css(c) {
    var s = colorCache.get(c);
    if (s === undefined) {
      s = 'rgba(' + ((c >> 16) & 255) + ',' + ((c >> 8) & 255) + ',' + (c & 255) + ',' + (((c >>> 24) & 255) / 255) + ')';
      if (colorCache.size > 4000) colorCache.clear();
      colorCache.set(c, s);
    }
    return s;
  }

  function rr(l, t, r, b, rad) {
    rad = Math.max(0, Math.min(rad, (r - l) / 2, (b - t) / 2));
    ctx.beginPath();
    ctx.moveTo(l + rad, t);
    ctx.arcTo(r, t, r, b, rad);
    ctx.arcTo(r, b, l, b, rad);
    ctx.arcTo(l, b, l, t, rad);
    ctx.arcTo(l, t, r, t, rad);
    ctx.closePath();
  }

  function gradient(radial, a, b) {
    var key = (radial ? 'r' : 'v') + a + '_' + b;
    var g = gradCache.get(key);
    if (!g) {
      g = radial ? ctx.createRadialGradient(0, 0, 0, 0, 0, 1) : ctx.createLinearGradient(0, 0, 0, 1);
      g.addColorStop(0, css(a));
      g.addColorStop(1, css(b));
      if (gradCache.size > 800) gradCache.clear();
      gradCache.set(key, g);
    }
    return g;
  }

  // ------------------------------------------------------------------ sound
  function unlockAudio() {
    if (audio) {
      if (audio.state === 'suspended') audio.resume();
      return;
    }
    var AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return;
    audio = new AC();
    // Playing a silent buffer inside the touch handler unlocks audio on iOS
    var src = audio.createBufferSource();
    src.buffer = audio.createBuffer(1, 1, 22050);
    src.connect(audio.destination);
    src.start(0);
    if (!soundsLoading) {
      soundsLoading = true;
      SOUNDS.forEach(function (name, i) {
        fetch('sounds/snd_' + name + '.wav').then(function (r) { return r.arrayBuffer(); }).then(function (data) {
          audio.decodeAudioData(data, function (buf) { buffers[i] = buf; }, function () {});
        }).catch(function () {});
      });
    }
  }

  // ------------------------------------------------------------------ setup
  function resize() {
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    var w = Math.round(window.innerWidth * dpr), h = Math.round(window.innerHeight * dpr);
    if (canvas.width !== w || canvas.height !== h) {
      canvas.width = w;
      canvas.height = h;
      curSize = -1;
      gradCache.clear();
    }
    canvas.style.width = window.innerWidth + 'px';
    canvas.style.height = window.innerHeight + 'px';
  }

  var insetProbe = null;
  function insetPx(i) {
    if (!insetProbe) return 0;
    var cs = getComputedStyle(insetProbe);
    var v = [cs.paddingLeft, cs.paddingTop, cs.paddingRight, cs.paddingBottom][i];
    return (parseFloat(v) || 0) * dpr;
  }

  function pos(e) {
    var r = canvas.getBoundingClientRect();
    return [(e.clientX - r.left) * dpr, (e.clientY - r.top) * dpr];
  }

  window.SB = {
    start: function (h) {
      handler = h;
      canvas = document.getElementById('game');
      ctx = canvas.getContext('2d', { alpha: false });
      insetProbe = document.getElementById('insets');
      resize();
      window.addEventListener('resize', resize);
      window.addEventListener('orientationchange', function () { setTimeout(resize, 200); });
      canvas.addEventListener('pointerdown', function (e) {
        e.preventDefault();
        unlockAudio();
        try { canvas.setPointerCapture(e.pointerId); } catch (err) {}
        var p = pos(e);
        handler(0, e.pointerId, p[0], p[1]);
      });
      canvas.addEventListener('pointermove', function (e) {
        e.preventDefault();
        var p = pos(e);
        handler(1, e.pointerId, p[0], p[1]);
      });
      var up = function (e) {
        e.preventDefault();
        var p = pos(e);
        handler(2, e.pointerId, p[0], p[1]);
      };
      canvas.addEventListener('pointerup', up);
      canvas.addEventListener('pointercancel', up);
      document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' || e.key === 'Backspace') handler(3, 0, 0, 0);
      });
      document.addEventListener('visibilitychange', function () {
        if (document.hidden) handler(4, 0, 0, 0);
      });
      ['touchstart', 'touchmove', 'gesturestart', 'dblclick', 'contextmenu'].forEach(function (n) {
        document.addEventListener(n, function (e) { if (e.target === canvas) e.preventDefault(); }, { passive: false });
      });
      var last = performance.now();
      var loop = function (now) {
        var dt = Math.min(0.1, Math.max(0, (now - last) / 1000));
        last = now;
        ctx.setTransform(1, 0, 0, 1, 0, 0);
        handler(5, 0, dt, 0);
        requestAnimationFrame(loop);
      };
      document.fonts && document.fonts.load('40px Lilita').then(function () { curSize = -1; });
      var loading = document.getElementById('loading');
      if (loading) loading.style.display = 'none';
      requestAnimationFrame(loop);
    },
    width: function () { return canvas.width; },
    height: function () { return canvas.height; },
    inset: insetPx,

    color: function (c) { fillStyle = css(c); },
    fillCircle: function (x, y, r) {
      ctx.fillStyle = fillStyle;
      ctx.beginPath();
      ctx.arc(x, y, r, 0, 6.283185307179586);
      ctx.fill();
    },
    strokeCircle: function (x, y, r, w) {
      ctx.strokeStyle = fillStyle;
      ctx.lineWidth = w;
      ctx.beginPath();
      ctx.arc(x, y, r, 0, 6.283185307179586);
      ctx.stroke();
    },
    fillRect: function (l, t, r, b) {
      ctx.fillStyle = fillStyle;
      ctx.fillRect(l, t, r - l, b - t);
    },
    fillRoundRect: function (l, t, r, b, rad) {
      ctx.fillStyle = fillStyle;
      rr(l, t, r, b, rad);
      ctx.fill();
    },
    strokeRoundRect: function (l, t, r, b, rad, w) {
      ctx.strokeStyle = fillStyle;
      ctx.lineWidth = w;
      rr(l, t, r, b, rad);
      ctx.stroke();
    },
    line: function (x1, y1, x2, y2, w) {
      ctx.strokeStyle = fillStyle;
      ctx.lineWidth = w;
      ctx.lineCap = 'round';
      ctx.beginPath();
      ctx.moveTo(x1, y1);
      ctx.lineTo(x2, y2);
      ctx.stroke();
    },
    arc: function (x, y, r, s, e, w) {
      ctx.strokeStyle = fillStyle;
      ctx.lineWidth = w;
      ctx.lineCap = 'round';
      var a0 = s * Math.PI / 180, a1 = (s + e) * Math.PI / 180;
      ctx.beginPath();
      ctx.arc(x, y, r, a0, a1, e < 0);
      ctx.stroke();
    },
    moveTo: function (x, y) {
      ctx.beginPath();
      ctx.moveTo(x, y);
    },
    lineTo: function (x, y) { ctx.lineTo(x, y); },
    fillPath: function () {
      ctx.closePath();
      ctx.fillStyle = fillStyle;
      ctx.fill();
    },
    text: function (s, x, y, size, align, o, oc) {
      if (size !== curSize) {
        curSize = size;
        curFont = size.toFixed(1) + 'px Lilita, "Arial Rounded MT Bold", sans-serif';
      }
      ctx.font = curFont;
      ctx.textAlign = align === 1 ? 'center' : (align === 2 ? 'right' : 'left');
      ctx.textBaseline = 'alphabetic';
      if (o > 0) {
        ctx.lineJoin = 'round';
        ctx.lineWidth = o * 2;
        ctx.strokeStyle = css(oc);
        ctx.strokeText(s, x, y);
      }
      ctx.fillStyle = fillStyle;
      ctx.fillText(s, x, y);
    },
    measure: function (s, size) {
      ctx.font = size.toFixed(1) + 'px Lilita, "Arial Rounded MT Bold", sans-serif';
      curSize = -1;
      return ctx.measureText(s).width;
    },
    radial: function (x, y, r, a, b) {
      ctx.save();
      ctx.translate(x, y);
      ctx.scale(r, r);
      ctx.fillStyle = gradient(true, a, b);
      ctx.beginPath();
      ctx.arc(0, 0, 1, 0, 6.283185307179586);
      ctx.fill();
      ctx.restore();
    },
    vertical: function (l, t, r, b, a, c) {
      ctx.save();
      ctx.translate(l, t);
      ctx.scale(r - l, b - t);
      ctx.fillStyle = gradient(false, a, c);
      ctx.fillRect(0, 0, 1, 1);
      ctx.restore();
    },
    clip: function (l, t, r, b) {
      ctx.beginPath();
      ctx.rect(l, t, r - l, b - t);
      ctx.clip();
    },
    save: function () { ctx.save(); },
    restore: function () { ctx.restore(); },
    translate: function (x, y) { ctx.translate(x, y); },
    scale: function (s) { ctx.scale(s, s); },
    rotate: function (d) { ctx.rotate(d * Math.PI / 180); },

    play: function (id, v) {
      if (!audio || !buffers[id]) return;
      var src = audio.createBufferSource();
      src.buffer = buffers[id];
      var g = audio.createGain();
      g.gain.value = Math.max(0, Math.min(1, v));
      src.connect(g);
      g.connect(audio.destination);
      src.start(0);
    },
    vibrate: function (ms) { if (navigator.vibrate) navigator.vibrate(ms); },
    load: function (k) {
      try { return localStorage.getItem('snakebrawl_' + k); } catch (e) { return null; }
    },
    store: function (k, v) {
      try { localStorage.setItem('snakebrawl_' + k, v); } catch (e) {}
    },
    prompt: function (title, initial) {
      var r = window.prompt(title, initial);
      return r === null ? null : r;
    }
  };
})();
