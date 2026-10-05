package com.kaki.snakebrawl.game;

/** The hero screen, the shop and the settings screen. */
final class MetaScreens {
    private static final int B_UPGRADE = 151;
    private static final int B_GIFT = 310, B_BOX = 311, B_MEGA = 312, B_DEAL = 313, B_COINS = 314;
    private static final int B_SETTING = 400, B_RESET = 420;

    private static final String[] SETTING_NAMES = {"Sound", "Vibration", "Damage numbers", "Graphics",
            "Auto-aim on tap", "Left-handed controls", "Joystick size", "Camera"};

    private final Game game;
    private final Profile pr;
    private final Ui ui;

    MetaScreens(Game game) {
        this.game = game;
        this.pr = game.profile;
        this.ui = game.ui;
    }

    // ------------------------------------------------------------------ layout

    void layout() {
        float u = game.u, w = game.w, h = game.h;
        float padL = game.padL, padR = game.padR, padT = game.padT, padB = game.padB;
        ui.add(Game.B_BACK, padL + 10 * u, padT + 10 * u, padL + 230 * u, padT + 110 * u, "BACK", null, 0xffff5a5a);
        switch (game.screen) {
            case Game.BRAWLERS: {
                float pl = w * 0.5f + 20 * u, pr2 = w - padR - 30 * u, by = h - padB - 30 * u;
                int cost = pr.upgradeCost();
                Ui.Btn up = ui.add(B_UPGRADE, pl + 20 * u, by - 120 * u, pr2 - 20 * u, by,
                        cost < 0 ? "MAX LEVEL" : "UPGRADE", cost < 0 ? null : cost + " coins", 0xffb35cff);
                up.enabled = cost >= 0;
                break;
            }
            case Game.SHOP: {
                float top = padT + 150 * u, bottom = h - padB - 20 * u;
                float l = padL + 20 * u, r = w - padR - 20 * u;
                ui.setScrollArea(l, top, r, bottom, 0);
                float gap = 24 * u, cw = (r - l - gap * 4) / 5f;
                int[] ids = {B_GIFT, B_BOX, B_MEGA, B_DEAL, B_COINS};
                for (int i = 0; i < ids.length; i++) {
                    float cl = l + i * (cw + gap);
                    ui.add(ids[i], cl, top + 10 * u, cl + cw, bottom - 10 * u, null, null, 0);
                }
                break;
            }
            case Game.SETTINGS: {
                float top = padT + 160 * u;
                float colW = (w - padL - padR - 100 * u) / 2f;
                float rowH = 118 * u;
                for (int k = 0; k < SETTING_NAMES.length; k++) {
                    float l = padL + 30 * u + (k / 4) * (colW + 40 * u);
                    float t = top + (k % 4) * (rowH + 18 * u);
                    String v = settingValue(k);
                    boolean on = "ON".equals(v);
                    boolean off = "OFF".equals(v);
                    ui.add(B_SETTING + k, l + colW - 300 * u, t + 14 * u, l + colW - 20 * u, t + rowH - 14 * u, v, null,
                            on ? 0xff4ad04a : (off ? 0xff8a8a9a : 0xff3fa0ff));
                }
                float rb = h - padB - 30 * u;
                ui.add(B_RESET, w / 2 - 260 * u, rb - 100 * u, w / 2 + 260 * u, rb, "RESET PROGRESS", null, 0xffff5a5a);
                break;
            }
            default:
                break;
        }
    }

    // ------------------------------------------------------------------ input

    void onButton(int id) {
        if (id >= B_SETTING && id < B_SETTING + SETTING_NAMES.length) {
            toggleSetting(id - B_SETTING);
            return;
        }
        switch (id) {
            case B_UPGRADE: {
                int cost = pr.upgradeCost();
                if (cost < 0) break;
                if (pr.coins < cost) notEnough(cost);
                else game.confirm("UPGRADE HERO?", "Power level " + pr.heroLevel + " to " + (pr.heroLevel + 1)
                        + ": +6% health and damage. Costs " + cost + " coins.", 3, 0, Game.ACT_UPGRADE, cost);
                break;
            }
            case B_GIFT:
                if (pr.giftReady()) {
                    int amount = 80 + MathUtil.randInt(9) * 10;
                    pr.coins += amount;
                    pr.nextGiftMinute = Profile.nowMinute() + Profile.GIFT_COOLDOWN_MIN;
                    pr.save();
                    game.gated.playSound(Platform.SND_POWER, 1f);
                    game.showInfo("FREE GIFT!", "You got " + amount + " coins. Come back in 4 hours for another gift!", 1, 0);
                } else {
                    game.showInfo("NOT YET", "Your next free gift is ready in " + giftCountdown() + ".", 0, 0);
                }
                break;
            case B_BOX:
                if (pr.coins < Profile.BOX_PRICE) notEnough(Profile.BOX_PRICE);
                else game.confirm("BRAWL BOX", "Open a Brawl Box for " + Profile.BOX_PRICE
                        + " coins? It holds coins or a free hero upgrade.", 4, 0, Game.ACT_BOX, 0);
                break;
            case B_MEGA:
                if (pr.coins < Profile.MEGA_BOX_PRICE) notEnough(Profile.MEGA_BOX_PRICE);
                else game.confirm("MEGA BOX", "Open a Mega Box for " + Profile.MEGA_BOX_PRICE
                        + " coins? A guaranteed hero upgrade plus bonus coins!", 4, 1, Game.ACT_MEGA_BOX, 0);
                break;
            case B_DEAL: {
                int cost = dealPrice();
                if (cost < 0) game.showInfo("SOLD OUT", "Your hero is already at max level!", 3, 0);
                else if (pr.coins < cost) notEnough(cost);
                else game.confirm("DAILY DEAL", "Upgrade your hero to power level " + (pr.heroLevel + 1) + " for only "
                        + cost + " coins?", 3, 0, Game.ACT_UPGRADE, cost);
                break;
            }
            case B_COINS:
                game.showInfo("COIN TIPS", "Win Showdown matches for the most coins. Every knockout pays 8 coins, "
                        + "and the free gift refills every 4 hours.", 1, 0);
                break;
            case B_RESET:
                game.confirm("RESET PROGRESS?", "This deletes your trophies, coins and hero upgrades. "
                        + "It cannot be undone!", 0, 0, Game.ACT_RESET, 0);
                break;
            default:
                break;
        }
    }

    private void notEnough(int price) {
        game.showInfo("NOT ENOUGH COINS", "You need " + (price - pr.coins) + " more coins. Play matches, get knockouts "
                + "and claim free gifts to earn coins!", 1, 0);
    }

    /** Runs a confirmed popup action. */
    void perform(int action, int arg) {
        switch (action) {
            case Game.ACT_UPGRADE: {
                // arg carries the price that was shown (normal or daily deal)
                if (pr.upgradeCost() < 0 || !pr.spend(arg)) return;
                pr.heroLevel++;
                pr.save();
                game.gated.playSound(Platform.SND_POWER, 1f);
                game.showInfo("POWER LEVEL " + pr.heroLevel + "!", "Your hero got stronger: +6% health and damage.", 3, 0);
                break;
            }
            case Game.ACT_BOX:
                if (pr.spend(Profile.BOX_PRICE)) openBox(false);
                break;
            case Game.ACT_MEGA_BOX:
                if (pr.spend(Profile.MEGA_BOX_PRICE)) openBox(true);
                break;
            case Game.ACT_RESET:
                pr.resetProgress();
                game.showInfo("PROGRESS RESET", "Fresh start! You have " + pr.coins + " coins.", 0, 0);
                break;
            default:
                break;
        }
        game.layout();
    }

    private void openBox(boolean mega) {
        game.gated.playSound(Platform.SND_BOX, 1f);
        boolean canUpgrade = pr.heroLevel < Brawler.MAX_LEVEL;
        if (mega) {
            int coins = 150 + MathUtil.randInt(21) * 10;
            if (canUpgrade) {
                pr.heroLevel++;
                pr.coins += coins;
                pr.save();
                game.showInfo("MEGA BOX!", "Hero upgraded to power level " + pr.heroLevel + "! Plus " + coins + " coins.", 3, 0);
            } else {
                pr.coins += coins + 400;
                pr.save();
                game.showInfo("MEGA BOX!", "Your hero is maxed, so you get " + (coins + 400) + " coins!", 1, 0);
            }
            return;
        }
        if (canUpgrade && MathUtil.rand() < 0.2f) {
            pr.heroLevel++;
            pr.save();
            game.showInfo("BRAWL BOX!", "Free upgrade! Your hero is now power level " + pr.heroLevel + ".", 3, 0);
            return;
        }
        int coins = 60 + MathUtil.randInt(17) * 10;
        pr.coins += coins;
        pr.save();
        game.showInfo("BRAWL BOX!", "You found " + coins + " coins!", 1, 0);
    }

    /** Today's discounted hero upgrade, or -1 when maxed. */
    private int dealPrice() {
        int cost = pr.upgradeCost();
        return cost < 0 ? -1 : Math.round(cost * 0.6f / 10f) * 10;
    }

    private String giftCountdown() {
        int mins = Math.max(1, pr.nextGiftMinute - Profile.nowMinute());
        return mins >= 60 ? (mins / 60) + "h " + (mins % 60) + "m" : mins + "m";
    }

    // ------------------------------------------------------------------ render

    void render(Gfx g) {
        float u = game.u, w = game.w, h = game.h;
        g.color(0xcc0d1030);
        g.fillRect(0, 0, w, h);
        String title = game.screen == Game.BRAWLERS ? "HERO" : (game.screen == Game.SHOP ? "SHOP" : "SETTINGS");
        g.color(0xffffd23f);
        g.text(title, w / 2, game.padT + 92 * u, 84 * u, Gfx.ALIGN_CENTER, 9 * u, Ui.INK);
        if (game.screen != Game.SETTINGS) ui.coinPill(g, w - game.padR - 20 * u, game.padT + 14 * u, pr.coins);
        switch (game.screen) {
            case Game.BRAWLERS:
                renderHero(g);
                break;
            case Game.SHOP:
                renderShop(g);
                break;
            default:
                renderSettings(g);
                break;
        }
    }

    private void renderHero(Gfx g) {
        float u = game.u, w = game.w, h = game.h;
        Brawler br = Brawler.HERO;
        float top = game.padT + 140 * u, bottom = h - game.padB - 30 * u;
        // Big showcase on the left
        float l = game.padL + 30 * u, r = w * 0.5f - 20 * u;
        ui.panel(g, l, top, r, bottom, 0xee22264a);
        float glow = 0.5f + 0.5f * MathUtil.sin(game.clock * 2f);
        g.color(MathUtil.withAlpha(0xff3fa0ff, 0.15f + 0.1f * glow));
        g.fillCircle((l + r) / 2, (top + bottom) / 2, Math.min(r - l, bottom - top) * 0.4f);
        float rad = Math.min(r - l, bottom - top) * 0.13f;
        HeroArt.draw(g, (l + r) / 2, (top + bottom) / 2 + rad * 0.5f, rad, game.clock * 0.6f, game.clock, 1f, 0, false);
        g.color(0xffffd23f);
        g.text(br.name, (l + r) / 2, top + 80 * u, 72 * u, Gfx.ALIGN_CENTER, 8 * u, Ui.INK);
        g.color(0xffd8dcff);
        g.text(br.role.toUpperCase(), (l + r) / 2, bottom - 36 * u, 36 * u, Gfx.ALIGN_CENTER, 5 * u, Ui.INK);

        // Details on the right
        float pl = w * 0.5f + 20 * u, prr = w - game.padR - 30 * u, pb = h - game.padB - 175 * u;
        ui.panel(g, pl, top, prr, pb, 0xee22264a);
        int lvl = pr.heroLevel;
        float y = top + 70 * u;
        g.color(0xffffffff);
        g.text("POWER " + lvl + "/" + Brawler.MAX_LEVEL, pl + 30 * u, y, 36 * u, Gfx.ALIGN_LEFT, 5 * u, Ui.INK);
        float px0 = pl + 270 * u, pw = (prr - 30 * u - px0) / Brawler.MAX_LEVEL;
        for (int k = 0; k < Brawler.MAX_LEVEL; k++) {
            g.color(Ui.INK);
            g.fillRoundRect(px0 + k * pw, y - 30 * u, px0 + (k + 1) * pw - 6 * u, y + 2 * u, 6 * u);
            g.color(k < lvl ? 0xffb35cff : 0xff3a3e70);
            g.fillRoundRect(px0 + k * pw + 3 * u, y - 27 * u, px0 + (k + 1) * pw - 9 * u, y - 1 * u, 4 * u);
        }
        float mult = 1f + 0.06f * (lvl - 1);
        y += 60 * u;
        String[] labels = {"HEALTH", "DAMAGE", "RANGE", "SPEED"};
        int[] stars = {br.statHp, br.statDamage, br.statRange, br.statSpeed};
        String[] vals = {Integer.toString(Math.round(br.hp * 2 * mult)), Math.round(br.damage * mult) + " x3", null, null};
        for (int k = 0; k < 4; k++) {
            g.color(0xffb8bdf0);
            g.text(labels[k], pl + 30 * u, y, 30 * u, Gfx.ALIGN_LEFT, 4 * u, Ui.INK);
            float sx0 = pl + 200 * u, sw = 40 * u;
            for (int p = 0; p < 5; p++) {
                g.color(Ui.INK);
                g.fillRoundRect(sx0 + p * sw, y - 26 * u, sx0 + p * sw + sw - 6 * u, y, 5 * u);
                g.color(p < stars[k] ? 0xffffc928 : 0xff3a3e70);
                g.fillRoundRect(sx0 + p * sw + 3 * u, y - 23 * u, sx0 + p * sw + sw - 9 * u, y - 3 * u, 3 * u);
            }
            if (vals[k] != null) {
                g.color(0xffffffff);
                g.text(vals[k], prr - 30 * u, y, 34 * u, Gfx.ALIGN_RIGHT, 4 * u, Ui.INK);
            }
            y += 48 * u;
        }
        float limit = pb - 20 * u;
        y += 10 * u;
        g.color(0xffff9a4a);
        g.text("ATTACK: " + br.attackName, pl + 30 * u, y, 32 * u, Gfx.ALIGN_LEFT, 4 * u, Ui.INK);
        y = ui.wrap(g, br.attackDesc, pl + 30 * u, prr - 30 * u, y + 6 * u, 28 * u, 0xffe8eaff, limit);
        y += 14 * u;
        g.color(0xffffd23f);
        g.text("SUPER: " + br.superName, pl + 30 * u, y + 24 * u, 32 * u, Gfx.ALIGN_LEFT, 4 * u, Ui.INK);
        y = ui.wrap(g, br.superDesc, pl + 30 * u, prr - 30 * u, y + 30 * u, 28 * u, 0xffe8eaff, limit);
        y += 14 * u;
        g.color(0xff3fb6ff);
        g.text("DASH", pl + 30 * u, y + 24 * u, 32 * u, Gfx.ALIGN_LEFT, 4 * u, Ui.INK);
        ui.wrap(g, "Quick dodge that makes snake bites miss. Recharges in 2.5 s.", pl + 30 * u, prr - 30 * u, y + 30 * u,
                28 * u, 0xffe8eaff, limit);
        for (int i = 0; i < ui.count; i++) ui.button(g, ui.btns[i]);
    }

    private void renderShop(Gfx g) {
        for (int i = 0; i < ui.count; i++) {
            Ui.Btn bt = ui.btns[i];
            if (bt.id == Game.B_BACK) ui.button(g, bt);
            else drawOffer(g, bt);
        }
    }

    private float press(Ui.Btn bt) {
        return ui.pressed == bt.id ? 6 * game.u : 0;
    }

    private void drawOffer(Gfx g, Ui.Btn bt) {
        float u = game.u, d = press(bt);
        float l = bt.l + d, t = bt.t + d, r = bt.r - d, b = bt.b - d;
        float cx = (l + r) / 2;
        String title, sub, price;
        int color;
        boolean canAfford = true;
        switch (bt.id) {
            case B_GIFT:
                title = "FREE GIFT";
                color = 0xff4ad04a;
                sub = pr.giftReady() ? "Ready to claim!" : "Next in " + giftCountdown();
                price = pr.giftReady() ? "CLAIM" : "WAIT";
                break;
            case B_BOX:
                title = "BRAWL BOX";
                color = 0xff8a5aff;
                sub = "Coins or an upgrade";
                price = Integer.toString(Profile.BOX_PRICE);
                canAfford = pr.coins >= Profile.BOX_PRICE;
                break;
            case B_MEGA:
                title = "MEGA BOX";
                color = 0xffffa62e;
                sub = "Upgrade + coins!";
                price = Integer.toString(Profile.MEGA_BOX_PRICE);
                canAfford = pr.coins >= Profile.MEGA_BOX_PRICE;
                break;
            case B_DEAL: {
                int cost = dealPrice();
                title = "DAILY DEAL";
                color = 0xffff5ab5;
                sub = cost < 0 ? "Hero maxed" : "Upgrade  -40%";
                price = cost < 0 ? "SOLD OUT" : Integer.toString(cost);
                canAfford = cost >= 0 && pr.coins >= cost;
                break;
            }
            default:
                title = "COINS";
                color = 0xffffc928;
                sub = "How to earn coins";
                price = "INFO";
                break;
        }
        ui.panel(g, l, t, r, b, 0xff262a54);
        g.color(MathUtil.darker(color, 0.3f));
        g.fillRoundRect(l, t, r, t + 90 * u, 26 * u);
        g.color(color);
        g.fillRoundRect(l, t, r, t + 80 * u, 26 * u);
        g.color(0xffffffff);
        g.text(title, cx, t + 58 * u, Ui.fit(g, title, 44 * u, (r - l) * 0.9f), Gfx.ALIGN_CENTER, 6 * u, Ui.INK);
        float artY = t + (b - t) * 0.45f;
        float glow = 0.5f + 0.5f * MathUtil.sin(game.clock * 3f + bt.id);
        g.color(MathUtil.withAlpha(color, 0.18f + 0.12f * glow));
        g.fillCircle(cx, artY, Math.min(r - l, b - t) * 0.36f);
        switch (bt.id) {
            case B_GIFT:
                drawGift(g, cx, artY, (r - l) * 0.42f, pr.giftReady());
                break;
            case B_BOX:
            case B_MEGA:
                drawBox(g, cx, artY, (r - l) * 0.45f, bt.id == B_MEGA, game.clock, u);
                break;
            case B_DEAL:
                HeroArt.draw(g, cx, artY + (r - l) * 0.05f, (r - l) * 0.1f, game.clock * 0.8f, 0, 1f, 0, false);
                break;
            default:
                for (int k = 0; k < 3; k++)
                    ui.coin(g, cx - (r - l) * 0.18f + k * (r - l) * 0.18f, artY + MathUtil.sin(game.clock * 4 + k) * 8 * u, (r - l) * 0.28f);
                break;
        }
        g.color(0xffd8dcff);
        g.text(sub, cx, b - 130 * u, Ui.fit(g, sub, 30 * u, (r - l) * 0.9f), Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
        int plate = bt.id == B_GIFT ? (pr.giftReady() ? 0xff4ad04a : 0xff6a6a7a)
                : bt.id == B_COINS ? 0xff3fa0ff : (canAfford ? 0xffffc928 : 0xff8a6a3a);
        g.color(Ui.INK);
        g.fillRoundRect(l + 20 * u, b - 96 * u, r - 20 * u, b - 18 * u, 22 * u);
        g.color(plate);
        g.fillRoundRect(l + 26 * u, b - 90 * u, r - 26 * u, b - 24 * u, 18 * u);
        boolean numeric = Character.isDigit(price.charAt(0));
        g.color(0xffffffff);
        float ps = Ui.fit(g, price, 42 * u, (r - l) * 0.6f);
        if (numeric) {
            float tw = g.measureText(price, ps);
            ui.coin(g, cx - tw / 2 - 18 * u, b - 57 * u, 42 * u);
            g.text(price, cx - tw / 2 + 10 * u, b - 41 * u, ps, Gfx.ALIGN_LEFT, 5 * u, Ui.INK);
        } else {
            g.text(price, cx, b - 41 * u, ps, Gfx.ALIGN_CENTER, 5 * u, Ui.INK);
        }
    }

    private void drawGift(Gfx g, float x, float y, float s, boolean ready) {
        float bob = ready ? MathUtil.sin(game.clock * 5f) * s * 0.04f : 0;
        y += bob;
        g.color(Ui.INK);
        g.fillRoundRect(x - s * 0.5f, y - s * 0.2f, x + s * 0.5f, y + s * 0.5f, s * 0.08f);
        g.fillRoundRect(x - s * 0.58f, y - s * 0.42f, x + s * 0.58f, y - s * 0.12f, s * 0.08f);
        g.color(ready ? 0xff3fd0ff : 0xff6a7a8a);
        g.fillRoundRect(x - s * 0.44f, y - s * 0.14f, x + s * 0.44f, y + s * 0.44f, s * 0.06f);
        g.fillRoundRect(x - s * 0.52f, y - s * 0.36f, x + s * 0.52f, y - s * 0.18f, s * 0.06f);
        g.color(ready ? 0xffff4a6a : 0xff9a9aaa);
        g.fillRect(x - s * 0.08f, y - s * 0.36f, x + s * 0.08f, y + s * 0.44f);
        g.strokeCircle(x - s * 0.17f, y - s * 0.5f, s * 0.15f, s * 0.08f);
        g.strokeCircle(x + s * 0.17f, y - s * 0.5f, s * 0.15f, s * 0.08f);
    }

    /** Treasure box art shared with the reward popup. */
    static void drawBox(Gfx g, float x, float y, float s, boolean mega, float clock, float u) {
        int body = mega ? 0xffffa62e : 0xff8a5aff, dark = mega ? 0xffc0701a : 0xff5a2bd1, trim = mega ? 0xfffff2a8 : 0xffffd23f;
        float wob = MathUtil.sin(clock * 6f) * 2f;
        g.save();
        g.translate(x, y);
        g.rotate(wob);
        g.color(0x55000000);
        g.fillRoundRect(-s * 0.5f, s * 0.38f, s * 0.5f, s * 0.5f, s * 0.06f);
        g.color(Ui.INK);
        g.fillRoundRect(-s * 0.56f, -s * 0.2f, s * 0.56f, s * 0.46f, s * 0.1f);
        g.fillRoundRect(-s * 0.6f, -s * 0.5f, s * 0.6f, -s * 0.08f, s * 0.2f);
        g.color(dark);
        g.fillRoundRect(-s * 0.5f, -s * 0.14f, s * 0.5f, s * 0.4f, s * 0.07f);
        g.color(body);
        g.fillRoundRect(-s * 0.5f, -s * 0.14f, s * 0.5f, s * 0.28f, s * 0.07f);
        g.fillRoundRect(-s * 0.54f, -s * 0.44f, s * 0.54f, -s * 0.13f, s * 0.16f);
        g.color(trim);
        g.fillRect(-s * 0.36f, -s * 0.44f, -s * 0.24f, s * 0.4f);
        g.fillRect(s * 0.24f, -s * 0.44f, s * 0.36f, s * 0.4f);
        g.color(Ui.INK);
        g.fillRoundRect(-s * 0.12f, -s * 0.22f, s * 0.12f, s * 0.06f, s * 0.04f);
        g.color(trim);
        g.fillRoundRect(-s * 0.08f, -s * 0.18f, s * 0.08f, s * 0.02f, s * 0.03f);
        g.color(0x55ffffff);
        g.fillRoundRect(-s * 0.46f, -s * 0.4f, -s * 0.06f, -s * 0.3f, s * 0.05f);
        if (mega) {
            float sp = 0.5f + 0.5f * MathUtil.sin(clock * 7f);
            g.color(MathUtil.withAlpha(0xffffffff, sp));
            Icons.bolt(g, s * 0.42f, -s * 0.55f, s * 0.3f, MathUtil.withAlpha(0xffffffff, sp));
        }
        g.restore();
    }

    private String settingValue(int k) {
        switch (k) {
            case 0:
                return pr.sound ? "ON" : "OFF";
            case 1:
                return pr.vibration ? "ON" : "OFF";
            case 2:
                return pr.damageNumbers ? "ON" : "OFF";
            case 3:
                return pr.lowGraphics ? "LOW" : "HIGH";
            case 4:
                return pr.autoAim ? "ON" : "OFF";
            case 5:
                return pr.leftHanded ? "ON" : "OFF";
            case 6:
                return pr.stickSize == 0 ? "SMALL" : (pr.stickSize == 1 ? "MEDIUM" : "LARGE");
            default:
                return pr.camera == 0 ? "CLOSE" : (pr.camera == 1 ? "NORMAL" : "FAR");
        }
    }

    private void toggleSetting(int k) {
        switch (k) {
            case 0:
                pr.sound = !pr.sound;
                break;
            case 1:
                pr.vibration = !pr.vibration;
                break;
            case 2:
                pr.damageNumbers = !pr.damageNumbers;
                break;
            case 3:
                pr.lowGraphics = !pr.lowGraphics;
                game.demo.lowGraphics = pr.lowGraphics;
                game.demo.fx.low = pr.lowGraphics;
                break;
            case 4:
                pr.autoAim = !pr.autoAim;
                break;
            case 5:
                pr.leftHanded = !pr.leftHanded;
                break;
            case 6:
                pr.stickSize = (pr.stickSize + 1) % 3;
                break;
            default:
                pr.camera = (pr.camera + 1) % 3;
                break;
        }
        pr.save();
        game.layout();
        if (k == 1 && pr.vibration) game.gated.vibrate(40);
    }

    private void renderSettings(Gfx g) {
        float u = game.u, w = game.w, h = game.h;
        for (int i = 0; i < ui.count; i++) {
            Ui.Btn bt = ui.btns[i];
            if (bt.id >= B_SETTING && bt.id < B_SETTING + SETTING_NAMES.length) {
                int k = bt.id - B_SETTING;
                float colW = (w - game.padL - game.padR - 100 * u) / 2f;
                float l = bt.r + 20 * u - colW;
                g.color(0xcc22264a);
                g.fillRoundRect(l, bt.t - 14 * u, bt.r + 20 * u, bt.b + 14 * u, 24 * u);
                g.color(0xffffffff);
                g.text(SETTING_NAMES[k], l + 30 * u, (bt.t + bt.b) / 2 + 16 * u, 44 * u, Gfx.ALIGN_LEFT, 5 * u, Ui.INK);
            }
            ui.button(g, bt);
        }
        g.color(0xff9aa0d0);
        g.text("Snake Brawl v1.2  •  " + pr.games + " games  •  " + pr.totalKills + " knockouts  •  best " + pr.bestTrophies + " trophies",
                w / 2, h - game.padB - 160 * u, 30 * u, Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
    }
}
