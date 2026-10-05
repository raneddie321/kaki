package com.kaki.snakebrawl.game;

/**
 * Draws the player's hero. If directional sprites exist in assets/hero/ (rendered from the
 * 3D model, frame i faces i * 360/N degrees clockwise from +x), they are used; otherwise a
 * vector placeholder is drawn.
 */
final class HeroArt {
    static final int FRAMES = 16;
    private static Object[] frames;
    /** Sprite size relative to the hero's collision radius. */
    private static final float SPRITE_SCALE = 5f;

    private HeroArt() {}

    static void load(Platform p) {
        Object[] f = new Object[FRAMES];
        for (int i = 0; i < FRAMES; i++) {
            f[i] = p.loadImage("hero/hero_" + (i < 10 ? "0" : "") + i + ".png");
            if (f[i] == null) {
                frames = null;
                return;
            }
        }
        frames = f;
    }

    static boolean hasSprites() {
        return frames != null;
    }

    private static final float[] P = new float[8];

    static void draw(Gfx g, float x, float y, float r, float look, float walk, float alpha, float flash, boolean dashing) {
        // Ground shadow
        g.color(MathUtil.withAlpha(0x55000000, alpha));
        g.fillCircle(x + r * 0.15f, y + r * 0.35f, r * 1.05f);
        if (dashing) {
            g.color(MathUtil.withAlpha(0x553fb6ff, alpha));
            g.fillCircle(x - MathUtil.cos(look) * r * 1.2f, y - MathUtil.sin(look) * r * 1.2f, r * 1.2f);
        }
        if (frames != null) {
            float a = look;
            while (a < 0) a += MathUtil.TAU;
            int idx = Math.round(a / MathUtil.TAU * FRAMES) % FRAMES;
            float bob = walk > 0 ? Math.abs(MathUtil.sin(walk * 12f)) * r * 0.12f : 0;
            float s = r * SPRITE_SCALE;
            g.image(frames[idx], x - s / 2, y - s * 0.62f - bob, x + s / 2, y + s * 0.38f - bob, alpha);
            if (flash > 0) {
                g.color(MathUtil.withAlpha(0xffffffff, 0.6f * flash));
                g.strokeCircle(x, y, r * 1.3f, 6);
            }
            return;
        }
        drawVector(g, x, y, r, look, walk, alpha, flash);
    }

    private static int tint(int c, float alpha, float flash) {
        if (flash > 0) c = MathUtil.mix(c, 0xffffffff, flash * 0.7f);
        return MathUtil.withAlpha(c, alpha);
    }

    /** Top-down cartoon gunner used until the model's sprites are available. */
    private static void drawVector(Gfx g, float x, float y, float r, float look, float walk, float alpha, float flash) {
        float ca = MathUtil.cos(look), sa = MathUtil.sin(look);
        float px = -sa, py = ca;
        // Feet
        float step = walk > 0 ? MathUtil.sin(walk * 12f) * r * 0.35f : 0;
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            float fx = x + px * r * 0.42f * sgn + ca * step * sgn, fy = y + py * r * 0.42f * sgn + sa * step * sgn;
            g.color(tint(0xff1a1a24, alpha, 0));
            g.fillCircle(fx, fy, r * 0.32f);
            g.color(tint(0xff3a3a4a, alpha, flash));
            g.fillCircle(fx, fy, r * 0.24f);
        }
        // Backpack
        g.color(tint(0xff14142a, alpha, 0));
        g.fillRoundRect(x - ca * r * 0.95f - r * 0.45f, y - sa * r * 0.95f - r * 0.45f,
                x - ca * r * 0.95f + r * 0.45f, y - sa * r * 0.95f + r * 0.45f, r * 0.2f);
        g.color(tint(0xffffa62e, alpha, flash));
        g.fillRoundRect(x - ca * r * 0.95f - r * 0.36f, y - sa * r * 0.95f - r * 0.36f,
                x - ca * r * 0.95f + r * 0.36f, y - sa * r * 0.95f + r * 0.36f, r * 0.15f);
        // Body
        g.color(tint(0xff14142a, alpha, 0));
        g.fillCircle(x, y, r * 0.98f);
        g.color(tint(0xff3fa0ff, alpha, flash));
        g.fillCircle(x, y, r * 0.86f);
        g.color(tint(0xff7ac6ff, alpha, flash));
        g.fillCircle(x - r * 0.2f, y - r * 0.25f, r * 0.4f);
        // Blaster held forward-right
        float gx = x + px * r * 0.45f, gy = y + py * r * 0.45f;
        g.color(tint(0xff14142a, alpha, 0));
        g.line(gx, gy, gx + ca * r * 1.6f, gy + sa * r * 1.6f, r * 0.5f);
        g.color(tint(0xff5a6582, alpha, flash));
        g.line(gx, gy, gx + ca * r * 1.5f, gy + sa * r * 1.5f, r * 0.32f);
        g.color(tint(0xffffd23f, alpha, flash));
        g.fillCircle(gx + ca * r * 1.5f, gy + sa * r * 1.5f, r * 0.16f);
        // Hands
        g.color(tint(0xffffc79a, alpha, flash));
        g.fillCircle(gx + ca * r * 0.3f, gy + sa * r * 0.3f, r * 0.22f);
        g.fillCircle(x - px * r * 0.2f + ca * r * 0.75f, y - py * r * 0.2f + sa * r * 0.75f, r * 0.2f);
        // Head with cap
        float hx = x + ca * r * 0.1f, hy = y + sa * r * 0.1f;
        g.color(tint(0xff14142a, alpha, 0));
        g.fillCircle(hx, hy, r * 0.62f);
        g.color(tint(0xffffc79a, alpha, flash));
        g.fillCircle(hx, hy, r * 0.54f);
        g.color(tint(0xffe8304a, alpha, flash));
        P[0] = hx - ca * r * 0.55f + px * r * 0.5f;
        P[1] = hy - sa * r * 0.55f + py * r * 0.5f;
        P[2] = hx - ca * r * 0.55f - px * r * 0.5f;
        P[3] = hy - sa * r * 0.55f - py * r * 0.5f;
        P[4] = hx + ca * r * 0.15f - px * r * 0.55f;
        P[5] = hy + sa * r * 0.15f - py * r * 0.55f;
        P[6] = hx + ca * r * 0.15f + px * r * 0.55f;
        P[7] = hy + sa * r * 0.15f + py * r * 0.55f;
        g.fillPoly(P, 4);
        g.line(hx + ca * r * 0.15f - px * r * 0.45f, hy + sa * r * 0.15f - py * r * 0.45f,
                hx + ca * r * 0.15f + px * r * 0.45f, hy + sa * r * 0.15f + py * r * 0.45f, r * 0.12f);
        // Eyes
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            float ex = hx + ca * r * 0.32f + px * r * 0.2f * sgn, ey = hy + sa * r * 0.32f + py * r * 0.2f * sgn;
            g.color(tint(0xff14142a, alpha, 0));
            g.fillCircle(ex, ey, r * 0.1f);
        }
    }
}
