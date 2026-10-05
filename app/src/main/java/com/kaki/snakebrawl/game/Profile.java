package com.kaki.snakebrawl.game;

/** Everything saved between sessions: progress, purchases and settings. */
final class Profile {
    static final int GIFT_COOLDOWN_MIN = 240;
    static final int BOX_PRICE = 120;
    static final int MEGA_BOX_PRICE = 450;
    static final int START_COINS = 200;

    private final Platform p;

    int coins;
    int trophies, bestTrophies, wins, games, bestLen, totalKills, hints;
    int mode;
    int heroLevel;
    int nextGiftMinute;

    // Settings
    boolean sound, vibration, damageNumbers, lowGraphics, leftHanded, autoAim;
    int stickSize;  // 0 small, 1 medium, 2 large
    int camera;     // 0 close, 1 normal, 2 far

    Profile(Platform p) {
        this.p = p;
        load();
    }

    void load() {
        coins = p.loadInt("coins", START_COINS);
        trophies = Math.max(0, p.loadInt("trophies", 0));
        bestTrophies = Math.max(trophies, p.loadInt("bestTrophies", 0));
        wins = p.loadInt("wins", 0);
        games = p.loadInt("games", 0);
        bestLen = p.loadInt("bestLen", 0);
        totalKills = p.loadInt("totalKills", 0);
        hints = p.loadInt("hints", 0);
        mode = clamp(p.loadInt("mode", 0), 0, 1);
        heroLevel = clamp(p.loadInt("heroLvl", 1), 1, Brawler.MAX_LEVEL);
        nextGiftMinute = p.loadInt("nextGift", 0);

        sound = p.loadInt("sound", 1) == 1;
        vibration = p.loadInt("vibration", 1) == 1;
        damageNumbers = p.loadInt("dmgNumbers", 1) == 1;
        lowGraphics = p.loadInt("lowGfx", 0) == 1;
        leftHanded = p.loadInt("leftHanded", 0) == 1;
        autoAim = p.loadInt("autoAim", 1) == 1;
        stickSize = clamp(p.loadInt("stickSize", 1), 0, 2);
        camera = clamp(p.loadInt("camera", 1), 0, 2);
    }

    void save() {
        p.saveInt("coins", coins);
        p.saveInt("trophies", trophies);
        p.saveInt("bestTrophies", bestTrophies);
        p.saveInt("wins", wins);
        p.saveInt("games", games);
        p.saveInt("bestLen", bestLen);
        p.saveInt("totalKills", totalKills);
        p.saveInt("hints", hints);
        p.saveInt("mode", mode);
        p.saveInt("heroLvl", heroLevel);
        p.saveInt("nextGift", nextGiftMinute);
        p.saveInt("sound", sound ? 1 : 0);
        p.saveInt("vibration", vibration ? 1 : 0);
        p.saveInt("dmgNumbers", damageNumbers ? 1 : 0);
        p.saveInt("lowGfx", lowGraphics ? 1 : 0);
        p.saveInt("leftHanded", leftHanded ? 1 : 0);
        p.saveInt("autoAim", autoAim ? 1 : 0);
        p.saveInt("stickSize", stickSize);
        p.saveInt("camera", camera);
    }

    /** Wipes progress and purchases but keeps settings. */
    void resetProgress() {
        coins = START_COINS;
        trophies = bestTrophies = wins = games = bestLen = totalKills = 0;
        heroLevel = 1;
        nextGiftMinute = 0;
        save();
    }

    static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    static int nowMinute() {
        return (int) (System.currentTimeMillis() / 60000L);
    }

    /** Coins for the next hero upgrade, or -1 at max level. */
    int upgradeCost() {
        return heroLevel >= Brawler.MAX_LEVEL ? -1 : Brawler.UPGRADE_COST[heroLevel - 1];
    }

    boolean giftReady() {
        return nowMinute() >= nextGiftMinute;
    }

    boolean spend(int amount) {
        if (amount < 0 || coins < amount) return false;
        coins -= amount;
        return true;
    }

}
