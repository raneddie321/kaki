package com.snakebrawl.myapp.game;

/**
 * Seeded random numbers that come out identical on Android, the desktop and both browser builds
 * (the browser's java.util.Random differs in nextInt(bound)). Same 48-bit LCG as java.util.Random.
 */
final class Rng {
    private static final long MULT = 0x5DEECE66DL, ADD = 0xBL, MASK = (1L << 48) - 1;
    private long seed;

    Rng(long seed) {
        this.seed = (seed ^ MULT) & MASK;
    }

    private int next(int bits) {
        seed = (seed * MULT + ADD) & MASK;
        return (int) (seed >>> (48 - bits));
    }

    float nextFloat() {
        return next(24) / (float) (1 << 24);
    }

    /** Uniform integer in [0, bound), using the same rejection rule as java.util.Random. */
    int nextInt(int bound) {
        if (bound <= 0) return 0;
        if ((bound & -bound) == bound) return (int) ((bound * (long) next(31)) >> 31);
        int bits, val;
        do {
            bits = next(31);
            val = bits % bound;
        } while (bits - val + (bound - 1) < 0);
        return val;
    }
}
