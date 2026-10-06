package com.snakebrawl.myapp.game;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Local Wi-Fi connection between two phones, using plain sockets so it runs on Android and on
 * the desktop test harness. The host opens a TCP port and announces itself with UDP broadcasts;
 * the guest listens for those announcements (or types the host's address) and connects.
 *
 * All socket work happens on background threads. The game thread only calls {@link #poll()},
 * the send methods and {@link #close()}, none of which block.
 */
final class NetSession {
    /** False in the browser build, which cannot open sockets. */
    static final boolean AVAILABLE = true;
    static final int TCP_PORT = 47321;
    static final int UDP_PORT = 47322;
    static final int PROTOCOL = 1;
    private static final String MAGIC = "SNAKEBRAWL" + PROTOCOL;

    static final int ST_WAITING = 0, ST_CONNECTING = 1, ST_CONNECTED = 2, ST_CLOSED = 3;

    static final int M_HELLO = 1, M_START = 2, M_MODE = 3, M_INPUT = 4, M_HASH = 5, M_BYE = 6, M_PING = 7;

    /** A message received from the other phone. */
    static final class Msg {
        int type;
        String name;
        int brawler, level, trophies, mode, hash, tick, protocol;
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
    volatile int state;
    /** Why the connection ended, for the player. */
    volatile String closeReason;
    private volatile boolean closing;
    private volatile long lastRecv = System.nanoTime();
    private long lastPing;

    private final ConcurrentLinkedQueue<Msg> inbox = new ConcurrentLinkedQueue<Msg>();
    private final LinkedBlockingQueue<byte[]> outbox = new LinkedBlockingQueue<byte[]>();
    private final List<Room> rooms = new ArrayList<Room>();

    private ServerSocket server;
    private Socket socket;
    private DatagramSocket udp;
    private final String myName;

    private NetSession(boolean host, String myName) {
        this.host = host;
        this.myName = myName;
    }

    // ------------------------------------------------------------------ host

    /** Opens a room: waits for one guest and announces the room on the network. */
    static NetSession host(String name) {
        final NetSession n = new NetSession(true, name);
        n.state = ST_WAITING;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                n.runHost();
            }
        }, "net-host");
        t.setDaemon(true);
        t.start();
        return n;
    }

    private void runHost() {
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(TCP_PORT));
        } catch (IOException e) {
            fail("Could not open a room on this phone (" + e.getMessage() + ")");
            return;
        }
        Thread beacon = new Thread(new Runnable() {
            @Override
            public void run() {
                runBeacon();
            }
        }, "net-beacon");
        beacon.setDaemon(true);
        beacon.start();
        try {
            Socket s = server.accept();
            closeQuietly(server);
            attach(s);
        } catch (IOException e) {
            if (!closing) fail("Room closed");
        }
    }

    private void runBeacon() {
        DatagramSocket ds = null;
        try {
            ds = new DatagramSocket();
            ds.setBroadcast(true);
            byte[] data = (MAGIC + "|" + myName + "|" + TCP_PORT).getBytes("UTF-8");
            while (!closing && state == ST_WAITING) {
                for (InetAddress a : broadcastAddresses()) {
                    try {
                        ds.send(new DatagramPacket(data, data.length, a, UDP_PORT));
                    } catch (IOException ignored) {
                        // Some interfaces refuse broadcasts; the others still work
                    }
                }
                Thread.sleep(700);
            }
        } catch (Exception ignored) {
            // Discovery is best effort; typing the address still works
        } finally {
            if (ds != null) ds.close();
        }
    }

    // ------------------------------------------------------------------ guest

    /** Starts listening for rooms on the network. Call {@link #join} to connect to one. */
    static NetSession search(String name) {
        final NetSession n = new NetSession(false, name);
        n.state = ST_WAITING;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                n.runSearch();
            }
        }, "net-search");
        t.setDaemon(true);
        t.start();
        return n;
    }

    private void runSearch() {
        try {
            udp = new DatagramSocket(null);
            udp.setReuseAddress(true);
            udp.setBroadcast(true);
            udp.bind(new InetSocketAddress(UDP_PORT));
            udp.setSoTimeout(500);
        } catch (IOException e) {
            return; // no discovery, the player can still type the address
        }
        byte[] buf = new byte[512];
        while (!closing && (state == ST_WAITING || state == ST_CONNECTING)) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                udp.receive(p);
                String msg = new String(p.getData(), 0, p.getLength(), "UTF-8");
                String[] parts = msg.split("\\|");
                if (parts.length < 3 || !MAGIC.equals(parts[0])) continue;
                String ip = p.getAddress().getHostAddress();
                synchronized (rooms) {
                    Room r = null;
                    for (Room o : rooms) if (o.ip.equals(ip)) r = o;
                    if (r == null) {
                        r = new Room();
                        r.ip = ip;
                        rooms.add(r);
                    }
                    r.name = parts[1];
                    r.port = Integer.parseInt(parts[2]);
                    r.seen = System.nanoTime();
                }
            } catch (SocketTimeoutException ignored) {
                // loop to check for cancel
            } catch (Exception e) {
                if (closing) break;
            }
        }
        if (udp != null) udp.close();
    }

    /** Rooms heard from in the last few seconds. */
    List<Room> rooms() {
        List<Room> out = new ArrayList<Room>();
        long now = System.nanoTime();
        synchronized (rooms) {
            for (Room r : rooms) if (now - r.seen < 4_000_000_000L) out.add(r);
        }
        return out;
    }

    /** Connects to a host by address. */
    void join(final String ip, final int port) {
        if (state != ST_WAITING) return;
        state = ST_CONNECTING;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket s = new Socket();
                    s.connect(new InetSocketAddress(ip, port), 5000);
                    if (udp != null) udp.close();
                    attach(s);
                } catch (Exception e) {
                    if (!closing) {
                        state = ST_WAITING;
                        closeReason = "Could not reach " + ip + ". Check that both phones are on the same Wi-Fi.";
                    }
                }
            }
        }, "net-join");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ connection

    private void attach(Socket s) throws IOException {
        socket = s;
        s.setTcpNoDelay(true);
        final DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
        final DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
        lastRecv = System.nanoTime();
        state = ST_CONNECTED;
        closeReason = null;
        Thread writer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        byte[] b = outbox.poll(1, TimeUnit.SECONDS);
                        if (b == null) {
                            if (closing || state == ST_CLOSED) break;
                            continue;
                        }
                        if (b.length == 0) break; // shutdown marker
                        out.write(b);
                        if (outbox.isEmpty()) out.flush();
                    }
                    out.flush();
                } catch (Exception ignored) {
                    // the reader notices the broken connection
                } finally {
                    closeQuietly(socket);
                }
            }
        }, "net-writer");
        writer.setDaemon(true);
        writer.start();
        try {
            while (true) {
                Msg m = read(in);
                lastRecv = System.nanoTime();
                if (m.type == M_PING) continue;
                if (m.type == M_BYE) {
                    fail("Your friend left the game");
                    break;
                }
                inbox.add(m);
            }
        } catch (IOException e) {
            if (!closing) fail("Lost connection to your friend");
        } finally {
            closeQuietly(socket);
        }
    }

    private static Msg read(DataInputStream in) throws IOException {
        Msg m = new Msg();
        m.type = in.readUnsignedByte();
        switch (m.type) {
            case M_HELLO: {
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
            case M_START:
                m.seed = in.readLong();
                m.mode = in.readInt();
                break;
            case M_MODE:
                m.mode = in.readInt();
                break;
            case M_INPUT: {
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
            case M_HASH:
                m.tick = in.readInt();
                m.hash = in.readInt();
                break;
            case M_BYE:
            case M_PING:
                break;
            default:
                throw new IOException("bad message " + m.type);
        }
        return m;
    }

    private void fail(String reason) {
        if (state == ST_CLOSED) return;
        closeReason = reason;
        state = ST_CLOSED;
        outbox.offer(new byte[0]);
    }

    /** Messages received since the last call, oldest first. Also handles keep-alive and timeouts. */
    Msg poll() {
        if (state == ST_CONNECTED) {
            long now = System.nanoTime();
            if (now - lastPing > 1_000_000_000L) {
                lastPing = now;
                send(new byte[]{(byte) M_PING});
            }
            if (now - lastRecv > 8_000_000_000L) fail("Lost connection to your friend");
        }
        return inbox.poll();
    }

    // ------------------------------------------------------------------ sending

    private void send(byte[] b) {
        if (state == ST_CONNECTED) outbox.offer(b);
    }

    void sendHello(String name, int brawler, int level, int trophies, int[] palette) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(bo);
        try {
            o.writeByte(M_HELLO);
            o.writeInt(PROTOCOL);
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
        send(bo.toByteArray());
    }

    void sendStart(long seed, int mode) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(bo);
        try {
            o.writeByte(M_START);
            o.writeLong(seed);
            o.writeInt(mode);
        } catch (IOException ignored) {
            // cannot happen
        }
        send(bo.toByteArray());
    }

    void sendMode(int mode) {
        send(new byte[]{(byte) M_MODE, (byte) (mode >>> 24), (byte) (mode >>> 16), (byte) (mode >>> 8), (byte) mode});
    }

    void sendInput(NetInput in) {
        byte[] b = new byte[18];
        b[0] = (byte) M_INPUT;
        putInt(b, 1, in.tick);
        b[5] = (byte) ((in.steer ? 1 : 0) | (in.boost ? 2 : 0) | (in.attack << 2));
        putInt(b, 6, Float.floatToIntBits(in.ang));
        putInt(b, 10, Float.floatToIntBits(in.atkAng));
        putInt(b, 14, Float.floatToIntBits(in.atkDist));
        send(b);
    }

    void sendHash(int tick, int hash) {
        byte[] b = new byte[9];
        b[0] = (byte) M_HASH;
        putInt(b, 1, tick);
        putInt(b, 5, hash);
        send(b);
    }

    private static void putInt(byte[] b, int at, int v) {
        b[at] = (byte) (v >>> 24);
        b[at + 1] = (byte) (v >>> 16);
        b[at + 2] = (byte) (v >>> 8);
        b[at + 3] = (byte) v;
    }

    /** Ends the session and tells the other phone. Safe to call more than once. */
    void close() {
        if (closing) return;
        closing = true;
        if (state == ST_CONNECTED) {
            outbox.offer(new byte[]{(byte) M_BYE});
            outbox.offer(new byte[0]);
        }
        state = ST_CLOSED;
        closeQuietly(server);
        if (udp != null) udp.close();
        if (socket == null) return;
        // The writer thread closes the socket after sending BYE; make sure it happens anyway
        final Socket s = socket;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException ignored) {
                    // closing anyway
                }
                closeQuietly(s);
            }
        }, "net-close");
        t.setDaemon(true);
        t.start();
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException ignored) {
            // already closed
        }
    }

    private static void closeQuietly(ServerSocket c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException ignored) {
            // already closed
        }
    }

    private static void closeQuietly(Socket c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException ignored) {
            // already closed
        }
    }

    // ------------------------------------------------------------------ addresses

    /** This phone's Wi-Fi addresses, for showing to a friend who types it in. */
    static List<String> localAddresses() {
        List<String> out = new ArrayList<String>();
        try {
            Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
            while (e != null && e.hasMoreElements()) {
                NetworkInterface ni = e.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> as = ni.getInetAddresses();
                while (as.hasMoreElements()) {
                    InetAddress a = as.nextElement();
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) out.add(a.getHostAddress());
                }
            }
        } catch (Exception ignored) {
            // no addresses
        }
        return out;
    }

    private static List<InetAddress> broadcastAddresses() {
        List<InetAddress> out = new ArrayList<InetAddress>();
        try {
            out.add(InetAddress.getByName("255.255.255.255"));
            Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
            while (e != null && e.hasMoreElements()) {
                NetworkInterface ni = e.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    if (ia.getBroadcast() != null) out.add(ia.getBroadcast());
                }
            }
        } catch (Exception ignored) {
            // use what we have
        }
        return out;
    }
}
