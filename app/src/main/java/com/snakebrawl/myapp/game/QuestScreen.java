package com.snakebrawl.myapp.game;

/** Daily reward calendar and today's three quests. */
final class QuestScreen {
    static final int B_FIRST = 800;
    private static final int B_DAILY = 800, B_QUEST = 801;
    static final int B_LAST = 810;

    private final Game game;
    private final Ui ui;
    private final Profile pr;

    QuestScreen(Game game) {
        this.game = game;
        this.ui = game.ui;
        this.pr = game.profile;
    }

    static boolean handles(int id) {
        return id >= B_FIRST && id < B_LAST;
    }

    private float dailyTop() {
        return game.padT + 175 * game.u;
    }

    private float questTop() {
        return dailyTop() + 365 * game.u;
    }

    void layout() {
        float u = game.u, w = game.w, cx = w / 2;
        Quests.refresh(pr);
        ui.add(Game.B_BACK, game.padL + 10 * u, game.padT + 10 * u, game.padL + 230 * u, game.padT + 110 * u, "BACK", null, 0xffff5a5a);
        if (Quests.dailyReady(pr)) {
            float t = dailyTop() + 218 * u;
            ui.add(B_DAILY, cx - 230 * u, t, cx + 230 * u, t + 88 * u, "CLAIM DAY " + (Quests.dailyNext(pr) + 1), null, 0xff4ad04a);
        }
        float qw = Math.min(1500 * u, w - game.padL - game.padR - 60 * u);
        float ql = cx - qw / 2;
        for (int i = 0; i < Quests.COUNT; i++) {
            float t = questTop() + i * 125 * u;
            if (Quests.done(pr, i) && !Quests.claimed(pr, i)) {
                ui.add(B_QUEST + i, ql + qw - 280 * u, t + 12 * u, ql + qw - 20 * u, t + 98 * u, "CLAIM", null, 0xffffc928);
            }
        }
    }

    void onButton(int id) {
        if (id == B_DAILY) {
            int d = Quests.claimDaily(pr);
            if (d < 0) return;
            int type = Quests.DAILY_TYPE[d];
            if (type == 0) {
                game.coinBurst(Quests.DAILY_COINS[d]);
                game.showInfo("DAY " + (d + 1) + " REWARD!", "+" + Quests.DAILY_COINS[d] + " coins. Come back tomorrow for day "
                        + (d == 6 ? 1 : d + 2) + "!", 1, 0);
            } else {
                game.openRewardBox(type == 2);
            }
            game.layout();
            return;
        }
        int i = id - B_QUEST;
        if (i >= 0 && i < Quests.COUNT && Quests.claim(pr, i)) {
            game.coinBurst(Quests.coins(pr, i));
            game.showInfo("QUEST COMPLETE!", "+" + Quests.coins(pr, i) + " coins and +" + Quests.xp(pr, i) + " Snake Pass XP.", 1, 0);
        }
        game.layout();
    }

    void render(Gfx g) {
        float u = game.u, w = game.w, h = game.h, cx = w / 2;
        g.color(0xcc0d1030);
        g.fillRect(0, 0, w, h);
        g.color(0xffffd23f);
        g.text("DAILY REWARDS & QUESTS", cx, game.padT + 92 * u, Ui.fit(g, "DAILY REWARDS & QUESTS", 80 * u, w - 600 * u),
                Gfx.ALIGN_CENTER, 9 * u, Ui.INK);

        // 7-day calendar
        float top = dailyTop();
        float cw = 190 * u, gap = 18 * u, ch = 200 * u;
        float total = 7 * cw + 6 * gap, l0 = cx - total / 2;
        int next = Quests.dailyNext(pr);
        boolean ready = Quests.dailyReady(pr);
        for (int d = 0; d < 7; d++) {
            float l = l0 + d * (cw + gap);
            boolean got = ready ? d < next : d <= pr.dailyIndex && pr.dailyIndex >= 0;
            boolean today = ready && d == next;
            int col = today ? 0xff3a6a2a : got ? 0xff2a2e4a : 0xee22264a;
            ui.panel(g, l, top, l + cw, top + ch, col);
            if (today && !pr.lowGraphics) {
                float p = 0.5f + 0.5f * MathUtil.sin(game.clock * 4f);
                g.color(MathUtil.withAlpha(0xffffd23f, 0.5f + 0.4f * p));
                g.strokeRoundRect(l - 4 * u, top - 4 * u, l + cw + 4 * u, top + ch + 4 * u, 26 * u, 6 * u);
            }
            g.color(today ? 0xffffd23f : 0xffd8dcff);
            g.text("DAY " + (d + 1), l + cw / 2, top + 42 * u, 32 * u, Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
            float iy = top + 112 * u;
            int type = Quests.DAILY_TYPE[d];
            if (type == 0) {
                ui.coin(g, l + cw / 2, iy, 70 * u);
                g.color(0xffffe066);
                g.text("+" + Quests.DAILY_COINS[d], l + cw / 2, top + ch - 20 * u, 34 * u, Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
            } else {
                MetaScreens.drawBox(g, l + cw / 2, iy, 56 * u, type == 2, game.clock, u);
                g.color(type == 2 ? 0xffd8a0ff : 0xff8ad8ff);
                g.text(type == 2 ? "MEGA BOX" : "BOX", l + cw / 2, top + ch - 20 * u, 28 * u, Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
            }
            if (got) {
                g.color(0x99000000);
                g.fillRoundRect(l + 6 * u, top + 6 * u, l + cw - 6 * u, top + ch - 6 * u, 20 * u);
                g.color(0xff6dff6d);
                g.line(l + cw / 2 - 30 * u, top + ch / 2, l + cw / 2 - 8 * u, top + ch / 2 + 24 * u, 10 * u);
                g.line(l + cw / 2 - 8 * u, top + ch / 2 + 24 * u, l + cw / 2 + 34 * u, top + ch / 2 - 22 * u, 10 * u);
            }
        }
        if (!ready) {
            g.color(0xffb8bdf0);
            g.text("Come back tomorrow for day " + ((pr.dailyIndex + 1) % 7 + 1) + "!  Miss a day and the calendar starts over.",
                    cx, top + ch + 60 * u, 30 * u, Gfx.ALIGN_CENTER, 4 * u, Ui.INK);
        }

        // Quests
        float qw = Math.min(1500 * u, w - game.padL - game.padR - 60 * u);
        float ql = cx - qw / 2;
        float qt = questTop();
        g.color(0xffffd23f);
        g.text("TODAY'S QUESTS", ql, qt - 22 * u, 40 * u, Gfx.ALIGN_LEFT, 5 * u, Ui.INK);
        g.color(0xffb8bdf0);
        g.text("New quests in " + Quests.hoursLeft() + "h", ql + qw, qt - 22 * u, 30 * u, Gfx.ALIGN_RIGHT, 4 * u, Ui.INK);
        for (int i = 0; i < Quests.COUNT; i++) {
            float t = qt + i * 125 * u, b = t + 110 * u;
            boolean done = Quests.done(pr, i), claimed = Quests.claimed(pr, i);
            ui.panel(g, ql, t, ql + qw, b, claimed ? 0xee1c2038 : 0xee262a54);
            g.color(claimed ? 0xff8a90b8 : 0xffffffff);
            g.text(Quests.text(pr, i), ql + 30 * u, t + 48 * u, 38 * u, Gfx.ALIGN_LEFT, 5 * u, Ui.INK);
            // Progress bar
            float bl = ql + 30 * u, br = ql + qw * 0.55f;
            float f = Math.min(1f, pr.questProgress[i] / (float) Quests.target(pr, i));
            g.color(Ui.INK);
            g.fillRoundRect(bl, t + 66 * u, br, t + 94 * u, 14 * u);
            g.color(0xff2a2e5a);
            g.fillRoundRect(bl + 4 * u, t + 70 * u, br - 4 * u, t + 90 * u, 10 * u);
            if (f > 0.02f) g.vertical(bl + 4 * u, t + 70 * u, bl + 4 * u + (br - bl - 8 * u) * f, t + 90 * u, 0xff9cff8a, 0xff3ac04a);
            g.color(0xffffffff);
            g.text(pr.questProgress[i] + " / " + Quests.target(pr, i), br + 20 * u, t + 92 * u, 28 * u, Gfx.ALIGN_LEFT, 4 * u, Ui.INK);
            // Reward
            float rx = ql + qw - 330 * u;
            if (claimed) {
                g.color(0xff6dff6d);
                g.text("DONE!", ql + qw - 40 * u, t + 70 * u, 44 * u, Gfx.ALIGN_RIGHT, 5 * u, Ui.INK);
            } else if (!done) {
                ui.coin(g, rx + 30 * u, t + 55 * u, 46 * u);
                g.color(0xffffe066);
                g.text("+" + Quests.coins(pr, i), rx + 62 * u, t + 70 * u, 36 * u, Gfx.ALIGN_LEFT, 4 * u, Ui.INK);
                g.color(0xff9cff8a);
                g.text("+" + Quests.xp(pr, i) + " XP", ql + qw - 30 * u, t + 70 * u, 34 * u, Gfx.ALIGN_RIGHT, 4 * u, Ui.INK);
            }
        }
        for (int i = 0; i < ui.count; i++) ui.button(g, ui.btns[i]);
    }
}
