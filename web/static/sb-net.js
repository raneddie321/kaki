// Online play between browsers over WebRTC (PeerJS). The host gets a short room code; the friend
// types it to connect directly to the host's browser. PeerJS's free public server only introduces
// the two browsers to each other - game traffic goes straight between them.
// The game's NetSession (browser version) polls this object every frame.
(function () {
  'use strict';
  var PREFIX = 'snakebrawl-v1-';
  var ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  var WAITING = 0, CONNECTING = 1, CONNECTED = 2, CLOSED = 3;

  var peer = null, conn = null, state = CLOSED, reason = null, code = '', inbox = [], closing = false;

  function options() {
    // ?peer=host:port points at a self-hosted PeerJS server (used by the tests)
    var m = /[?&]peer=([^&:]+)(?::(\d+))?/.exec(location.search);
    if (!m) return { debug: 0 };
    return { host: m[1], port: m[2] ? +m[2] : 443, path: '/', secure: location.protocol === 'https:', debug: 0 };
  }

  function newCode() {
    var s = '';
    var buf = new Uint32Array(5);
    (window.crypto || window.msCrypto).getRandomValues(buf);
    for (var i = 0; i < 5; i++) s += ALPHABET[buf[i] % ALPHABET.length];
    return s;
  }

  function fail(why) {
    if (state === CLOSED) return;
    reason = why;
    state = CLOSED;
    cleanup();
  }

  function cleanup() {
    try { if (conn) conn.close(); } catch (e) {}
    try { if (peer) peer.destroy(); } catch (e) {}
    conn = null;
    peer = null;
  }

  function attach(c) {
    conn = c;
    c.on('open', function () {
      if (conn !== c) return;
      state = CONNECTED;
      reason = null;
      // The lobby server is no longer needed once the browsers talk directly
      try { peer.disconnect(); } catch (e) {}
    });
    c.on('data', function (d) {
      if (typeof d === 'string') inbox.push(d);
    });
    c.on('close', function () {
      if (conn === c && !closing) fail('Your friend left the game');
    });
    c.on('error', function () {
      if (conn === c && !closing) fail('Lost connection to your friend');
    });
  }

  function peerError(err) {
    if (closing) return;
    var t = err && err.type;
    if (t === 'unavailable-id' && state === WAITING && isHost) {
      // Someone else has this code: pick another one
      startHost();
      return;
    }
    if (t === 'peer-unavailable') {
      state = WAITING;
      reason = 'No room with code ' + code + '. Check the code on your friend\'s screen.';
      conn = null;
      return;
    }
    if (state === CONNECTED) return; // lobby server hiccups don't matter once connected
    if (t === 'network' || t === 'server-error' || t === 'socket-error' || t === 'socket-closed') {
      fail('Could not reach the online lobby. Check your internet connection.');
    } else if (t === 'browser-incompatible') {
      fail('This browser does not support online play. Try Safari or Chrome.');
    } else {
      fail('Online play error (' + (t || 'unknown') + ')');
    }
  }

  var isHost = false;

  function startHost() {
    cleanup();
    code = newCode();
    state = WAITING;
    peer = new Peer(PREFIX + code, options());
    peer.on('connection', function (c) {
      if (conn) { // already have a friend: refuse extra guests
        c.on('open', function () { c.close(); });
        return;
      }
      attach(c);
    });
    peer.on('error', peerError);
  }

  window.SBNet = {
    available: function () {
      return typeof Peer !== 'undefined' && typeof RTCPeerConnection !== 'undefined';
    },
    host: function () {
      closing = false;
      isHost = true;
      reason = null;
      inbox = [];
      startHost();
    },
    search: function () {
      closing = false;
      isHost = false;
      reason = null;
      inbox = [];
      cleanup();
      code = '';
      state = WAITING;
      peer = new Peer(options());
      peer.on('error', peerError);
    },
    join: function (c) {
      if (!peer || state !== WAITING) return;
      code = String(c).toUpperCase();
      reason = null;
      state = CONNECTING;
      var go = function () {
        if (state !== CONNECTING) return;
        attach(peer.connect(PREFIX + code, { reliable: true, serialization: 'raw' }));
        // No answer in time: the room probably does not exist
        setTimeout(function () {
          if (state === CONNECTING) {
            state = WAITING;
            conn = null;
            reason = 'Could not join room ' + code + '. Check the code and try again.';
          }
        }, 12000);
      };
      if (peer.open) go(); else peer.once('open', go);
    },
    state: function () { return state; },
    reason: function () { return reason; },
    clearReason: function () { reason = null; },
    code: function () { return code; },
    send: function (s) {
      if (state === CONNECTED && conn && conn.open) {
        try { conn.send(s); } catch (e) { fail('Lost connection to your friend'); }
      }
    },
    poll: function () {
      return inbox.length ? inbox.shift() : null;
    },
    close: function () {
      closing = true;
      state = CLOSED;
      inbox = [];
      // Give a final message (BYE) a moment to leave before tearing down
      var p = peer, c = conn;
      peer = null;
      conn = null;
      setTimeout(function () {
        try { if (c) c.close(); } catch (e) {}
        try { if (p) p.destroy(); } catch (e) {}
      }, 300);
    }
  };
})();
