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
        return (float) atan2(y2 - y1, x2 - x1);
    }

    static float cos(float a) {
        return (float) cosD(a);
    }

    static float sin(float a) {
        return (float) sinD(a);
    }

    // Deterministic math for multiplayer lockstep. These use only +, -, *, / and sqrt, which are
    // exactly rounded everywhere, so every phone and every browser engine (where Math.sin and
    // friends may differ in the last bit) computes bit-identical results.

    private static final double DPI = Math.PI, DTAU = Math.PI * 2, HALF_PI = Math.PI / 2;

    static double sinD(double x) {
        if (x != x || x == Double.POSITIVE_INFINITY || x == Double.NEGATIVE_INFINITY) return Double.NaN;
        x -= DTAU * Math.floor(x / DTAU + 0.5); // now in [-PI, PI]
        if (x > HALF_PI) x = DPI - x;
        else if (x < -HALF_PI) x = -DPI - x;
        double x2 = x * x;
        // Taylor series to x^17: error below 1e-14 on [-PI/2, PI/2]
        return x * (1 - x2 / 6 * (1 - x2 / 20 * (1 - x2 / 42 * (1 - x2 / 72 * (1 - x2 / 110 * (1 - x2 / 156
                * (1 - x2 / 210 * (1 - x2 / 272))))))));
    }

    static double cosD(double x) {
        return sinD(x + HALF_PI);
    }

    static double atan2(double y, double x) {
        if (x == 0 && y == 0) return 0;
        if (x == 0) return y > 0 ? HALF_PI : -HALF_PI;
        double a = atan(y / x);
        if (x > 0) return a;
        return y >= 0 ? a + DPI : a - DPI;
    }

    static double atan(double t) {
        boolean neg = t < 0;
        if (neg) t = -t;
        boolean inv = t > 1;
        if (inv) t = 1 / t;
        // Two half-angle steps bring t below tan(PI/16), then a short series
        t = t / (1 + Math.sqrt(1 + t * t));
        t = t / (1 + Math.sqrt(1 + t * t));
        double t2 = t * t;
        double r = 4 * t * (1 - t2 * (1.0 / 3 - t2 * (1.0 / 5 - t2 * (1.0 / 7 - t2 * (1.0 / 9 - t2 * (1.0 / 11
                - t2 * (1.0 / 13 - t2 * (1.0 / 15 - t2 / 17))))))));
        if (inv) r = HALF_PI - r;
        return neg ? -r : r;
    }

    static double exp(double x) {
        if (x > 700) return Double.POSITIVE_INFINITY;
        if (x < -700) return 0;
        int halvings = 0;
        while (x > 0.5 || x < -0.5) {
            x *= 0.5;
            halvings++;
        }
        double term = 1, sum = 1;
        for (int i = 1; i < 18; i++) {
            term *= x / i;
            sum += term;
        }
        for (int i = 0; i < halvings; i++) sum *= sum;
        return sum;
    }

    /** Natural log for positive x. */
    static double log(double x) {
        if (x <= 0) return Double.NEGATIVE_INFINITY;
        int e = 0;
        while (x > 1.5) {
            x *= 0.5;
            e++;
        }
        while (x < 0.75) {
            x *= 2;
            e--;
        }
        double z = (x - 1) / (x + 1), z2 = z * z, term = z, sum = 0;
        for (int i = 1; i < 40; i += 2) {
            sum += term / i;
            term *= z2;
        }
        return 2 * sum + e * 0.6931471805599453;
    }

    static double pow(double a, double b) {
        if (a == 0) return b == 0 ? 1 : 0;
        return exp(b * log(a));
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
