package com.snakebrawl.myapp.game;

import java.util.List;

/**
 * Multiplayer lobby: host a room or join a friend's room, then start together. On Android rooms are
 * on the local Wi-Fi; in the browser they are online and joined with a room code.
 */
final class FriendsScreen {
    static final int B_FIRST = 600;
    private static final int B_HOST = 600, B_JOIN = 601, B_MODE = 602, B_CANCEL = 603, B_START = 604, B_ADDRESS = 605,
            B_ROOM = 610;
    static final int B_LAST = 640;

    static final int HOME = 0, HOSTING = 1, SEARCHING = 2, GUEST_LOBBY = 3;
    /** Lobby modes: play on the same team, or against each other. */
    static final int TOGETHER = 0, VERSUS = 1;

    private final Game game;
    private final Ui ui;
    int state = HOME;
    NetSession net;
    private boolean helloSent;
    /** The friend's HELLO, once received. */
    NetSession.Msg friend;
    int mode = TOGETHER;
    private List<NetSession.Room> shownRooms = new java.util.ArrayList<NetSession.Room>();
    private float roomRefresh;
    private String lastError;

    FriendsScreen(Game game) {
        this.game = game;
        this.ui = game.ui;
    }

    static boolean handles(int id) {
        return id >= B_FIRST && id < B_LAST;
    }

    /** Leaves the lobby, closing any connection. */
    void cancel() {
        if (net != null) net.close();
        net = null;
        friend = null;
        helloSent = false;
        state = HOME;
        game.gated.setNetworkDiscovery(false);
    }

    // ------------------------------------------------------------------ update

    void update(float dt) {
        if (net == null) return;
        if (state == SEARCHING) {
            roomRefresh -= dt;
            if (roomRefresh <= 0) {
                roomRefresh = 0.5f;
                List<NetSession.Room> r = net.rooms();
                if (r.size() != shownRooms.size() || !sameRooms(r)) {
                    shownRooms = r;
                    game.layout();
                }
            }
            if (net.closeReason != null && net.state == NetSession.ST_WAITING && !net.closeReason.equals(lastError)) {
                lastError = net.closeReason;
                game.showInfo("CAN'T CONNECT", lastError, 0, 0);
            }
        }
        if (net.state == NetSession.ST_CONNECTED && !helloSent) {
            helloSent = true;
            Profile pr = game.profile;
            Brawler b = Brawler.ALL[pr.selected];
            net.sendHello(pr.displayName(), b.id, pr.levels[b.id], pr.trophies, pr.palette());
            if (net.host) net.sendMode(mode);
            game.gated.playSound(Platform.SND_POWER, 0.8f);
            game.layout();
        }
        NetSession.Msg m;
        while ((m = net.poll()) != null) {
            if (m.type == NetSession.M_HELLO) {
                if (m.protocol != NetSession.PROTOCOL) {
                    String why = "Your friend has a different version of Snake Brawl. Update both phones to the same version.";
                    cancel();
                    game.showInfo("VERSION MISMATCH", why, 0, 0);
                    game.layout();
                    return;
                }
                friend = m;
                if (!net.host) state = GUEST_LOBBY;
                game.layout();
            } else if (m.type == NetSession.M_MODE) {
                mode = m.mode == VERSUS ? VERSUS : TOGETHER;
            } else if (m.type == NetSession.M_START && !net.host && friend != null) {
                mode = m.mode == VERSUS ? VERSUS : TOGETHER;
                begin(m.seed);
                return; // leave the following messages (inputs) for the match
            }
        }
        if (net.state == NetSession.ST_CLOSED) {
            String why = net.closeReason != null ? net.closeReason : "Connection closed";
            cancel();
            game.showInfo("DISCONNECTED", why, 0, 0);
            game.layout();
        }
    }

    private boolean sameRooms(List<NetSession.Room> r) {
        for (int i = 0; i < r.size(); i++) {
            if (!r.get(i).ip.equals(shownRooms.get(i).ip) || !r.get(i).name.equals(shownRooms.get(i).name)) return false;
        }
        return true;
    }

    private void begin(long seed) {
        NetSession s = net;
        NetSession.Msg f = friend;
        net = null; // the match owns the session now
        friend = null;
        helloSent = false;
        state = HOME;
        game.gated.setNetworkDiscovery(false);
        game.startNetGame(s, f, seed, mode);
    }

    // ------------------------------------------------------------------ input

    void onButton(int id) {
        switch (id) {
            case B_HOST:
                net = NetSession.host(game.profile.displayName());
                state = HOSTING;
                game.gated.setNetworkDiscovery(true);
                break;
            case B_JOIN:
                game.gated.setNetworkDiscovery(true);
                net = NetSession.search(game.profile.displayName());
                shownRooms = new java.util.ArrayList<NetSession.Room>();
                lastError = null;
                state = SEARCHING;
                break;
            case B_CANCEL:
                cancel();
                break;
            case B_MODE:
                mode = mode == TOGETHER ? VERSUS : TOGETHER;
                if (net != null) net.sendMode(mode);
                break;
            case B_START:
                if (net != null && net.state == NetSession.ST_CONNECTED && friend != null) {
                    long seed = new java.util.Random().nextLong();
                    net.sendStart(seed, mode);
                    begin(seed);
                    return;
                }
                break;
            case B_ADDRESS:
                String ask = NetSession.LAN ? "Friend's address (shown on their phone)" : "Room code (shown on your friend's screen)";
                game.gated.requestText(ask, NetSession.LAN ? "192.168." : "", NetSession.LAN ? 15 : 8, false, new Platform.TextCallback() {
                    @Override
                    public void onText(String text) {
                        if (text == null || net == null || state != SEARCHING) return;
                        String ip = text.trim().toUpperCase();
                        if (NetSession.LAN && !ip.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
                            game.showInfo("CHECK THE ADDRESS", "It looks like 192.168.1.23 and is shown on your friend's phone.", 0, 0);
                            return;
                        }
                        if (!NetSession.LAN && !ip.matches("[A-Z0-9]{4,6}")) {
                            game.showInfo("CHECK THE CODE", "The room code has 5 letters and numbers, like K7QX2.", 0, 0);
                            return;
                        }
                        lastError = null;
                        net.clearError();
                        net.join(ip, NetSession.TCP_PORT);
                        game.layout();
                    }
                });
                return;
            default:
                if (id >= B_ROOM && id < B_LAST && net != null && state == SEARCHING) {
                    int i = id - B_ROOM;
                    if (i < shownRooms.size()) {
                        NetSession.Room r = shownRooms.get(i);
                        lastError = null;
                        net.clearError();
                        net.join(r.ip, r.port);
                    }
                }
                break;
        }
        game.layout();
    }

    // ------------------------------------------------------------------ layout

    void layout() {
        float u = game.u, w = game.w, h = game.h, cx = w / 2;
        ui.add(Game.B_BACK, game.padL + 10 * u, game.padT + 10 * u, game.padL + 230 * u, game.padT + 110 * u, "BACK", null, 0xffff5a5a);
        float bottom = h - game.padB - 30 * u;
        switch (state) {
            case HOME: {
                float cw = 560 * u, ch = 430 * u, top = game.padT + 190 * u;
                ui.add(B_HOST, cx - cw - 30 * u, top, cx - 30 * u, top + ch, null, null, 0);
                ui.add(B_JOIN, cx + 30 * u, top, cx + cw + 30 * u, top + ch, null, null, 0);
                break;
            }
            case HOSTING: {
                ui.add(B_MODE, cx - 330 * u, bottom - 290 * u, cx + 330 * u, bottom - 170 * u,
                        mode == TOGETHER ? "TOGETHER" : "VERSUS", mode == TOGETHER ? "Team up vs 4 bot teams" : "Fight each other + 8 bots",
                        mode == TOGETHER ? 0xffff7a2e : 0xff3fa0ff);
                ui.add(B_CANCEL, cx - 500 * u, bottom - 130 * u, cx - 30 * u, bottom, "CLOSE ROOM", null, 0xffff5a5a);
                Ui.Btn st = ui.add(B_START, cx + 30 * u, bottom - 130 * u, cx + 500 * u, bottom, "START", null, 0xffffc928);
                st.enabled = friend != null;
                break;
            }
            case SEARCHING: {
                float top = game.padT + 200 * u;
                int n = Math.min(4, shownRooms.size());
                for (int i = 0; i < n; i++) {
                    float t = top + i * 120 * u;
                    ui.add(B_ROOM + i, cx - 450 * u, t, cx + 450 * u, t + 100 * u, null, null, 0);
                }
                ui.add(B_CANCEL, cx - 500 * u, bottom - 130 * u, cx - 30 * u, bottom, "CANCEL", null, 0xffff5a5a);
                ui.add(B_ADDRESS, cx + 30 * u, bottom - 130 * u, cx + 500 * u, bottom, NetSession.LAN ? "TYPE ADDRESS" : "ENTER CODE", null,
                        0xff3fa0ff);
                break;
            }
            default:
                ui.add(B_CANCEL, cx - 240 * u, bottom - 130 * u, cx + 240 * u, bottom, "LEAVE", null, 0xffff5a5a);
                break;
        }
    }

    // ------------------------------------------------------------------ render

    void render(Gfx g) {
        float u = game.u, w = game.w, h = game.h, cx = w / 2;
        g.color(0xcc0d1030);
        g.fillRect(0, 0, w, h);
        g.color(0xffffd23f);
        g.text("PLAY WITH FRIENDS", cx, game.padT + 92 * u, 84 * u, Gfx.ALIGN_CENTER, 9 * u, Ui.INK);
        switch (state) {
            case HOME:
                renderHome(g);
                break;
            case HOSTING:
                renderHosting(g);
                break;
            case SEARCHING:
                renderSearching(g);
                break;
            default:
                renderGuestLobby(g);
                break;
        }
        for (int i = 0; i < ui.count; i++) {
            Ui.Btn b = ui.btns[i];
            if (b.id == B_HOST || b.id == B_JOIN || (b.id >= B_ROOM && b.id < B_LAST)) continue;
            ui.button(g, b);
        }
    }

    private void renderHome(Gfx g) {
        float u = game.u, w = game.w, h = game.h;
        drawChoice(g, ui.find(B_HOST), 0xffff7a2e, "HOST A ROOM", "Your friend joins you", true);
        drawChoice(g, ui.find(B_JOIN), 0xff3fa0ff, "JOIN A ROOM", "Find your friend's room", false);
        g.color(0xffb8bdf0);
        g.text(NetSession.LAN ? "Both phones must be on the same Wi-Fi network (or one phone's hotspot)."
                : "Play online with a friend anywhere: share your room code.", w / 2, h - game.padB - 90 * u, 32 * u,
                Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
    }

    private void drawChoice(Gfx g, Ui.Btn b, int col, String title, String sub, boolean hostIcon) {
        if (b == null) return;
        float u = game.u, d = ui.pressed == b.id ? 6 * u : 0;
        float l = b.l + d, t = b.t + d, r = b.r - d, bt = b.b - d;
        ui.panel(g, l, t, r, bt, 0xff262a54);
        g.save();
        g.clip(l + 10 * u, t + 10 * u, r - 10 * u, bt - 10 * u);
        g.vertical(l, t, r, bt, MathUtil.withAlpha(col, 0.45f), 0x00262a54);
        g.restore();
        float cx = (l + r) / 2, iy = t + (bt - t) * 0.36f;
        // Two phones with a signal between them
        float s = 70 * u;
        drawPhone(g, cx - s * 1.3f, iy, s, hostIcon ? col : 0xffd8dcff);
        drawPhone(g, cx + s * 1.3f, iy, s, hostIcon ? 0xffd8dcff : col);
        float pulse = (game.clock * 1.4f) % 1f;
        for (int k = 0; k < 3; k++) {
            float pr = (k + pulse) * 0.33f;
            g.color(MathUtil.withAlpha(0xffffffff, 1f - pr));
            g.arc(cx + (hostIcon ? -s * 0.6f : s * 0.6f), iy, s * (0.4f + pr * 0.9f), hostIcon ? -40 : 140, 80, 5 * u);
        }
        g.color(0xffffffff);
        g.text(title, cx, bt - 110 * u, Ui.fit(g, title, 56 * u, r - l - 40 * u), Gfx.ALIGN_CENTER, 7 * u, Ui.INK);
        g.color(0xffd8dcff);
        g.text(sub, cx, bt - 55 * u, 32 * u, Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
    }

    private void drawPhone(Gfx g, float x, float y, float s, int col) {
        float u = game.u;
        g.color(Ui.INK);
        g.fillRoundRect(x - s * 0.42f - 5 * u, y - s * 0.75f - 5 * u, x + s * 0.42f + 5 * u, y + s * 0.75f + 5 * u, 16 * u);
        g.color(col);
        g.fillRoundRect(x - s * 0.42f, y - s * 0.75f, x + s * 0.42f, y + s * 0.75f, 12 * u);
        g.color(0xff1a1e3e);
        g.fillRoundRect(x - s * 0.32f, y - s * 0.6f, x + s * 0.32f, y + s * 0.5f, 6 * u);
        g.color(0xff5be05b);
        g.fillCircle(x - s * 0.08f, y - s * 0.05f, s * 0.12f);
        g.fillCircle(x + s * 0.1f, y + s * 0.12f, s * 0.1f);
    }

    private void renderHosting(Gfx g) {
        float u = game.u, w = game.w, cx = w / 2;
        float top = game.padT + 170 * u;
        List<String> ips = NetSession.localAddresses();
        String addr = ips.isEmpty() ? (NetSession.LAN ? "No Wi-Fi found" : "Opening room...") : ips.get(0);
        ui.panel(g, cx - 560 * u, top, cx + 560 * u, top + 150 * u, 0xee22264a);
        g.color(0xffb8bdf0);
        g.text(NetSession.LAN ? "YOUR ROOM ADDRESS" : "YOUR ROOM CODE", cx, top + 50 * u, 30 * u, Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
        g.color(0xffffffff);
        g.text(addr, cx, top + 118 * u, 60 * u, Gfx.ALIGN_CENTER, 7 * u, Ui.INK);
        drawPlayers(g, top + 190 * u, friend == null ? null : friend.name, friend == null ? -1 : friend.brawler,
                friend == null ? null : friend.palette);
        g.color(0xffb8bdf0);
        String note = friend == null ? (NetSession.LAN ? "Ask your friend to tap JOIN A ROOM. Your room shows up on their phone."
                : "Ask your friend to tap JOIN A ROOM and enter this code.") : "Pick a mode and tap START!";
        g.text(note, cx, top + 470 * u, 30 * u, Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
    }

    private void renderGuestLobby(Gfx g) {
        float u = game.u, w = game.w, cx = w / 2;
        float top = game.padT + 170 * u;
        drawPlayers(g, top + 40 * u, friend == null ? null : friend.name, friend == null ? -1 : friend.brawler,
                friend == null ? null : friend.palette);
        String m = mode == TOGETHER ? "TOGETHER: team up vs 4 bot teams" : "VERSUS: fight each other + 8 bots";
        g.color(mode == TOGETHER ? 0xffffa35a : 0xff8ac8ff);
        g.text(m, cx, top + 360 * u, 44 * u, Gfx.ALIGN_CENTER, 6 * u, Ui.INK);
        g.color(0xffb8bdf0);
        String host = friend == null ? "the host" : friend.name;
        int dots = (int) (game.clock * 2) % 4;
        g.text("Waiting for " + host + " to start" + "...".substring(0, dots), cx, top + 430 * u, 34 * u, Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
    }

    /** You and your friend side by side. */
    private void drawPlayers(Gfx g, float top, String friendName, int friendBrawler, int[] friendPal) {
        float u = game.u, w = game.w, cx = w / 2;
        Profile pr = game.profile;
        float cw = 480 * u, ch = 230 * u;
        float l1 = cx - cw - 50 * u, l2 = cx + 50 * u;
        ui.panel(g, l1, top, l1 + cw, top + ch, 0xee22264a);
        ui.panel(g, l2, top, l2 + cw, top + ch, 0xee22264a);
        g.color(0xff9cff8a);
        g.text(pr.displayName(), l1 + cw / 2, top + 58 * u, 42 * u, Gfx.ALIGN_CENTER, 5 * u, Ui.INK);
        ui.snakeArt(g, Brawler.ALL[pr.selected], pr.palette(), l1 + cw / 2, top + 150 * u, 0.85f * u, game.clock);
        g.color(0xffffd23f);
        g.text(mode == TOGETHER ? "+" : "VS", cx, top + ch / 2 + 24 * u, 70 * u, Gfx.ALIGN_CENTER, 8 * u, Ui.INK);
        if (friendName != null) {
            g.color(0xff8ad8ff);
            g.text(friendName, l2 + cw / 2, top + 58 * u, Ui.fit(g, friendName, 42 * u, cw - 40 * u), Gfx.ALIGN_CENTER, 5 * u, Ui.INK);
            Brawler fb = Brawler.ALL[Math.max(0, Math.min(Brawler.ALL.length - 1, friendBrawler))];
            ui.snakeArt(g, fb, friendPal != null && friendPal.length >= 2 ? friendPal : new int[]{fb.color1, fb.color2},
                    l2 + cw / 2, top + 150 * u, 0.85f * u, game.clock + 1.3f);
        } else {
            int dots = (int) (game.clock * 2) % 4;
            g.color(0xffb8bdf0);
            g.text("Waiting" + "...".substring(0, dots), l2 + cw / 2, top + ch / 2 + 14 * u, 40 * u, Gfx.ALIGN_CENTER, 5 * u, Ui.INK);
        }
    }

    private void renderSearching(Gfx g) {
        float u = game.u, w = game.w, cx = w / 2;
        float top = game.padT + 200 * u;
        if (shownRooms.isEmpty()) {
            float pulse = (game.clock * 1.2f) % 1f;
            for (int k = 0; k < 3; k++) {
                float pr = (k + pulse) / 3f;
                g.color(MathUtil.withAlpha(0xff3fa0ff, 1f - pr));
                g.strokeCircle(cx, top + 150 * u, 40 * u + pr * 140 * u, 6 * u);
            }
            g.color(0xffffffff);
            g.color(0xffffffff);
            g.text(NetSession.LAN ? "Looking for rooms on your Wi-Fi..." : "Ask your friend for their room code", cx, top + 380 * u,
                    44 * u, Gfx.ALIGN_CENTER, 6 * u, Ui.INK);
            g.color(0xffb8bdf0);
            g.text(NetSession.LAN ? "Not showing up? Tap TYPE ADDRESS and enter the address on your friend's screen."
                    : "Tap ENTER CODE and type the code shown on their screen.", cx, top + 440 * u, 28 * u,
                    Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
        }
        boolean connecting = net != null && net.state == NetSession.ST_CONNECTING;
        for (int i = 0; i < ui.count; i++) {
            Ui.Btn b = ui.btns[i];
            if (b.id < B_ROOM || b.id >= B_LAST) continue;
            int ri = b.id - B_ROOM;
            if (ri >= shownRooms.size()) continue;
            NetSession.Room r = shownRooms.get(ri);
            float d = ui.pressed == b.id ? 5 * u : 0;
            ui.panel(g, b.l + d, b.t + d, b.r - d, b.b - d, 0xff2c3266);
            g.color(0xffffffff);
            g.text(r.name + "'s room", b.l + 40 * u, (b.t + b.b) / 2 + 14 * u, 40 * u, Gfx.ALIGN_LEFT, 5 * u, Ui.INK);
            g.color(0xffffd23f);
            g.text(connecting ? "JOINING..." : "JOIN", b.r - 40 * u, (b.t + b.b) / 2 + 14 * u, 40 * u, Gfx.ALIGN_RIGHT, 5 * u, Ui.INK);
        }
        if (connecting && shownRooms.isEmpty()) {
            g.color(0xffffd23f);
            g.text("Connecting...", cx, top + 500 * u, 40 * u, Gfx.ALIGN_CENTER, 5 * u, Ui.INK);
        }
    }
}
