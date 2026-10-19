package com.snakebrawl.myapp.game;

/** One player's controls for one simulation tick of a Wi-Fi game. */
final class NetInput {
    static final int NONE = 0, ATTACK = 1, SUPER = 2;

    int tick;
    boolean steer;
    float ang;
    boolean boost;
    int attack;
    float atkAng, atkDist;

    void clear() {
        steer = false;
        ang = 0;
        boost = false;
        attack = NONE;
        atkAng = 0;
        atkDist = 0;
    }

    void copyFrom(NetInput o) {
        tick = o.tick;
        steer = o.steer;
        ang = o.ang;
        boost = o.boost;
        attack = o.attack;
        atkAng = o.atkAng;
        atkDist = o.atkDist;
    }
}
