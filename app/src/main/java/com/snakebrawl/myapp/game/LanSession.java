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
 * Local Wi-Fi connection between two phones (Android only), using plain sockets so it runs on Android and on
 * the desktop test harness. The host opens a TCP port and announces itself with UDP broadcasts;
 * the guest listens for those announcements (or types the host's address) and connects.
 *
 * All socket work happens on background threads. The game thread only calls {@link #poll()},
 * the send methods and {@link #close()}, none of which block.
 */
final class LanSession extends NetSession {
    static final int TCP_PORT = 47321;
    static final int UDP_PORT = 47322;
    private static final String MAGIC = "SNAKEBRAWL" + PROTOCOL;

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

    private LanSession(boolean host, String myName) {
        super(host);
        this.myName = myName;
    }

    // ------------------------------------------------------------------ host

    /** Opens a room: waits for one guest and announces the room on the network. */
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
                Msg m = NetCodec.read(in);
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

    private void fail(String reason) {
        if (state == ST_CLOSED) return;
        closeReason = reason;
        state = ST_CLOSED;
        outbox.offer(new byte[0]);
    }

    /** Messages received since the last call, oldest first. Also handles keep-alive and timeouts. */
    @Override
    Msg poll() {
        if (state == ST_CONNECTED) {
            long now = System.nanoTime();
            if (now - lastPing > 1_000_000_000L) {
                lastPing = now;
                send(NetCodec.single(M_PING));
            }
            if (now - lastRecv > 8_000_000_000L) fail("Lost connection to your friend");
        }
        return inbox.poll();
    }

    // ------------------------------------------------------------------ sending

    private void send(byte[] b) {
        if (state == ST_CONNECTED) outbox.offer(b);
    }

    @Override
    void sendBytes(byte[] b) {
        send(b);
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

    /** Ends the session and tells the other phone. Safe to call more than once. */
    @Override
    void close() {
        if (closing) return;
        closing = true;
        if (state == ST_CONNECTED) {
            outbox.offer(NetCodec.single(M_BYE));
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
