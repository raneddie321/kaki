package com.kaki.snakebrawl.game;

/** Simple utility-style AI: avoid obstacles, collect food and boxes, fight, flee and dodge poison. */
final class BotBrain {
    private static final float[] OFFSETS = {0f, 0.35f, -0.35f, 0.7f, -0.7f, 1.05f, -1.05f, 1.45f, -1.45f,
            1.9f, -1.9f, 2.4f, -2.4f, 2.9f, -2.9f};

    private final World w;
    private final Snake me;
    private final float skill;
    private final float aggression;
    private float thinkTimer;
    private float desired;
    private boolean wantBoost;
    private float strafeDir = 1f;
    private float strafeTimer;
    private float wanderX, wanderY, wanderTimer;
    private float reaction;
    private float shotGap;
    private Snake lastTarget;

    BotBrain(World w, Snake me, int trophies) {
        this.w = w;
        this.me = me;
        float base = 0.35f + Math.min(0.45f, trophies / 700f);
        this.skill = MathUtil.clamp(base + MathUtil.rand(-0.12f, 0.12f), 0.2f, 0.95f);
        this.aggression = MathUtil.rand(0.2f, 1f);
        reset();
    }

    void reset() {
        thinkTimer = MathUtil.rand(0, 0.2f);
        desired = me.ang;
        wantBoost = false;
        wanderTimer = 0;
        lastTarget = null;
        reaction = 0;
    }

    void update(float dt) {
        thinkTimer -= dt;
        strafeTimer -= dt;
        wanderTimer -= dt;
        if (reaction > 0) reaction -= dt;
        if (shotGap > 0) shotGap -= dt;
        if (thinkTimer <= 0) {
            thinkTimer = 0.11f + MathUtil.rand(0, 0.07f);
            think();
        }
        me.targetAng = desired;
        me.boostInput = wantBoost;
    }

    private void think() {
        float hx = me.hx(), hy = me.hy();
        float goalAng;
        float urgency = 0f;
        wantBoost = false;

        // Find the most attractive visible enemy
        Snake target = null;
        float bestScore = Float.MAX_VALUE;
        float targetDist = 0;
        for (int i = 0; i < w.snakeCount; i++) {
            Snake o = w.snakes[i];
            if (o == me || !o.alive || !w.visibleTo(o, me)) continue;
            float d = MathUtil.dist(hx, hy, o.hx(), o.hy());
            if (d > 950) continue;
            float score = d + (o.hp / o.maxHp) * 250f - (o == lastTarget ? 120 : 0) - (o == me.lastAttacker ? 150 : 0) - (o.isHero ? 260 : 0);
            if (score < bestScore) {
                bestScore = score;
                target = o;
                targetDist = d;
            }
        }
        if (target != lastTarget) reaction = 0.45f - skill * 0.35f;
        lastTarget = target;

        float hpFrac = me.hp / me.maxHp;
        boolean poisonDanger = w.zoneActive() && (!w.inZone(hx, hy) || distToZoneEdge(hx, hy) < 220);

        if (poisonDanger) {
            goalAng = MathUtil.angleTo(hx, hy, (w.zoneL + w.zoneR) / 2, (w.zoneT + w.zoneB) / 2);
            urgency = 1f;
            wantBoost = !w.inZone(hx, hy) && me.mass > 50;
        } else if (target != null && hpFrac < 0.32f && targetDist < 650 && target.hp > me.hp) {
            goalAng = MathUtil.angleTo(target.hx(), target.hy(), hx, hy);
            wantBoost = me.mass > 70 && targetDist < 400;
            urgency = 0.8f;
        } else if (target != null && targetDist < me.type.range * (0.9f + aggression * 0.7f) + 60
                && (w.matchTime > 18f || targetDist < me.type.range * 0.8f)) {
            goalAng = combatMove(target, targetDist);
        } else {
            goalAng = foodGoal(hx, hy);
        }

        // Shooting happens independently of where we steer. Early on bots mostly farm unless provoked.
        boolean provoked = me.lastAttacker == target && me.sinceDamaged < 4f;
        boolean early = w.mode == World.MODE_SHOWDOWN && w.matchTime < 18f;
        if (target != null && reaction <= 0 && (!early || provoked || targetDist < me.type.range * 0.55f)) shootAt(target, targetDist);
        else if (target == null && me.ammo >= 2.5f) shootBox();

        desired = avoid(goalAng, urgency);
    }

    private float distToZoneEdge(float x, float y) {
        return Math.min(Math.min(x - w.zoneL, w.zoneR - x), Math.min(y - w.zoneT, w.zoneB - y));
    }

    private float combatMove(Snake t, float dist) {
        float hx = me.hx(), hy = me.hy();
        float toT = MathUtil.angleTo(hx, hy, t.hx(), t.hy());
        if (strafeTimer <= 0) {
            strafeTimer = MathUtil.rand(1.2f, 2.8f);
            strafeDir = MathUtil.rand() < 0.5f ? 1f : -1f;
        }
        // Snakes love to bite the hero
        if (t.isHero && me.hp > me.maxHp * 0.4f && dist < 520 && aggression > 0.45f) {
            wantBoost = dist < 300 && me.mass > 60 && MathUtil.rand() < skill;
            float tt = Math.min(0.5f, dist / 600f);
            return MathUtil.angleTo(hx, hy, t.hx() + t.vx * tt, t.hy() + t.vy * tt);
        }
        // Big snakes try to cut smaller snakes off, slither.io style
        if (me.mass > t.mass * 1.35f && dist < 600 && skill > 0.4f) {
            float lead = 160f + t.radius * 3f;
            float tx = t.hx() + MathUtil.cos(t.ang) * lead, ty = t.hy() + MathUtil.sin(t.ang) * lead;
            wantBoost = dist < 380 && me.mass > 90 && MathUtil.rand() < skill;
            return MathUtil.angleTo(hx, hy, tx, ty);
        }
        if (me.type.id == Brawler.BLAZE) {
            return toT;
        }
        float ideal = me.type.range * 0.7f;
        if (dist > ideal * 1.15f) return toT + strafeDir * 0.35f;
        if (dist < ideal * 0.6f) return toT + MathUtil.PI - strafeDir * 0.5f;
        return toT + strafeDir * 1.45f;
    }

    private void shootAt(Snake t, float dist) {
        Brawler b = me.type;
        // Aim at the closest body part, leading heads by the projectile flight time
        float reach = me.superReady() ? Math.max(b.range, b.superRange) : b.range;
        if (!w.findAim(me, reach * 0.95f, b.projSpeed)) return;
        float ang = w.aimOutAng, d = w.aimOutDist;
        if (b.lobbed()) {
            // Bombs need the target point ahead of the head
            float tt = b.id == Brawler.TOXIN ? 0.6f : 0.55f;
            float tx = t.hx() + t.vx * tt * skill, ty = t.hy() + t.vy * tt * skill;
            float dd = MathUtil.dist(me.hx(), me.hy(), tx, ty);
            if (dd < b.range) {
                ang = MathUtil.angleTo(me.hx(), me.hy(), tx, ty);
                d = dd;
            }
        }
        float err = (1f - skill) * 0.28f;
        ang += MathUtil.rand(-err, err);

        if (me.superReady()) {
            boolean use = d < b.superRange * 0.85f;
            if (b.id == Brawler.BLAZE) use = d < 420 && me.hp > me.maxHp * 0.25f;
            else if (b.id == Brawler.FROST) use = d < b.superRange * 0.8f;
            else if (b.id == Brawler.ZIGGY) use = d < 500;
            else if (b.id == Brawler.SHADE) {
                // Shadow Step is an escape: blink away from danger and vanish
                if (me.hp < me.maxHp * 0.5f && me.trySuper(w, MathUtil.angleTo(t.hx(), t.hy(), me.hx(), me.hy()), b.superRange)) return;
                use = false;
            }
            if (use && me.trySuper(w, ang, d)) return;
        }
        if (d > b.range * 0.95f || shotGap > 0) return;
        boolean keepAmmo = me.ammo < 1.9f && MathUtil.rand() > skill && d > b.range * 0.6f;
        if (keepAmmo) return;
        if (MathUtil.rand() < 0.25f + skill * 0.45f && me.tryAttack(w, ang, d)) shotGap = 0.5f + (1f - skill) * 0.6f;
    }

    private void shootBox() {
        if (!w.findAim(me, me.type.range * 0.9f, 0)) return;
        me.tryAttack(w, w.aimOutAng, w.aimOutDist);
    }

    private float foodGoal(float hx, float hy) {
        float best = 0;
        float gx = 0, gy = 0;
        boolean found = false;
        for (int i = 0; i < w.cubeCount; i++) {
            float d = MathUtil.dist(hx, hy, w.cubeX[i], w.cubeY[i]);
            if (d > 1100) continue;
            float score = 40f / (d + 60f);
            if (score > best) {
                best = score;
                gx = w.cubeX[i];
                gy = w.cubeY[i];
                found = true;
            }
        }
        for (int b = 0; b < w.boxCount; b++) {
            float bx = (w.boxTile[b] % w.n + 0.5f) * World.T, by = (w.boxTile[b] / w.n + 0.5f) * World.T;
            float d = MathUtil.dist(hx, hy, bx, by);
            if (d > 900) continue;
            float score = 12f / (d + 100f);
            if (score > best) {
                best = score;
                // Stop at shooting distance rather than ramming the box
                float a = MathUtil.angleTo(bx, by, hx, hy);
                gx = bx + MathUtil.cos(a) * me.type.range * 0.6f;
                gy = by + MathUtil.sin(a) * me.type.range * 0.6f;
                found = true;
            }
        }
        float ca = MathUtil.cos(me.ang), sa = MathUtil.sin(me.ang);
        boolean zone = w.zoneActive();
        for (int i = 0; i < w.orbCount; i++) {
            float dx = w.ox[i] - hx, dy = w.oy[i] - hy;
            if (dx > 700 || dx < -700 || dy > 700 || dy < -700) continue;
            if (zone && !w.inZone(w.ox[i], w.oy[i])) continue;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            float facing = (dx * ca + dy * sa) / (d + 1f);
            float score = w.ov[i] / (d + 90f) * (1.2f + facing * 0.5f);
            if (score > best) {
                best = score;
                gx = w.ox[i];
                gy = w.oy[i];
                found = true;
            }
        }
        if (found) {
            if (best > 0.12f && me.mass > 80 && MathUtil.rand() < 0.15f) wantBoost = true;
            return MathUtil.angleTo(hx, hy, gx, gy);
        }
        if (wanderTimer <= 0 || MathUtil.dist2(hx, hy, wanderX, wanderY) < 150 * 150) {
            wanderTimer = MathUtil.rand(3, 6);
            float cx = w.zoneActive() ? (w.zoneL + w.zoneR) / 2 : w.size / 2;
            float cy = w.zoneActive() ? (w.zoneT + w.zoneB) / 2 : w.size / 2;
            float spread = w.zoneActive() ? (w.zoneR - w.zoneL) * 0.4f : w.size * 0.4f;
            wanderX = cx + MathUtil.rand(-spread, spread);
            wanderY = cy + MathUtil.rand(-spread, spread);
        }
        return MathUtil.angleTo(hx, hy, wanderX, wanderY);
    }

    /** Steers towards the goal while keeping clear of walls, bodies and poison. */
    private float avoid(float goalAng, float urgency) {
        float spd = me.speed();
        float look = 110f + spd * 0.55f + me.radius * 2f;
        float clearGoal = w.rayClear(me, goalAng, look);
        if (clearGoal >= look) return goalAng;
        float bestScore = -1e9f;
        float bestAng = goalAng;
        for (float off : OFFSETS) {
            float a = me.ang + off;
            float c = w.rayClear(me, a, look);
            float score = c / look * 2.4f + MathUtil.cos(MathUtil.wrap(a - goalAng)) * (0.9f + urgency * 0.6f)
                    - Math.abs(off) * 0.08f;
            if (score > bestScore) {
                bestScore = score;
                bestAng = a;
            }
        }
        // If everything is blocked, boosting through a gap is the best hope
        if (bestScore < 0.6f && me.mass > 45) wantBoost = true;
        return bestAng;
    }
}
