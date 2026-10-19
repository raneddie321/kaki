package com.snakebrawl.myapp.game;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
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
 * Local Wi-Fi room for up to three phones (Android only), using plain sockets so it runs on
 * Android and on the desktop test harness. The host opens a TCP port and announces itself with UDP
 * broadcasts; guests listen for those announcements (or type the host's address) and connect.
 * Every guest has its own connection to the host; the host forwards what one guest sends to the
 * others (see Game and FriendsScreen).
 *
 * All socket work happens on background threads. The game thread only calls {@link #poll()},
 * the send methods and {@link #close()}, none of which block.
 */
final class LanSession extends NetSession {
    static final int TCP_PORT = 47321;
    static final int UDP_PORT = 47322;
    private static final String MAGIC = "SNAKEBRAWL" + PROTOCOL;

    private volatile boolean closing;
    /** The host stops taking guests once the match starts. */
    private volatile boolean locked;
    private long lastPing;

    private final ConcurrentLinkedQueue<Msg> inbox = new ConcurrentLinkedQueue<Msg>();
    private final List<Room> rooms = new ArrayList<Room>();

    private ServerSocket server;
    private DatagramSocket udp;
    private final String myName;

    /** Connections by slot: the host uses 1..MAX_PLAYERS-1, a guest only uses 0 (the host). */
    private final Peer[] peers = new Peer[MAX_PLAYERS];

    /** One TCP connection with its own writer thread. */
    private final class Peer {
        final int slot;
        final Socket socket;
        final LinkedBlockingQueue<byte[]> outbox = new LinkedBlockingQueue<byte[]>();
        volatile long lastRecv = System.nanoTime();
        volatile boolean open = true;

        Peer(int slot, Socket socket) {
            this.slot = slot;
            this.socket = socket;
        }

        void send(byte[] b) {
            if (open) outbox.offer(b);
        }

        /** Closes the connection; the reader reports the player as gone. */
        void shut(boolean sayBye) {
            if (!open) return;
            if (sayBye) outbox.offer(NetCodec.single(M_BYE));
            outbox.offer(new byte[0]);
            open = false;
        }
    }

    private LanSession(boolean host, String myName) {
        super(host);
        this.myName = myName;
    }

    // ------------------------------------------------------------------ host

    /** Opens a room: takes guests and announces the room on the network. */
    static LanSession host(String name) {
        final LanSession n = new LanSession(true, name);
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
        while (!closing && !locked) {
            Socket s;
            try {
                s = server.accept();
            } catch (IOException e) {
                if (!closing && !locked) fail("Room closed");
                return;
            }
            int slot = freeSlot();
            if (slot < 0 || locked) {
                // Room full (or the match already started): turn the phone away politely
                try {
                    s.getOutputStream().write(M_BYE);
                    s.getOutputStream().flush();
                } catch (IOException ignored) {
                    // closing anyway
                }
                closeQuietly(s);
                continue;
            }
            startPeer(slot, s);
        }
    }

    private synchronized int freeSlot() {
        for (int i = 1; i < peers.length; i++) if (peers[i] == null) return i;
        return -1;
    }

    private void runBeacon() {
        DatagramSocket ds = null;
        try {
            ds = new DatagramSocket();
            ds.setBroadcast(true);
            while (!closing && !locked) {
                if (freeSlot() > 0) {
                    byte[] data = (MAGIC + "|" + myName + "|" + TCP_PORT).getBytes("UTF-8");
                    for (InetAddress a : broadcastAddresses()) {
                        try {
                            ds.send(new DatagramPacket(data, data.length, a, UDP_PORT));
                        } catch (IOException ignored) {
                            // Some interfaces refuse broadcasts; the others still work
                        }
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
    static LanSession search(String name) {
        final LanSession n = new LanSession(false, name);
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
    @Override
    List<Room> rooms() {
        List<Room> out = new ArrayList<Room>();
        long now = System.nanoTime();
        synchronized (rooms) {
            for (Room r : rooms) if (now - r.seen < 4_000_000_000L) out.add(r);
        }
        return out;
    }

    /** Connects to a host by address. */
    @Override
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
                    startPeer(0, s);
                } catch (Exception e) {
                    if (!closing) {
                        state = ST_WAITING;
                        closeReason = "Could not reach " + ip + ". Check that all phones are on the same Wi-Fi.";
                    }
                }
            }
        }, "net-join");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ connections

    private void startPeer(final int slot, final Socket s) {
        final Peer p = new Peer(slot, s);
        synchronized (this) {
            peers[slot] = p;
        }
        Thread reader = new Thread(new Runnable() {
            @Override
            public void run() {
                runPeer(p);
            }
        }, "net-peer-" + slot);
        reader.setDaemon(true);
        reader.start();
    }

    private void runPeer(final Peer p) {
        final DataInputStream in;
        final DataOutputStream out;
        try {
            p.socket.setTcpNoDelay(true);
            in = new DataInputStream(new BufferedInputStream(p.socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(p.socket.getOutputStream()));
        } catch (IOException e) {
            peerGone(p, "Lost connection");
            return;
        }
        Thread writer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        byte[] b = p.outbox.poll(1, TimeUnit.SECONDS);
                        if (b == null) {
                            if (closing || !p.open) break;
                            continue;
                        }
                        if (b.length == 0) break; // shutdown marker
                        out.write(b);
                        if (p.outbox.isEmpty()) out.flush();
                    }
                    out.flush();
                } catch (Exception ignored) {
                    // the reader notices the broken connection
                } finally {
                    closeQuietly(p.socket);
                }
            }
        }, "net-writer-" + p.slot);
        writer.setDaemon(true);
        writer.start();
        p.lastRecv = System.nanoTime();
        if (host) {
            state = ST_CONNECTED;
            Msg j = new Msg();
            j.type = M_JOINED;
            j.from = p.slot;
            inbox.add(j);
        } else {
            state = ST_CONNECTED;
            closeReason = null;
        }
        String why = null;
        boolean heard = false;
        try {
            while (true) {
                Msg m = NetCodec.read(in);
                p.lastRecv = System.nanoTime();
                if (m.type == M_PING) continue;
                if (m.type == M_BYE) {
                    why = host ? "left the game" : heard ? "The host left the game"
                            : "That room is full (" + MAX_PLAYERS + " players max) or its match already started";
                    break;
                }
                heard = true;
                m.from = p.slot;
                inbox.add(m);
            }
        } catch (IOException e) {
            why = host ? "lost connection" : "Lost connection to the host";
        } finally {
            closeQuietly(p.socket);
        }
        peerGone(p, why);
    }

    private void peerGone(Peer p, String why) {
        p.open = false;
        p.outbox.offer(new byte[0]);
        synchronized (this) {
            if (peers[p.slot] != p) return;
            peers[p.slot] = null;
        }
        if (closing) return;
        if (host) {
            Msg m = new Msg();
            m.type = M_LEFT;
            m.from = p.slot;
            m.name = why;
            inbox.add(m);
        } else {
            fail(why != null ? why : "Lost connection to the host");
        }
    }

    private void fail(String reason) {
        if (state == ST_CLOSED) return;
        closeReason = reason;
        state = ST_CLOSED;
        for (Peer p : peersNow()) p.shut(false);
    }

    private synchronized Peer[] peersNow() {
        int n = 0;
        for (Peer p : peers) if (p != null) n++;
        Peer[] out = new Peer[n];
        n = 0;
        for (Peer p : peers) if (p != null) out[n++] = p;
        return out;
    }

    /** Next message, oldest first. Also handles keep-alive and timeouts. */
    @Override
    Msg poll() {
        if (state == ST_CONNECTED) {
            long now = System.nanoTime();
            boolean ping = now - lastPing > 1_000_000_000L;
            if (ping) lastPing = now;
            for (Peer p : peersNow()) {
                if (ping) p.send(NetCodec.single(M_PING));
                if (now - p.lastRecv > 8_000_000_000L) {
                    // Silent for too long: drop it (the reader thread reports it)
                    p.shut(false);
                    closeQuietly(p.socket);
                }
            }
        }
        return inbox.poll();
    }

    // ------------------------------------------------------------------ sending

    @Override
    void sendBytes(byte[] b) {
        if (state != ST_CONNECTED) return;
        for (Peer p : peersNow()) p.send(b);
    }

    @Override
    void sendTo(int slot, byte[] b) {
        Peer p;
        synchronized (this) {
            p = slot >= 0 && slot < peers.length ? peers[slot] : null;
        }
        if (p != null) p.send(b);
    }

    @Override
    void kick(int slot) {
        Peer p;
        synchronized (this) {
            p = slot > 0 && slot < peers.length ? peers[slot] : null;
        }
        if (p != null) {
            p.shut(true);
            // The writer closes the socket after BYE; the reader then reports the guest as gone
        }
    }

    @Override
    void lockRoom() {
        if (!host || locked) return;
        locked = true;
        closeQuietly(server);
    }

    @Override
    int maxPlayers() {
        return MAX_PLAYERS;
    }

    @Override
    String roomLabel() {
        List<String> ips = localAddresses();
        return ips.isEmpty() ? null : ips.get(0);
    }

    @Override
    boolean lan() {
        return true;
    }

    @Override
    boolean exact() {
        return true;
    }

    /** Ends the session and tells the other phones. Safe to call more than once. */
    @Override
    void close() {
        if (closing) return;
        closing = true;
        state = ST_CLOSED;
        closeQuietly(server);
        if (udp != null) udp.close();
        final Peer[] ps = peersNow();
        for (Peer p : ps) p.shut(true);
        if (ps.length == 0) return;
        // The writer threads close the sockets after sending BYE; make sure it happens anyway
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException ignored) {
                    // closing anyway
                }
                for (Peer p : ps) closeQuietly(p.socket);
            }
        }, "net-close");
        t.setDaemon(true);
        t.start();
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

    @Override
    void clearError() {
        closeReason = null;
    }

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
