package com.snakebrawl.myapp.game;

/** One arena: map, snakes, projectiles, food, poison and effects. */
final class World {
    static final int MODE_SHOWDOWN = 0;
    static final int MODE_ENDLESS = 1;
    static final int MODE_DEMO = 2;
    /** Duo Showdown: teams of two, last team standing. */
    static final int MODE_DUO = 3;

    static final float T = 64f;
    static final byte EMPTY = 0, WALL = 1, BOX = 2;

    static final int CAUSE_SHOT = 0, CAUSE_CRASH = 1, CAUSE_POISON = 2, CAUSE_DASH = 3;

    static final float ZONE_START = 25f;
    static final float ZONE_END = 205f;
    static final float REVEAL_DIST = 230f;
    static final float PLAYER_DAMAGE_TAKEN = 0.45f;
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
        this(platform, mode, playerType, playerPalette, playerLevel, null, null, 1, 0, botCount, trophies);
    }

    /** Random source for this match. Seeded identically on every phone in a Wi-Fi game. */
    final Rng rng;
    /** Number of human-controlled snakes (snakes[0 .. humans-1]). */
    final int humans;
    /** Team size in team modes (2 for Duo, 3 for a Trio of friends). */
    final int teamSize;
    /** Map id (see {@link Maps}) and its look. */
    final int mapId;
    final Theme theme;

    /**
     * Two-player constructor. With a guest brawler, snakes[0] is the host and snakes[1] the guest;
     * {@code localIndex} picks which of them this phone controls.
     */
    World(Platform platform, int mode, Brawler playerType, int[] playerPalette, int playerLevel, Brawler guestType,
            int[] guestPalette, int guestLevel, int localIndex, int botCount, int trophies) {
        this(platform, mode,
                playerType == null ? new Brawler[0] : guestType == null ? new Brawler[]{playerType} : new Brawler[]{playerType, guestType},
                new int[][]{playerPalette, guestPalette}, new int[]{playerLevel, guestLevel}, localIndex, botCount, trophies, 2,
                Maps.SUNNY, null);
    }

    /**
     * Full constructor: snakes[0 .. types.length-1] are humans in that order and {@code localIndex}
     * is the one this phone controls. Team modes put {@code teamSize} snakes in each team.
     */
    World(Platform platform, int mode, Brawler[] types, int[][] palettes, int[] levels, int localIndex, int botCount,
            int trophies, int teamSize, int mapId, Maps.Custom custom) {
        this.rng = MathUtil.RNG;
        this.platform = platform;
        this.botTrophies = trophies;
        this.mode = mode;
        this.teamSize = Math.max(2, teamSize);
        this.mapId = custom != null ? Maps.CUSTOM : mapId;
        this.theme = Theme.get(custom != null ? custom.theme : mapId);
        boolean br = mode == MODE_SHOWDOWN || mode == MODE_DUO;
        this.n = br ? 66 : (mode == MODE_ENDLESS ? 80 : 56);
        this.size = n * T;
        this.tiles = new byte[n * n];
        this.bush = new boolean[n * n];
        this.grid = new SegGrid(size);
        this.orbTarget = br ? 480 : (mode == MODE_ENDLESS ? 760 : 420);
        this.boxTarget = br ? 14 : (mode == MODE_ENDLESS ? 18 : 8);
        for (int i = 0; i < proj.length; i++) proj[i] = new Projectile();

        humans = types.length;
        boolean hasPlayer = humans > 0;
        snakeCount = botCount + humans;
        snakes = new Snake[snakeCount];
        drawOrder = new Snake[snakeCount];
        for (int i = 0; i < snakeCount; i++) {
            snakes[i] = new Snake(i);
            drawOrder[i] = snakes[i];
        }

        // Spawn points on a ring so nobody starts on top of someone else
        float[] spX = new float[snakeCount], spY = new float[snakeCount], spA = new float[snakeCount];
        float a0 = MathUtil.rand(0, MathUtil.TAU);
        boolean duo = mode == MODE_DUO;
        int ts = this.teamSize;
        int groups = duo ? (snakeCount + ts - 1) / ts : snakeCount;
        for (int i = 0; i < snakeCount; i++) {
            int gi = duo ? i / ts : i;
            float a = a0 + MathUtil.TAU * gi / groups;
            float side = 0f;
            if (duo) side = ts == 2 ? ((i & 1) == 0 ? -90f : 90f) : (i % ts - (ts - 1) / 2) * 120f;
            spX[i] = size / 2 + MathUtil.cos(a) * size * 0.36f - MathUtil.sin(a) * side;
            spY[i] = size / 2 + MathUtil.sin(a) * size * 0.36f + MathUtil.cos(a) * side;
            spA[i] = a + MathUtil.PI;
            snakes[i].team = duo ? gi : -1;
        }
        if (custom != null) applyCustom(custom, spX, spY);
        else if (mapId == Maps.FROST) generateFrost(spX, spY);
        else if (mapId == Maps.LAVA) generateLava(spX, spY);
        else generateMap(spX, spY);

        zoneL = 0;
        zoneT = 0;
        zoneR = size;
        zoneB = size;

        String[] names = shuffledNames();
        float startMass = br ? 40f : 45f;
        for (int i = 0; i < snakeCount; i++) {
            Snake s = snakes[i];
            if (i < humans) {
                Brawler ht = types[i];
                int[] pal = palettes != null && i < palettes.length ? palettes[i] : null;
                int lvl = levels != null && i < levels.length ? levels[i] : 1;
                s.isPlayer = true;
                s.name = "You";
                s.setColors(pal != null ? pal : new int[]{ht.color1, ht.color2});
                s.level = Math.max(1, Math.min(Brawler.MAX_LEVEL, lvl));
                s.spawn(ht, spX[i], spY[i], spA[i], startMass);
            } else {
                s.name = names[i % names.length];
                dressBot(s, i);
                Brawler b = Brawler.ALL[MathUtil.randInt(Brawler.ALL.length)];
                float m = br ? startMass : MathUtil.rand(45, 260);
                s.spawn(b, spX[i], spY[i], spA[i], m);
                s.brain = new BotBrain(this, s, trophies);
            }
        }
        player = hasPlayer ? snakes[Math.max(0, Math.min(localIndex, humans - 1))] : null;
        focus = hasPlayer ? player : snakes[0];
        camX = focus.hx();
        camY = focus.hy();

        for (int i = 0; i < orbTarget; i++) spawnNaturalOrb();
        for (int i = 0; i < boxTarget; i++) spawnBox();
        countAlive();
        if (mode == MODE_SHOWDOWN) banner("SHOWDOWN!");
        else if (mode == MODE_DUO) banner(ts == 3 ? "TRIO SHOWDOWN!" : "DUO SHOWDOWN!");
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

    static final float DUO_RESPAWN = 5f;

    static boolean sameTeam(Snake a, Snake b) {
        return a != null && b != null && a != b && a.team >= 0 && a.team == b.team;
    }

    /** A teammate of {@code s}: a living one when there is one (Trio teams have two). */
    Snake mateOf(Snake s) {
        if (s.team < 0) return null;
        Snake any = null;
        for (int i = 0; i < snakeCount; i++) {
            if (!sameTeam(s, snakes[i])) continue;
            if (snakes[i].alive) return snakes[i];
            if (any == null) any = snakes[i];
        }
        return any;
    }

    /** All teammates of {@code s} (up to two), in snake order; returns how many were written. */
    int matesOf(Snake s, Snake[] out) {
        int n = 0;
        if (s.team < 0) return 0;
        for (int i = 0; i < snakeCount && n < out.length; i++) if (sameTeam(s, snakes[i])) out[n++] = snakes[i];
        return n;
    }

    /** Teams with at least one living member (duo), or living snakes otherwise. */
    int aliveTeams() {
        if (mode != MODE_DUO) return aliveCount;
        int mask = 0;
        for (int i = 0; i < snakeCount; i++) if (snakes[i].alive && snakes[i].team >= 0) mask |= 1 << snakes[i].team;
        return Integer.bitCount(mask);
    }

    private void respawnNear(Snake s, Snake mate) {
        float a = mate.ang + MathUtil.PI;
        float x = mate.hx() + MathUtil.cos(a) * 160, y = mate.hy() + MathUtil.sin(a) * 160;
        for (int k = 0; k < 12 && solidCircle(x, y, 60); k++) {
            a += 0.5f;
            x = mate.hx() + MathUtil.cos(a) * 160;
            y = mate.hy() + MathUtil.sin(a) * 160;
        }
        x = MathUtil.clamp(x, 80, size - 80);
        y = MathUtil.clamp(y, 80, size - 80);
        int kills = s.kills;
        s.spawn(s.type, x, y, mate.ang, 40f);
        s.kills = kills;
        if (s.brain != null) s.brain.reset();
        fx.ring(x, y, 120, 0xff9ae6ff, 0.5f);
        fx.flash(x, y, 140, 0xff9ae6ff, 0.3f);
        if (s == player) banner("BACK IN THE FIGHT!");
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

    /** Sets a wall tile unless it is near a spawn point or the arena edge. */
    private void wallAt(int x, int y, float[] spX, float[] spY, float clear) {
        if (x < 2 || y < 2 || x >= n - 2 || y >= n - 2) return;
        if (nearSpawn(x, y, spX, spY, clear)) return;
        tiles[y * n + x] = WALL;
        bush[y * n + x] = false;
    }

    private void plantBush(int x, int y) {
        if (x < 1 || y < 1 || x >= n - 1 || y >= n - 1) return;
        if (tiles[y * n + x] == EMPTY) bush[y * n + x] = true;
    }

    /**
     * Frost Peak: an icy fortress, the same in all four corners (mirror symmetric), with long
     * walls, corner forts, pillars and snowy pine groves.
     */
    private void generateFrost(float[] spX, float[] spY) {
        int h = n / 2;
        int[] wx = new int[4096], wy = new int[4096];
        int wc = 0;
        // Long ice walls in the quarter, then mirrored
        int lines = 7;
        for (int k = 0; k < lines; k++) {
            int x = 4 + MathUtil.randInt(h - 6), y = 4 + MathUtil.randInt(h - 6);
            boolean horiz = MathUtil.rand() < 0.5f;
            int len = 4 + MathUtil.randInt(5);
            for (int i = 0; i < len && wc < wx.length; i++) {
                wx[wc] = horiz ? x + i : x;
                wy[wc] = horiz ? y : y + i;
                wc++;
            }
            if (MathUtil.rand() < 0.45f) {
                // Turn it into an L-shaped fort corner
                int len2 = 2 + MathUtil.randInt(3);
                for (int i = 1; i <= len2 && wc < wx.length; i++) {
                    wx[wc] = horiz ? x : x + i;
                    wy[wc] = horiz ? y + i : y;
                    wc++;
                }
            }
        }
        // Ice pillars
        for (int k = 0; k < 5; k++) {
            int x = 3 + MathUtil.randInt(h - 4), y = 3 + MathUtil.randInt(h - 4);
            for (int dy = 0; dy < 2; dy++)
                for (int dx = 0; dx < 2; dx++) {
                    wx[wc] = x + dx;
                    wy[wc] = y + dy;
                    wc++;
                }
        }
        // Central keep: four short walls around the middle
        for (int i = -3; i <= -1; i++) {
            wx[wc] = h + i;
            wy[wc] = h - 4;
            wc++;
            wx[wc] = h - 4;
            wy[wc] = h + i;
            wc++;
        }
        for (int k = 0; k < wc; k++) {
            int x = wx[k], y = wy[k];
            if (x >= h || y >= h) continue;
            wallAt(x, y, spX, spY, 4f);
            wallAt(n - 1 - x, y, spX, spY, 4f);
            wallAt(x, n - 1 - y, spX, spY, 4f);
            wallAt(n - 1 - x, n - 1 - y, spX, spY, 4f);
        }
        // Pine groves: compact clumps, mirrored too
        for (int k = 0; k < 7; k++) {
            int cx = 3 + MathUtil.randInt(h - 4), cy = 3 + MathUtil.randInt(h - 4);
            int r = 1 + MathUtil.randInt(2);
            for (int dy = -r; dy <= r; dy++)
                for (int dx = -r; dx <= r; dx++) {
                    if (dx * dx + dy * dy > r * r + 1) continue;
                    int x = cx + dx, y = cy + dy;
                    if (x >= h || y >= h || x < 0 || y < 0) continue;
                    plantBush(x, y);
                    plantBush(n - 1 - x, y);
                    plantBush(x, n - 1 - y);
                    plantBush(n - 1 - x, n - 1 - y);
                }
        }
    }

    /**
     * Lava Canyon: big rock masses that are the same when the arena is turned upside down, a ring of
     * dry scrub around an open plaza in the middle, and cracked ridges.
     */
    private void generateLava(float[] spX, float[] spY) {
        int c = n / 2;
        // Rock masses: random-walk blobs, each placed twice (point symmetry)
        for (int k = 0; k < 9; k++) {
            int x = 4 + MathUtil.randInt(n - 8), y = 4 + MathUtil.randInt(c - 4);
            int steps = 10 + MathUtil.randInt(14);
            for (int st = 0; st < steps; st++) {
                for (int dy = 0; dy <= 1; dy++)
                    for (int dx = 0; dx <= 1; dx++) {
                        int px = x + dx, py = y + dy;
                        if (Math.abs(px - c) < 7 && Math.abs(py - c) < 7) continue; // keep the plaza open
                        wallAt(px, py, spX, spY, 4.5f);
                        wallAt(n - 1 - px, n - 1 - py, spX, spY, 4.5f);
                    }
                int d = MathUtil.randInt(4);
                if (d == 0) x++;
                else if (d == 1) x--;
                else if (d == 2) y++;
                else y--;
                x = Math.max(3, Math.min(n - 5, x));
                y = Math.max(3, Math.min(c - 2, y));
            }
        }
        // Ridges: thin diagonal-ish lines
        for (int k = 0; k < 4; k++) {
            int x = 5 + MathUtil.randInt(n - 10), y = 5 + MathUtil.randInt(c - 8);
            int dx = MathUtil.rand() < 0.5f ? 1 : -1;
            int len = 4 + MathUtil.randInt(4);
            for (int i = 0; i < len; i++) {
                int px = x + i * dx, py = y + i / 2;
                wallAt(px, py, spX, spY, 4.5f);
                wallAt(n - 1 - px, n - 1 - py, spX, spY, 4.5f);
            }
        }
        // Scrub ring around the plaza, with gaps
        for (int a = 0; a < 48; a++) {
            if (a % 8 < 2) continue;
            float ang = a * MathUtil.TAU / 48f;
            int x = c + Math.round(MathUtil.cos(ang) * 8.5f), y = c + Math.round(MathUtil.sin(ang) * 8.5f);
            plantBush(x, y);
            plantBush(x + 1, y);
        }
        // Scattered scrub patches
        for (int k = 0; k < 8; k++) {
            int x = 3 + MathUtil.randInt(n - 6), y = 3 + MathUtil.randInt(c - 3);
            int steps = 5 + MathUtil.randInt(7);
            for (int st = 0; st < steps; st++) {
                plantBush(x, y);
                plantBush(n - 1 - x, n - 1 - y);
                int d = MathUtil.randInt(4);
                if (d == 0) x++;
                else if (d == 1) x--;
                else if (d == 2) y++;
                else y--;
                x = Math.max(2, Math.min(n - 3, x));
                y = Math.max(2, Math.min(c - 1, y));
            }
        }
    }

    /** A player-made map: its grid is stretched over the arena; spawn areas are always kept clear. */
    private void applyCustom(Maps.Custom m, float[] spX, float[] spY) {
        for (int ty = 1; ty < n - 1; ty++) {
            for (int tx = 1; tx < n - 1; tx++) {
                int gx = Math.min(Maps.G - 1, tx * Maps.G / n), gy = Math.min(Maps.G - 1, ty * Maps.G / n);
                byte c = m.cells[gy * Maps.G + gx];
                if (c == Maps.WALL) {
                    if (!nearSpawn(tx, ty, spX, spY, 2.5f)) tiles[ty * n + tx] = WALL;
                } else if (c == Maps.BUSH) {
                    bush[ty * n + tx] = true;
                }
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
        return (mode == MODE_SHOWDOWN || mode == MODE_DUO) && matchTime > ZONE_START;
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
        Rng prev = MathUtil.RNG;
        MathUtil.RNG = rng;
        try {
            simulate(dt);
        } finally {
            MathUtil.RNG = prev;
        }
    }

    /** One lockstep tick of a two-player game (kept for the determinism replay). */
    void netStep(float dt, NetInput host, NetInput guest) {
        netStep(dt, new NetInput[]{host, guest});
    }

    /** One lockstep tick of a Wi-Fi game: applies every human's input (by snake index), then simulates. */
    void netStep(float dt, NetInput[] inputs) {
        Rng prev = MathUtil.RNG;
        MathUtil.RNG = rng;
        try {
            for (int i = 0; i < humans && i < inputs.length; i++) applyInput(snakes[i], inputs[i]);
            simulate(dt);
        } finally {
            MathUtil.RNG = prev;
        }
    }

    /** Lockstep-safe {@link #makeBot}: every phone calls it at the same tick. */
    void netMakeBot(Snake s) {
        Rng prev = MathUtil.RNG;
        MathUtil.RNG = rng;
        try {
            makeBot(s);
        } finally {
            MathUtil.RNG = prev;
        }
    }

    private void applyInput(Snake s, NetInput in) {
        if (in == null || !s.alive || s.brain != null) return;
        if (in.steer) s.targetAng = in.ang;
        s.boostInput = in.boost;
        if (in.attack == NetInput.ATTACK) s.tryAttack(this, in.atkAng, in.atkDist);
        else if (in.attack == NetInput.SUPER) s.trySuper(this, in.atkAng, in.atkDist);
    }

    /** Turns a human snake into a bot, e.g. when the friend's phone disconnects. */
    void makeBot(Snake s) {
        if (s.brain == null) s.brain = new BotBrain(this, s, botTrophies);
        // Lockstep has ended when this runs, so dropping the player perks is safe
        s.isPlayer = false;
        s.boostInput = false;
    }

    /** Cheap fingerprint of the simulation, compared between phones to detect desyncs. */
    int stateHash() {
        int h = orbCount * 31 + projCountForHash();
        for (int i = 0; i < snakeCount; i++) {
            Snake s = snakes[i];
            h = h * 31 + (s.alive ? 1 : 0);
            h = h * 31 + Float.floatToIntBits(s.hx());
            h = h * 31 + Float.floatToIntBits(s.hy());
            h = h * 31 + Float.floatToIntBits(s.hp);
            h = h * 31 + Float.floatToIntBits(s.mass);
        }
        return h;
    }

    private int projCountForHash() {
        int c = 0;
        for (Projectile p : proj) if (p.active) c++;
        return c;
    }

    private void simulate(float dt) {
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
                if (mode == MODE_DUO) {
                    // Duo: you come back next to your partner while they survive
                    Snake mate = mateOf(s);
                    if (mate != null && mate.alive && s.deadTime > DUO_RESPAWN) respawnNear(s, mate);
                } else if (mode != MODE_SHOWDOWN && !s.isPlayer && s.deadTime > 3.5f) respawnBot(s);
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
        if (mode == MODE_ENDLESS || mode == MODE_DEMO) {
            if (boxCount >= boxTarget) boxRespawnTimer = 0;
        }
        if ((mode == MODE_ENDLESS || mode == MODE_DEMO) && boxCount < boxTarget) {
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
        if (mode != MODE_SHOWDOWN && mode != MODE_DUO) return;
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
        if (ready && !s.superWasReady && s == player) sound(Platform.SND_SUPER_READY, s.hx(), s.hy(), 0.9f);
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
            // While you wait to respawn in Duo, the camera follows your partner
            Snake mate = mateOf(player);
            focus = !player.alive && player.deadTime > 1.2f && mate != null && mate.alive ? mate : player;
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
                if (!b.alive || j >= b.segs || sameTeam(a, b)) continue;
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
                        hurt(a, a.maxHp * 0.2f / PLAYER_DAMAGE_TAKEN, b, hx, hy, ang, 600, CAUSE_CRASH);
                        a.ang = a.targetAng = ang;
                        fx.burst(hx, hy, 12, 0xffffffff, 300, 7, 0.4f);
                    }
                    continue;
                }
                if (j == 0) {
                    if (b.dashTime > 0) continue;
                    if (b.isPlayer) {
                        // Players bounce off in their own pass; only a smaller attacker can lose here
                        if (a.mass < b.mass * 0.92f) kill(a, b, CAUSE_CRASH);
                        continue;
                    }
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
                    if (s == player) ate = true;
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
                    if (s == player) sound(Platform.SND_POWER, hx, hy, 1f);
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
            case Brawler.THORN:
                return 0xffb8ff8a;
            case Brawler.RUMBLE:
                return 0xffffb07a;
            case Brawler.NOVA:
                return 0xffc8aaff;
            case Brawler.JOKER:
                return 0xffff6ad0;
            case Brawler.REAPER:
                return 0xff8affd8;
            case Brawler.MAGMA:
                return 0xffff8a2a;
            case Brawler.GLITCH:
                return 0xff2affd0;
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
        if (s == player) shake = Math.max(shake, sup ? 4f : 1.5f);
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
            case Brawler.COBRA:
                s.burstLeft = sup ? 2 : 4;
                s.burstInterval = sup ? 0.22f : 0.075f;
                s.burstTimer = 0;
                s.burstAng = ang;
                s.burstSuper = sup;
                sound(sup ? Platform.SND_SUPER : Platform.SND_SHOOT, mx, my, 0.8f);
                break;
            case Brawler.THORN:
                if (sup) {
                    float range = s.type.superRange;
                    float d = MathUtil.clamp(dist, 110, range);
                    Projectile p = spawnProj(Projectile.SEED, s, mx, my, ang, 0, range, 900 * mult, 16, 200, true);
                    if (p != null) {
                        p.startX = mx;
                        p.startY = my;
                        p.targetX = MathUtil.clamp(s.hx() + MathUtil.cos(ang) * d, 0, size);
                        p.targetY = MathUtil.clamp(s.hy() + MathUtil.sin(ang) * d, 0, size);
                        p.flight = 0.7f;
                        p.t = 0;
                        p.aoe = 140;
                    }
                    sound(Platform.SND_SUPER, mx, my, 0.8f);
                } else {
                    Projectile p = spawnProj(Projectile.SPIKE, s, mx, my, ang, 1150, s.type.range, s.type.damage * mult, 11, 120, false);
                    if (p != null) p.split = 6;
                    sound(Platform.SND_THROW, mx, my, 0.7f);
                }
                break;
            case Brawler.RUMBLE:
                if (sup) {
                    quake(s, s.type.superRange, 1500 * mult);
                    sound(Platform.SND_EXPLODE, mx, my, 1f);
                } else {
                    spawnProj(Projectile.WAVE, s, mx, my, ang, 950, s.type.range, s.type.damage * mult, 30, 460, false);
                    sound(Platform.SND_HIT, mx, my, 0.9f);
                }
                break;
            case Brawler.NOVA:
                if (sup) {
                    s.burstLeft = 5;
                    s.burstInterval = 0.16f;
                    s.burstTimer = 0;
                    s.burstAng = ang;
                    s.burstDist = MathUtil.clamp(dist, 150, s.type.superRange);
                    s.burstSuper = true;
                    sound(Platform.SND_SUPER, mx, my, 0.9f);
                } else {
                    spawnProj(Projectile.ORB, s, mx, my, ang, 1050, s.type.range, s.type.damage * mult, 13, 140, false);
                    sound(Platform.SND_BOLT, mx, my, 0.6f);
                }
                break;
            case Brawler.JOKER:
                if (sup) {
                    for (int i = 0; i < 18; i++) {
                        float a = ang + MathUtil.TAU * i / 18f;
                        float sp = 1150 + (i % 3) * 180;
                        Projectile p = spawnProj(Projectile.CARD, s, s.hx() + MathUtil.cos(a) * hr, s.hy() + MathUtil.sin(a) * hr, a, sp,
                                s.type.superRange, 430 * mult, 12, 150, false);
                        if (p != null) p.bounces = 2;
                    }
                    fx.ring(s.hx(), s.hy(), 140, 0xffff6ad0, 0.35f);
                    fx.burst(s.hx(), s.hy(), 24, 0xffffe14a, 500, 8, 0.5f);
                    sound(Platform.SND_SUPER, mx, my, 0.9f);
                } else {
                    for (int i = -1; i <= 1; i++) {
                        Projectile p = spawnProj(Projectile.CARD, s, mx, my, ang + i * 0.14f + MathUtil.rand(-0.05f, 0.05f),
                                MathUtil.rand(1350, 1600), s.type.range, s.type.damage * mult, 11, 100, false);
                        if (p == null) continue;
                        // Every card is a surprise
                        float r = MathUtil.rand();
                        if (r < 0.34f) p.bounces = 1;
                        else if (r < 0.67f) {
                            p.slowFactor = 0.55f;
                            p.slowDur = 1.2f;
                        } else p.damage *= 1.35f;
                    }
                    sound(Platform.SND_THROW, mx, my, 0.7f);
                }
                break;
            case Brawler.REAPER:
                if (sup) {
                    soulHarvest(s, s.type.superRange, 1300 * mult);
                    sound(Platform.SND_SUPER, mx, my, 1f);
                } else {
                    for (int i = -1; i <= 1; i++)
                        spawnProj(Projectile.SLASH, s, mx, my, ang + i * 0.38f, 1150, s.type.range, s.type.damage * mult, 26, 160, false);
                    sound(Platform.SND_SHOTGUN, mx, my, 0.6f);
                }
                break;
            case Brawler.MAGMA:
                if (sup) {
                    s.burstLeft = 9;
                    s.burstInterval = 0.12f;
                    s.burstTimer = 0;
                    s.burstAng = ang;
                    s.burstSuper = true;
                    fx.ring(s.hx(), s.hy(), 160, 0xffff8a2a, 0.4f);
                    fx.fireball(s.hx(), s.hy(), 110, 0.5f);
                    shakeAt(s.hx(), s.hy(), 10);
                    sound(Platform.SND_EXPLODE, mx, my, 0.9f);
                } else {
                    float d = MathUtil.clamp(dist, 140, s.type.range);
                    for (int i = -1; i <= 1; i++) {
                        float a = ang + i * 0.16f;
                        float dd = d * (i == 0 ? 1f : 0.86f);
                        lavaBlob(s, mx, my, s.hx() + MathUtil.cos(a) * dd, s.hy() + MathUtil.sin(a) * dd, s.type.damage * mult * (i == 0 ? 1f : 0.7f), 70, 0.6f + i * i * 0.08f);
                    }
                    sound(Platform.SND_THROW, mx, my, 0.8f);
                }
                break;
            case Brawler.GLITCH:
                if (sup) {
                    systemCrash(s, ang, dist, 1150 * mult);
                    sound(Platform.SND_EXPLODE, mx, my, 1f);
                } else {
                    for (int i = -1; i <= 1; i += 2) {
                        Projectile p = spawnProj(Projectile.PIXEL, s, mx, my, ang + i * 0.045f, 2300, s.type.range, s.type.damage * mult, 9, 120, false);
                        if (p != null) p.throughWalls = true;
                    }
                    sound(Platform.SND_BOLT, mx, my, 0.8f);
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
        } else if (s.type.id == Brawler.COBRA) {
            if (s.burstSuper) {
                float off = s.burstLeft % 2 == 0 ? 0 : MathUtil.TAU / 32f;
                for (int i = 0; i < 16; i++) {
                    float a = s.burstAng + off + MathUtil.TAU * i / 16f;
                    spawnProj(Projectile.BULLET, s, s.hx() + MathUtil.cos(a) * hr, s.hy() + MathUtil.sin(a) * hr, a, 1600,
                            s.type.superRange, 420 * mult, 8, 140, false);
                }
                fx.ring(s.hx(), s.hy(), 90, 0xffffd060, 0.25f);
                sound(Platform.SND_SHOTGUN, s.hx(), s.hy(), 0.7f);
            } else {
                float a = s.burstAng + MathUtil.rand(-0.035f, 0.035f);
                float mx = s.hx() + MathUtil.cos(a) * hr, my = s.hy() + MathUtil.sin(a) * hr;
                spawnProj(Projectile.BULLET, s, mx, my, a, 1700, s.type.range, s.type.damage * mult, 8, 90, false);
                if (isNearCamera(mx, my)) fx.flash(mx, my, 40, 0xffffd060, 0.08f);
                sound(Platform.SND_SHOOT, mx, my, 0.5f);
            }
        } else if (s.type.id == Brawler.NOVA) {
            float a = s.burstAng + MathUtil.rand(-0.22f, 0.22f);
            float d = s.burstDist + MathUtil.rand(-110, 110);
            float tx = MathUtil.clamp(s.hx() + MathUtil.cos(a) * d, 0, size), ty = MathUtil.clamp(s.hy() + MathUtil.sin(a) * d, 0, size);
            Projectile p = spawnProj(Projectile.METEOR, s, s.hx(), s.hy(), a, 0, s.type.superRange, 760 * mult, 15, 260, true);
            if (p != null) {
                // Meteors fall from the sky onto the target area
                p.startX = tx - 160;
                p.startY = ty - 420;
                p.targetX = tx;
                p.targetY = ty;
                p.flight = 0.55f;
                p.t = 0;
                p.aoe = 120;
            }
        } else if (s.type.id == Brawler.MAGMA) {
            // Eruption: lava rocks rain down all around Magma
            float a = MathUtil.rand(0, MathUtil.TAU);
            float d = MathUtil.rand(140, s.type.superRange);
            float tx = MathUtil.clamp(s.hx() + MathUtil.cos(a) * d, 0, size), ty = MathUtil.clamp(s.hy() + MathUtil.sin(a) * d, 0, size);
            Projectile p = lavaBlob(s, s.hx(), s.hy(), tx, ty, 520 * mult, 110, 0.65f);
            if (p != null) p.radius = 17;
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
            p.split = 0;
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
            if (p.kind == Projectile.ORB) homeIn(p, dt);
            p.pushTrail();
            p.px = p.x;
            p.py = p.y;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            float step = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy) * dt;
            p.traveled += step;
            if (p.kind == Projectile.FLAME) p.radius = 12f + 20f * (p.traveled / p.range);
            if ((p.kind == Projectile.BALL || p.kind == Projectile.CARD) && p.traveled < p.range && p.bounces > 0
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
                if (p.split > 0) splitSpike(p, p.px, p.py);
                else if (p.kind != Projectile.FLAME) fx.sparks(p.x, p.y, 3, 0xffffffff, 120, 0.2f);
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
                if (p.split > 0) splitSpike(p, p.px, p.py);
                else fx.sparks(p.px, p.py, 5, 0xffdddddd, 200, 0.25f);
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
            if (s == p.owner || !s.alive || j >= s.segs || sameTeam(s, p.owner)) continue;
            float sxj = s.sx[j], syj = s.sy[j];
            float t = len2 > 0 ? MathUtil.clamp(((sxj - ax) * bx + (syj - ay) * by) / len2, 0, 1) : 0;
            float cx = ax + bx * t, cy = ay + by * t;
            float rr = s.radius + p.radius;
            if (MathUtil.dist2(cx, cy, sxj, syj) < rr * rr) {
                float ang = (float) MathUtil.atan2(p.vy, p.vx);
                int color = p.kind == Projectile.FLAME ? 0xffff8a2a : (p.kind == Projectile.PELLET || p.kind == Projectile.BALL ? 0xffffe066 : 0xff9af0ff);
                fx.sparks(cx, cy, 7, color, 300, 0.25f);
                fx.flash(cx, cy, 48, projColor(p.kind), 0.14f);
                // Head shots crit
                boolean crit = j <= 1;
                float dmg = crit ? p.damage * 1.25f : p.damage;
                if (crit && p.owner != null && p.owner == player && showDamage) fx.text(cx, cy - 78, "CRIT!", 0xffffd23f, 30);
                hurt(s, dmg, p.owner, cx, cy, ang, p.knock, CAUSE_SHOT);
                if (p.slowDur > 0) slow(s, p.slowFactor, p.slowDur);
                p.active = false;
                // Thorn's spike also bursts on a direct hit
                if (p.split > 0) splitSpike(p, cx, cy);
                return;
            }
        }
    }

    private void explode(Projectile p) {
        p.active = false;
        float x = p.x, y = p.y, r = p.aoe;
        if (p.kind == Projectile.SEED) {
            fx.ring(x, y, r, 0xff9aff6a, 0.4f);
            fx.burst(x, y, 24, 0xff7ad85a, r * 3f, 9, 0.5f);
            fx.flash(x, y, r * 1.4f, 0xffd8ff9a, 0.18f);
            addDecal(x, y, r * 0.6f);
            sound(Platform.SND_EXPLODE, x, y, 0.6f);
            splash(p, x, y, r);
            Snake o = p.owner;
            float mult = o != null ? o.damageMult() : 1f;
            for (int i = 0; i < 12; i++) {
                float a = MathUtil.TAU * i / 12f;
                Projectile nd = spawnProj(Projectile.NEEDLE, o, x + MathUtil.cos(a) * 20, y + MathUtil.sin(a) * 20, a, 1100, 330,
                        260 * mult, 7, 70, false);
                if (nd != null) nd.throughWalls = false;
            }
            return;
        }
        if (p.kind == Projectile.METEOR) {
            fx.ring(x, y, r * 1.1f, 0xffc8aaff, 0.4f);
            fx.fireball(x, y, r, 0.45f);
            fx.flash(x, y, r * 1.5f, 0xffd8c8ff, 0.18f);
            fx.burst(x, y, 18, 0xff9a7aff, r * 3f, 9, 0.5f);
            fx.burst(x, y, 8, 0xffffe066, r * 2f, 7, 0.4f);
            addDecal(x, y, r * 0.8f);
            sound(Platform.SND_EXPLODE, x, y, 0.6f);
            shakeAt(x, y, 7);
            splash(p, x, y, r);
            return;
        }
        if (p.kind == Projectile.LAVA) {
            float mult = p.owner != null ? p.owner.damageMult() : 1f;
            addArea(x, y, r * 0.75f, 2.2f, 300 * mult, p.owner, true);
            fx.fireball(x, y, r * 0.9f, 0.4f);
            fx.ring(x, y, r, 0xffffb03a, 0.35f);
            fx.burst(x, y, 16, 0xffff6a1a, r * 3f, 8, 0.5f);
            fx.smoke(x, y, 3, 0xff4a2a20, r * 0.4f, 0.7f);
            addDecal(x, y, r * 0.7f);
            sound(Platform.SND_EXPLODE, x, y, 0.4f);
            shakeAt(x, y, 4);
            splash(p, x, y, r);
            return;
        }
        if (p.kind == Projectile.GLOB || p.kind == Projectile.MEGAGLOB) {
            boolean big = p.kind == Projectile.MEGAGLOB;
            addArea(x, y, r, big ? 5f : 3f, (big ? 850 : 650) * (p.owner != null ? p.owner.damageMult() : 1f), p.owner, false);
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
            if (o == s || !o.alive || sameTeam(o, s)) continue;
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

    /** Thorn's spike bursts into needles where it stops. */
    private void splitSpike(Projectile p, float x, float y) {
        float base = (float) MathUtil.atan2(p.vy, p.vx);
        fx.burst(x, y, 10, 0xff9aff6a, 260, 6, 0.3f);
        for (int i = 0; i < p.split; i++) {
            float a = base + MathUtil.TAU * i / p.split;
            spawnProj(Projectile.NEEDLE, p.owner, x, y, a, 1100, 240, p.damage * 0.42f, 7, 60, false);
        }
    }

    /** Nova's orbs curve toward the nearest enemy head in front of them. */
    private void homeIn(Projectile p, float dt) {
        Snake best = null;
        float bestD = 480 * 480;
        for (int i = 0; i < snakeCount; i++) {
            Snake s = snakes[i];
            if (!s.alive || s == p.owner || sameTeam(s, p.owner) || s.spawnShield > 0) continue;
            if (isHiddenFromViewer(s, p.owner, s.hx(), s.hy())) continue;
            float d = MathUtil.dist2(p.x, p.y, s.hx(), s.hy());
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        if (best == null) return;
        float speed = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy);
        float cur = (float) MathUtil.atan2(p.vy, p.vx);
        float want = MathUtil.angleTo(p.x, p.y, best.hx(), best.hy());
        float diff = MathUtil.wrap(want - cur);
        float turn = 3.2f * dt;
        cur += MathUtil.clamp(diff, -turn, turn);
        p.vx = MathUtil.cos(cur) * speed;
        p.vy = MathUtil.sin(cur) * speed;
    }

    /** Rumble's Earthquake: heavy damage, knockback and a long slow around him. */
    private void quake(Snake s, float radius, float dmg) {
        float x = s.hx(), y = s.hy();
        fx.ring(x, y, radius, 0xffffb07a, 0.5f);
        fx.ring(x, y, radius * 0.65f, 0xffc8763e, 0.4f);
        fx.burst(x, y, 36, 0xff8a5a3a, radius * 3f, 11, 0.6f);
        fx.smoke(x, y, 5, 0xffb89a78, radius * 0.25f, 0.6f);
        addDecal(x, y, radius * 0.3f);
        shakeAt(x, y, 18);
        for (int i = 0; i < snakeCount; i++) {
            Snake o = snakes[i];
            if (o == s || !o.alive || sameTeam(o, s)) continue;
            if (x < o.minX - radius || x > o.maxX + radius || y < o.minY - radius || y > o.maxY + radius) continue;
            for (int j = 0; j < o.segs; j += 2) {
                float rr = radius + o.radius;
                if (MathUtil.dist2(x, y, o.sx[j], o.sy[j]) < rr * rr) {
                    hurt(o, dmg, s, o.sx[j], o.sy[j], MathUtil.angleTo(x, y, o.sx[j], o.sy[j]), 520, CAUSE_SHOT);
                    slow(o, 0.5f, 2f);
                    break;
                }
            }
        }
    }

    /** Magma's lava blob: lobbed to (tx, ty), bursts into burning ground. */
    private Projectile lavaBlob(Snake s, float sx, float sy, float tx, float ty, float dmg, float aoe, float flight) {
        Projectile p = spawnProj(Projectile.LAVA, s, sx, sy, 0, 0, s.type.superRange, dmg, 12, 140, true);
        if (p != null) {
            p.startX = sx;
            p.startY = sy;
            p.targetX = MathUtil.clamp(tx, 0, size);
            p.targetY = MathUtil.clamp(ty, 0, size);
            p.flight = flight;
            p.t = 0;
            p.aoe = aoe;
        }
        return p;
    }

    /** Reaper's Soul Harvest: pulls nearby snakes in and drains their life. */
    private void soulHarvest(Snake s, float radius, float dmg) {
        float x = s.hx(), y = s.hy();
        fx.ring(x, y, radius, 0xff8affd8, 0.5f);
        fx.ring(x, y, radius * 0.55f, 0xff3a3a52, 0.45f);
        fx.burst(x, y, 36, 0xff8affd8, radius * 2.2f, 9, 0.6f);
        fx.smoke(x, y, 6, 0xff1c1c2a, radius * 0.3f, 0.7f);
        shakeAt(x, y, 12);
        for (int i = 0; i < snakeCount; i++) {
            Snake o = snakes[i];
            if (o == s || !o.alive || sameTeam(o, s)) continue;
            if (x < o.minX - radius || x > o.maxX + radius || y < o.minY - radius || y > o.maxY + radius) continue;
            for (int j = 0; j < o.segs; j += 2) {
                float rr = radius + o.radius;
                if (MathUtil.dist2(x, y, o.sx[j], o.sy[j]) < rr * rr) {
                    // Negative knockback pulls the victim towards the Reaper
                    hurt(o, dmg, s, o.sx[j], o.sy[j], MathUtil.angleTo(x, y, o.sx[j], o.sy[j]), -380, CAUSE_SHOT);
                    slow(o, 0.6f, 1.2f);
                    fx.add(Particles.DOT, o.sx[j], o.sy[j], (x - o.sx[j]) * 1.5f, (y - o.sy[j]) * 1.5f, 9, 0xff8affd8, 0.6f);
                    break;
                }
            }
        }
    }

    /** A blast at (x, y) that damages, knocks back and slows (Glitch's System Crash). */
    private void blastAt(Snake s, float x, float y, float radius, float dmg) {
        fx.ring(x, y, radius, 0xff2affd0, 0.45f);
        fx.ring(x, y, radius * 0.7f, 0xffff2aa8, 0.4f);
        fx.flash(x, y, radius * 1.4f, 0xffffffff, 0.15f);
        fx.burst(x, y, 30, 0xff2affd0, radius * 3f, 9, 0.5f);
        fx.burst(x, y, 20, 0xffff2aa8, radius * 2.5f, 8, 0.5f);
        shakeAt(x, y, 12);
        for (int i = 0; i < snakeCount; i++) {
            Snake o = snakes[i];
            if (o == s || !o.alive || sameTeam(o, s)) continue;
            if (x < o.minX - radius || x > o.maxX + radius || y < o.minY - radius || y > o.maxY + radius) continue;
            for (int j = 0; j < o.segs; j += 2) {
                float rr = radius + o.radius;
                if (MathUtil.dist2(x, y, o.sx[j], o.sy[j]) < rr * rr) {
                    hurt(o, dmg, s, o.sx[j], o.sy[j], MathUtil.angleTo(x, y, o.sx[j], o.sy[j]), 420, CAUSE_SHOT);
                    slow(o, 0.5f, 1.5f);
                    break;
                }
            }
        }
    }

    /** True if (x, y) is within r of another living snake's body. */
    private boolean nearBody(Snake self, float x, float y, float r) {
        for (int i = 0; i < snakeCount; i++) {
            Snake o = snakes[i];
            if (o == self || !o.alive) continue;
            if (x < o.minX - r || x > o.maxX + r || y < o.minY - r || y > o.maxY + r) continue;
            for (int j = 0; j < o.segs; j++) {
                float rr = r + o.radius;
                if (MathUtil.dist2(x, y, o.sx[j], o.sy[j]) < rr * rr) return true;
            }
        }
        return false;
    }

    /** Glitch's System Crash: blows up where it stands, teleports, and blows up again. */
    private void systemCrash(Snake s, float ang, float dist, float dmg) {
        float ox = s.hx(), oy = s.hy();
        blastAt(s, ox, oy, 170, dmg * 0.7f);
        // Land a little short of the aim point, never inside a wall or another snake's body
        float d = MathUtil.clamp(dist - 120, 180, s.type.superRange);
        float tx = ox, ty = oy;
        for (float k = d; k > 0; k -= 20) {
            float x = ox + MathUtil.cos(ang) * k, y = oy + MathUtil.sin(ang) * k;
            if (!solidCircle(x, y, s.radius) && !nearBody(s, x, y, s.radius * 3f)) {
                tx = x;
                ty = y;
                break;
            }
        }
        s.sx[0] = tx;
        s.sy[0] = ty;
        s.ang = s.targetAng = ang;
        s.spawnShield = Math.max(s.spawnShield, 0.4f);
        blastAt(s, tx, ty, 210, dmg);
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
    /** Magma's burning ground instead of Toxin's poison. */
    private final boolean[] areaLava = new boolean[MAX_AREAS];
    private int areaCount;

    private void addArea(float x, float y, float r, float life, float dps, Snake owner, boolean lava) {
        if (areaCount >= MAX_AREAS) return;
        int i = areaCount++;
        areaLava[i] = lava;
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
                areaLava[i] = areaLava[last];
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
            if (areaLava[i]) {
                // Glowing lava pool with bubbles
                if (!lowGraphics) g.radial(x, y, rad * 1.25f, MathUtil.withAlpha(0xffff6a1a, 0.5f * f), 0x00ff3a0a);
                g.color(MathUtil.withAlpha(0xff5a1a0a, 0.7f * f));
                g.fillCircle(x, y, rad * 0.9f);
                g.color(MathUtil.withAlpha(0xffff5a1a, 0.75f * f));
                g.fillCircle(x, y, rad * 0.74f);
                g.color(MathUtil.withAlpha(0xffffc23f, 0.8f * f));
                for (int k = 0; k < 6; k++) {
                    float a = k * 1.1f + i * 0.7f;
                    float ph = (tt * 1.2f + k * 0.17f) % 1f;
                    g.fillCircle(x + MathUtil.cos(a) * rad * 0.45f, y + MathUtil.sin(a) * rad * 0.45f, rad * 0.13f * (1f - ph));
                }
                continue;
            }
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
        if (!v.alive || sameTeam(v, by)) return;
        if (v.spawnShield > 0 && cause != CAUSE_POISON) {
            fx.ring(v.hx(), v.hy(), v.radius * 2.2f, 0xaaffffff, 0.25f);
            return;
        }
        // Easier fights: the player shrugs off a good part of every hit
        if (v.isPlayer) dmg *= PLAYER_DAMAGE_TAKEN;
        v.hp -= dmg;
        // Reaper heals from every hit it lands
        if (by != null && by != v && by.alive && by.type.id == Brawler.REAPER && cause != CAUSE_POISON) {
            float heal = dmg * 0.45f;
            by.hp = Math.min(by.maxHp, by.hp + heal);
            if (heal > 40 && (by == player || isNearCamera(by.hx(), by.hy())) && MathUtil.frand() < 0.5f)
                fx.text(by.hx(), by.hy() - by.radius - 70, "+" + (int) heal, 0xff8affd8, 26);
        }
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
            boolean mine = by != null && by == player, me = v == player;
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
        int aliveBefore = mode == MODE_DUO ? aliveTeams() : 0;
        if (mode != MODE_DUO) for (int i = 0; i < snakeCount; i++) if (snakes[i].alive) aliveBefore++;
        v.alive = false;
        v.deadTime = 0;
        v.hp = 0;
        v.rank = aliveBefore;
        if (mode == MODE_DUO) {
            Snake mate = mateOf(v);
            if (mate != null && !mate.alive) mate.rank = aliveBefore; // whole team is out
        }
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
            addOrb(x, y, value, v.palette[k % Skin.colorCount(v.palette)], -1);
        }
        for (int c = 0; c < v.cubes; c++) {
            int j = MathUtil.randInt(Math.max(1, Math.min(v.segs, 12)));
            dropCube(v.sx[j] + MathUtil.rand(-40, 40), v.sy[j] + MathUtil.rand(-40, 40));
        }
        for (int j = 0; j < v.segs; j += 4) fx.burst(v.sx[j], v.sy[j], 2, v.color1, 220, 10, 0.6f);
        fx.burst(v.hx(), v.hy(), 26, 0xffffffff, 420, 8, 0.6f);
        fx.ring(v.hx(), v.hy(), 140, v.color1, 0.5f);
        fx.ring(v.hx(), v.hy(), 220, 0xffffffff, 0.35f);
        fx.flash(v.hx(), v.hy(), 240, MathUtil.lighter(v.color1, 0.3f), 0.25f);
        fx.burst(v.hx(), v.hy(), 14, 0xffffd23f, 520, 6, 0.5f);
        fx.smoke(v.hx(), v.hy(), 8, 0xff404040, 40, 1f);
        shakeAt(v.hx(), v.hy(), 10);

        if (by != null && by != v) {
            by.kills++;
            if (by.alive) by.hp = Math.min(by.maxHp, by.hp + by.maxHp * 0.25f);
        }
        addFeed(by, v, cause);

        // The knockout freeze is part of the simulation, so it must not depend on which phone this is
        if (!v.isPlayer && by != null && by.isPlayer) hitStop = 0.07f;
        if (v == player) {
            playerDied = true;
            sound(Platform.SND_DEATH, v.hx(), v.hy(), 1f);
            platform.vibrate(180);
        } else if (by != null && by == player) {
            sound(Platform.SND_KILL, v.hx(), v.hy(), 1f);
            platform.vibrate(40);
            fx.text(by.hx(), by.hy() - by.radius - 70, "KNOCKOUT!", 0xffffd23f, 44);
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
        if (sameTeam(target, viewer)) return true;
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
            if (o == s || !o.alive || sameTeam(o, s) || !visibleTo(o, s)) continue;
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
            float target = base * zoomMult * (float) MathUtil.pow(19f / f.radius, 0.5f);
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
        if (fancy) g.vertical(0, 0, w, h, theme.seaTop, theme.seaBottom);
        else {
            g.color(theme.seaFlat);
            g.fillRect(0, 0, w, h);
        }
        g.save();
        g.translate(w / 2 + sx, h / 2 + sy);
        g.scale(zoom);
        g.translate(-camX, -camY);

        drawGround(g, l, t, r, b, tt);
        if (fancy) drawLightPatches(g, l, t, r, b, tt);
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
        if (fancy) drawAmbient(g, l, t, r, b, tt);
        drawLockOn(g, tt);
        drawLabels(g, l, t, r, b);
        drawAim(g);
        drawPoison(g, l, t, r, b, tt);
        fx.drawText(g);
        g.restore();
        Snake.shadows = false;

        if (fancy) {
            // Warm sunlight from the top, cooler shade at the bottom
            g.vertical(0, 0, w, h * 0.5f, theme.tintTop, theme.tintTop & 0x00ffffff);
            g.vertical(0, h * 0.55f, w, h, theme.tintBottom & 0x00ffffff, theme.tintBottom);
            // Soft vignette pulls the eye to the centre
            float rad = (float) Math.sqrt(w * w + h * h) * 0.62f;
            g.radial(w / 2, h / 2, rad, 0x00000000, 0x70000018);
        }
    }

    /** Big soft sun-dapples and shadows that break up the tiled ground. */
    private void drawLightPatches(Gfx g, float l, float t, float r, float b, float tt) {
        float cell = T * 6;
        int x0 = (int) Math.floor((l - cell) / cell), x1 = (int) Math.floor((r + cell) / cell);
        int y0 = (int) Math.floor((t - cell) / cell), y1 = (int) Math.floor((b + cell) / cell);
        for (int cy = y0; cy <= y1; cy++) {
            for (int cx = x0; cx <= x1; cx++) {
                int h = hash(cx * 31 + 7, cy * 17 + 3);
                float x = (cx + 0.5f) * cell + ((h & 63) - 32) * 3f, y = (cy + 0.5f) * cell + (((h >>> 6) & 63) - 32) * 3f;
                if (x < -100 || y < -100 || x > size + 100 || y > size + 100) continue;
                float rad = cell * (0.45f + ((h >>> 12) & 15) / 40f);
                if ((h & 0x10000) != 0) g.radial(x, y, rad, theme.sunPatch, theme.sunPatch & 0x00ffffff);
                else g.radial(x, y, rad, theme.shadePatch, theme.shadePatch & 0x00ffffff);
            }
        }
        // Sparkles on the water around the island
        for (int k = 0; k < 26; k++) {
            int h = hash(k * 13 + 1, (int) (tt * 0.5f) + k * 7);
            float x = l + ((h & 1023) / 1023f) * (r - l), y = t + (((h >>> 10) & 1023) / 1023f) * (b - t);
            if (x > -40 && y > -40 && x < size + 40 && y < size + 40) continue;
            float tw = MathUtil.sin((tt * 0.5f % 1f) * MathUtil.PI);
            float s = 10f * tw;
            g.color(MathUtil.withAlpha(0xffffffff, 0.8f * tw));
            g.line(x - s, y, x + s, y, 2.5f);
            g.line(x, y - s, x, y + s, 2.5f);
        }
    }

    /** Floating pollen and drifting leaves for atmosphere. */
    private void drawAmbient(Gfx g, float l, float t, float r, float b, float tt) {
        float wdt = r - l, hgt = b - t;
        for (int k = 0; k < 34; k++) {
            float sp = 14f + (k % 5) * 6f;
            float fx0 = ((k * 0.6180339f) % 1f) * wdt * 1.4f + tt * sp;
            float fy0 = ((k * 0.3819660f) % 1f) * hgt * 1.4f + tt * sp * 0.35f;
            float x = l + (((fx0 + camX * 0.15f) % (wdt * 1.2f)) + wdt * 1.2f) % (wdt * 1.2f) - wdt * 0.1f;
            float y = t + (((fy0 + camY * 0.15f) % (hgt * 1.2f)) + hgt * 1.2f) % (hgt * 1.2f) - hgt * 0.1f
                    + MathUtil.sin(tt * 1.3f + k) * 18f;
            if (theme.ambientStyle == 1) {
                // Snowflake drifting down
                float sy = t + (((fy0 * 2.2f + camY * 0.15f) % (hgt * 1.2f)) + hgt * 1.2f) % (hgt * 1.2f) - hgt * 0.1f;
                float sz = 4f + (k % 4) * 1.6f;
                g.color(MathUtil.withAlpha(theme.ambient, 0.75f));
                g.fillCircle(x, sy, sz);
                if (k % 3 == 0) {
                    g.line(x - sz * 1.8f, sy, x + sz * 1.8f, sy, 1.5f);
                    g.line(x, sy - sz * 1.8f, x, sy + sz * 1.8f, 1.5f);
                }
            } else if (theme.ambientStyle == 2) {
                // Embers rising from the lava
                float ey = b - ((((fy0 * 1.8f - camY * 0.15f) % (hgt * 1.2f)) + hgt * 1.2f) % (hgt * 1.2f)) + hgt * 0.1f;
                float tw = 0.5f + 0.5f * MathUtil.sin(tt * 5f + k * 2.1f);
                g.radial(x, ey, 7f + 5f * tw, MathUtil.withAlpha(theme.ambient, 0.55f + 0.35f * tw), theme.ambient & 0x00ffffff);
                g.color(MathUtil.withAlpha(0xffffe08a, 0.8f * tw));
                g.fillCircle(x, ey, 2.2f);
            } else if (k % 6 == 0) {
                // Leaf
                float a = tt * (1.5f + k % 3) + k;
                g.color(theme.leaf);
                g.save();
                g.translate(x, y);
                g.rotate((float) Math.toDegrees(a));
                g.fillRoundRect(-9, -4, 9, 4, 4);
                g.color(theme.leafVein);
                g.line(-8, 0, 8, 0, 1.5f);
                g.restore();
            } else {
                float tw = 0.5f + 0.5f * MathUtil.sin(tt * 3f + k * 1.7f);
                g.radial(x, y, 9f + 4f * tw, MathUtil.withAlpha(theme.ambient, 0.5f * tw + 0.2f), theme.ambient & 0x00ffffff);
            }
        }
    }

    /** Smooth 0..1 noise over tiles (value noise on a 6-tile grid) for meadow patches. */
    private static float meadow(int tx, int ty) {
        int gx = tx / 6, gy = ty / 6; // tile indices are never negative
        float fx = (tx - gx * 6) / 6f, fy = (ty - gy * 6) / 6f;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        float a = (hash(gx, gy) & 1023) / 1023f, b = (hash(gx + 1, gy) & 1023) / 1023f;
        float c = (hash(gx, gy + 1) & 1023) / 1023f, d = (hash(gx + 1, gy + 1) & 1023) / 1023f;
        return MathUtil.lerp(MathUtil.lerp(a, b, fx), MathUtil.lerp(c, d, fx), fy);
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
            g.color(theme.wave);
            g.line(wl, y + off, wr, y + off + 6, 5);
        }
        if (fancy) {
            // Foam and wet sand along the shore
            float foam = 26f + 6f * MathUtil.sin(tt * 2f);
            g.color(theme.foam);
            g.fillRoundRect(-foam - 18, -foam - 18, size + foam + 18, size + foam + 18, 60);
            g.color(theme.rimWet);
            g.fillRoundRect(-30, -30, size + 30, size + 30, 40);
        }
        g.color(theme.rimDry);
        g.fillRect(-18, -18, size + 18, size + 18);
        g.color(theme.ground);
        g.fillRect(0, 0, size, size);
        int tx0 = Math.max(0, (int) (l / T)), tx1 = Math.min(n - 1, (int) (r / T));
        int ty0 = Math.max(0, (int) (t / T)), ty1 = Math.min(n - 1, (int) (b / T));
        // Checker tiles with a little colour variation so the ground feels natural
        for (int ty = ty0; ty <= ty1; ty++) {
            for (int tx = tx0; tx <= tx1; tx++) {
                int hsh = hash(tx, ty);
                boolean dark = ((tx + ty) & 1) == 0;
                if (!dark && (!fancy || (hsh & 7) != 0)) continue;
                int c = dark ? ((hsh & 3) == 0 ? theme.checkA : theme.checkB) : theme.checkLight;
                g.color(c);
                g.fillRect(tx * T, ty * T, tx * T + T, ty * T + T);
            }
        }
        if (fancy) {
            // Soft meadow patches: low-frequency noise decides where grass grows over the sand
            for (int ty = ty0; ty <= ty1; ty++) {
                for (int tx = tx0; tx <= tx1; tx++) {
                    float m = meadow(tx, ty);
                    if (m < 0.52f) continue;
                    float a = Math.min(1f, (m - 0.52f) * 3.2f);
                    // Tile-sized tint fading in with the noise, so patches blend into the checker
                    g.color(MathUtil.withAlpha(theme.meadow, 0.38f * a));
                    g.fillRect(tx * T, ty * T, tx * T + T, ty * T + T);
                }
            }
            // Sand grain
            for (int ty = ty0; ty <= ty1; ty++) {
                for (int tx = tx0; tx <= tx1; tx++) {
                    int hsh = hash(tx * 13 + 1, ty * 17 + 7);
                    g.color(theme.grain);
                    for (int k = 0; k < 4; k++) {
                        hsh = hsh * 1103515245 + 12345;
                        g.fillCircle(tx * T + ((hsh >>> 8) & 63), ty * T + ((hsh >>> 16) & 63), 1.6f + ((hsh >>> 24) & 1));
                    }
                }
            }
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
                        g.color(theme.tuft);
                        g.line(x, y, x - 6 + sway, y - 12, 3);
                        g.line(x, y, x + sway, y - 15, 3);
                        g.line(x, y, x + 6 + sway, y - 11, 3);
                    } else if (kind <= 4) {
                        g.color(0x33000000);
                        g.fillCircle(x + 2, y + 3, 6);
                        g.color(theme.pebble);
                        g.fillCircle(x, y, 6);
                        g.color(theme.pebbleHi);
                        g.fillCircle(x - 2, y - 2, 2.5f);
                    } else {
                        int fc = (hsh & 1) == 0 ? theme.flowerA : theme.flowerB;
                        g.color(theme.tuft);
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
        g.color(theme.fenceDark);
        g.strokeRoundRect(-9, -7, size + 9, size + 11, 12, 16);
        g.color(theme.fence);
        g.strokeRoundRect(-9, -9, size + 9, size + 9, 12, 12);
        if (fancy) {
            g.color(theme.fenceHi);
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
                        if (fancy) g.vertical(x, y + T - lift, x + T, y + T, theme.wallFrontA, theme.wallFrontB);
                        else {
                            g.color(theme.wallFrontFlat);
                            g.fillRect(x, y + T - lift, x + T, y + T);
                        }
                        g.color(0x55262c40);
                        g.line(x + T / 2, y + T - lift + 3, x + T / 2, y + T - 2, 2);
                        if (fancy) {
                            // Brick courses on the front face
                            g.line(x + 2, y + T - lift * 0.5f, x + T - 2, y + T - lift * 0.5f, 2);
                            g.color(0x22ffffff);
                            g.line(x + 3, y + T - lift + 2, x + T - 3, y + T - lift + 2, 2);
                        }
                    }
                    // Top face
                    g.color(theme.wallTopEdge);
                    g.fillRoundRect(x - 1, y - lift - 1, x + T + 1, y + T - lift + 1, 7);
                    if (fancy) g.vertical(x, y - lift, x + T, y + T - lift, theme.wallTopA, theme.wallTopB);
                    else {
                        g.color(theme.wallTopFlat);
                        g.fillRect(x, y - lift, x + T, y + T - lift);
                    }
                    g.color(theme.wallHi);
                    g.fillRoundRect(x + 5, y - lift + 4, x + T - 5, y - lift + 10, 3);
                    if (fancy) {
                        int hsh = hash(tx, ty);
                        // Stone slabs: mortar joints, offset every other row, with bevelled edges
                        float top = y - lift, mid = top + T * 0.5f;
                        float jx = (ty & 1) == 0 ? x + T * 0.5f : x + T * 0.3f;
                        float jx2 = (ty & 1) == 0 ? x + T * 0.3f : x + T * 0.62f;
                        g.color(theme.mortar);
                        g.line(x + 3, mid, x + T - 3, mid, 2.5f);
                        g.line(jx, top + 12, jx, mid - 1, 2.5f);
                        g.line(jx2, mid + 1, jx2, top + T - 3, 2.5f);
                        g.color(0x33ffffff);
                        g.line(x + 4, mid + 3, x + T - 4, mid + 3, 1.5f);
                        g.line(jx + 3, top + 13, jx + 3, mid - 2, 1.5f);
                        if ((hsh & 7) == 0) {
                            g.color(theme.mortar);
                            float cx = x + 14 + (hsh >>> 4 & 15) * 2, cy = top + 20 + ((hsh >>> 8) & 7);
                            g.line(cx, cy, cx + 8, cy + 6, 2);
                            g.line(cx + 8, cy + 6, cx + 5, cy + 13, 2);
                        }
                        if (((hsh >>> 12) & 3) == 0) {
                            // Moss creeping over the edge
                            float mx = x + 8 + ((hsh >>> 16) & 31), my = top + 6;
                            g.color(theme.moss);
                            g.fillCircle(mx, my, 7);
                            g.fillCircle(mx + 8, my + 2, 5);
                            g.fillCircle(mx - 6, my + 3, 4);
                            g.color(theme.mossHi);
                            g.fillCircle(mx - 1, my - 1, 3.5f);
                        }
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
        if (s == viewer || s.revealTime > 0 || sameTeam(s, viewer)) return false;
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
            case Projectile.BULLET:
                return 0xffffd060;
            case Projectile.SPIKE:
            case Projectile.NEEDLE:
            case Projectile.SEED:
                return 0xff9aff6a;
            case Projectile.WAVE:
                return 0xffffb07a;
            case Projectile.ORB:
            case Projectile.METEOR:
                return 0xffb89aff;
            case Projectile.CARD:
                return 0xffff6ad0;
            case Projectile.SLASH:
                return 0xff8affd8;
            case Projectile.LAVA:
                return 0xffff7a1a;
            case Projectile.PIXEL:
                return 0xff2affd0;
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
                        float a = (float) MathUtil.atan2(p.vy, p.vx) + MathUtil.PI / 2;
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
                    float a = (float) MathUtil.atan2(p.vy, p.vx);
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
                case Projectile.BULLET: {
                    g.color(0x88ffb030);
                    g.line(p.x, p.y, p.x - p.vx * 0.03f, p.y - p.vy * 0.03f, p.radius * 1.3f);
                    g.color(0xffffe9a0);
                    g.line(p.x, p.y, p.x - p.vx * 0.018f, p.y - p.vy * 0.018f, p.radius * 0.7f);
                    g.color(0xff8a5a24);
                    g.fillCircle(p.x, p.y, p.radius * 0.75f);
                    g.color(0xffffd060);
                    g.fillCircle(p.x, p.y, p.radius * 0.55f);
                    break;
                }
                case Projectile.SPIKE:
                case Projectile.NEEDLE: {
                    boolean big = p.kind == Projectile.SPIKE;
                    float a = (float) MathUtil.atan2(p.vy, p.vx);
                    float ca = MathUtil.cos(a), sa = MathUtil.sin(a);
                    float len = p.radius * (big ? 2.4f : 2.2f), wd = p.radius * (big ? 0.75f : 0.45f);
                    poly4[0] = p.x + ca * len;
                    poly4[1] = p.y + sa * len;
                    poly4[2] = p.x - ca * len * 0.6f - sa * wd;
                    poly4[3] = p.y - sa * len * 0.6f + ca * wd;
                    poly4[4] = p.x - ca * len * 0.6f + sa * wd;
                    poly4[5] = p.y - sa * len * 0.6f - ca * wd;
                    g.color(0xff1e5a24);
                    g.fillPoly(poly4, 3);
                    g.color(big ? 0xff7ad85a : 0xffc8ff9a);
                    g.fillCircle(p.x - ca * len * 0.15f, p.y - sa * len * 0.15f, wd * 0.7f);
                    if (big) {
                        g.color(0xffff7ab0);
                        g.fillCircle(p.x - ca * len * 0.45f, p.y - sa * len * 0.45f, wd * 0.5f);
                    }
                    break;
                }
                case Projectile.SEED: {
                    float f = Math.min(1f, p.t / p.flight);
                    float hgt = MathUtil.sin(f * MathUtil.PI) * 150;
                    g.color(0x40000000);
                    g.fillCircle(p.x, p.y, p.radius * 0.8f);
                    g.color(0x889aff6a);
                    g.strokeCircle(p.targetX, p.targetY, p.aoe * (0.6f + 0.4f * f), 4);
                    float by = p.y - hgt;
                    if (fancy) g.radial(p.x, by, p.radius * 2.4f, 0x889aff6a, 0x009aff6a);
                    g.color(0xff1e5a24);
                    g.fillCircle(p.x, by, p.radius + 3);
                    g.color(0xff6ac24a);
                    g.fillCircle(p.x, by, p.radius);
                    float spin = tt * 9f;
                    g.color(0xffe8ffd0);
                    for (int k = 0; k < 6; k++) {
                        float a = spin + k * MathUtil.TAU / 6f;
                        g.fillCircle(p.x + MathUtil.cos(a) * p.radius * 0.62f, by + MathUtil.sin(a) * p.radius * 0.62f, p.radius * 0.14f);
                    }
                    g.color(0xffff7ab0);
                    g.fillCircle(p.x, by, p.radius * 0.32f);
                    break;
                }
                case Projectile.WAVE: {
                    float a = (float) MathUtil.atan2(p.vy, p.vx);
                    float f = p.traveled / p.range;
                    float deg = (float) Math.toDegrees(a);
                    float rr = p.radius * (1.1f + f * 0.6f);
                    g.color(MathUtil.withAlpha(0xffffd8a8, 0.85f * (1f - f * 0.6f)));
                    g.arc(p.x - MathUtil.cos(a) * rr * 0.5f, p.y - MathUtil.sin(a) * rr * 0.5f, rr, deg - 70, 140, p.radius * 0.5f);
                    g.color(MathUtil.withAlpha(0xffc8763e, 0.9f * (1f - f * 0.5f)));
                    g.arc(p.x - MathUtil.cos(a) * rr * 0.5f, p.y - MathUtil.sin(a) * rr * 0.5f, rr * 0.75f, deg - 60, 120, p.radius * 0.35f);
                    g.color(0xffff3a3a);
                    g.fillCircle(p.x, p.y, p.radius * 0.35f);
                    g.color(0xffffd8a8);
                    g.fillCircle(p.x - p.radius * 0.1f, p.y - p.radius * 0.12f, p.radius * 0.15f);
                    break;
                }
                case Projectile.ORB: {
                    float pulse = 0.85f + 0.15f * MathUtil.sin(tt * 18f);
                    if (fancy) g.radial(p.x, p.y, p.radius * 2.6f * pulse, 0xccb89aff, 0x00b89aff);
                    g.color(0xff35208a);
                    g.fillCircle(p.x, p.y, p.radius + 2.5f);
                    g.color(0xff9a7aff);
                    g.fillCircle(p.x, p.y, p.radius);
                    Icons.star(g, p.x, p.y, p.radius * 1.3f, tt * 4f, 0xffffe066);
                    break;
                }
                case Projectile.METEOR: {
                    float f = Math.min(1f, p.t / p.flight);
                    g.color(MathUtil.withAlpha(0xffb89aff, 0.25f + 0.35f * f));
                    g.fillCircle(p.targetX, p.targetY, p.aoe * f);
                    g.color(0xaab89aff);
                    g.strokeCircle(p.targetX, p.targetY, p.aoe, 4);
                    float ang = MathUtil.angleTo(p.startX, p.startY, p.targetX, p.targetY);
                    float ca = MathUtil.cos(ang), sa = MathUtil.sin(ang);
                    if (fancy) {
                        g.radial(p.x - ca * 40, p.y - sa * 40, p.radius * 3.2f, 0x99ff9a4a, 0x00ff6a2a);
                        g.radial(p.x, p.y, p.radius * 2.4f, 0xccd8c8ff, 0x009a7aff);
                    }
                    g.color(0x99ffb04a);
                    g.line(p.x, p.y, p.x - ca * 120, p.y - sa * 120, p.radius * 1.2f);
                    g.color(0xff2a1a4a);
                    g.fillCircle(p.x, p.y, p.radius + 3);
                    g.color(0xff6a5aa0);
                    g.fillCircle(p.x, p.y, p.radius);
                    g.color(0xffffd060);
                    g.fillCircle(p.x - ca * p.radius * 0.3f, p.y - sa * p.radius * 0.3f, p.radius * 0.45f);
                    break;
                }
                case Projectile.CARD: {
                    // Spinning playing card with a suit
                    float spin = tt * 14f + p.x * 0.01f;
                    float w = p.radius * 1.15f, h = p.radius * 1.6f;
                    g.save();
                    g.translate(p.x, p.y);
                    g.rotate((float) Math.toDegrees(spin));
                    g.color(0xff2a1040);
                    g.fillRoundRect(-w - 2, -h - 2, w + 2, h + 2, 5);
                    int face = p.bounces > 0 ? 0xffffe14a : p.slowDur > 0 ? 0xffbff0ff : 0xffffffff;
                    g.color(face);
                    g.fillRoundRect(-w, -h, w, h, 4);
                    g.color(p.slowDur > 0 ? 0xff2a7ad8 : 0xffe8204a);
                    g.fillCircle(-w * 0.28f, -h * 0.18f, w * 0.34f);
                    g.fillCircle(w * 0.28f, -h * 0.18f, w * 0.34f);
                    poly4[0] = -w * 0.62f;
                    poly4[1] = -h * 0.05f;
                    poly4[2] = w * 0.62f;
                    poly4[3] = -h * 0.05f;
                    poly4[4] = 0;
                    poly4[5] = h * 0.55f;
                    g.fillPoly(poly4, 3);
                    g.restore();
                    break;
                }
                case Projectile.SLASH: {
                    float a = (float) MathUtil.atan2(p.vy, p.vx);
                    float f = p.traveled / p.range;
                    float deg = (float) Math.toDegrees(a);
                    float rr = p.radius * (1.2f + f * 0.4f);
                    float cx = p.x - MathUtil.cos(a) * rr * 0.6f, cy = p.y - MathUtil.sin(a) * rr * 0.6f;
                    g.color(MathUtil.withAlpha(0xff12121e, 0.8f * (1f - f * 0.5f)));
                    g.arc(cx, cy, rr, deg - 75, 150, p.radius * 0.55f);
                    g.color(MathUtil.withAlpha(0xff8affd8, 0.95f * (1f - f * 0.5f)));
                    g.arc(cx, cy, rr, deg - 65, 130, p.radius * 0.3f);
                    g.color(MathUtil.withAlpha(0xffffffff, 0.9f * (1f - f)));
                    g.arc(cx, cy, rr, deg - 40, 80, p.radius * 0.1f);
                    break;
                }
                case Projectile.LAVA: {
                    float f = Math.min(1f, p.t / p.flight);
                    float hgt = MathUtil.sin(f * MathUtil.PI) * 120;
                    g.color(MathUtil.withAlpha(0xffff6a1a, 0.18f + 0.3f * f));
                    g.fillCircle(p.targetX, p.targetY, p.aoe * f);
                    g.color(0x99ff8a2a);
                    g.strokeCircle(p.targetX, p.targetY, p.aoe, 3);
                    g.color(0x44000000);
                    g.fillCircle(p.x, p.y, p.radius * 0.9f);
                    float by = p.y - hgt;
                    if (fancy) g.radial(p.x, by, p.radius * 2.8f, 0xccff8a2a, 0x00ff3a0a);
                    g.color(0xff3a1410);
                    g.fillCircle(p.x, by, p.radius + 2.5f);
                    g.color(0xffff5a1a);
                    g.fillCircle(p.x, by, p.radius);
                    g.color(0xffffd23f);
                    g.fillCircle(p.x - p.radius * 0.25f, by - p.radius * 0.3f, p.radius * 0.45f);
                    if (MathUtil.frand() < 0.4f) fx.add(Particles.DOT, p.x, by, MathUtil.frand(-30, 30), 40, 5, 0xffff8a2a, 0.35f);
                    break;
                }
                case Projectile.PIXEL: {
                    // A glitchy beam: square pixels flickering between two colours
                    float a = (float) MathUtil.atan2(p.vy, p.vx);
                    float ca = MathUtil.cos(a), sa = MathUtil.sin(a);
                    for (int k = 0; k < 6; k++) {
                        float back = k * 16f;
                        float jit = ((int) (tt * 30f + k * 7) % 3 - 1) * 4f;
                        float x = p.x - ca * back - sa * jit, y = p.y - sa * back + ca * jit;
                        float sz = p.radius * (1f - k * 0.12f);
                        g.color(((int) (tt * 20f) + k) % 2 == 0 ? 0xff2affd0 : 0xffff2aa8);
                        g.fillRect(x - sz, y - sz, x + sz, y + sz);
                    }
                    g.color(0xffffffff);
                    g.fillRect(p.x - p.radius * 0.5f, p.y - p.radius * 0.5f, p.x + p.radius * 0.5f, p.y + p.radius * 0.5f);
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
            if (fancy) c = pass == 0 ? theme.bush0 : pass == 1 ? theme.bush1 : pass == 2 ? theme.bush2 : pass == 3 ? theme.bush3 : theme.bush4;
            else c = pass == 0 ? theme.bushLowA : theme.bushLowB;
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
                                g.color(MathUtil.withAlpha(theme.bushFlower, a));
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
            boolean mine = s == player;
            g.color(0xee0c0c1a);
            g.fillRoundRect(x - barW / 2 - 4, by - 4, x + barW / 2 + 4, by + barH + 4, 9);
            g.color(0xff3a2030);
            g.fillRoundRect(x - barW / 2, by, x + barW / 2, by + barH, 6);
            // Trailing "damage" chunk, then the real value
            g.color(0xfffff2c0);
            g.fillRoundRect(x - barW / 2, by, x - barW / 2 + barW * s.hpShown, by + barH, 6);
            boolean ally = sameTeam(s, player);
            int top = mine ? 0xff8cff6a : ally ? 0xff8ad8ff : 0xffff7a5a, bot = mine ? 0xff2ea82e : ally ? 0xff2a7ad8 : 0xffc8202a;
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
            g.color(mine ? 0xff9cff8a : ally ? 0xff8ad8ff : 0xffffffff);
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
                if (o == p || !o.alive || sameTeam(o, p) || !visibleTo(o, p)) continue;
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

    private void drawAimCone(Gfx g, float hx, float hy, float range, float spread, int col, int edge) {
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
            case Brawler.JOKER:
            case Brawler.REAPER:
            case Brawler.MAGMA:
                if (aimSuper) {
                    g.color(col);
                    g.fillCircle(hx, hy, range);
                    g.color(edge);
                    g.strokeCircle(hx, hy, range, 4);
                    break;
                }
                if (s.type.id == Brawler.MAGMA) {
                    float d = MathUtil.clamp(aimDist, 140, range);
                    g.color(col);
                    for (int i = -1; i <= 1; i++) {
                        float a = aimAng + i * 0.16f, dd = d * (i == 0 ? 1f : 0.86f);
                        g.fillCircle(hx + MathUtil.cos(a) * dd, hy + MathUtil.sin(a) * dd, 70);
                    }
                    g.color(edge);
                    g.strokeCircle(hx + ca * d, hy + sa * d, 70, 4);
                    break;
                }
                drawAimCone(g, hx, hy, range, s.type.id == Brawler.REAPER ? 0.5f : 0.2f, col, edge);
                break;
            case Brawler.GLITCH: {
                if (!aimSuper) {
                    drawAimLane(g, hx, hy, ca, sa, range, 14, col, edge);
                    break;
                }
                float d = MathUtil.clamp(aimDist, 180, range);
                drawAimLane(g, hx, hy, ca, sa, d, s.radius, col, edge);
                g.color(col);
                g.fillCircle(hx + ca * d, hy + sa * d, 210);
                g.color(edge);
                g.strokeCircle(hx + ca * d, hy + sa * d, 210, 4);
                break;
            }
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
            case Brawler.COBRA:
            case Brawler.RUMBLE:
                if (aimSuper) {
                    g.color(col);
                    g.fillCircle(hx, hy, range);
                    g.color(edge);
                    g.strokeCircle(hx, hy, range, 4);
                } else {
                    drawAimLane(g, hx, hy, ca, sa, range, s.type.id == Brawler.RUMBLE ? 40 : 12, col, edge);
                }
                break;
            case Brawler.THORN:
            case Brawler.NOVA:
                if (!aimSuper) {
                    drawAimLane(g, hx, hy, ca, sa, range, 16, col, edge);
                    if (s.type.id == Brawler.THORN) {
                        g.color(edge);
                        g.strokeCircle(hx + ca * range, hy + sa * range, 60, 3);
                    }
                    break;
                }
                // fall through: both supers are aimed at an area
            case Brawler.BOOMER:
            default: {
                float d = MathUtil.clamp(aimDist, 110, range);
                float tx = hx + ca * d, ty = hy + sa * d;
                int bid = s.type.id;
                float aoe = bid == Brawler.TOXIN ? (aimSuper ? 210 : 95) : bid == Brawler.THORN ? 200
                        : bid == Brawler.NOVA ? 220 : (aimSuper ? 230 : 105);
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
