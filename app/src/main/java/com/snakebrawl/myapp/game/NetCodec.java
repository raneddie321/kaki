package com.snakebrawl.myapp.game;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Wire format of multiplayer messages, shared by the Wi-Fi (Android) and online (browser) sessions. */
final class NetCodec {
    private NetCodec() {}

    static byte[] hello(String name, int brawler, int level, int trophies, int[] palette, boolean exact, int platform) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(bo);
        try {
            o.writeByte(NetSession.M_HELLO);
            o.writeInt(NetSession.PROTOCOL);
            writePlayer(o, name, brawler, level, trophies, palette, exact, platform);
        } catch (IOException ignored) {
            // cannot happen with a byte array
        }
        return bo.toByteArray();
    }

    private static void writePlayer(DataOutputStream o, String name, int brawler, int level, int trophies, int[] palette,
            boolean exact, int platform) throws IOException {
        o.writeUTF(name);
        o.writeInt(brawler);
        o.writeInt(level);
        o.writeInt(trophies);
        int n = palette == null ? 0 : Math.min(palette.length, 16);
        o.writeByte(n);
        for (int i = 0; i < n; i++) o.writeInt(palette[i]);
        o.writeByte(exact ? 1 : 0);
        o.writeByte(platform);
    }

    private static void readPlayer(DataInputStream in, NetSession.Msg m) throws IOException {
        m.name = in.readUTF();
        m.brawler = in.readInt();
        m.level = in.readInt();
        m.trophies = in.readInt();
        int n = in.readUnsignedByte();
        m.palette = new int[n];
        for (int i = 0; i < n; i++) m.palette[i] = in.readInt();
        m.exact = in.readUnsignedByte() != 0;
        m.platform = in.readUnsignedByte();
    }

    /** Host to guests: who is in the room, by slot, and which slot is the receiver's. */
    static byte[] roster(NetSession.Msg[] players, int you) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(bo);
        try {
            o.writeByte(NetSession.M_ROSTER);
            o.writeByte(players.length);
            o.writeByte(you);
            for (NetSession.Msg p : players) {
                o.writeByte(p == null ? 0 : 1);
                if (p != null) writePlayer(o, p.name, p.brawler, p.level, p.trophies, p.palette, p.exact, p.platform);
            }
        } catch (IOException ignored) {
            // cannot happen with a byte array
        }
        return bo.toByteArray();
    }

    static byte[] start(long seed, int mode, int players, int mapId, byte[] mapData) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(bo);
        try {
            o.writeByte(NetSession.M_START);
            o.writeLong(seed);
            o.writeInt(mode);
            o.writeByte(players);
            o.writeInt(mapId);
            int n = mapData == null ? 0 : mapData.length;
            o.writeShort(n);
            if (n > 0) o.write(mapData);
        } catch (IOException ignored) {
            // cannot happen with a byte array
        }
        return bo.toByteArray();
    }

    /** Host to guests: the lobby's mode and map (name only, the layout comes with START). */
    static byte[] mode(int mode, int mapId, String mapName) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(bo);
        try {
            o.writeByte(NetSession.M_MODE);
            o.writeInt(mode);
            o.writeInt(mapId);
            o.writeUTF(mapName == null ? "" : mapName);
        } catch (IOException ignored) {
            // cannot happen with a byte array
        }
        return bo.toByteArray();
    }

    static byte[] input(NetInput in, int slot) {
        byte[] b = new byte[19];
        b[0] = (byte) NetSession.M_INPUT;
        putInt(b, 1, in.tick);
        b[5] = (byte) ((in.steer ? 1 : 0) | (in.boost ? 2 : 0) | (in.attack << 2));
        putInt(b, 6, Float.floatToIntBits(in.ang));
        putInt(b, 10, Float.floatToIntBits(in.atkAng));
        putInt(b, 14, Float.floatToIntBits(in.atkDist));
        b[18] = (byte) slot;
        return b;
    }

    /** From tick {@code tick} on, the player in {@code slot} is played by a bot on every phone. */
    static byte[] drop(int slot, int tick) {
        byte[] b = new byte[6];
        b[0] = (byte) NetSession.M_DROP;
        b[1] = (byte) slot;
        putInt(b, 2, tick);
        return b;
    }

    /**
     * Rounds an input's angles exactly as sending it does, so the local phone applies the same
     * values its friends receive. (In the browser floats are really doubles until rounded.)
     */
    static void normalize(NetInput in) {
        in.ang = Float.intBitsToFloat(Float.floatToIntBits(in.ang));
        in.atkAng = Float.intBitsToFloat(Float.floatToIntBits(in.atkAng));
        in.atkDist = Float.intBitsToFloat(Float.floatToIntBits(in.atkDist));
    }

    static byte[] hash(int tick, int hash) {
        byte[] b = new byte[9];
        b[0] = (byte) NetSession.M_HASH;
        putInt(b, 1, tick);
        putInt(b, 5, hash);
        return b;
    }

    static byte[] single(int type) {
        return new byte[]{(byte) type};
    }

    private static void putInt(byte[] b, int at, int v) {
        b[at] = (byte) (v >>> 24);
        b[at + 1] = (byte) (v >>> 16);
        b[at + 2] = (byte) (v >>> 8);
        b[at + 3] = (byte) v;
    }

    /** Reads one message from a stream. */
    static NetSession.Msg read(DataInputStream in) throws IOException {
        NetSession.Msg m = new NetSession.Msg();
        m.type = in.readUnsignedByte();
        switch (m.type) {
            case NetSession.M_HELLO:
                m.protocol = in.readInt();
                // Older versions wrote a different layout: stop reading, the version check turns them away
                if (m.protocol == NetSession.PROTOCOL) readPlayer(in, m);
                else m.name = "";
                break;
            case NetSession.M_ROSTER: {
                int n = Math.min(in.readUnsignedByte(), 8);
                m.you = in.readUnsignedByte();
                m.roster = new NetSession.Msg[n];
                for (int i = 0; i < n; i++) {
                    if (in.readUnsignedByte() == 0) continue;
                    NetSession.Msg p = new NetSession.Msg();
                    readPlayer(in, p);
                    m.roster[i] = p;
                }
                break;
            }
            case NetSession.M_START: {
                m.seed = in.readLong();
                m.mode = in.readInt();
                m.players = in.readUnsignedByte();
                m.mapId = in.readInt();
                int n = in.readUnsignedShort();
                m.mapData = new byte[n];
                in.readFully(m.mapData);
                break;
            }
            case NetSession.M_MODE:
                m.mode = in.readInt();
                m.mapId = in.readInt();
                m.name = in.readUTF();
                break;
            case NetSession.M_INPUT: {
                NetInput ni = m.input;
                ni.tick = in.readInt();
                int f = in.readUnsignedByte();
                ni.steer = (f & 1) != 0;
                ni.boost = (f & 2) != 0;
                ni.attack = f >> 2;
                ni.ang = in.readFloat();
                ni.atkAng = in.readFloat();
                ni.atkDist = in.readFloat();
                m.slot = in.readUnsignedByte();
                break;
            }
            case NetSession.M_DROP:
                m.slot = in.readUnsignedByte();
                m.tick = in.readInt();
                break;
            case NetSession.M_HASH:
                m.tick = in.readInt();
                m.hash = in.readInt();
                break;
            case NetSession.M_BYE:
            case NetSession.M_PING:
                break;
            default:
                throw new IOException("bad message " + m.type);
        }
        return m;
    }

    /** Decodes one whole message. */
    static NetSession.Msg decode(byte[] b) throws IOException {
        return read(new DataInputStream(new ByteArrayInputStream(b)));
    }
}
