package com.snakebrawl.myapp.game;

import java.util.ArrayList;
import java.util.List;

/**
 * Browser build stand-in for the Wi-Fi connection: browsers cannot open raw sockets, so Wi-Fi
 * play is hidden and every method here does nothing. The API mirrors the real NetSession.
 */
final class NetSession {
    static final boolean AVAILABLE = false;
    static final int TCP_PORT = 47321;
    static final int UDP_PORT = 47322;
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

    final boolean host;
    volatile int state = ST_CLOSED;
    volatile String closeReason = "Wi-Fi play is only available in the Android app";

    private NetSession(boolean host) {
        this.host = host;
    }

    static NetSession host(String name) {
        return new NetSession(true);
    }

    static NetSession search(String name) {
        return new NetSession(false);
    }

    List<Room> rooms() {
        return new ArrayList<Room>();
    }

    void join(String ip, int port) {}

    Msg poll() {
        return null;
    }

    void sendHello(String name, int brawler, int level, int trophies, int[] palette) {}

    void sendStart(long seed, int mode) {}

    void sendMode(int mode) {}

    void sendInput(NetInput in) {}

    void sendHash(int tick, int hash) {}

    void close() {}

    static List<String> localAddresses() {
        return new ArrayList<String>();
    }
}
