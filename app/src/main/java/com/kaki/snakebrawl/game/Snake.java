package com.kaki.snakebrawl.game;

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
    float hitFlash;
    float kbx, kby;
    float poisonTime, poisonTick;
    float bumpCooldown;
    float eatSoundTimer;
    // Damage numbers are batched per snake for a short moment
    float dmgPending, dmgTimer, dmgX, dmgY, dmgSize;
    int dmgColor;
    int kills;
    int rank;
    Snake lastAttacker;
    float lastAttackerTime;
    BotBrain brain;

    // Queued multi-shot attacks (flame bursts, rail storm).
    int burstLeft;
    float burstTimer, burstInterval, burstAng;
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
        this.spawnShield = 2.5f;
        this.dashTime = 0;
        this.revealTime = 0;
        this.hitFlash = 0;
        this.kbx = this.kby = 0;
        this.poisonTime = 0;
        this.poisonTick = 0;
        this.bumpCooldown = 0;
        this.dmgPending = 0;
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

    float computeMaxHp() {
        return type.hp * 2f * (1f + 0.1f * cubes) + mass * 2f;
    }

    float damageMult() {
        return 1f + 0.1f * cubes;
    }

    float speed() {
        float s = BASE_SPEED * type.speed * (1f - Math.min(0.15f, mass / 20000f));
        if (dashTime > 0) s *= DASH_MULT;
        else if (boosting) s *= BOOST_MULT;
        return s;
    }

    float turnRate() {
        float r = 4.6f * (float) Math.pow(17f / radius, 0.45f);
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
        float decay = (float) Math.exp(-7f * dt);
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
        w.fire(this, aimAng, aimDist, false);
        return true;
    }

    boolean trySuper(World w, float aimAng, float aimDist) {
        if (!alive || !superReady() || burstLeft > 0 || dashTime > 0) return false;
        superCharge = 0;
        superWasReady = false;
        sinceAttack = 0;
        revealTime = 1.6f;
        w.fire(this, aimAng, aimDist, true);
        return true;
    }

    // ---------------------------------------------------------------- drawing

    private static final float[] TRI = new float[8];

    /**
     * Draws the snake. {@code hidden} lets the world hide segments that sit inside bushes.
     * Shared by the in-game renderer and the brawler preview cards.
     */
    static void drawBody(Gfx g, float[] xs, float[] ys, int n, float r, int c1, int c2,
                         boolean[] hidden, float alpha, boolean glow, float flash, float time,
                         float viewL, float viewT, float viewR, float viewB) {
        int outline = MathUtil.darker(c2, 0.45f);
        if (glow) {
            int gc = MathUtil.withAlpha(MathUtil.lighter(c1, 0.3f), 0.22f * alpha);
            g.color(gc);
            for (int i = n - 1; i >= 0; i -= 2) {
                if (hidden != null && hidden[i]) continue;
                float x = xs[i], y = ys[i];
                if (x < viewL - r * 2 || x > viewR + r * 2 || y < viewT - r * 2 || y > viewB + r * 2) continue;
                g.fillCircle(x, y, r * 1.7f);
            }
        }
        for (int i = n - 1; i >= 1; i--) {
            if (hidden != null && hidden[i]) continue;
            float x = xs[i], y = ys[i];
            if (x < viewL - r || x > viewR + r || y < viewT - r || y > viewB + r) continue;
            float taper = i > n - 8 ? 0.55f + 0.45f * (n - 1 - i) / 7f : 1f;
            float rr = r * taper;
            boolean stripe = ((i + 1) / 3) % 2 == 0;
            int base = stripe ? c1 : c2;
            if (flash > 0) base = MathUtil.mix(base, 0xffffffff, flash);
            g.color(MathUtil.withAlpha(outline, alpha));
            g.fillCircle(x, y, rr + 2.5f);
            g.color(MathUtil.withAlpha(base, alpha));
            g.fillCircle(x, y, rr);
            g.color(MathUtil.withAlpha(MathUtil.lighter(base, 0.35f), alpha));
            g.fillCircle(x - rr * 0.18f, y - rr * 0.22f, rr * 0.5f);
        }
    }

    static void drawHead(Gfx g, float x, float y, float r, float ang, float lookAng, int c1, int c2,
                         int brawler, int accent, float alpha, float flash, float time, float tongue) {
        int outline = MathUtil.darker(c2, 0.45f);
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
            default:
                break;
        }

        g.color(MathUtil.withAlpha(outline, alpha));
        g.fillCircle(x, y, hr + 3f);
        g.color(MathUtil.withAlpha(base, alpha));
        g.fillCircle(x, y, hr);
        g.color(MathUtil.withAlpha(MathUtil.lighter(base, 0.35f), alpha));
        g.fillCircle(x - hr * 0.2f, y - hr * 0.25f, hr * 0.5f);

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
            default:
                break;
        }

        // Eyes
        float lc = MathUtil.cos(lookAng), ls = MathUtil.sin(lookAng);
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            float ex = x + ca * hr * 0.38f + px * hr * 0.48f * sgn;
            float ey = y + sa * hr * 0.38f + py * hr * 0.48f * sgn;
            g.color(MathUtil.withAlpha(0xff1a1a24, alpha));
            g.fillCircle(ex, ey, hr * 0.38f);
            g.color(MathUtil.withAlpha(0xffffffff, alpha));
            g.fillCircle(ex, ey, hr * 0.32f);
            g.color(MathUtil.withAlpha(0xff111118, alpha));
            g.fillCircle(ex + lc * hr * 0.12f, ey + ls * hr * 0.12f, hr * 0.17f);
            g.color(MathUtil.withAlpha(0xffffffff, alpha));
            g.fillCircle(ex + lc * hr * 0.12f - hr * 0.06f, ey + ls * hr * 0.12f - hr * 0.07f, hr * 0.06f);
        }
        if (brawler == Brawler.BLAZE) {
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
}
