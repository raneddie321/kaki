package com.snakebrawl.myapp.game;

/** Everything saved between sessions: progress, purchases and settings. */
final class Profile {
    static final int GIFT_COOLDOWN_MIN = 240;
    static final int BOX_PRICE = 120;
    static final int MEGA_BOX_PRICE = 450;
    static final int START_COINS = 200;
    /** Players younger than this pick a generated nickname instead of typing one (COPPA). */
    static final int FREE_NAME_AGE = 13;
    /** Online play (WebRTC: the friend and the connection server see IP addresses) starts at this age (COPPA, GDPR). */
    static final int ONLINE_AGE = 16;

    private Platform p;
    /** Called after every save (RanEddie ID uses it to know the progress changed). */
    Runnable onSave;

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

    // Snake Pass (see SnakePass): season it belongs to, XP, claimed tiers (bit masks) and Pass+
    int passSeason, passXp;
    /** Claimed Snake Pass rewards, one bit per tier. */
    long passFree, passPremium;
    boolean passPlus;

    /** Arena picked on the menu (see Maps). */
    int map;
    /** 1 once the first-match tutorial is done. */
    int tutorial;
    /** Daily reward: day number of the last claim and which of the 7 days it was (0..6). */
    int dailyDay = -1, dailyIndex = -1;
    /** Daily quests: the day they belong to, progress of each, and which were claimed (bits). */
    int questDay = -1, questClaimed;
    final int[] questProgress = new int[Quests.COUNT];

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
        passSeason = p.loadInt("passSeason", -1);
        passXp = Math.max(0, p.loadInt("passXp", 0));
        passFree = (p.loadInt("passFree", 0) & 0xffffffffL) | ((long) p.loadInt("passFreeHi", 0) << 32);
        passPremium = (p.loadInt("passPrem", 0) & 0xffffffffL) | ((long) p.loadInt("passPremHi", 0) << 32);
        passPlus = p.loadInt("passPlus", 0) == 1;
        SnakePass.checkSeason(this);
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
        if (club != Clubs.CUSTOM && (club < -1 || club >= Clubs.NAMES.length)) club = -1;
        customClubName = p.loadString("clubName", "");
        customClubBadge = clamp(p.loadInt("clubBadge", 0), 0, Clubs.BADGE_COLORS.length - 1);

        map = p.loadInt("map", Maps.SUNNY);
        if (!Maps.valid(p, map)) map = Maps.SUNNY;
        // Players who already played before the tutorial existed skip it
        tutorial = p.loadInt("tutorial", games > 0 ? 1 : 0);
        dailyDay = p.loadInt("dailyDay", -1);
        dailyIndex = p.loadInt("dailyIndex", -1);
        questDay = p.loadInt("questDay", -1);
        questClaimed = p.loadInt("questClaimed", 0);
        for (int i = 0; i < questProgress.length; i++) questProgress[i] = Math.max(0, p.loadInt("quest" + i, 0));
        Quests.refresh(this);

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
        p.saveInt("passSeason", passSeason);
        p.saveInt("passXp", passXp);
        p.saveInt("passFree", (int) passFree);
        p.saveInt("passFreeHi", (int) (passFree >>> 32));
        p.saveInt("passPrem", (int) passPremium);
        p.saveInt("passPremHi", (int) (passPremium >>> 32));
        p.saveInt("passPlus", passPlus ? 1 : 0);
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
        p.saveInt("map", map);
        p.saveInt("tutorial", tutorial);
        p.saveInt("dailyDay", dailyDay);
        p.saveInt("dailyIndex", dailyIndex);
        p.saveInt("questDay", questDay);
        p.saveInt("questClaimed", questClaimed);
        for (int i = 0; i < questProgress.length; i++) p.saveInt("quest" + i, questProgress[i]);
        p.saveInt("sound", sound ? 1 : 0);
        p.saveInt("vibration", vibration ? 1 : 0);
        p.saveInt("dmgNumbers", damageNumbers ? 1 : 0);
        p.saveInt("lowGfx", lowGraphics ? 1 : 0);
        p.saveInt("leftHanded", leftHanded ? 1 : 0);
        p.saveInt("autoAim", autoAim ? 1 : 0);
        p.saveInt("stickSize", stickSize);
        p.saveInt("camera", camera);
        if (onSave != null && !(p instanceof Recorder)) onSave.run();
    }

    // ------------------------------------------------------------------ cloud save (RanEddie ID)

    /** Saved keys that live outside the profile but belong to the player's progress. */
    private static final String[] EXTRA_KEYS = {"codesUsed"};

    /** All progress as text, one "i:key=value" or "s:key=value" line per saved value. */
    String exportText() {
        Recorder r = new Recorder();
        Platform real = p;
        p = r;
        try {
            save();
        } finally {
            p = real;
        }
        for (String k : EXTRA_KEYS) r.saveString(k, real.loadString(k, ""));
        return r.text();
    }

    /** Replaces the progress with a cloud save made by {@link #exportText()}, then saves it here. */
    void importText(String text) {
        Recorder r = Recorder.parse(text);
        Platform real = p;
        p = r;
        try {
            load();
        } finally {
            p = real;
        }
        save();
        for (String k : EXTRA_KEYS) {
            String v = r.loadString(k, null);
            if (v != null) real.saveString(k, v);
        }
    }

    /** A Platform that only stores saved values, so save()/load() can write or read a cloud save. */
    private static final class Recorder implements Platform {
        private final java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<String, String>();

        static Recorder parse(String text) {
            Recorder r = new Recorder();
            if (text == null) return r;
            for (String line : text.split("\n")) {
                int eq = line.indexOf('=');
                if (line.length() < 3 || line.charAt(1) != ':' || eq < 0) continue;
                r.values.put(line.substring(0, eq), unescape(line.substring(eq + 1)));
            }
            return r;
        }

        String text() {
            StringBuilder b = new StringBuilder();
            for (java.util.Map.Entry<String, String> e : values.entrySet()) {
                b.append(e.getKey()).append('=').append(escape(e.getValue())).append('\n');
            }
            return b.toString();
        }

        private static String escape(String v) {
            return v.replace("\\", "\\\\").replace("\n", "\\n");
        }

        private static String unescape(String v) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < v.length(); i++) {
                char c = v.charAt(i);
                if (c == '\\' && i + 1 < v.length()) {
                    char n = v.charAt(++i);
                    b.append(n == 'n' ? '\n' : n);
                } else {
                    b.append(c);
                }
            }
            return b.toString();
        }

        @Override
        public int loadInt(String key, int def) {
            String v = values.get("i:" + key);
            if (v == null) return def;
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException e) {
                return def;
            }
        }

        @Override
        public void saveInt(String key, int value) {
            values.put("i:" + key, Integer.toString(value));
        }

        @Override
        public String loadString(String key, String def) {
            String v = values.get("s:" + key);
            return v == null ? def : v;
        }

        @Override
        public void saveString(String key, String value) {
            values.put("s:" + key, value == null ? "" : value);
        }

        @Override
        public void playSound(int id, float volume) {}

        @Override
        public void vibrate(int millis) {}

        @Override
        public void requestText(String title, String initial, int maxLength, boolean numeric, TextCallback callback) {}

        @Override
        public boolean launchPurchase(String productId) {
            return false;
        }

        @Override
        public void setNetworkDiscovery(boolean on) {}
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
        passXp = 0;
        passFree = passPremium = 0;
        passPlus = false;
        club = -1;
        hints = 0;
        dailyDay = dailyIndex = -1;
        questDay = -1;
        Quests.refresh(this);
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
    boolean canPlayOnline() {
        return age >= ONLINE_AGE;
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
