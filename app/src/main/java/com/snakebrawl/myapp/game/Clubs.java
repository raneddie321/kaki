package com.snakebrawl.myapp.game;

/**
 * Clubs. There is no online server yet, so clubs are local: built-in clubs and the player's own
 * club are filled with computer-controlled members, one of whom can join you in Duo Showdown.
 */
final class Clubs {
    static final int CUSTOM = 100;
    static final int MEMBERS = 8;

    static final String[] NAMES = {"Venom Kings", "Coil Crew", "Fang Squad", "Slither Stars", "Scale Storm", "Hiss Legends"};
    static final int[] BADGES = {0, 1, 2, 3, 4, 5};
    static final int[] COLORS = {0xff4ad04a, 0xff3fa0ff, 0xffff5a5a, 0xffffc928, 0xffb35cff, 0xffff9a2e};
    static final int[] REQUIRED = {0, 0, 30, 60, 100, 200};
    static final String[] BADGE_NAMES = {"Fang", "Star", "Bolt", "Crown", "Skull", "Heart"};
    static final int[] BADGE_COLORS = {0xff4ad04a, 0xff3fa0ff, 0xffffc928, 0xffb35cff, 0xffff5a5a, 0xffff5ab5};

    private Clubs() {}

    static String name(Profile p) {
        if (p.club == CUSTOM) return p.customClubName;
        return p.club >= 0 && p.club < NAMES.length ? NAMES[p.club] : null;
    }

    static int badge(Profile p) {
        return p.club == CUSTOM ? p.customClubBadge : (p.club >= 0 ? BADGES[p.club] : 0);
    }

    static int color(Profile p) {
        return BADGE_COLORS[badge(p) % BADGE_COLORS.length];
    }

    /** Deterministic member names for a club. */
    static String memberName(Profile p, int i) {
        int seed = p.club == CUSTOM ? (p.customClubName.hashCode() & 0x7fffffff) : p.club * 7 + 3;
        // Consecutive indices keep every member name distinct.
        return Brawler.BOT_NAMES[(seed + i) % Brawler.BOT_NAMES.length];
    }

    /** Member trophies stay around the player's level so the club feels like peers. */
    static int memberTrophies(Profile p, int i) {
        int seed = p.club == CUSTOM ? (p.customClubName.hashCode() & 0x7fff) : p.club * 131 + 17;
        int spread = ((seed + i * 97) % 120) - 50;
        return Math.max(0, p.trophies + spread + (MEMBERS - i) * 12);
    }

    static int totalTrophies(Profile p) {
        int t = p.trophies;
        for (int i = 0; i < MEMBERS - 1; i++) t += memberTrophies(p, i);
        return t;
    }

    /** Club shield with an emblem, centred on (x, y). */
    static void drawBadge(Gfx g, int badge, float x, float y, float s) {
        int col = BADGE_COLORS[badge % BADGE_COLORS.length];
        float[] p = SHIELD;
        p[0] = x - s * 0.5f;
        p[1] = y - s * 0.5f;
        p[2] = x + s * 0.5f;
        p[3] = y - s * 0.5f;
        p[4] = x + s * 0.5f;
        p[5] = y + s * 0.1f;
        p[6] = x;
        p[7] = y + s * 0.6f;
        p[8] = x - s * 0.5f;
        p[9] = y + s * 0.1f;
        g.save();
        g.translate(x, y);
        g.scale(1.12f);
        g.translate(-x, -y);
        g.color(Ui.INK);
        g.fillPoly(p, 5);
        g.restore();
        g.color(MathUtil.darker(col, 0.35f));
        g.fillPoly(p, 5);
        g.save();
        g.translate(x, y - s * 0.04f);
        g.scale(0.86f);
        g.translate(-x, -y);
        g.color(col);
        g.fillPoly(p, 5);
        g.restore();
        g.color(0xffffffff);
        float e = s * 0.32f;
        switch (badge % 6) {
            case 0: // fang
                p[0] = x - e * 0.6f;
                p[1] = y - e * 0.7f;
                p[2] = x + e * 0.6f;
                p[3] = y - e * 0.7f;
                p[4] = x;
                p[5] = y + e;
                g.fillPoly(p, 3);
                break;
            case 1: // star
                for (int k = 0; k < 5; k++) {
                    float a0 = -MathUtil.PI / 2 + k * MathUtil.TAU / 5f;
                    p[0] = x + MathUtil.cos(a0) * e;
                    p[1] = y + MathUtil.sin(a0) * e;
                    p[2] = x + MathUtil.cos(a0 + 0.63f) * e * 0.42f;
                    p[3] = y + MathUtil.sin(a0 + 0.63f) * e * 0.42f;
                    p[4] = x + MathUtil.cos(a0 - 0.63f) * e * 0.42f;
                    p[5] = y + MathUtil.sin(a0 - 0.63f) * e * 0.42f;
                    g.fillPoly(p, 3);
                }
                g.fillCircle(x, y, e * 0.42f);
                break;
            case 2:
                Icons.bolt(g, x, y, e * 2f, 0xffffffff);
                break;
            case 3: // crown
                p[0] = x - e;
                p[1] = y + e * 0.6f;
                p[2] = x - e;
                p[3] = y - e * 0.5f;
                p[4] = x - e * 0.4f;
                p[5] = y;
                p[6] = x;
                p[7] = y - e * 0.8f;
                p[8] = x + e * 0.4f;
                p[9] = y;
                p[10] = x + e;
                p[11] = y - e * 0.5f;
                p[12] = x + e;
                p[13] = y + e * 0.6f;
                g.fillPoly(p, 7);
                break;
            case 4:
                Icons.skull(g, x, y, e * 2f, 1f);
                break;
            default: // heart
                g.fillCircle(x - e * 0.45f, y - e * 0.2f, e * 0.5f);
                g.fillCircle(x + e * 0.45f, y - e * 0.2f, e * 0.5f);
                p[0] = x - e * 0.92f;
                p[1] = y - e * 0.05f;
                p[2] = x + e * 0.92f;
                p[3] = y - e * 0.05f;
                p[4] = x;
                p[5] = y + e * 0.9f;
                g.fillPoly(p, 3);
                break;
        }
    }

    private static final float[] SHIELD = new float[16];
}
