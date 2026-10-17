package com.snakebrawl.myapp.game;

final class Projectile {
    static final int PELLET = 0;
    static final int BOLT = 1;
    static final int SUPERBOLT = 2;
    static final int BOMB = 3;
    static final int MEGABOMB = 4;
    static final int FLAME = 5;
    static final int SHARD = 6;
    static final int BALL = 7;
    static final int SHURIKEN = 8;
    static final int GLOB = 9;
    static final int MEGAGLOB = 10;
    static final int BULLET = 11;
    static final int SPIKE = 12;
    static final int NEEDLE = 13;
    static final int SEED = 14;
    static final int WAVE = 15;
    static final int ORB = 16;
    static final int METEOR = 17;
    static final int CARD = 18;
    static final int SLASH = 19;
    static final int LAVA = 20;
    static final int PIXEL = 21;

    boolean active;
    int kind;
    Snake owner;
    float x, y, px, py, vx, vy;
    float traveled, range;
    float damage, radius, knock;
    boolean throughWalls;
    int bounces;
    float slowFactor, slowDur;
    /** Thorn's spikes split into this many needles when they stop. */
    int split;
    // Recent positions for the glowing trail
    static final int TRAIL = 7;
    final float[] trailX = new float[TRAIL], trailY = new float[TRAIL];
    int trailCount;

    void pushTrail() {
        System.arraycopy(trailX, 0, trailX, 1, TRAIL - 1);
        System.arraycopy(trailY, 0, trailY, 1, TRAIL - 1);
        trailX[0] = x;
        trailY[0] = y;
        if (trailCount < TRAIL) trailCount++;
    }

    // Lobbed bombs and globs
    float startX, startY, targetX, targetY, flight, t, aoe;

    boolean isBomb() {
        return kind == BOMB || kind == MEGABOMB || kind == GLOB || kind == MEGAGLOB || kind == SEED || kind == METEOR || kind == LAVA;
    }
}
