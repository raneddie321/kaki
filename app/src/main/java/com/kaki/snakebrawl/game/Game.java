package com.kaki.snakebrawl.game;

/** Top-level game: screens, HUD, touch controls and persistence. Host-agnostic. */
public final class Game {
    private static final int MENU = 0, BRAWLERS = 1, PLAY = 2, RESULT = 3;
    private static final float STEP = 1f / 60f;

    private static final int B_PLAY = 0, B_MODE = 1, B_BRAWLERS = 2, B_SOUND = 3, B_BACK = 4,
            B_CARD = 5, /* 5..8 */ B_AGAIN = 9, B_MENU = 10, B_RESUME = 11, B_QUIT = 12, B_PAUSE = 13;

    private static final int[] TROPHY_TABLE = {10, 8, 7, 6, 4, 2, 0, -1, -2, -3};

    private final Platform host;
    private final Platform gated;
    private float w = 1920, h = 1080, u = 1;
    private float insL, insT, insR, insB;
    private float padL, padT, padR, padB;

    private int screen = MENU;
    private float screenTime;
    private World world;
    private World demo;
    private float acc;
    private float clock;

    private int selected;
    private int mode;
    private int trophies;
    private int bestLen;
    private int wins;
    private int games;
    private boolean soundOn;
    private int hintGames;

    private boolean paused;
    private float endTimer = -1;
    private boolean endIsWin;
    private int resRank, resKills, resLength, resDelta, resCubes;
    private boolean resWin, resNewBest;
    private float resTime;

    // Touch controls
    private int movePtr = -1;
    private float moveOx, moveOy, moveX, moveY;
    private int atkPtr = -1;
    private float atkOx, atkOy, atkX, atkY, atkMax;
    private int supPtr = -1;
    private float supOx, supOy, supX, supY, supMax;
    private int boostPtr = -1;
    private float atkCX, atkCY, atkR, supCX, supCY, supR, boostCX, boostCY, boostR;
    private float moveCX, moveCY, moveR, pauseX, pauseY, pauseR, mapX, mapY, mapS;

    // Buttons
    private static final class Btn {
        int id;
        float l, t, r, b;
        String label, sub;
        int color;

        boolean hit(float x, float y) {
            return x >= l && x <= r && y >= t && y <= b;
        }
    }

    private final Btn[] btns = new Btn[16];
    private int btnCount;
    private int pressedBtn = -1;
    private int pressedPtr = -1;

    private final float[] artX = new float[40], artY = new float[40];
    private final float[] poly = new float[16];

    public Game(Platform platform) {
        this.host = platform;
        for (int i = 0; i < btns.length; i++) btns[i] = new Btn();
        selected = clampInt(host.loadInt("brawler", 0), 0, Brawler.ALL.length - 1);
        mode = clampInt(host.loadInt("mode", 0), 0, 1);
        trophies = Math.max(0, host.loadInt("trophies", 0));
        bestLen = host.loadInt("bestLen", 0);
        wins = host.loadInt("wins", 0);
        games = host.loadInt("games", 0);
        soundOn = host.loadInt("sound", 1) == 1;
        hintGames = host.loadInt("hints", 0);
        gated = new Platform() {
            @Override
            public void playSound(int id, float volume) {
                if (soundOn) host.playSound(id, volume);
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
                host.vibrate(millis);
            }
        };
        newDemo();
        layout();
    }

    private static int clampInt(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private void newDemo() {
        demo = new World(gated, World.MODE_DEMO, null, 9, 350);
        // Let the demo arena develop a bit so the menu starts lively
        for (int i = 0; i < 240; i++) demo.update(STEP);
    }

    // ------------------------------------------------------------------ host API

    public void resize(float width, float height) {
        w = width;
        h = height;
        u = Math.min(h / 1080f, w / 1920f);
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
        clock += dt;
        screenTime += dt;
        update(dt);
        render(g);
    }

    World currentWorld() {
        return world != null ? world : demo;
    }

    int screenId() {
        return screen;
    }

    /** Advances the game without drawing (used by the desktop test harness). */
    void tick(float dt) {
        clock += dt;
        screenTime += dt;
        update(dt);
    }

    /** Called when the app goes to the background. */
    public void onPause() {
        releaseControls();
        if (screen == PLAY && endTimer < 0) {
            paused = true;
            layout();
        }
    }

    /** Returns true if the back press was handled. */
    public boolean onBack() {
        switch (screen) {
            case PLAY:
                if (endTimer >= 0) return true;
                paused = !paused;
                releaseControls();
                layout();
                return true;
            case BRAWLERS:
            case RESULT:
                goMenu();
                return true;
            default:
                return false;
        }
    }

    // ------------------------------------------------------------------ flow

    private void setScreen(int s) {
        screen = s;
        screenTime = 0;
        pressedBtn = -1;
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
        Brawler b = Brawler.ALL[selected];
        int wm = mode == 0 ? World.MODE_SHOWDOWN : World.MODE_ENDLESS;
        world = new World(gated, wm, b, wm == World.MODE_SHOWDOWN ? 9 : 11, trophies);
        world.updateCamera(1f, w, h);
        world.zoom = h / 900f;
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
            resDelta = TROPHY_TABLE[clampInt(resRank - 1, 0, TROPHY_TABLE.length - 1)];
            if (win) wins++;
        } else {
            resRank = 0;
            resDelta = Math.min(5, p.kills);
        }
        int before = trophies;
        trophies = Math.max(0, trophies + resDelta);
        resDelta = trophies - before;
        resNewBest = resLength > bestLen;
        if (resNewBest) bestLen = resLength;
        games++;
        hintGames++;
        host.saveInt("trophies", trophies);
        host.saveInt("bestLen", bestLen);
        host.saveInt("wins", wins);
        host.saveInt("games", games);
        host.saveInt("hints", hintGames);
        gated.playSound(win ? Platform.SND_VICTORY : Platform.SND_DEFEAT, 1f);
        setScreen(RESULT);
    }

    // ------------------------------------------------------------------ update

    private void update(float dt) {
        if (screen == MENU || screen == BRAWLERS) {
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

    private float projSpeed(Brawler b) {
        switch (b.id) {
            case Brawler.VOLT:
                return 2000;
            case Brawler.VIPER:
                return 1300;
            case Brawler.BLAZE:
                return 950;
            default:
                return 0;
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
        } else if (world.findAim(p, range, projSpeed(p.type))) {
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
        movePtr = atkPtr = supPtr = boostPtr = -1;
        pressedBtn = -1;
        if (world != null) {
            world.aimActive = false;
            if (world.player != null) world.player.boostInput = false;
        }
    }

    // ------------------------------------------------------------------ input

    public void touchDown(int id, float x, float y) {
        if (screen == PLAY && !paused && endTimer < 0) {
            if (MathUtil.dist2(x, y, pauseX, pauseY) < (pauseR * 1.5f) * (pauseR * 1.5f)) {
                pressedBtn = B_PAUSE;
                pressedPtr = id;
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
            if (atkPtr < 0 && x > w * 0.5f) {
                atkPtr = id;
                boolean onStick = MathUtil.dist2(x, y, atkCX, atkCY) < (atkR * 1.9f) * (atkR * 1.9f);
                atkOx = onStick ? atkCX : x;
                atkOy = onStick ? atkCY : y;
                atkX = x;
                atkY = y;
                atkMax = 0;
                return;
            }
            if (movePtr < 0 && x <= w * 0.5f) {
                movePtr = id;
                moveOx = MathUtil.clamp(x, padL + moveR, w * 0.5f - moveR * 0.5f);
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
        for (int i = btnCount - 1; i >= 0; i--) {
            if (btns[i].hit(x, y)) {
                pressedBtn = btns[i].id;
                pressedPtr = id;
                return;
            }
        }
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
        if (id == pressedPtr && pressedBtn >= 0) {
            int b = pressedBtn;
            pressedBtn = -1;
            if (b == B_PAUSE) {
                if (MathUtil.dist2(x, y, pauseX, pauseY) < (pauseR * 1.8f) * (pauseR * 1.8f)) {
                    gated.playSound(Platform.SND_CLICK, 0.7f);
                    paused = true;
                    releaseControls();
                    layout();
                }
                return;
            }
            for (int i = 0; i < btnCount; i++) {
                if (btns[i].id == b && btns[i].hit(x, y)) {
                    gated.playSound(Platform.SND_CLICK, 0.7f);
                    onButton(b);
                    return;
                }
            }
        }
    }

    public void touchCancelAll() {
        releaseControls();
    }

    private void onButton(int id) {
        switch (id) {
            case B_PLAY:
                startGame();
                break;
            case B_MODE:
                mode = 1 - mode;
                host.saveInt("mode", mode);
                layout();
                break;
            case B_BRAWLERS:
                setScreen(BRAWLERS);
                break;
            case B_SOUND:
                soundOn = !soundOn;
                host.saveInt("sound", soundOn ? 1 : 0);
                layout();
                break;
            case B_BACK:
                goMenu();
                break;
            case B_AGAIN:
                startGame();
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
                if (id >= B_CARD && id < B_CARD + Brawler.ALL.length) {
                    selected = id - B_CARD;
                    host.saveInt("brawler", selected);
                    gated.playSound(Platform.SND_POWER, 0.6f);
                }
                break;
        }
    }

    // ------------------------------------------------------------------ layout

    private Btn addBtn(int id, float l, float t, float r, float b, String label, String sub, int color) {
        Btn x = btns[btnCount++];
        x.id = id;
        x.l = l;
        x.t = t;
        x.r = r;
        x.b = b;
        x.label = label;
        x.sub = sub;
        x.color = color;
        return x;
    }

    private void layout() {
        padL = Math.max(28 * u, insL + 8 * u);
        padR = Math.max(28 * u, insR + 8 * u);
        padT = Math.max(20 * u, insT + 4 * u);
        padB = Math.max(20 * u, insB + 4 * u);

        atkR = 92 * u;
        atkCX = w - padR - 175 * u;
        atkCY = h - padB - 175 * u;
        supR = 70 * u;
        supCX = atkCX - 225 * u;
        supCY = atkCY + 55 * u;
        boostR = 62 * u;
        boostCX = atkCX + 25 * u;
        boostCY = atkCY - 215 * u;
        moveR = 105 * u;
        moveCX = padL + 200 * u;
        moveCY = h - padB - 190 * u;
        pauseR = 38 * u;
        pauseX = padL + 44 * u;
        pauseY = padT + 44 * u;
        mapS = 210 * u;
        mapX = w - padR - mapS;
        mapY = padT;

        btnCount = 0;
        switch (screen) {
            case MENU: {
                float bw = 400 * u, bh = 150 * u;
                float r = w - padR - 30 * u, b = h - padB - 30 * u;
                addBtn(B_PLAY, r - bw, b - bh, r, b, "PLAY", null, 0xffffc928);
                addBtn(B_MODE, r - bw - 30 * u - 430 * u, b - bh, r - bw - 30 * u, b,
                        mode == 0 ? "SHOWDOWN" : "ENDLESS", mode == 0 ? "Last snake standing" : "Grow forever", mode == 0 ? 0xff3fa0ff : 0xffb35cff);
                float pl = padL + 30 * u;
                addBtn(B_BRAWLERS, pl, b - 110 * u, pl + 470 * u, b, "BRAWLERS", null, 0xff4ad04a);
                addBtn(B_SOUND, w - padR - 120 * u, padT + 10 * u, w - padR - 10 * u, padT + 120 * u, soundOn ? "ON" : "OFF", "SOUND", soundOn ? 0xff4ad04a : 0xff8a8a9a);
                break;
            }
            case BRAWLERS: {
                addBtn(B_BACK, padL + 10 * u, padT + 10 * u, padL + 230 * u, padT + 110 * u, "BACK", null, 0xffff5a5a);
                float top = padT + 150 * u, bottom = h - padB - 30 * u;
                float gap = 26 * u;
                float cw = (w - padL - padR - 40 * u - gap * 3) / 4f;
                for (int i = 0; i < Brawler.ALL.length; i++) {
                    float l = padL + 20 * u + i * (cw + gap);
                    addBtn(B_CARD + i, l, top, l + cw, bottom, null, null, 0);
                }
                break;
            }
            case PLAY: {
                if (paused) {
                    float cx = w / 2, cy = h / 2;
                    addBtn(B_RESUME, cx - 220 * u, cy - 30 * u, cx + 220 * u, cy + 100 * u, "RESUME", null, 0xff4ad04a);
                    addBtn(B_QUIT, cx - 220 * u, cy + 130 * u, cx + 220 * u, cy + 250 * u, "QUIT", null, 0xffff5a5a);
                }
                break;
            }
            case RESULT: {
                float cx = w / 2, b = h - padB - 40 * u;
                addBtn(B_MENU, cx - 470 * u, b - 130 * u, cx - 30 * u, b, "MENU", null, 0xff3fa0ff);
                addBtn(B_AGAIN, cx + 30 * u, b - 130 * u, cx + 470 * u, b, "PLAY AGAIN", null, 0xffffc928);
                break;
            }
            default:
                break;
        }
    }

    // ------------------------------------------------------------------ render

    private void render(Gfx g) {
        switch (screen) {
            case MENU:
                demo.render(g);
                renderMenu(g);
                break;
            case BRAWLERS:
                demo.render(g);
                renderBrawlers(g);
                break;
            case PLAY:
                world.render(g);
                renderHud(g);
                if (paused) renderPause(g);
                break;
            case RESULT:
            default:
                world.render(g);
                renderResult(g);
                break;
        }
    }

    private void drawButton(Gfx g, Btn b) {
        boolean pressed = pressedBtn == b.id;
        float l = b.l, t = b.t, r = b.r, bt = b.b;
        if (pressed) {
            float cx = (l + r) / 2, cy = (t + bt) / 2, s = 0.94f;
            l = cx + (l - cx) * s;
            r = cx + (r - cx) * s;
            t = cy + (t - cy) * s;
            bt = cy + (bt - cy) * s;
        }
        float rad = 22 * u;
        g.color(0x55000000);
        g.fillRoundRect(l + 4 * u, t + 10 * u, r + 4 * u, bt + 10 * u, rad);
        g.color(0xff14142a);
        g.fillRoundRect(l - 5 * u, t - 5 * u, r + 5 * u, bt + 5 * u, rad + 4 * u);
        g.color(MathUtil.darker(b.color, 0.35f));
        g.fillRoundRect(l, t, r, bt, rad);
        g.color(b.color);
        g.fillRoundRect(l, t, r, bt - 12 * u, rad);
        g.color(MathUtil.withAlpha(0xffffffff, 0.25f));
        g.fillRoundRect(l + 12 * u, t + 8 * u, r - 12 * u, t + (bt - t) * 0.35f, rad * 0.6f);
        float cy = (t + bt) / 2;
        g.color(0xffffffff);
        if (b.sub != null) {
            float size = Math.min(56 * u, (bt - t) * 0.42f);
            g.text(b.label, (l + r) / 2, cy + size * 0.2f, size, Gfx.ALIGN_CENTER, 7 * u, 0xff14142a);
            g.text(b.sub, (l + r) / 2, cy + size * 0.2f + size * 0.75f, size * 0.5f, Gfx.ALIGN_CENTER, 4 * u, 0xff14142a);
        } else {
            float size = Math.min(70 * u, (bt - t) * 0.5f);
            g.text(b.label, (l + r) / 2, cy + size * 0.32f, size, Gfx.ALIGN_CENTER, 8 * u, 0xff14142a);
        }
    }

    private void drawTrophy(Gfx g, float x, float y, float s) {
        g.color(0xff14142a);
        g.fillRoundRect(x - s * 0.62f, y - s * 0.62f, x + s * 0.62f, y + s * 0.05f, s * 0.3f);
        g.fillRoundRect(x - s * 0.14f, y - s * 0.05f, x + s * 0.14f, y + s * 0.45f, s * 0.05f);
        g.fillRoundRect(x - s * 0.44f, y + s * 0.32f, x + s * 0.44f, y + s * 0.62f, s * 0.1f);
        g.strokeCircle(x - s * 0.55f, y - s * 0.3f, s * 0.24f, s * 0.2f);
        g.strokeCircle(x + s * 0.55f, y - s * 0.3f, s * 0.24f, s * 0.2f);
        g.color(0xffffc928);
        g.strokeCircle(x - s * 0.55f, y - s * 0.3f, s * 0.24f, s * 0.09f);
        g.strokeCircle(x + s * 0.55f, y - s * 0.3f, s * 0.24f, s * 0.09f);
        g.fillRoundRect(x - s * 0.5f, y - s * 0.52f, x + s * 0.5f, y - s * 0.02f, s * 0.24f);
        g.fillRect(x - s * 0.07f, y - s * 0.05f, x + s * 0.07f, y + s * 0.38f);
        g.fillRoundRect(x - s * 0.34f, y + s * 0.38f, x + s * 0.34f, y + s * 0.54f, s * 0.06f);
        g.color(0xfffff2a8);
        g.fillRoundRect(x - s * 0.34f, y - s * 0.44f, x - s * 0.18f, y - s * 0.14f, s * 0.06f);
    }

    private void drawPanel(Gfx g, float l, float t, float r, float b, int color) {
        g.color(0x66000000);
        g.fillRoundRect(l + 6 * u, t + 12 * u, r + 6 * u, b + 12 * u, 30 * u);
        g.color(0xff0e1024);
        g.fillRoundRect(l - 6 * u, t - 6 * u, r + 6 * u, b + 6 * u, 32 * u);
        g.color(color);
        g.fillRoundRect(l, t, r, b, 26 * u);
    }

    /** A wavy snake used for previews. Positions are in the current transform. */
    private void drawSnakeArt(Gfx g, Brawler br, int c1, int c2, float cx, float cy, float scale, float time) {
        int n = 26;
        float r = 20f, sp = r * 0.55f;
        for (int i = 0; i < n; i++) {
            float x = -i * sp + n * sp * 0.5f;
            artX[i] = x;
            artY[i] = MathUtil.sin(time * 3f - i * 0.38f) * 26f * Math.min(1f, i / 6f + 0.15f);
        }
        g.save();
        g.translate(cx, cy);
        g.scale(scale);
        Snake.drawBody(g, artX, artY, n, r, c1, c2, null, 1f, false, 0, time, -1e5f, -1e5f, 1e5f, 1e5f);
        float ang = MathUtil.angleTo(artX[1], artY[1], artX[0], artY[0]);
        Snake.drawHead(g, artX[0], artY[0], r, ang, ang, c1, c2, br.id, br.accent, 1f, 0, time, 0);
        g.restore();
    }

    private void renderMenu(Gfx g) {
        g.color(0x88101236);
        g.fillRect(0, 0, w, h);

        // Title
        float ty = h * 0.3f;
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

        // Trophies
        float tl = padL + 20 * u, tt = padT + 14 * u;
        g.color(0xcc0e1024);
        g.fillRoundRect(tl, tt, tl + 300 * u, tt + 96 * u, 48 * u);
        drawTrophy(g, tl + 52 * u, tt + 48 * u, 56 * u);
        g.color(0xffffffff);
        g.text(Integer.toString(trophies), tl + 100 * u, tt + 68 * u, 58 * u, Gfx.ALIGN_LEFT, 6 * u, 0xff14142a);
        g.color(0xffd8dcff);
        g.text("Wins " + wins + "   Best length " + bestLen, tl + 10 * u, tt + 140 * u, 32 * u, Gfx.ALIGN_LEFT, 5 * u, 0xff14142a);

        // Selected brawler showcase
        Brawler b = Brawler.ALL[selected];
        float pl = padL + 30 * u, pb = h - padB - 150 * u, pt = pb - 250 * u, pr = pl + 470 * u;
        drawPanel(g, pl, pt, pr, pb, 0xdd22264a);
        g.color(b.color1);
        g.text(b.name, pl + 30 * u, pt + 70 * u, 64 * u, Gfx.ALIGN_LEFT, 7 * u, 0xff0e1024);
        g.color(0xffd8dcff);
        g.text(b.role, pr - 30 * u, pt + 64 * u, 38 * u, Gfx.ALIGN_RIGHT, 5 * u, 0xff0e1024);
        drawSnakeArt(g, b, b.color1, b.color2, (pl + pr) / 2, pt + 165 * u, 1.45f * u, clock);

        for (int i = 0; i < btnCount; i++) drawButton(g, btns[i]);
    }

    private void renderBrawlers(Gfx g) {
        g.color(0xcc0d1030);
        g.fillRect(0, 0, w, h);
        g.color(0xffffd23f);
        g.text("CHOOSE YOUR BRAWLER", w / 2, padT + 95 * u, 76 * u, Gfx.ALIGN_CENTER, 9 * u, 0xff14142a);
        for (int i = 0; i < btnCount; i++) {
            Btn bt = btns[i];
            if (bt.id == B_BACK) {
                drawButton(g, bt);
                continue;
            }
            int bi = bt.id - B_CARD;
            Brawler b = Brawler.ALL[bi];
            boolean sel = bi == selected;
            float l = bt.l, t = bt.t, r = bt.r, bb = bt.b;
            if (pressedBtn == bt.id) {
                l += 6 * u;
                r -= 6 * u;
                t += 6 * u;
                bb -= 6 * u;
            }
            float cw = r - l;
            float ts = Math.min(1f, cw / (440 * u));
            if (sel) {
                float glow = 0.5f + 0.5f * MathUtil.sin(clock * 5f);
                g.color(MathUtil.withAlpha(0xffffd23f, 0.5f + 0.4f * glow));
                g.fillRoundRect(l - 16 * u, t - 16 * u, r + 16 * u, bb + 16 * u, 40 * u);
            }
            drawPanel(g, l, t, r, bb, 0xff262a54);
            // Header band
            g.color(MathUtil.darker(b.color2, 0.2f));
            g.fillRoundRect(l, t, r, t + 96 * u, 26 * u);
            g.color(b.color1);
            g.fillRoundRect(l, t, r, t + 84 * u, 26 * u);
            g.color(0xffffffff);
            g.text(b.name, (l + r) / 2, t + 62 * u, 56 * u * ts, Gfx.ALIGN_CENTER, 7 * u, 0xff14142a);
            drawSnakeArt(g, b, b.color1, b.color2, (l + r) / 2, t + 185 * u, 1.25f * u * ts, clock + bi);
            g.color(0xffd8dcff);
            g.text(b.role.toUpperCase(), (l + r) / 2, t + 290 * u, 34 * u * ts, Gfx.ALIGN_CENTER, 5 * u, 0xff14142a);

            float sy = t + 330 * u;
            String[] labels = {"HEALTH", "DAMAGE", "RANGE", "SPEED"};
            int[] vals = {b.statHp, b.statDamage, b.statRange, b.statSpeed};
            for (int k = 0; k < 4; k++) {
                float y = sy + k * 44 * u;
                g.color(0xffb8bdf0);
                g.text(labels[k], l + 24 * u, y + 26 * u, 26 * u * ts, Gfx.ALIGN_LEFT, 4 * u, 0xff14142a);
                float px0 = l + cw * 0.45f, pw = (r - 24 * u - px0) / 5f;
                for (int p = 0; p < 5; p++) {
                    g.color(0xff14142a);
                    g.fillRoundRect(px0 + p * pw, y + 4 * u, px0 + (p + 1) * pw - 6 * u, y + 30 * u, 6 * u);
                    g.color(p < vals[k] ? 0xffffc928 : 0xff3a3e70);
                    g.fillRoundRect(px0 + p * pw + 3 * u, y + 7 * u, px0 + (p + 1) * pw - 9 * u, y + 27 * u, 4 * u);
                }
            }
            float ay = sy + 4 * 44 * u + 30 * u;
            ay = drawAbility(g, "ATTACK: " + b.attackName, b.attackDesc, l + 24 * u, r - 24 * u, ay, ts, 0xffff9a4a, bb);
            ay = drawAbility(g, "SUPER: " + b.superName, b.superDesc, l + 24 * u, r - 24 * u, ay + 14 * u, ts, 0xffffd23f, bb);
            if (sel) {
                g.color(0xff14142a);
                g.fillRoundRect((l + r) / 2 - 120 * u, bb - 30 * u, (l + r) / 2 + 120 * u, bb + 22 * u, 20 * u);
                g.color(0xffffd23f);
                g.text("SELECTED", (l + r) / 2, bb + 10 * u, 34 * u, Gfx.ALIGN_CENTER, 0, 0);
            } else if (ay < bb - 70 * u) {
                g.color(MathUtil.withAlpha(0xffb8bdf0, 0.6f + 0.3f * MathUtil.sin(clock * 3f + bi)));
                g.text("TAP TO SELECT", (l + r) / 2, bb - 34 * u, 30 * u * ts, Gfx.ALIGN_CENTER, 4 * u, 0xff14142a);
            }
        }
    }

    private float drawAbility(Gfx g, String title, String desc, float l, float r, float y, float ts, int color, float limit) {
        float size = 28 * u * ts;
        if (y + size > limit - 30 * u) return y;
        g.color(color);
        g.text(title, l, y + size, size, Gfx.ALIGN_LEFT, 4 * u, 0xff14142a);
        y += size + 8 * u;
        float ds = 25 * u * ts;
        g.color(0xffe8eaff);
        String[] words = desc.split(" ");
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String test = line.length() == 0 ? words[i] : line + " " + words[i];
            if (g.measureText(test, ds) > r - l && line.length() > 0) {
                if (y + ds > limit - 30 * u) return y;
                g.text(line.toString(), l, y + ds, ds, Gfx.ALIGN_LEFT, 3 * u, 0xff14142a);
                y += ds + 6 * u;
                line.setLength(0);
                line.append(words[i]);
            } else {
                line.setLength(0);
                line.append(test);
            }
        }
        if (line.length() > 0 && y + ds <= limit - 30 * u) {
            g.text(line.toString(), l, y + ds, ds, Gfx.ALIGN_LEFT, 3 * u, 0xff14142a);
            y += ds + 6 * u;
        }
        return y;
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

        if (hintGames < 3 && wd.matchTime < 12f && p.alive) {
            float a = Math.min(1f, (12f - wd.matchTime) * 0.8f);
            g.color(MathUtil.withAlpha(0xaa0e1024, a));
            g.fillRoundRect(cx - 560 * u, h * 0.62f - 46 * u, cx + 560 * u, h * 0.62f + 64 * u, 26 * u);
            g.color(MathUtil.withAlpha(0xffffffff, a));
            g.text("Drag LEFT side to steer  •  Right stick: tap = auto-aim, drag = aim", cx, h * 0.62f, 32 * u,
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
        g.color(movePtr >= 0 ? 0x553a3f7a : 0x332a2f5a);
        g.fillCircle(mx, my, moveR);
        g.color(0x66ffffff);
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
        g.color(0xff2b6fd6);
        g.fillCircle(kx, ky, moveR * 0.45f + 4 * u);
        g.color(0xff4ea4ff);
        g.fillCircle(kx, ky, moveR * 0.45f);

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
        g.color(0x44ff5a3a);
        g.fillCircle(ax, ay, atkR * 1.25f);
        g.color(0x88ffffff);
        g.strokeCircle(ax, ay, atkR * 1.25f, 4 * u);
        if (atkPtr >= 0) drawStickKnob(g, atkOx, atkOy, atkX, atkY, atkR, 0xffff5a3a);
        else {
            g.color(0xff8a1f1f);
            g.fillCircle(ax, ay, atkR * 0.5f + 4 * u);
            g.color(p.ammo >= 1 ? 0xffff5a3a : 0xff8a5a5a);
            g.fillCircle(ax, ay, atkR * 0.5f);
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
        g.text("PAUSED", w / 2, h / 2 - 110 * u, 110 * u, Gfx.ALIGN_CENTER, 12 * u, 0xff14142a);
        for (int i = 0; i < btnCount; i++) drawButton(g, btns[i]);
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
        g.text(title, 0, 0, 130 * u, Gfx.ALIGN_CENTER, 14 * u, 0xff14142a);
        g.restore();

        float pw = 860 * u, ph = 400 * u;
        float pl = cx - pw / 2, pt = padT + 210 * u;
        float maxB = h - padB - 200 * u;
        if (pt + ph > maxB) ph = maxB - pt;
        drawPanel(g, pl, pt, pl + pw, pt + ph, 0xee22264a);
        Brawler b = world.player.type;
        drawSnakeArt(g, b, b.color1, b.color2, pl + 175 * u, pt + ph / 2, 0.95f * u, clock);

        float sx = pl + 380 * u, sy = pt + 80 * u, row = Math.min(64 * u, (ph - 60 * u) / 4.5f);
        g.color(0xffd8dcff);
        g.text("KNOCKOUTS", sx, sy, 36 * u, Gfx.ALIGN_LEFT, 5 * u, 0xff14142a);
        g.color(0xffffffff);
        g.text(Integer.toString(resKills), pl + pw - 50 * u, sy, 44 * u, Gfx.ALIGN_RIGHT, 5 * u, 0xff14142a);
        sy += row;
        g.color(0xffd8dcff);
        g.text("LENGTH", sx, sy, 36 * u, Gfx.ALIGN_LEFT, 5 * u, 0xff14142a);
        g.color(resNewBest ? 0xffffd23f : 0xffffffff);
        g.text((resNewBest ? "NEW BEST! " : "") + resLength, pl + pw - 50 * u, sy, 44 * u, Gfx.ALIGN_RIGHT, 5 * u, 0xff14142a);
        sy += row;
        g.color(0xffd8dcff);
        g.text("POWER CUBES", sx, sy, 36 * u, Gfx.ALIGN_LEFT, 5 * u, 0xff14142a);
        g.color(0xff6dff6d);
        g.text(Integer.toString(resCubes), pl + pw - 50 * u, sy, 44 * u, Gfx.ALIGN_RIGHT, 5 * u, 0xff14142a);
        sy += row;
        g.color(0xffd8dcff);
        g.text("SURVIVED", sx, sy, 36 * u, Gfx.ALIGN_LEFT, 5 * u, 0xff14142a);
        int secs = (int) resTime;
        g.color(0xffffffff);
        g.text(secs / 60 + ":" + (secs % 60 < 10 ? "0" : "") + secs % 60, pl + pw - 50 * u, sy, 44 * u, Gfx.ALIGN_RIGHT, 5 * u, 0xff14142a);
        sy += row;
        drawTrophy(g, sx + 26 * u, sy - 14 * u, 46 * u);
        g.color(resDelta > 0 ? 0xff9cff8a : (resDelta < 0 ? 0xffff7a6a : 0xffffffff));
        g.text((resDelta > 0 ? "+" : "") + resDelta, sx + 70 * u, sy, 50 * u, Gfx.ALIGN_LEFT, 6 * u, 0xff14142a);
        g.color(0xffffffff);
        g.text("Total " + trophies, pl + pw - 50 * u, sy, 40 * u, Gfx.ALIGN_RIGHT, 5 * u, 0xff14142a);

        for (int i = 0; i < btnCount; i++) drawButton(g, btns[i]);
    }
}
