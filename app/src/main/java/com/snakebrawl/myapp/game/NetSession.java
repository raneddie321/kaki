package com.snakebrawl.myapp.game;

import java.util.List;

/**
 * A connection to one friend's game. Two kinds exist: {@link LanSession} (Android Wi-Fi, plain
 * sockets) and {@link OnlineSession} (internet rooms over WebRTC, in the app and the browser, so
 * app and browser players can play together). The game thread only calls the methods below; none
 * of them block.
 */
abstract class NetSession {
    /** Version 2: HELLO carries the float mode and platform for cross-play checks. */
    static final int PROTOCOL = 2;

    static final int ST_WAITING = 0, ST_CONNECTING = 1, ST_CONNECTED = 2, ST_CLOSED = 3;

    static final int M_HELLO = 1, M_START = 2, M_MODE = 3, M_INPUT = 4, M_HASH = 5, M_BYE = 6, M_PING = 7;

    /** Where a player is playing, shown in the lobby. */
    static final int PLAT_APP = 0, PLAT_BROWSER = 1;

    /** A message received from the friend. */
    static final class Msg {
        int type;
        String name;
        int brawler, level, trophies, mode, hash, tick, protocol, platform;
        /** True when the sender simulates with real 32-bit floats (app and WebAssembly browsers). */
        boolean exact;
        int[] palette;
        long seed;
        final NetInput input = new NetInput();
    }

    /** A room found on the local network. */
    static final class Room {
        String name, ip;
        int port;
        long seen;
    }

    final boolean host;
    volatile int state = ST_WAITING;
    /** Why the connection failed or ended, for the player. */
    volatile String closeReason;

    NetSession(boolean host) {
        this.host = host;
    }

    /** Rooms found nearby (Wi-Fi only; online rooms are joined by code). */
    abstract List<Room> rooms();

    /** Joins a room: a Wi-Fi address, or an online room code. */
    abstract void join(String address, int port);

    /** Next message from the friend, or null. Also handles keep-alive and timeouts. */
    abstract Msg poll();

    abstract void sendBytes(byte[] b);

    abstract void close();

    abstract void clearError();

    /** The address or code a friend types to join this room, or null while it is not ready. */
    abstract String roomLabel();

    /** True for a local Wi-Fi session. */
    abstract boolean lan();

    /** True when this side simulates with real 32-bit floats. */
    abstract boolean exact();

    /** Platform of this side (app or browser). */
    int platform() {
        return PLAT_APP;
    }

    void sendHello(String name, int brawler, int level, int trophies, int[] palette) {
        sendBytes(NetCodec.hello(name, brawler, level, trophies, palette, exact(), platform()));
    }

    void sendStart(long seed, int mode) {
        sendBytes(NetCodec.start(seed, mode));
    }

    void sendMode(int mode) {
        sendBytes(NetCodec.mode(mode));
    }

    void sendInput(NetInput in) {
        sendBytes(NetCodec.input(in));
    }

    void sendHash(int tick, int hash) {
        sendBytes(NetCodec.hash(tick, hash));
    }
}
