package com.snakebrawl.myapp.game;

/** Everything saved between sessions: progress, purchases and settings. */
final class Profile {
    static final int GIFT_COOLDOWN_MIN = 240;
    static final int BOX_PRICE = 120;
    static final int MEGA_BOX_PRICE = 450;
    static final int START_COINS = 200;

    private final Platform p;

    int coins;
    int trophies, bestTrophies, wins, games, bestLen, totalKills, hints;
    int selected, mode, skin;
    int ownedSkins;      // bit mask
    int unlocked;        // bit mask of brawlers
    final int[] levels = new int[Brawler.ALL.length];
    int nextGiftMinute;

    // Player identity (asked on first launch)
    String nickname;
    int age;
    boolean onboarded;

    // Club: -1 none, 0..N-1 one of the built-in clubs, Clubs.CUSTOM for a club the player created
    int club = -1;
    String customClubName;
    int customClubBadge;

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
        mode = clamp(p.loadInt("mode", 0), 0, 2);
        skin = clamp(p.loadInt("skin", 0), 0, Skin.ALL.length - 1);
        ownedSkins = p.loadInt("ownedSkins", 1) | 1;
        // The four original brawlers are always free
        unlocked = p.loadInt("unlocked", 0) | 0xf;
        for (int i = 0; i < levels.length; i++) levels[i] = clamp(p.loadInt("lvl" + i, 1), 1, Brawler.MAX_LEVEL);
        selected = clamp(p.loadInt("brawler", 0), 0, Brawler.ALL.length - 1);
        if (!isUnlocked(selected)) selected = 0;
        if (!ownsSkin(skin)) skin = 0;
        nextGiftMinute = p.loadInt("nextGift", 0);
        nickname = p.loadString("nickname", "");
        age = p.loadInt("age", 0);
        onboarded = p.loadInt("onboarded", 0) == 1 && nickname.length() > 0 && age > 0;
        club = p.loadInt("club", -1);
        customClubName = p.loadString("clubName", "");
        customClubBadge = p.loadInt("clubBadge", 0);

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
        p.saveInt("skin", skin);
        p.saveInt("ownedSkins", ownedSkins);
        p.saveInt("unlocked", unlocked);
        for (int i = 0; i < levels.length; i++) p.saveInt("lvl" + i, levels[i]);
        p.saveInt("brawler", selected);
        p.saveInt("nextGift", nextGiftMinute);
        p.saveString("nickname", nickname);
        p.saveInt("age", age);
        p.saveInt("onboarded", onboarded ? 1 : 0);
        p.saveInt("club", club);
        p.saveString("clubName", customClubName);
        p.saveInt("clubBadge", customClubBadge);
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
        skin = 0;
        ownedSkins = 1;
        unlocked = 0xf;
        selected = 0;
        for (int i = 0; i < levels.length; i++) levels[i] = 1;
        nextGiftMinute = 0;
        save();
    }

    static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    static int nowMinute() {
        return (int) (System.currentTimeMillis() / 60000L);
    }

    boolean isUnlocked(int b) {
        return (unlocked & (1 << b)) != 0;
    }

    boolean ownsSkin(int s) {
        return (ownedSkins & (1 << s)) != 0;
    }

    int upgradeCost(int b) {
        int lvl = levels[b];
        return lvl >= Brawler.MAX_LEVEL ? -1 : Brawler.UPGRADE_COST[lvl - 1];
    }

    /** Players under 13 can't make real-money purchases. */
    boolean canPurchase() {
        return age >= 13;
    }

    String displayName() {
        return nickname == null || nickname.length() == 0 ? "You" : nickname;
    }

    boolean giftReady() {
        return nowMinute() >= nextGiftMinute;
    }

    boolean spend(int amount) {
        if (amount < 0 || coins < amount) return false;
        coins -= amount;
        return true;
    }

    int[] palette() {
        return Skin.ALL[skin].paletteFor(Brawler.ALL[selected]);
    }
}
