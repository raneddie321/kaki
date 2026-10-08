package com.snakebrawl.myapp.game;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Internet room over WebRTC. The host gets a 5-character room code; the friend types it. The
 * actual WebRTC work is done by the platform's {@link Platform.OnlineLink} (the browser page, or
 * a hidden WebView in the Android app), which carries each message as a string of byte values.
 */
final class OnlineSession extends NetSession {
    private final Platform.OnlineLink link;
    private boolean closed;
    private long lastRecv = System.currentTimeMillis(), lastPing;

    private OnlineSession(Platform.OnlineLink link, boolean host) {
        super(host);
        this.link = link;
    }

    /** True when this platform can play online. */
    static boolean available(Platform p) {
        Platform.OnlineLink l = p.online();
        return l != null && l.available();
    }

    static OnlineSession host(Platform p) {
        Platform.OnlineLink l = p.online();
        l.host();
        return new OnlineSession(l, true);
    }

    static OnlineSession search(Platform p) {
        Platform.OnlineLink l = p.online();
        l.search();
        return new OnlineSession(l, false);
    }

    @Override
    List<Room> rooms() {
        return new ArrayList<Room>();
    }

    @Override
    void join(String code, int port) {
        if (closed) return;
        link.join(code);
        sync();
    }

    /** The host learns about its (only) guest like a Wi-Fi host does: as a JOINED message in slot 1. */
    private boolean joinedSent;

    private void sync() {
        if (closed) return;
        int s = link.state();
        if (s == ST_CONNECTED && state != ST_CONNECTED) lastRecv = System.currentTimeMillis();
        state = s;
        String r = link.reason();
        if (r != null && r.length() > 0) closeReason = r;
        else if (s != ST_CLOSED) closeReason = null;
    }

    @Override
    Msg poll() {
        if (closed) return null;
        sync();
        if (state == ST_CONNECTED) {
            long now = System.currentTimeMillis();
            if (now - lastPing > 1000) {
                lastPing = now;
                sendBytes(NetCodec.single(M_PING));
            }
            if (now - lastRecv > 8000) {
                fail("Lost connection to your friend");
                return null;
            }
        }
        if (host && state == ST_CONNECTED && !joinedSent) {
            joinedSent = true;
            Msg j = new Msg();
            j.type = M_JOINED;
            j.from = 1;
            return j;
        }
        while (true) {
            String s = link.poll();
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
            m.from = host ? 1 : 0;
            return m;
        }
    }

    private void fail(String reason) {
        closeReason = reason;
        state = ST_CLOSED;
        closed = true;
        link.close();
    }

    @Override
    void sendBytes(byte[] b) {
        if (state != ST_CONNECTED || closed) return;
        char[] c = new char[b.length];
        for (int i = 0; i < b.length; i++) c[i] = (char) (b[i] & 0xff);
        link.send(new String(c));
    }

    @Override
    void sendTo(int slot, byte[] b) {
        sendBytes(b);
    }

    @Override
    void kick(int slot) {
        close();
    }

    @Override
    void lockRoom() {
        // Online rooms hold exactly two players
    }

    @Override
    int maxPlayers() {
        return 2;
    }

    @Override
    void close() {
        if (closed) return;
        sendBytes(NetCodec.single(M_BYE));
        closed = true;
        state = ST_CLOSED;
        link.close();
    }

    @Override
    void clearError() {
        link.clearReason();
        closeReason = null;
    }

    @Override
    String roomLabel() {
        String c = link.code();
        return c == null || c.length() == 0 ? null : c;
    }

    @Override
    boolean lan() {
        return false;
    }

    @Override
    boolean exact() {
        return link.exactFloats();
    }

    @Override
    int platform() {
        return link.isBrowser() ? PLAT_BROWSER : PLAT_APP;
    }
}
