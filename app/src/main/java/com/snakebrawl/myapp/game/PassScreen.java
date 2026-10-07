package com.snakebrawl.myapp.game;

/** The Snake Pass screen: a scrolling track of 30 tiers with free and Pass+ rewards. */
final class PassScreen {
    static final int B_FIRST = 700;
    private static final int B_PLUS = 700, B_CLAIM_ALL = 701, B_REWARD = 710;
    static final int B_LAST = 710 + SnakePass.TIERS * 2 + 2;

    private final Game game;
    private final Ui ui;
    private final Profile pr;

    PassScreen(Game game) {
        this.game = game;
        this.ui = game.ui;
        this.pr = game.profile;
    }

    static boolean handles(int id) {
        return id >= B_FIRST && id < B_LAST;
    }

    private float rowH() {
        return 190 * game.u;
    }

    /** Scrolls so the current tier is in view. */
    void scrollToCurrent() {
        int t = Math.max(0, SnakePass.tier(pr) - 1);
        ui.scrollY = t * rowH();
    }

    // ------------------------------------------------------------------ layout

    void layout() {
        float u = game.u, w = game.w, h = game.h;
        ui.add(Game.B_BACK, game.padL + 10 * u, game.padT + 10 * u, game.padL + 230 * u, game.padT + 110 * u, "BACK", null, 0xffff5a5a);
        float r = w - game.padR - 20 * u;
        if (!pr.passPlus) {
            ui.add(B_PLUS, r - 320 * u, game.padT + 10 * u, r, game.padT + 130 * u, "GET PASS+", SnakePass.PLUS_PRICE + " coins",
                    0xffffc928);
        }
        int ready = SnakePass.readyCount(pr);
        if (ready > 1) {
            float l = pr.passPlus ? r - 320 * u : r - 660 * u;
            ui.add(B_CLAIM_ALL, l, game.padT + 10 * u, l + 320 * u, game.padT + 130 * u, "CLAIM ALL", ready + " rewards", 0xff4ad04a);
        }
        float top = game.padT + 290 * u, bottom = h - game.padB - 20 * u;
        float cx = w / 2;
        float rh = rowH();
        ui.setScrollArea(game.padL + 20 * u, top, r, bottom, SnakePass.TIERS * rh + 20 * u);
        float cw = 560 * u;
        for (int t = 1; t <= SnakePass.TIERS; t++) {
            float ct = top + 10 * u + (t - 1) * rh;
            ui.addScroll(B_REWARD + (t - 1) * 2, cx - 110 * u - cw, ct, cx - 110 * u, ct + rh - 22 * u, null, null, 0);
            ui.addScroll(B_REWARD + (t - 1) * 2 + 1, cx + 110 * u, ct, cx + 110 * u + cw, ct + rh - 22 * u, null, null, 0);
        }
    }

    // ------------------------------------------------------------------ input

    void onButton(int id) {
        if (id == B_PLUS) {
            game.confirm("SNAKE PASS+", "Unlock the golden track for this season for " + SnakePass.PLUS_PRICE
                    + " coins? You get the " + Brawler.ALL[SnakePass.BRAWLER_PLUS].name + " brawler, the "
                    + Skin.ALL[SnakePass.SKIN_PLUS].name + " skin, Mega Boxes and lots of coins.", 1, 0, Game.ACT_PASS_PLUS, 0);
            return;
        }
        if (id == B_CLAIM_ALL) {
            int got = 0;
            for (int t = 1; t <= SnakePass.tier(pr); t++) {
                for (int track = 0; track < 2; track++) {
                    if (SnakePass.canClaim(pr, track, t)) {
                        claim(track, t, false);
                        got++;
                    }
                }
            }
            if (got > 0) {
                game.gated.playSound(Platform.SND_VICTORY, 0.8f);
                game.showInfo("REWARDS CLAIMED!", got + " rewards collected. Coins: " + pr.coins + ".", 1, 0);
            }
            game.layout();
            return;
        }
        int k = id - B_REWARD;
        int t = k / 2 + 1, track = k % 2;
        if (t < 1 || t > SnakePass.TIERS) return;
        if (SnakePass.canClaim(pr, track, t)) {
            claim(track, t, true);
        } else if (track == SnakePass.TRACK_PLUS && !pr.passPlus) {
            onButton(B_PLUS);
            return;
        } else if (!SnakePass.claimed(pr, track, t)) {
            game.showInfo("TIER " + t, "Reach tier " + t + " to get " + SnakePass.name(track, t) + ". Play matches to earn pass XP!",
                    0, 0);
        }
        game.layout();
    }

    void perform(int action) {
        if (action == Game.ACT_PASS_PLUS) {
            if (pr.passPlus || !pr.spend(SnakePass.PLUS_PRICE)) {
                if (!pr.passPlus) game.showInfo("NOT ENOUGH COINS", "Snake Pass+ costs " + SnakePass.PLUS_PRICE + " coins.", 1, 0);
                return;
            }
            pr.passPlus = true;
            pr.save();
            game.gated.playSound(Platform.SND_VICTORY, 1f);
            game.showInfo("SNAKE PASS+ UNLOCKED!", "The golden track is open. Claim every reward you have already reached!", 1, 0);
            game.layout();
        }
    }

    private void claim(int track, int t, boolean announce) {
        int bit = 1 << (t - 1);
        if (track == SnakePass.TRACK_FREE) pr.passFree |= bit;
        else pr.passPremium |= bit;
        int type = SnakePass.type(track, t), a = SnakePass.amount(track, t);
        switch (type) {
            case SnakePass.R_COINS:
                pr.coins += a;
                pr.save();
                game.gated.playSound(Platform.SND_KILL, 0.8f);
                if (announce) game.coinBurst(a);
                break;
            case SnakePass.R_BOX:
            case SnakePass.R_MEGA:
                pr.save();
                if (announce) game.openRewardBox(type == SnakePass.R_MEGA);
                else pr.coins += type == SnakePass.R_MEGA ? 400 : 120;
                break;
            case SnakePass.R_SKIN:
                if (pr.ownsSkin(a)) {
                    pr.coins += 600;
                    if (announce) game.showInfo("TIER " + t, "You already own " + Skin.ALL[a].name + ", so here are 600 coins!", 1, 0);
                } else {
                    pr.ownedSkins |= 1 << a;
                    pr.skin = a;
                    if (announce) game.showInfo("NEW SKIN!", Skin.ALL[a].name + " is yours and equipped on all your brawlers.", 2, a);
                }
                pr.save();
                game.gated.playSound(Platform.SND_VICTORY, 0.8f);
                break;
            default:
                if (pr.isUnlocked(a)) {
                    pr.coins += 800;
                    if (announce) game.showInfo("TIER " + t, "You already have " + Brawler.ALL[a].name + ", so here are 800 coins!", 1, 0);
                } else {
                    pr.unlocked |= 1 << a;
                    if (announce) game.showInfo(Brawler.ALL[a].name + " UNLOCKED!", Brawler.ALL[a].name + " joined your team. Super: "
                            + Brawler.ALL[a].superName + ".", 3, a);
                }
                pr.save();
                game.gated.playSound(Platform.SND_VICTORY, 0.8f);
                break;
        }
    }

    // ------------------------------------------------------------------ render

    void render(Gfx g) {
        float u = game.u, w = game.w, h = game.h, cx = w / 2;
        g.vertical(0, 0, w, h, 0xee1a1450, 0xf00a0c24);
        if (!pr.lowGraphics) g.radial(cx, game.padT + 120 * u, 700 * u, 0x44ffc94a, 0x00ffc94a);
        g.color(0xffffd23f);
        g.text("SNAKE PASS", cx, game.padT + 88 * u, 84 * u, Gfx.ALIGN_CENTER, 9 * u, Ui.INK);
        g.color(0xffd8dcff);
        g.text("SEASON " + pr.passSeason + "  •  ENDS IN " + SnakePass.daysLeft() + " DAYS", cx, game.padT + 136 * u, 30 * u,
                Gfx.ALIGN_CENTER, 4 * u, Ui.INK);

        // Progress bar
        int tier = SnakePass.tier(pr);
        float bl = cx - 520 * u, br = cx + 520 * u, bt = game.padT + 168 * u;
        drawTierBadge(g, bl - 50 * u, bt + 30 * u, 52 * u, tier, true);
        g.color(Ui.INK);
        g.fillRoundRect(bl + 20 * u, bt + 6 * u, br, bt + 56 * u, 25 * u);
        g.color(0xff2a2e5a);
        g.fillRoundRect(bl + 26 * u, bt + 12 * u, br - 6 * u, bt + 50 * u, 19 * u);
        float f = tier >= SnakePass.TIERS ? 1f : SnakePass.tierXp(pr) / (float) SnakePass.XP_PER_TIER;
        if (f > 0.02f) {
            g.vertical(bl + 26 * u, bt + 12 * u, bl + 26 * u + (br - bl - 32 * u) * f, bt + 50 * u, 0xff9cff8a, 0xff3ac04a);
        }
        g.color(0xffffffff);
        g.text(tier >= SnakePass.TIERS ? "PASS COMPLETE!" : SnakePass.tierXp(pr) + " / " + SnakePass.XP_PER_TIER + " XP", (bl + br) / 2,
                bt + 44 * u, 32 * u, Gfx.ALIGN_CENTER, 4 * u, Ui.INK);

        // Track headers
        float hy = game.padT + 272 * u;
        g.color(0xffb8f0ff);
        g.text("FREE", cx - 110 * u - 280 * u, hy, 36 * u, Gfx.ALIGN_CENTER, 5 * u, Ui.INK);
        g.color(0xffffd23f);
        g.text(pr.passPlus ? "SNAKE PASS+" : "SNAKE PASS+  (LOCKED)", cx + 110 * u + 280 * u, hy, 36 * u, Gfx.ALIGN_CENTER, 5 * u, Ui.INK);

        // The scrolling track
        g.save();
        g.clip(ui.viewL - 20 * u, ui.viewT, ui.viewR + 20 * u, ui.viewB);
        g.translate(0, -ui.scrollY);
        float rh = rowH();
        float top = ui.viewT + 10 * u;
        // Center rail
        float railT = top, railB = top + SnakePass.TIERS * rh;
        g.color(0xff151838);
        g.fillRoundRect(cx - 14 * u, railT, cx + 14 * u, railB, 14 * u);
        float filled = Math.min(railB, top + (tier - 0.5f) * rh + (tier < SnakePass.TIERS ? f * rh : 0));
        if (tier > 0 || f > 0) g.vertical(cx - 9 * u, railT, cx + 9 * u, Math.max(railT, filled), 0xff9cff8a, 0xff3ac04a);
        for (int t = 1; t <= SnakePass.TIERS; t++) {
            float ct = top + (t - 1) * rh;
            if (ct + rh < ui.viewT + ui.scrollY - 40 * u || ct > ui.viewB + ui.scrollY + 40 * u) continue;
            drawTierBadge(g, cx, ct + (rh - 22 * u) / 2, 44 * u, t, t <= tier);
        }
        for (int i = 0; i < ui.count; i++) {
            Ui.Btn b = ui.btns[i];
            if (!b.scrolls) continue;
            if (b.b - ui.scrollY < ui.viewT - 20 * u || b.t - ui.scrollY > ui.viewB + 20 * u) continue;
            int k = b.id - B_REWARD;
            drawReward(g, b, k % 2, k / 2 + 1);
        }
        g.restore();
        for (int i = 0; i < ui.count; i++) if (!ui.btns[i].scrolls) ui.button(g, ui.btns[i]);
        ui.coinPill(g, w - game.padR - 20 * u, h - game.padB - 110 * u, pr.coins);
    }

    private void drawTierBadge(Gfx g, float x, float y, float r, int t, boolean reached) {
        g.color(Ui.INK);
        g.fillCircle(x, y, r + 5 * game.u);
        g.color(reached ? 0xff3ac04a : 0xff3a3f6a);
        g.fillCircle(x, y, r);
        g.color(reached ? 0xff9cff8a : 0xff5a6090);
        g.fillCircle(x - r * 0.1f, y - r * 0.12f, r * 0.8f);
        g.color(0xffffffff);
        g.text(Integer.toString(t), x, y + r * 0.36f, r * 0.95f, Gfx.ALIGN_CENTER, 4 * game.u, Ui.INK);
    }

    private void drawReward(Gfx g, Ui.Btn b, int track, int t) {
        float u = game.u, d = ui.pressed == b.id ? 5 * u : 0;
        float l = b.l + d, tp = b.t + d, r = b.r - d, bt = b.b - d;
        boolean plus = track == SnakePass.TRACK_PLUS;
        boolean claimed = SnakePass.claimed(pr, track, t);
        boolean ready = SnakePass.canClaim(pr, track, t);
        boolean locked = plus && !pr.passPlus;
        int bg = plus ? 0xff4a3a1a : 0xff22305a;
        if (ready && !pr.lowGraphics) {
            float p = 0.5f + 0.5f * MathUtil.sin(game.clock * 5f + t);
            g.radial((l + r) / 2, (tp + bt) / 2, (r - l) * 0.6f, MathUtil.withAlpha(plus ? 0xffffd23f : 0xff9cff8a, 0.3f + 0.2f * p),
                    0x00ffffff);
        }
        ui.panel(g, l, tp, r, bt, claimed ? 0xff1c2036 : bg);
        if (plus && !claimed) {
            g.save();
            g.clip(l + 8 * u, tp + 8 * u, r - 8 * u, bt - 8 * u);
            g.vertical(l, tp, r, bt, 0x66ffd23f, 0x00ffd23f);
            g.restore();
        }
        float ix = l + 90 * u, iy = (tp + bt) / 2;
        int type = SnakePass.type(track, t), a = SnakePass.amount(track, t);
        float alpha = claimed ? 0.45f : 1f;
        switch (type) {
            case SnakePass.R_COINS:
                ui.coin(g, ix, iy, 80 * u);
                break;
            case SnakePass.R_BOX:
            case SnakePass.R_MEGA:
                MetaScreens.drawBox(g, ix, iy, 100 * u, type == SnakePass.R_MEGA, game.clock, u);
                break;
            case SnakePass.R_SKIN: {
                Brawler sb = Brawler.ALL[pr.selected];
                ui.snakeArt(g, sb, Skin.ALL[a].paletteFor(sb), ix + 10 * u, iy, 0.42f * u, game.clock);
                break;
            }
            default: {
                Brawler ab = Brawler.ALL[a];
                ui.snakeArt(g, ab, new int[]{ab.color1, ab.color2}, ix + 10 * u, iy, 0.42f * u, game.clock);
                break;
            }
        }
        String title = type == SnakePass.R_COINS ? a + " COINS" : type == SnakePass.R_BOX ? "BRAWL BOX" : type == SnakePass.R_MEGA
                ? "MEGA BOX" : type == SnakePass.R_SKIN ? Skin.ALL[a].name.toUpperCase() : Brawler.ALL[a].name;
        g.color(MathUtil.withAlpha(type >= SnakePass.R_SKIN ? 0xffffd23f : 0xffffffff, alpha));
        g.text(title, l + 180 * u, iy - 4 * u, Ui.fit(g, title, 40 * u, r - l - 200 * u), Gfx.ALIGN_LEFT, 5 * u, Ui.INK);
        String sub = type == SnakePass.R_SKIN ? "Exclusive skin" : type == SnakePass.R_BRAWLER ? "New brawler!"
                : type == SnakePass.R_COINS ? "Coins" : "Open for prizes";
        g.color(MathUtil.withAlpha(0xffb8bdf0, alpha));
        g.text(sub, l + 180 * u, iy + 38 * u, 26 * u, Gfx.ALIGN_LEFT, 3 * u, Ui.INK);
        if (claimed) {
            g.color(0xff9cff8a);
            g.text("CLAIMED", r - 24 * u, tp + 44 * u, 26 * u, Gfx.ALIGN_RIGHT, 3 * u, Ui.INK);
        } else if (ready) {
            float p = 1f + 0.06f * MathUtil.sin(game.clock * 6f);
            g.color(0xffffe066);
            g.text("CLAIM!", r - 24 * u, tp + 46 * u, 32 * u * p, Gfx.ALIGN_RIGHT, 4 * u, Ui.INK);
        } else if (locked) {
            ui.lock(g, r - 50 * u, tp + 44 * u, 42 * u);
        }
    }
}
