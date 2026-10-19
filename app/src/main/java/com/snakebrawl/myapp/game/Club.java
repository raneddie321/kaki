package com.snakebrawl.myapp.game;

/**
 * Snake Club, the monthly Google Play subscription: 50 coins every day, a free Snake Crate every
 * week, +50% Snake Pass XP, and a CLUB badge with a gold name. Google Play decides whether it is
 * active; the game only keeps track of which daily and weekly rewards were claimed.
 */
final class Club {
    static final String PRODUCT = "snake_club";
    static final int DAILY_COINS = 50;
    static String price = "₪25.90";

    private Club() {
    }

    static int week() {
        return Quests.today() / 7;
    }

    static boolean coinsReady(Profile p) {
        return p.clubActive && p.clubCoinDay != Quests.today();
    }

    static boolean crateReady(Profile p) {
        return p.clubActive && p.clubCrateWeek != week();
    }

    /** Days until the next free crate. */
    static int crateDaysLeft() {
        return 7 - Quests.today() % 7;
    }
}
