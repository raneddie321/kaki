package com.snakebrawl.myapp.game;

/** The brawler roster, the shop and the settings screen. */
final class MetaScreens {
    private static final int B_TILE = 100, B_SELECT = 150, B_UPGRADE = 151, B_UNLOCK = 152;
    private static final int B_SKIN = 200;
    private static final int B_TAB = 300, B_GIFT = 310, B_BOX = 311, B_MEGA = 312, B_DEAL = 313, B_SHOP_BRAWLER = 330;
    private static final int B_PACK = 350;
    private static final int B_SETTING = 400, B_RESET = 420, B_PRIVACY = 421;

    /** The COINS tab only exists while the real-money store is enabled. */
    private static int tabCount() {
        return Game.coinStoreEnabled ? TABS.length : TABS.length - 1;
    }

    /** Short in-app privacy notice (the full policy is on the store page). */
    static final String PRIVACY_TEXT = "No account, ads or tracking. Your nickname, age and progress stay on this device; "
            + "RESET PROGRESS deletes them. Playing with a friend sends your nickname and game moves to their device. "
            + "Online play (16+) connects through the PeerJS server, which sees your IP address. "
            + "Coin purchases are handled by Google Play; we never see payment details. "
            + "Questions: raneddie321@gmail.com";

    private static final String[] TABS = {"OFFERS", "SKINS", "BRAWLERS", "COINS"};
    private static final String[] SETTING_NAMES = {"Sound", "Vibration", "Damage numbers", "Graphics",
            "Auto-aim on tap", "Left-handed controls", "Joystick size", "Camera"};

    private final Game game;
    private final Profile pr;
    private final Ui ui;
    int viewBrawler;
    private int shopTab;

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
                float top = padT + 140 * u, bottom = h - padB - 30 * u;
                float gridR = padL + 20 * u + (w - padL - padR - 40 * u) * 0.6f;
                float gap = 22 * u;
                int rows = (Brawler.ALL.length + 3) / 4;
                float tw = (gridR - padL - 20 * u - gap * 3) / 4f, th = (bottom - top - gap * (rows - 1)) / rows;
                for (int i = 0; i < Brawler.ALL.length; i++) {
                    float l = padL + 20 * u + (i % 4) * (tw + gap), t = top + (i / 4) * (th + gap);
                    ui.add(B_TILE + i, l, t, l + tw, t + th, null, null, 0);
                }
                float pl = gridR + 40 * u, pr2 = w - padR - 20 * u;
                Brawler b = Brawler.ALL[viewBrawler];
                float by = bottom;
                if (!pr.isUnlocked(b.id)) {
                    ui.add(B_UNLOCK, pl + 20 * u, by - 120 * u, pr2 - 20 * u, by, "UNLOCK", b.price + " coins", 0xffffc928);
                } else {
                    float mid = (pl + pr2) / 2;
                    boolean inUse = pr.selected == b.id;
                    Ui.Btn sel = ui.add(B_SELECT, pl + 20 * u, by - 120 * u, mid - 12 * u, by,
                            inUse ? "IN USE" : "SELECT", null, inUse ? 0xff3a8a3a : 0xff4ad04a);
                    sel.enabled = !inUse;
                    int cost = pr.upgradeCost(b.id);
                    Ui.Btn up = ui.add(B_UPGRADE, mid + 12 * u, by - 120 * u, pr2 - 20 * u, by,
                            cost < 0 ? "MAXED" : "UPGRADE", cost < 0 ? null : cost + " coins", 0xffb35cff);
                    up.enabled = cost >= 0;
                }
                break;
            }
            case Game.SHOP: {
                float tabW = 270 * u, tabT = padT + 130 * u;
                float tabL = w / 2 - (tabW * tabCount() + 20 * u * (tabCount() - 1)) / 2;
                for (int i = 0; i < tabCount(); i++) {
                    float l = tabL + i * (tabW + 20 * u);
                    ui.add(B_TAB + i, l, tabT, l + tabW, tabT + 90 * u, TABS[i], null, i == shopTab ? 0xffffc928 : 0xff4a5090);
                }
                float top = tabT + 120 * u, bottom = h - padB - 20 * u;
                float l = padL + 20 * u, r = w - padR - 20 * u;
                if (shopTab == 0) {
                    ui.setScrollArea(l, top, r, bottom, 0);
                    float gap = 26 * u, cw = (r - l - gap * 3) / 4f;
                    int[] ids = {B_GIFT, B_BOX, B_MEGA, B_DEAL};
                    for (int i = 0; i < 4; i++) {
                        float cl = l + i * (cw + gap);
                        ui.add(ids[i], cl, top + 10 * u, cl + cw, bottom - 10 * u, null, null, 0);
                    }
                } else if (shopTab == 1) {
                    int cols = 5;
                    float gap = 22 * u, cw = (r - l - gap * (cols - 1)) / cols, ch = 330 * u;
                    int n = Skin.ALL.length;
                    int rows = (n + cols - 1) / cols;
                    ui.setScrollArea(l - 10 * u, top, r + 10 * u, bottom, rows * (ch + gap) + 20 * u);
                    for (int i = 0; i < n; i++) {
                        float cl = l + (i % cols) * (cw + gap), ct = top + 10 * u + (i / cols) * (ch + gap);
                        ui.addScroll(B_SKIN + i, cl, ct, cl + cw, ct + ch, null, null, 0);
                    }
                } else if (shopTab == 2) {
                    // Every brawler that is not free, four per row; scrolls when there are more
                    int first = 4, n = Brawler.ALL.length - first;
                    float gap = 26 * u, cw = (r - l - gap * 3) / 4f, ch = Math.min(bottom - top - 20 * u, 560 * u);
                    int rows = (n + 3) / 4;
                    ui.setScrollArea(l - 10 * u, top, r + 10 * u, bottom, rows * (ch + gap) + 20 * u);
                    for (int i = 0; i < n; i++) {
                        float cl = l + (i % 4) * (cw + gap), ct = top + 10 * u + (i / 4) * (ch + gap);
                        ui.addScroll(B_SHOP_BRAWLER + first + i, cl, ct, cl + cw, ct + ch, null, null, 0);
                    }
                } else {
                    ui.setScrollArea(l, top, r, bottom, 0);
                    int n = CoinStore.PRODUCT_IDS.length;
                    float gap = 22 * u, cw = (r - l - gap * (n - 1)) / n;
                    for (int i = 0; i < n; i++) {
                        float cl = l + i * (cw + gap);
                        ui.add(B_PACK + i, cl, top + 10 * u, cl + cw, bottom - 10 * u, null, null, 0);
                    }
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
                ui.add(B_RESET, w / 2 + 20 * u, rb - 100 * u, w / 2 + 500 * u, rb, "RESET PROGRESS", null, 0xffff5a5a);
                ui.add(B_PRIVACY, w / 2 - 500 * u, rb - 100 * u, w / 2 - 20 * u, rb, "PRIVACY", null, 0xff3fa0ff);
                break;
            }
            default:
                break;
        }
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

    // ------------------------------------------------------------------ input

    void onButton(int id) {
        if (id >= B_TILE && id < B_TILE + Brawler.ALL.length) {
            viewBrawler = id - B_TILE;
            game.layout();
            return;
        }
        if (id >= B_SKIN && id < B_SKIN + Skin.ALL.length) {
            int s = id - B_SKIN;
            Skin sk = Skin.ALL[s];
            if (pr.ownsSkin(s)) {
                pr.skin = s;
                pr.save();
                game.gated.playSound(Platform.SND_POWER, 0.6f);
            } else if (pr.coins < sk.price) {
                notEnough(sk.price);
            } else {
                game.confirm("BUY SKIN?", "Buy the " + sk.name + " skin for " + sk.price + " coins?", 2, s, Game.ACT_BUY_SKIN, s);
            }
            return;
        }
        if (id >= B_TAB && id < B_TAB + tabCount()) {
            shopTab = id - B_TAB;
            ui.resetScroll();
            game.layout();
            return;
        }
        if (id >= B_PACK && id < B_PACK + CoinStore.PRODUCT_IDS.length) {
            game.startPurchase(id - B_PACK);
            return;
        }
        if (id >= B_SHOP_BRAWLER && id < B_SHOP_BRAWLER + Brawler.ALL.length) {
            int b = id - B_SHOP_BRAWLER;
            if (pr.isUnlocked(b)) {
                viewBrawler = b;
                game.setScreen(Game.BRAWLERS);
            } else {
                askUnlock(b);
            }
            return;
        }
        if (id >= B_SETTING && id < B_SETTING + SETTING_NAMES.length) {
            toggleSetting(id - B_SETTING);
            return;
        }
        Brawler vb = Brawler.ALL[viewBrawler];
        switch (id) {
            case B_SELECT:
                pr.selected = vb.id;
                pr.save();
                game.gated.playSound(Platform.SND_POWER, 0.6f);
                game.layout();
                break;
            case B_UNLOCK:
                askUnlock(vb.id);
                break;
            case B_UPGRADE: {
                int cost = pr.upgradeCost(vb.id);
                if (cost < 0) break;
                if (pr.coins < cost) notEnough(cost);
                else game.confirm("UPGRADE " + vb.name + "?", "Power level " + pr.levels[vb.id] + " to " + (pr.levels[vb.id] + 1)
                        + ": +6% health and damage. Costs " + cost + " coins.", 3, vb.id, Game.ACT_UPGRADE, vb.id);
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
                        + " coins? It can hold coins, a skin or a free upgrade.", 4, 0, Game.ACT_BOX, 0);
                break;
            case B_MEGA:
                if (pr.coins < Profile.MEGA_BOX_PRICE) notEnough(Profile.MEGA_BOX_PRICE);
                else game.confirm("MEGA BOX", "Open a Mega Box for " + Profile.MEGA_BOX_PRICE
                        + " coins? A guaranteed new skin plus bonus coins!", 4, 1, Game.ACT_MEGA_BOX, 0);
                break;
            case B_DEAL: {
                int s = dealSkin();
                if (pr.ownsSkin(s)) {
                    game.showInfo("SOLD OUT", "You already own today's deal. A new deal arrives tomorrow!", 2, s);
                } else if (pr.coins < dealPrice(s)) {
                    notEnough(dealPrice(s));
                } else {
                    game.confirm("DAILY DEAL", "Get the " + Skin.ALL[s].name + " skin for only " + dealPrice(s) + " coins?", 2, s,
                            Game.ACT_DEAL, s);
                }
                break;
            }
            case B_PRIVACY:
                game.showInfo("PRIVACY", PRIVACY_TEXT, 0, 0);
                return;
            case B_RESET:
                game.confirm("RESET PROGRESS?", "This deletes your trophies, coins, skins, unlocked brawlers and upgrades. "
                        + "It cannot be undone!", 0, 0, Game.ACT_RESET, 0);
                break;
            default:
                break;
        }
    }

    private void askUnlock(int b) {
        Brawler br = Brawler.ALL[b];
        if (pr.coins < br.price) notEnough(br.price);
        else game.confirm("UNLOCK " + br.name + "?", "Unlock the " + Brawler.RARITY_NAMES[br.rarity].toLowerCase() + " "
                + br.role.toLowerCase() + " " + br.name + " for " + br.price + " coins?", 3, b, Game.ACT_UNLOCK, b);
    }

    private void notEnough(int price) {
        game.showInfo("NOT ENOUGH COINS", "You need " + (price - pr.coins) + " more coins. Play matches, get knockouts "
                + "and claim free gifts to earn coins!", 1, 0);
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

    /** Runs a confirmed popup action. */
    void perform(int action, int arg) {
        switch (action) {
            case Game.ACT_BUY_SKIN:
            case Game.ACT_DEAL: {
                Skin s = Skin.ALL[arg];
                int price = action == Game.ACT_DEAL ? dealPrice(arg) : s.price;
                if (pr.ownsSkin(arg) || !pr.spend(price)) return;
                pr.ownedSkins |= 1 << arg;
                pr.skin = arg;
                pr.save();
                game.gated.playSound(Platform.SND_KILL, 1f);
                game.showInfo("NEW SKIN!", s.name + " is now equipped on all your brawlers.", 2, arg);
                break;
            }
            case Game.ACT_UNLOCK: {
                Brawler b = Brawler.ALL[arg];
                if (pr.isUnlocked(arg) || !pr.spend(b.price)) return;
                pr.unlocked |= 1 << arg;
                pr.selected = arg;
                viewBrawler = arg;
                pr.save();
                game.gated.playSound(Platform.SND_VICTORY, 0.8f);
                game.showInfo(b.name + " UNLOCKED!", b.name + " is ready to brawl! Super: " + b.superName + " - " + b.superDesc, 3, arg);
                break;
            }
            case Game.ACT_UPGRADE: {
                int cost = pr.upgradeCost(arg);
                if (cost < 0 || !pr.spend(cost)) return;
                pr.levels[arg]++;
                pr.save();
                game.gated.playSound(Platform.SND_POWER, 1f);
                game.showInfo("POWER LEVEL " + pr.levels[arg] + "!", Brawler.ALL[arg].name + " got stronger: +6% health and damage.", 3, arg);
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
                viewBrawler = 0;
                game.showInfo("PROGRESS RESET", "Fresh start! You have " + pr.coins + " coins.", 0, 0);
                break;
            default:
                break;
        }
        game.layout();
    }

    void openBox(boolean mega) {
        game.gated.playSound(Platform.SND_BOX, 1f);
        if (mega) {
            int s = randomUnownedSkin(Integer.MAX_VALUE);
            int coins = 150 + MathUtil.randInt(21) * 10;
            pr.coins += coins;
            if (s > 0) {
                pr.ownedSkins |= 1 << s;
                pr.save();
                game.showInfo("MEGA BOX!", "New skin: " + Skin.ALL[s].name + "! Plus " + coins + " coins. Equip it in the shop.", 2, s);
            } else {
                pr.coins += 400;
                pr.save();
                game.showInfo("MEGA BOX!", "You own every skin, so you get " + (coins + 400) + " coins!", 1, 0);
            }
            return;
        }
        float r = MathUtil.rand();
        if (r < 0.3f) {
            int s = randomUnownedSkin(900);
            if (s > 0) {
                pr.ownedSkins |= 1 << s;
                pr.save();
                game.showInfo("BRAWL BOX!", "New skin: " + Skin.ALL[s].name + "! Equip it in the shop.", 2, s);
                return;
            }
        } else if (r < 0.45f) {
            int count = 0;
            for (int i = 0; i < Brawler.ALL.length; i++) if (pr.isUnlocked(i) && pr.levels[i] < Brawler.MAX_LEVEL) count++;
            if (count > 0) {
                int pick = MathUtil.randInt(count);
                for (int i = 0; i < Brawler.ALL.length; i++) {
                    if (pr.isUnlocked(i) && pr.levels[i] < Brawler.MAX_LEVEL && pick-- == 0) {
                        pr.levels[i]++;
                        pr.save();
                        game.showInfo("BRAWL BOX!", "Free upgrade! " + Brawler.ALL[i].name + " is now power level " + pr.levels[i] + ".", 3, i);
                        return;
                    }
                }
            }
        }
        int coins = 60 + MathUtil.randInt(17) * 10;
        pr.coins += coins;
        pr.save();
        game.showInfo("BRAWL BOX!", "You found " + coins + " coins!", 1, 0);
    }

    private int randomUnownedSkin(int maxPrice) {
        int count = 0;
        for (int i = 1; i < Skin.ALL.length; i++) if (!pr.ownsSkin(i) && Skin.ALL[i].price <= maxPrice) count++;
        if (count == 0) return -1;
        int pick = MathUtil.randInt(count);
        for (int i = 1; i < Skin.ALL.length; i++) if (!pr.ownsSkin(i) && Skin.ALL[i].price <= maxPrice && pick-- == 0) return i;
        return -1;
    }

    private static int dealSkin() {
        int day = Profile.nowMinute() / 1440;
        return 1 + (day * 7 + 3) % (Skin.ALL.length - 1);
    }

    private static int dealPrice(int s) {
        return Math.round(Skin.ALL[s].price * 0.6f / 10f) * 10;
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
        String title = game.screen == Game.BRAWLERS ? "BRAWLERS" : (game.screen == Game.SHOP ? "SHOP" : "SETTINGS");
        g.color(0xffffd23f);
        g.text(title, w / 2, game.padT + 92 * u, 84 * u, Gfx.ALIGN_CENTER, 9 * u, Ui.INK);
        if (game.screen != Game.SETTINGS) ui.coinPill(g, w - game.padR - 20 * u, game.padT + 14 * u, pr.coins);
        switch (game.screen) {
            case Game.BRAWLERS:
                renderBrawlers(g);
                break;
            case Game.SHOP:
                renderShop(g);
                break;
            default:
                renderSettings(g);
                break;
        }
    }

    private void renderBrawlers(Gfx g) {
        float u = game.u;
        for (int i = 0; i < ui.count; i++) {
            Ui.Btn bt = ui.btns[i];
            if (bt.id < B_TILE || bt.id >= B_TILE + Brawler.ALL.length) {
                ui.button(g, bt);
                continue;
            }
            int bi = bt.id - B_TILE;
            Brawler b = Brawler.ALL[bi];
            boolean unlocked = pr.isUnlocked(bi);
            float l = bt.l, t = bt.t, r = bt.r, bb = bt.b;
            if (ui.pressed == bt.id) {
                l += 6 * u;
                r -= 6 * u;
                t += 6 * u;
                bb -= 6 * u;
            }
            if (bi == viewBrawler) {
                float glow = 0.5f + 0.5f * MathUtil.sin(game.clock * 5f);
                g.color(MathUtil.withAlpha(0xffffd23f, 0.5f + 0.4f * glow));
                g.fillRoundRect(l - 12 * u, t - 12 * u, r + 12 * u, bb + 12 * u, 36 * u);
            }
            ui.panel(g, l, t, r, bb, unlocked ? 0xff262a54 : 0xff1c1e3a);
            int band = Brawler.RARITY_COLORS[b.rarity];
            g.color(MathUtil.darker(band, 0.3f));
            g.fillRoundRect(l, t, r, t + 74 * u, 26 * u);
            g.color(band);
            g.fillRoundRect(l, t, r, t + 64 * u, 26 * u);
            g.color(0xffffffff);
            g.text(b.name, (l + r) / 2, t + 50 * u, Ui.fit(g, b.name, 44 * u, (r - l) * 0.9f), Gfx.ALIGN_CENTER, 6 * u, Ui.INK);
            float scale = Math.min(0.95f * u, (r - l) / 320f);
            int[] pal = pr.selected == bi ? pr.palette() : new int[]{b.color1, b.color2};
            ui.snakeArt(g, b, pal, (l + r) / 2, (t + bb) / 2 - 10 * u, scale, game.clock + bi);
            if (!unlocked) {
                g.color(0x880d1030);
                g.fillRoundRect(l, t + 74 * u, r, bb, 26 * u);
                ui.lock(g, (l + r) / 2, (t + bb) / 2 - 10 * u, 70 * u);
                ui.coin(g, (l + r) / 2 - 50 * u, bb - 40 * u, 40 * u);
                g.color(0xffffe066);
                g.text(Integer.toString(b.price), (l + r) / 2 - 22 * u, bb - 26 * u, 40 * u, Gfx.ALIGN_LEFT, 5 * u, Ui.INK);
            } else {
                g.color(0xffd8dcff);
                g.text(b.role.toUpperCase(), (l + r) / 2, bb - 70 * u, Ui.fit(g, b.role.toUpperCase(), 28 * u, (r - l) * 0.9f),
                        Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
                g.color(0xffb35cff);
                g.fillRoundRect((l + r) / 2 - 70 * u, bb - 52 * u, (l + r) / 2 + 70 * u, bb - 14 * u, 18 * u);
                g.color(0xffffffff);
                g.text("LVL " + pr.levels[bi], (l + r) / 2, bb - 22 * u, 30 * u, Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
                if (pr.selected == bi) {
                    g.color(0xff4ad04a);
                    g.fillCircle(r - 26 * u, t + 100 * u, 22 * u);
                    g.color(0xffffffff);
                    g.line(r - 36 * u, t + 100 * u, r - 28 * u, t + 109 * u, 6 * u);
                    g.line(r - 28 * u, t + 109 * u, r - 14 * u, t + 90 * u, 6 * u);
                }
            }
        }
        renderBrawlerDetail(g);
    }

    private void renderBrawlerDetail(Gfx g) {
        float u = game.u, w = game.w, h = game.h;
        Ui.Btn first = ui.find(B_TILE);
        if (first == null) return;
        float gridR = game.padL + 20 * u + (w - game.padL - game.padR - 40 * u) * 0.6f;
        float l = gridR + 40 * u, r = w - game.padR - 20 * u, t = first.t, b = h - game.padB - 175 * u;
        Brawler br = Brawler.ALL[viewBrawler];
        ui.panel(g, l, t, r, b, 0xee22264a);
        float cx = (l + r) / 2;
        g.color(br.color1);
        g.text(br.name, l + 30 * u, t + 70 * u, 64 * u, Gfx.ALIGN_LEFT, 7 * u, Ui.INK);
        g.color(Brawler.RARITY_COLORS[br.rarity]);
        g.text(Brawler.RARITY_NAMES[br.rarity], r - 30 * u, t + 50 * u, 30 * u, Gfx.ALIGN_RIGHT, 4 * u, Ui.INK);
        g.color(0xffd8dcff);
        g.text(br.role.toUpperCase(), r - 30 * u, t + 86 * u, 28 * u, Gfx.ALIGN_RIGHT, 4 * u, Ui.INK);
        int[] pal = pr.selected == br.id ? pr.palette() : new int[]{br.color1, br.color2};
        ui.snakeArt(g, br, pal, cx, t + 165 * u, Math.min(1.2f * u, (r - l) / 380f), game.clock);

        int lvl = pr.levels[br.id];
        float y = t + 250 * u;
        g.color(0xffffffff);
        g.text("POWER " + lvl + "/" + Brawler.MAX_LEVEL, l + 30 * u, y, 32 * u, Gfx.ALIGN_LEFT, 4 * u, Ui.INK);
        float px0 = l + 240 * u, pw = (r - 30 * u - px0) / Brawler.MAX_LEVEL;
        for (int k = 0; k < Brawler.MAX_LEVEL; k++) {
            g.color(Ui.INK);
            g.fillRoundRect(px0 + k * pw, y - 26 * u, px0 + (k + 1) * pw - 6 * u, y + 2 * u, 6 * u);
            g.color(k < lvl ? 0xffb35cff : 0xff3a3e70);
            g.fillRoundRect(px0 + k * pw + 3 * u, y - 23 * u, px0 + (k + 1) * pw - 9 * u, y - 1 * u, 4 * u);
        }
        float mult = 1f + 0.06f * (lvl - 1);
        y += 50 * u;
        String[] labels = {"HEALTH", "DAMAGE", "RANGE", "SPEED"};
        int[] stars = {br.statHp, br.statDamage, br.statRange, br.statSpeed};
        String[] vals = {Integer.toString(Math.round(br.hp * 2 * mult)), Integer.toString(Math.round(br.damage * mult)),
                null, null};
        for (int k = 0; k < 4; k++) {
            g.color(0xffb8bdf0);
            g.text(labels[k], l + 30 * u, y, 26 * u, Gfx.ALIGN_LEFT, 4 * u, Ui.INK);
            float sx0 = l + 170 * u, sw = 34 * u;
            for (int p = 0; p < 5; p++) {
                g.color(Ui.INK);
                g.fillRoundRect(sx0 + p * sw, y - 22 * u, sx0 + p * sw + sw - 6 * u, y, 5 * u);
                g.color(p < stars[k] ? 0xffffc928 : 0xff3a3e70);
                g.fillRoundRect(sx0 + p * sw + 3 * u, y - 19 * u, sx0 + p * sw + sw - 9 * u, y - 3 * u, 3 * u);
            }
            if (vals[k] != null) {
                g.color(0xffffffff);
                g.text(vals[k], r - 30 * u, y, 30 * u, Gfx.ALIGN_RIGHT, 4 * u, Ui.INK);
            }
            y += 40 * u;
        }
        y += 6 * u;
        float limit = b - 20 * u;
        if (y + 30 * u < limit) {
            g.color(0xffff9a4a);
            g.text("ATTACK: " + br.attackName, l + 30 * u, y, 28 * u, Gfx.ALIGN_LEFT, 4 * u, Ui.INK);
            y = ui.wrap(g, br.attackDesc, l + 30 * u, r - 30 * u, y + 4 * u, 25 * u, 0xffe8eaff, limit);
        }
        y += 14 * u;
        if (y + 30 * u < limit) {
            g.color(0xffffd23f);
            g.text("SUPER: " + br.superName, l + 30 * u, y + 20 * u, 28 * u, Gfx.ALIGN_LEFT, 4 * u, Ui.INK);
            ui.wrap(g, br.superDesc, l + 30 * u, r - 30 * u, y + 24 * u, 25 * u, 0xffe8eaff, limit);
        }
    }

    private void renderShop(Gfx g) {
        float u = game.u;
        for (int i = 0; i < ui.count; i++) {
            Ui.Btn bt = ui.btns[i];
            if (bt.id == Game.B_BACK || (bt.id >= B_TAB && bt.id < B_TAB + tabCount())) ui.button(g, bt);
        }
        if (shopTab == 0) {
            for (int i = 0; i < ui.count; i++) {
                Ui.Btn bt = ui.btns[i];
                if (bt.id >= B_GIFT && bt.id <= B_DEAL) drawOffer(g, bt);
            }
        } else if (shopTab == 3) {
            for (int i = 0; i < ui.count; i++) {
                Ui.Btn bt = ui.btns[i];
                if (bt.id >= B_PACK && bt.id < B_PACK + CoinStore.PRODUCT_IDS.length) drawCoinPack(g, bt);
            }
        } else {
            g.save();
            g.clip(ui.viewL, ui.viewT, ui.viewR, ui.viewB);
            g.translate(0, -ui.scrollY);
            for (int i = 0; i < ui.count; i++) {
                Ui.Btn bt = ui.btns[i];
                if (!bt.scrolls) continue;
                if (bt.b - ui.scrollY < ui.viewT - 20 * u || bt.t - ui.scrollY > ui.viewB + 20 * u) continue;
                if (shopTab == 2) drawBrawlerOffer(g, bt);
                else drawSkinTile(g, bt);
            }
            g.restore();
            if (ui.scrollMax > 0) {
                // Scroll bar
                float trackT = ui.viewT + 10 * u, trackB = ui.viewB - 10 * u;
                float frac = (ui.viewB - ui.viewT) / (ui.viewB - ui.viewT + ui.scrollMax);
                float barH = (trackB - trackT) * frac;
                float by = trackT + (trackB - trackT - barH) * (ui.scrollY / ui.scrollMax);
                g.color(0x55ffffff);
                g.fillRoundRect(ui.viewR + 2 * u, by, ui.viewR + 12 * u, by + barH, 5 * u);
            }
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
                sub = "Coins, skins or upgrades";
                price = Integer.toString(Profile.BOX_PRICE);
                canAfford = pr.coins >= Profile.BOX_PRICE;
                break;
            case B_MEGA:
                title = "MEGA BOX";
                color = 0xffffa62e;
                sub = "Guaranteed new skin!";
                price = Integer.toString(Profile.MEGA_BOX_PRICE);
                canAfford = pr.coins >= Profile.MEGA_BOX_PRICE;
                break;
            default: {
                int s = dealSkin();
                title = "DAILY DEAL";
                color = 0xffff5ab5;
                sub = Skin.ALL[s].name + "  -40%";
                price = pr.ownsSkin(s) ? "OWNED" : Integer.toString(dealPrice(s));
                canAfford = pr.coins >= dealPrice(s);
                break;
            }
        }
        ui.panel(g, l, t, r, b, 0xff262a54);
        g.color(MathUtil.darker(color, 0.3f));
        g.fillRoundRect(l, t, r, t + 90 * u, 26 * u);
        g.color(color);
        g.fillRoundRect(l, t, r, t + 80 * u, 26 * u);
        g.color(0xffffffff);
        g.text(title, cx, t + 58 * u, Ui.fit(g, title, 46 * u, (r - l) * 0.9f), Gfx.ALIGN_CENTER, 6 * u, Ui.INK);
        float artY = t + (b - t) * 0.45f;
        float glow = 0.5f + 0.5f * MathUtil.sin(game.clock * 3f + bt.id);
        g.color(MathUtil.withAlpha(color, 0.18f + 0.12f * glow));
        g.fillCircle(cx, artY, Math.min(r - l, b - t) * 0.33f);
        switch (bt.id) {
            case B_GIFT:
                drawGift(g, cx, artY, (r - l) * 0.42f, pr.giftReady());
                break;
            case B_BOX:
            case B_MEGA:
                drawBox(g, cx, artY, (r - l) * 0.45f, bt.id == B_MEGA, game.clock, u);
                break;
            default: {
                int s = dealSkin();
                Brawler br = Brawler.ALL[pr.selected];
                ui.snakeArt(g, br, Skin.ALL[s].paletteFor(br), cx, artY, Math.min(1.1f * u, (r - l) / 330f), game.clock);
                break;
            }
        }
        g.color(0xffd8dcff);
        g.text(sub, cx, b - 130 * u, Ui.fit(g, sub, 30 * u, (r - l) * 0.9f), Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
        // Price plate
        int plate = bt.id == B_GIFT ? (pr.giftReady() ? 0xff4ad04a : 0xff6a6a7a) : (canAfford ? 0xffffc928 : 0xff8a6a3a);
        g.color(Ui.INK);
        g.fillRoundRect(l + 24 * u, b - 96 * u, r - 24 * u, b - 18 * u, 22 * u);
        g.color(plate);
        g.fillRoundRect(l + 30 * u, b - 90 * u, r - 30 * u, b - 24 * u, 18 * u);
        boolean numeric = price.length() > 0 && Character.isDigit(price.charAt(0));
        g.color(0xffffffff);
        if (numeric) {
            float tw = g.measureText(price, 44 * u);
            ui.coin(g, cx - tw / 2 - 18 * u, b - 57 * u, 44 * u);
            g.text(price, cx - tw / 2 + 10 * u, b - 41 * u, 44 * u, Gfx.ALIGN_LEFT, 5 * u, Ui.INK);
        } else {
            g.text(price, cx, b - 41 * u, 44 * u, Gfx.ALIGN_CENTER, 5 * u, Ui.INK);
        }
    }

    private void drawCoinPack(Gfx g, Ui.Btn bt) {
        float u = game.u, d = press(bt);
        float l = bt.l + d, t = bt.t + d, r = bt.r - d, b = bt.b - d;
        int i = bt.id - B_PACK;
        float cx = (l + r) / 2;
        int[] tint = {0xff3fa0ff, 0xff4ad04a, 0xffb35cff, 0xffff9a2e, 0xffff4a6a};
        int col = tint[i];
        ui.panel(g, l, t, r, b, 0xff262a54);
        g.save();
        g.clip(l, t + 26 * u, r, b - 26 * u);
        g.vertical(l, t, r, b, MathUtil.withAlpha(col, 0.35f), 0x00262a54);
        g.restore();
        // Pile of coins that grows with the pack size
        float artY = t + (b - t) * 0.42f;
        float glow = 0.5f + 0.5f * MathUtil.sin(game.clock * 3f + i);
        g.radial(cx, artY, (r - l) * 0.5f, MathUtil.withAlpha(0xffffd23f, 0.35f + 0.15f * glow), 0x00ffd23f);
        int coins = 3 + i * 3;
        float cs = (r - l) * 0.26f;
        for (int k = 0; k < coins; k++) {
            int row = k < 5 ? 0 : (k < 9 ? 1 : (k < 12 ? 2 : 3));
            int inRow = row == 0 ? k : (row == 1 ? k - 5 : (row == 2 ? k - 9 : k - 12));
            int rowN = row == 0 ? Math.min(coins, 5) : (row == 1 ? Math.min(coins - 5, 4) : (row == 2 ? Math.min(coins - 9, 3) : coins - 12));
            float x = cx + (inRow - (rowN - 1) / 2f) * cs * 0.6f;
            float y = artY + cs * 0.55f - row * cs * 0.42f;
            ui.coin(g, x, y, cs);
        }
        // Sparkle
        float sp = (game.clock * 1.5f + i * 0.3f) % 1f;
        float sr = cs * 0.4f * MathUtil.sin(sp * MathUtil.PI);
        g.color(0xeeffffff);
        g.line(cx + cs * 0.4f - sr, artY - cs * 0.2f, cx + cs * 0.4f + sr, artY - cs * 0.2f, 3 * u);
        g.line(cx + cs * 0.4f, artY - cs * 0.2f - sr, cx + cs * 0.4f, artY - cs * 0.2f + sr, 3 * u);

        g.color(0xffffe066);
        String amount = CoinStore.format(CoinStore.COINS[i]);
        g.text(amount, cx, t + 80 * u, Ui.fit(g, amount, 60 * u, (r - l) * 0.85f), Gfx.ALIGN_CENTER, 7 * u, Ui.INK);
        g.color(0xffd8dcff);
        g.text("COINS", cx, t + 118 * u, 30 * u, Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
        g.color(0xffffffff);
        g.text(CoinStore.NAMES[i], cx, b - 128 * u, Ui.fit(g, CoinStore.NAMES[i], 30 * u, (r - l) * 0.9f), Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
        // Price plate
        g.color(Ui.INK);
        g.fillRoundRect(l + 20 * u, b - 96 * u, r - 20 * u, b - 18 * u, 22 * u);
        g.vertical(l + 26 * u, b - 90 * u, r - 26 * u, b - 24 * u, 0xff6ee86e, 0xff2ea82e);
        g.color(0xffffffff);
        g.text(CoinStore.PRICES[i], cx, b - 41 * u, 44 * u, Gfx.ALIGN_CENTER, 5 * u, Ui.INK);
        String badge = CoinStore.BADGES[i];
        if (badge != null) {
            float bw = Math.min(r - l - 20 * u, g.measureText(badge, 26 * u) + 40 * u);
            float by = t + 132 * u;
            g.color(Ui.INK);
            g.fillRoundRect(cx - bw / 2 - 4 * u, by - 4 * u, cx + bw / 2 + 4 * u, by + 46 * u, 22 * u);
            g.color(i == 3 ? 0xffff4a6a : 0xffffc928);
            g.fillRoundRect(cx - bw / 2, by, cx + bw / 2, by + 42 * u, 20 * u);
            g.color(0xffffffff);
            g.text(badge, cx, by + 31 * u, Ui.fit(g, badge, 26 * u, bw - 20 * u), Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
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

    private void drawSkinTile(Gfx g, Ui.Btn bt) {
        float u = game.u, d = press(bt);
        float l = bt.l + d, t = bt.t + d, r = bt.r - d, b = bt.b - d;
        int si = bt.id - B_SKIN;
        Skin sk = Skin.ALL[si];
        boolean owned = pr.ownsSkin(si), equipped = pr.skin == si;
        if (equipped) {
            g.color(0xccffd23f);
            g.fillRoundRect(l - 10 * u, t - 10 * u, r + 10 * u, b + 10 * u, 34 * u);
        }
        ui.panel(g, l, t, r, b, 0xff262a54);
        int rc = Brawler.RARITY_COLORS[sk.rarity];
        g.color(MathUtil.darker(rc, 0.3f));
        g.fillRoundRect(l, t, r, t + 64 * u, 26 * u);
        g.color(rc);
        g.fillRoundRect(l, t, r, t + 56 * u, 26 * u);
        g.color(0xffffffff);
        g.text(sk.name, (l + r) / 2, t + 42 * u, Ui.fit(g, sk.name, 34 * u, (r - l) * 0.9f), Gfx.ALIGN_CENTER, 5 * u, Ui.INK);
        Brawler br = Brawler.ALL[pr.selected];
        ui.snakeArt(g, br, sk.paletteFor(br), (l + r) / 2, t + (b - t) * 0.48f, Math.min(0.9f * u, (r - l) / 330f), game.clock + si * 0.3f);
        float py = b - 22 * u;
        if (equipped) {
            g.color(0xff4ad04a);
            g.text("EQUIPPED", (l + r) / 2, py, 32 * u, Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
        } else if (owned) {
            g.color(0xffd8dcff);
            g.text("TAP TO EQUIP", (l + r) / 2, py, 28 * u, Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
        } else {
            String p = Integer.toString(sk.price);
            float tw = g.measureText(p, 36 * u);
            ui.coin(g, (l + r) / 2 - tw / 2 - 16 * u, py - 12 * u, 36 * u);
            g.color(pr.coins >= sk.price ? 0xffffe066 : 0xffb08a5a);
            g.text(p, (l + r) / 2 - tw / 2 + 8 * u, py, 36 * u, Gfx.ALIGN_LEFT, 5 * u, Ui.INK);
        }
    }

    private void drawBrawlerOffer(Gfx g, Ui.Btn bt) {
        float u = game.u, d = press(bt);
        float l = bt.l + d, t = bt.t + d, r = bt.r - d, b = bt.b - d;
        int bi = bt.id - B_SHOP_BRAWLER;
        Brawler br = Brawler.ALL[bi];
        boolean owned = pr.isUnlocked(bi);
        ui.panel(g, l, t, r, b, 0xff262a54);
        int rc = Brawler.RARITY_COLORS[br.rarity];
        g.color(MathUtil.darker(rc, 0.3f));
        g.fillRoundRect(l, t, r, t + 90 * u, 26 * u);
        g.color(rc);
        g.fillRoundRect(l, t, r, t + 80 * u, 26 * u);
        g.color(0xffffffff);
        g.text(br.name, (l + r) / 2, t + 58 * u, 50 * u, Gfx.ALIGN_CENTER, 6 * u, Ui.INK);
        g.color(rc);
        g.text(Brawler.RARITY_NAMES[br.rarity] + " " + br.role.toUpperCase(), (l + r) / 2, t + 130 * u,
                Ui.fit(g, Brawler.RARITY_NAMES[br.rarity] + " " + br.role.toUpperCase(), 28 * u, (r - l) * 0.9f),
                Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
        ui.snakeArt(g, br, new int[]{br.color1, br.color2}, (l + r) / 2, t + 230 * u, Math.min(1.05f * u, (r - l) / 330f), game.clock + bi);
        ui.wrap(g, "SUPER: " + br.superDesc, l + 24 * u, r - 24 * u, t + 320 * u, 26 * u, 0xffe8eaff, b - 110 * u);
        g.color(Ui.INK);
        g.fillRoundRect(l + 24 * u, b - 96 * u, r - 24 * u, b - 18 * u, 22 * u);
        g.color(owned ? 0xff4ad04a : (pr.coins >= br.price ? 0xffffc928 : 0xff8a6a3a));
        g.fillRoundRect(l + 30 * u, b - 90 * u, r - 30 * u, b - 24 * u, 18 * u);
        g.color(0xffffffff);
        if (owned) {
            g.text("OWNED", (l + r) / 2, b - 41 * u, 44 * u, Gfx.ALIGN_CENTER, 5 * u, Ui.INK);
        } else {
            String p = Integer.toString(br.price);
            float tw = g.measureText(p, 44 * u);
            ui.coin(g, (l + r) / 2 - tw / 2 - 18 * u, b - 57 * u, 44 * u);
            g.text(p, (l + r) / 2 - tw / 2 + 10 * u, b - 41 * u, 44 * u, Gfx.ALIGN_LEFT, 5 * u, Ui.INK);
        }
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
        g.text("Snake Brawl v3.2  •  " + pr.games + " games  •  " + pr.totalKills + " knockouts  •  best " + pr.bestTrophies + " trophies",
                w / 2, h - game.padB - 160 * u, 30 * u, Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
    }
}
