package com.snakebrawl.myapp.game;

import java.util.List;

/**
 * A connection to friends' games on the same Wi-Fi ({@link LanSession}: plain sockets, up to
 * {@link #MAX_PLAYERS} phones). There is no internet play. The game thread only calls the methods
 * below; none of them block.
 *
 * Players are numbered by slot: the host is slot 0 and guests get 1, 2, ... The host relays every
 * guest's messages to the other guests, so guests only ever talk to the host.
 */
abstract class NetSession {
    /** Version 3: up to three players per room (slots, rosters and the map in START). */
    static final int PROTOCOL = 3;
    static final int MAX_PLAYERS = 3;

    static final int ST_WAITING = 0, ST_CONNECTING = 1, ST_CONNECTED = 2, ST_CLOSED = 3;

    static final int M_HELLO = 1, M_START = 2, M_MODE = 3, M_INPUT = 4, M_HASH = 5, M_BYE = 6, M_PING = 7,
            M_ROSTER = 8, M_DROP = 9;
    /** Made up by the host's session (never sent): a guest connected or left. {@link Msg#from} is its slot. */
    static final int M_JOINED = 100, M_LEFT = 101;

    /** Where a player is playing, shown in the lobby. */
    static final int PLAT_APP = 0, PLAT_BROWSER = 1;

    /** A message received from a friend. */
    static final class Msg {
        int type;
        /** Slot of the connection it arrived on (the host sees which guest; guests always get 0). */
        int from;
        /** Player slot an INPUT or DROP is about. */
        int slot;
        String name;
        int brawler, level, trophies, mode, hash, tick, protocol, platform;
        /** True when the sender simulates with real 32-bit floats (app and WebAssembly browsers). */
        boolean exact;
        int[] palette;
        long seed;
        /** START: number of players, map and (custom maps) its layout. */
        int players, mapId;
        byte[] mapData;
        /** ROSTER: every player by slot (null for an empty slot) and the receiver's own slot. */
        Msg[] roster;
        int you;
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

    /** Rooms found nearby on the Wi-Fi. */
    abstract List<Room> rooms();

    /** Joins a room by its Wi-Fi address. */
    abstract void join(String address, int port);

    /** Next message, or null. Also handles keep-alive and timeouts. */
    abstract Msg poll();

    /** Sends to everyone (host) or to the host (guest). */
    abstract void sendBytes(byte[] b);

    /** Host: sends to one guest. */
    abstract void sendTo(int slot, byte[] b);

    /** Host: disconnects one guest. */
    abstract void kick(int slot);

    /** Host: stops letting new guests in (the match is starting). */
    abstract void lockRoom();

    /** Most players a room of this kind can hold, host included. */
    abstract int maxPlayers();

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

    byte[] hello(String name, int brawler, int level, int trophies, int[] palette) {
        return NetCodec.hello(name, brawler, level, trophies, palette, exact(), platform());
    }

    void sendHello(String name, int brawler, int level, int trophies, int[] palette) {
        sendBytes(hello(name, brawler, level, trophies, palette));
    }

    void sendInput(NetInput in, int slot) {
        sendBytes(NetCodec.input(in, slot));
    }

    void sendHash(int tick, int hash) {
        sendBytes(NetCodec.hash(tick, hash));
    }
}
