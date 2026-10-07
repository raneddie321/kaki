package com.snakebrawl.myapp.game;

/** First-launch setup (nickname and age) and the club screen. */
final class SocialScreens {
    static final int B_FIRST = 500;
    private static final int B_OB_NAME = 500, B_OB_RANDOM = 501, B_OB_NEXT = 502, B_OB_MINUS = 503, B_OB_PLUS = 504,
            B_OB_AGE = 505, B_OB_DONE = 506, B_OB_BACK = 507;
    private static final int B_CLUB_JOIN = 520, B_CLUB_CREATE = 540, B_CLUB_DUO = 541, B_CLUB_LEAVE = 542,
            B_CLUB_BADGE = 543;
    static final int B_LAST = 560;

    private static final String[] NAME_A = {"Sly", "Swift", "Mighty", "Tiny", "Sneaky", "Royal", "Turbo", "Zippy",
            "Cosmic", "Shadow", "Golden", "Hyper"};
    private static final String[] NAME_B = {"Viper", "Noodle", "Fang", "Cobra", "Python", "Slither", "Mamba",
            "Rattler", "Coil", "Scales", "Boa", "Hiss"};

    private final Game game;
    private final Profile pr;
    private final Ui ui;
    private int obStep;
    private String draftName = "";
    private int draftAge;

    SocialScreens(Game game) {
        this.game = game;
        this.pr = game.profile;
        this.ui = game.ui;
        draftName = pr.nickname == null ? "" : pr.nickname;
        draftAge = pr.age;
    }

    static boolean handles(int id) {
        return id >= B_FIRST && id < B_LAST;
    }

    /** Keeps letters (any language), digits, spaces and a few symbols; 2..14 characters. */
    static String cleanName(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < 14; i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '_' || c == '-' || c == '.') b.append(c);
        }
        return b.toString().trim();
    }

    static String randomName() {
        return NAME_A[MathUtil.randInt(NAME_A.length)] + NAME_B[MathUtil.randInt(NAME_B.length)] + MathUtil.randInt(100);
    }

    /** True for names made by {@link #randomName()}. */
    static boolean isGeneratedName(String n) {
        for (String a : NAME_A) {
            if (!n.startsWith(a)) continue;
            String rest = n.substring(a.length());
            for (String b : NAME_B) {
                if (rest.startsWith(b) && rest.substring(b.length()).matches("\\d{0,2}")) return true;
            }
        }
        return false;
    }

    private boolean nameValid() {
        return draftName.length() >= 2;
    }

    // ------------------------------------------------------------------ layout

    void layout() {
        float u = game.u, w = game.w, h = game.h;
        float cx = w / 2;
        if (game.screen == Game.ONBOARD) {
            float pt = h * 0.26f, pb = h - game.padB - 40 * u;
            // Age comes first: younger players then pick a random name instead of typing one
            if (obStep == 0) {
                ui.add(B_OB_MINUS, cx - 400 * u, pt + 170 * u, cx - 240 * u, pt + 330 * u, "-", null, 0xffff5a5a);
                ui.add(B_OB_AGE, cx - 200 * u, pt + 170 * u, cx + 200 * u, pt + 330 * u, null, null, 0);
                ui.add(B_OB_PLUS, cx + 240 * u, pt + 170 * u, cx + 400 * u, pt + 330 * u, "+", null, 0xff4ad04a);
                Ui.Btn next = ui.add(B_OB_NEXT, cx - 240 * u, pb - 120 * u, cx + 240 * u, pb, "NEXT", null, 0xff4ad04a);
                next.enabled = draftAge >= 4 && draftAge <= 99;
            } else {
                ui.add(B_OB_NAME, cx - 420 * u, pt + 170 * u, cx + 420 * u, pt + 290 * u, null, null, 0);
                ui.add(B_OB_RANDOM, cx - 200 * u, pt + 320 * u, cx + 200 * u, pt + 410 * u, "RANDOM NAME", null, 0xffb35cff);
                ui.add(B_OB_BACK, cx - 520 * u, pb - 120 * u, cx - 40 * u, pb, "BACK", null, 0xff8a8fb8);
                Ui.Btn done = ui.add(B_OB_DONE, cx + 40 * u, pb - 120 * u, cx + 520 * u, pb, "LET'S BRAWL!", null, 0xffffc928);
                done.enabled = nameValid();
            }
            return;
        }
        // Club screen
        ui.add(Game.B_BACK, game.padL + 10 * u, game.padT + 10 * u, game.padL + 230 * u, game.padT + 110 * u, "BACK", null, 0xffff5a5a);
        float top = game.padT + 150 * u, bottom = h - game.padB - 30 * u;
        float l = game.padL + 30 * u, r = w - game.padR - 30 * u;
        if (pr.club < 0) {
            float gap = 24 * u;
            float cw = (r - l - gap * 2) / 3f, ch = (bottom - top - 150 * u - gap) / 2f;
            for (int i = 0; i < Clubs.NAMES.length; i++) {
                float cl = l + (i % 3) * (cw + gap), ct = top + (i / 3) * (ch + gap);
                ui.add(B_CLUB_JOIN + i, cl, ct, cl + cw, ct + ch, null, null, 0);
            }
            ui.add(B_CLUB_CREATE, cx - 300 * u, bottom - 120 * u, cx + 300 * u, bottom, "CREATE A CLUB", null, 0xff3fb6a8);
        } else {
            float bx = r - 460 * u;
            ui.add(B_CLUB_DUO, bx, top + 260 * u, r, top + 400 * u, "PLAY DUO", "with " + game.partnerName(), 0xffff7a2e);
            if (pr.club == Clubs.CUSTOM) ui.add(B_CLUB_BADGE, bx, top + 430 * u, r, top + 540 * u, "CHANGE BADGE", null, 0xff3fa0ff);
            ui.add(B_CLUB_LEAVE, bx, bottom - 110 * u, r, bottom, "LEAVE CLUB", null, 0xffff5a5a);
        }
    }

    // ------------------------------------------------------------------ input

    void onButton(int id) {
        switch (id) {
            case B_OB_NAME:
                if (draftAge < Profile.FREE_NAME_AGE) {
                    // Players under 13 only pick from generated names, so no personal info can be typed in
                    onButton(B_OB_RANDOM);
                    return;
                }
                game.gated.requestText("Choose your nickname", draftName, 14, false, new Platform.TextCallback() {
                    @Override
                    public void onText(String text) {
                        if (text != null) draftName = cleanName(text);
                        game.layout();
                    }
                });
                return;
            case B_OB_RANDOM:
                draftName = randomName();
                game.layout();
                return;
            case B_OB_NEXT:
                if (draftAge < 4) return;
                if (draftAge < Profile.FREE_NAME_AGE && !isGeneratedName(draftName)) draftName = randomName();
                obStep = 1;
                game.layout();
                return;
            case B_OB_BACK:
                obStep = 0;
                game.layout();
                return;
            case B_OB_MINUS:
                draftAge = draftAge <= 0 ? 10 : Math.max(4, draftAge - 1);
                game.layout();
                return;
            case B_OB_PLUS:
                draftAge = draftAge <= 0 ? 10 : Math.min(99, draftAge + 1);
                game.layout();
                return;
            case B_OB_AGE:
                game.gated.requestText("How old are you?", draftAge > 0 ? Integer.toString(draftAge) : "", 2, true,
                        new Platform.TextCallback() {
                            @Override
                            public void onText(String text) {
                                if (text != null) {
                                    try {
                                        int a = Integer.parseInt(text.trim());
                                        draftAge = Math.max(4, Math.min(99, a));
                                    } catch (NumberFormatException ignored) {
                                        // keep the previous value
                                    }
                                }
                                game.layout();
                            }
                        });
                return;
            case B_OB_DONE:
                if (!nameValid() || draftAge < 4) return;
                if (draftAge < Profile.FREE_NAME_AGE && !isGeneratedName(draftName)) draftName = randomName();
                pr.nickname = draftName;
                pr.age = draftAge;
                pr.onboarded = true;
                pr.save();
                game.gated.playSound(Platform.SND_VICTORY, 0.8f);
                game.setScreen(Game.MENU);
                game.showInfo("WELCOME, " + pr.nickname.toUpperCase() + "!", "Join a club to get a Duo partner, pick a brawler "
                        + "and jump into Showdown. Have fun!", 3, pr.selected);
                return;
            case B_CLUB_CREATE:
                game.gated.requestText("Name your club", "", 16, false, new Platform.TextCallback() {
                    @Override
                    public void onText(String text) {
                        String n = cleanName(text);
                        if (n.length() >= 3) {
                            pr.customClubName = n;
                            pr.customClubBadge = MathUtil.randInt(Clubs.BADGE_COLORS.length);
                            pr.club = Clubs.CUSTOM;
                            pr.save();
                            game.gated.playSound(Platform.SND_POWER, 1f);
                            game.showInfo("CLUB CREATED!", n + " is ready. Your clubmate " + game.partnerName()
                                    + " will team up with you in Duo Showdown.", 0, 0);
                        } else if (text != null) {
                            game.showInfo("NAME TOO SHORT", "Club names need at least 3 letters.", 0, 0);
                        }
                        game.layout();
                    }
                });
                return;
            case B_CLUB_DUO:
                pr.mode = 2;
                pr.save();
                game.startGame();
                return;
            case B_CLUB_BADGE:
                pr.customClubBadge = (pr.customClubBadge + 1) % Clubs.BADGE_COLORS.length;
                pr.save();
                return;
            case B_CLUB_LEAVE:
                game.confirm("LEAVE CLUB?", "Leave " + Clubs.name(pr) + "? You can join another club any time.", 0, 0,
                        Game.ACT_LEAVE_CLUB, 0);
                return;
            default:
                if (id >= B_CLUB_JOIN && id < B_CLUB_JOIN + Clubs.NAMES.length) {
                    int c = id - B_CLUB_JOIN;
                    if (pr.trophies < Clubs.REQUIRED[c]) {
                        game.showInfo("NOT YET", Clubs.NAMES[c] + " needs " + Clubs.REQUIRED[c] + " trophies. Keep brawling!", 0, 0);
                    } else {
                        game.confirm("JOIN CLUB?", "Join " + Clubs.NAMES[c] + "?", 0, 0, Game.ACT_JOIN_CLUB, c);
                    }
                }
                break;
        }
    }

    void perform(int action, int arg) {
        if (action == Game.ACT_JOIN_CLUB) {
            pr.club = arg;
            pr.save();
            game.gated.playSound(Platform.SND_POWER, 1f);
            game.showInfo("WELCOME TO THE CLUB!", "You joined " + Clubs.NAMES[arg] + ". " + game.partnerName()
                    + " will be your Duo partner.", 0, 0);
        } else if (action == Game.ACT_LEAVE_CLUB) {
            pr.club = -1;
            pr.save();
        }
        game.layout();
    }

    // ------------------------------------------------------------------ render

    void render(Gfx g) {
        if (game.screen == Game.ONBOARD) renderOnboarding(g);
        else renderClub(g);
    }

    private void renderOnboarding(Gfx g) {
        float u = game.u, w = game.w, h = game.h, cx = w / 2;
        g.vertical(0, 0, w, h, 0xdd141a4a, 0xee0a0c24);
        g.radial(cx, h * 0.15f, 700 * u, 0x44ffc94a, 0x00ffc94a);
        g.color(0xffffd23f);
        g.text("WELCOME TO SNAKE BRAWL!", cx, game.padT + 110 * u, Ui.fit(g, "WELCOME TO SNAKE BRAWL!", 90 * u, w * 0.9f),
                Gfx.ALIGN_CENTER, 10 * u, Ui.INK);
        float pt = h * 0.26f;
        // Step dots
        for (int k = 0; k < 2; k++) {
            g.color(k == obStep ? 0xffffd23f : 0x66ffffff);
            g.fillCircle(cx - 20 * u + k * 40 * u, pt + 40 * u, 10 * u);
        }
        Brawler b = Brawler.ALL[pr.selected];
        ui.snakeArt(g, b, pr.palette(), w * 0.16f, h * 0.55f, 1.4f * u, game.clock);
        ui.snakeArt(g, Brawler.ALL[(pr.selected + 3) % Brawler.ALL.length], new int[]{0xffb35cff, 0xff6a2bd1}, w * 0.84f,
                h * 0.55f, 1.4f * u, game.clock + 1);
        if (obStep == 1) {
            boolean young = draftAge < Profile.FREE_NAME_AGE;
            g.color(0xffffffff);
            g.text("CHOOSE YOUR NICKNAME", cx, pt + 130 * u, 56 * u, Gfx.ALIGN_CENTER, 7 * u, Ui.INK);
            Ui.Btn box = ui.find(B_OB_NAME);
            if (box != null) {
                boolean pressed = ui.pressed == B_OB_NAME;
                g.color(Ui.INK);
                g.fillRoundRect(box.l - 6 * u, box.t - 6 * u, box.r + 6 * u, box.b + 6 * u, 34 * u);
                g.color(pressed ? 0xffe8ecff : 0xffffffff);
                g.fillRoundRect(box.l, box.t, box.r, box.b, 30 * u);
                boolean empty = draftName.length() == 0;
                g.color(empty ? 0xff9aa0c0 : 0xff14142a);
                String shown = empty ? (young ? "Tap RANDOM NAME" : "Tap to type...") : draftName;
                g.text(shown, (box.l + box.r) / 2, (box.t + box.b) / 2 + 22 * u, 60 * u, Gfx.ALIGN_CENTER, 0, 0);
                if (!empty && !young && (game.clock % 1f) < 0.5f) {
                    float tw = g.measureText(shown, 60 * u);
                    g.color(0xff3fa0ff);
                    g.fillRect((box.l + box.r) / 2 + tw / 2 + 8 * u, box.t + 30 * u, (box.l + box.r) / 2 + tw / 2 + 13 * u, box.b - 30 * u);
                }
            }
            g.color(0xffb8bdf0);
            g.text(young ? "Tap RANDOM NAME until you find a name you like!" : "2-14 letters. Don't use your real name.", cx,
                    pt + 470 * u, 32 * u, Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
        } else {
            g.color(0xffffffff);
            g.text("HOW OLD ARE YOU?", cx, pt + 130 * u, 56 * u, Gfx.ALIGN_CENTER, 7 * u, Ui.INK);
            Ui.Btn box = ui.find(B_OB_AGE);
            if (box != null) {
                g.color(Ui.INK);
                g.fillRoundRect(box.l - 6 * u, box.t - 6 * u, box.r + 6 * u, box.b + 6 * u, 34 * u);
                g.color(ui.pressed == B_OB_AGE ? 0xffe8ecff : 0xffffffff);
                g.fillRoundRect(box.l, box.t, box.r, box.b, 30 * u);
                g.color(draftAge > 0 ? 0xff14142a : 0xff9aa0c0);
                g.text(draftAge > 0 ? Integer.toString(draftAge) : "?", cx, (box.t + box.b) / 2 + 38 * u, 110 * u, Gfx.ALIGN_CENTER, 0, 0);
            }
            g.color(0xffb8bdf0);
            ui.wrap(g, "Your age stays on this device. We only use it to keep the game safe for younger players.",
                    cx - 480 * u, cx + 480 * u, pt + 370 * u, 32 * u, 0xffb8bdf0, h);
        }
        for (int i = 0; i < ui.count; i++) {
            Ui.Btn bt = ui.btns[i];
            if (bt.id != B_OB_NAME && bt.id != B_OB_AGE) ui.button(g, bt);
        }
    }

    private void renderClub(Gfx g) {
        float u = game.u, w = game.w, h = game.h, cx = w / 2;
        g.color(0xcc0d1030);
        g.fillRect(0, 0, w, h);
        g.color(0xffffd23f);
        g.text(pr.club < 0 ? "JOIN A CLUB" : "MY CLUB", cx, game.padT + 92 * u, 84 * u, Gfx.ALIGN_CENTER, 9 * u, Ui.INK);
        float top = game.padT + 150 * u, bottom = h - game.padB - 30 * u;
        float l = game.padL + 30 * u, r = w - game.padR - 30 * u;
        if (pr.club < 0) {
            for (int i = 0; i < ui.count; i++) {
                Ui.Btn bt = ui.btns[i];
                if (bt.id >= B_CLUB_JOIN && bt.id < B_CLUB_JOIN + Clubs.NAMES.length) drawClubCard(g, bt, bt.id - B_CLUB_JOIN);
                else ui.button(g, bt);
            }
            return;
        }
        // Club header
        float bx = r - 460 * u;
        ui.panel(g, bx, top, r, top + 230 * u, 0xee22264a);
        Clubs.drawBadge(g, Clubs.badge(pr), bx + 90 * u, top + 105 * u, 120 * u);
        String nm = Clubs.name(pr);
        g.color(0xffffffff);
        g.text(nm, bx + 170 * u, top + 80 * u, Ui.fit(g, nm, 46 * u, r - bx - 190 * u), Gfx.ALIGN_LEFT, 6 * u, Ui.INK);
        ui.trophy(g, bx + 194 * u, top + 132 * u, 38 * u);
        g.color(0xffffe066);
        g.text(Integer.toString(Clubs.totalTrophies(pr)), bx + 228 * u, top + 146 * u, 40 * u, Gfx.ALIGN_LEFT, 5 * u, Ui.INK);
        g.color(0xffb8bdf0);
        g.text(Clubs.MEMBERS + " members", bx + 170 * u, top + 196 * u, 28 * u, Gfx.ALIGN_LEFT, 4 * u, Ui.INK);
        // Members list
        float ml = l, mr = bx - 30 * u;
        ui.panel(g, ml, top, mr, bottom, 0xee22264a);
        g.color(0xffd8dcff);
        g.text("MEMBERS", ml + 30 * u, top + 56 * u, 36 * u, Gfx.ALIGN_LEFT, 5 * u, Ui.INK);
        String[] names = new String[Clubs.MEMBERS];
        int[] trophies = new int[Clubs.MEMBERS];
        int[] who = new int[Clubs.MEMBERS]; // 0 = you, 1 = duo partner, 2 = others
        for (int i = 0; i < Clubs.MEMBERS; i++) who[i] = i == 0 ? 0 : (i == 1 ? 1 : 2);
        names[0] = pr.displayName();
        trophies[0] = pr.trophies;
        for (int i = 1; i < Clubs.MEMBERS; i++) {
            names[i] = Clubs.memberName(pr, i - 1);
            trophies[i] = Clubs.memberTrophies(pr, i - 1);
        }
        // Sort by trophies
        for (int i = 1; i < Clubs.MEMBERS; i++) {
            for (int j = i; j > 0 && trophies[j] > trophies[j - 1]; j--) {
                int t = trophies[j];
                trophies[j] = trophies[j - 1];
                trophies[j - 1] = t;
                String s = names[j];
                names[j] = names[j - 1];
                names[j - 1] = s;
                int k = who[j];
                who[j] = who[j - 1];
                who[j - 1] = k;
            }
        }
        float rowH = Math.min(78 * u, (bottom - top - 90 * u) / Clubs.MEMBERS);
        for (int i = 0; i < Clubs.MEMBERS; i++) {
            float y = top + 80 * u + i * rowH;
            boolean me = who[i] == 0;
            boolean partner = who[i] == 1;
            g.color(me ? 0x553fa0ff : (i % 2 == 0 ? 0x22ffffff : 0x11ffffff));
            g.fillRoundRect(ml + 20 * u, y, mr - 20 * u, y + rowH - 8 * u, 14 * u);
            g.color(0xffffffff);
            g.text((i + 1) + ".", ml + 40 * u, y + rowH * 0.62f, 32 * u, Gfx.ALIGN_LEFT, 4 * u, Ui.INK);
            g.color(me ? 0xff9cff8a : 0xffffffff);
            g.text(names[i], ml + 100 * u, y + rowH * 0.62f, 34 * u, Gfx.ALIGN_LEFT, 4 * u, Ui.INK);
            String role = i == 0 ? "President" : (i < 3 ? "Senior" : "Member");
            if (partner) role = "Duo partner";
            g.color(partner ? 0xffff9a4a : 0xffb8bdf0);
            g.text(role, ml + 100 * u + g.measureText(names[i], 34 * u) + 24 * u, y + rowH * 0.62f, 24 * u, Gfx.ALIGN_LEFT, 3 * u, Ui.INK);
            ui.trophy(g, mr - 150 * u, y + rowH * 0.42f, 30 * u);
            g.color(0xffffe066);
            g.text(Integer.toString(trophies[i]), mr - 124 * u, y + rowH * 0.62f, 32 * u, Gfx.ALIGN_LEFT, 4 * u, Ui.INK);
        }
        g.color(0xff8a90c0);
        ui.wrap(g, "Club members are computer players until online play is added.", bx, r, bottom - 190 * u, 24 * u, 0xff8a90c0, bottom - 120 * u);
        for (int i = 0; i < ui.count; i++) ui.button(g, ui.btns[i]);
    }

    private void drawClubCard(Gfx g, Ui.Btn bt, int c) {
        float u = game.u, d = ui.pressed == bt.id ? 6 * u : 0;
        float l = bt.l + d, t = bt.t + d, r = bt.r - d, b = bt.b - d;
        boolean locked = pr.trophies < Clubs.REQUIRED[c];
        ui.panel(g, l, t, r, b, locked ? 0xff1c1e3a : 0xff262a54);
        int col = Clubs.BADGE_COLORS[Clubs.BADGES[c]];
        g.save();
        g.clip(l, t + 26 * u, r, b - 26 * u);
        g.vertical(l, t, r, b, MathUtil.withAlpha(col, 0.3f), 0x00262a54);
        g.restore();
        float bs = Math.min(110 * u, (b - t) * 0.5f);
        Clubs.drawBadge(g, Clubs.BADGES[c], l + bs * 0.5f + 30 * u, (t + b) / 2, bs);
        float tx = l + bs + 60 * u;
        g.color(0xffffffff);
        g.text(Clubs.NAMES[c], tx, t + (b - t) * 0.38f, Ui.fit(g, Clubs.NAMES[c], 40 * u, r - tx - 20 * u), Gfx.ALIGN_LEFT, 5 * u, Ui.INK);
        g.color(0xffb8bdf0);
        g.text(Clubs.MEMBERS + " members", tx, t + (b - t) * 0.58f, 26 * u, Gfx.ALIGN_LEFT, 3 * u, Ui.INK);
        ui.trophy(g, tx + 16 * u, t + (b - t) * 0.76f, 28 * u);
        g.color(locked ? 0xffff7a6a : 0xffffe066);
        g.text(Clubs.REQUIRED[c] + "+ required", tx + 40 * u, t + (b - t) * 0.8f, 26 * u, Gfx.ALIGN_LEFT, 3 * u, Ui.INK);
    }
}
