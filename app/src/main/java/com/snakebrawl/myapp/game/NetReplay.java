package com.snakebrawl.myapp.game;

/**
 * Cross-platform determinism check: simulates a two-player match from a seed with scripted
 * inputs and returns state hashes. The Android app, the desktop harness and the browser build
 * must all produce the same string for cross-play to stay in sync.
 */
public final class NetReplay {
    private NetReplay() {}

    /** Duo, then Snake Ball and Egg Heist (on two of the newer maps), joined with " | ". */
    public static String run(Platform platform, long seed, int ticks) {
        return run(platform, seed, ticks, World.MODE_DUO, Maps.SUNNY) + " | " + run(platform, seed, ticks, World.MODE_BALL, Maps.CANDY)
                + " | " + run(platform, seed, ticks, World.MODE_EGGS, Maps.JUNGLE);
    }

    private static String run(Platform platform, long seed, int ticks, int mode, int map) {
        Rng prev = MathUtil.RNG;
        World w;
        MathUtil.RNG = new Rng(seed);
        try {
            if (mode == World.MODE_DUO) w = new World(platform, World.MODE_DUO, Brawler.ALL[1], null, 3, Brawler.ALL[2], null, 2, 0, 8, 120);
            else w = new World(platform, mode, new Brawler[]{Brawler.ALL[1], Brawler.ALL[2]}, new int[2][], new int[]{3, 2}, 0,
                    mode == World.MODE_BALL ? 4 : 6, 120, 2, map, null);
        } finally {
            MathUtil.RNG = prev;
        }
        NetInput a = new NetInput(), b = new NetInput();
        int lcg = (int) seed;
        StringBuilder out = new StringBuilder();
        for (int t = 0; t < ticks; t++) {
            lcg = lcg * 1103515245 + 12345;
            int r = (lcg >>> 8) & 0xffff;
            fill(a, t, r);
            fill(b, t, r ^ 0x5a5a);
            w.netStep(1f / 60f, a, b);
            if (t % 60 == 59) out.append(Integer.toHexString(w.stateHash())).append(' ');
        }
        return out.toString().trim();
    }

    private static void fill(NetInput in, int t, int r) {
        in.clear();
        in.tick = t;
        in.steer = (r & 3) != 0;
        in.ang = (float) (t * 0.031 + (r & 255) / 40.0);
        in.boost = (r & 0x700) == 0x700;
        int k = (r >> 11) & 31;
        in.attack = k == 1 ? NetInput.ATTACK : (k == 2 ? NetInput.SUPER : NetInput.NONE);
        in.atkAng = (float) (r / 1000.0);
        in.atkDist = 300 + (r & 511);
        NetCodec.normalize(in);
    }
}
