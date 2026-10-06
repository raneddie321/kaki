package com.snakebrawl.myapp.game;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.teavm.jso.JSBody;

/**
 * Browser version of the multiplayer session: online play over WebRTC through the page's
 * sb-net.js (PeerJS). The host gets a room code instead of a Wi-Fi address; the friend types it.
 * Same API and message format (NetCodec) as the Android Wi-Fi session.
 */
final class NetSession {
    static final boolean AVAILABLE = available();
    /** False: rooms are joined with a code over the internet, not found on the local Wi-Fi. */
    static final boolean LAN = false;
    static final int TCP_PORT = 0;
    static final int UDP_PORT = 0;
    static final int PROTOCOL = 1;

    static final int ST_WAITING = 0, ST_CONNECTING = 1, ST_CONNECTED = 2, ST_CLOSED = 3;
    static final int M_HELLO = 1, M_START = 2, M_MODE = 3, M_INPUT = 4, M_HASH = 5, M_BYE = 6, M_PING = 7;

    static final class Msg {
        int type;
        String name;
        int brawler, level, trophies, mode, hash, tick, protocol;
        int[] palette;
        long seed;
        final NetInput input = new NetInput();
    }

    static final class Room {
        String name, ip;
        int port;
        long seen;
    }

    @JSBody(script = "return !!(window.SBNet && SBNet.available());")
    private static native boolean available();

    @JSBody(script = "SBNet.host();")
    private static native void jsHost();

    @JSBody(script = "SBNet.search();")
    private static native void jsSearch();

    @JSBody(params = "c", script = "SBNet.join(c);")
    private static native void jsJoin(String c);

    @JSBody(script = "return SBNet.state();")
    private static native int jsState();

    @JSBody(script = "return SBNet.reason();")
    private static native String jsReason();

    @JSBody(script = "SBNet.clearReason();")
    private static native void jsClearReason();

    @JSBody(script = "return SBNet.code();")
    private static native String jsCode();

    @JSBody(params = "s", script = "SBNet.send(s);")
    private static native void jsSend(String s);

    @JSBody(script = "return SBNet.poll();")
    private static native String jsPoll();

    @JSBody(script = "SBNet.close();")
    private static native void jsClose();

    final boolean host;
    int state;
    String closeReason;
    private boolean closed;
    private long lastRecv = System.currentTimeMillis(), lastPing;

    private NetSession(boolean host) {
        this.host = host;
        state = ST_WAITING;
    }

    /** Opens a room; its code is shown by {@link #localAddresses()}. */
    static NetSession host(String name) {
        jsHost();
        return new NetSession(true);
    }

    /** Gets ready to join a room by code. */
    static NetSession search(String name) {
        jsSearch();
        return new NetSession(false);
    }

    List<Room> rooms() {
        return new ArrayList<Room>();
    }

    /** Joins the room with this code (the port is unused online). */
    void join(String roomCode, int port) {
        if (closed) return;
        jsJoin(roomCode);
        sync();
    }

    private void sync() {
        if (closed) return;
        int s = jsState();
        if (s == ST_CONNECTED && state != ST_CONNECTED) lastRecv = System.currentTimeMillis();
        state = s;
        String r = jsReason();
        if (r != null) closeReason = r;
        else if (s != ST_CLOSED) closeReason = null;
    }

    Msg poll() {
        if (closed) return null;
        sync();
        if (state == ST_CONNECTED) {
            long now = System.currentTimeMillis();
            if (now - lastPing > 1000) {
                lastPing = now;
                send(NetCodec.single(M_PING));
            }
            if (now - lastRecv > 8000) {
                fail("Lost connection to your friend");
                return null;
            }
        }
        while (true) {
            String s = jsPoll();
            if (s == null) return null;
            lastRecv = System.currentTimeMillis();
            byte[] b = new byte[s.length()];
            for (int i = 0; i < b.length; i++) b[i] = (byte) s.charAt(i);
            Msg m;
            try {
                m = NetCodec.decode(b);
            } catch (IOException e) {
                continue;
            }
            if (m.type == M_PING) continue;
            if (m.type == M_BYE) {
                fail("Your friend left the game");
                return null;
            }
            return m;
        }
    }

    private void fail(String reason) {
        closeReason = reason;
        state = ST_CLOSED;
        closed = true;
        jsClose();
    }

    private void send(byte[] b) {
        if (state != ST_CONNECTED || closed) return;
        char[] c = new char[b.length];
        for (int i = 0; i < b.length; i++) c[i] = (char) (b[i] & 0xff);
        jsSend(new String(c));
    }

    void sendHello(String name, int brawler, int level, int trophies, int[] palette) {
        send(NetCodec.hello(name, brawler, level, trophies, palette));
    }

    void sendStart(long seed, int mode) {
        send(NetCodec.start(seed, mode));
    }

    void sendMode(int mode) {
        send(NetCodec.mode(mode));
    }

    void sendInput(NetInput in) {
        send(NetCodec.input(in));
    }

    void sendHash(int tick, int hash) {
        send(NetCodec.hash(tick, hash));
    }

    void close() {
        if (closed) return;
        send(NetCodec.single(M_BYE));
        closed = true;
        state = ST_CLOSED;
        jsClose();
    }

    /** The current room code while hosting (shown to the player instead of a Wi-Fi address). */
    static List<String> localAddresses() {
        List<String> out = new ArrayList<String>();
        String c = jsCode();
        if (c != null && c.length() > 0) out.add(c);
        return out;
    }

    /** Clears a shown join error so the next one is reported. */
    void clearError() {
        jsClearReason();
        closeReason = null;
    }
}
