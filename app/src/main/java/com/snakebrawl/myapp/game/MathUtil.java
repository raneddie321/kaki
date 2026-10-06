package com.snakebrawl.myapp.game;

import java.util.Random;

final class MathUtil {
    static final float PI = (float) Math.PI;
    static final float TAU = (float) (Math.PI * 2);
    /** Default random source. A World swaps in its own seeded generator while it simulates. */
    static final Random GLOBAL = new Random();
    static Random RNG = GLOBAL;
    /** Cosmetic randomness (particles) that must never affect the simulation. */
    static final Random FX = new Random();

    private MathUtil() {}

    static float rand() {
        return RNG.nextFloat();
    }

    static float rand(float lo, float hi) {
        return lo + RNG.nextFloat() * (hi - lo);
    }

    static int randInt(int n) {
        return RNG.nextInt(n);
    }

    static float frand(float lo, float hi) {
        return lo + FX.nextFloat() * (hi - lo);
    }

    static float frand() {
        return FX.nextFloat();
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    static float dist2(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1, dy = y2 - y1;
        return dx * dx + dy * dy;
    }

    static float dist(float x1, float y1, float x2, float y2) {
        return (float) Math.sqrt(dist2(x1, y1, x2, y2));
    }

    /** Wraps an angle to [-PI, PI). */
    static float wrap(float a) {
        while (a >= PI) a -= TAU;
        while (a < -PI) a += TAU;
        return a;
    }

    static float angleTo(float x1, float y1, float x2, float y2) {
        return (float) StrictMath.atan2(y2 - y1, x2 - x1);
    }

    static float cos(float a) {
        return (float) StrictMath.cos(a);
    }

    static float sin(float a) {
        return (float) StrictMath.sin(a);
    }

    static float sqrt(float v) {
        return (float) Math.sqrt(v);
    }

    static int argb(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    static int withAlpha(int color, float alpha) {
        int a = (int) (clamp(alpha, 0, 1) * ((color >>> 24) & 0xff));
        return (a << 24) | (color & 0x00ffffff);
    }

    /** Mixes two colors; t = 0 gives a, t = 1 gives b. */
    static int mix(int a, int b, float t) {
        t = clamp(t, 0, 1);
        int aa = (a >>> 24) & 0xff, ar = (a >> 16) & 0xff, ag = (a >> 8) & 0xff, ab = a & 0xff;
        int ba = (b >>> 24) & 0xff, br = (b >> 16) & 0xff, bg = (b >> 8) & 0xff, bb = b & 0xff;
        return argb((int) (aa + (ba - aa) * t), (int) (ar + (br - ar) * t),
                (int) (ag + (bg - ag) * t), (int) (ab + (bb - ab) * t));
    }

    static int darker(int c, float f) {
        return (c & 0xff000000) | (mix(c | 0xff000000, 0xff000000, f) & 0xffffff);
    }

    static int lighter(int c, float f) {
        return (c & 0xff000000) | (mix(c | 0xff000000, 0xffffffff, f) & 0xffffff);
    }
}
