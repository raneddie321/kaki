package com.snakebrawl.myapp.game;

/**
 * Coin packs sold for real money. Product ids match what should be created as in-app products
 * in the Google Play Console. Until Play Billing is wired in (see {@link Platform#launchPurchase}),
 * purchases go through a simulated test checkout that never charges anything.
 */
final class CoinStore {
    static final String[] PRODUCT_IDS = {"coins_500", "coins_1200", "coins_2800", "coins_6500", "coins_15000"};
    static final String[] NAMES = {"Handful of Coins", "Pouch of Coins", "Bag of Coins", "Chest of Coins", "Vault of Coins"};
    static final int[] COINS = {500, 1200, 2800, 6500, 15000};
    static final String[] PRICES = {"$0.99", "$1.99", "$3.99", "$7.99", "$14.99"};
    /** Badge text shown on the card, or null. */
    static final String[] BADGES = {null, "POPULAR", "+15% BONUS", "BEST VALUE", "+35% BONUS"};

    private CoinStore() {}

    static int indexOf(String productId) {
        for (int i = 0; i < PRODUCT_IDS.length; i++) if (PRODUCT_IDS[i].equals(productId)) return i;
        return -1;
    }

    static String format(int n) {
        String s = Integer.toString(n);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append(',');
            b.append(s.charAt(i));
        }
        return b.toString();
    }
}
