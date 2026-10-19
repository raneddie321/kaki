package com.snakebrawl.myapp.game;

/** Pooled visual effects: dots, sparks, rings, smoke and floating text. */
final class Particles {
    static final int DOT = 0;
    static final int SPARK = 1;
    static final int RING = 2;
    static final int SMOKE = 3;
    /** Bright radial flash (muzzle flashes, impacts). */
    static final int FLASH = 4;
    /** Expanding fireball with a hot core. */
    static final int FIRE = 5;

    private static final int MAX = 1600;
    private final float[] x = new float[MAX], y = new float[MAX], vx = new float[MAX], vy = new float[MAX];
    private final float[] life = new float[MAX], maxLife = new float[MAX], size = new float[MAX];
    private final int[] color = new int[MAX], type = new int[MAX];
    private int count;
    boolean low;

    private static final int MAX_TEXT = 80;
    private final float[] tx = new float[MAX_TEXT], ty = new float[MAX_TEXT], tlife = new float[MAX_TEXT], tsize = new float[MAX_TEXT];
    private final int[] tcolor = new int[MAX_TEXT];
    private final String[] text = new String[MAX_TEXT];
    private int textCount;

    void clear() {
        count = 0;
        textCount = 0;
    }

    void add(int t, float px, float py, float pvx, float pvy, float sz, int c, float l) {
        if (count >= (low ? MAX / 3 : MAX)) return;
        int i = count++;
        type[i] = t;
        x[i] = px;
        y[i] = py;
        vx[i] = pvx;
        vy[i] = pvy;
        size[i] = sz;
        color[i] = c;
        life[i] = l;
        maxLife[i] = l;
    }

    void burst(float px, float py, int n, int c, float speed, float sz, float l) {
        if (low) n = (n + 1) / 2;
        for (int k = 0; k < n; k++) {
            float a = MathUtil.frand(0, MathUtil.TAU);
            float s = speed * MathUtil.frand(0.3f, 1f);
            add(DOT, px, py, MathUtil.cos(a) * s, MathUtil.sin(a) * s, sz * MathUtil.frand(0.6f, 1.2f), c, l * MathUtil.frand(0.6f, 1.1f));
        }
    }

    void sparks(float px, float py, int n, int c, float speed, float l) {
        for (int k = 0; k < n; k++) {
            float a = MathUtil.frand(0, MathUtil.TAU);
            float s = speed * MathUtil.frand(0.4f, 1f);
            add(SPARK, px, py, MathUtil.cos(a) * s, MathUtil.sin(a) * s, MathUtil.frand(2.5f, 4.5f), c, l * MathUtil.frand(0.5f, 1f));
        }
    }

    void ring(float px, float py, float radius, int c, float l) {
        add(RING, px, py, 0, 0, radius, c, l);
    }

    void smoke(float px, float py, int n, int c, float sz, float l) {
        if (low) n = (n + 1) / 2;
        for (int k = 0; k < n; k++) {
            float a = MathUtil.frand(0, MathUtil.TAU);
            float s = MathUtil.frand(10, 60);
            add(SMOKE, px + MathUtil.cos(a) * sz * 0.3f, py + MathUtil.sin(a) * sz * 0.3f,
                    MathUtil.cos(a) * s, MathUtil.sin(a) * s, sz * MathUtil.frand(0.6f, 1.1f), c, l * MathUtil.frand(0.7f, 1.1f));
        }
    }

    void flash(float px, float py, float radius, int c, float l) {
        add(FLASH, px, py, 0, 0, radius, c, l);
    }

    void fireball(float px, float py, float radius, float l) {
        add(FIRE, px, py, 0, 0, radius, 0xffff8a2a, l);
    }

    void text(float px, float py, String s, int c, float sz) {
        if (textCount >= MAX_TEXT) {
            // Drop the oldest entry
            System.arraycopy(tx, 1, tx, 0, MAX_TEXT - 1);
            System.arraycopy(ty, 1, ty, 0, MAX_TEXT - 1);
            System.arraycopy(tlife, 1, tlife, 0, MAX_TEXT - 1);
            System.arraycopy(tsize, 1, tsize, 0, MAX_TEXT - 1);
            System.arraycopy(tcolor, 1, tcolor, 0, MAX_TEXT - 1);
            System.arraycopy(text, 1, text, 0, MAX_TEXT - 1);
            textCount--;
        }
        int i = textCount++;
        tx[i] = px + MathUtil.frand(-12, 12);
        ty[i] = py;
        tlife[i] = 0.9f;
        tsize[i] = sz;
        tcolor[i] = c;
        text[i] = s;
    }

    void update(float dt) {
        float drag = (float) Math.exp(-3.5f * dt);
        for (int i = 0; i < count; i++) {
            life[i] -= dt;
            if (life[i] <= 0) {
                int last = --count;
                x[i] = x[last];
                y[i] = y[last];
                vx[i] = vx[last];
                vy[i] = vy[last];
                life[i] = life[last];
                maxLife[i] = maxLife[last];
                size[i] = size[last];
                color[i] = color[last];
                type[i] = type[last];
                i--;
                continue;
            }
            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;
            vx[i] *= drag;
            vy[i] *= drag;
        }
        for (int i = 0; i < textCount; i++) {
            tlife[i] -= dt;
            ty[i] -= 70 * dt;
            if (tlife[i] <= 0) {
                for (int j = i; j < textCount - 1; j++) {
                    tx[j] = tx[j + 1];
                    ty[j] = ty[j + 1];
                    tlife[j] = tlife[j + 1];
                    tsize[j] = tsize[j + 1];
                    tcolor[j] = tcolor[j + 1];
                    text[j] = text[j + 1];
                }
                textCount--;
                i--;
            }
        }
    }

    void draw(Gfx g, float l, float t, float r, float b) {
        for (int i = 0; i < count; i++) {
            float px = x[i], py = y[i];
            float sz = size[i];
            if (px < l - sz * 2 || px > r + sz * 2 || py < t - sz * 2 || py > b + sz * 2) continue;
            float f = life[i] / maxLife[i];
            switch (type[i]) {
                case DOT:
                    g.color(MathUtil.withAlpha(color[i], Math.min(1f, f * 1.5f)));
                    g.fillCircle(px, py, sz * (0.4f + 0.6f * f));
                    break;
                case SPARK:
                    g.color(MathUtil.withAlpha(color[i], f));
                    g.line(px, py, px - vx[i] * 0.035f, py - vy[i] * 0.035f, sz);
                    break;
                case RING: {
                    float rr = sz * (1f - f * f * 0.85f);
                    g.color(MathUtil.withAlpha(color[i], f));
                    g.strokeCircle(px, py, rr, 4f + 10f * f);
                    break;
                }
                case FLASH: {
                    float rr = sz * (0.6f + 0.4f * (1f - f));
                    if (low) {
                        g.color(MathUtil.withAlpha(color[i], f * 0.5f));
                        g.fillCircle(px, py, rr * 0.6f);
                    } else {
                        g.radial(px, py, rr, MathUtil.withAlpha(color[i], f), color[i] & 0x00ffffff);
                        g.radial(px, py, rr * 0.45f, MathUtil.withAlpha(0xffffffff, f * 0.9f), 0x00ffffff);
                    }
                    break;
                }
                case FIRE: {
                    float k = 1f - f;
                    float rr = sz * (0.35f + 0.65f * (float) Math.sqrt(k));
                    if (low) {
                        g.color(MathUtil.withAlpha(0xffff8a2a, f * 0.7f));
                        g.fillCircle(px, py, rr * 0.8f);
                    } else {
                        g.radial(px, py, rr, MathUtil.withAlpha(0xffff6a1a, f * 0.85f), 0x00ff3a0a);
                        g.radial(px, py, rr * 0.6f, MathUtil.withAlpha(0xffffd84a, f), 0x00ffa62e);
                        g.radial(px, py, rr * 0.3f, MathUtil.withAlpha(0xffffffff, f * f), 0x00ffffcc);
                    }
                    break;
                }
                case SMOKE:
                default:
                    g.color(MathUtil.withAlpha(color[i], f * 0.55f));
                    g.fillCircle(px, py, sz * (1.3f - 0.5f * f));
                    break;
            }
        }
    }

    void drawText(Gfx g) {
        for (int i = 0; i < textCount; i++) {
            float f = tlife[i] / 0.9f;
            float pop = f > 0.8f ? 1f + (f - 0.8f) * 2.5f : 1f;
            g.color(MathUtil.withAlpha(tcolor[i], Math.min(1f, f * 2f)));
            g.text(text[i], tx[i], ty[i], tsize[i] * pop, Gfx.ALIGN_CENTER, tsize[i] * 0.16f,
                    MathUtil.withAlpha(0xff000000, Math.min(1f, f * 2f)));
        }
    }
}
