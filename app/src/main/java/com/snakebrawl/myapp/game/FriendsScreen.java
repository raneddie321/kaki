package com.snakebrawl.myapp.game;

import java.util.List;

/**
 * Multiplayer lobby: host a room or join a friend's room on the same Wi-Fi, then start together.
 * There is no internet play; the browser version has no multiplayer.
 */
final class FriendsScreen {
    static final int B_FIRST = 600;
    private static final int B_HOST = 600, B_JOIN = 601, B_MODE = 602, B_CANCEL = 603, B_START = 604, B_ADDRESS = 605,
            B_MAP = 607, B_INVITE = 608, B_ROOM = 610;
    static final int B_LAST = 640;

    static final int HOME = 0, HOSTING = 1, SEARCHING = 2, GUEST_LOBBY = 3;
    /** Lobby modes: play on the same team, or against each other. */
    static final int TOGETHER = 0, VERSUS = 1;

    private final Game game;
    private final Ui ui;
    int state = HOME;
    NetSession net;
    private boolean helloSent;
    /** Everyone in the room by slot (slot 0 is the host), null for empty slots. */
    NetSession.Msg[] roster = new NetSession.Msg[NetSession.MAX_PLAYERS];
    /** This phone's slot in the room. */
    int mySlot;
    /** Guest: the host's HELLO arrived and passed the version checks. */
    private boolean hostOk;
    int mode = TOGETHER;
    /** Guest: the map the host picked (name for the lobby). */
    private String mapName = "";
    private List<NetSession.Room> shownRooms = new java.util.ArrayList<NetSession.Room>();
    private float roomRefresh;
    private String lastError;

    FriendsScreen(Game game) {
        this.game = game;
        this.ui = game.ui;
    }

    /** Nothing to play with: the browser version has no Wi-Fi play. */
    private boolean blocked() {
        return !Lan.available();
    }

    /** Always local Wi-Fi now (kept as a method so the texts read clearly). */
    private boolean lanMode() {
        return true;
    }

    static boolean handles(int id) {
        return id >= B_FIRST && id < B_LAST;
    }

    /** Leaves the lobby, closing any connection. */
    void cancel() {
        if (net != null) net.close();
        net = null;
        clearRoster();
        helloSent = false;
        hostOk = false;
        state = HOME;
        game.gated.setNetworkDiscovery(false);
    }

    private void clearRoster() {
        for (int i = 0; i < roster.length; i++) roster[i] = null;
        mySlot = 0;
    }

    /** Players in the room, this phone included. */
    int playerCount() {
        int n = 0;
        for (NetSession.Msg m : roster) if (m != null) n++;
        return n;
    }

    /** This phone as a roster entry. */
    private NetSession.Msg me() {
        Profile pr = game.profile;
        NetSession.Msg m = new NetSession.Msg();
        m.name = pr.displayName();
        m.brawler = pr.selected;
        m.level = pr.levels[pr.selected];
        m.trophies = pr.trophies;
        m.palette = pr.palette();
        m.exact = net != null && net.exact();
        m.platform = net != null ? net.platform() : NetSession.PLAT_APP;
        return m;
    }

    private byte[] myHello() {
        Profile pr = game.profile;
        Brawler b = Brawler.ALL[pr.selected];
        return net.hello(pr.displayName(), b.id, pr.levels[b.id], pr.trophies, pr.palette());
    }

    private byte[] modeMsg() {
        int map = hostMap();
        return NetCodec.mode(mode, map, Maps.name(game.gated, map));
    }

    /** The map the host plays: the one picked on the menu. */
    private int hostMap() {
        int map = game.profile.map;
        return Maps.valid(game.gated, map) ? map : Maps.SUNNY;
    }

    /** Host: tells every guest who is in the room. */
    private void sendRoster() {
        if (net == null || !net.host) return;
        roster[0] = me();
        for (int s = 1; s < roster.length; s++) if (roster[s] != null) net.sendTo(s, NetCodec.roster(roster, s));
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
        if (!net.host && net.state == NetSession.ST_CONNECTED && !helloSent) {
            helloSent = true;
            net.sendBytes(myHello());
            game.gated.playSound(Platform.SND_POWER, 0.8f);
            game.layout();
        }
        NetSession.Msg m;
        while (net != null && (m = net.poll()) != null) {
            switch (m.type) {
                case NetSession.M_JOINED:
                    // A phone connected: introduce ourselves first, so a friend we turn away learns why
                    net.sendTo(m.from, myHello());
                    net.sendTo(m.from, modeMsg());
                    break;
                case NetSession.M_LEFT:
                    if (m.from > 0 && m.from < roster.length && roster[m.from] != null) {
                        roster[m.from] = null;
                        sendRoster();
                        game.layout();
                    }
                    break;
                case NetSession.M_HELLO: {
                    m.name = SocialScreens.cleanName(m.name);
                    if (m.name.length() == 0) m.name = "Friend";
                    String why = null;
                    if (m.protocol != NetSession.PROTOCOL) {
                        why = net.host ? "A friend tried to join with a different version of Snake Brawl. Update all phones to the same version."
                                : "Your friend has a different version of Snake Brawl. Update all phones to the same version.";
                    } else if (m.exact != net.exact()) {
                        why = net.exact()
                                ? m.name + "'s browser is too old to play with you. Ask them to update Safari or Chrome."
                                : "This browser is too old to play with " + m.name + ". Update Safari or Chrome and try again.";
                    }
                    if (net.host) {
                        if (why != null) {
                            net.kick(m.from);
                            game.showInfo("CAN'T JOIN", why, 0, 0);
                        } else if (m.from > 0 && m.from < roster.length) {
                            roster[m.from] = m;
                            sendRoster();
                            game.gated.playSound(Platform.SND_POWER, 0.8f);
                        }
                    } else if (why != null) {
                        cancel();
                        game.showInfo(m.protocol != NetSession.PROTOCOL ? "VERSION MISMATCH" : "CAN'T PLAY TOGETHER", why, 0, 0);
                        game.layout();
                        return;
                    } else {
                        hostOk = true;
                        if (roster[0] != null) state = GUEST_LOBBY;
                    }
                    game.layout();
                    break;
                }
                case NetSession.M_ROSTER:
                    if (net.host || m.roster == null) break;
                    clearRoster();
                    for (int i = 0; i < m.roster.length && i < roster.length; i++) roster[i] = m.roster[i];
                    mySlot = m.you;
                    if (hostOk) state = GUEST_LOBBY;
                    game.layout();
                    break;
                case NetSession.M_MODE:
                    mode = m.mode == VERSUS ? VERSUS : TOGETHER;
                    mapName = m.name == null ? "" : m.name;
                    break;
                case NetSession.M_START:
                    if (net.host || !hostOk || roster[0] == null) break;
                    mode = m.mode == VERSUS ? VERSUS : TOGETHER;
                    begin(m.seed, m.mapId, m.mapData);
                    return; // leave the following messages (inputs) for the match
                default:
                    break;
            }
        }
        if (net != null && net.state == NetSession.ST_CLOSED) {
            String why = net.closeReason != null ? net.closeReason : "Connection closed";
            cancel();
            game.showInfo(why.contains("full") ? "ROOM FULL" : "DISCONNECTED", why, 0, 0);
            game.layout();
        }
    }

    private boolean sameRooms(List<NetSession.Room> r) {
        for (int i = 0; i < r.size(); i++) {
            if (!r.get(i).ip.equals(shownRooms.get(i).ip) || !r.get(i).name.equals(shownRooms.get(i).name)) return false;
        }
        return true;
    }

    private void begin(long seed, int mapId, byte[] mapData) {
        NetSession s = net;
        NetSession.Msg[] r = roster.clone();
        int slot = mySlot;
        net = null; // the match owns the session now
        clearRoster();
        helloSent = false;
        hostOk = false;
        state = HOME;
        game.gated.setNetworkDiscovery(false);
        game.startNetGame(s, r, slot, seed, mode, mapId, mapData);
    }

    // ------------------------------------------------------------------ input

    void onButton(int id) {
        switch (id) {
            case B_HOST:
                if (blocked()) return;
                net = Lan.host(game.profile.displayName());
                game.gated.setNetworkDiscovery(true);
                clearRoster();
                roster[0] = me();
                state = HOSTING;
                break;
            case B_JOIN:
                if (blocked()) return;
                game.gated.setNetworkDiscovery(true);
                net = Lan.search(game.profile.displayName());
                shownRooms = new java.util.ArrayList<NetSession.Room>();
                lastError = null;
                clearRoster();
                mapName = "";
                state = SEARCHING;
                break;
            case B_CANCEL:
                cancel();
                break;
            case B_MODE:
                mode = mode == TOGETHER ? VERSUS : TOGETHER;
                if (net != null) net.sendBytes(modeMsg());
                break;
            case B_MAP:
                game.profile.map = Maps.next(game.gated, hostMap());
                game.profile.save();
                if (net != null) net.sendBytes(modeMsg());
                break;
            case B_INVITE:
                game.invite();
                return;
            case B_START:
                if (net != null && net.state == NetSession.ST_CONNECTED && playerCount() >= 2) {
                    net.lockRoom();
                    sendRoster();
                    long seed = new java.util.Random().nextLong();
                    int map = hostMap();
                    Maps.Custom c = Maps.isCustom(map) ? Maps.load(game.gated, map - Maps.CUSTOM) : null;
                    byte[] data = c != null ? Maps.encode(c) : null;
                    net.sendBytes(NetCodec.start(seed, mode, playerCount(), c != null ? Maps.CUSTOM : map, data));
                    begin(seed, c != null ? Maps.CUSTOM : map, data);
                    return;
                }
                break;
            case B_ADDRESS:
                final boolean lan = lanMode();
                String ask = lan ? "Friend's address (shown on their phone)" : "Room code (shown on your friend's screen)";
                game.gated.requestText(ask, lan ? "192.168." : "", lan ? 15 : 8, false, new Platform.TextCallback() {
                    @Override
                    public void onText(String text) {
                        if (text == null || net == null || state != SEARCHING) return;
                        String ip = text.trim().toUpperCase();
                        if (lan && !ip.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
                            game.showInfo("CHECK THE ADDRESS", "It looks like 192.168.1.23 and is shown on your friend's phone.", 0, 0);
                            return;
                        }
                        if (!lan && !ip.matches("[A-Z0-9]{4,6}")) {
                            game.showInfo("CHECK THE CODE", "The room code has 5 letters and numbers, like K7QX2.", 0, 0);
                            return;
                        }
                        lastError = null;
                        net.clearError();
                        net.join(ip, lan ? Lan.port() : 0);
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
                if (blocked()) break;
                float cw = 560 * u, ch = 430 * u, top = game.padT + 190 * u;
                ui.add(B_HOST, cx - cw - 30 * u, top, cx - 30 * u, top + ch, null, null, 0);
                ui.add(B_JOIN, cx + 30 * u, top, cx + cw + 30 * u, top + ch, null, null, 0);
                ui.add(B_INVITE, cx - 260 * u, top + ch + 40 * u, cx + 260 * u, top + ch + 150 * u, "INVITE FRIENDS",
                        "Send them the game", 0xff4ad04a);
                break;
            }
            case HOSTING: {
                ui.add(B_MODE, cx - 690 * u, bottom - 290 * u, cx - 30 * u, bottom - 170 * u,
                        mode == TOGETHER ? "TOGETHER" : "VERSUS", modeSub(), mode == TOGETHER ? 0xffff7a2e : 0xff3fa0ff);
                int map = hostMap();
                ui.add(B_MAP, cx + 30 * u, bottom - 290 * u, cx + 690 * u, bottom - 170 * u, "MAP: " + Maps.name(game.gated, map),
                        "Tap to change", 0xff3fb6a8);
                ui.add(B_CANCEL, cx - 500 * u, bottom - 130 * u, cx - 30 * u, bottom, "CLOSE ROOM", null, 0xffff5a5a);
                Ui.Btn st = ui.add(B_START, cx + 30 * u, bottom - 130 * u, cx + 500 * u, bottom, "START", null, 0xffffc928);
                st.enabled = playerCount() >= 2;
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
                ui.add(B_ADDRESS, cx + 30 * u, bottom - 130 * u, cx + 500 * u, bottom, lanMode() ? "TYPE ADDRESS" : "ENTER CODE", null,
                        0xff3fa0ff);
                break;
            }
            default:
                ui.add(B_CANCEL, cx - 240 * u, bottom - 130 * u, cx + 240 * u, bottom, "LEAVE", null, 0xffff5a5a);
                break;
        }
    }

    private String modeSub() {
        int n = Math.max(2, playerCount());
        if (mode == VERSUS) return "Fight each other + " + (10 - n) + " bots";
        return n >= 3 ? "Trio vs 3 bot teams" : "Team up vs 4 bot teams";
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
        if (blocked()) {
            g.color(0xffffffff);
            g.text("PLAY WITH FRIENDS IN THE APP", w / 2, h * 0.45f, 56 * u, Gfx.ALIGN_CENTER, 7 * u, Ui.INK);
            g.color(0xffb8bdf0);
            g.text("Friends play together on the same Wi-Fi in the Snake Brawl app.", w / 2, h * 0.45f + 70 * u, 32 * u,
                    Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
            g.text("You can still play Showdown, Duo and Endless against bots!", w / 2, h * 0.45f + 115 * u, 32 * u,
                    Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
            return;
        }
        drawChoice(g, ui.find(B_HOST), 0xffff7a2e, "HOST A ROOM", "Your friend joins you", true);
        drawChoice(g, ui.find(B_JOIN), 0xff3fa0ff, "JOIN A ROOM", "Find your friend's room", false);
        g.color(0xffb8bdf0);
        String note = "Up to 3 phones on the same Wi-Fi network (or one phone's hotspot).";
        g.text(note, w / 2, h - game.padB - 90 * u, 32 * u,
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
        float top = game.padT + 150 * u;
        String label = net != null ? net.roomLabel() : null;
        String addr = label == null ? (lanMode() ? "No Wi-Fi found" : "Opening room...") : label;
        ui.panel(g, cx - 560 * u, top, cx + 560 * u, top + 130 * u, 0xee22264a);
        g.color(0xffb8bdf0);
        g.text(lanMode() ? "YOUR ROOM ADDRESS" : "YOUR ROOM CODE", cx, top + 42 * u, 28 * u, Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
        g.color(0xffffffff);
        g.text(addr, cx, top + 104 * u, 56 * u, Gfx.ALIGN_CENTER, 7 * u, Ui.INK);
        drawPlayers(g, top + 150 * u);
        g.color(0xffb8bdf0);
        int max = net != null ? net.maxPlayers() : 2;
        int n = playerCount();
        String note;
        if (n < 2) note = lanMode() ? "Ask your friends to tap JOIN A ROOM. Your room shows up on their phones (up to " + max + " players)."
                : "Ask your friend to tap JOIN A ROOM and enter this code.";
        else if (n < max) note = "Tap START, or wait for one more friend to join!";
        else note = "The room is full. Pick a mode and tap START!";
        g.text(note, cx, top + 420 * u, 30 * u, Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
    }

    private void renderGuestLobby(Gfx g) {
        float u = game.u, w = game.w, cx = w / 2;
        float top = game.padT + 170 * u;
        drawPlayers(g, top);
        int n = playerCount();
        String m = mode == TOGETHER ? (n >= 3 ? "TOGETHER: trio vs 3 bot teams" : "TOGETHER: team up vs 4 bot teams")
                : "VERSUS: fight each other + " + (10 - Math.max(2, n)) + " bots";
        g.color(mode == TOGETHER ? 0xffffa35a : 0xff8ac8ff);
        g.text(m, cx, top + 320 * u, 44 * u, Gfx.ALIGN_CENTER, 6 * u, Ui.INK);
        if (mapName.length() > 0) {
            g.color(0xff8af0e0);
            g.text("MAP: " + mapName, cx, top + 375 * u, 36 * u, Gfx.ALIGN_CENTER, 5 * u, Ui.INK);
        }
        g.color(0xffb8bdf0);
        String host = roster[0] == null ? "the host" : roster[0].name;
        int dots = (int) (game.clock * 2) % 4;
        g.text("Waiting for " + host + " to start" + "...".substring(0, dots), cx, top + 435 * u, 34 * u, Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
    }

    /** Everyone in the room side by side (you first), with empty seats while waiting. */
    private void drawPlayers(Gfx g, float top) {
        float u = game.u, w = game.w, cx = w / 2;
        int seats = net != null ? Math.min(net.maxPlayers(), roster.length) : 2;
        float cw = seats >= 3 ? 400 * u : 480 * u, ch = 230 * u, gap = seats >= 3 ? 40 * u : 100 * u;
        float total = seats * cw + (seats - 1) * gap;
        float l0 = cx - total / 2;
        // You first, then the others in slot order
        int[] order = new int[seats];
        int k = 0;
        order[k++] = mySlot;
        for (int s = 0; s < roster.length && k < seats; s++) if (s != mySlot) order[k++] = s;
        for (int i = 0; i < seats; i++) {
            float l = l0 + i * (cw + gap);
            ui.panel(g, l, top, l + cw, top + ch, 0xee22264a);
            NetSession.Msg p = roster[order[i]];
            if (i > 0) {
                g.color(0xffffd23f);
                g.text(mode == TOGETHER ? "+" : "VS", l - gap / 2, top + ch / 2 + 22 * u, (seats >= 3 ? 50 : 70) * u, Gfx.ALIGN_CENTER, 7 * u, Ui.INK);
            }
            if (p == null) {
                int dots = (int) (game.clock * 2) % 4;
                g.color(0xffb8bdf0);
                g.text("Waiting" + "...".substring(0, dots), l + cw / 2, top + ch / 2 + 14 * u, 40 * u, Gfx.ALIGN_CENTER, 5 * u, Ui.INK);
                continue;
            }
            boolean mine = i == 0;
            Brawler fb = Brawler.ALL[Math.max(0, Math.min(Brawler.ALL.length - 1, mine ? game.profile.selected : p.brawler))];
            int[] pal = mine ? game.profile.palette() : (p.palette != null && p.palette.length >= 2 ? p.palette : new int[]{fb.color1, fb.color2});
            String nm = mine ? game.profile.displayName() : p.name;
            g.color(mine ? 0xff9cff8a : 0xff8ad8ff);
            g.text(nm, l + cw / 2, top + 58 * u, Ui.fit(g, nm, 42 * u, cw - 40 * u), Gfx.ALIGN_CENTER, 5 * u, Ui.INK);
            ui.snakeArt(g, fb, pal, l + cw / 2, top + 150 * u, (seats >= 3 ? 0.72f : 0.85f) * u, game.clock + i * 1.3f);
            if (!mine) {
                g.color(0xffb8bdf0);
                String where = order[i] == 0 ? "host" : p.platform == NetSession.PLAT_BROWSER ? "in a browser" : "in the app";
                g.text(where, l + cw / 2, top + ch - 16 * u, 24 * u, Gfx.ALIGN_CENTER, 3 * u, Ui.INK);
            }
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
            g.text(lanMode() ? "Looking for rooms on your Wi-Fi..." : "Ask your friend for their room code", cx, top + 380 * u,
                    44 * u, Gfx.ALIGN_CENTER, 6 * u, Ui.INK);
            g.color(0xffb8bdf0);
            g.text(lanMode() ? "Not showing up? Tap TYPE ADDRESS and enter the address on your friend's screen."
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
