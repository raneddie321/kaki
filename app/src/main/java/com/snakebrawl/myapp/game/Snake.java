package com.snakebrawl.myapp.game;

final class Snake {
    static final int MAX_SEG = 400;
    static final float BASE_SPEED = 250f;
    static final float BOOST_MULT = 1.8f;
    static final float DASH_MULT = 2.6f;
    static final float MIN_BOOST_MASS = 30f;

    final int index;
    String name;
    Brawler type;
    boolean isPlayer;
    int color1, color2;
    /** Body colors, cycled along the snake. color1/color2 are the first two. */
    int[] palette;
    /** Power level 1..7 from upgrades (players) or trophies (bots). */
    int level = 1;

    boolean alive;
    float deadTime;

    final float[] sx = new float[MAX_SEG];
    final float[] sy = new float[MAX_SEG];
    int segs;

    float ang, targetAng;
    float vx, vy;
    float mass;
    float hp, maxHp;
    int cubes;
    float superCharge;
    float ammo;
    float fireCooldown;
    float sinceDamaged, sinceAttack;
    boolean boostInput, boosting;
    float boostDropTimer;
    float spawnShield;
    float dashTime;
    final boolean[] dashHit = new boolean[32];
    float revealTime;
    float invisTime;
    float slowTime, slowFactor = 1f;
    float hitFlash;
    float kbx, kby;
    float poisonTime, poisonTick;
    float bumpCooldown;
    float eatSoundTimer;
    /** Displayed health fraction, lagging behind real health for the damage-chunk effect. */
    float hpShown = 1f;
    /** For reveal effects: whether this snake's head was hidden from the player last step. */
    boolean wasHidden;
    // Damage numbers are batched per snake for a short moment
    float dmgPending, dmgTimer, dmgX, dmgY, dmgSize;
    int dmgColor;
    int kills;
    int rank;
    /** Team index in Duo Showdown, -1 when everyone is on their own. */
    int team = -1;
    Snake lastAttacker;
    float lastAttackerTime;
    BotBrain brain;

    // Queued multi-shot attacks (flame bursts, rail storm).
    int burstLeft;
    float burstTimer, burstInterval, burstAng, burstDist;
    boolean burstSuper;

    float radius, spacing;
    float minX, minY, maxX, maxY;
    boolean headInBush;
    float animTime;
    float tongueTimer;
    boolean superWasReady;

    Snake(int index) {
        this.index = index;
    }

    void spawn(Brawler type, float x, float y, float angle, float mass) {
        this.type = type;
        this.alive = true;
        this.deadTime = 0;
        this.mass = mass;
        this.cubes = 0;
        this.superCharge = 0;
        this.ammo = 3;
        this.fireCooldown = 0;
        this.sinceDamaged = 10;
        this.sinceAttack = 10;
        this.boostInput = false;
        this.boosting = false;
        this.spawnShield = isPlayer ? 4f : 2.5f;
        this.dashTime = 0;
        this.revealTime = 0;
        this.invisTime = 0;
        this.slowTime = 0;
        this.slowFactor = 1f;
        this.hitFlash = 0;
        this.kbx = this.kby = 0;
        this.poisonTime = 0;
        this.poisonTick = 0;
        this.bumpCooldown = 0;
        this.dmgPending = 0;
        this.hpShown = 1f;
        this.wasHidden = false;
        this.dmgTimer = 0;
        this.kills = 0;
        this.rank = 0;
        this.lastAttacker = null;
        this.burstLeft = 0;
        this.ang = this.targetAng = angle;
        this.superWasReady = false;
        updateSize();
        this.segs = targetSegs();
        for (int i = 0; i < segs; i++) {
            sx[i] = x - MathUtil.cos(angle) * spacing * i;
            sy[i] = y - MathUtil.sin(angle) * spacing * i;
        }
        this.maxHp = computeMaxHp();
        this.hp = maxHp;
        updateBounds();
    }

    float hx() {
        return sx[0];
    }

    float hy() {
        return sy[0];
    }

    int targetSegs() {
        int n = 12 + (int) (MathUtil.sqrt(mass) * 3.2f);
        return Math.min(n, MAX_SEG);
    }

    void updateSize() {
        radius = Math.min(34f, 15f + MathUtil.sqrt(mass) * 0.42f);
        spacing = radius * 0.52f;
    }

    void setColors(int[] pal) {
        palette = pal;
        color1 = pal[0];
        color2 = pal.length > 1 ? pal[1] : pal[0];
    }

    float levelMult() {
        return 1f + 0.06f * (level - 1);
    }

    float computeMaxHp() {
        return type.hp * 2f * (1f + 0.1f * cubes) * levelMult() + mass * 2f;
    }

    float damageMult() {
        return (1f + 0.1f * cubes) * levelMult() * (isPlayer ? World.PLAYER_DAMAGE_DEALT : 1f);
    }

    float speed() {
        float s = BASE_SPEED * type.speed * (1f - Math.min(0.15f, mass / 20000f));
        if (dashTime > 0) s *= DASH_MULT;
        else if (boosting) s *= BOOST_MULT;
        if (slowTime > 0) s *= slowFactor;
        return s;
    }

    float turnRate() {
        float r = 4.6f * (float) MathUtil.pow(17f / radius, 0.45f);
        return dashTime > 0 ? r * 0.35f : r;
    }

    boolean superReady() {
        return superCharge >= 1f;
    }

    /** Integrates movement for one step. Wall pushing is done by the world afterwards. */
    void move(float dt) {
        animTime += dt;
        float diff = MathUtil.wrap(targetAng - ang);
        float maxTurn = turnRate() * dt;
        if (diff > maxTurn) diff = maxTurn;
        else if (diff < -maxTurn) diff = -maxTurn;
        ang = MathUtil.wrap(ang + diff);

        boosting = boostInput && mass > MIN_BOOST_MASS && dashTime <= 0;
        float spd = speed();
        float ox = sx[0], oy = sy[0];
        sx[0] += MathUtil.cos(ang) * spd * dt + kbx * dt;
        sy[0] += MathUtil.sin(ang) * spd * dt + kby * dt;
        float decay = (float) MathUtil.exp(-7f * dt);
        kbx *= decay;
        kby *= decay;
        vx = (sx[0] - ox) / dt;
        vy = (sy[0] - oy) / dt;
    }

    /** Pulls every body segment after the head, then grows or shrinks by one segment. */
    void followBody() {
        updateSize();
        int target = targetSegs();
        if (segs < target) {
            sx[segs] = sx[segs - 1];
            sy[segs] = sy[segs - 1];
            segs++;
        } else if (segs > target && segs > 8) {
            segs--;
        }
        float sp = spacing;
        for (int i = 1; i < segs; i++) {
            float dx = sx[i - 1] - sx[i];
            float dy = sy[i - 1] - sy[i];
            float d2 = dx * dx + dy * dy;
            if (d2 > sp * sp) {
                float d = (float) Math.sqrt(d2);
                float f = (d - sp) / d;
                sx[i] += dx * f;
                sy[i] += dy * f;
            }
        }
        updateBounds();
    }

    void updateBounds() {
        float a = sx[0], b = sy[0], c = sx[0], d = sy[0];
        for (int i = 1; i < segs; i++) {
            float x = sx[i], y = sy[i];
            if (x < a) a = x;
            if (x > c) c = x;
            if (y < b) b = y;
            if (y > d) d = y;
        }
        minX = a - radius;
        minY = b - radius;
        maxX = c + radius;
        maxY = d + radius;
    }

    boolean tryAttack(World w, float aimAng, float aimDist) {
        if (!alive || ammo < 1f || fireCooldown > 0 || burstLeft > 0 || dashTime > 0) return false;
        ammo -= 1f;
        fireCooldown = 0.32f;
        sinceAttack = 0;
        revealTime = 1.3f;
        invisTime = 0;
        w.fire(this, aimAng, aimDist, false);
        return true;
    }

    boolean trySuper(World w, float aimAng, float aimDist) {
        if (!alive || !superReady() || burstLeft > 0 || dashTime > 0) return false;
        superCharge = 0;
        superWasReady = false;
        sinceAttack = 0;
        revealTime = 1.6f;
        invisTime = 0;
        w.fire(this, aimAng, aimDist, true);
        return true;
    }

    // ---------------------------------------------------------------- drawing

    private static final float[] TRI = new float[8];

    /**
     * Draws the snake. {@code hidden} lets the world hide segments that sit inside bushes.
     * Shared by the in-game renderer and the brawler preview cards.
     */
    /** High-quality rendering (shadows, gloss, glows); turned off by the low graphics setting. */
    static boolean fancy = true;
    /** Drop shadows are only drawn in the arena, not on menu previews. */
    static boolean shadows;

    static void drawBody(Gfx g, float[] xs, float[] ys, int n, float r, int[] pal,
                         boolean[] hidden, float alpha, boolean glow, float flash, float time,
                         float viewL, float viewT, float viewR, float viewB) {
        int np = Skin.colorCount(pal);
        int style = Skin.styleOf(pal);
        float m = r * 2.2f;
        if (shadows) {
            // Soft drop shadow under the whole body
            g.color(MathUtil.withAlpha(0x38000000, alpha));
            float ox = r * 0.22f, oy = r * 0.42f;
            for (int i = n - 1; i >= 0; i -= fancy ? 1 : 2) {
                if (hidden != null && hidden[i]) continue;
                float x = xs[i], y = ys[i];
                if (x < viewL - m || x > viewR + m || y < viewT - m || y > viewB + m) continue;
                g.fillCircle(x + ox, y + oy, r * (i > n - 8 ? 0.55f + 0.45f * (n - 1 - i) / 7f : 1f) + 2f);
            }
        }
        if (glow) {
            int gc = MathUtil.lighter(pal[0], 0.4f);
            for (int i = n - 1; i >= 0; i -= 3) {
                if (hidden != null && hidden[i]) continue;
                float x = xs[i], y = ys[i];
                if (x < viewL - m || x > viewR + m || y < viewT - m || y > viewB + m) continue;
                if (fancy) {
                    g.radial(x, y, r * 2.3f, MathUtil.withAlpha(gc, 0.45f * alpha), gc & 0x00ffffff);
                } else {
                    g.color(MathUtil.withAlpha(gc, 0.22f * alpha));
                    g.fillCircle(x, y, r * 1.7f);
                }
            }
        }
        for (int i = n - 1; i >= 1; i--) {
            if (hidden != null && hidden[i]) continue;
            float x = xs[i], y = ys[i];
            if (x < viewL - r || x > viewR + r || y < viewT - r || y > viewB + r) continue;
            if (fancy && i + 1 < n) {
                // Gentle slither wave across the body (visual only)
                float dx = xs[i - 1] - xs[i + 1], dy = ys[i - 1] - ys[i + 1];
                float dl = (float) Math.sqrt(dx * dx + dy * dy);
                if (dl > 0.01f) {
                    float wave = MathUtil.sin(time * 7f - i * 0.55f) * r * 0.14f * Math.min(1f, i / 5f);
                    x += -dy / dl * wave;
                    y += dx / dl * wave;
                }
            }
            float taper = i > n - 8 ? 0.55f + 0.45f * (n - 1 - i) / 7f : 1f;
            float rr = r * taper;
            int base = pal[((i + 1) / 3) % np];
            if (flash > 0) base = MathUtil.mix(base, 0xffffffff, flash);
            g.color(MathUtil.withAlpha(MathUtil.darker(base, 0.62f), alpha));
            g.fillCircle(x, y, rr + 2.5f);
            g.color(MathUtil.withAlpha(MathUtil.darker(base, 0.22f), alpha));
            g.fillCircle(x, y, rr);
            g.color(MathUtil.withAlpha(base, alpha));
            g.fillCircle(x - rr * 0.07f, y - rr * 0.11f, rr * 0.84f);
            g.color(MathUtil.withAlpha(MathUtil.lighter(base, 0.42f), alpha));
            g.fillCircle(x - rr * 0.22f, y - rr * 0.3f, rr * 0.42f);
            if (fancy) {
                g.color(MathUtil.withAlpha(0xffffffff, 0.55f * alpha));
                g.fillCircle(x - rr * 0.33f, y - rr * 0.43f, rr * 0.14f);
                if (style != Skin.STYLE_SPONGE && i + 1 < n && rr > 9f) {
                    // Overlapping scales: a soft crescent towards the tail on every segment
                    float tx = xs[i + 1] - xs[i], ty = ys[i + 1] - ys[i];
                    if (tx * tx + ty * ty > 0.01f) {
                        float deg = (float) Math.toDegrees(MathUtil.atan2(ty, tx));
                        g.color(MathUtil.withAlpha(MathUtil.darker(base, 0.38f), 0.42f * alpha));
                        g.arc(x, y, rr * 0.7f, deg - 58, 116, rr * 0.11f);
                        g.color(MathUtil.withAlpha(MathUtil.lighter(base, 0.3f), 0.35f * alpha));
                        g.arc(x, y, rr * 0.52f, deg + 180 - 40, 80, rr * 0.07f);
                    }
                }
            }
            if (style == Skin.STYLE_SPIKES && i % 2 == 0 && i < n - 3) {
                // Swept-back quills on both sides
                float bx = xs[i + 1] - xs[i], by = ys[i + 1] - ys[i];
                float bl = (float) Math.sqrt(bx * bx + by * by);
                if (bl > 0.01f) {
                    bx /= bl;
                    by /= bl;
                    float qx = -by, qy = bx;
                    int qc = MathUtil.withAlpha(MathUtil.darker(base, 0.35f), alpha);
                    for (int sgn = -1; sgn <= 1; sgn += 2) {
                        QUILL[0] = x + qx * rr * 0.55f * sgn - bx * rr * 0.2f;
                        QUILL[1] = y + qy * rr * 0.55f * sgn - by * rr * 0.2f;
                        QUILL[2] = x + qx * rr * 0.15f * sgn + bx * rr * 0.4f;
                        QUILL[3] = y + qy * rr * 0.15f * sgn + by * rr * 0.4f;
                        QUILL[4] = x + qx * rr * 1.25f * sgn + bx * rr * 1.2f;
                        QUILL[5] = y + qy * rr * 1.25f * sgn + by * rr * 1.2f;
                        g.color(qc);
                        g.fillPoly(QUILL, 3);
                    }
                    g.color(MathUtil.withAlpha(0xfff2d0a0, alpha));
                    g.fillCircle(x + bx * rr * 0.1f, y + by * rr * 0.1f, rr * 0.22f);
                }
            } else if (style == Skin.STYLE_SPONGE) {
                // Sponge holes at fixed spots per segment
                int hsh = i * 73 + 19;
                for (int k = 0; k < 3; k++) {
                    hsh = hsh * 1103515245 + 12345;
                    float ox = ((hsh >>> 8) & 255) / 255f - 0.5f, oy = ((hsh >>> 16) & 255) / 255f - 0.5f;
                    float hr2 = rr * (0.17f + 0.1f * (((hsh >>> 24) & 3) / 3f));
                    float hx2 = x + ox * rr * 1.1f, hy2 = y + oy * rr * 1.1f;
                    g.color(MathUtil.withAlpha(0xffa88a12, alpha));
                    g.fillCircle(hx2, hy2, hr2);
                    g.color(MathUtil.withAlpha(0xff7a6208, alpha));
                    g.fillCircle(hx2 + hr2 * 0.2f, hy2 + hr2 * 0.25f, hr2 * 0.6f);
                }
            }
        }
    }

    private static final float[] QUILL = new float[6];

    static void drawHead(Gfx g, float x, float y, float r, float ang, float lookAng, int c1, int c2,
                         int brawler, int accent, float alpha, float flash, float time, float tongue) {
        int base = flash > 0 ? MathUtil.mix(c1, 0xffffffff, flash) : c1;
        float hr = r * 1.18f;
        float ca = MathUtil.cos(ang), sa = MathUtil.sin(ang);
        float px = -sa, py = ca;

        // Tongue
        if (tongue > 0) {
            float tl = hr * (1.1f + 0.5f * tongue);
            float tx = x + ca * tl, ty = y + sa * tl;
            g.color(MathUtil.withAlpha(0xffe8304a, alpha));
            g.line(x + ca * hr * 0.8f, y + sa * hr * 0.8f, tx, ty, r * 0.16f);
            g.line(tx, ty, tx + (ca * 0.7f + px * 0.5f) * r * 0.35f, ty + (sa * 0.7f + py * 0.5f) * r * 0.35f, r * 0.13f);
            g.line(tx, ty, tx + (ca * 0.7f - px * 0.5f) * r * 0.35f, ty + (sa * 0.7f - py * 0.5f) * r * 0.35f, r * 0.13f);
        }

        // Accessories behind the head
        switch (brawler) {
            case Brawler.VOLT: {
                g.color(MathUtil.withAlpha(0xff222a3a, alpha));
                float bx1 = x - ca * hr * 0.3f + px * hr * 0.55f, by1 = y - sa * hr * 0.3f + py * hr * 0.55f;
                float bx2 = x - ca * hr * 0.3f - px * hr * 0.55f, by2 = y - sa * hr * 0.3f - py * hr * 0.55f;
                float wob = MathUtil.sin(time * 9f) * 0.15f;
                float ex1 = bx1 + (-ca + px * (0.9f + wob)) * hr * 0.9f, ey1 = by1 + (-sa + py * (0.9f + wob)) * hr * 0.9f;
                float ex2 = bx2 + (-ca - px * (0.9f - wob)) * hr * 0.9f, ey2 = by2 + (-sa - py * (0.9f - wob)) * hr * 0.9f;
                g.line(bx1, by1, ex1, ey1, r * 0.14f);
                g.line(bx2, by2, ex2, ey2, r * 0.14f);
                g.color(MathUtil.withAlpha(0x66fff04a, alpha));
                g.fillCircle(ex1, ey1, r * 0.42f);
                g.fillCircle(ex2, ey2, r * 0.42f);
                g.color(MathUtil.withAlpha(accent, alpha));
                g.fillCircle(ex1, ey1, r * 0.24f);
                g.fillCircle(ex2, ey2, r * 0.24f);
                break;
            }
            case Brawler.BOOMER: {
                float fx = x - ca * hr * 0.75f, fy = y - sa * hr * 0.75f;
                float wob = MathUtil.sin(time * 6f) * 0.4f;
                float ex = fx + (-ca + px * wob) * hr * 0.9f, ey = fy + (-sa + py * wob) * hr * 0.9f;
                g.color(MathUtil.withAlpha(0xff5a4632, alpha));
                g.line(fx, fy, ex, ey, r * 0.18f);
                float fl = 0.8f + 0.4f * MathUtil.sin(time * 30f);
                g.color(MathUtil.withAlpha(0xccff7a1a, alpha));
                g.fillCircle(ex, ey, r * 0.38f * fl);
                g.color(MathUtil.withAlpha(0xffffee66, alpha));
                g.fillCircle(ex, ey, r * 0.2f * fl);
                break;
            }
            case Brawler.SHADE: {
                // Ninja headband tails fluttering behind
                g.color(MathUtil.withAlpha(accent, alpha));
                for (int k = 0; k < 2; k++) {
                    float wob = MathUtil.sin(time * 11f + k * 1.7f) * 0.45f;
                    float bx = x - ca * hr * 0.6f + px * hr * 0.25f * (k == 0 ? 1 : -1);
                    float by = y - sa * hr * 0.6f + py * hr * 0.25f * (k == 0 ? 1 : -1);
                    float ex = bx + (-ca + px * (wob + (k == 0 ? 0.3f : -0.3f))) * hr * 1.2f;
                    float ey = by + (-sa + py * (wob + (k == 0 ? 0.3f : -0.3f))) * hr * 1.2f;
                    g.line(bx, by, ex, ey, r * 0.22f);
                }
                break;
            }
            case Brawler.NOVA: {
                // Twinkling stardust trailing behind
                for (int k = 0; k < 4; k++) {
                    float ph = (time * 0.7f + k / 4f) % 1f;
                    float bx = x - ca * hr * (0.6f + ph * 1.6f) + px * hr * MathUtil.sin(time * 2.3f + k * 1.9f) * 0.7f;
                    float by = y - sa * hr * (0.6f + ph * 1.6f) + py * hr * MathUtil.sin(time * 2.3f + k * 1.9f) * 0.7f;
                    Icons.star(g, bx, by, r * (0.5f - 0.3f * ph), time * 3f + k, MathUtil.withAlpha(0xffffe066, alpha * (1f - ph)));
                }
                break;
            }
            case Brawler.JOKER: {
                // Jester hat: three floppy points with bells
                for (int k = -1; k <= 1; k++) {
                    float wob = MathUtil.sin(time * 7f + k * 1.4f) * 0.25f;
                    float bx = x - ca * hr * 0.35f, by = y - sa * hr * 0.35f;
                    float dir = k * 0.75f + wob;
                    float ex = bx + (-ca + px * dir) * hr * 1.25f, ey = by + (-sa + py * dir) * hr * 1.25f;
                    TRI[0] = bx + px * hr * 0.32f + px * k * hr * 0.3f;
                    TRI[1] = by + py * hr * 0.32f + py * k * hr * 0.3f;
                    TRI[2] = ex;
                    TRI[3] = ey;
                    TRI[4] = bx - px * hr * 0.32f + px * k * hr * 0.3f;
                    TRI[5] = by - py * hr * 0.32f + py * k * hr * 0.3f;
                    g.color(MathUtil.withAlpha(k == 0 ? 0xffffe14a : (k < 0 ? 0xffff2e8a : 0xff7a1fd6), alpha));
                    g.fillPoly(TRI, 3);
                    g.color(MathUtil.withAlpha(0xffffe14a, alpha));
                    g.fillCircle(ex, ey, r * 0.22f);
                    g.color(MathUtil.withAlpha(0xffb08a1a, alpha));
                    g.fillCircle(ex + r * 0.05f, ey + r * 0.06f, r * 0.08f);
                }
                break;
            }
            case Brawler.REAPER: {
                // Tattered hood behind the head and a floating scythe
                g.color(MathUtil.withAlpha(0xff0c0c16, alpha));
                g.fillCircle(x - ca * hr * 0.35f, y - sa * hr * 0.35f, hr * 1.12f);
                for (int k = -2; k <= 2; k++) {
                    float bx = x - ca * hr * 0.9f + px * hr * 0.35f * k, by = y - sa * hr * 0.9f + py * hr * 0.35f * k;
                    float wob = MathUtil.sin(time * 6f + k) * 0.2f;
                    TRI[0] = bx + px * hr * 0.2f;
                    TRI[1] = by + py * hr * 0.2f;
                    TRI[2] = bx + (-ca + px * wob) * hr * 0.75f;
                    TRI[3] = by + (-sa + py * wob) * hr * 0.75f;
                    TRI[4] = bx - px * hr * 0.2f;
                    TRI[5] = by - py * hr * 0.2f;
                    g.fillPoly(TRI, 3);
                }
                float sa2 = ang + 2.2f + MathUtil.sin(time * 2f) * 0.15f;
                float hx2 = x + MathUtil.cos(sa2) * hr * 1.35f, hy2 = y + MathUtil.sin(sa2) * hr * 1.35f;
                float bx2 = x + MathUtil.cos(sa2 + 2.6f) * hr * 0.9f, by2 = y + MathUtil.sin(sa2 + 2.6f) * hr * 0.9f;
                g.color(MathUtil.withAlpha(0xff5a3a24, alpha));
                g.line(hx2, hy2, bx2, by2, r * 0.16f);
                g.color(MathUtil.withAlpha(0xffd8e8f0, alpha));
                g.arc(hx2 + MathUtil.cos(sa2 + 1.6f) * hr * 0.55f, hy2 + MathUtil.sin(sa2 + 1.6f) * hr * 0.55f, hr * 0.6f,
                        (float) Math.toDegrees(sa2 + 1.6f + MathUtil.PI) - 70, 120, r * 0.18f);
                break;
            }
            case Brawler.MAGMA: {
                // Smoke puffs rising from the volcano head
                for (int k = 0; k < 3; k++) {
                    float ph = (time * 0.7f + k / 3f) % 1f;
                    float bx = x - ca * hr * (0.3f + ph * 1.6f) + px * hr * MathUtil.sin(time * 2f + k * 2f) * 0.4f;
                    float by = y - sa * hr * (0.3f + ph * 1.6f) + py * hr * MathUtil.sin(time * 2f + k * 2f) * 0.4f;
                    g.color(MathUtil.withAlpha(0xff3a2a26, alpha * 0.7f * (1f - ph)));
                    g.fillCircle(bx, by, r * (0.3f + 0.45f * ph));
                }
                break;
            }
            case Brawler.GLITCH: {
                // Broken pixels flickering around the head
                for (int k = 0; k < 6; k++) {
                    int st = (int) (time * 12f) + k * 31;
                    if ((st * 7 + k) % 5 == 0) continue;
                    float a = k * 1.05f + (st % 4) * 0.4f;
                    float d = hr * (1.05f + (st % 3) * 0.18f);
                    float qx = x + MathUtil.cos(a) * d, qy = y + MathUtil.sin(a) * d;
                    float qs = r * (0.12f + (st % 2) * 0.08f);
                    g.color(MathUtil.withAlpha(k % 2 == 0 ? 0xff2affd0 : 0xffff2aa8, alpha));
                    g.fillRect(qx - qs, qy - qs, qx + qs, qy + qs);
                }
                // Colour-split ghost of the head
                float off = hr * 0.12f * MathUtil.sin(time * 23f);
                g.color(MathUtil.withAlpha(0x88ff2aa8, alpha));
                g.fillCircle(x + off, y, hr + 2f);
                g.color(MathUtil.withAlpha(0x882affd0, alpha));
                g.fillCircle(x - off, y, hr + 2f);
                break;
            }
            case Brawler.TOXIN: {
                // Toxic bubbles drifting off the head
                for (int k = 0; k < 3; k++) {
                    float ph = (time * 0.9f + k / 3f) % 1f;
                    float bx = x - ca * hr * (0.4f + ph * 1.4f) + px * hr * MathUtil.sin(time * 3f + k) * 0.5f;
                    float by = y - sa * hr * (0.4f + ph * 1.4f) + py * hr * MathUtil.sin(time * 3f + k) * 0.5f;
                    g.color(MathUtil.withAlpha(0xffa6ff3a, alpha * (1f - ph)));
                    g.fillCircle(bx, by, r * (0.15f + 0.2f * ph));
                }
                break;
            }
            default:
                break;
        }

        if (shadows) {
            g.color(MathUtil.withAlpha(0x40000000, alpha));
            g.fillCircle(x + hr * 0.22f, y + hr * 0.42f, hr + 3f);
        }
        g.color(MathUtil.withAlpha(MathUtil.darker(base, 0.62f), alpha));
        g.fillCircle(x, y, hr + 3f);
        g.color(MathUtil.withAlpha(MathUtil.darker(base, 0.22f), alpha));
        g.fillCircle(x, y, hr);
        g.color(MathUtil.withAlpha(base, alpha));
        g.fillCircle(x - hr * 0.06f, y - hr * 0.1f, hr * 0.86f);
        g.color(MathUtil.withAlpha(MathUtil.lighter(base, 0.42f), alpha));
        g.fillCircle(x - hr * 0.22f, y - hr * 0.28f, hr * 0.45f);
        if (fancy) {
            g.color(MathUtil.withAlpha(0xffffffff, 0.6f * alpha));
            g.fillCircle(x - hr * 0.34f, y - hr * 0.42f, hr * 0.13f);
            // Nostrils
            g.color(MathUtil.withAlpha(MathUtil.darker(base, 0.55f), alpha));
            g.fillCircle(x + ca * hr * 0.8f + px * hr * 0.18f, y + sa * hr * 0.8f + py * hr * 0.18f, hr * 0.06f);
            g.fillCircle(x + ca * hr * 0.8f - px * hr * 0.18f, y + sa * hr * 0.8f - py * hr * 0.18f, hr * 0.06f);
        }

        switch (brawler) {
            case Brawler.VIPER: {
                // Spiky crest along the back of the head
                g.color(MathUtil.withAlpha(accent, alpha));
                for (int k = 0; k < 3; k++) {
                    float off = -0.15f - k * 0.38f;
                    float cxp = x + ca * hr * off, cyp = y + sa * hr * off;
                    float s = hr * (0.42f - k * 0.08f);
                    TRI[0] = cxp + ca * s * 0.6f; TRI[1] = cyp + sa * s * 0.6f;
                    TRI[2] = cxp - ca * s * 0.6f + px * s * 0.2f; TRI[3] = cyp - sa * s * 0.6f + py * s * 0.2f;
                    TRI[4] = cxp - ca * s * 0.6f - px * s * 0.2f; TRI[5] = cyp - sa * s * 0.6f - py * s * 0.2f;
                    g.fillPoly(TRI, 3);
                }
                break;
            }
            case Brawler.BLAZE: {
                g.color(MathUtil.withAlpha(accent, alpha));
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    float bx = x + ca * hr * 0.15f + px * hr * 0.75f * sgn;
                    float by = y + sa * hr * 0.15f + py * hr * 0.75f * sgn;
                    TRI[0] = bx + (ca * 0.2f + px * 0.9f * sgn) * hr * 0.75f;
                    TRI[1] = by + (sa * 0.2f + py * 0.9f * sgn) * hr * 0.75f;
                    TRI[2] = bx + ca * hr * 0.3f;
                    TRI[3] = by + sa * hr * 0.3f;
                    TRI[4] = bx - ca * hr * 0.3f;
                    TRI[5] = by - sa * hr * 0.3f;
                    g.fillPoly(TRI, 3);
                }
                break;
            }
            case Brawler.BOOMER: {
                // Helmet stripe
                g.color(MathUtil.withAlpha(0xff3a2a20, alpha * 0.85f));
                g.line(x - ca * hr * 0.75f, y - sa * hr * 0.75f, x + ca * hr * 0.1f, y + sa * hr * 0.1f, hr * 0.32f);
                break;
            }
            case Brawler.FROST: {
                // Icicle crown
                for (int k = -1; k <= 1; k++) {
                    float cxp = x - ca * hr * 0.35f + px * hr * 0.5f * k, cyp = y - sa * hr * 0.35f + py * hr * 0.5f * k;
                    float s2 = hr * (k == 0 ? 0.55f : 0.42f);
                    TRI[0] = cxp - ca * s2;
                    TRI[1] = cyp - sa * s2;
                    TRI[2] = cxp + px * s2 * 0.3f;
                    TRI[3] = cyp + py * s2 * 0.3f;
                    TRI[4] = cxp - px * s2 * 0.3f;
                    TRI[5] = cyp - py * s2 * 0.3f;
                    g.color(MathUtil.withAlpha(0xff2a7ab0, alpha));
                    g.fillPoly(TRI, 3);
                    g.color(MathUtil.withAlpha(0xffe8ffff, alpha));
                    g.fillCircle(cxp, cyp, s2 * 0.22f);
                }
                break;
            }
            case Brawler.ZIGGY: {
                // Propeller cap
                float cxp = x - ca * hr * 0.25f, cyp = y - sa * hr * 0.25f;
                g.color(MathUtil.withAlpha(accent, alpha));
                g.fillCircle(cxp, cyp, hr * 0.42f);
                float pa = time * 14f;
                g.color(MathUtil.withAlpha(0xffffe14a, alpha));
                g.line(cxp + MathUtil.cos(pa) * hr * 0.75f, cyp + MathUtil.sin(pa) * hr * 0.75f,
                        cxp - MathUtil.cos(pa) * hr * 0.75f, cyp - MathUtil.sin(pa) * hr * 0.75f, hr * 0.18f);
                g.color(MathUtil.withAlpha(0xff222230, alpha));
                g.fillCircle(cxp, cyp, hr * 0.12f);
                break;
            }
            case Brawler.TOXIN: {
                // Gas mask filter on the snout
                float fx = x + ca * hr * 0.75f, fy = y + sa * hr * 0.75f;
                g.color(MathUtil.withAlpha(0xff2a2a36, alpha));
                g.fillCircle(fx, fy, hr * 0.38f);
                g.color(MathUtil.withAlpha(0xffa6ff3a, alpha));
                g.strokeCircle(fx, fy, hr * 0.26f, hr * 0.1f);
                break;
            }
            case Brawler.SHADE: {
                // Headband across the eyes
                g.color(MathUtil.withAlpha(accent, alpha));
                g.line(x + ca * hr * 0.15f + px * hr * 0.95f, y + sa * hr * 0.15f + py * hr * 0.95f,
                        x + ca * hr * 0.15f - px * hr * 0.95f, y + sa * hr * 0.15f - py * hr * 0.95f, hr * 0.3f);
                break;
            }
            case Brawler.COBRA: {
                // Cowboy hat seen from above: wide brim, crown and band
                float hx = x - ca * hr * 0.32f, hy = y - sa * hr * 0.32f;
                g.color(MathUtil.withAlpha(0x55000000, alpha));
                g.fillCircle(hx + hr * 0.08f, hy + hr * 0.12f, hr * 0.95f);
                g.color(MathUtil.withAlpha(0xff5a3a1a, alpha));
                g.fillCircle(hx, hy, hr * 0.92f);
                g.color(MathUtil.withAlpha(0xff8a5a2a, alpha));
                g.fillCircle(hx, hy, hr * 0.84f);
                g.color(MathUtil.withAlpha(accent, alpha));
                g.fillCircle(hx, hy, hr * 0.56f);
                g.color(MathUtil.withAlpha(0xffa8743a, alpha));
                g.fillCircle(hx - ca * hr * 0.04f, hy - sa * hr * 0.04f, hr * 0.48f);
                g.color(MathUtil.withAlpha(0xffc89a5a, alpha));
                g.line(hx - px * hr * 0.3f, hy - py * hr * 0.3f, hx + px * hr * 0.3f, hy + py * hr * 0.3f, hr * 0.1f);
                break;
            }
            case Brawler.THORN: {
                // Cactus spines around the head and a pink flower on top
                g.color(MathUtil.withAlpha(0xfff2ffd8, alpha));
                for (int k = 0; k < 8; k++) {
                    float a = ang + MathUtil.PI * 0.35f + k * MathUtil.PI * 1.3f / 7f;
                    float cxa = MathUtil.cos(a), sxa = MathUtil.sin(a);
                    g.line(x + cxa * hr * 0.8f, y + sxa * hr * 0.8f, x + cxa * hr * 1.18f, y + sxa * hr * 1.18f, hr * 0.07f);
                }
                float fx = x - ca * hr * 0.45f, fy = y - sa * hr * 0.45f;
                g.color(MathUtil.withAlpha(accent, alpha));
                for (int k = 0; k < 5; k++) {
                    float a = time * 0.8f + k * MathUtil.TAU / 5f;
                    g.fillCircle(fx + MathUtil.cos(a) * hr * 0.2f, fy + MathUtil.sin(a) * hr * 0.2f, hr * 0.17f);
                }
                g.color(MathUtil.withAlpha(0xffffe066, alpha));
                g.fillCircle(fx, fy, hr * 0.12f);
                break;
            }
            case Brawler.RUMBLE: {
                // Red sweatband with tails
                g.color(MathUtil.withAlpha(accent, alpha));
                g.line(x - ca * hr * 0.05f + px * hr * 0.98f, y - sa * hr * 0.05f + py * hr * 0.98f,
                        x - ca * hr * 0.05f - px * hr * 0.98f, y - sa * hr * 0.05f - py * hr * 0.98f, hr * 0.26f);
                g.color(MathUtil.withAlpha(0xffffffff, alpha * 0.8f));
                g.line(x - ca * hr * 0.05f + px * hr * 0.5f, y - sa * hr * 0.05f + py * hr * 0.5f,
                        x - ca * hr * 0.05f - px * hr * 0.5f, y - sa * hr * 0.05f - py * hr * 0.5f, hr * 0.06f);
                break;
            }
            case Brawler.JOKER: {
                // Diamond face paint and a wide grin
                g.color(MathUtil.withAlpha(0xffffffff, alpha));
                g.fillCircle(x + ca * hr * 0.2f, y + sa * hr * 0.2f, hr * 0.6f);
                g.color(MathUtil.withAlpha(0xff1a0a20, alpha));
                g.arc(x + ca * hr * 0.25f, y + sa * hr * 0.25f, hr * 0.55f, (float) Math.toDegrees(ang) - 55, 110, hr * 0.12f);
                g.color(MathUtil.withAlpha(0xffff2e8a, alpha));
                g.fillCircle(x + ca * hr * 0.85f, y + sa * hr * 0.85f, hr * 0.17f);
                break;
            }
            case Brawler.REAPER: {
                // Skull face
                g.color(MathUtil.withAlpha(0xffe8e4d8, alpha));
                g.fillCircle(x + ca * hr * 0.25f, y + sa * hr * 0.25f, hr * 0.7f);
                g.color(MathUtil.withAlpha(0xff12121e, alpha));
                for (int k = -1; k <= 1; k++)
                    g.line(x + ca * hr * 0.7f + px * hr * 0.12f * k, y + sa * hr * 0.7f + py * hr * 0.12f * k,
                            x + ca * hr * 0.88f + px * hr * 0.12f * k, y + sa * hr * 0.88f + py * hr * 0.12f * k, hr * 0.06f);
                break;
            }
            case Brawler.MAGMA: {
                // Glowing lava cracks across the rock head
                float glow = 0.7f + 0.3f * MathUtil.sin(time * 5f);
                g.color(MathUtil.withAlpha(0xffffa21a, alpha * glow));
                g.line(x - ca * hr * 0.7f + px * hr * 0.2f, y - sa * hr * 0.7f + py * hr * 0.2f, x - ca * hr * 0.1f - px * hr * 0.25f,
                        y - sa * hr * 0.1f - py * hr * 0.25f, hr * 0.1f);
                g.line(x - ca * hr * 0.1f - px * hr * 0.25f, y - sa * hr * 0.1f - py * hr * 0.25f, x + ca * hr * 0.15f - px * hr * 0.7f,
                        y + sa * hr * 0.15f - py * hr * 0.7f, hr * 0.08f);
                g.line(x - ca * hr * 0.4f - px * hr * 0.05f, y - sa * hr * 0.4f - py * hr * 0.05f, x - ca * hr * 0.2f + px * hr * 0.7f,
                        y - sa * hr * 0.2f + py * hr * 0.7f, hr * 0.08f);
                // Crater on top with lava
                float cx2 = x - ca * hr * 0.45f, cy2 = y - sa * hr * 0.45f;
                g.color(MathUtil.withAlpha(0xff2a120c, alpha));
                g.fillCircle(cx2, cy2, hr * 0.36f);
                g.color(MathUtil.withAlpha(0xffff5a1a, alpha));
                g.fillCircle(cx2, cy2, hr * 0.26f);
                g.color(MathUtil.withAlpha(0xffffe066, alpha * glow));
                g.fillCircle(cx2, cy2, hr * 0.12f);
                break;
            }
            case Brawler.NOVA: {
                // Pointy wizard hat with a star
                float bx = x - ca * hr * 0.2f, by = y - sa * hr * 0.2f;
                float tipX = bx - ca * hr * 1.45f + px * hr * 0.25f * MathUtil.sin(time * 2f);
                float tipY = by - sa * hr * 1.45f + py * hr * 0.25f * MathUtil.sin(time * 2f);
                TRI[0] = bx + px * hr * 0.7f;
                TRI[1] = by + py * hr * 0.7f;
                TRI[2] = tipX;
                TRI[3] = tipY;
                TRI[4] = bx - px * hr * 0.7f;
                TRI[5] = by - py * hr * 0.7f;
                g.color(MathUtil.withAlpha(0xff1e1250, alpha));
                g.fillPoly(TRI, 3);
                g.color(MathUtil.withAlpha(0xff4a32b0, alpha));
                g.line(bx + px * hr * 0.72f, by + py * hr * 0.72f, bx - px * hr * 0.72f, by - py * hr * 0.72f, hr * 0.22f);
                Icons.star(g, bx - ca * hr * 0.55f, by - sa * hr * 0.55f, hr * 0.42f, time, MathUtil.withAlpha(accent, alpha));
                Icons.star(g, tipX, tipY, hr * 0.3f, -time * 2f, MathUtil.withAlpha(0xffffffff, alpha));
                break;
            }
            default:
                break;
        }

        if (brawler >= Brawler.JOKER && brawler <= Brawler.GLITCH) {
            drawCrazyEyes(g, x, y, hr, ca, sa, px, py, brawler, alpha, time);
            return;
        }
        // Eyes (with an occasional blink)
        float lc = MathUtil.cos(lookAng), ls = MathUtil.sin(lookAng);
        boolean blink = fancy && (time * 0.8f) % 3.7f < 0.1f;
        for (int sgn = -1; sgn <= 1 && blink; sgn += 2) {
            float ex = x + ca * hr * 0.38f + px * hr * 0.48f * sgn;
            float ey = y + sa * hr * 0.38f + py * hr * 0.48f * sgn;
            g.color(MathUtil.withAlpha(MathUtil.darker(base, 0.3f), alpha));
            g.fillCircle(ex, ey, hr * 0.36f);
            g.color(MathUtil.withAlpha(0xff1a1a24, alpha));
            g.line(ex - ca * hr * 0.25f, ey - sa * hr * 0.25f, ex + ca * hr * 0.25f, ey + sa * hr * 0.25f, hr * 0.09f);
        }
        for (int sgn = -1; sgn <= 1 && !blink; sgn += 2) {
            float ex = x + ca * hr * 0.38f + px * hr * 0.48f * sgn;
            float ey = y + sa * hr * 0.38f + py * hr * 0.48f * sgn;
            g.color(MathUtil.withAlpha(0xff1a1a24, alpha));
            g.fillCircle(ex, ey, hr * 0.39f);
            g.color(MathUtil.withAlpha(0xffffffff, alpha));
            g.fillCircle(ex, ey, hr * 0.33f);
            float ix = ex + lc * hr * 0.12f, iy = ey + ls * hr * 0.12f;
            g.color(MathUtil.withAlpha(0xff3a2f6a, alpha));
            g.fillCircle(ix, iy, hr * 0.2f);
            g.color(MathUtil.withAlpha(0xff0c0c12, alpha));
            g.fillCircle(ix, iy, hr * 0.13f);
            g.color(MathUtil.withAlpha(0xffffffff, alpha));
            g.fillCircle(ix - hr * 0.07f, iy - hr * 0.08f, hr * 0.07f);
            g.fillCircle(ix + hr * 0.05f, iy + hr * 0.06f, hr * 0.03f);
        }
        if (brawler == Brawler.BLAZE || brawler == Brawler.RUMBLE) {
            // Angry brows
            g.color(MathUtil.withAlpha(0xff1a1a24, alpha));
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                float ex = x + ca * hr * 0.38f + px * hr * 0.48f * sgn;
                float ey = y + sa * hr * 0.38f + py * hr * 0.48f * sgn;
                g.line(ex - ca * hr * 0.05f + px * hr * 0.3f * sgn, ey - sa * hr * 0.05f + py * hr * 0.3f * sgn,
                        ex + ca * hr * 0.3f - px * hr * 0.05f * sgn, ey + sa * hr * 0.3f - py * hr * 0.05f * sgn, hr * 0.12f);
            }
        }
    }

    /** Wild eyes for the crazy brawlers: spirals, glowing slits, fire and pixels. */
    private static void drawCrazyEyes(Gfx g, float x, float y, float hr, float ca, float sa, float px, float py, int brawler,
                                      float alpha, float time) {
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            // Joker's eyes are different sizes, which makes him look properly mad
            float size = brawler == Brawler.JOKER ? (sgn < 0 ? 0.46f : 0.32f) : 0.36f;
            float ex = x + ca * hr * 0.4f + px * hr * 0.48f * sgn;
            float ey = y + sa * hr * 0.4f + py * hr * 0.48f * sgn;
            switch (brawler) {
                case Brawler.JOKER: {
                    g.color(MathUtil.withAlpha(0xff1a1a24, alpha));
                    g.fillCircle(ex, ey, hr * (size + 0.06f));
                    g.color(MathUtil.withAlpha(0xffffffff, alpha));
                    g.fillCircle(ex, ey, hr * size);
                    // Spinning spiral
                    g.color(MathUtil.withAlpha(0xffff2e8a, alpha));
                    float rot = time * 6f * sgn;
                    for (int k = 0; k < 7; k++) {
                        float a = rot + k * 0.9f, d = hr * size * (0.12f + k * 0.11f);
                        g.fillCircle(ex + MathUtil.cos(a) * d, ey + MathUtil.sin(a) * d, hr * 0.06f);
                    }
                    break;
                }
                case Brawler.REAPER: {
                    float glow = 0.75f + 0.25f * MathUtil.sin(time * 4f + sgn);
                    g.color(MathUtil.withAlpha(0xff05050a, alpha));
                    g.fillCircle(ex, ey, hr * 0.32f);
                    g.color(MathUtil.withAlpha(0x668affd8, alpha * glow));
                    g.fillCircle(ex, ey, hr * 0.3f);
                    g.color(MathUtil.withAlpha(0xff8affd8, alpha * glow));
                    g.fillCircle(ex, ey, hr * 0.14f);
                    break;
                }
                case Brawler.MAGMA: {
                    g.color(MathUtil.withAlpha(0xff2a120c, alpha));
                    g.fillCircle(ex, ey, hr * 0.36f);
                    g.color(MathUtil.withAlpha(0xffffa21a, alpha));
                    g.fillCircle(ex, ey, hr * 0.29f);
                    g.color(MathUtil.withAlpha(0xffffffa0, alpha));
                    float j = MathUtil.sin(time * 17f + sgn) * hr * 0.05f;
                    g.fillCircle(ex + j, ey, hr * 0.13f);
                    // Angry brow
                    g.color(MathUtil.withAlpha(0xff1a0a06, alpha));
                    g.line(ex - ca * hr * 0.05f + px * hr * 0.32f * sgn, ey - sa * hr * 0.05f + py * hr * 0.32f * sgn,
                            ex + ca * hr * 0.32f - px * hr * 0.05f * sgn, ey + sa * hr * 0.32f - py * hr * 0.05f * sgn, hr * 0.13f);
                    break;
                }
                default: {
                    // Glitch: square eyes that jump around
                    int st = (int) (time * 9f) + (sgn > 0 ? 3 : 0);
                    float jx = (st % 3 - 1) * hr * 0.07f, jy = ((st / 3) % 3 - 1) * hr * 0.07f;
                    float s1 = hr * 0.34f, s2 = hr * 0.17f;
                    g.color(MathUtil.withAlpha(0xff0a0a14, alpha));
                    g.fillRect(ex - s1, ey - s1, ex + s1, ey + s1);
                    g.color(MathUtil.withAlpha(st % 7 == 0 ? 0xffff2aa8 : 0xff2affd0, alpha));
                    g.fillRect(ex + jx - s2, ey + jy - s2, ex + jx + s2, ey + jy + s2);
                    break;
                }
            }
        }
    }
}
