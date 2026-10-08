package com.snakebrawl.myapp.game;

/**
 * Daily reward (a 7-day calendar that restarts if a day is missed) and three daily quests that
 * change every day (UTC). Everything is decided from the day number, so it needs no server.
 */
final class Quests {
    private Quests() {}

    static final int COUNT = 3;
    static final int PLAY = 0, KNOCKOUTS = 1, WIN = 2, LENGTH = 3, CUBES = 4, TOP3 = 5, BRAWLER = 6, TOGETHER = 7;
    private static final int TYPES = 8;

    // Daily calendar: type 0 coins, 1 Brawl Box, 2 Mega Box
    static final int[] DAILY_TYPE = {0, 0, 1, 0, 0, 1, 2};
    static final int[] DAILY_COINS = {60, 80, 0, 120, 150, 0, 0};

    static int today() {
        return (int) (System.currentTimeMillis() / 86400000L);
    }

    /** Hours until the next day starts (new quests, next daily reward). */
    static int hoursLeft() {
        long ms = 86400000L - System.currentTimeMillis() % 86400000L;
        return (int) Math.max(1, (ms + 3599999L) / 3600000L);
    }

    // ------------------------------------------------------------------ daily reward

    static boolean dailyReady(Profile p) {
        return p.dailyDay != today();
    }

    /** Which calendar day (0..6) the next claim gives: the streak continues only from yesterday. */
    static int dailyNext(Profile p) {
        int t = today();
        if (p.dailyDay == t) return p.dailyIndex;
        return p.dailyDay == t - 1 ? (p.dailyIndex + 1) % 7 : 0;
    }

    /** Claims today's reward; returns the calendar day claimed, or -1 if already claimed today. */
    static int claimDaily(Profile p) {
        if (!dailyReady(p)) return -1;
        int d = dailyNext(p);
        p.dailyIndex = d;
        p.dailyDay = today();
        p.coins += DAILY_COINS[d];
        p.save();
        return d;
    }

    // ------------------------------------------------------------------ quests

    /** Starts a new set of quests when the day has changed. */
    static void refresh(Profile p) {
        int t = today();
        if (p.questDay == t) return;
        p.questDay = t;
        p.questClaimed = 0;
        for (int i = 0; i < COUNT; i++) p.questProgress[i] = 0;
    }

    private static int hash(int day, int k) {
        int h = day * 73856093 ^ (k + 1) * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return (h ^ h >>> 15) & 0x7fffffff;
    }

    /** Quest type of slot i today: three different types. */
    static int type(Profile p, int i) {
        int[] picked = new int[COUNT];
        for (int k = 0; k <= i; k++) {
            int t = hash(p.questDay, k * 3 + 1) % TYPES;
            boolean dup = true;
            while (dup) {
                dup = false;
                for (int j = 0; j < k; j++) if (picked[j] == t) dup = true;
                if (dup) t = (t + 1) % TYPES;
            }
            picked[k] = t;
        }
        return picked[i];
    }

    /** The brawler a BRAWLER quest asks for: one the player owns. */
    static int brawler(Profile p, int i) {
        int owned = 0;
        for (int b = 0; b < Brawler.ALL.length; b++) if (p.isUnlocked(b)) owned++;
        int pick = hash(p.questDay, i * 3 + 2) % Math.max(1, owned);
        for (int b = 0; b < Brawler.ALL.length; b++) {
            if (!p.isUnlocked(b)) continue;
            if (pick-- == 0) return b;
        }
        return 0;
    }

    static int target(Profile p, int i) {
        switch (type(p, i)) {
            case PLAY:
                return 3;
            case KNOCKOUTS:
                return 6 + hash(p.questDay, i) % 3 * 2;
            case WIN:
                return 1;
            case LENGTH:
                return 200 + hash(p.questDay, i) % 3 * 50;
            case CUBES:
                return 6 + hash(p.questDay, i) % 3 * 2;
            case TOP3:
                return 2;
            case BRAWLER:
                return 2;
            default:
                return 1;
        }
    }

    static int coins(Profile p, int i) {
        int t = type(p, i);
        return t == WIN || t == TOP3 ? 80 : t == TOGETHER ? 70 : 50;
    }

    static int xp(Profile p, int i) {
        int t = type(p, i);
        return t == WIN || t == TOP3 ? 60 : 40;
    }

    static String text(Profile p, int i) {
        int n = target(p, i);
        switch (type(p, i)) {
            case PLAY:
                return "Play " + n + " matches";
            case KNOCKOUTS:
                return "Knock out " + n + " snakes";
            case WIN:
                return "Win a Showdown or Duo match";
            case LENGTH:
                return "Reach length " + n + " in one match";
            case CUBES:
                return "Collect " + n + " power cubes";
            case TOP3:
                return "Finish top 3 in Showdown twice";
            case BRAWLER:
                return "Play 2 matches with " + Brawler.ALL[brawler(p, i)].name;
            default:
                return "Play a match with friends";
        }
    }

    static boolean done(Profile p, int i) {
        return p.questProgress[i] >= target(p, i);
    }

    static boolean claimed(Profile p, int i) {
        return (p.questClaimed & 1 << i) != 0;
    }

    static int readyCount(Profile p) {
        refresh(p);
        int n = dailyReady(p) ? 1 : 0;
        for (int i = 0; i < COUNT; i++) if (done(p, i) && !claimed(p, i)) n++;
        return n;
    }

    /** Counts a finished match towards today's quests. Returns how many quests it completed. */
    static int onMatch(Profile p, int mode, int rank, int kills, int length, int cubes, boolean win, int brawler, boolean friends) {
        refresh(p);
        int finished = 0;
        for (int i = 0; i < COUNT; i++) {
            if (done(p, i)) continue;
            int add = 0;
            switch (type(p, i)) {
                case PLAY:
                    add = 1;
                    break;
                case KNOCKOUTS:
                    add = kills;
                    break;
                case WIN:
                    add = win && mode != World.MODE_ENDLESS ? 1 : 0;
                    break;
                case LENGTH:
                    if (length >= target(p, i)) add = target(p, i);
                    break;
                case CUBES:
                    add = cubes;
                    break;
                case TOP3:
                    add = mode == World.MODE_SHOWDOWN && rank >= 1 && rank <= 3 ? 1 : 0;
                    break;
                case BRAWLER:
                    add = brawler == brawler(p, i) ? 1 : 0;
                    break;
                default:
                    add = friends ? 1 : 0;
                    break;
            }
            p.questProgress[i] = Math.min(target(p, i), p.questProgress[i] + add);
            if (done(p, i)) finished++;
        }
        return finished;
    }

    /** Claims a finished quest's reward; returns false if it isn't ready. */
    static boolean claim(Profile p, int i) {
        refresh(p);
        if (!done(p, i) || claimed(p, i)) return false;
        p.questClaimed |= 1 << i;
        p.coins += coins(p, i);
        SnakePass.checkSeason(p);
        p.passXp += xp(p, i);
        p.save();
        return true;
    }
}
