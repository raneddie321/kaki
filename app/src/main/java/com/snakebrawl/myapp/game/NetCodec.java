package com.snakebrawl.myapp.game;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Wire format of multiplayer messages, shared by the Wi-Fi (Android) and online (browser) sessions. */
final class NetCodec {
    private NetCodec() {}

    static byte[] hello(String name, int brawler, int level, int trophies, int[] palette) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(bo);
        try {
            o.writeByte(NetSession.M_HELLO);
            o.writeInt(NetSession.PROTOCOL);
            o.writeUTF(name);
            o.writeInt(brawler);
            o.writeInt(level);
            o.writeInt(trophies);
            int n = Math.min(palette.length, 16);
            o.writeByte(n);
            for (int i = 0; i < n; i++) o.writeInt(palette[i]);
        } catch (IOException ignored) {
            // cannot happen with a byte array
        }
        return bo.toByteArray();
    }

    static byte[] start(long seed, int mode) {
        byte[] b = new byte[13];
        b[0] = (byte) NetSession.M_START;
        putInt(b, 1, (int) (seed >>> 32));
        putInt(b, 5, (int) seed);
        putInt(b, 9, mode);
        return b;
    }

    static byte[] mode(int mode) {
        byte[] b = new byte[5];
        b[0] = (byte) NetSession.M_MODE;
        putInt(b, 1, mode);
        return b;
    }

    static byte[] input(NetInput in) {
        byte[] b = new byte[18];
        b[0] = (byte) NetSession.M_INPUT;
        putInt(b, 1, in.tick);
        b[5] = (byte) ((in.steer ? 1 : 0) | (in.boost ? 2 : 0) | (in.attack << 2));
        putInt(b, 6, Float.floatToIntBits(in.ang));
        putInt(b, 10, Float.floatToIntBits(in.atkAng));
        putInt(b, 14, Float.floatToIntBits(in.atkDist));
        return b;
    }

    /**
     * Rounds an input's angles exactly as sending it does, so the local phone applies the same
     * values its friend receives. (In the browser floats are really doubles until rounded.)
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
            case NetSession.M_HELLO: {
                m.protocol = in.readInt();
                m.name = in.readUTF();
                m.brawler = in.readInt();
                m.level = in.readInt();
                m.trophies = in.readInt();
                int n = in.readUnsignedByte();
                m.palette = new int[n];
                for (int i = 0; i < n; i++) m.palette[i] = in.readInt();
                break;
            }
            case NetSession.M_START:
                m.seed = in.readLong();
                m.mode = in.readInt();
                break;
            case NetSession.M_MODE:
                m.mode = in.readInt();
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
                break;
            }
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
