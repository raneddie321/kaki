package com.snakebrawl.myapp.game;

/** One arena: map, snakes, projectiles, food, poison and effects. */
final class World {
    static final int MODE_SHOWDOWN = 0;
    static final int MODE_ENDLESS = 1;
    static final int MODE_DEMO = 2;

    static final float T = 64f;
    static final byte EMPTY = 0, WALL = 1, BOX = 2;

    static final int CAUSE_SHOT = 0, CAUSE_CRASH = 1, CAUSE_POISON = 2, CAUSE_DASH = 3;

    static final float ZONE_START = 25f;
    static final float ZONE_END = 205f;
    static final float REVEAL_DIST = 230f;
    static final float PLAYER_DAMAGE_TAKEN = 0.55f;
    static final float PLAYER_DAMAGE_DEALT = 1.3f;

    final Platform platform;
    final int mode;
    final int n;
    final float size;
    final byte[] tiles;
    final boolean[] bush;

    final Snake[] snakes;
    final int snakeCount;
    final Snake player;
    final Particles fx = new Particles();
    final SegGrid grid;
    private final int[] q = new int[6000];

    // Food orbs
    static final int MAX_ORBS = 2400;
    final float[] ox = new float[MAX_ORBS], oy = new float[MAX_ORBS], ov = new float[MAX_ORBS];
    final float[] orad = new float[MAX_ORBS], ophase = new float[MAX_ORBS], oage = new float[MAX_ORBS];
    final int[] ocol = new int[MAX_ORBS], oowner = new int[MAX_ORBS];
    int orbCount;
    final int orbTarget;

    // Power cubes lying on the ground
    static final int MAX_CUBES = 80;
    final float[] cubeX = new float[MAX_CUBES], cubeY = new float[MAX_CUBES], cubeAge = new float[MAX_CUBES];
    int cubeCount;

    // Power cube boxes
    static final float BOX_HP = 3200f;
    final int[] boxTile = new int[64];
    final float[] boxHp = new float[64], boxFlash = new float[64];
    int boxCount;
    final int boxTarget;
    float boxRespawnTimer;

    final Projectile[] proj = new Projectile[360];

    // Poison zone (showdown)
    float zoneL, zoneT, zoneR, zoneB;
    float matchTime;
    boolean zoneWarned;

    // Kill feed
    static final int FEED = 5;
    final String[] feedA = new String[FEED], feedB = new String[FEED];
    final int[] feedColA = new int[FEED], feedColB = new int[FEED], feedCause = new int[FEED];
    final float[] feedTime = new float[FEED];
    int feedCount;

    // Results / events read by Game
    boolean playerDied;
    int aliveCount;
    float shake;
    String bannerText;
    float bannerTime;

    // Camera
    float camX, camY, zoom = 1f, viewW = 1920, viewH = 1080;
    Snake focus;
    private float focusTimer;

    // Aim preview (set by Game while the player drags an attack stick)
    boolean aimActive, aimSuper;
    float aimAng, aimDist;

    private final float[] tmpPoly = new float[32];
    private final float[] poly4 = new float[8];
    private final boolean[] hiddenBuf = new boolean[Snake.MAX_SEG];
    private final Snake[] drawOrder;
    private final float time0 = MathUtil.rand(0, 100);
    float time;

    private static final int[] ORB_COLORS = {
            0xffff5a5a, 0xffffb13b, 0xfffff04a, 0xff7cff5a, 0xff3ff0ff, 0xff5a8cff,
            0xffc65aff, 0xffff5ad7, 0xffffffff,
    };

    /** Player-facing options (set by Game). */
    boolean showDamage = true;
    boolean lowGraphics;
    float zoomMult = 1f;
    private final int botTrophies;

    World(Platform platform, int mode, Brawler playerType, int[] playerPalette, int playerLevel, int botCount, int trophies) {
        this.platform = platform;
        this.botTrophies = trophies;
        this.mode = mode;
        this.n = mode == MODE_SHOWDOWN ? 66 : (mode == MODE_ENDLESS ? 80 : 56);
        this.size = n * T;
        this.tiles = new byte[n * n];
        this.bush = new boolean[n * n];
        this.grid = new SegGrid(size);
        this.orbTarget = mode == MODE_SHOWDOWN ? 480 : (mode == MODE_ENDLESS ? 760 : 420);
        this.boxTarget = mode == MODE_SHOWDOWN ? 14 : (mode == MODE_ENDLESS ? 18 : 8);
        for (int i = 0; i < proj.length; i++) proj[i] = new Projectile();

        boolean hasPlayer = playerType != null;
        snakeCount = botCount + (hasPlayer ? 1 : 0);
        snakes = new Snake[snakeCount];
        drawOrder = new Snake[snakeCount];
        for (int i = 0; i < snakeCount; i++) {
            snakes[i] = new Snake(i);
            drawOrder[i] = snakes[i];
        }

        // Spawn points on a ring so nobody starts on top of someone else
        float[] spX = new float[snakeCount], spY = new float[snakeCount], spA = new float[snakeCount];
        float a0 = MathUtil.rand(0, MathUtil.TAU);
        for (int i = 0; i < snakeCount; i++) {
            float a = a0 + MathUtil.TAU * i / snakeCount;
            spX[i] = size / 2 + MathUtil.cos(a) * size * 0.36f;
            spY[i] = size / 2 + MathUtil.sin(a) * size * 0.36f;
            spA[i] = a + MathUtil.PI;
        }
        generateMap(spX, spY);

        zoneL = 0;
        zoneT = 0;
        zoneR = size;
        zoneB = size;

        String[] names = shuffledNames();
        float startMass = mode == MODE_SHOWDOWN ? 40f : 45f;
        for (int i = 0; i < snakeCount; i++) {
            Snake s = snakes[i];
            if (hasPlayer && i == 0) {
                s.isPlayer = true;
                s.name = "You";
                s.setColors(playerPalette != null ? playerPalette : new int[]{playerType.color1, playerType.color2});
                s.level = Math.max(1, Math.min(Brawler.MAX_LEVEL, playerLevel));
                s.spawn(playerType, spX[i], spY[i], spA[i], startMass);
            } else {
                s.name = names[i % names.length];
                dressBot(s, i);
                Brawler b = Brawler.ALL[MathUtil.randInt(Brawler.ALL.length)];
                float m = mode == MODE_SHOWDOWN ? startMass : MathUtil.rand(45, 260);
                s.spawn(b, spX[i], spY[i], spA[i], m);
                s.brain = new BotBrain(this, s, trophies);
            }
        }
        player = hasPlayer ? snakes[0] : null;
        focus = hasPlayer ? player : snakes[0];
        camX = focus.hx();
        camY = focus.hy();

        for (int i = 0; i < orbTarget; i++) spawnNaturalOrb();
        for (int i = 0; i < boxTarget; i++) spawnBox();
        countAlive();
        if (mode == MODE_SHOWDOWN) banner("SHOWDOWN!");
        else if (mode == MODE_ENDLESS) banner("ENDLESS BRAWL!");
    }

    /** Bots get fancier skins and higher power levels as the player's trophies grow. */
    private void dressBot(Snake s, int i) {
        float fancy = 0.15f + Math.min(0.6f, botTrophies / 800f);
        if (MathUtil.rand() < fancy) s.setColors(Skin.ALL[1 + MathUtil.randInt(Skin.ALL.length - 1)].palette);
        else s.setColors(Brawler.BOT_SKINS[(i + MathUtil.randInt(Brawler.BOT_SKINS.length)) % Brawler.BOT_SKINS.length]);
        int lvl = 1 + botTrophies / 250 + (MathUtil.rand() < 0.3f ? 1 : 0) - (MathUtil.rand() < 0.4f ? 1 : 0);
        s.level = Math.max(1, Math.min(Brawler.MAX_LEVEL, lvl));
    }

    private static String[] shuffledNames() {
        String[] a = Brawler.BOT_NAMES.clone();
        for (int i = a.length - 1; i > 0; i--) {
            int j = MathUtil.randInt(i + 1);
            String t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
        return a;
    }

    void banner(String s) {
        bannerText = s;
        bannerTime = 2.2f;
    }

    // ------------------------------------------------------------------ map

    private void generateMap(float[] spX, float[] spY) {
        int clusters = n * n / 160;
        for (int c = 0, tries = 0; c < clusters && tries < clusters * 20; tries++) {
            int tx = 3 + MathUtil.randInt(n - 6), ty = 3 + MathUtil.randInt(n - 6);
            int shape = MathUtil.randInt(6);
            int[] cells = shapeCells(shape, tx, ty);
            if (cells == null) continue;
            boolean ok = true;
            for (int k = 0; k < cells.length && ok; k += 2) {
                int x = cells[k], y = cells[k + 1];
                if (x < 2 || y < 2 || x >= n - 2 || y >= n - 2) ok = false;
                else if (nearSpawn(x, y, spX, spY, 4.5f)) ok = false;
                else {
                    for (int dy = -1; dy <= 1 && ok; dy++)
                        for (int dx = -1; dx <= 1 && ok; dx++)
                            if (tiles[(y + dy) * n + x + dx] != EMPTY && !inCells(cells, x + dx, y + dy)) ok = false;
                }
            }
            if (!ok) continue;
            for (int k = 0; k < cells.length; k += 2) tiles[cells[k + 1] * n + cells[k]] = WALL;
            c++;
        }
        int patches = n * n / 150;
        for (int p = 0; p < patches; p++) {
            int x = 2 + MathUtil.randInt(n - 4), y = 2 + MathUtil.randInt(n - 4);
            int steps = 8 + MathUtil.randInt(16);
            for (int s = 0; s < steps; s++) {
                for (int dy = 0; dy <= 1; dy++)
                    for (int dx = 0; dx <= 1; dx++) {
                        int bx = x + dx, by = y + dy;
                        if (bx > 0 && by > 0 && bx < n - 1 && by < n - 1 && tiles[by * n + bx] == EMPTY)
                            bush[by * n + bx] = true;
                    }
                int d = MathUtil.randInt(4);
                if (d == 0) x++;
                else if (d == 1) x--;
                else if (d == 2) y++;
                else y--;
                x = Math.max(2, Math.min(n - 3, x));
                y = Math.max(2, Math.min(n - 3, y));
            }
        }
    }

    private static boolean inCells(int[] cells, int x, int y) {
        for (int k = 0; k < cells.length; k += 2) if (cells[k] == x && cells[k + 1] == y) return true;
        return false;
    }

    private boolean nearSpawn(int tx, int ty, float[] spX, float[] spY, float tilesDist) {
        float cx = (tx + 0.5f) * T, cy = (ty + 0.5f) * T;
        float d2 = tilesDist * T * tilesDist * T;
        for (int i = 0; i < spX.length; i++) if (MathUtil.dist2(cx, cy, spX[i], spY[i]) < d2) return true;
        return false;
    }

    private static int[] shapeCells(int shape, int x, int y) {
        int len = 3 + MathUtil.randInt(4);
        switch (shape) {
            case 0: {
                int[] c = new int[len * 2];
                for (int i = 0; i < len; i++) {
                    c[i * 2] = x + i;
                    c[i * 2 + 1] = y;
                }
                return c;
            }
            case 1: {
                int[] c = new int[len * 2];
                for (int i = 0; i < len; i++) {
                    c[i * 2] = x;
                    c[i * 2 + 1] = y + i;
                }
                return c;
            }
            case 2: {
                int[] c = new int[(len * 2 - 1) * 2];
                int k = 0;
                for (int i = 0; i < len; i++) {
                    c[k++] = x + i;
                    c[k++] = y;
                }
                for (int i = 1; i < len; i++) {
                    c[k++] = x;
                    c[k++] = y + i;
                }
                return c;
            }
            case 3:
                return new int[]{x, y, x + 1, y, x, y + 1, x + 1, y + 1};
            case 4:
                return new int[]{x, y, x + 1, y, x + 2, y, x + 1, y + 1, x + 1, y - 1};
            default:
                return new int[]{x, y, x + 1, y, x + 2, y, x, y + 1, x + 2, y + 1};
        }
    }

    boolean solidTile(int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= n || ty >= n) return true;
        return tiles[ty * n + tx] != EMPTY;
    }

    boolean solidAt(float x, float y) {
        if (x < 0 || y < 0 || x >= size || y >= size) return true;
        return tiles[(int) (y / T) * n + (int) (x / T)] != EMPTY;
    }

    /** True if a circle at (x, y) touches a wall, box or the arena edge. */
    boolean solidCircle(float x, float y, float r) {
        if (x < r || y < r || x > size - r || y > size - r) return true;
        int tx0 = (int) ((x - r) / T), tx1 = (int) ((x + r) / T);
        int ty0 = (int) ((y - r) / T), ty1 = (int) ((y + r) / T);
        for (int ty = ty0; ty <= ty1; ty++) {
            for (int tx = tx0; tx <= tx1; tx++) {
                if (!solidTile(tx, ty)) continue;
                float cx = MathUtil.clamp(x, tx * T, tx * T + T);
                float cy = MathUtil.clamp(y, ty * T, ty * T + T);
                if (MathUtil.dist2(x, y, cx, cy) < r * r) return true;
            }
        }
        return false;
    }

    boolean bushAt(float x, float y) {
        if (x < 0 || y < 0 || x >= size || y >= size) return false;
        return bush[(int) (y / T) * n + (int) (x / T)];
    }

    boolean inZone(float x, float y) {
        return x >= zoneL && x <= zoneR && y >= zoneT && y <= zoneB;
    }

    boolean zoneActive() {
        return mode == MODE_SHOWDOWN && matchTime > ZONE_START;
    }

    private void spawnBox() {
        if (boxCount >= boxTile.length) return;
        for (int tries = 0; tries < 200; tries++) {
            int tx = 3 + MathUtil.randInt(n - 6), ty = 3 + MathUtil.randInt(n - 6);
            int idx = ty * n + tx;
            if (tiles[idx] != EMPTY || bush[idx]) continue;
            float cx = (tx + 0.5f) * T, cy = (ty + 0.5f) * T;
            if (!inZone(cx, cy)) continue;
            boolean nearSnake = false;
            for (int i = 0; i < snakeCount && !nearSnake; i++) {
                Snake s = snakes[i];
                if (s.alive && cx > s.minX - T && cx < s.maxX + T && cy > s.minY - T && cy < s.maxY + T) nearSnake = true;
            }
            if (nearSnake) continue;
            tiles[idx] = BOX;
            boxTile[boxCount] = idx;
            boxHp[boxCount] = BOX_HP;
            boxFlash[boxCount] = 0;
            boxCount++;
            return;
        }
    }

    private int boxAt(int tileIdx) {
        for (int i = 0; i < boxCount; i++) if (boxTile[i] == tileIdx) return i;
        return -1;
    }

    void damageBox(int tileIdx, float dmg, Snake by) {
        int b = boxAt(tileIdx);
        if (b < 0) return;
        boxHp[b] -= dmg;
        boxFlash[b] = 0.12f;
        float cx = (tileIdx % n + 0.5f) * T, cy = (tileIdx / n + 0.5f) * T;
        if (showDamage && by != null && (by.isPlayer || isNearCamera(cx, cy))) fx.text(cx, cy - 30, Integer.toString((int) dmg), 0xffffffff, 26);
        if (boxHp[b] <= 0) {
            tiles[tileIdx] = EMPTY;
            boxTile[b] = boxTile[boxCount - 1];
            boxHp[b] = boxHp[boxCount - 1];
            boxFlash[b] = boxFlash[boxCount - 1];
            boxCount--;
            fx.burst(cx, cy, 24, 0xffb9773a, 420, 9, 0.7f);
            fx.burst(cx, cy, 10, 0xff6b3f1a, 300, 7, 0.6f);
            fx.ring(cx, cy, 90, 0xffffe08a, 0.4f);
            dropCube(cx, cy);
            for (int k = 0; k < 8; k++) {
                float a = MathUtil.rand(0, MathUtil.TAU);
                addOrb(cx + MathUtil.cos(a) * 40, cy + MathUtil.sin(a) * 40, 3, ORB_COLORS[MathUtil.randInt(ORB_COLORS.length)], -1);
            }
            sound(Platform.SND_BOX, cx, cy, 1f);
        }
    }

    // ------------------------------------------------------------------ orbs

    void addOrb(float x, float y, float value, int color, int owner) {
        if (orbCount >= MAX_ORBS) return;
        x = MathUtil.clamp(x, 10, size - 10);
        y = MathUtil.clamp(y, 10, size - 10);
        int i = orbCount++;
        ox[i] = x;
        oy[i] = y;
        ov[i] = value;
        orad[i] = Math.min(17f, 4.5f + MathUtil.sqrt(value) * 2.4f);
        ocol[i] = color;
        ophase[i] = MathUtil.rand(0, MathUtil.TAU);
        oowner[i] = owner;
        oage[i] = 0;
    }

    private void removeOrb(int i) {
        int last = --orbCount;
        ox[i] = ox[last];
        oy[i] = oy[last];
        ov[i] = ov[last];
        orad[i] = orad[last];
        ocol[i] = ocol[last];
        ophase[i] = ophase[last];
        oowner[i] = oowner[last];
        oage[i] = oage[last];
    }

    private void spawnNaturalOrb() {
        for (int tries = 0; tries < 10; tries++) {
            float x = MathUtil.rand(20, size - 20), y = MathUtil.rand(20, size - 20);
            if (solidAt(x, y)) continue;
            if (zoneActive() && !inZone(x, y)) continue;
            addOrb(x, y, 1 + MathUtil.randInt(3), ORB_COLORS[MathUtil.randInt(ORB_COLORS.length)], -1);
            return;
        }
    }

    void dropCube(float x, float y) {
        if (cubeCount >= MAX_CUBES) return;
        cubeX[cubeCount] = MathUtil.clamp(x, 30, size - 30);
        cubeY[cubeCount] = MathUtil.clamp(y, 30, size - 30);
        cubeAge[cubeCount] = 0;
        cubeCount++;
    }

    // ------------------------------------------------------------------ update

    /** Brief freeze after the player lands a knockout, for punch. */
    float hitStop;
    private int streak;
    private float streakTimer;

    void update(float dt) {
        if (hitStop > 0) {
            hitStop -= dt;
            return;
        }
        if (streakTimer > 0 && (streakTimer -= dt) <= 0) streak = 0;
        time += dt;
        matchTime += dt;
        if (bannerTime > 0) bannerTime -= dt;
        if (shake > 0) shake = Math.max(0, shake - dt * 30);
        for (int i = 0; i < feedCount; i++) feedTime[i] += dt;
        while (feedCount > 0 && feedTime[0] > 5f) shiftFeed();

        updateZone();

        for (int i = 0; i < snakeCount; i++) {
            Snake s = snakes[i];
            if (!s.alive) {
                s.deadTime += dt;
                if (mode != MODE_SHOWDOWN && !s.isPlayer && s.deadTime > 3.5f) respawnBot(s);
                continue;
            }
            if (s.brain != null) s.brain.update(dt);
            updateSnake(s, dt);
        }

        grid.build(snakes, snakeCount);
        checkCollisions(dt);
        eatAndPickup(dt);
        updateProjectiles(dt);
        updateAreas(dt);

        for (int i = 0; i < boxCount; i++) if (boxFlash[i] > 0) boxFlash[i] -= dt;
        if (mode != MODE_SHOWDOWN && boxCount < boxTarget) {
            boxRespawnTimer += dt;
            if (boxRespawnTimer > 12f) {
                boxRespawnTimer = 0;
                spawnBox();
            }
        }
        int spawnBudget = 6;
        while (orbCount < orbTarget && spawnBudget-- > 0) spawnNaturalOrb();
        for (int i = 0; i < orbCount; i++) oage[i] += dt;
        for (int i = 0; i < cubeCount; i++) cubeAge[i] += dt;

        fx.update(dt);
        countAlive();
        updateFocus(dt);
        updateReveals();
        updateLock(dt);
    }

    /** Pops a warning when a hidden enemy (bush or invisibility) is suddenly revealed. */
    private void updateReveals() {
        Snake p = player;
        if (p == null || !p.alive) return;
        for (int i = 0; i < snakeCount; i++) {
            Snake s = snakes[i];
            if (s == p || !s.alive) continue;
            boolean hidden = isHiddenFromViewer(s, p, s.hx(), s.hy());
            if (s.wasHidden && !hidden) {
                float hr = s.radius * 1.18f;
                fx.ring(s.hx(), s.hy(), hr * 3.2f, 0xffff4a4a, 0.45f);
                fx.flash(s.hx(), s.hy(), hr * 3f, 0xffff4a4a, 0.25f);
                fx.text(s.hx(), s.hy() - hr - 64, "!", 0xffff4a4a, 64);
                fx.burst(s.hx(), s.hy(), 10, 0xff3f9b3c, 260, 7, 0.45f);
                sound(Platform.SND_CLICK, s.hx(), s.hy(), 0.8f);
            }
            s.wasHidden = hidden;
        }
    }

    private void updateZone() {
        if (mode != MODE_SHOWDOWN) return;
        float t = MathUtil.clamp((matchTime - ZONE_START) / (ZONE_END - ZONE_START), 0, 1);
        float half = size / 2 * (1f - t);
        zoneL = size / 2 - half;
        zoneR = size / 2 + half;
        zoneT = size / 2 - half;
        zoneB = size / 2 + half;
        if (!zoneWarned && matchTime > ZONE_START - 4) {
            zoneWarned = true;
            banner("POISON IS CLOSING IN!");
        }
    }

    private void updateSnake(Snake s, float dt) {
        if (s.dmgTimer > 0) {
            s.dmgTimer -= dt;
            if (s.dmgTimer <= 0) flushDamageText(s);
        }
        if (s.spawnShield > 0) s.spawnShield -= dt;
        if (s.revealTime > 0) s.revealTime -= dt;
        if (s.invisTime > 0) s.invisTime -= dt;
        if (s.slowTime > 0) {
            s.slowTime -= dt;
            if (s.slowTime <= 0) s.slowFactor = 1f;
            else if (MathUtil.rand() < dt * 8f) fx.add(Particles.DOT, s.hx() + MathUtil.rand(-15, 15), s.hy() + MathUtil.rand(-15, 15), 0, -30, 5, 0xffbff0ff, 0.5f);
        }
        if (s.hitFlash > 0) s.hitFlash -= dt;
        if (s.fireCooldown > 0) s.fireCooldown -= dt;
        if (s.bumpCooldown > 0) s.bumpCooldown -= dt;
        s.sinceDamaged += dt;
        s.sinceAttack += dt;
        if (s.ammo < 3f) s.ammo = Math.min(3f, s.ammo + dt / s.type.reload);
        if (s.tongueTimer > 0) s.tongueTimer -= dt;
        else if (MathUtil.rand() < dt * 0.4f) s.tongueTimer = 0.35f;

        if (s.dashTime > 0) {
            s.dashTime -= dt;
            if (MathUtil.rand() < 0.8f) fx.add(Particles.SMOKE, s.hx(), s.hy(), MathUtil.rand(-30, 30), MathUtil.rand(-30, 30), s.radius * 1.1f, 0xffff7a2a, 0.45f);
        }

        if (s.burstLeft > 0) {
            s.burstTimer -= dt;
            while (s.burstTimer <= 0 && s.burstLeft > 0) {
                fireBurstShot(s);
                s.burstLeft--;
                s.burstTimer += s.burstInterval;
            }
        }

        // Regeneration after a few quiet seconds
        float quiet = s.isPlayer ? 2f : 3f;
        if (s.sinceDamaged > quiet && s.sinceAttack > quiet && s.hp < s.maxHp && !(zoneActive() && !inZone(s.hx(), s.hy()))) {
            s.hp = Math.min(s.maxHp, s.hp + s.maxHp * (s.isPlayer ? 0.2f : 0.12f) * dt);
        }

        // Poison
        if (zoneActive() && !inZone(s.hx(), s.hy())) {
            s.poisonTime += dt;
            float dps = 700f + 120f * s.poisonTime;
            s.poisonTick += dt;
            if (s.poisonTick >= 0.5f) {
                s.poisonTick -= 0.5f;
                float dmg = dps * 0.5f;
                if (showDamage && (s.isPlayer || isNearCamera(s.hx(), s.hy()))) fx.text(s.hx(), s.hy() - s.radius - 50, Integer.toString((int) dmg), 0xff8cff5a, 30);
                hurt(s, dmg, null, s.hx(), s.hy(), 0, 0, CAUSE_POISON);
                if (!s.alive) return;
                fx.smoke(s.hx(), s.hy(), 3, 0xff4ad04a, 26, 0.8f);
            }
        } else {
            s.poisonTime = 0;
            s.poisonTick = 0;
        }

        // Boost burns length and leaves a trail of food
        s.move(dt);
        if (s.boosting) {
            s.mass -= 9f * dt;
            s.boostDropTimer -= dt;
            if (s.boostDropTimer <= 0) {
                s.boostDropTimer = 0.14f;
                int t = s.segs - 1;
                addOrb(s.sx[t] + MathUtil.rand(-6, 6), s.sy[t] + MathUtil.rand(-6, 6), 1.1f, s.color1, s.index);
            }
        }
        pushOutOfWalls(s);
        s.followBody();
        s.headInBush = bushAt(s.hx(), s.hy());

        float newMax = s.computeMaxHp();
        if (newMax > s.maxHp) s.hp += newMax - s.maxHp;
        s.maxHp = newMax;
        if (s.hp > s.maxHp) s.hp = s.maxHp;

        boolean ready = s.superReady();
        if (ready && !s.superWasReady && s.isPlayer) sound(Platform.SND_SUPER_READY, s.hx(), s.hy(), 0.9f);
        s.superWasReady = ready;
    }

    private void pushOutOfWalls(Snake s) {
        float r = s.radius * 0.9f;
        float x = s.sx[0], y = s.sy[0];
        boolean hit = false;
        if (x < r) { x = r; hit = true; }
        if (y < r) { y = r; hit = true; }
        if (x > size - r) { x = size - r; hit = true; }
        if (y > size - r) { y = size - r; hit = true; }
        for (int pass = 0; pass < 2; pass++) {
            int tx0 = (int) ((x - r) / T), tx1 = (int) ((x + r) / T);
            int ty0 = (int) ((y - r) / T), ty1 = (int) ((y + r) / T);
            for (int ty = ty0; ty <= ty1; ty++) {
                for (int tx = tx0; tx <= tx1; tx++) {
                    if (tx < 0 || ty < 0 || tx >= n || ty >= n || tiles[ty * n + tx] == EMPTY) continue;
                    float l = tx * T, t = ty * T;
                    float cx = MathUtil.clamp(x, l, l + T), cy = MathUtil.clamp(y, t, t + T);
                    float dx = x - cx, dy = y - cy;
                    float d2 = dx * dx + dy * dy;
                    if (d2 >= r * r) continue;
                    hit = true;
                    if (d2 > 1e-4f) {
                        float d = (float) Math.sqrt(d2);
                        x += dx / d * (r - d);
                        y += dy / d * (r - d);
                    } else {
                        float pl = x - l, pr = l + T - x, pt = y - t, pb = t + T - y;
                        float m = Math.min(Math.min(pl, pr), Math.min(pt, pb));
                        if (m == pl) x = l - r;
                        else if (m == pr) x = l + T + r;
                        else if (m == pt) y = t - r;
                        else y = t + T + r;
                    }
                }
            }
        }
        s.sx[0] = x;
        s.sy[0] = y;
        if (hit && s.dashTime > 0) s.dashTime = Math.min(s.dashTime, 0.05f);
    }

    private void respawnBot(Snake s) {
        for (int tries = 0; tries < 60; tries++) {
            float x = MathUtil.rand(300, size - 300), y = MathUtil.rand(300, size - 300);
            if (solidCircle(x, y, 120)) continue;
            boolean crowded = false;
            for (int i = 0; i < snakeCount && !crowded; i++) {
                Snake o = snakes[i];
                if (o.alive && x > o.minX - 350 && x < o.maxX + 350 && y > o.minY - 350 && y < o.maxY + 350) crowded = true;
            }
            if (crowded) continue;
            if (player != null && player.alive && MathUtil.dist2(x, y, player.hx(), player.hy()) < 700 * 700) continue;
            dressBot(s, MathUtil.randInt(100));
            s.name = Brawler.BOT_NAMES[MathUtil.randInt(Brawler.BOT_NAMES.length)];
            s.spawn(Brawler.ALL[MathUtil.randInt(Brawler.ALL.length)], x, y, MathUtil.rand(0, MathUtil.TAU), MathUtil.rand(45, 220));
            if (s.brain != null) s.brain.reset();
            return;
        }
    }

    private void countAlive() {
        int c = 0;
        for (int i = 0; i < snakeCount; i++) if (snakes[i].alive) c++;
        aliveCount = c;
    }

    private void updateFocus(float dt) {
        if (player != null) {
            focus = player;
            return;
        }
        focusTimer -= dt;
        if (focus == null || !focus.alive && focus.deadTime > 1.5f || focusTimer <= 0) {
            Snake best = null;
            for (int i = 0; i < snakeCount; i++) {
                Snake s = snakes[i];
                if (!s.alive) continue;
                if (best == null || s.mass + s.kills * 50 > best.mass + best.kills * 50) best = s;
            }
            if (best != null) focus = best;
            focusTimer = 12f;
        }
    }

    // ------------------------------------------------------------------ collisions

    private void checkCollisions(float dt) {
        for (int ai = 0; ai < snakeCount; ai++) {
            Snake a = snakes[ai];
            if (!a.alive) continue;
            float hx = a.hx(), hy = a.hy();
            int cnt = grid.query(hx, hy, a.radius + 40f, q);
            for (int k = 0; k < cnt && a.alive; k++) {
                int id = q[k];
                int bi = id / Snake.MAX_SEG, j = id % Snake.MAX_SEG;
                if (bi == ai) continue;
                Snake b = snakes[bi];
                if (!b.alive || j >= b.segs) continue;
                float rr = (a.radius + b.radius) * 0.78f;
                if (MathUtil.dist2(hx, hy, b.sx[j], b.sy[j]) >= rr * rr) continue;
                if (a.spawnShield > 0 || b.spawnShield > 0) continue;

                if (a.dashTime > 0) {
                    if (!a.dashHit[bi]) {
                        a.dashHit[bi] = true;
                        float kA = a.ang + (MathUtil.wrap(MathUtil.angleTo(hx, hy, b.sx[j], b.sy[j]) - a.ang) > 0 ? 1.4f : -1.4f);
                        hurt(b, 1000f * a.damageMult(), a, b.sx[j], b.sy[j], kA, 380, CAUSE_DASH);
                        fx.burst(b.sx[j], b.sy[j], 14, 0xffff8a3a, 420, 9, 0.5f);
                        shakeAt(b.sx[j], b.sy[j], 6);
                    }
                    continue;
                }
                if (a.isPlayer) {
                    // The player never dies instantly from a crash: it hurts and bounces off instead
                    if (a.bumpCooldown <= 0) {
                        float ang = MathUtil.angleTo(b.sx[j], b.sy[j], hx, hy);
                        a.bumpCooldown = 0.6f;
                        hurt(a, a.maxHp * 0.3f / PLAYER_DAMAGE_TAKEN, b, hx, hy, ang, 600, CAUSE_CRASH);
                        a.ang = a.targetAng = ang;
                        fx.burst(hx, hy, 12, 0xffffffff, 300, 7, 0.4f);
                    }
                    continue;
                }
                if (j == 0) {
                    if (b.dashTime > 0) continue;
                    if (a.mass < b.mass * 0.92f) {
                        kill(a, b, CAUSE_CRASH);
                    } else if (b.mass < a.mass * 0.92f) {
                        kill(b, a, CAUSE_CRASH);
                    } else if (ai < bi && a.bumpCooldown <= 0 && b.bumpCooldown <= 0) {
                        float ang = MathUtil.angleTo(b.hx(), b.hy(), hx, hy);
                        a.bumpCooldown = b.bumpCooldown = 0.5f;
                        hurt(a, 900, b, hx, hy, ang, 700, CAUSE_CRASH);
                        hurt(b, 900, a, b.hx(), b.hy(), ang + MathUtil.PI, 700, CAUSE_CRASH);
                        a.ang = a.targetAng = ang;
                        b.ang = b.targetAng = ang + MathUtil.PI;
                    }
                } else {
                    kill(a, b, CAUSE_CRASH);
                }
            }
        }
    }

    private void eatAndPickup(float dt) {
        for (int si = 0; si < snakeCount; si++) {
            Snake s = snakes[si];
            if (!s.alive) continue;
            float hx = s.hx(), hy = s.hy();
            float mag = s.radius * 2.5f + 34f;
            float mag2 = mag * mag;
            float eat = s.radius + 6f;
            float pull = (380f + s.speed()) * dt;
            boolean ate = false;
            for (int i = 0; i < orbCount; i++) {
                float dx = hx - ox[i];
                if (dx > mag || dx < -mag) continue;
                float dy = hy - oy[i];
                if (dy > mag || dy < -mag) continue;
                float d2 = dx * dx + dy * dy;
                if (d2 > mag2) continue;
                if (oowner[i] == si && oage[i] < 1f) continue;
                float d = (float) Math.sqrt(d2);
                if (d < eat + orad[i] * 0.5f) {
                    s.mass += ov[i];
                    if (s.isPlayer) ate = true;
                    removeOrb(i);
                    i--;
                    continue;
                }
                float mv = Math.min(pull, d);
                ox[i] += dx / d * mv;
                oy[i] += dy / d * mv;
            }
            if (ate) {
                s.eatSoundTimer -= dt;
                if (s.eatSoundTimer <= 0) {
                    s.eatSoundTimer = 0.09f;
                    sound(Platform.SND_EAT, hx, hy, 0.35f);
                }
            }
            for (int c = 0; c < cubeCount; c++) {
                if (MathUtil.dist2(hx, hy, cubeX[c], cubeY[c]) < (s.radius + 30) * (s.radius + 30)) {
                    s.cubes++;
                    s.hp = Math.min(s.computeMaxHp(), s.hp + s.type.hp * 0.15f);
                    fx.text(hx, hy - s.radius - 60, "+POWER", 0xff6dff6d, 34);
                    fx.ring(cubeX[c], cubeY[c], 70, 0xff6dff6d, 0.4f);
                    if (s.isPlayer) sound(Platform.SND_POWER, hx, hy, 1f);
                    cubeCount--;
                    cubeX[c] = cubeX[cubeCount];
                    cubeY[c] = cubeY[cubeCount];
                    cubeAge[c] = cubeAge[cubeCount];
                    c--;
                }
            }
        }
    }

    // ------------------------------------------------------------------ combat

    private static int muzzleColor(int brawler) {
        switch (brawler) {
            case Brawler.VOLT:
                return 0xff7ad8ff;
            case Brawler.FROST:
                return 0xffd0f6ff;
            case Brawler.ZIGGY:
                return 0xffff8ad0;
            case Brawler.TOXIN:
                return 0xffc0ff6a;
            case Brawler.SHADE:
                return 0xffff5a8a;
            case Brawler.BLAZE:
                return 0xffff7a2a;
            default:
                return 0xffffd060;
        }
    }

    void fire(Snake s, float ang, float dist, boolean sup) {
        float mult = s.damageMult();
        float hr = s.radius * 1.2f;
        float mx = s.hx() + MathUtil.cos(ang) * hr, my = s.hy() + MathUtil.sin(ang) * hr;
        // Muzzle flash
        if (isNearCamera(mx, my)) fx.flash(mx, my, sup ? 95 : 60, muzzleColor(s.type.id), sup ? 0.2f : 0.13f);
        if (s.isPlayer) shake = Math.max(shake, sup ? 4f : 1.5f);
        switch (s.type.id) {
            case Brawler.VIPER: {
                int count = sup ? 11 : 5;
                float spread = sup ? 0.5f : 0.34f;
                float range = sup ? s.type.superRange : s.type.range;
                for (int i = 0; i < count; i++) {
                    float a = ang - spread + 2 * spread * i / (count - 1) + MathUtil.rand(-0.03f, 0.03f);
                    spawnProj(Projectile.PELLET, s, mx, my, a, MathUtil.rand(1250, 1400), range,
                            s.type.damage * (sup ? 1.1f : 1f) * mult, sup ? 9 : 7, sup ? 520 : 110, false);
                }
                fx.burst(mx, my, 6, 0xffffe066, 260, 6, 0.2f);
                sound(sup ? Platform.SND_SUPER : Platform.SND_SHOTGUN, mx, my, 0.8f);
                break;
            }
            case Brawler.VOLT:
                if (sup) {
                    s.burstLeft = 8;
                    s.burstInterval = 0.075f;
                    s.burstTimer = 0;
                    s.burstAng = ang;
                    s.burstSuper = true;
                    sound(Platform.SND_SUPER, mx, my, 0.8f);
                } else {
                    spawnProj(Projectile.BOLT, s, mx, my, ang, 2000, s.type.range, s.type.damage * mult, 9, 160, false);
                    sound(Platform.SND_BOLT, mx, my, 0.7f);
                }
                break;
            case Brawler.BOOMER: {
                float range = sup ? s.type.superRange : s.type.range;
                float d = MathUtil.clamp(dist, 110, range);
                Projectile p = spawnProj(sup ? Projectile.MEGABOMB : Projectile.BOMB, s, mx, my, ang, 0, range,
                        (sup ? 2300 : s.type.damage) * mult, sup ? 20 : 13, sup ? 750 : 300, true);
                if (p != null) {
                    p.startX = mx;
                    p.startY = my;
                    p.targetX = MathUtil.clamp(s.hx() + MathUtil.cos(ang) * d, 0, size);
                    p.targetY = MathUtil.clamp(s.hy() + MathUtil.sin(ang) * d, 0, size);
                    p.flight = sup ? 0.85f : 0.55f;
                    p.t = 0;
                    p.aoe = sup ? 230 : 105;
                }
                sound(sup ? Platform.SND_SUPER : Platform.SND_THROW, mx, my, 0.8f);
                break;
            }
            case Brawler.FROST:
                if (sup) {
                    nova(s, s.type.superRange, 1200 * mult);
                    sound(Platform.SND_SUPER, mx, my, 0.9f);
                } else {
                    for (int i = -1; i <= 1; i++) {
                        Projectile p = spawnProj(Projectile.SHARD, s, mx, my, ang + i * 0.12f, 1300, s.type.range,
                                s.type.damage * mult, 9, 80, false);
                        if (p != null) {
                            p.slowFactor = 0.6f;
                            p.slowDur = 1.3f;
                        }
                    }
                    sound(Platform.SND_SHOOT, mx, my, 0.7f);
                }
                break;
            case Brawler.ZIGGY:
                if (sup) {
                    for (int i = 0; i < 12; i++) {
                        float a = ang + MathUtil.TAU * i / 12f;
                        Projectile p = spawnProj(Projectile.BALL, s, s.hx() + MathUtil.cos(a) * hr, s.hy() + MathUtil.sin(a) * hr,
                                a, 1350, s.type.superRange, 420 * mult, 12, 150, false);
                        if (p != null) p.bounces = 3;
                    }
                    sound(Platform.SND_SUPER, mx, my, 0.9f);
                } else {
                    s.burstLeft = 3;
                    s.burstInterval = 0.1f;
                    s.burstTimer = 0;
                    s.burstAng = ang;
                    s.burstSuper = false;
                }
                break;
            case Brawler.TOXIN: {
                float range = sup ? s.type.superRange : s.type.range;
                float d = MathUtil.clamp(dist, 110, range);
                Projectile p = spawnProj(sup ? Projectile.MEGAGLOB : Projectile.GLOB, s, mx, my, ang, 0, range,
                        (sup ? 600 : s.type.damage) * mult, sup ? 18 : 12, 120, true);
                if (p != null) {
                    p.startX = mx;
                    p.startY = my;
                    p.targetX = MathUtil.clamp(s.hx() + MathUtil.cos(ang) * d, 0, size);
                    p.targetY = MathUtil.clamp(s.hy() + MathUtil.sin(ang) * d, 0, size);
                    p.flight = sup ? 0.8f : 0.6f;
                    p.t = 0;
                    p.aoe = sup ? 210 : 95;
                }
                sound(sup ? Platform.SND_SUPER : Platform.SND_THROW, mx, my, 0.8f);
                break;
            }
            case Brawler.SHADE:
                if (sup) {
                    shadowStep(s, ang, dist);
                    sound(Platform.SND_SUPER, mx, my, 0.9f);
                } else {
                    for (int i = -1; i <= 1; i++)
                        spawnProj(Projectile.SHURIKEN, s, mx, my, ang + i * 0.1f, 1600, s.type.range, s.type.damage * mult, 9, 90, false);
                    sound(Platform.SND_SHOOT, mx, my, 0.7f);
                }
                break;
            case Brawler.BLAZE:
            default:
                if (sup) {
                    s.dashTime = 1.05f;
                    s.ang = s.targetAng = ang;
                    java.util.Arrays.fill(s.dashHit, false);
                    fx.ring(s.hx(), s.hy(), 80, 0xffff8a3a, 0.35f);
                    sound(Platform.SND_SUPER, mx, my, 0.9f);
                } else {
                    s.burstLeft = 7;
                    s.burstInterval = 0.032f;
                    s.burstTimer = 0;
                    s.burstAng = ang;
                    s.burstSuper = false;
                    sound(Platform.SND_FLAME, mx, my, 0.8f);
                }
                break;
        }
    }

    private void fireBurstShot(Snake s) {
        float mult = s.damageMult();
        float hr = s.radius * 1.2f;
        if (s.type.id == Brawler.VOLT) {
            float a = s.burstAng + MathUtil.rand(-0.04f, 0.04f);
            float mx = s.hx() + MathUtil.cos(a) * hr, my = s.hy() + MathUtil.sin(a) * hr;
            spawnProj(Projectile.SUPERBOLT, s, mx, my, a, 2100, s.type.superRange, 520 * mult, 11, 200, true);
            sound(Platform.SND_BOLT, mx, my, 0.5f);
        } else if (s.type.id == Brawler.ZIGGY) {
            float a = s.burstAng + MathUtil.rand(-0.05f, 0.05f);
            float mx = s.hx() + MathUtil.cos(a) * hr, my = s.hy() + MathUtil.sin(a) * hr;
            Projectile p = spawnProj(Projectile.BALL, s, mx, my, a, 1400, s.type.range, s.type.damage * mult, 11, 120, false);
            if (p != null) p.bounces = 2;
            sound(Platform.SND_SHOOT, mx, my, 0.6f);
        } else {
            // Flames follow the head while it moves
            float a = s.burstAng + MathUtil.rand(-0.2f, 0.2f);
            float mx = s.hx() + MathUtil.cos(a) * hr, my = s.hy() + MathUtil.sin(a) * hr;
            spawnProj(Projectile.FLAME, s, mx, my, a, MathUtil.rand(880, 1000), s.type.range, s.type.damage * mult, 12, 60, false);
        }
    }

    private Projectile spawnProj(int kind, Snake owner, float x, float y, float ang, float speed, float range,
                                 float dmg, float radius, float knock, boolean thru) {
        for (Projectile p : proj) {
            if (p.active) continue;
            p.active = true;
            p.kind = kind;
            p.owner = owner;
            p.x = p.px = x;
            p.y = p.py = y;
            p.vx = MathUtil.cos(ang) * speed;
            p.vy = MathUtil.sin(ang) * speed;
            p.traveled = 0;
            p.range = range;
            p.damage = dmg;
            p.radius = radius;
            p.knock = knock;
            p.throughWalls = thru;
            p.bounces = 0;
            p.slowFactor = 1f;
            p.slowDur = 0;
            p.trailCount = 0;
            return p;
        }
        return null;
    }

    private void updateProjectiles(float dt) {
        for (Projectile p : proj) {
            if (!p.active) continue;
            if (p.isBomb()) {
                p.t += dt;
                float f = Math.min(1f, p.t / p.flight);
                p.x = MathUtil.lerp(p.startX, p.targetX, f);
                p.y = MathUtil.lerp(p.startY, p.targetY, f);
                if (f >= 1f) explode(p);
                continue;
            }
            p.pushTrail();
            p.px = p.x;
            p.py = p.y;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            float step = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy) * dt;
            p.traveled += step;
            if (p.kind == Projectile.FLAME) p.radius = 12f + 20f * (p.traveled / p.range);
            if (p.kind == Projectile.BALL && p.traveled < p.range && p.bounces > 0
                    && (p.x < 0 || p.y < 0 || p.x >= size || p.y >= size || tiles[(int) (p.y / T) * n + (int) (p.x / T)] == WALL)) {
                boolean flipX = solidAt(p.x, p.py), flipY = solidAt(p.px, p.y);
                if (!flipX && !flipY) flipX = flipY = true;
                if (flipX) p.vx = -p.vx;
                if (flipY) p.vy = -p.vy;
                p.x = p.px;
                p.y = p.py;
                p.bounces--;
                fx.sparks(p.x, p.y, 4, 0xffffe14a, 180, 0.2f);
                continue;
            }
            if (p.traveled >= p.range || p.x < 0 || p.y < 0 || p.x >= size || p.y >= size) {
                p.active = false;
                if (p.kind != Projectile.FLAME) fx.sparks(p.x, p.y, 3, 0xffffffff, 120, 0.2f);
                continue;
            }
            int tx = (int) (p.x / T), ty = (int) (p.y / T);
            byte tile = tiles[ty * n + tx];
            if (tile == BOX) {
                damageBox(ty * n + tx, p.damage, p.owner);
                p.active = false;
                fx.sparks(p.x, p.y, 5, 0xffffd27a, 200, 0.25f);
                continue;
            }
            if (tile == WALL && !p.throughWalls) {
                p.active = false;
                fx.sparks(p.px, p.py, 5, 0xffdddddd, 200, 0.25f);
                continue;
            }
            hitSnakes(p, step);
        }
    }

    private void hitSnakes(Projectile p, float step) {
        float mx = (p.x + p.px) * 0.5f, my = (p.y + p.py) * 0.5f;
        int cnt = grid.query(mx, my, step * 0.5f + 40f + p.radius, q);
        float ax = p.px, ay = p.py, bx = p.x - p.px, by = p.y - p.py;
        float len2 = bx * bx + by * by;
        for (int k = 0; k < cnt; k++) {
            int id = q[k];
            Snake s = snakes[id / Snake.MAX_SEG];
            int j = id % Snake.MAX_SEG;
            if (s == p.owner || !s.alive || j >= s.segs) continue;
            float sxj = s.sx[j], syj = s.sy[j];
            float t = len2 > 0 ? MathUtil.clamp(((sxj - ax) * bx + (syj - ay) * by) / len2, 0, 1) : 0;
            float cx = ax + bx * t, cy = ay + by * t;
            float rr = s.radius + p.radius;
            if (MathUtil.dist2(cx, cy, sxj, syj) < rr * rr) {
                float ang = (float) Math.atan2(p.vy, p.vx);
                int color = p.kind == Projectile.FLAME ? 0xffff8a2a : (p.kind == Projectile.PELLET || p.kind == Projectile.BALL ? 0xffffe066 : 0xff9af0ff);
                fx.sparks(cx, cy, 7, color, 300, 0.25f);
                fx.flash(cx, cy, 48, projColor(p.kind), 0.14f);
                // Head shots crit
                boolean crit = j <= 1;
                float dmg = crit ? p.damage * 1.25f : p.damage;
                if (crit && p.owner != null && p.owner.isPlayer && showDamage) fx.text(cx, cy - 78, "CRIT!", 0xffffd23f, 30);
                hurt(s, dmg, p.owner, cx, cy, ang, p.knock, CAUSE_SHOT);
                if (p.slowDur > 0) slow(s, p.slowFactor, p.slowDur);
                p.active = false;
                return;
            }
        }
    }

    private void explode(Projectile p) {
        p.active = false;
        float x = p.x, y = p.y, r = p.aoe;
        if (p.kind == Projectile.GLOB || p.kind == Projectile.MEGAGLOB) {
            boolean big = p.kind == Projectile.MEGAGLOB;
            addArea(x, y, r, big ? 5f : 3f, (big ? 850 : 650) * (p.owner != null ? p.owner.damageMult() : 1f), p.owner);
            fx.burst(x, y, big ? 30 : 16, 0xffa6ff3a, r * 3f, 9, 0.5f);
            fx.ring(x, y, r, 0xffa6ff3a, 0.35f);
            fx.flash(x, y, r * 1.3f, 0xffa6ff3a, 0.2f);
            sound(Platform.SND_EXPLODE, x, y, 0.45f);
            splash(p, x, y, r * 0.8f);
            return;
        }
        boolean mega = p.kind == Projectile.MEGABOMB;
        fx.ring(x, y, r * 1.1f, 0xffffe28a, 0.45f);
        fx.fireball(x, y, r * 1.15f, mega ? 0.75f : 0.55f);
        fx.flash(x, y, r * 1.6f, 0xffffe08a, 0.2f);
        addDecal(x, y, r * 0.95f);
        fx.burst(x, y, mega ? 40 : 22, 0xffff8a2a, r * 3.5f, mega ? 14 : 10, 0.6f);
        fx.burst(x, y, mega ? 20 : 10, 0xffffe066, r * 2.5f, 8, 0.4f);
        fx.smoke(x, y, mega ? 14 : 7, 0xff5a4a40, r * 0.5f, 0.9f);
        sound(Platform.SND_EXPLODE, x, y, mega ? 1f : 0.7f);
        shakeAt(x, y, mega ? 16 : 8);
        splash(p, x, y, r);
    }

    /** Damages every snake (once) and box within r of (x, y). */
    private void splash(Projectile p, float x, float y, float r) {
        for (int i = 0; i < snakeCount; i++) {
            Snake s = snakes[i];
            if (!s.alive || s == p.owner) continue;
            if (x < s.minX - r || x > s.maxX + r || y < s.minY - r || y > s.maxY + r) continue;
            float best = Float.MAX_VALUE;
            int bj = 0;
            for (int j = 0; j < s.segs; j++) {
                float d2 = MathUtil.dist2(x, y, s.sx[j], s.sy[j]);
                if (d2 < best) {
                    best = d2;
                    bj = j;
                }
            }
            float reach = r + s.radius;
            if (best < reach * reach) {
                float ang = MathUtil.angleTo(x, y, s.sx[bj], s.sy[bj]);
                hurt(s, p.damage, p.owner, s.sx[bj], s.sy[bj], ang, p.knock, CAUSE_SHOT);
            }
        }
        int tx0 = (int) ((x - r) / T), tx1 = (int) ((x + r) / T), ty0 = (int) ((y - r) / T), ty1 = (int) ((y + r) / T);
        for (int ty = Math.max(0, ty0); ty <= Math.min(n - 1, ty1); ty++)
            for (int tx = Math.max(0, tx0); tx <= Math.min(n - 1, tx1); tx++)
                if (tiles[ty * n + tx] == BOX && MathUtil.dist2(x, y, (tx + 0.5f) * T, (ty + 0.5f) * T) < (r + T * 0.5f) * (r + T * 0.5f))
                    damageBox(ty * n + tx, p.damage, p.owner);
    }

    void slow(Snake v, float factor, float dur) {
        if (!v.alive || v.spawnShield > 0) return;
        if (v.slowTime <= 0 || factor <= v.slowFactor) v.slowFactor = factor;
        v.slowTime = Math.max(v.slowTime, dur);
    }

    /** Frost's Blizzard: damages and nearly freezes everyone close by. */
    private void nova(Snake s, float radius, float dmg) {
        float x = s.hx(), y = s.hy();
        fx.ring(x, y, radius, 0xffbff0ff, 0.5f);
        fx.ring(x, y, radius * 0.6f, 0xffffffff, 0.35f);
        fx.burst(x, y, 40, 0xffe8ffff, radius * 3f, 9, 0.6f);
        shakeAt(x, y, 8);
        for (int i = 0; i < snakeCount; i++) {
            Snake o = snakes[i];
            if (o == s || !o.alive) continue;
            if (x < o.minX - radius || x > o.maxX + radius || y < o.minY - radius || y > o.maxY + radius) continue;
            for (int j = 0; j < o.segs; j += 2) {
                float rr = radius + o.radius;
                if (MathUtil.dist2(x, y, o.sx[j], o.sy[j]) < rr * rr) {
                    hurt(o, dmg, s, o.sx[j], o.sy[j], MathUtil.angleTo(x, y, o.sx[j], o.sy[j]), 200, CAUSE_SHOT);
                    slow(o, 0.3f, 2.5f);
                    break;
                }
            }
        }
    }

    /** Shade's Shadow Step: blink forward and turn invisible. */
    private void shadowStep(Snake s, float ang, float dist) {
        float d = MathUtil.clamp(dist, 180, s.type.superRange);
        float ox = s.hx(), oy = s.hy();
        float tx = ox, ty = oy;
        for (float k = d; k > 0; k -= 20) {
            float x = ox + MathUtil.cos(ang) * k, y = oy + MathUtil.sin(ang) * k;
            if (!solidCircle(x, y, s.radius)) {
                tx = x;
                ty = y;
                break;
            }
        }
        fx.smoke(ox, oy, 10, 0xff2a2a40, 40, 0.8f);
        s.sx[0] = tx;
        s.sy[0] = ty;
        s.ang = s.targetAng = ang;
        s.invisTime = 3.5f;
        s.revealTime = 0;
        fx.smoke(tx, ty, 8, 0xff4a4a66, 34, 0.7f);
    }

    // Lingering poison puddles (Toxin)
    private static final int MAX_AREAS = 24;
    private final float[] areaX = new float[MAX_AREAS], areaY = new float[MAX_AREAS], areaR = new float[MAX_AREAS];
    private final float[] areaLife = new float[MAX_AREAS], areaMax = new float[MAX_AREAS], areaDps = new float[MAX_AREAS];
    private final float[] areaTick = new float[MAX_AREAS];
    private final Snake[] areaOwner = new Snake[MAX_AREAS];
    private int areaCount;

    private void addArea(float x, float y, float r, float life, float dps, Snake owner) {
        if (areaCount >= MAX_AREAS) return;
        int i = areaCount++;
        areaX[i] = x;
        areaY[i] = y;
        areaR[i] = r;
        areaLife[i] = areaMax[i] = life;
        areaDps[i] = dps;
        areaTick[i] = 0.25f;
        areaOwner[i] = owner;
    }

    private void updateAreas(float dt) {
        for (int i = 0; i < areaCount; i++) {
            areaLife[i] -= dt;
            if (areaLife[i] <= 0) {
                int last = --areaCount;
                areaX[i] = areaX[last];
                areaY[i] = areaY[last];
                areaR[i] = areaR[last];
                areaLife[i] = areaLife[last];
                areaMax[i] = areaMax[last];
                areaDps[i] = areaDps[last];
                areaTick[i] = areaTick[last];
                areaOwner[i] = areaOwner[last];
                areaOwner[last] = null;
                i--;
                continue;
            }
            areaTick[i] -= dt;
            if (areaTick[i] > 0) continue;
            areaTick[i] += 0.5f;
            float x = areaX[i], y = areaY[i], r = areaR[i];
            for (int k = 0; k < snakeCount; k++) {
                Snake o = snakes[k];
                if (o == areaOwner[i] || !o.alive) continue;
                if (x < o.minX - r || x > o.maxX + r || y < o.minY - r || y > o.maxY + r) continue;
                for (int j = 0; j < o.segs; j += 2) {
                    float rr = r + o.radius * 0.5f;
                    if (MathUtil.dist2(x, y, o.sx[j], o.sy[j]) < rr * rr) {
                        hurt(o, areaDps[i] * 0.5f, areaOwner[i], o.sx[j], o.sy[j], 0, 0, CAUSE_SHOT);
                        break;
                    }
                }
            }
        }
    }

    private void drawAreas(Gfx g, float l, float t, float r, float b, float tt) {
        for (int i = 0; i < areaCount; i++) {
            float x = areaX[i], y = areaY[i], rad = areaR[i];
            if (x < l - rad || x > r + rad || y < t - rad || y > b + rad) continue;
            float f = Math.min(1f, areaLife[i] / 0.4f) * Math.min(1f, (areaMax[i] - areaLife[i]) / 0.2f + 0.3f);
            g.color(MathUtil.withAlpha(0xff6a2bd1, 0.35f * f));
            g.fillCircle(x, y, rad);
            g.color(MathUtil.withAlpha(0xffa6ff3a, 0.45f * f));
            g.fillCircle(x, y, rad * 0.82f);
            g.color(MathUtil.withAlpha(0xffd8ff8a, 0.7f * f));
            for (int k = 0; k < 5; k++) {
                float a = k * 1.3f + i;
                float ph = (tt * 0.8f + k * 0.21f) % 1f;
                g.fillCircle(x + MathUtil.cos(a) * rad * 0.55f, y + MathUtil.sin(a) * rad * 0.55f, rad * 0.12f * (1f - ph));
            }
        }
    }

    void hurt(Snake v, float dmg, Snake by, float x, float y, float kAng, float knock, int cause) {
        if (!v.alive) return;
        if (v.spawnShield > 0 && cause != CAUSE_POISON) {
            fx.ring(v.hx(), v.hy(), v.radius * 2.2f, 0xaaffffff, 0.25f);
            return;
        }
        // Easier fights: the player shrugs off a good part of every hit
        if (v.isPlayer) dmg *= PLAYER_DAMAGE_TAKEN;
        v.hp -= dmg;
        v.hitFlash = 0.12f;
        v.sinceDamaged = 0;
        if (by != null && by != v) {
            v.lastAttacker = by;
            v.lastAttackerTime = time;
            if (by.alive) by.superCharge = Math.min(1f, by.superCharge + dmg / by.type.superCost * (by.isPlayer ? 1.6f : 1f));
        }
        if (knock > 0) {
            v.kbx += MathUtil.cos(kAng) * knock;
            v.kby += MathUtil.sin(kAng) * knock;
        }
        if (cause != CAUSE_POISON) {
            boolean mine = by != null && by.isPlayer, me = v.isPlayer;
            if (showDamage && (mine || me || isNearCamera(x, y))) {
                int col = me ? 0xffff5a5a : (mine ? 0xffffffff : 0xffd8d8d8);
                // Pellets and flames land in clusters: sum them into one number
                if (v.dmgTimer > 0 && v.dmgColor == col) {
                    v.dmgPending += dmg;
                } else {
                    flushDamageText(v);
                    v.dmgPending = dmg;
                    v.dmgTimer = 0.12f;
                    v.dmgColor = col;
                    v.dmgSize = mine || me ? 36 : 26;
                    v.dmgX = x;
                    v.dmgY = y - 34;
                }
            }
            if (mine || me) sound(Platform.SND_HIT, x, y, 0.6f);
            if (me) shake = Math.max(shake, 5);
        }
        if (v.hp <= 0) {
            Snake killer = by;
            if (killer == null && v.lastAttacker != null && time - v.lastAttackerTime < 4f) killer = v.lastAttacker;
            kill(v, killer, cause);
        }
    }

    private void flushDamageText(Snake v) {
        if (v.dmgPending > 0) fx.text(v.dmgX, v.dmgY, Integer.toString((int) v.dmgPending), v.dmgColor, v.dmgSize);
        v.dmgPending = 0;
        v.dmgTimer = 0;
    }

    void kill(Snake v, Snake by, int cause) {
        if (!v.alive) return;
        flushDamageText(v);
        int aliveBefore = 0;
        for (int i = 0; i < snakeCount; i++) if (snakes[i].alive) aliveBefore++;
        v.alive = false;
        v.deadTime = 0;
        v.hp = 0;
        v.rank = aliveBefore;
        v.burstLeft = 0;
        v.dashTime = 0;

        // Explode into food
        int count = Math.max(6, Math.min(90, v.segs / 2));
        float total = Math.max(20f, v.mass * 0.6f);
        float value = total / count;
        for (int k = 0; k < count; k++) {
            int j = (int) ((k + 0.5f) * v.segs / count);
            if (j >= v.segs) j = v.segs - 1;
            float x = v.sx[j] + MathUtil.rand(-v.radius, v.radius);
            float y = v.sy[j] + MathUtil.rand(-v.radius, v.radius);
            if (solidAt(x, y)) {
                x = v.sx[j];
                y = v.sy[j];
            }
            addOrb(x, y, value, v.palette[k % v.palette.length], -1);
        }
        for (int c = 0; c < v.cubes; c++) {
            int j = MathUtil.randInt(Math.max(1, Math.min(v.segs, 12)));
            dropCube(v.sx[j] + MathUtil.rand(-40, 40), v.sy[j] + MathUtil.rand(-40, 40));
        }
        for (int j = 0; j < v.segs; j += 4) fx.burst(v.sx[j], v.sy[j], 2, v.color1, 220, 10, 0.6f);
        fx.burst(v.hx(), v.hy(), 26, 0xffffffff, 420, 8, 0.6f);
        fx.ring(v.hx(), v.hy(), 140, v.color1, 0.5f);
        fx.smoke(v.hx(), v.hy(), 8, 0xff404040, 40, 1f);
        shakeAt(v.hx(), v.hy(), 10);

        if (by != null && by != v) {
            by.kills++;
            if (by.alive) by.hp = Math.min(by.maxHp, by.hp + by.maxHp * 0.25f);
        }
        addFeed(by, v, cause);

        if (v.isPlayer) {
            playerDied = true;
            sound(Platform.SND_DEATH, v.hx(), v.hy(), 1f);
            platform.vibrate(180);
        } else if (by != null && by.isPlayer) {
            sound(Platform.SND_KILL, v.hx(), v.hy(), 1f);
            platform.vibrate(40);
            fx.text(by.hx(), by.hy() - by.radius - 70, "KNOCKOUT!", 0xffffd23f, 44);
            hitStop = 0.07f;
            shake = Math.max(shake, 9);
            streak = streakTimer > 0 ? streak + 1 : 1;
            streakTimer = 4.5f;
            if (streak == 2) banner("DOUBLE KNOCKOUT!");
            else if (streak == 3) banner("TRIPLE KNOCKOUT!");
            else if (streak >= 4) banner("UNSTOPPABLE!");
        } else {
            sound(Platform.SND_EXPLODE, v.hx(), v.hy(), 0.6f);
        }
        countAlive();
    }

    private void addFeed(Snake by, Snake v, int cause) {
        if (feedCount == FEED) shiftFeed();
        int i = feedCount++;
        feedA[i] = by != null && by != v ? by.name : null;
        feedColA[i] = by != null ? by.color1 : 0xffffffff;
        feedB[i] = v.name;
        feedColB[i] = v.color1;
        feedCause[i] = cause;
        feedTime[i] = 0;
    }

    private void shiftFeed() {
        for (int i = 0; i < feedCount - 1; i++) {
            feedA[i] = feedA[i + 1];
            feedB[i] = feedB[i + 1];
            feedColA[i] = feedColA[i + 1];
            feedColB[i] = feedColB[i + 1];
            feedCause[i] = feedCause[i + 1];
            feedTime[i] = feedTime[i + 1];
        }
        feedCount--;
    }

    // ------------------------------------------------------------------ queries for AI / aim

    boolean visibleTo(Snake target, Snake viewer) {
        if (target.invisTime > 0 && target.revealTime <= 0) {
            return viewer != null && viewer.alive && MathUtil.dist2(target.hx(), target.hy(), viewer.hx(), viewer.hy()) < 150 * 150;
        }
        if (target.revealTime > 0 || !target.headInBush) return true;
        return viewer != null && viewer.alive
                && MathUtil.dist2(target.hx(), target.hy(), viewer.hx(), viewer.hy()) < REVEAL_DIST * REVEAL_DIST;
    }

    /** Result of {@link #findAim}. */
    float aimOutAng, aimOutDist;

    /**
     * Picks the closest visible enemy body part (or a box) within range and leads moving heads.
     * Returns false when nothing is in range.
     */
    boolean findAim(Snake s, float range, float projSpeed) {
        float hx = s.hx(), hy = s.hy();
        float best = range * range;
        float tx = 0, ty = 0;
        boolean found = false;
        for (int i = 0; i < snakeCount; i++) {
            Snake o = snakes[i];
            if (o == s || !o.alive || !visibleTo(o, s)) continue;
            if (hx < o.minX - range || hx > o.maxX + range || hy < o.minY - range || hy > o.maxY + range) continue;
            for (int j = 0; j < o.segs; j += 2) {
                float d2 = MathUtil.dist2(hx, hy, o.sx[j], o.sy[j]);
                if (j > 0 && bushAt(o.sx[j], o.sy[j]) && d2 > REVEAL_DIST * REVEAL_DIST && o.revealTime <= 0) continue;
                // Prefer heads a little: they move, but kills come faster
                if (j == 0) d2 *= 0.8f;
                if (d2 < best) {
                    best = d2;
                    found = true;
                    if (j == 0 && projSpeed > 0) {
                        float tt = MathUtil.sqrt(d2) / projSpeed;
                        tx = o.hx() + o.vx * tt;
                        ty = o.hy() + o.vy * tt;
                    } else {
                        tx = o.sx[j];
                        ty = o.sy[j];
                    }
                }
            }
        }
        if (!found) {
            for (int b = 0; b < boxCount; b++) {
                float bx = (boxTile[b] % n + 0.5f) * T, by = (boxTile[b] / n + 0.5f) * T;
                float d2 = MathUtil.dist2(hx, hy, bx, by);
                if (d2 < best) {
                    best = d2;
                    found = true;
                    tx = bx;
                    ty = by;
                }
            }
        }
        if (!found) return false;
        aimOutAng = MathUtil.angleTo(hx, hy, tx, ty);
        aimOutDist = MathUtil.dist(hx, hy, tx, ty);
        return true;
    }

    /** Free distance along a ray before hitting walls, bodies, the arena edge or poison. */
    float rayClear(Snake s, float ang, float maxDist) {
        float ca = MathUtil.cos(ang), sa = MathUtil.sin(ang);
        float step = Math.max(18f, s.radius);
        float hx = s.hx(), hy = s.hy();
        boolean checkZone = zoneActive() && inZone(hx, hy);
        float zoneMargin = 60f;
        for (float d = step; d <= maxDist; d += step) {
            float x = hx + ca * d, y = hy + sa * d;
            if (solidCircle(x, y, s.radius * 0.85f)) return d - step;
            if (checkZone && (x < zoneL + zoneMargin || x > zoneR - zoneMargin || y < zoneT + zoneMargin || y > zoneB - zoneMargin))
                return d - step;
            int cnt = grid.query(x, y, s.radius + 40f, q);
            for (int k = 0; k < cnt; k++) {
                int id = q[k];
                int bi = id / Snake.MAX_SEG;
                if (bi == s.index) continue;
                Snake o = snakes[bi];
                int j = id % Snake.MAX_SEG;
                if (!o.alive || j >= o.segs) continue;
                float rr = s.radius + o.radius + 8f;
                if (MathUtil.dist2(x, y, o.sx[j], o.sy[j]) < rr * rr) return d - step;
            }
        }
        return maxDist;
    }

    // ------------------------------------------------------------------ sound / camera helpers

    boolean isNearCamera(float x, float y) {
        float hw = viewW / zoom * 0.6f, hh = viewH / zoom * 0.6f;
        return x > camX - hw && x < camX + hw && y > camY - hh && y < camY + hh;
    }

    void sound(int id, float x, float y, float vol) {
        if (mode == MODE_DEMO) return;
        float d = MathUtil.dist(x, y, camX, camY);
        float f = 1f - d / 1500f;
        if (f <= 0) return;
        platform.playSound(id, vol * Math.min(1f, f * 1.4f));
    }

    void shakeAt(float x, float y, float amount) {
        if (mode == MODE_DEMO) return;
        float d = MathUtil.dist(x, y, camX, camY);
        float f = 1f - d / 1200f;
        if (f > 0) shake = Math.max(shake, amount * f);
    }

    void updateCamera(float dt, float screenW, float screenH) {
        viewW = screenW;
        viewH = screenH;
        Snake f = focus;
        if (f != null) {
            float k = Math.min(1f, dt * 7f);
            camX += (f.hx() - camX) * k;
            camY += (f.hy() - camY) * k;
            float base = screenH / 900f * (mode == MODE_DEMO ? 0.85f : 1f);
            float target = base * zoomMult * (float) Math.pow(19f / f.radius, 0.5f);
            if (f.boosting) target *= 0.96f;
            zoom += (target - zoom) * Math.min(1f, dt * 2f);
        }
    }

    // ------------------------------------------------------------------ rendering

    void render(Gfx g) {
        float w = g.width(), h = g.height();
        float sx = shake > 0 ? MathUtil.rand(-shake, shake) : 0, sy = shake > 0 ? MathUtil.rand(-shake, shake) : 0;
        float halfW = w / 2 / zoom, halfH = h / 2 / zoom;
        float l = camX - halfW, t = camY - halfH, r = camX + halfW, b = camY + halfH;
        float tt = time + time0;
        boolean fancy = !lowGraphics;
        Snake.fancy = fancy;
        Snake.shadows = true;

        // Deep sea around the island
        if (fancy) g.vertical(0, 0, w, h, 0xff2f8fd0, 0xff174a85);
        else {
            g.color(0xff1d5e8c);
            g.fillRect(0, 0, w, h);
        }
        g.save();
        g.translate(w / 2 + sx, h / 2 + sy);
        g.scale(zoom);
        g.translate(-camX, -camY);

        drawGround(g, l, t, r, b, tt);
        drawDecals(g, l, t, r, b);
        drawAreas(g, l, t, r, b, tt);
        drawOrbs(g, l, t, r, b, tt);
        drawCubes(g, l, t, r, b, tt);
        drawWallsAndBoxes(g, l, t, r, b, tt);
        boolean hidePlayer = playerInBush();
        drawSnakes(g, l, t, r, b, tt, hidePlayer);
        drawProjectiles(g, l, t, r, b, tt);
        drawBushes(g, l, t, r, b, tt, hidePlayer);
        // Like Brawl Stars, you can always see yourself inside a bush (faded)
        if (hidePlayer) drawSnake(g, player, 0.6f, l, t, r, b, tt);
        fx.draw(g, l, t, r, b);
        drawLockOn(g, tt);
        drawLabels(g, l, t, r, b);
        drawAim(g);
        drawPoison(g, l, t, r, b, tt);
        fx.drawText(g);
        g.restore();
        Snake.shadows = false;

        if (fancy) {
            // Soft vignette pulls the eye to the centre
            float rad = (float) Math.sqrt(w * w + h * h) * 0.62f;
            g.radial(w / 2, h / 2, rad, 0x00000000, 0x70000018);
        }
    }

    private static int hash(int x, int y) {
        int h = x * 73856093 ^ y * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return h ^ (h >>> 15);
    }

    private void drawGround(Gfx g, float l, float t, float r, float b, float tt) {
        boolean fancy = !lowGraphics;
        // Animated waves around the island
        float wl = Math.max(l, -2000), wr = Math.min(r, size + 2000);
        for (float y = (float) Math.floor(t / 90) * 90; y < b; y += 90) {
            float off = MathUtil.sin(tt * 1.5f + y * 0.02f) * 20;
            g.color(0x2affffff);
            g.line(wl, y + off, wr, y + off + 6, 5);
        }
        if (fancy) {
            // Foam and wet sand along the shore
            float foam = 26f + 6f * MathUtil.sin(tt * 2f);
            g.color(0x55ffffff);
            g.fillRoundRect(-foam - 18, -foam - 18, size + foam + 18, size + foam + 18, 60);
            g.color(0xffb08c58);
            g.fillRoundRect(-30, -30, size + 30, size + 30, 40);
        }
        g.color(0xffc9a46a);
        g.fillRect(-18, -18, size + 18, size + 18);
        g.color(0xffeed49e);
        g.fillRect(0, 0, size, size);
        int tx0 = Math.max(0, (int) (l / T)), tx1 = Math.min(n - 1, (int) (r / T));
        int ty0 = Math.max(0, (int) (t / T)), ty1 = Math.min(n - 1, (int) (b / T));
        // Checker tiles with a little colour variation so the ground feels natural
        for (int ty = ty0; ty <= ty1; ty++) {
            for (int tx = tx0; tx <= tx1; tx++) {
                int hsh = hash(tx, ty);
                boolean dark = ((tx + ty) & 1) == 0;
                if (!dark && (!fancy || (hsh & 7) != 0)) continue;
                int c = dark ? ((hsh & 3) == 0 ? 0xffe0c085 : 0xffe5c78d) : 0xfff2dba8;
                g.color(c);
                g.fillRect(tx * T, ty * T, tx * T + T, ty * T + T);
            }
        }
        if (fancy) {
            // Decorations: grass tufts, pebbles and little flowers
            for (int ty = ty0; ty <= ty1; ty++) {
                for (int tx = tx0; tx <= tx1; tx++) {
                    int idx = ty * n + tx;
                    if (tiles[idx] != EMPTY || bush[idx]) continue;
                    int hsh = hash(tx * 7 + 3, ty * 11 + 5);
                    int kind = (hsh >>> 4) & 15;
                    if (kind > 5) continue;
                    float x = tx * T + 12 + ((hsh >>> 8) & 31) * 1.3f, y = ty * T + 12 + ((hsh >>> 13) & 31) * 1.3f;
                    if (kind <= 2) {
                        float sway = MathUtil.sin(tt * 2f + x * 0.05f) * 2f;
                        g.color(0xff8fb04a);
                        g.line(x, y, x - 6 + sway, y - 12, 3);
                        g.line(x, y, x + sway, y - 15, 3);
                        g.line(x, y, x + 6 + sway, y - 11, 3);
                    } else if (kind <= 4) {
                        g.color(0x33000000);
                        g.fillCircle(x + 2, y + 3, 6);
                        g.color(0xffb8a88c);
                        g.fillCircle(x, y, 6);
                        g.color(0xffd8ccb4);
                        g.fillCircle(x - 2, y - 2, 2.5f);
                    } else {
                        int fc = (hsh & 1) == 0 ? 0xffff7ab0 : 0xffffffff;
                        g.color(0xff8fb04a);
                        g.line(x, y + 4, x, y + 12, 2.5f);
                        g.color(fc);
                        for (int k = 0; k < 5; k++) {
                            float a = k * MathUtil.TAU / 5;
                            g.fillCircle(x + MathUtil.cos(a) * 4, y + MathUtil.sin(a) * 4, 3);
                        }
                        g.color(0xffffd23f);
                        g.fillCircle(x, y, 2.5f);
                    }
                }
            }
        }
        // Wooden edge fence
        g.color(0xff5a3a1a);
        g.strokeRoundRect(-9, -7, size + 9, size + 11, 12, 16);
        g.color(0xff8b5a2b);
        g.strokeRoundRect(-9, -9, size + 9, size + 9, 12, 12);
        if (fancy) {
            g.color(0xffb07a3c);
            g.strokeRoundRect(-9, -11, size + 9, size + 7, 12, 4);
        }
    }

    // Scorch marks left by explosions
    private static final int MAX_DECALS = 40;
    private final float[] decalX = new float[MAX_DECALS], decalY = new float[MAX_DECALS], decalR = new float[MAX_DECALS];
    private final float[] decalBorn = new float[MAX_DECALS];
    private int decalNext;

    void addDecal(float x, float y, float r) {
        int i = decalNext;
        decalNext = (decalNext + 1) % MAX_DECALS;
        decalX[i] = x;
        decalY[i] = y;
        decalR[i] = r;
        decalBorn[i] = time;
    }

    private void drawDecals(Gfx g, float l, float t, float r, float b) {
        for (int i = 0; i < MAX_DECALS; i++) {
            float rad = decalR[i];
            if (rad <= 0) continue;
            float age = time - decalBorn[i];
            if (age > 9f) continue;
            float x = decalX[i], y = decalY[i];
            if (x < l - rad || x > r + rad || y < t - rad || y > b + rad) continue;
            float a = Math.min(1f, (9f - age) / 3f);
            if (lowGraphics) {
                g.color(MathUtil.withAlpha(0x44302010, a));
                g.fillCircle(x, y, rad * 0.8f);
            } else {
                g.radial(x, y, rad, MathUtil.withAlpha(0x88281608, a), 0x00281608);
            }
        }
    }

    private void drawOrbs(Gfx g, float l, float t, float r, float b, float tt) {
        boolean fancy = !lowGraphics;
        for (int i = 0; i < orbCount; i++) {
            float x = ox[i], y = oy[i], rad = orad[i];
            if (x < l - 40 || x > r + 40 || y < t - 40 || y > b + 40) continue;
            float pulse = 1f + 0.15f * MathUtil.sin(tt * 4f + ophase[i]);
            int c = ocol[i];
            if (fancy) {
                g.radial(x, y, rad * 2.6f * pulse, MathUtil.withAlpha(c, 0.55f), c & 0x00ffffff);
            } else if (rad > 9) {
                g.color(MathUtil.withAlpha(c, 0.28f));
                g.fillCircle(x, y, rad * 1.9f * pulse);
            }
            g.color(MathUtil.darker(c, 0.25f));
            g.fillCircle(x, y, rad * pulse);
            g.color(c);
            g.fillCircle(x - rad * 0.1f, y - rad * 0.12f, rad * 0.82f * pulse);
            g.color(0xddffffff);
            g.fillCircle(x - rad * 0.3f, y - rad * 0.32f, rad * 0.3f);
            if (fancy && rad > 8 && ((int) (tt * 2 + ophase[i]) & 3) == 0) {
                // Occasional sparkle on big orbs
                float sp = (tt * 2 + ophase[i]) % 1f;
                float sr = rad * 0.9f * MathUtil.sin(sp * MathUtil.PI);
                g.color(0xccffffff);
                g.line(x + rad * 0.4f - sr, y - rad * 0.5f, x + rad * 0.4f + sr, y - rad * 0.5f, 2);
                g.line(x + rad * 0.4f, y - rad * 0.5f - sr, x + rad * 0.4f, y - rad * 0.5f + sr, 2);
            }
        }
    }

    private void drawCubes(Gfx g, float l, float t, float r, float b, float tt) {
        for (int i = 0; i < cubeCount; i++) {
            float x = cubeX[i], y = cubeY[i];
            if (x < l - 60 || x > r + 60 || y < t - 60 || y > b + 60) continue;
            float bob = MathUtil.sin(tt * 3f + i) * 5f;
            g.color(0x40000000);
            g.fillCircle(x, y + 18, 18);
            if (!lowGraphics) g.radial(x, y + bob, 52, 0x996dff6d, 0x006dff6d);
            else {
                g.color(0x5566ff66);
                g.fillCircle(x, y + bob, 32);
            }
            g.save();
            g.translate(x, y + bob);
            g.rotate(45 + MathUtil.sin(tt * 2f + i) * 12f);
            g.color(0xff14501c);
            g.fillRoundRect(-18, -18, 18, 18, 5);
            g.color(0xff2fbf45);
            g.fillRoundRect(-14, -14, 14, 14, 4);
            g.color(0xff7aff7a);
            g.fillRoundRect(-14, -14, 6, 6, 4);
            g.color(0xffe0ffe0);
            g.fillRoundRect(-10, -10, -2, -2, 2);
            g.restore();
        }
    }

    private void drawWallsAndBoxes(Gfx g, float l, float t, float r, float b, float tt) {
        boolean fancy = !lowGraphics;
        int tx0 = Math.max(0, (int) (l / T) - 1), tx1 = Math.min(n - 1, (int) (r / T) + 1);
        int ty0 = Math.max(0, (int) (t / T) - 1), ty1 = Math.min(n - 1, (int) (b / T) + 1);
        float lift = T * 0.24f;
        // Shadows first so blocks never shade each other
        g.color(0x38000000);
        for (int ty = ty0; ty <= ty1; ty++)
            for (int tx = tx0; tx <= tx1; tx++)
                if (tiles[ty * n + tx] != EMPTY) g.fillRect(tx * T + 8, ty * T + 10, tx * T + T + 10, ty * T + T + 12);
        for (int ty = ty0; ty <= ty1; ty++) {
            for (int tx = tx0; tx <= tx1; tx++) {
                byte tile = tiles[ty * n + tx];
                if (tile == EMPTY) continue;
                float x = tx * T, y = ty * T;
                if (tile == WALL) {
                    boolean below = ty + 1 < n && tiles[(ty + 1) * n + tx] == WALL;
                    // Front face
                    if (!below) {
                        if (fancy) g.vertical(x, y + T - lift, x + T, y + T, 0xff5a6688, 0xff3a4260);
                        else {
                            g.color(0xff4e5873);
                            g.fillRect(x, y + T - lift, x + T, y + T);
                        }
                        g.color(0x55262c40);
                        g.line(x + T / 2, y + T - lift + 3, x + T / 2, y + T - 2, 2);
                    }
                    // Top face
                    g.color(0xff2a3048);
                    g.fillRoundRect(x - 1, y - lift - 1, x + T + 1, y + T - lift + 1, 7);
                    if (fancy) g.vertical(x, y - lift, x + T, y + T - lift, 0xffa6b2d0, 0xff7a87a6);
                    else {
                        g.color(0xff7d8aa8);
                        g.fillRect(x, y - lift, x + T, y + T - lift);
                    }
                    g.color(0xffb8c4e0);
                    g.fillRoundRect(x + 5, y - lift + 4, x + T - 5, y - lift + 10, 3);
                    if (fancy) {
                        int hsh = hash(tx, ty);
                        g.color(0x55404a66);
                        float cx = x + 14 + (hsh & 15) * 2, cy = y - lift + 20 + ((hsh >>> 4) & 15);
                        g.line(cx, cy, cx + 10, cy + 8, 2);
                        g.line(cx + 10, cy + 8, cx + 6, cy + 18, 2);
                        g.color(0x22ffffff);
                        g.fillCircle(x + T - 16, y - lift + 22, 5);
                    }
                } else {
                    int bi = boxAt(ty * n + tx);
                    float flash = bi >= 0 && boxFlash[bi] > 0 ? 0.6f : 0f;
                    float wob = flash > 0 ? MathUtil.rand(-2f, 2f) : 0f;
                    x += wob;
                    g.color(MathUtil.mix(0xff6a3c14, 0xffffffff, flash));
                    g.fillRect(x + 2, y + T - lift, x + T - 2, y + T);
                    g.color(0xff3a200a);
                    g.fillRoundRect(x + 1, y - lift - 1, x + T - 1, y + T - lift + 1, 7);
                    if (fancy) g.vertical(x + 3, y - lift + 2, x + T - 3, y + T - lift - 2,
                            MathUtil.mix(0xffe0a35a, 0xffffffff, flash), MathUtil.mix(0xffb0742e, 0xffffffff, flash));
                    else {
                        g.color(MathUtil.mix(0xffc4873f, 0xffffffff, flash));
                        g.fillRect(x + 3, y - lift + 2, x + T - 3, y + T - lift - 2);
                    }
                    // Planks
                    g.color(0x668a5626);
                    g.line(x + 4, y - lift + T * 0.33f, x + T - 4, y - lift + T * 0.33f, 2);
                    g.line(x + 4, y - lift + T * 0.66f, x + T - 4, y - lift + T * 0.66f, 2);
                    // Metal corners
                    g.color(0xff8a8fa0);
                    g.fillRect(x + 3, y - lift + 2, x + 13, y - lift + 12);
                    g.fillRect(x + T - 13, y - lift + 2, x + T - 3, y - lift + 12);
                    g.fillRect(x + 3, y + T - lift - 12, x + 13, y + T - lift - 2);
                    g.fillRect(x + T - 13, y + T - lift - 12, x + T - 3, y + T - lift - 2);
                    // Glowing power cube emblem
                    float ex = x + T / 2, ey = y + T / 2 - lift;
                    if (fancy) g.radial(ex, ey, 26 + 3 * MathUtil.sin(tt * 4f + tx), 0xaa6dff6d, 0x006dff6d);
                    g.color(0xff14501c);
                    g.fillRoundRect(ex - 12, ey - 12, ex + 12, ey + 12, 4);
                    g.color(0xff6dff6d);
                    g.fillRoundRect(ex - 8, ey - 8, ex + 8, ey + 8, 3);
                    g.color(0xffe0ffe0);
                    g.fillRoundRect(ex - 6, ey - 6, ex - 1, ey - 1, 2);
                    if (bi >= 0 && boxHp[bi] < BOX_HP) {
                        float f = boxHp[bi] / BOX_HP;
                        g.color(0xcc000000);
                        g.fillRoundRect(x + 2, y - lift - 20, x + T - 2, y - lift - 6, 5);
                        g.color(0xff6dff6d);
                        g.fillRoundRect(x + 4, y - lift - 18, x + 4 + (T - 8) * f, y - lift - 8, 4);
                    }
                }
            }
        }
    }

    private boolean isHiddenFromViewer(Snake s, Snake viewer, float x, float y) {
        if (s == viewer || s.revealTime > 0) return false;
        if (s.invisTime > 0) {
            return viewer == null || !viewer.alive || MathUtil.dist2(x, y, viewer.hx(), viewer.hy()) > 150 * 150;
        }
        if (!bushAt(x, y)) return false;
        if (viewer == null || !viewer.alive) return true;
        return MathUtil.dist2(x, y, viewer.hx(), viewer.hy()) > REVEAL_DIST * REVEAL_DIST;
    }

    private boolean playerInBush() {
        if (player == null || !player.alive) return false;
        for (int j = 0; j < player.segs; j += 2) if (bushAt(player.sx[j], player.sy[j])) return true;
        return false;
    }

    private void drawSnakes(Gfx g, float l, float t, float r, float b, float tt, boolean skipPlayer) {
        // Smallest first so big snakes are drawn on top
        for (int i = 1; i < snakeCount; i++) {
            Snake s = drawOrder[i];
            int j = i - 1;
            while (j >= 0 && drawOrder[j].mass > s.mass) {
                drawOrder[j + 1] = drawOrder[j];
                j--;
            }
            drawOrder[j + 1] = s;
        }
        for (int i = 0; i < snakeCount; i++) {
            Snake s = drawOrder[i];
            if (!s.alive || (skipPlayer && s == player)) continue;
            drawSnake(g, s, 1f, l, t, r, b, tt);
        }
    }

    private void drawSnake(Gfx g, Snake s, float alpha, float l, float t, float r, float b, float tt) {
        if (s.maxX < l || s.minX > r || s.maxY < t || s.minY > b) return;
        Snake viewer = player;
        boolean anyHidden = false;
        for (int j = 0; j < s.segs; j++) {
            boolean hdn = viewer != null && isHiddenFromViewer(s, viewer, s.sx[j], s.sy[j]);
            hiddenBuf[j] = hdn;
            anyHidden |= hdn;
        }
        if (s == player && s.invisTime > 0 && s.revealTime <= 0) alpha = Math.min(alpha, 0.45f);
        float flash = s.hitFlash > 0 ? s.hitFlash / 0.12f * 0.8f : 0f;
        boolean glow = s.boosting || s.dashTime > 0;
        Snake.drawBody(g, s.sx, s.sy, s.segs, s.radius, s.palette, anyHidden ? hiddenBuf : null,
                alpha, glow, flash, tt, l, t, r, b);
        if (anyHidden && hiddenBuf[0]) return;
        float hr = s.radius * 1.18f;
        if (s.dashTime > 0) {
            g.color(0x66ff7a2a);
            g.fillCircle(s.hx(), s.hy(), hr * 1.9f);
        }
        if (s.slowTime > 0) {
            g.color(MathUtil.withAlpha(0xffbff0ff, 0.45f * alpha));
            g.fillCircle(s.hx(), s.hy(), hr * 1.6f);
        }
        if (s.superReady()) {
            float p = 0.5f + 0.5f * MathUtil.sin(tt * 8f);
            g.color(MathUtil.withAlpha(0xffffd23f, (0.35f + 0.3f * p) * alpha));
            g.strokeCircle(s.hx(), s.hy(), hr + 9 + p * 3, 5);
        }
        float look = s.ang;
        if (s == player && aimActive) look = aimAng;
        else if (s == player && hasLock()) look = MathUtil.angleTo(s.hx(), s.hy(), lockX, lockY);
        Snake.drawHead(g, s.hx(), s.hy(), s.radius, s.ang, look, s.color1, s.color2, s.type.id, s.type.accent,
                alpha, flash, tt + s.index, s.tongueTimer > 0 ? s.tongueTimer / 0.35f : 0f);
        if (s.spawnShield > 0) {
            g.color(MathUtil.withAlpha(0xff9ae6ff, 0.25f + 0.15f * MathUtil.sin(tt * 10f)));
            g.fillCircle(s.hx(), s.hy(), hr * 2f);
            g.color(0xccbff0ff);
            g.strokeCircle(s.hx(), s.hy(), hr * 2f, 3);
        }
    }

    static int projColor(int kind) {
        switch (kind) {
            case Projectile.PELLET:
                return 0xffffb030;
            case Projectile.BOLT:
                return 0xff4ac8ff;
            case Projectile.SUPERBOLT:
                return 0xffffe94a;
            case Projectile.SHARD:
                return 0xffaaeeff;
            case Projectile.BALL:
                return 0xffff6fb5;
            case Projectile.SHURIKEN:
                return 0xffff3a6a;
            case Projectile.GLOB:
            case Projectile.MEGAGLOB:
                return 0xffa6ff3a;
            case Projectile.FLAME:
                return 0xffff7a2a;
            default:
                return 0xffffa62e;
        }
    }

    private void drawTrail(Gfx g, Projectile p, int color, float width) {
        float px = p.x, py = p.y;
        for (int k = 0; k < p.trailCount; k++) {
            float x = p.trailX[k], y = p.trailY[k];
            float f = 1f - (k + 1f) / (Projectile.TRAIL + 1f);
            g.color(MathUtil.withAlpha(color, 0.7f * f));
            g.line(px, py, x, y, width * (0.35f + 0.65f * f));
            px = x;
            py = y;
        }
    }

    private void drawProjectiles(Gfx g, float l, float t, float r, float b, float tt) {
        boolean fancy = !lowGraphics;
        for (Projectile p : proj) {
            if (!p.active) continue;
            if (p.x < l - 300 || p.x > r + 300 || p.y < t - 300 || p.y > b + 300) continue;
            int col = projColor(p.kind);
            if (fancy && !p.isBomb() && p.kind != Projectile.FLAME) {
                drawTrail(g, p, col, p.radius * 1.6f);
                g.radial(p.x, p.y, p.radius * 3.4f, MathUtil.withAlpha(col, 0.7f), col & 0x00ffffff);
            }
            switch (p.kind) {
                case Projectile.PELLET:
                    if (!fancy) {
                        g.color(0x88ff9a2a);
                        g.line(p.x, p.y, p.x - p.vx * 0.025f, p.y - p.vy * 0.025f, p.radius * 1.4f);
                    }
                    g.color(0xff8a3a10);
                    g.fillCircle(p.x, p.y, p.radius + 2);
                    g.color(0xffffd060);
                    g.fillCircle(p.x, p.y, p.radius);
                    g.color(0xffffffff);
                    g.fillCircle(p.x - p.radius * 0.25f, p.y - p.radius * 0.25f, p.radius * 0.45f);
                    break;
                case Projectile.BOLT:
                case Projectile.SUPERBOLT: {
                    boolean sup = p.kind == Projectile.SUPERBOLT;
                    float tl = sup ? 0.05f : 0.04f;
                    g.color(sup ? 0x66fff04a : 0x663fb6ff);
                    g.line(p.x, p.y, p.x - p.vx * tl, p.y - p.vy * tl, p.radius * 2.6f);
                    g.color(sup ? 0xffffe94a : 0xff7ad8ff);
                    g.line(p.x, p.y, p.x - p.vx * tl, p.y - p.vy * tl, p.radius * 1.3f);
                    g.color(0xffffffff);
                    g.line(p.x, p.y, p.x - p.vx * tl * 0.7f, p.y - p.vy * tl * 0.7f, p.radius * 0.6f);
                    if (fancy) {
                        // Crackling arcs
                        float a = (float) Math.atan2(p.vy, p.vx) + MathUtil.PI / 2;
                        float j = MathUtil.rand(-1f, 1f) * p.radius * 1.4f;
                        g.color(0xccffffff);
                        g.line(p.x - p.vx * tl * 0.3f, p.y - p.vy * tl * 0.3f,
                                p.x - p.vx * tl * 0.5f + MathUtil.cos(a) * j, p.y - p.vy * tl * 0.5f + MathUtil.sin(a) * j, 2);
                    }
                    break;
                }
                case Projectile.BOMB:
                case Projectile.MEGABOMB: {
                    float f = Math.min(1f, p.t / p.flight);
                    float hgt = MathUtil.sin(f * MathUtil.PI) * (p.kind == Projectile.MEGABOMB ? 190 : 130);
                    float br = p.radius;
                    g.color(0x40000000);
                    g.fillCircle(p.x, p.y, br * (1f - 0.3f * hgt / 190f));
                    // Landing marker
                    float pulse = 0.5f + 0.5f * MathUtil.sin(tt * 14f);
                    g.color(MathUtil.withAlpha(0xffff3a2a, 0.25f + 0.25f * pulse));
                    g.fillCircle(p.targetX, p.targetY, p.aoe * f);
                    g.color(0xaaff3a2a);
                    g.strokeCircle(p.targetX, p.targetY, p.aoe, 4);
                    float by = p.y - hgt;
                    float fl = 0.7f + 0.3f * MathUtil.sin(tt * 40f);
                    if (fancy) g.radial(p.x + br * 0.6f, by - br * 0.9f, br * 1.6f * fl, 0xccffb02a, 0x00ff7a1a);
                    g.color(0xff1a1a22);
                    g.fillCircle(p.x, by, br + 3);
                    g.color(p.kind == Projectile.MEGABOMB ? 0xffb02a2a : 0xff3a3a48);
                    g.fillCircle(p.x, by, br);
                    g.color(0x88ffffff);
                    g.fillCircle(p.x - br * 0.35f, by - br * 0.35f, br * 0.3f);
                    g.color(0xffffa32a);
                    g.fillCircle(p.x + br * 0.6f, by - br * 0.9f, br * 0.45f * fl);
                    g.color(0xffffff8a);
                    g.fillCircle(p.x + br * 0.6f, by - br * 0.9f, br * 0.22f * fl);
                    break;
                }
                case Projectile.SHARD: {
                    float a = (float) Math.atan2(p.vy, p.vx);
                    float ca = MathUtil.cos(a), sa = MathUtil.sin(a);
                    float len = p.radius * 2.6f;
                    poly4[0] = p.x + ca * len;
                    poly4[1] = p.y + sa * len;
                    poly4[2] = p.x - sa * p.radius * 0.8f;
                    poly4[3] = p.y + ca * p.radius * 0.8f;
                    poly4[4] = p.x - ca * len;
                    poly4[5] = p.y - sa * len;
                    poly4[6] = p.x + sa * p.radius * 0.8f;
                    poly4[7] = p.y - ca * p.radius * 0.8f;
                    g.color(0xff4aa8e0);
                    g.fillPoly(poly4, 4);
                    g.color(0xffe8ffff);
                    g.fillCircle(p.x, p.y, p.radius * 0.45f);
                    break;
                }
                case Projectile.BALL: {
                    g.color(0xff7a3cff);
                    g.fillCircle(p.x, p.y, p.radius + 2.5f);
                    g.color(0xffff6fb5);
                    g.fillCircle(p.x, p.y, p.radius);
                    g.color(0xfffff04a);
                    g.fillCircle(p.x - p.radius * 0.3f, p.y - p.radius * 0.3f, p.radius * 0.4f);
                    break;
                }
                case Projectile.SHURIKEN: {
                    float spin = tt * 25f;
                    g.color(0xff1c1c2a);
                    g.fillCircle(p.x, p.y, p.radius * 0.6f);
                    for (int k = 0; k < 4; k++) {
                        float a = spin + k * MathUtil.PI / 2;
                        poly4[0] = p.x + MathUtil.cos(a) * p.radius * 1.7f;
                        poly4[1] = p.y + MathUtil.sin(a) * p.radius * 1.7f;
                        poly4[2] = p.x + MathUtil.cos(a + 0.7f) * p.radius * 0.5f;
                        poly4[3] = p.y + MathUtil.sin(a + 0.7f) * p.radius * 0.5f;
                        poly4[4] = p.x + MathUtil.cos(a - 0.7f) * p.radius * 0.5f;
                        poly4[5] = p.y + MathUtil.sin(a - 0.7f) * p.radius * 0.5f;
                        g.color(0xffd8d8e8);
                        g.fillPoly(poly4, 3);
                    }
                    g.color(0xffff3a6a);
                    g.fillCircle(p.x, p.y, p.radius * 0.25f);
                    break;
                }
                case Projectile.GLOB:
                case Projectile.MEGAGLOB: {
                    float f = Math.min(1f, p.t / p.flight);
                    float hgt = MathUtil.sin(f * MathUtil.PI) * (p.kind == Projectile.MEGAGLOB ? 170 : 120);
                    g.color(0x40000000);
                    g.fillCircle(p.x, p.y, p.radius * 0.8f);
                    g.color(0x88a6ff3a);
                    g.strokeCircle(p.targetX, p.targetY, p.aoe * (0.6f + 0.4f * f), 4);
                    float by = p.y - hgt;
                    if (fancy) g.radial(p.x, by, p.radius * 2.6f, 0x99a6ff3a, 0x00a6ff3a);
                    g.color(0xff3a1a66);
                    g.fillCircle(p.x, by, p.radius + 3);
                    g.color(0xffa6ff3a);
                    g.fillCircle(p.x, by, p.radius);
                    g.color(0xccf2ff8a);
                    g.fillCircle(p.x - p.radius * 0.3f, by - p.radius * 0.3f, p.radius * 0.35f);
                    break;
                }
                case Projectile.FLAME:
                default: {
                    float f = p.traveled / p.range;
                    if (fancy) {
                        g.radial(p.x, p.y, p.radius * 1.8f, MathUtil.withAlpha(0xffff5a1a, 0.8f * (1f - f)), 0x00ff3a0a);
                        g.radial(p.x, p.y, p.radius * 0.9f, MathUtil.withAlpha(0xffffe070, 1f - f * 0.7f), 0x00ffa62e);
                    } else {
                        g.color(MathUtil.withAlpha(0xffff4a1a, 0.75f * (1f - f)));
                        g.fillCircle(p.x, p.y, p.radius * 1.15f);
                        g.color(MathUtil.withAlpha(0xffffb42a, 0.9f * (1f - f * 0.8f)));
                        g.fillCircle(p.x, p.y, p.radius * 0.75f);
                    }
                    break;
                }
            }
        }
    }

    private void drawBushes(Gfx g, float l, float t, float r, float b, float tt, boolean playerIn) {
        boolean fancy = !lowGraphics;
        int tx0 = Math.max(0, (int) (l / T) - 1), tx1 = Math.min(n - 1, (int) (r / T) + 1);
        int ty0 = Math.max(0, (int) (t / T) - 1), ty1 = Math.min(n - 1, (int) (b / T) + 1);
        float a = playerIn ? 0.72f : 1f;
        int passes = fancy ? 5 : 2;
        for (int pass = 0; pass < passes; pass++) {
            int c;
            if (fancy) c = pass == 0 ? 0x33000000 : pass == 1 ? 0xff1e5426 : pass == 2 ? 0xff3a9a3a : pass == 3 ? 0xff5cc04c : 0xff8ae070;
            else c = pass == 0 ? 0xff23602a : 0xff3f9b3c;
            g.color(MathUtil.withAlpha(c, a));
            for (int ty = ty0; ty <= ty1; ty++) {
                for (int tx = tx0; tx <= tx1; tx++) {
                    if (!bush[ty * n + tx]) continue;
                    float x = tx * T + T / 2, y = ty * T + T / 2;
                    float sway = MathUtil.sin(tt * 1.6f + tx * 0.7f + ty * 1.3f) * 2.5f;
                    int hsh = hash(tx, ty);
                    float jx = ((hsh & 7) - 3.5f), jy = (((hsh >>> 3) & 7) - 3.5f);
                    if (!fancy) {
                        float rr = pass == 0 ? 30 : 24;
                        g.fillCircle(x - 14 + sway, y - 12, rr);
                        g.fillCircle(x + 14 + sway, y - 10, rr);
                        g.fillCircle(x - 12 + sway, y + 14, rr);
                        g.fillCircle(x + 14 + sway, y + 14, rr);
                        continue;
                    }
                    switch (pass) {
                        case 0:
                            g.fillCircle(x + 8, y + 14, 38);
                            break;
                        case 1:
                            g.fillCircle(x - 14 + sway + jx, y - 12 + jy, 31);
                            g.fillCircle(x + 14 + sway, y - 10, 31);
                            g.fillCircle(x - 12 + sway, y + 14, 31);
                            g.fillCircle(x + 14 + sway + jy, y + 14 + jx, 31);
                            break;
                        case 2:
                            g.fillCircle(x - 14 + sway + jx, y - 15 + jy, 25);
                            g.fillCircle(x + 14 + sway, y - 13, 25);
                            g.fillCircle(x - 12 + sway, y + 11, 25);
                            g.fillCircle(x + 14 + sway + jy, y + 11 + jx, 25);
                            break;
                        case 3:
                            g.fillCircle(x - 17 + sway + jx, y - 21 + jy, 13);
                            g.fillCircle(x + 11 + sway, y - 19, 12);
                            g.fillCircle(x - 7 + sway, y + 4, 11);
                            break;
                        default:
                            g.fillCircle(x - 20 + sway + jx, y - 25 + jy, 5);
                            g.fillCircle(x + 8 + sway, y - 23, 4);
                            if ((hsh & 31) == 0) {
                                // Rare little flower
                                g.color(MathUtil.withAlpha(0xffff7ab0, a));
                                g.fillCircle(x + 4 + sway, y + 2, 6);
                                g.color(MathUtil.withAlpha(0xffffe14a, a));
                                g.fillCircle(x + 4 + sway, y + 2, 2.5f);
                                g.color(MathUtil.withAlpha(c, a));
                            }
                            break;
                    }
                }
            }
        }
    }

    private void drawLabels(Gfx g, float l, float t, float r, float b) {
        Snake viewer = player;
        boolean fancy = !lowGraphics;
        for (int i = 0; i < snakeCount; i++) {
            Snake s = snakes[i];
            if (!s.alive) continue;
            float x = s.hx(), y = s.hy();
            if (x < l - 150 || x > r + 150 || y < t - 150 || y > b + 150) continue;
            if (viewer != null && isHiddenFromViewer(s, viewer, x, y)) continue;
            float hr = s.radius * 1.18f;
            float barW = 100, barH = 18;
            float by = y - hr - 42;
            float f = MathUtil.clamp(s.hp / s.maxHp, 0, 1);
            s.hpShown += (f - s.hpShown) * 0.08f;
            if (s.hpShown < f) s.hpShown = f;
            boolean mine = s.isPlayer;
            g.color(0xee0c0c1a);
            g.fillRoundRect(x - barW / 2 - 4, by - 4, x + barW / 2 + 4, by + barH + 4, 9);
            g.color(0xff3a2030);
            g.fillRoundRect(x - barW / 2, by, x + barW / 2, by + barH, 6);
            // Trailing "damage" chunk, then the real value
            g.color(0xfffff2c0);
            g.fillRoundRect(x - barW / 2, by, x - barW / 2 + barW * s.hpShown, by + barH, 6);
            int top = mine ? 0xff8cff6a : 0xffff7a5a, bot = mine ? 0xff2ea82e : 0xffc8202a;
            if (f > 0) {
                if (fancy) g.vertical(x - barW / 2, by, x - barW / 2 + barW * f, by + barH, top, bot);
                else {
                    g.color(bot);
                    g.fillRect(x - barW / 2, by, x - barW / 2 + barW * f, by + barH);
                }
                g.color(0x55ffffff);
                g.fillRoundRect(x - barW / 2 + 2, by + 2, x - barW / 2 + Math.max(4, barW * f - 2), by + 6, 3);
            }
            g.color(0xffffffff);
            g.text(Integer.toString((int) Math.ceil(s.hp)), x, by + barH - 3f, 18, Gfx.ALIGN_CENTER, 3, 0xff000000);
            g.color(mine ? 0xff9cff8a : 0xffffffff);
            g.text(s.name, x, by - 9, 23, Gfx.ALIGN_CENTER, 4, 0xff000000);
            if (s.cubes > 0) {
                float cxp = x + barW / 2 + 20;
                Icons.cube(g, cxp, by + barH / 2, 24, 1f);
                g.color(0xffffffff);
                g.text(Integer.toString(s.cubes), cxp, by + barH / 2 + 7, 18, Gfx.ALIGN_CENTER, 3, 0xff000000);
            }
            if (mine) {
                float ay = by + barH + 7;
                float sw = (barW - 8) / 3f;
                for (int k = 0; k < 3; k++) {
                    float x0 = x - barW / 2 + k * (sw + 4);
                    g.color(0xee0c0c1a);
                    g.fillRoundRect(x0 - 2, ay - 2, x0 + sw + 2, ay + 10, 4);
                    float fill = MathUtil.clamp(s.ammo - k, 0, 1);
                    g.color(fill >= 1 ? 0xffffa62e : 0xff9a5a1a);
                    if (fill > 0) g.fillRoundRect(x0, ay, x0 + sw * fill, ay + 8, 3);
                }
            }
        }
    }

    // ------------------------------------------------------------------ auto-aim lock-on

    /** The player's current auto-aim target (nearest visible enemy), smoothed for display. */
    Snake lockTarget;
    float lockX, lockY, lockAnim;
    private boolean lockValid;

    void updateLock(float dt) {
        Snake p = player;
        lockValid = false;
        if (p == null || !p.alive) {
            lockTarget = null;
            return;
        }
        float range = Math.max(p.type.range, p.type.superRange) * 1.1f;
        if (findAim(p, range, p.type.projSpeed)) {
            // findAim also returns boxes when no snake is around; only lock onto snakes
            Snake best = null;
            float bd = Float.MAX_VALUE;
            float ax = p.hx() + MathUtil.cos(aimOutAng) * aimOutDist, ay = p.hy() + MathUtil.sin(aimOutAng) * aimOutDist;
            for (int i = 0; i < snakeCount; i++) {
                Snake o = snakes[i];
                if (o == p || !o.alive || !visibleTo(o, p)) continue;
                if (ax < o.minX - 80 || ax > o.maxX + 80 || ay < o.minY - 80 || ay > o.maxY + 80) continue;
                float d = MathUtil.dist2(ax, ay, o.hx(), o.hy());
                if (d < bd) {
                    bd = d;
                    best = o;
                }
            }
            if (best != null) {
                if (best != lockTarget) {
                    lockAnim = 0;
                    lockX = ax;
                    lockY = ay;
                }
                lockTarget = best;
                lockValid = true;
                float k = Math.min(1f, dt * 16f);
                lockX += (ax - lockX) * k;
                lockY += (ay - lockY) * k;
                lockAnim = Math.min(1f, lockAnim + dt * 5f);
                return;
            }
        }
        lockTarget = null;
    }

    boolean hasLock() {
        return lockValid && lockTarget != null && lockTarget.alive;
    }

    private void drawLockOn(Gfx g, float tt) {
        if (!hasLock() || player == null || !player.alive) return;
        float x = lockX, y = lockY;
        float a = lockAnim;
        float size = 46f + (1f - a) * 40f + 4f * MathUtil.sin(tt * 6f);
        int col = player.superReady() ? 0xffffd23f : 0xffff3a3a;
        // Dotted guide line from the head
        float hx = player.hx(), hy = player.hy();
        float d = MathUtil.dist(hx, hy, x, y);
        int dots = (int) (d / 34f);
        float off = (tt * 90f) % 34f;
        g.color(MathUtil.withAlpha(0xffffffff, 0.35f * a));
        for (int k = 1; k < dots; k++) {
            float f = (k * 34f + off) / d;
            if (f >= 1f) break;
            g.fillCircle(hx + (x - hx) * f, hy + (y - hy) * f, 3.5f);
        }
        if (!lowGraphics) g.radial(x, y, size * 1.2f, MathUtil.withAlpha(col, 0.25f * a), col & 0x00ffffff);
        g.save();
        g.translate(x, y);
        g.rotate(tt * 90f);
        for (int k = 0; k < 4; k++) {
            g.rotate(90);
            g.color(MathUtil.withAlpha(0xff14142a, a));
            g.arc(0, 0, size, -28, 56, 11);
            g.color(MathUtil.withAlpha(col, a));
            g.arc(0, 0, size, -26, 52, 6);
        }
        g.restore();
        g.color(MathUtil.withAlpha(col, a));
        g.strokeCircle(x, y, 10, 4);
        g.fillCircle(x, y, 3.5f);
    }

    private void drawAim(Gfx g) {
        if (!aimActive || player == null || !player.alive) return;
        Snake s = player;
        float hx = s.hx(), hy = s.hy();
        int col = aimSuper ? 0x66ffd23f : 0x55ffffff;
        int edge = aimSuper ? 0xccffd23f : 0xaaffffff;
        float ca = MathUtil.cos(aimAng), sa = MathUtil.sin(aimAng);
        float range = aimSuper ? s.type.superRange : s.type.range;
        switch (s.type.id) {
            case Brawler.FROST:
            case Brawler.ZIGGY:
                if (aimSuper) {
                    g.color(col);
                    g.fillCircle(hx, hy, s.type.id == Brawler.FROST ? range : 260);
                    g.color(edge);
                    g.strokeCircle(hx, hy, s.type.id == Brawler.FROST ? range : 260, 4);
                    break;
                }
                if (s.type.id == Brawler.ZIGGY) {
                    drawAimLane(g, hx, hy, ca, sa, range, 16, col, edge);
                    break;
                }
                // fall through: Frost's shards use a narrow cone
            case Brawler.VIPER:
            case Brawler.BLAZE:
            case Brawler.SHADE: {
                int id = s.type.id;
                float spread = id == Brawler.VIPER ? (aimSuper ? 0.5f : 0.34f) : id == Brawler.FROST ? 0.15f : id == Brawler.SHADE ? 0.12f : 0.24f;
                if (id == Brawler.BLAZE && aimSuper) {
                    drawAimLane(g, hx, hy, ca, sa, 2.6f * Snake.BASE_SPEED * s.type.speed * 1.05f, s.radius * 1.4f, col, edge);
                    break;
                }
                if (id == Brawler.SHADE && aimSuper) {
                    float d = MathUtil.clamp(aimDist, 180, range);
                    drawAimLane(g, hx, hy, ca, sa, d, s.radius, col, edge);
                    g.color(edge);
                    g.strokeCircle(hx + ca * d, hy + sa * d, s.radius * 1.6f, 4);
                    break;
                }
                int segs = 10;
                int k = 1;
                float[] poly = tmpPoly;
                poly[0] = hx;
                poly[1] = hy;
                for (int i = 0; i <= segs; i++) {
                    float a = aimAng - spread + 2 * spread * i / segs;
                    poly[k * 2] = hx + MathUtil.cos(a) * range;
                    poly[k * 2 + 1] = hy + MathUtil.sin(a) * range;
                    k++;
                }
                g.color(col);
                g.fillPoly(poly, k);
                g.color(edge);
                g.arc(hx, hy, range, (float) Math.toDegrees(aimAng - spread), (float) Math.toDegrees(2 * spread), 4);
                break;
            }
            case Brawler.VOLT:
                drawAimLane(g, hx, hy, ca, sa, range, aimSuper ? 30 : 18, col, edge);
                break;
            case Brawler.BOOMER:
            default: {
                float d = MathUtil.clamp(aimDist, 110, range);
                float tx = hx + ca * d, ty = hy + sa * d;
                float aoe = s.type.id == Brawler.TOXIN ? (aimSuper ? 210 : 95) : (aimSuper ? 230 : 105);
                g.color(edge);
                for (int i = 1; i < 12; i++) {
                    float f = i / 12f;
                    float hgt = MathUtil.sin(f * MathUtil.PI) * 80;
                    g.fillCircle(hx + ca * d * f, hy + sa * d * f - hgt, 5);
                }
                g.color(col);
                g.fillCircle(tx, ty, aoe);
                g.color(edge);
                g.strokeCircle(tx, ty, aoe, 4);
                break;
            }
        }
    }

    private void drawAimLane(Gfx g, float hx, float hy, float ca, float sa, float len, float halfW, int col, int edge) {
        float px = -sa * halfW, py = ca * halfW;
        tmpPoly[0] = hx + px;
        tmpPoly[1] = hy + py;
        tmpPoly[2] = hx + ca * len + px;
        tmpPoly[3] = hy + sa * len + py;
        tmpPoly[4] = hx + ca * len - px;
        tmpPoly[5] = hy + sa * len - py;
        tmpPoly[6] = hx - px;
        tmpPoly[7] = hy - py;
        g.color(col);
        g.fillPoly(tmpPoly, 4);
        g.color(edge);
        g.line(tmpPoly[2], tmpPoly[3], tmpPoly[4], tmpPoly[5], 4);
    }

    private void drawPoison(Gfx g, float l, float t, float r, float b, float tt) {
        if (!zoneActive()) return;
        int fog = 0x8a2a9a40;
        g.color(fog);
        float L = Math.max(l, -2000), R = Math.min(r, size + 2000), Tp = Math.max(t, -2000), B = Math.min(b, size + 2000);
        if (zoneT > Tp) g.fillRect(L, Tp, R, Math.min(zoneT, B));
        if (zoneB < B) g.fillRect(L, Math.max(zoneB, Tp), R, B);
        float midT = Math.max(zoneT, Tp), midB = Math.min(zoneB, B);
        if (midB > midT) {
            if (zoneL > L) g.fillRect(L, midT, Math.min(zoneL, R), midB);
            if (zoneR < R) g.fillRect(Math.max(zoneR, L), midT, R, midB);
        }
        // Puffy cloud edge
        boolean fancy = !lowGraphics;
        float step = 70f;
        g.color(0x993fd05a);
        for (int side = 0; side < 4; side++) {
            boolean horiz = side < 2;
            float fixed = side == 0 ? zoneT : side == 1 ? zoneB : side == 2 ? zoneL : zoneR;
            float from = horiz ? Math.max(zoneL, l - step) : Math.max(zoneT, t - step);
            float to = horiz ? Math.min(zoneR, r + step) : Math.min(zoneB, b + step);
            if (horiz ? (fixed < t - 80 || fixed > b + 80) : (fixed < l - 80 || fixed > r + 80)) continue;
            float start = (float) Math.floor(from / step) * step;
            for (float p = start; p <= to; p += step) {
                float wob = MathUtil.sin(tt * 2f + p * 0.05f) * 10f;
                float rad = 50f + MathUtil.sin(tt * 1.3f + p * 0.11f) * 12f;
                float cx = horiz ? p : fixed + wob, cy = horiz ? fixed + wob : p;
                if (fancy) g.radial(cx, cy, rad * 1.5f, 0xbb62e070, 0x0040c050);
                else g.fillCircle(cx, cy, rad);
            }
        }
    }
}
