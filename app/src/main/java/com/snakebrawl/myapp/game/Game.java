package com.snakebrawl.myapp.game;

/** Top-level game: screens, HUD, touch controls and progression. Host-agnostic. */
public final class Game {
    static final int MENU = 0, BRAWLERS = 1, PLAY = 2, RESULT = 3, SHOP = 4, SETTINGS = 5, ONBOARD = 6, CLUB = 7, FRIENDS = 8;
    private static final float STEP = 1f / 60f;

    static final int B_PLAY = 1, B_MODE = 2, B_BRAWLERS = 3, B_SHOP = 4, B_SETTINGS = 5, B_BACK = 6,
            B_AGAIN = 7, B_MENU = 8, B_RESUME = 9, B_QUIT = 10, B_CLUB = 11, B_FRIENDS = 12, B_YES = 90, B_NO = 91, B_OK = 92,
            B_SHEET_BUY = 93, B_SHEET_CANCEL = 94;

    // Popup actions confirmed with YES
    static final int ACT_NONE = 0, ACT_BUY_SKIN = 1, ACT_UNLOCK = 2, ACT_UPGRADE = 3, ACT_BOX = 4,
            ACT_MEGA_BOX = 5, ACT_RESET = 6, ACT_DEAL = 7, ACT_JOIN_CLUB = 8, ACT_LEAVE_CLUB = 9;

    private static final int[] TROPHY_TABLE = {10, 8, 7, 6, 4, 2, 0, -1, -2, -3};
    private static final int[] RANK_COINS = {50, 40, 32, 26, 20, 15, 11, 8, 5, 3};

    private final Platform host;
    final Platform gated;
    final Profile profile;
    final Ui ui = new Ui();
    private final MetaScreens meta;
    private SocialScreens social;
    private FriendsScreen friends;

    // Wi-Fi match (lockstep: both phones simulate the same world from the same inputs)
    private static final int NET_DELAY = 4, NET_RING = 512, NET_HASH_EVERY = 120;
    NetSession net;
    private boolean netHost;
    /** The last result came from a Wi-Fi match. */
    private boolean resNet;
    int netTick;
    private final NetInput netIn = new NetInput();
    private final NetInput[] netLocal = new NetInput[NET_RING], netRemote = new NetInput[NET_RING];
    private final int[] netHashTick = new int[64], netHashVal = new int[64];
    private float netStall;
    float w = 1920, h = 1080, u = 1;
    private float insL, insT, insR, insB;
    float padL, padT, padR, padB;

    int screen = MENU;
    private float screenTime;
    private World world;
    World demo;
    private float acc;
    float clock;
    /** Studio splash shown on start-up; tests turn it off. */
    static boolean showSplash = true;
    static final float SPLASH_TIME = 3f;
    float splash = showSplash ? SPLASH_TIME : 0;

    private boolean paused;
    private float endTimer = -1;
    private boolean endIsWin;
    private int resRank, resKills, resLength, resDelta, resCubes, resCoins;
    private boolean resWin, resNewBest;
    private float resTime;

    // Popup dialog
    boolean popup;
    private String popTitle, popText;
    private int popArtType; // 0 none, 1 coins, 2 skin, 3 brawler, 4 box
    private int popArt;
    private int popAction, popArg;
    private boolean popConfirm;
    private float popTime;

    // Touch controls
    private int movePtr = -1;
    private float moveOx, moveOy, moveX, moveY;
    private int atkPtr = -1;
    private float atkOx, atkOy, atkX, atkY, atkMax;
    private int supPtr = -1;
    private float supOx, supOy, supX, supY, supMax;
    private int boostPtr = -1;
    private int pausePtr = -1;
    private float atkCX, atkCY, atkR, supCX, supCY, supR, boostCX, boostCY, boostR;
    private float moveCX, moveCY, moveR, pauseX, pauseY, pauseR, mapX, mapY, mapS;

    private final float[] poly = new float[16];

    public Game(Platform platform) {
        this.host = platform;
        this.profile = new Profile(platform);
        gated = new Platform() {
            @Override
            public void playSound(int id, float volume) {
                if (profile.sound) host.playSound(id, volume);
            }

            @Override
            public int loadInt(String key, int def) {
                return host.loadInt(key, def);
            }

            @Override
            public void saveInt(String key, int value) {
                host.saveInt(key, value);
            }

            @Override
            public void vibrate(int millis) {
                if (profile.vibration) host.vibrate(millis);
            }

            @Override
            public boolean launchPurchase(String productId) {
                return host.launchPurchase(productId);
            }

            @Override
            public String loadString(String key, String def) {
                return host.loadString(key, def);
            }

            @Override
            public void saveString(String key, String value) {
                host.saveString(key, value);
            }

            @Override
            public void requestText(String title, String initial, int maxLength, boolean numeric, TextCallback callback) {
                host.requestText(title, initial, maxLength, numeric, callback);
            }

            @Override
            public void setNetworkDiscovery(boolean on) {
                host.setNetworkDiscovery(on);
            }
        };
        meta = new MetaScreens(this);
        social = new SocialScreens(this);
        friends = new FriendsScreen(this);
        for (int i = 0; i < NET_RING; i++) {
            netLocal[i] = new NetInput();
            netRemote[i] = new NetInput();
            netLocal[i].tick = netRemote[i].tick = -1;
        }
        newDemo();
        if (!profile.onboarded) screen = ONBOARD;
        layout();
    }

    private void newDemo() {
        demo = new World(gated, World.MODE_DEMO, null, null, 1, 9, 350);
        demo.lowGraphics = profile.lowGraphics;
        demo.fx.low = profile.lowGraphics;
        // Let the demo arena develop a bit so the menu starts lively
        for (int i = 0; i < 240; i++) demo.update(STEP);
    }

    // ------------------------------------------------------------------ host API

    public void resize(float width, float height) {
        w = width;
        h = height;
        u = Math.min(h / 1080f, w / 1920f);
        ui.u = u;
        layout();
    }

    public void setInsets(float l, float t, float r, float b) {
        insL = l;
        insT = t;
        insR = r;
        insB = b;
        layout();
    }

    public void frame(float dt, Gfx g) {
        if (g.width() != w || g.height() != h) resize(g.width(), g.height());
        dt = Math.min(dt, 0.1f);
        tick(dt);
        render(g);
    }

    World currentWorld() {
        return world != null ? world : demo;
    }

    int screenId() {
        return screen;
    }

    // ------------------------------------------------------------------ coin purchases

    /** Simulated checkout sheet: -1 closed, else the coin pack being bought. */
    int sheetPack = -1;
    private int sheetPhase; // 0 confirm, 1 processing, 2 done
    private float sheetTime, sheetPhaseTime;
    private boolean sheetClosing;

    // Coins flying into the coin counter after a purchase
    private static final int FLY = 24;
    private final float[] flyX = new float[FLY], flyY = new float[FLY], flyT = new float[FLY];
    private int flyCount;
    private float fade;

    /** Starts buying a coin pack: real Play Billing if the host supports it, else the test checkout. */
    void startPurchase(int pack) {
        if (!profile.canPurchase()) {
            showInfo("ASK A GROWN-UP", "Players under 13 can't buy coins. You can still earn lots of coins by playing, "
                    + "getting knockouts and claiming free gifts!", 1, 0);
            return;
        }
        if (host.launchPurchase(CoinStore.PRODUCT_IDS[pack])) return;
        sheetPack = pack;
        sheetPhase = 0;
        sheetTime = 0;
        sheetPhaseTime = 0;
        sheetClosing = false;
        ui.cancel();
        layout();
    }

    /** Called by the host when a real purchase finishes (or by the test checkout). */
    public void onPurchaseResult(String productId, boolean success) {
        int pack = CoinStore.indexOf(productId);
        if (!success || pack < 0) return;
        profile.coins += CoinStore.COINS[pack];
        profile.save();
        gated.playSound(Platform.SND_VICTORY, 0.8f);
        for (int i = 0; i < FLY; i++) {
            flyX[i] = w / 2 + MathUtil.rand(-160, 160) * u;
            flyY[i] = h / 2 + MathUtil.rand(-90, 90) * u;
            flyT[i] = -i * 0.035f;
        }
        flyCount = FLY;
        layout();
    }

    private void updateSheet(float dt) {
        if (sheetPack >= 0) {
            sheetTime += dt;
            sheetPhaseTime += dt;
            if (sheetPhase == 1 && sheetPhaseTime > 1.4f) {
                sheetPhase = 2;
                sheetPhaseTime = 0;
                gated.playSound(Platform.SND_POWER, 1f);
            } else if (sheetPhase == 2 && sheetPhaseTime > 1.1f && !sheetClosing) {
                String id = CoinStore.PRODUCT_IDS[sheetPack];
                closeSheet();
                onPurchaseResult(id, true);
            }
            if (sheetClosing && sheetPhaseTime > 0.25f) {
                sheetPack = -1;
                layout();
            }
        }
        for (int i = 0; i < flyCount; i++) flyT[i] += dt;
        if (flyCount > 0 && flyT[flyCount - 1] > 1.2f) flyCount = 0;
        if (fade > 0) fade = Math.max(0, fade - dt * 3.5f);
    }

    private void closeSheet() {
        sheetClosing = true;
        sheetPhaseTime = 0;
        ui.cancel();
    }

    private void sheetButton(int id) {
        if (sheetClosing || sheetPhase != 0) return;
        if (id == B_SHEET_BUY) {
            sheetPhase = 1;
            sheetPhaseTime = 0;
            layout();
        } else if (id == B_SHEET_CANCEL) {
            closeSheet();
        }
    }

    private float sheetTop() {
        float sh = Math.min(560 * u, h * 0.62f);
        float k = sheetClosing ? 1f - Math.min(1f, sheetPhaseTime / 0.25f) : Math.min(1f, sheetTime / 0.28f);
        k = 1f - (1f - k) * (1f - k) * (1f - k);
        return h - sh * k;
    }

    private void renderSheet(Gfx g) {
        float sw = Math.min(1100 * u, w - padL - padR);
        float sl = (w - sw) / 2, sr = sl + sw;
        float top = sheetTop();
        float k = sheetClosing ? 1f - Math.min(1f, sheetPhaseTime / 0.25f) : Math.min(1f, sheetTime / 0.28f);
        g.color(MathUtil.withAlpha(0x99000000, k));
        g.fillRect(0, 0, w, h);
        // Sheet body
        g.color(0x55000000);
        g.fillRoundRect(sl, top - 8 * u, sr, h + 60 * u, 36 * u);
        g.color(0xfffbfbfe);
        g.fillRoundRect(sl, top, sr, h + 60 * u, 32 * u);
        g.color(0xffd0d4dc);
        g.fillRoundRect(w / 2 - 50 * u, top + 16 * u, w / 2 + 50 * u, top + 26 * u, 5 * u);
        float pad = 50 * u;
        int pack = sheetPack;
        // Header: app icon, app and item names, price
        float iy = top + 60 * u;
        g.color(0xff3a2fb0);
        g.fillRoundRect(sl + pad, iy, sl + pad + 110 * u, iy + 110 * u, 26 * u);
        g.radial(sl + pad + 55 * u, iy + 55 * u, 60 * u, 0x55ffd23f, 0x00ffd23f);
        ui.coin(g, sl + pad + 55 * u, iy + 55 * u, 70 * u);
        g.color(0xff202124);
        g.text(CoinStore.format(CoinStore.COINS[pack]) + " Coins", sl + pad + 140 * u, iy + 46 * u, 46 * u, Gfx.ALIGN_LEFT, 0, 0);
        g.color(0xff5f6368);
        g.text("Snake Brawl  •  " + CoinStore.NAMES[pack], sl + pad + 140 * u, iy + 92 * u, 32 * u, Gfx.ALIGN_LEFT, 0, 0);
        g.color(0xff202124);
        g.text(CoinStore.PRICES[pack], sr - pad, iy + 46 * u, 46 * u, Gfx.ALIGN_RIGHT, 0, 0);
        g.color(0xff5f6368);
        g.text("+ tax if applicable", sr - pad, iy + 92 * u, 26 * u, Gfx.ALIGN_RIGHT, 0, 0);
        float dy = iy + 140 * u;
        g.color(0xffe3e5ea);
        g.fillRect(sl + pad, dy, sr - pad, dy + 3 * u);
        // Payment method row
        float py = dy + 30 * u;
        g.color(0xffe8f0fe);
        g.fillRoundRect(sl + pad, py, sl + pad + 92 * u, py + 62 * u, 12 * u);
        g.color(0xff1a73e8);
        g.fillRoundRect(sl + pad + 14 * u, py + 14 * u, sl + pad + 78 * u, py + 48 * u, 6 * u);
        g.color(0xfffbbc04);
        g.fillRoundRect(sl + pad + 22 * u, py + 22 * u, sl + pad + 38 * u, py + 34 * u, 3 * u);
        g.color(0xff202124);
        g.text("Test card  •••• 4242", sl + pad + 115 * u, py + 30 * u, 32 * u, Gfx.ALIGN_LEFT, 0, 0);
        g.color(0xff5f6368);
        g.text("Simulated payment method", sl + pad + 115 * u, py + 62 * u, 26 * u, Gfx.ALIGN_LEFT, 0, 0);
        // Test mode notice
        float ny = py + 92 * u;
        g.color(0xfffff4e0);
        g.fillRoundRect(sl + pad, ny, sr - pad, ny + 56 * u, 14 * u);
        g.color(0xffb06000);
        g.text("TEST MODE: no real money is charged", w / 2, ny + 38 * u, 28 * u, Gfx.ALIGN_CENTER, 0, 0);

        float by = ny + 80 * u;
        float bh = 100 * u;
        if (sheetPhase == 0) {
            Ui.Btn b = ui.find(B_SHEET_BUY);
            boolean pr = b != null && ui.pressed == B_SHEET_BUY;
            g.color(pr ? 0xff1557b0 : 0xff1a73e8);
            g.fillRoundRect(sl + pad, by, sr - pad, by + bh, bh / 2);
            g.color(0xffffffff);
            g.text("Buy  " + CoinStore.PRICES[pack], w / 2, by + bh * 0.64f, 44 * u, Gfx.ALIGN_CENTER, 0, 0);
        } else if (sheetPhase == 1) {
            float cx = w / 2, cy = by + bh / 2;
            g.color(0xffe3e5ea);
            g.strokeCircle(cx - 170 * u, cy, 28 * u, 8 * u);
            g.color(0xff1a73e8);
            g.arc(cx - 170 * u, cy, 28 * u, clock * 400f, 100, 8 * u);
            g.color(0xff202124);
            g.text("Processing payment...", cx - 120 * u, cy + 14 * u, 38 * u, Gfx.ALIGN_LEFT, 0, 0);
        } else {
            float cx = w / 2, cy = by + bh / 2;
            float pop = Math.min(1f, sheetPhaseTime / 0.2f);
            g.color(0xff1e8e3e);
            g.fillCircle(cx - 190 * u, cy, 34 * u * pop);
            g.color(0xffffffff);
            g.line(cx - 206 * u, cy, cx - 194 * u, cy + 12 * u, 7 * u * pop);
            g.line(cx - 194 * u, cy + 12 * u, cx - 172 * u, cy - 12 * u, 7 * u * pop);
            g.color(0xff1e8e3e);
            g.text("Payment successful", cx - 135 * u, cy + 14 * u, 40 * u, Gfx.ALIGN_LEFT, 0, 0);
        }
    }

    private void renderCoinFly(Gfx g) {
        if (flyCount == 0) return;
        float tx = padL + 290 * u + 68 * u, ty = padT + 14 * u + 45 * u;
        if (screen == SHOP || screen == BRAWLERS) {
            tx = w - padR - 120 * u;
            ty = padT + 56 * u;
        }
        for (int i = 0; i < flyCount; i++) {
            float t = flyT[i];
            if (t < 0 || t > 1f) continue;
            float e = t * t * (3 - 2 * t);
            float x = flyX[i] + (tx - flyX[i]) * e;
            float y = flyY[i] + (ty - flyY[i]) * e - MathUtil.sin(t * MathUtil.PI) * 160 * u;
            ui.coin(g, x, y, 54 * u * (1f - 0.4f * e));
        }
    }

    /** Advances the game without drawing (also used by the desktop test harness). */
    void tick(float dt) {
        clock += dt;
        if (splash > 0) {
            splash = Math.max(0, splash - dt);
            if (splash > 0) return;
        }
        screenTime += dt;
        popTime += dt;
        updateSheet(dt);
        ui.update(dt);
        update(dt);
    }

    /** Called when the app goes to the background. */
    public void onPause() {
        releaseControls();
        profile.save();
        if (screen == PLAY && endTimer < 0) {
            paused = true;
            layout();
        }
    }

    /** Returns true if the back press was handled. */
    public boolean onBack() {
        if (splash > 0) return true;
        if (sheetPack >= 0) {
            if (sheetPhase == 0) closeSheet();
            return true;
        }
        if (popup) {
            closePopup();
            return true;
        }
        switch (screen) {
            case PLAY:
                if (endTimer >= 0) return true;
                paused = !paused;
                releaseControls();
                layout();
                return true;
            case BRAWLERS:
            case SHOP:
            case SETTINGS:
            case CLUB:
                setScreen(MENU);
                return true;
            case FRIENDS:
                friends.cancel();
                setScreen(MENU);
                return true;
            case ONBOARD:
                return true;
            case RESULT:
                goMenu();
                return true;
            default:
                return false;
        }
    }

    // ------------------------------------------------------------------ flow

    void setScreen(int s) {
        if (s != screen) fade = 0.55f;
        screen = s;
        screenTime = 0;
        ui.cancel();
        ui.resetScroll();
        releaseControls();
        layout();
    }

    private void goMenu() {
        endNet();
        world = null;
        paused = false;
        newDemo();
        setScreen(MENU);
    }

    static int worldMode(int profileMode) {
        return profileMode == 0 ? World.MODE_SHOWDOWN : (profileMode == 2 ? World.MODE_DUO : World.MODE_ENDLESS);
    }

    /** Your Duo partner: a member of your club, or a friendly bot if you have no club. */
    String partnerName() {
        return profile.club >= 0 ? Clubs.memberName(profile, 0) : "Buddy";
    }

    void startGame() {
        Brawler b = Brawler.ALL[profile.selected];
        int wm = worldMode(profile.mode);
        world = new World(gated, wm, b, profile.palette(), profile.levels[b.id], wm == World.MODE_ENDLESS ? 11 : 9, profile.trophies);
        world.player.name = profile.displayName();
        if (wm == World.MODE_DUO) {
            Snake mate = world.mateOf(world.player);
            if (mate != null) {
                mate.name = partnerName();
                if (mate.level < profile.levels[b.id]) mate.level = profile.levels[b.id];
            }
        }
        configureWorld();
    }

    /** Starts a Wi-Fi match. Both phones call this with the same seed and mode. */
    void startNetGame(NetSession session, NetSession.Msg friend, long seed, int lobbyMode) {
        net = session;
        netHost = session.host;
        netTick = 0;
        netStall = 0;
        netIn.clear();
        for (int i = 0; i < NET_RING; i++) netLocal[i].tick = netRemote[i].tick = -1;
        for (int i = 0; i < netHashTick.length; i++) netHashTick[i] = -1;
        // The first few ticks have no input yet on either phone
        for (int t = 0; t < NET_DELAY; t++) {
            NetInput li = netLocal[t];
            li.clear();
            li.tick = t;
            NetInput ri = netRemote[t];
            ri.clear();
            ri.tick = t;
        }
        Brawler mine = Brawler.ALL[profile.selected];
        Brawler theirs = Brawler.ALL[Profile.clamp(friend.brawler, 0, Brawler.ALL.length - 1)];
        int[] myPal = profile.palette();
        int[] theirPal = friend.palette != null && friend.palette.length >= 2 ? friend.palette : new int[]{theirs.color1, theirs.color2};
        int myLvl = profile.levels[mine.id], theirLvl = Profile.clamp(friend.level, 1, Brawler.MAX_LEVEL);
        int wm = lobbyMode == FriendsScreen.VERSUS ? World.MODE_SHOWDOWN : World.MODE_DUO;
        int trophies = netHost ? profile.trophies : friend.trophies;
        java.util.Random prev = MathUtil.RNG;
        MathUtil.RNG = new java.util.Random(seed);
        try {
            if (netHost) {
                world = new World(gated, wm, mine, myPal, myLvl, theirs, theirPal, theirLvl, 0, 8, trophies);
            } else {
                world = new World(gated, wm, theirs, theirPal, theirLvl, mine, myPal, myLvl, 1, 8, trophies);
            }
        } finally {
            MathUtil.RNG = prev;
        }
        world.snakes[netHost ? 0 : 1].name = profile.displayName();
        world.snakes[netHost ? 1 : 0].name = friend.name;
        world.banner(wm == World.MODE_DUO ? "TEAM UP WITH " + friend.name.toUpperCase() + "!" : "BEAT " + friend.name.toUpperCase() + "!");
        configureWorld();
    }

    /** Snake controlled by the friend's phone. */
    private Snake netFriend() {
        return world.snakes[netHost ? 1 : 0];
    }

    private void netUpdate(float dt) {
        NetSession.Msg m;
        while (net != null && (m = net.poll()) != null) {
            if (m.type == NetSession.M_INPUT) {
                NetInput ri = netRemote[m.input.tick % NET_RING];
                ri.copyFrom(m.input);
            } else if (m.type == NetSession.M_HASH) {
                int k = (m.tick / NET_HASH_EVERY) % netHashTick.length;
                if (netHashTick[k] == m.tick && netHashVal[k] != m.hash) {
                    dropNet("OUT OF SYNC", "The two phones got out of sync, so " + netFriend().name + " is now played by a bot.");
                    return;
                }
            }
        }
        if (net != null && net.state == NetSession.ST_CLOSED) {
            dropNet("FRIEND LEFT", (net.closeReason != null ? net.closeReason : "Connection lost")
                    + ". Their snake is now played by a bot.");
            return;
        }
        acc += dt;
        int steps = 0;
        while (acc >= STEP && steps < 6) {
            NetInput host, guest;
            NetInput remote = netRemote[netTick % NET_RING];
            if (remote.tick != netTick) {
                // Waiting for the friend's controls for this tick
                netStall += dt;
                acc = Math.min(acc, STEP * 4);
                break;
            }
            netStall = 0;
            applyControls();
            // Controls entered now take effect NET_DELAY ticks later on both phones
            int future = netTick + NET_DELAY;
            NetInput li = netLocal[future % NET_RING];
            li.copyFrom(netIn);
            li.tick = future;
            Snake me = world.player;
            if (me == null || !me.alive || screen != PLAY) {
                li.clear();
                li.tick = future;
            }
            net.sendInput(li);
            netIn.attack = NetInput.NONE;
            netIn.steer = false;
            NetInput local = netLocal[netTick % NET_RING];
            host = netHost ? local : remote;
            guest = netHost ? remote : local;
            world.netStep(STEP, host, guest);
            if (netTick % NET_HASH_EVERY == 0) {
                int k = (netTick / NET_HASH_EVERY) % netHashTick.length;
                netHashTick[k] = netTick;
                netHashVal[k] = world.stateHash();
                net.sendHash(netTick, netHashVal[k]);
            }
            netTick++;
            acc -= STEP;
            steps++;
        }
        if (steps == 6) acc = 0;
        world.updateCamera(dt, w, h);
    }

    /** Continues the match without the friend's phone: their snake becomes a bot. */
    private void dropNet(String title, String text) {
        if (net == null) return;
        Snake f = netFriend();
        net.close();
        net = null;
        acc = 0;
        if (world != null) {
            world.makeBot(f);
            world.banner(title);
        }
        if (screen == PLAY && f.alive) showInfo(title, text, 0, 0);
    }

    private void endNet() {
        if (net != null) net.close();
        net = null;
    }

    private void configureWorld() {
        world.showDamage = profile.damageNumbers;
        world.lowGraphics = profile.lowGraphics;
        world.fx.low = profile.lowGraphics;
        world.zoomMult = profile.camera == 0 ? 1.15f : (profile.camera == 2 ? 0.85f : 1f);
        world.updateCamera(1f, w, h);
        world.zoom = h / 900f * world.zoomMult;
        paused = false;
        endTimer = -1;
        acc = 0;
        setScreen(PLAY);
    }

    private void finishGame(boolean win) {
        Snake p = world.player;
        resNet = net != null || world.humans > 1;
        resWin = win;
        resKills = p.kills;
        resLength = (int) p.mass;
        resCubes = p.cubes;
        resTime = world.matchTime;
        if (world.mode == World.MODE_SHOWDOWN) {
            resRank = win ? 1 : Math.max(1, p.rank);
            resDelta = TROPHY_TABLE[Profile.clamp(resRank - 1, 0, TROPHY_TABLE.length - 1)];
            resCoins = RANK_COINS[Profile.clamp(resRank - 1, 0, RANK_COINS.length - 1)] + p.kills * 8 + Math.min(30, resLength / 15);
            if (win) profile.wins++;
        } else if (world.mode == World.MODE_DUO) {
            resRank = win ? 1 : Math.max(1, p.rank);
            int[] duoTrophies = {9, 6, 3, 0, -2};
            int[] duoCoins = {50, 36, 24, 14, 6};
            int ri = Profile.clamp(resRank - 1, 0, 4);
            resDelta = duoTrophies[ri];
            resCoins = duoCoins[ri] + p.kills * 8 + Math.min(30, resLength / 15);
            if (win) profile.wins++;
        } else {
            resRank = 0;
            resDelta = Math.min(5, p.kills);
            resCoins = p.kills * 8 + Math.min(60, resLength / 12);
        }
        int before = profile.trophies;
        profile.trophies = Math.max(0, profile.trophies + resDelta);
        profile.bestTrophies = Math.max(profile.bestTrophies, profile.trophies);
        resDelta = profile.trophies - before;
        resNewBest = resLength > profile.bestLen;
        if (resNewBest) profile.bestLen = resLength;
        profile.coins += resCoins;
        profile.totalKills += p.kills;
        profile.games++;
        profile.hints++;
        profile.save();
        gated.playSound(win ? Platform.SND_VICTORY : Platform.SND_DEFEAT, 1f);
        setScreen(RESULT);
    }

    // ------------------------------------------------------------------ popups

    void showInfo(String title, String text, int artType, int art) {
        openPopup(title, text, artType, art, ACT_NONE, 0, false);
    }

    void confirm(String title, String text, int artType, int art, int action, int arg) {
        openPopup(title, text, artType, art, action, arg, true);
    }

    private void openPopup(String title, String text, int artType, int art, int action, int arg, boolean confirm) {
        popup = true;
        popTitle = title;
        popText = text;
        popArtType = artType;
        popArt = art;
        popAction = action;
        popArg = arg;
        popConfirm = confirm;
        popTime = 0;
        ui.cancel();
        layout();
    }

    private void closePopup() {
        popup = false;
        ui.cancel();
        layout();
    }

    private void popupButton(int id) {
        int action = popAction, arg = popArg;
        closePopup();
        if (id == B_YES) {
            if (action == ACT_JOIN_CLUB || action == ACT_LEAVE_CLUB) social.perform(action, arg);
            else meta.perform(action, arg);
        }
    }

    private void renderPopup(Gfx g) {
        float t = Math.min(1f, popTime * 5f);
        g.color(MathUtil.withAlpha(0xcc0a0c22, t));
        g.fillRect(0, 0, w, h);
        float pw = Math.min(980 * u, w - padL - padR - 40 * u), ph = 600 * u;
        float cx = w / 2, cy = h / 2 - 30 * u;
        g.save();
        g.translate(cx, cy);
        g.scale(0.7f + 0.3f * (1f - (1f - t) * (1f - t)));
        float l = -pw / 2, top = -ph / 2;
        ui.panel(g, l, top, l + pw, top + ph, 0xff2a2f66);
        g.color(0xff3a3f80);
        g.fillRoundRect(l, top, l + pw, top + 100 * u, 26 * u);
        g.color(0xffffd23f);
        g.text(popTitle, 0, top + 70 * u, Ui.fit(g, popTitle, 60 * u, pw * 0.9f), Gfx.ALIGN_CENTER, 7 * u, Ui.INK);
        float artY = top + 200 * u;
        switch (popArtType) {
            case 1:
                for (int k = 0; k < 3; k++) ui.coin(g, -70 * u + k * 70 * u, artY + MathUtil.sin(clock * 4 + k) * 8 * u, 90 * u);
                break;
            case 2: {
                Brawler b = Brawler.ALL[profile.selected];
                ui.snakeArt(g, b, Skin.ALL[popArt].paletteFor(b), 0, artY, 1.3f * u, clock);
                break;
            }
            case 3: {
                Brawler b = Brawler.ALL[popArt];
                ui.snakeArt(g, b, new int[]{b.color1, b.color2}, 0, artY, 1.3f * u, clock);
                break;
            }
            case 4:
                MetaScreens.drawBox(g, 0, artY, 150 * u, popArt == 1, clock, u);
                break;
            default:
                artY = top + 110 * u;
                break;
        }
        float ty = popArtType == 0 ? top + 140 * u : top + 300 * u;
        g.save();
        ui.wrap(g, popText, l + 60 * u, l + pw - 60 * u, ty, 36 * u, 0xffffffff, top + ph - 150 * u);
        g.restore();
        g.restore();
        for (int i = 0; i < ui.count; i++) ui.button(g, ui.btns[i]);
    }

    // ------------------------------------------------------------------ update

    private void update(float dt) {
        if (screen == FRIENDS) friends.update(dt);
        if (screen != PLAY && screen != RESULT) {
            demo.update(dt);
            demo.updateCamera(dt, w, h);
            return;
        }
        if (world == null) return;
        if (net != null) {
            netUpdate(dt);
        } else {
            if (screen == PLAY && paused) return;
            localUpdate(dt);
        }
        checkMatchEnd(dt);
    }

    private void localUpdate(float dt) {
        acc += dt;
        int steps = 0;
        while (acc >= STEP && steps < 5) {
            applyControls();
            world.update(STEP);
            acc -= STEP;
            steps++;
        }
        if (steps == 5) acc = 0;
        world.updateCamera(dt, w, h);
    }

    private void checkMatchEnd(float dt) {
        if (screen == PLAY) {
            Snake p = world.player;
            Snake mate = world.mateOf(p);
            boolean teamAlive = p.alive || (mate != null && mate.alive);
            if (!p.alive && movePtr >= 0) releaseControls();
            if (endTimer < 0) {
                if (!teamAlive) {
                    endTimer = 1.8f;
                    endIsWin = false;
                    releaseControls();
                } else if (world.mode == World.MODE_DUO && world.aliveTeams() <= 1) {
                    endTimer = 1.6f;
                    endIsWin = true;
                    p.rank = 1;
                    world.banner("VICTORY!");
                    releaseControls();
                } else if (world.mode == World.MODE_SHOWDOWN && world.aliveCount <= 1) {
                    endTimer = 1.6f;
                    endIsWin = true;
                    p.rank = 1;
                    world.banner("VICTORY!");
                    releaseControls();
                }
            } else {
                endTimer -= dt;
                if (endTimer <= 0) finishGame(endIsWin);
            }
        }
    }

    private void applyControls() {
        Snake p = world.player;
        if (p == null || !p.alive || screen != PLAY) {
            world.aimActive = false;
            return;
        }
        if (movePtr >= 0) {
            float dx = moveX - moveOx, dy = moveY - moveOy;
            if (dx * dx + dy * dy > (10 * u) * (10 * u)) steer(p, (float) Math.atan2(dy, dx));
        }
        if (net != null) netIn.boost = boostPtr >= 0;
        else p.boostInput = boostPtr >= 0;

        world.aimActive = false;
        float dead = atkR * 0.28f;
        if (atkPtr >= 0) {
            float dx = atkX - atkOx, dy = atkY - atkOy;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (atkMax > dead && d > dead * 0.6f) {
                world.aimActive = true;
                world.aimSuper = false;
                world.aimAng = (float) Math.atan2(dy, dx);
                world.aimDist = Math.min(1f, d / atkR) * p.type.range;
            }
        } else if (supPtr >= 0) {
            float dx = supX - supOx, dy = supY - supOy;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (supMax > dead && d > dead * 0.6f) {
                world.aimActive = true;
                world.aimSuper = true;
                world.aimAng = (float) Math.atan2(dy, dx);
                world.aimDist = Math.min(1f, d / supR) * p.type.superRange;
            }
        }
    }

    private void releaseAttack(boolean sup) {
        if (world == null || world.player == null || !world.player.alive) return;
        Snake p = world.player;
        float ox = sup ? supOx : atkOx, oy = sup ? supOy : atkOy;
        float x = sup ? supX : atkX, y = sup ? supY : atkY;
        float max = sup ? supMax : atkMax;
        float rad = sup ? supR : atkR;
        float dead = atkR * 0.28f;
        float dx = x - ox, dy = y - oy;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        float range = sup ? p.type.superRange : p.type.range;
        float ang, dist;
        if (max > dead) {
            if (d < dead * 0.6f) return; // dragged back to the middle: cancel
            ang = (float) Math.atan2(dy, dx);
            dist = Math.min(1f, d / rad) * range;
        } else if (profile.autoAim && world.findAim(p, Math.max(range, p.type.range), p.type.projSpeed)) {
            ang = world.aimOutAng;
            dist = world.aimOutDist;
        } else {
            ang = p.ang;
            dist = range * 0.8f;
        }
        if (net != null) {
            netIn.attack = sup ? NetInput.SUPER : NetInput.ATTACK;
            netIn.atkAng = ang;
            netIn.atkDist = dist;
        } else if (sup) {
            p.trySuper(world, ang, dist);
        } else {
            p.tryAttack(world, ang, dist);
        }
    }

    private void steer(Snake p, float ang) {
        if (net != null) {
            netIn.steer = true;
            netIn.ang = ang;
        } else {
            p.targetAng = ang;
        }
    }

    private void releaseControls() {
        movePtr = atkPtr = supPtr = boostPtr = pausePtr = -1;
        ui.cancel();
        if (world != null) {
            world.aimActive = false;
            if (world.player != null) world.player.boostInput = false;
        }
    }

    // ------------------------------------------------------------------ input

    private boolean onMoveSide(float x) {
        return profile.leftHanded ? x >= w * 0.5f : x <= w * 0.5f;
    }

    public void touchDown(int id, float x, float y) {
        if (splash > 0) return;
        if (screen == PLAY && !paused && endTimer < 0 && !popup) {
            if (MathUtil.dist2(x, y, pauseX, pauseY) < (pauseR * 1.5f) * (pauseR * 1.5f)) {
                pausePtr = id;
                return;
            }
            Snake p = world.player;
            if (supPtr < 0 && MathUtil.dist2(x, y, supCX, supCY) < (supR * 1.35f) * (supR * 1.35f)) {
                supPtr = id;
                supOx = supCX;
                supOy = supCY;
                supX = x;
                supY = y;
                supMax = 0;
                return;
            }
            if (boostPtr < 0 && MathUtil.dist2(x, y, boostCX, boostCY) < (boostR * 1.4f) * (boostR * 1.4f)) {
                boostPtr = id;
                return;
            }
            if (atkPtr < 0 && !onMoveSide(x)) {
                atkPtr = id;
                boolean onStick = MathUtil.dist2(x, y, atkCX, atkCY) < (atkR * 1.9f) * (atkR * 1.9f);
                atkOx = onStick ? atkCX : x;
                atkOy = onStick ? atkCY : y;
                atkX = x;
                atkY = y;
                atkMax = 0;
                return;
            }
            if (movePtr < 0 && onMoveSide(x)) {
                movePtr = id;
                float minX = profile.leftHanded ? w * 0.5f + moveR * 0.5f : padL + moveR;
                float maxX = profile.leftHanded ? w - padR - moveR : w * 0.5f - moveR * 0.5f;
                moveOx = MathUtil.clamp(x, minX, maxX);
                moveOy = MathUtil.clamp(y, padT + moveR + 80 * u, h - padB - moveR * 0.6f);
                moveX = x;
                moveY = y;
                if (p != null && p.alive) {
                    float dx = x - moveOx, dy = y - moveOy;
                    if (dx * dx + dy * dy > 100 * u * u) steer(p, (float) Math.atan2(dy, dx));
                }
            }
            return;
        }
        ui.down(id, x, y);
    }

    public void touchMove(int id, float x, float y) {
        if (id == movePtr) {
            moveX = x;
            moveY = y;
            // Drag the floating stick along when the finger goes past its rim
            float dx = x - moveOx, dy = y - moveOy;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > moveR * 1.4f) {
                moveOx = x - dx / d * moveR * 1.4f;
                moveOy = y - dy / d * moveR * 1.4f;
            }
        } else if (id == atkPtr) {
            atkX = x;
            atkY = y;
            atkMax = Math.max(atkMax, MathUtil.dist(x, y, atkOx, atkOy));
        } else if (id == supPtr) {
            supX = x;
            supY = y;
            supMax = Math.max(supMax, MathUtil.dist(x, y, supOx, supOy));
        } else {
            ui.move(id, x, y);
        }
    }

    public void touchUp(int id, float x, float y) {
        if (id == movePtr) {
            movePtr = -1;
            return;
        }
        if (id == boostPtr) {
            boostPtr = -1;
            return;
        }
        if (id == atkPtr) {
            atkX = x;
            atkY = y;
            releaseAttack(false);
            atkPtr = -1;
            if (world != null) world.aimActive = false;
            return;
        }
        if (id == supPtr) {
            supX = x;
            supY = y;
            releaseAttack(true);
            supPtr = -1;
            if (world != null) world.aimActive = false;
            return;
        }
        if (id == pausePtr) {
            pausePtr = -1;
            if (MathUtil.dist2(x, y, pauseX, pauseY) < (pauseR * 1.8f) * (pauseR * 1.8f)) {
                gated.playSound(Platform.SND_CLICK, 0.7f);
                paused = true;
                releaseControls();
                layout();
            }
            return;
        }
        int b = ui.up(id, x, y);
        if (b >= 0) {
            gated.playSound(Platform.SND_CLICK, 0.7f);
            onButton(b);
        }
    }

    public void touchCancelAll() {
        releaseControls();
    }

    private void onButton(int id) {
        if (sheetPack >= 0) {
            sheetButton(id);
            return;
        }
        if (popup) {
            popupButton(id);
            return;
        }
        switch (id) {
            case B_PLAY:
                startGame();
                break;
            case B_AGAIN:
                if (resNet) {
                    goMenu();
                    setScreen(FRIENDS);
                } else {
                    startGame();
                }
                break;
            case B_MODE:
                profile.mode = profile.mode == 0 ? 2 : (profile.mode == 2 ? 1 : 0);
                profile.save();
                layout();
                break;
            case B_BRAWLERS:
                meta.viewBrawler = profile.selected;
                setScreen(BRAWLERS);
                break;
            case B_SHOP:
                setScreen(SHOP);
                break;
            case B_SETTINGS:
                setScreen(SETTINGS);
                break;
            case B_CLUB:
                setScreen(CLUB);
                break;
            case B_FRIENDS:
                setScreen(FRIENDS);
                break;
            case B_BACK:
                setScreen(MENU);
                break;
            case B_MENU:
            case B_QUIT:
                goMenu();
                break;
            case B_RESUME:
                paused = false;
                layout();
                break;
            default:
                if (SocialScreens.handles(id)) social.onButton(id);
                else if (FriendsScreen.handles(id)) friends.onButton(id);
                else meta.onButton(id);
                break;
        }
    }

    // ------------------------------------------------------------------ layout

    void layout() {
        padL = Math.max(28 * u, insL + 8 * u);
        padR = Math.max(28 * u, insR + 8 * u);
        padT = Math.max(20 * u, insT + 4 * u);
        padB = Math.max(20 * u, insB + 4 * u);

        float ss = profile.stickSize == 0 ? 0.82f : (profile.stickSize == 2 ? 1.2f : 1f);
        atkR = 92 * u * ss;
        atkCX = w - padR - 175 * u * ss;
        atkCY = h - padB - 175 * u * ss;
        supR = 70 * u * ss;
        supCX = atkCX - 225 * u * ss;
        supCY = atkCY + 55 * u * ss;
        boostR = 62 * u * ss;
        boostCX = atkCX + 25 * u * ss;
        boostCY = atkCY - 215 * u * ss;
        moveR = 105 * u * ss;
        moveCX = padL + 200 * u * ss;
        moveCY = h - padB - 190 * u * ss;
        if (profile.leftHanded) {
            atkCX = w - atkCX;
            supCX = w - supCX;
            boostCX = w - boostCX;
            moveCX = w - moveCX;
        }
        pauseR = 38 * u;
        pauseX = padL + 44 * u;
        pauseY = padT + 44 * u;
        mapS = 210 * u;
        mapX = w - padR - mapS;
        mapY = padT;

        ui.clear();
        if (sheetPack >= 0) {
            if (sheetPhase == 0) {
                ui.add(B_SHEET_CANCEL, 0, 0, w, h, null, null, 0);
                float sw = Math.min(1100 * u, w - padL - padR);
                float sl = (w - sw) / 2;
                float sh = Math.min(560 * u, h * 0.62f);
                ui.add(B_SHEET_BUY - 1000, sl, h - sh, sl + sw, h, null, null, 0); // swallows taps on the sheet
                float by = h - sh + 60 * u + 140 * u + 30 * u + 92 * u + 80 * u;
                ui.add(B_SHEET_BUY, sl + 50 * u, by, sl + sw - 50 * u, by + 100 * u, null, null, 0);
            }
            return;
        }
        if (popup) {
            float cx = w / 2, by = h / 2 - 30 * u + 300 * u - 40 * u;
            if (popConfirm) {
                ui.add(B_NO, cx - 380 * u, by - 110 * u, cx - 30 * u, by, "NO", null, 0xffff5a5a);
                ui.add(B_YES, cx + 30 * u, by - 110 * u, cx + 380 * u, by, "YES", null, 0xff4ad04a);
            } else {
                ui.add(B_OK, cx - 200 * u, by - 110 * u, cx + 200 * u, by, "OK", null, 0xff4ad04a);
            }
            return;
        }
        switch (screen) {
            case MENU: {
                float bw = 400 * u, bh = 150 * u;
                float r = w - padR - 30 * u, b = h - padB - 30 * u;
                ui.add(B_PLAY, r - bw, b - bh, r, b, "PLAY", null, 0xffffc928);
                String[] modeNames = {"SHOWDOWN", "ENDLESS", "DUO SHOWDOWN"};
                String[] modeSubs = {"Last snake standing", "Grow forever", "You + " + partnerName() + " vs 4 teams"};
                int[] modeCols = {0xff3fa0ff, 0xffb35cff, 0xffff7a2e};
                ui.add(B_MODE, r - bw - 30 * u - 430 * u, b - bh, r - bw - 30 * u, b,
                        modeNames[profile.mode], modeSubs[profile.mode], modeCols[profile.mode]);
                float pl = padL + 30 * u;
                ui.add(B_BRAWLERS, pl, b - 110 * u, pl + 470 * u, b, "BRAWLERS", null, 0xff4ad04a);
                ui.add(B_SHOP, pl, padT + 200 * u, pl + 300 * u, padT + 330 * u, "SHOP", "Skins & boxes", 0xffff5ab5);
                String club = Clubs.name(profile);
                ui.add(B_CLUB, pl, padT + 360 * u, pl + 300 * u, padT + 490 * u, "CLUB", club != null ? club : "Join a club!", 0xff3fb6a8);
                ui.add(B_FRIENDS, r - bw - 30 * u - 430 * u, b - bh - 150 * u, r - bw - 30 * u, b - bh - 30 * u, "FRIENDS",
                        "Play together on Wi-Fi", 0xff6a5cff);
                ui.add(B_SETTINGS, w - padR - 120 * u, padT + 10 * u, w - padR - 10 * u, padT + 120 * u, null, null, 0xff8a8fb8);
                break;
            }
            case PLAY: {
                if (paused) {
                    float cx = w / 2, cy = h / 2;
                    ui.add(B_RESUME, cx - 220 * u, cy - 30 * u, cx + 220 * u, cy + 100 * u, "RESUME", null, 0xff4ad04a);
                    ui.add(B_QUIT, cx - 220 * u, cy + 130 * u, cx + 220 * u, cy + 250 * u, "QUIT", null, 0xffff5a5a);
                }
                break;
            }
            case RESULT: {
                float cx = w / 2, b = h - padB - 40 * u;
                ui.add(B_MENU, cx - 470 * u, b - 130 * u, cx - 30 * u, b, "MENU", null, 0xff3fa0ff);
                ui.add(B_AGAIN, cx + 30 * u, b - 130 * u, cx + 470 * u, b, resNet ? "FRIENDS" : "PLAY AGAIN", null, 0xffffc928);
                break;
            }
            case ONBOARD:
            case CLUB:
                if (social != null) social.layout();
                break;
            case FRIENDS:
                if (friends != null) friends.layout();
                break;
            default:
                meta.layout();
                break;
        }
    }

    // ------------------------------------------------------------------ render

    private void renderSplash(Gfx g) {
        float t = SPLASH_TIME - splash;
        float alpha = MathUtil.clamp(splash / 0.45f, 0, 1);
        int a = (int) (alpha * 255) << 24;
        g.vertical(0, 0, w, h, (0xff1a1450 & 0xffffff) | a, (0xff06071a & 0xffffff) | a);
        if (alpha <= 0) return;
        float cx = w / 2, cy = h * 0.42f;
        float pop = MathUtil.clamp(t / 0.5f, 0, 1);
        float sc = pop < 1 ? 0.6f + 0.5f * pop - 0.1f * pop * pop : 1f;
        g.radial(cx, cy, 520 * u, MathUtil.withAlpha(0xffffc94a, 0.28f * alpha), 0x00ffc94a);
        // Rotating rays
        g.save();
        g.translate(cx, cy);
        g.rotate(clock * 14f);
        g.color(MathUtil.withAlpha(0xffffffff, 0.05f * alpha));
        float[] ray = splashRay;
        for (int k = 0; k < 12; k++) {
            float a0 = k * MathUtil.TAU / 12f;
            ray[0] = 0;
            ray[1] = 0;
            ray[2] = MathUtil.cos(a0 - 0.1f) * 900 * u;
            ray[3] = MathUtil.sin(a0 - 0.1f) * 900 * u;
            ray[4] = MathUtil.cos(a0 + 0.1f) * 900 * u;
            ray[5] = MathUtil.sin(a0 + 0.1f) * 900 * u;
            g.fillPoly(ray, 3);
        }
        g.restore();
        g.save();
        g.translate(cx, cy);
        g.scale(sc);
        g.translate(-cx, -cy);
        // Emblem: a coiled snake in a golden ring
        float er = 120 * u;
        g.color(MathUtil.withAlpha(Ui.INK, alpha));
        g.fillCircle(cx, cy - 150 * u, er + 12 * u);
        g.radial(cx, cy - 170 * u, er, MathUtil.withAlpha(0xffffe27a, alpha), MathUtil.withAlpha(0xffe09a12, alpha));
        g.color(MathUtil.withAlpha(0xff2a1a5c, alpha));
        g.fillCircle(cx, cy - 150 * u, er * 0.8f);
        for (int k = 0; k < 14; k++) {
            float ang = clock * 2.2f + k * 0.42f;
            float rr = er * (0.55f - k * 0.022f);
            float sx = cx + MathUtil.cos(ang) * rr, sy = cy - 150 * u + MathUtil.sin(ang) * rr;
            g.color(MathUtil.withAlpha(k % 2 == 0 ? 0xff5be05b : 0xff38b838, alpha));
            g.fillCircle(sx, sy, (16 - k * 0.6f) * u);
            if (k == 0) {
                g.color(MathUtil.withAlpha(0xffffffff, alpha));
                g.fillCircle(sx, sy - 5 * u, 6 * u);
                g.color(MathUtil.withAlpha(0xff14142a, alpha));
                g.fillCircle(sx, sy - 5 * u, 3 * u);
            }
        }
        g.color(MathUtil.withAlpha(0xffffd23f, alpha));
        g.text("RanEddie", cx, cy + 90 * u, 150 * u, Gfx.ALIGN_CENTER, 14 * u, MathUtil.withAlpha(Ui.INK, alpha));
        float gl = MathUtil.clamp((t - 0.45f) / 0.4f, 0, 1);
        g.color(MathUtil.withAlpha(0xffffffff, alpha * gl));
        g.text("G A M E S", cx, cy + 175 * u, 64 * u, Gfx.ALIGN_CENTER, 8 * u, MathUtil.withAlpha(Ui.INK, alpha * gl));
        g.restore();
        // Loading bar
        float bw = 520 * u, by = h * 0.86f;
        float prog = MathUtil.clamp(t / (SPLASH_TIME - 0.5f), 0, 1);
        g.color(MathUtil.withAlpha(Ui.INK, alpha));
        g.fillRoundRect(cx - bw / 2 - 6 * u, by - 6 * u, cx + bw / 2 + 6 * u, by + 30 * u, 18 * u);
        g.color(MathUtil.withAlpha(0xff2a2e5a, alpha));
        g.fillRoundRect(cx - bw / 2, by, cx + bw / 2, by + 24 * u, 12 * u);
        if (prog > 0.03f) {
            g.color(MathUtil.withAlpha(0xffffc928, alpha));
            g.fillRoundRect(cx - bw / 2, by, cx - bw / 2 + bw * prog, by + 24 * u, 12 * u);
            g.color(MathUtil.withAlpha(0x66ffffff, alpha));
            g.fillRoundRect(cx - bw / 2 + 6 * u, by + 4 * u, cx - bw / 2 + bw * prog - 6 * u, by + 10 * u, 4 * u);
        }
        g.color(MathUtil.withAlpha(0xffb8bdf0, alpha));
        g.text(prog < 1 ? "LOADING..." : "READY!", cx, by + 80 * u, 34 * u, Gfx.ALIGN_CENTER, 4 * u, MathUtil.withAlpha(Ui.INK, alpha));
    }

    private final float[] splashRay = new float[6];

    private void render(Gfx g) {
        switch (screen) {
            case PLAY:
                world.render(g);
                renderHud(g);
                if (paused) renderPause(g);
                break;
            case RESULT:
                world.render(g);
                renderResult(g);
                break;
            case MENU:
                demo.render(g);
                renderMenu(g);
                break;
            case ONBOARD:
            case CLUB:
                demo.render(g);
                social.render(g);
                break;
            case FRIENDS:
                demo.render(g);
                friends.render(g);
                break;
            default:
                demo.render(g);
                meta.render(g);
                break;
        }
        if (popup) renderPopup(g);
        if (sheetPack >= 0) renderSheet(g);
        renderCoinFly(g);
        if (splash > 0) renderSplash(g);
        if (fade > 0) {
            g.color(MathUtil.withAlpha(0xff05060f, fade));
            g.fillRect(0, 0, w, h);
        }
    }

    private void drawGear(Gfx g, float x, float y, float s) {
        g.color(Ui.INK);
        g.fillCircle(x, y, s * 0.42f);
        for (int k = 0; k < 8; k++) {
            float a = k * MathUtil.TAU / 8f + clock * 0.3f;
            g.line(x + MathUtil.cos(a) * s * 0.3f, y + MathUtil.sin(a) * s * 0.3f,
                    x + MathUtil.cos(a) * s * 0.52f, y + MathUtil.sin(a) * s * 0.52f, s * 0.2f);
        }
        g.color(0xffe8eaff);
        g.fillCircle(x, y, s * 0.34f);
        for (int k = 0; k < 8; k++) {
            float a = k * MathUtil.TAU / 8f + clock * 0.3f;
            g.line(x + MathUtil.cos(a) * s * 0.3f, y + MathUtil.sin(a) * s * 0.3f,
                    x + MathUtil.cos(a) * s * 0.46f, y + MathUtil.sin(a) * s * 0.46f, s * 0.12f);
        }
        g.color(Ui.INK);
        g.fillCircle(x, y, s * 0.14f);
    }

    private void renderMenu(Gfx g) {
        g.vertical(0, 0, w, h, 0x99101236, 0xcc0a0c24);

        // Title with a light burst behind it
        float ty = h * 0.3f;
        if (!profile.lowGraphics) {
            g.save();
            g.translate(w / 2, ty - 40 * u);
            g.rotate(clock * 8f);
            float rl = Math.max(w, h);
            for (int k = 0; k < 14; k++) {
                float a0 = k * MathUtil.TAU / 14f, a1 = a0 + MathUtil.TAU / 28f;
                poly[0] = 0;
                poly[1] = 0;
                poly[2] = MathUtil.cos(a0) * rl;
                poly[3] = MathUtil.sin(a0) * rl;
                poly[4] = MathUtil.cos(a1) * rl;
                poly[5] = MathUtil.sin(a1) * rl;
                g.color(0x0effe08a);
                g.fillPoly(poly, 3);
            }
            g.restore();
            g.radial(w / 2, ty - 40 * u, 620 * u, 0x55ffc94a, 0x00ffc94a);
        }
        float bounce = MathUtil.sin(clock * 2.2f) * 6 * u;
        g.save();
        g.translate(w / 2, ty + bounce);
        g.rotate(MathUtil.sin(clock * 1.3f) * 1.5f);
        g.color(0xff2a1a56);
        g.text("SNAKE BRAWL", 6 * u, 10 * u, 170 * u, Gfx.ALIGN_CENTER, 16 * u, 0xff2a1a56);
        g.color(0xffffd23f);
        g.text("SNAKE BRAWL", 0, 0, 170 * u, Gfx.ALIGN_CENTER, 14 * u, 0xff1a1030);
        g.restore();
        g.color(0xffffffff);
        g.text("SLITHER  •  SHOOT  •  SURVIVE", w / 2, ty + 80 * u, 44 * u, Gfx.ALIGN_CENTER, 6 * u, 0xff1a1030);

        // Trophies and coins
        float tl = padL + 20 * u, tt = padT + 14 * u;
        g.color(0xcc0e1024);
        g.fillRoundRect(tl, tt, tl + 270 * u, tt + 90 * u, 45 * u);
        ui.trophy(g, tl + 50 * u, tt + 45 * u, 52 * u);
        g.color(0xffffffff);
        g.text(Integer.toString(profile.trophies), tl + 96 * u, tt + 64 * u, 54 * u, Gfx.ALIGN_LEFT, 6 * u, Ui.INK);
        float cl = tl + 290 * u;
        g.color(0xcc0e1024);
        g.fillRoundRect(cl, tt, cl + 270 * u, tt + 90 * u, 45 * u);
        ui.coin(g, cl + 48 * u, tt + 45 * u, 60 * u);
        g.color(0xffffe066);
        g.text(Integer.toString(profile.coins), cl + 92 * u, tt + 64 * u, 54 * u, Gfx.ALIGN_LEFT, 6 * u, Ui.INK);
        g.color(0xffd8dcff);
        // Player card: club badge, nickname and wins
        float nx = tl + 10 * u;
        if (profile.club >= 0) {
            Clubs.drawBadge(g, Clubs.badge(profile), tl + 30 * u, tt + 128 * u, 40 * u);
            nx = tl + 62 * u;
        }
        g.color(0xffffffff);
        g.text(profile.displayName(), nx, tt + 142 * u, 38 * u, Gfx.ALIGN_LEFT, 5 * u, Ui.INK);
        g.color(0xffd8dcff);
        g.text("Wins " + profile.wins, nx + g.measureText(profile.displayName(), 38 * u) + 24 * u, tt + 142 * u, 30 * u,
                Gfx.ALIGN_LEFT, 4 * u, Ui.INK);

        // Selected brawler showcase
        Brawler b = Brawler.ALL[profile.selected];
        float pl = padL + 30 * u, pb = h - padB - 150 * u, pt = pb - 250 * u, pr = pl + 470 * u;
        ui.panel(g, pl, pt, pr, pb, 0xdd22264a);
        g.color(b.color1);
        g.text(b.name, pl + 30 * u, pt + 70 * u, 64 * u, Gfx.ALIGN_LEFT, 7 * u, 0xff0e1024);
        g.color(0xffd8dcff);
        g.text("LVL " + profile.levels[b.id], pr - 30 * u, pt + 64 * u, 38 * u, Gfx.ALIGN_RIGHT, 5 * u, 0xff0e1024);
        ui.snakeArt(g, b, profile.palette(), (pl + pr) / 2, pt + 165 * u, 1.45f * u, clock);

        Ui.Btn playBtn = ui.find(B_PLAY);
        if (playBtn != null && !profile.lowGraphics) {
            float p = 0.5f + 0.5f * MathUtil.sin(clock * 3.5f);
            g.radial((playBtn.l + playBtn.r) / 2, (playBtn.t + playBtn.b) / 2, (playBtn.r - playBtn.l) * (0.7f + 0.1f * p),
                    MathUtil.withAlpha(0xffffd23f, 0.35f + 0.25f * p), 0x00ffd23f);
        }
        for (int i = 0; i < ui.count; i++) {
            Ui.Btn bt = ui.btns[i];
            ui.button(g, bt);
            if (bt.id == B_PLAY && !profile.lowGraphics) {
                // Shine sweeping across the PLAY button
                float sweep = (clock * 0.6f) % 1.6f;
                if (sweep < 1f) {
                    float sx = bt.l + (bt.r - bt.l) * sweep;
                    g.save();
                    g.clip(bt.l, bt.t, bt.r, bt.b - 12 * u);
                    g.color(0x55ffffff);
                    poly[0] = sx - 40 * u;
                    poly[1] = bt.b;
                    poly[2] = sx + 10 * u;
                    poly[3] = bt.b;
                    poly[4] = sx + 70 * u;
                    poly[5] = bt.t;
                    poly[6] = sx + 20 * u;
                    poly[7] = bt.t;
                    g.fillPoly(poly, 4);
                    g.restore();
                }
            }
            if (bt.id == B_SETTINGS) drawGear(g, (bt.l + bt.r) / 2, (bt.t + bt.b) / 2 - 4 * u, 70 * u);
            if (bt.id == B_SHOP && profile.giftReady()) {
                float bx = bt.r - 6 * u, by = bt.t + 6 * u;
                float p = 1f + 0.12f * MathUtil.sin(clock * 6f);
                g.color(Ui.INK);
                g.fillCircle(bx, by, 26 * u * p);
                g.color(0xffff3a3a);
                g.fillCircle(bx, by, 21 * u * p);
                g.color(0xffffffff);
                g.text("!", bx, by + 12 * u, 34 * u, Gfx.ALIGN_CENTER, 0, 0);
            }
        }
    }

    private void renderHud(Gfx g) {
        World wd = world;
        Snake p = wd.player;

        // Low health vignette
        if (p.alive && p.hp < p.maxHp * 0.35f) {
            float a = (0.35f - p.hp / p.maxHp) / 0.35f * (0.5f + 0.2f * MathUtil.sin(clock * 8f));
            int c = MathUtil.withAlpha(0xffff2020, a * 0.55f);
            g.color(c);
            float e = 70 * u;
            g.fillRect(0, 0, w, e);
            g.fillRect(0, h - e, w, h);
            g.fillRect(0, e, e, h - e);
            g.fillRect(w - e, e, w, h - e);
        }

        // Pause button
        g.color(0xcc0e1024);
        g.fillCircle(pauseX, pauseY, pauseR);
        g.color(0xffffffff);
        g.fillRoundRect(pauseX - 13 * u, pauseY - 15 * u, pauseX - 4 * u, pauseY + 15 * u, 3 * u);
        g.fillRoundRect(pauseX + 4 * u, pauseY - 15 * u, pauseX + 13 * u, pauseY + 15 * u, 3 * u);

        // Stats next to the pause button
        float sx = pauseX + pauseR + 22 * u;
        g.color(0xcc0e1024);
        g.fillRoundRect(sx, pauseY - 34 * u, sx + 420 * u, pauseY + 34 * u, 34 * u);
        Icons.skull(g, sx + 40 * u, pauseY - 2 * u, 40 * u, 1f);
        g.color(0xffffffff);
        g.text(Integer.toString(p.kills), sx + 68 * u, pauseY + 14 * u, 40 * u, Gfx.ALIGN_LEFT, 5 * u, 0xff14142a);
        Icons.cube(g, sx + 150 * u, pauseY, 34 * u, 1f);
        g.color(0xff6dff6d);
        g.text(Integer.toString(p.cubes), sx + 176 * u, pauseY + 14 * u, 40 * u, Gfx.ALIGN_LEFT, 5 * u, 0xff14142a);
        g.color(0xffffd23f);
        g.text("LEN " + (int) p.mass, sx + 250 * u, pauseY + 14 * u, 40 * u, Gfx.ALIGN_LEFT, 5 * u, 0xff14142a);

        // Kill feed
        float fy = pauseY + 90 * u;
        for (int i = 0; i < wd.feedCount; i++) {
            float a = Math.min(1f, (5f - wd.feedTime[i]) * 2f);
            float x = padL + 10 * u;
            float y = fy + i * 46 * u;
            g.color(MathUtil.withAlpha(0xaa0e1024, a));
            float fw = 520 * u;
            g.fillRoundRect(x, y - 32 * u, x + fw, y + 10 * u, 20 * u);
            float tx = x + 16 * u;
            float ts = 28 * u;
            if (wd.feedA[i] != null) {
                g.color(MathUtil.withAlpha(wd.feedColA[i], a));
                g.text(wd.feedA[i], tx, y, ts, Gfx.ALIGN_LEFT, 4 * u, MathUtil.withAlpha(0xff000000, a));
                tx += g.measureText(wd.feedA[i], ts) + 14 * u;
            }
            float iy = y - ts * 0.38f;
            if (wd.feedCause[i] == World.CAUSE_POISON) Icons.poison(g, tx + 16 * u, iy, 32 * u, a);
            else if (wd.feedCause[i] == World.CAUSE_CRASH) Icons.crash(g, tx + 16 * u, iy, 30 * u, a);
            else Icons.skull(g, tx + 16 * u, iy, 30 * u, a);
            tx += 46 * u;
            g.color(MathUtil.withAlpha(wd.feedColB[i], a));
            g.text(wd.feedB[i], tx, y, ts, Gfx.ALIGN_LEFT, 4 * u, MathUtil.withAlpha(0xff000000, a));
        }

        // Top centre: snakes left / poison timer
        float cx = w / 2;
        if (wd.mode == World.MODE_SHOWDOWN || wd.mode == World.MODE_DUO) {
            boolean duo = wd.mode == World.MODE_DUO;
            g.color(0xcc0e1024);
            g.fillRoundRect(cx - 170 * u, padT, cx + 170 * u, padT + 92 * u, 28 * u);
            g.color(0xffd8dcff);
            g.text(duo ? "TEAMS LEFT" : "SNAKES LEFT", cx, padT + 32 * u, 28 * u, Gfx.ALIGN_CENTER, 4 * u, 0xff14142a);
            g.color(0xffffffff);
            g.text(Integer.toString(duo ? wd.aliveTeams() : wd.aliveCount), cx, padT + 80 * u, 50 * u, Gfx.ALIGN_CENTER, 6 * u, 0xff14142a);
            if (duo) drawPartnerHud(g, wd, p);
            if (wd.matchTime < World.ZONE_START) {
                int secs = (int) Math.ceil(World.ZONE_START - wd.matchTime);
                g.color(0xff8cff6a);
                g.text("Poison in " + secs + "s", cx, padT + 130 * u, 32 * u, Gfx.ALIGN_CENTER, 5 * u, 0xff14142a);
            } else if (p.alive && !wd.inZone(p.hx(), p.hy())) {
                g.color(MathUtil.withAlpha(0xffff5a5a, 0.6f + 0.4f * MathUtil.sin(clock * 10f)));
                g.text("GET OUT OF THE POISON!", cx, padT + 135 * u, 40 * u, Gfx.ALIGN_CENTER, 6 * u, 0xff14142a);
            }
        } else {
            g.color(0xcc0e1024);
            g.fillRoundRect(cx - 170 * u, padT, cx + 170 * u, padT + 92 * u, 28 * u);
            g.color(0xffd8dcff);
            g.text("YOUR RANK", cx, padT + 32 * u, 28 * u, Gfx.ALIGN_CENTER, 4 * u, 0xff14142a);
            int rank = 1;
            for (int i = 0; i < wd.snakeCount; i++) {
                Snake o = wd.snakes[i];
                if (o != p && o.alive && o.mass > p.mass) rank++;
            }
            g.color(0xffffffff);
            g.text("#" + rank + " of " + wd.aliveCount, cx, padT + 80 * u, 46 * u, Gfx.ALIGN_CENTER, 6 * u, 0xff14142a);
        }

        drawMinimap(g);
        if (wd.mode == World.MODE_ENDLESS) drawLeaderboard(g);

        // Banner
        if (wd.bannerText != null && wd.bannerTime > 0) {
            float t = 2.2f - wd.bannerTime;
            float sc = t < 0.25f ? 0.5f + t / 0.25f * 0.6f : (t < 0.4f ? 1.1f - (t - 0.25f) / 0.15f * 0.1f : 1f);
            float a = Math.min(1f, wd.bannerTime * 2.5f);
            g.save();
            g.translate(cx, h * 0.3f);
            g.scale(sc);
            g.color(MathUtil.withAlpha(0xffffd23f, a));
            g.text(wd.bannerText, 0, 0, 96 * u, Gfx.ALIGN_CENTER, 11 * u, MathUtil.withAlpha(0xff14142a, a));
            g.restore();
        }

        if (profile.hints < 3 && wd.matchTime < 12f && p.alive) {
            float a = Math.min(1f, (12f - wd.matchTime) * 0.8f);
            g.color(MathUtil.withAlpha(0xaa0e1024, a));
            g.fillRoundRect(cx - 560 * u, h * 0.62f - 46 * u, cx + 560 * u, h * 0.62f + 64 * u, 26 * u);
            g.color(MathUtil.withAlpha(0xffffffff, a));
            g.text(profile.leftHanded ? "Drag RIGHT side to steer  •  Left stick: tap = auto-aim, drag = aim" : "Drag LEFT side to steer  •  Right stick: tap = auto-aim, drag = aim", cx, h * 0.62f, 32 * u,
                    Gfx.ALIGN_CENTER, 4 * u, MathUtil.withAlpha(0xff14142a, a));
            g.text("Hold BOOST to sprint  •  Hit snakes to charge your SUPER", cx, h * 0.62f + 44 * u, 32 * u,
                    Gfx.ALIGN_CENTER, 4 * u, MathUtil.withAlpha(0xff14142a, a));
        }

        if (!p.alive || endTimer >= 0) return;
        drawControls(g, p);
    }

    /** Partner health card, respawn countdown and an edge arrow pointing to your partner. */
    private void drawPartnerHud(Gfx g, World wd, Snake p) {
        Snake mate = wd.mateOf(p);
        if (mate == null) return;
        // Card under the minimap
        float cl = mapX, ct = mapY + mapS + 20 * u, cr = mapX + mapS;
        g.color(0xcc0e1024);
        g.fillRoundRect(cl - 6 * u, ct, cr + 6 * u, ct + 92 * u, 16 * u);
        g.color(0xff8ad8ff);
        g.text("PARTNER", cl + 6 * u, ct + 28 * u, 22 * u, Gfx.ALIGN_LEFT, 3 * u, Ui.INK);
        g.color(0xffffffff);
        String nm = mate.name.length() > 11 ? mate.name.substring(0, 11) : mate.name;
        g.text(nm, cl + 6 * u, ct + 56 * u, 28 * u, Gfx.ALIGN_LEFT, 4 * u, Ui.INK);
        float f = mate.alive ? MathUtil.clamp(mate.hp / mate.maxHp, 0, 1) : 0f;
        g.color(0xff3a2030);
        g.fillRoundRect(cl + 6 * u, ct + 66 * u, cr - 6 * u, ct + 82 * u, 6 * u);
        if (f > 0) g.vertical(cl + 6 * u, ct + 66 * u, cl + 6 * u + (cr - cl - 12 * u) * f, ct + 82 * u, 0xff8ad8ff, 0xff2a7ad8);
        if (!mate.alive) {
            g.color(0xffff7a6a);
            g.text(p.alive ? "Respawns if you survive" : "Knocked out", cl + 6 * u, ct + 80 * u, 18 * u, Gfx.ALIGN_LEFT, 3 * u, Ui.INK);
        }
        // Respawn countdown for the player
        if (!p.alive && mate.alive) {
            int secs = (int) Math.ceil(World.DUO_RESPAWN - p.deadTime);
            g.color(0xaa0e1024);
            g.fillRoundRect(w / 2 - 330 * u, h * 0.62f - 70 * u, w / 2 + 330 * u, h * 0.62f + 40 * u, 30 * u);
            g.color(0xffffffff);
            g.text("RESPAWNING IN " + Math.max(0, secs), w / 2, h * 0.62f, 58 * u, Gfx.ALIGN_CENTER, 7 * u, Ui.INK);
            g.color(0xff8ad8ff);
            g.text("Watching " + mate.name, w / 2, h * 0.62f + 32 * u, 26 * u, Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
        }
        // Edge arrow towards the partner when off screen
        if (mate.alive && p.alive) {
            float sx = w / 2 + (mate.hx() - wd.camX) * wd.zoom, sy = h / 2 + (mate.hy() - wd.camY) * wd.zoom;
            float m = 70 * u;
            if (sx < m || sx > w - m || sy < m || sy > h - m) {
                float ang = MathUtil.angleTo(w / 2, h / 2, sx, sy);
                float ax = MathUtil.clamp(sx, m, w - m), ay = MathUtil.clamp(sy, m + 60 * u, h - m);
                float ca = MathUtil.cos(ang), sa = MathUtil.sin(ang);
                g.color(Ui.INK);
                g.fillCircle(ax, ay, 34 * u);
                g.color(0xff2a7ad8);
                g.fillCircle(ax, ay, 29 * u);
                poly[0] = ax + ca * 44 * u;
                poly[1] = ay + sa * 44 * u;
                poly[2] = ax + ca * 20 * u - sa * 16 * u;
                poly[3] = ay + sa * 20 * u + ca * 16 * u;
                poly[4] = ax + ca * 20 * u + sa * 16 * u;
                poly[5] = ay + sa * 20 * u - ca * 16 * u;
                g.fillPoly(poly, 3);
                g.color(0xffffffff);
                g.text(mate.name.substring(0, 1), ax, ay + 10 * u, 28 * u, Gfx.ALIGN_CENTER, 0, 0);
            }
        }
    }

    private void drawControls(Gfx g, Snake p) {
        // Movement stick
        float mx = movePtr >= 0 ? moveOx : moveCX, my = movePtr >= 0 ? moveOy : moveCY;
        g.radial(mx, my, moveR, movePtr >= 0 ? 0x223a3f7a : 0x112a2f5a, movePtr >= 0 ? 0x993a3f7a : 0x662a2f5a);
        g.color(0x88ffffff);
        g.strokeCircle(mx, my, moveR, 4 * u);
        float kx = mx, ky = my;
        if (movePtr >= 0) {
            float dx = moveX - moveOx, dy = moveY - moveOy;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            float m = Math.min(d, moveR);
            if (d > 0) {
                kx = mx + dx / d * m;
                ky = my + dy / d * m;
            }
        }
        g.color(0xff1a3f8a);
        g.fillCircle(kx, ky, moveR * 0.45f + 5 * u);
        g.radial(kx - moveR * 0.1f, ky - moveR * 0.12f, moveR * 0.5f, 0xff9ad0ff, 0xff2b6fd6);
        g.color(0x88ffffff);
        g.fillCircle(kx - moveR * 0.16f, ky - moveR * 0.18f, moveR * 0.12f);

        // Boost button
        boolean canBoost = p.mass > Snake.MIN_BOOST_MASS;
        g.color(0xff14142a);
        g.fillCircle(boostCX, boostCY, boostR + 5 * u);
        g.color(boostPtr >= 0 ? 0xff2fc0ff : (canBoost ? 0xff1d8fd0 : 0xff5a5a6a));
        g.fillCircle(boostCX, boostCY, boostR);
        g.color(0xffffffff);
        for (int k = 0; k < 2; k++) {
            float ox = boostCX - 14 * u + k * 20 * u;
            poly[0] = ox - 10 * u;
            poly[1] = boostCY - 20 * u;
            poly[2] = ox + 12 * u;
            poly[3] = boostCY;
            poly[4] = ox - 10 * u;
            poly[5] = boostCY + 20 * u;
            g.fillPoly(poly, 3);
        }
        g.text("BOOST", boostCX, boostCY + boostR + 34 * u, 28 * u, Gfx.ALIGN_CENTER, 4 * u, 0xff14142a);

        // Super button with charge ring
        boolean ready = p.superReady();
        float pulse = ready ? 1f + 0.06f * MathUtil.sin(clock * 9f) : 1f;
        float sr = supR * pulse;
        if (ready) g.radial(supCX, supCY, sr * 2.1f, 0xaaffd23f, 0x00ffd23f);
        g.color(0xff14142a);
        g.fillCircle(supCX, supCY, sr + 6 * u);
        if (ready) g.radial(supCX - sr * 0.2f, supCY - sr * 0.25f, sr * 1.1f, 0xfffff2a8, 0xffffb020);
        else {
            g.color(0xff4a4a62);
            g.fillCircle(supCX, supCY, sr);
        }
        if (!ready) {
            g.color(0xffffc928);
            g.arc(supCX, supCY, sr - 6 * u, -90, 360 * p.superCharge, 10 * u);
        } else {
            g.color(0x66fff2a8);
            g.strokeCircle(supCX, supCY, sr + 16 * u + 6 * u * MathUtil.sin(clock * 9f), 6 * u);
        }
        // Skull-ish star icon
        g.color(ready ? 0xff14142a : 0xff8a8aa0);
        float ir = sr * 0.42f;
        for (int k = 0; k < 5; k++) {
            float a0 = -MathUtil.PI / 2 + k * MathUtil.TAU / 5f;
            float a1 = a0 + MathUtil.TAU / 10f;
            float a2 = a0 - MathUtil.TAU / 10f;
            poly[0] = supCX + MathUtil.cos(a0) * ir;
            poly[1] = supCY + MathUtil.sin(a0) * ir;
            poly[2] = supCX + MathUtil.cos(a1) * ir * 0.42f;
            poly[3] = supCY + MathUtil.sin(a1) * ir * 0.42f;
            poly[4] = supCX + MathUtil.cos(a2) * ir * 0.42f;
            poly[5] = supCY + MathUtil.sin(a2) * ir * 0.42f;
            g.fillPoly(poly, 3);
        }
        g.fillCircle(supCX, supCY, ir * 0.45f);
        if (supPtr >= 0) drawStickKnob(g, supOx, supOy, supX, supY, supR, 0xffffc928);

        // Attack stick
        float ax = atkPtr >= 0 ? atkOx : atkCX, ay = atkPtr >= 0 ? atkOy : atkCY;
        g.radial(ax, ay, atkR * 1.25f, 0x22ff5a3a, 0x77ff5a3a);
        g.color(0x88ffffff);
        g.strokeCircle(ax, ay, atkR * 1.25f, 4 * u);
        if (world.hasLock() && atkPtr < 0) {
            // Locked-on indicator: spinning ring
            float sp = clock * 200f;
            g.color(0xffffd23f);
            g.arc(ax, ay, atkR * 1.25f + 8 * u, sp, 70, 6 * u);
            g.arc(ax, ay, atkR * 1.25f + 8 * u, sp + 180, 70, 6 * u);
        }
        if (atkPtr >= 0) drawStickKnob(g, atkOx, atkOy, atkX, atkY, atkR, 0xffff5a3a);
        else {
            g.color(0xff8a1f1f);
            g.fillCircle(ax, ay, atkR * 0.5f + 4 * u);
            int kc = p.ammo >= 1 ? 0xffff5a3a : 0xff8a5a5a;
            g.radial(ax - atkR * 0.1f, ay - atkR * 0.12f, atkR * 0.55f, MathUtil.lighter(kc, 0.45f), kc);
            // Crosshair
            g.color(0xffffffff);
            g.strokeCircle(ax, ay, atkR * 0.22f, 4 * u);
            g.line(ax - atkR * 0.35f, ay, ax + atkR * 0.35f, ay, 4 * u);
            g.line(ax, ay - atkR * 0.35f, ax, ay + atkR * 0.35f, 4 * u);
        }
    }

    private void drawStickKnob(Gfx g, float ox, float oy, float x, float y, float r, int color) {
        float dx = x - ox, dy = y - oy;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        float m = Math.min(d, r);
        float kx = ox, ky = oy;
        if (d > 0) {
            kx = ox + dx / d * m;
            ky = oy + dy / d * m;
        }
        g.color(MathUtil.withAlpha(color, 0.35f));
        g.fillCircle(ox, oy, r * 1.25f);
        g.color(MathUtil.darker(color, 0.4f));
        g.fillCircle(kx, ky, r * 0.5f + 4 * u);
        g.color(color);
        g.fillCircle(kx, ky, r * 0.5f);
    }

    private void drawMinimap(Gfx g) {
        World wd = world;
        float s = mapS, x0 = mapX, y0 = mapY;
        float k = s / wd.size;
        g.color(0xff0e1024);
        g.fillRoundRect(x0 - 6 * u, y0 - 6 * u, x0 + s + 6 * u, y0 + s + 6 * u, 14 * u);
        g.color(0xffcdb37e);
        g.fillRect(x0, y0, x0 + s, y0 + s);
        g.color(0xff5a6582);
        int n = wd.n;
        float ts = World.T * k;
        for (int ty = 0; ty < n; ty++) {
            for (int tx = 0; tx < n; tx++) {
                byte t = wd.tiles[ty * n + tx];
                if (t == World.WALL) g.fillRect(x0 + tx * ts, y0 + ty * ts, x0 + (tx + 1) * ts + 0.5f, y0 + (ty + 1) * ts + 0.5f);
            }
        }
        g.color(0xff3c8a3a);
        for (int ty = 0; ty < n; ty++) {
            for (int tx = 0; tx < n; tx++) {
                if (wd.bush[ty * n + tx]) g.fillRect(x0 + tx * ts, y0 + ty * ts, x0 + (tx + 1) * ts + 0.5f, y0 + (ty + 1) * ts + 0.5f);
            }
        }
        g.color(0xffc4873f);
        for (int b = 0; b < wd.boxCount; b++) {
            int t = wd.boxTile[b];
            float bx = x0 + (t % n + 0.5f) * ts, by = y0 + (t / n + 0.5f) * ts;
            g.fillRect(bx - 3 * u, by - 3 * u, bx + 3 * u, by + 3 * u);
        }
        if (wd.zoneActive()) {
            g.color(0x9932c04a);
            float zl = x0 + wd.zoneL * k, zr = x0 + wd.zoneR * k, zt = y0 + wd.zoneT * k, zb = y0 + wd.zoneB * k;
            g.fillRect(x0, y0, x0 + s, zt);
            g.fillRect(x0, zb, x0 + s, y0 + s);
            g.fillRect(x0, zt, zl, zb);
            g.fillRect(zr, zt, x0 + s, zb);
        }
        Snake p = wd.player;
        Snake mate = p != null ? wd.mateOf(p) : null;
        if (mate != null && mate.alive) {
            g.color(0xff14142a);
            g.fillCircle(x0 + mate.hx() * k, y0 + mate.hy() * k, 8 * u);
            g.color(0xff3fa0ff);
            g.fillCircle(x0 + mate.hx() * k, y0 + mate.hy() * k, 5.5f * u);
        }
        if (p != null && p.alive) {
            g.color(0xff14142a);
            g.fillCircle(x0 + p.hx() * k, y0 + p.hy() * k, 8 * u);
            g.color(0xffffffff);
            g.fillCircle(x0 + p.hx() * k, y0 + p.hy() * k, 5.5f * u);
            // View rectangle
            float vw = w / wd.zoom * k, vh = h / wd.zoom * k;
            g.color(0x88ffffff);
            g.strokeRoundRect(x0 + wd.camX * k - vw / 2, y0 + wd.camY * k - vh / 2, x0 + wd.camX * k + vw / 2, y0 + wd.camY * k + vh / 2, 3 * u, 2 * u);
        }
    }

    private final Snake[] board = new Snake[32];

    private void drawLeaderboard(Gfx g) {
        World wd = world;
        int n = 0;
        for (int i = 0; i < wd.snakeCount; i++) if (wd.snakes[i].alive) board[n++] = wd.snakes[i];
        for (int i = 1; i < n; i++) {
            Snake s = board[i];
            int j = i - 1;
            while (j >= 0 && board[j].mass < s.mass) {
                board[j + 1] = board[j];
                j--;
            }
            board[j + 1] = s;
        }
        float x0 = mapX, y0 = mapY + mapS + 24 * u, bw = mapS;
        int rows = Math.min(5, n);
        g.color(0xaa0e1024);
        g.fillRoundRect(x0 - 6 * u, y0 - 6 * u, x0 + bw + 6 * u, y0 + 44 * u + rows * 36 * u, 14 * u);
        g.color(0xffffd23f);
        g.text("LEADERBOARD", x0 + bw / 2, y0 + 30 * u, 28 * u, Gfx.ALIGN_CENTER, 4 * u, 0xff14142a);
        for (int i = 0; i < rows; i++) {
            Snake s = board[i];
            float y = y0 + 70 * u + i * 36 * u;
            g.color(s.isPlayer ? 0xff9cff8a : 0xffffffff);
            String nm = s.name.length() > 10 ? s.name.substring(0, 10) : s.name;
            g.text((i + 1) + ". " + nm, x0 + 6 * u, y, 24 * u, Gfx.ALIGN_LEFT, 3 * u, 0xff14142a);
            g.text(Integer.toString((int) s.mass), x0 + bw - 4 * u, y, 24 * u, Gfx.ALIGN_RIGHT, 3 * u, 0xff14142a);
        }
    }


    private void renderPause(Gfx g) {
        g.color(0xaa0a0c22);
        g.fillRect(0, 0, w, h);
        g.color(0xffffffff);
        g.text("PAUSED", w / 2, h / 2 - 110 * u, 110 * u, Gfx.ALIGN_CENTER, 12 * u, Ui.INK);
        for (int i = 0; i < ui.count; i++) ui.button(g, ui.btns[i]);
    }

    private void renderResult(Gfx g) {
        g.color(0xaa0a0c22);
        g.fillRect(0, 0, w, h);
        float t = Math.min(1f, screenTime * 3f);
        if (resWin) {
            // Confetti shower
            int[] cols = {0xffffd23f, 0xff4ad04a, 0xff3fa0ff, 0xffff5ab5, 0xffb35cff, 0xffff7a2a};
            for (int k = 0; k < 70; k++) {
                float sp = 120f + (k % 7) * 30f;
                float x = ((k * 0.6180339f) % 1f) * w + MathUtil.sin(clock * 2f + k) * 30 * u;
                float y = ((((k * 0.3819660f) % 1f) * h + clock * sp * u) % (h + 40 * u)) - 20 * u;
                g.save();
                g.translate(x, y);
                g.rotate(clock * (90 + k * 7));
                g.color(cols[k % cols.length]);
                g.fillRect(-8 * u, -4 * u, 8 * u, 4 * u);
                g.restore();
            }
        }
        float cx = w / 2;
        String title;
        int tc;
        if (resWin) {
            title = "VICTORY!";
            tc = 0xffffd23f;
        } else if (resRank > 0) {
            title = "RANK #" + resRank;
            tc = resRank <= 4 ? 0xff9cff8a : 0xffff7a6a;
        } else {
            title = "GAME OVER";
            tc = 0xffff7a6a;
        }
        g.save();
        g.translate(cx, padT + 150 * u);
        g.scale(0.6f + 0.4f * t + (resWin ? 0.04f * MathUtil.sin(clock * 4f) : 0));
        g.color(tc);
        g.text(title, 0, 0, 130 * u, Gfx.ALIGN_CENTER, 14 * u, Ui.INK);
        g.restore();

        float pw = 860 * u, ph = 440 * u;
        float pl = cx - pw / 2, pt = padT + 200 * u;
        float maxB = h - padB - 190 * u;
        if (pt + ph > maxB) ph = maxB - pt;
        ui.panel(g, pl, pt, pl + pw, pt + ph, 0xee22264a);
        Snake player = world.player;
        ui.snakeArt(g, player.type, player.palette, pl + 175 * u, pt + ph / 2, 0.95f * u, clock);

        float sx = pl + 380 * u, sy = pt + 70 * u, row = Math.min(62 * u, (ph - 50 * u) / 5.4f);
        float vx = pl + pw - 50 * u;
        resultRow(g, "KNOCKOUTS", Integer.toString(resKills), 0xffffffff, sx, vx, sy);
        sy += row;
        resultRow(g, "LENGTH", (resNewBest ? "NEW BEST! " : "") + resLength, resNewBest ? 0xffffd23f : 0xffffffff, sx, vx, sy);
        sy += row;
        resultRow(g, "POWER CUBES", Integer.toString(resCubes), 0xff6dff6d, sx, vx, sy);
        sy += row;
        int secs = (int) resTime;
        resultRow(g, "SURVIVED", secs / 60 + ":" + (secs % 60 < 10 ? "0" : "") + secs % 60, 0xffffffff, sx, vx, sy);
        sy += row;
        ui.coin(g, sx + 24 * u, sy - 14 * u, 48 * u);
        g.color(0xffffe066);
        g.text("+" + resCoins, sx + 64 * u, sy, 46 * u, Gfx.ALIGN_LEFT, 6 * u, Ui.INK);
        ui.trophy(g, sx + 250 * u, sy - 14 * u, 44 * u);
        g.color(resDelta > 0 ? 0xff9cff8a : (resDelta < 0 ? 0xffff7a6a : 0xffffffff));
        g.text((resDelta > 0 ? "+" : "") + resDelta, sx + 292 * u, sy, 46 * u, Gfx.ALIGN_LEFT, 6 * u, Ui.INK);
        g.color(0xffffffff);
        g.text("Total " + profile.trophies, vx, sy, 38 * u, Gfx.ALIGN_RIGHT, 5 * u, Ui.INK);

        for (int i = 0; i < ui.count; i++) ui.button(g, ui.btns[i]);
    }

    private void resultRow(Gfx g, String label, String value, int color, float x, float vx, float y) {
        g.color(0xffd8dcff);
        g.text(label, x, y, 36 * u, Gfx.ALIGN_LEFT, 5 * u, Ui.INK);
        g.color(color);
        g.text(value, vx, y, 44 * u, Gfx.ALIGN_RIGHT, 5 * u, Ui.INK);
    }
}
