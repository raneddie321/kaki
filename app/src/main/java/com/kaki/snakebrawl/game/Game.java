package com.kaki.snakebrawl.game;

/** Top-level game: screens, HUD, touch controls and progression. Host-agnostic. */
public final class Game {
    static final int MENU = 0, BRAWLERS = 1, PLAY = 2, RESULT = 3, SHOP = 4, SETTINGS = 5;
    private static final float STEP = 1f / 60f;

    static final int B_PLAY = 1, B_MODE = 2, B_BRAWLERS = 3, B_SHOP = 4, B_SETTINGS = 5, B_BACK = 6,
            B_AGAIN = 7, B_MENU = 8, B_RESUME = 9, B_QUIT = 10, B_YES = 90, B_NO = 91, B_OK = 92;

    // Popup actions confirmed with YES
    static final int ACT_NONE = 0, ACT_BUY_SKIN = 1, ACT_UNLOCK = 2, ACT_UPGRADE = 3, ACT_BOX = 4,
            ACT_MEGA_BOX = 5, ACT_RESET = 6, ACT_DEAL = 7;

    private static final int[] TROPHY_TABLE = {10, 8, 7, 6, 4, 2, 0, -1, -2, -3};
    private static final int[] RANK_COINS = {50, 40, 32, 26, 20, 15, 11, 8, 5, 3};

    private final Platform host;
    final Platform gated;
    final Profile profile;
    final Ui ui = new Ui();
    private final MetaScreens meta;
    float w = 1920, h = 1080, u = 1;
    private float insL, insT, insR, insB;
    float padL, padT, padR, padB;

    int screen = MENU;
    private float screenTime;
    private World world;
    World demo;
    private float acc;
    float clock;

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
        };
        meta = new MetaScreens(this);
        newDemo();
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

    /** Advances the game without drawing (also used by the desktop test harness). */
    void tick(float dt) {
        clock += dt;
        screenTime += dt;
        popTime += dt;
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
                setScreen(MENU);
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
        screen = s;
        screenTime = 0;
        ui.cancel();
        ui.resetScroll();
        releaseControls();
        layout();
    }

    private void goMenu() {
        world = null;
        paused = false;
        newDemo();
        setScreen(MENU);
    }

    private void startGame() {
        Brawler b = Brawler.ALL[profile.selected];
        int wm = profile.mode == 0 ? World.MODE_SHOWDOWN : World.MODE_ENDLESS;
        world = new World(gated, wm, b, profile.palette(), profile.levels[b.id], wm == World.MODE_SHOWDOWN ? 9 : 11, profile.trophies);
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
        if (id == B_YES) meta.perform(action, arg);
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
        if (screen != PLAY && screen != RESULT) {
            demo.update(dt);
            demo.updateCamera(dt, w, h);
            return;
        }
        if (world == null) return;
        if (screen == PLAY && paused) return;

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

        if (screen == PLAY) {
            Snake p = world.player;
            if (endTimer < 0) {
                if (!p.alive) {
                    endTimer = 1.8f;
                    endIsWin = false;
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
            if (dx * dx + dy * dy > (10 * u) * (10 * u)) p.targetAng = (float) Math.atan2(dy, dx);
        }
        p.boostInput = boostPtr >= 0;

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
        if (sup) p.trySuper(world, ang, dist);
        else p.tryAttack(world, ang, dist);
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
                    if (dx * dx + dy * dy > 100 * u * u) p.targetAng = (float) Math.atan2(dy, dx);
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
        if (popup) {
            popupButton(id);
            return;
        }
        switch (id) {
            case B_PLAY:
            case B_AGAIN:
                startGame();
                break;
            case B_MODE:
                profile.mode = 1 - profile.mode;
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
                meta.onButton(id);
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
                ui.add(B_MODE, r - bw - 30 * u - 430 * u, b - bh, r - bw - 30 * u, b,
                        profile.mode == 0 ? "SHOWDOWN" : "ENDLESS", profile.mode == 0 ? "Last snake standing" : "Grow forever",
                        profile.mode == 0 ? 0xff3fa0ff : 0xffb35cff);
                float pl = padL + 30 * u;
                ui.add(B_BRAWLERS, pl, b - 110 * u, pl + 470 * u, b, "BRAWLERS", null, 0xff4ad04a);
                ui.add(B_SHOP, pl, padT + 200 * u, pl + 300 * u, padT + 330 * u, "SHOP", "Skins & boxes", 0xffff5ab5);
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
                ui.add(B_AGAIN, cx + 30 * u, b - 130 * u, cx + 470 * u, b, "PLAY AGAIN", null, 0xffffc928);
                break;
            }
            default:
                meta.layout();
                break;
        }
    }

    // ------------------------------------------------------------------ render

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
            default:
                demo.render(g);
                meta.render(g);
                break;
        }
        if (popup) renderPopup(g);
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
        g.text("Wins " + profile.wins + "   Best length " + profile.bestLen, tl + 10 * u, tt + 140 * u, 32 * u,
                Gfx.ALIGN_LEFT, 5 * u, Ui.INK);

        // Selected brawler showcase
        Brawler b = Brawler.ALL[profile.selected];
        float pl = padL + 30 * u, pb = h - padB - 150 * u, pt = pb - 250 * u, pr = pl + 470 * u;
        ui.panel(g, pl, pt, pr, pb, 0xdd22264a);
        g.color(b.color1);
        g.text(b.name, pl + 30 * u, pt + 70 * u, 64 * u, Gfx.ALIGN_LEFT, 7 * u, 0xff0e1024);
        g.color(0xffd8dcff);
        g.text("LVL " + profile.levels[b.id], pr - 30 * u, pt + 64 * u, 38 * u, Gfx.ALIGN_RIGHT, 5 * u, 0xff0e1024);
        ui.snakeArt(g, b, profile.palette(), (pl + pr) / 2, pt + 165 * u, 1.45f * u, clock);

        for (int i = 0; i < ui.count; i++) {
            Ui.Btn bt = ui.btns[i];
            ui.button(g, bt);
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
        if (wd.mode == World.MODE_SHOWDOWN) {
            g.color(0xcc0e1024);
            g.fillRoundRect(cx - 170 * u, padT, cx + 170 * u, padT + 92 * u, 28 * u);
            g.color(0xffd8dcff);
            g.text("SNAKES LEFT", cx, padT + 32 * u, 28 * u, Gfx.ALIGN_CENTER, 4 * u, 0xff14142a);
            g.color(0xffffffff);
            g.text(Integer.toString(wd.aliveCount), cx, padT + 80 * u, 50 * u, Gfx.ALIGN_CENTER, 6 * u, 0xff14142a);
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
        if (wd.mode != World.MODE_SHOWDOWN) drawLeaderboard(g);

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
        g.color(0xff14142a);
        g.fillCircle(supCX, supCY, sr + 6 * u);
        g.color(ready ? 0xffffc928 : 0xff4a4a62);
        g.fillCircle(supCX, supCY, sr);
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
