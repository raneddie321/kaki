package com.kaki.snakebrawl.game;

/** Small vector icons (the game font has no symbol glyphs). Centered on (x, y), s = size. */
final class Icons {
    private Icons() {}

    static void skull(Gfx g, float x, float y, float s, float alpha) {
        g.color(MathUtil.withAlpha(0xff14142a, alpha));
        g.fillCircle(x, y - s * 0.08f, s * 0.5f);
        g.fillRoundRect(x - s * 0.32f, y + s * 0.05f, x + s * 0.32f, y + s * 0.5f, s * 0.1f);
        g.color(MathUtil.withAlpha(0xffffffff, alpha));
        g.fillCircle(x, y - s * 0.08f, s * 0.4f);
        g.fillRoundRect(x - s * 0.24f, y + s * 0.08f, x + s * 0.24f, y + s * 0.42f, s * 0.08f);
        g.color(MathUtil.withAlpha(0xff14142a, alpha));
        g.fillCircle(x - s * 0.16f, y - s * 0.06f, s * 0.12f);
        g.fillCircle(x + s * 0.16f, y - s * 0.06f, s * 0.12f);
        g.fillRect(x - s * 0.03f, y + s * 0.22f, x + s * 0.03f, y + s * 0.42f);
    }

    static void cube(Gfx g, float x, float y, float s, float alpha) {
        g.color(MathUtil.withAlpha(0xff14142a, alpha));
        g.fillRoundRect(x - s * 0.5f, y - s * 0.5f, x + s * 0.5f, y + s * 0.5f, s * 0.16f);
        g.color(MathUtil.withAlpha(0xff2a9a35, alpha));
        g.fillRoundRect(x - s * 0.4f, y - s * 0.4f, x + s * 0.4f, y + s * 0.4f, s * 0.12f);
        g.color(MathUtil.withAlpha(0xff6dff6d, alpha));
        g.fillRoundRect(x - s * 0.4f, y - s * 0.4f, x + s * 0.22f, y + s * 0.22f, s * 0.1f);
        g.color(MathUtil.withAlpha(0xffd8ffd8, alpha));
        g.fillRoundRect(x - s * 0.3f, y - s * 0.3f, x - s * 0.08f, y - s * 0.08f, s * 0.05f);
    }

    static void poison(Gfx g, float x, float y, float s, float alpha) {
        g.color(MathUtil.withAlpha(0xff14142a, alpha));
        g.fillCircle(x - s * 0.22f, y + s * 0.08f, s * 0.3f);
        g.fillCircle(x + s * 0.2f, y + s * 0.1f, s * 0.28f);
        g.fillCircle(x, y - s * 0.12f, s * 0.32f);
        g.color(MathUtil.withAlpha(0xff6dff6d, alpha));
        g.fillCircle(x - s * 0.22f, y + s * 0.08f, s * 0.22f);
        g.fillCircle(x + s * 0.2f, y + s * 0.1f, s * 0.2f);
        g.fillCircle(x, y - s * 0.12f, s * 0.24f);
    }

    static void crash(Gfx g, float x, float y, float s, float alpha) {
        g.color(MathUtil.withAlpha(0xff14142a, alpha));
        g.line(x - s * 0.35f, y - s * 0.35f, x + s * 0.35f, y + s * 0.35f, s * 0.3f);
        g.line(x + s * 0.35f, y - s * 0.35f, x - s * 0.35f, y + s * 0.35f, s * 0.3f);
        g.color(MathUtil.withAlpha(0xffffc928, alpha));
        g.line(x - s * 0.35f, y - s * 0.35f, x + s * 0.35f, y + s * 0.35f, s * 0.16f);
        g.line(x + s * 0.35f, y - s * 0.35f, x - s * 0.35f, y + s * 0.35f, s * 0.16f);
    }

    static void bolt(Gfx g, float x, float y, float s, int color) {
        float[] p = BOLT;
        p[0] = x + s * 0.18f;
        p[1] = y - s * 0.5f;
        p[2] = x - s * 0.3f;
        p[3] = y + s * 0.1f;
        p[4] = x - s * 0.02f;
        p[5] = y + s * 0.1f;
        p[6] = x - s * 0.18f;
        p[7] = y + s * 0.5f;
        p[8] = x + s * 0.3f;
        p[9] = y - s * 0.12f;
        p[10] = x + s * 0.02f;
        p[11] = y - s * 0.12f;
        g.color(color);
        g.fillPoly(p, 6);
    }

    private static final float[] BOLT = new float[12];
}
