package com.kaki.snakebrawl.game;

final class Projectile {
    static final int PELLET = 0;
    static final int BOLT = 1;
    static final int SUPERBOLT = 2;
    static final int BOMB = 3;
    static final int MEGABOMB = 4;
    static final int FLAME = 5;

    boolean active;
    int kind;
    Snake owner;
    float x, y, px, py, vx, vy;
    float traveled, range;
    float damage, radius, knock;
    boolean throughWalls;
    // Lobbed bombs
    float startX, startY, targetX, targetY, flight, t, aoe;

    boolean isBomb() {
        return kind == BOMB || kind == MEGABOMB;
    }
}
