package com.snakebrawl.myapp.game;

/**
 * The Snake Pass: a season of 50 tiers. Every match earns pass XP; each tier has a free reward
 * and a Snake Pass+ reward (Pass+ is bought with coins). Seasons last 30 days and reset the pass.
 */
final class SnakePass {
    static final int TIERS = 50;
    static final int XP_PER_TIER = 150;
    static final int SEASON_DAYS = 30;
    static final int PLUS_PRICE = 1200;
    /** Snake Pass MAX: a Google Play purchase, consumed so it can be bought again next season. */
    static final String MAX_PRODUCT = "snakepass_max";
    /** Price shown until Google Play reports the local one. */
    static String maxPrice = "₪23.90";

    /** This season's Snake Pass MAX skin. */
    static int maxSkin(int season) {
        return Skin.MAX_SKINS[Math.max(0, season - 1) % Skin.MAX_SKINS.length];
    }
    /** Day number (since 1970) when season 1 started: 2026-01-01. */
    private static final long FIRST_DAY = 20454;

    static final int R_COINS = 0, R_BOX = 1, R_MEGA = 2, R_SKIN = 3, R_BRAWLER = 4;
    static final int TRACK_FREE = 0, TRACK_PLUS = 1;

    /** Skins that are the headline rewards (also sold in the shop). */
    static final int SKIN_FREE = 22, SKIN_PLUS = 21;
    static final int BRAWLER_PLUS = Brawler.COBRA;

    private SnakePass() {}

    private static long today() {
        return System.currentTimeMillis() / 86400000L;
    }

    static int currentSeason() {
        return (int) Math.max(1, (today() - FIRST_DAY) / SEASON_DAYS + 1);
    }

    static int daysLeft() {
        long d = (today() - FIRST_DAY) % SEASON_DAYS;
        return (int) (SEASON_DAYS - Math.max(0, d));
    }

    /** Starts a fresh pass when a new season has begun. */
    static void checkSeason(Profile p) {
        int s = currentSeason();
        if (p.passSeason == s) return;
        p.passSeason = s;
        p.passXp = 0;
        p.passFree = 0;
        p.passPremium = 0;
        p.passPlus = false;
        p.passMax = false;
    }

    static int tier(Profile p) {
        return Math.min(TIERS, p.passXp / XP_PER_TIER);
    }

    /** XP into the current tier, 0..XP_PER_TIER. */
    static int tierXp(Profile p) {
        return tier(p) >= TIERS ? XP_PER_TIER : p.passXp % XP_PER_TIER;
    }

    /** Reward type for a tier (1..TIERS) on a track. */
    static int type(int track, int t) {
        if (track == TRACK_FREE) {
            if (t == 35) return R_SKIN;
            if (t == 25 || t == TIERS) return R_MEGA;
            return t % 5 == 0 ? R_BOX : R_COINS;
        }
        if (t == TIERS) return R_SKIN;
        if (t == 25) return R_BRAWLER;
        if (t % 10 == 0) return R_MEGA;
        return t % 4 == 0 ? R_BOX : R_COINS;
    }

    /** Coins for coin rewards, the skin id for skins, the brawler id for brawlers. */
    static int amount(int track, int t) {
        switch (type(track, t)) {
            case R_SKIN:
                return track == TRACK_FREE ? SKIN_FREE : SKIN_PLUS;
            case R_BRAWLER:
                return BRAWLER_PLUS;
            case R_COINS:
                return track == TRACK_FREE ? 20 + (t * 3 / 10) * 10 : 50 + (t * 5 / 10) * 10;
            default:
                return 0;
        }
    }

    static boolean claimed(Profile p, int track, int t) {
        long mask = track == TRACK_FREE ? p.passFree : p.passPremium;
        return (mask & (1L << (t - 1))) != 0;
    }

    static boolean canClaim(Profile p, int track, int t) {
        return t <= tier(p) && !claimed(p, track, t) && (track == TRACK_FREE || p.passPlus);
    }

    /** Rewards ready to claim right now. */
    static int readyCount(Profile p) {
        int n = 0;
        for (int t = 1; t <= tier(p); t++) {
            if (canClaim(p, TRACK_FREE, t)) n++;
            if (canClaim(p, TRACK_PLUS, t)) n++;
        }
        return n;
    }

    /** Pass XP for a finished match. */
    static int matchXp(int mode, int rank, int kills, boolean win) {
        int xp;
        if (mode == World.MODE_SHOWDOWN) {
            int[] byRank = {40, 32, 26, 22, 18, 14, 11, 9, 7, 5};
            xp = byRank[Profile.clamp(rank - 1, 0, byRank.length - 1)];
        } else if (mode == World.MODE_DUO) {
            int[] byRank = {40, 28, 20, 12, 8};
            xp = byRank[Profile.clamp(rank - 1, 0, byRank.length - 1)];
        } else if (mode == World.MODE_BALL) {
            xp = rank == 1 ? 35 : 15;
        } else if (mode == World.MODE_EGGS) {
            int[] byRank = {40, 30, 24, 18, 14, 10, 8, 6};
            xp = byRank[Profile.clamp(rank - 1, 0, byRank.length - 1)];
        } else {
            xp = 12;
        }
        return xp + Math.min(15, kills * 3) + (win ? 10 : 0);
    }

    static String name(int track, int t) {
        int a = amount(track, t);
        switch (type(track, t)) {
            case R_COINS:
                return a + " coins";
            case R_BOX:
                return "Snake Crate";
            case R_MEGA:
                return "Mega Crate";
            case R_SKIN:
                return Skin.ALL[a].name + " skin";
            default:
                return Brawler.ALL[a].name;
        }
    }
}
