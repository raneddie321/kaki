package com.snakebrawl.myapp.game;

/**
 * Redeem codes, checked inside the game with no server: each code carries its reward and a
 * signature made with a secret key, so only codes made with tools/codes.py (which has the same
 * key) work. A code works once on a phone; a code made for one player ID works only for that player.
 *
 * Code layout (100 bits, 20 characters, shown as XXXXX-XXXXX-XXXXX-XXXXX): 64 bits of reward
 * (type 4, amount 12, player 30, serial 18) scrambled with the key, then a 36-bit signature.
 */
final class Codes {
    private Codes() {}

    private static final String KEY = "e3e584825457c68a1363580ac43e7fc4";
    /** No I, O, 0 or 1, so codes are easy to type. */
    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    static final int COINS = 0, TROPHIES = 1, BOX = 2, MEGA_BOX = 3, SKIN = 4, BRAWLER = 5, PASS_XP = 6, PASS_PLUS = 7;

    // ------------------------------------------------------------------ player id

    /** This player's ID (30 bits, never 0), made on first use. */
    static int playerId(Platform p) {
        int id = p.loadInt("pid", 0) & 0x3fffffff;
        if (id == 0) {
            long seed = System.currentTimeMillis() * 6364136223846793005L + System.nanoTime();
            id = (int) (mix(seed) & 0x3fffffff);
            if (id == 0) id = 1;
            p.saveInt("pid", id);
        }
        return id;
    }

    /** The ID as 6 characters, for the player to send to whoever makes codes. */
    static String playerIdText(Platform p) {
        int id = playerId(p);
        StringBuilder s = new StringBuilder();
        for (int i = 5; i >= 0; i--) s.append(ALPHABET.charAt((id >>> (i * 5)) & 31));
        return s.toString();
    }

    // ------------------------------------------------------------------ signature

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private static long keyHash(long salt) {
        long h = salt;
        for (int i = 0; i < KEY.length(); i++) h = mix(h ^ KEY.charAt(i) * 0x100000001b3L + i);
        return h;
    }

    private static long signature(long payload) {
        return mix(keyHash(0x5eed) ^ payload) & 0xfffffffffL;
    }

    private static final long MASK46 = (1L << 46) - 1;

    /** Hides the reward: the serial is masked with the key, the rest with a mask that depends on the serial. */
    private static long scramble(long payload) {
        long serial = payload & 0x3ffff;
        long upper = (payload >>> 18) ^ (mix(keyHash(0xface) ^ serial) & MASK46);
        return upper << 18 | ((serial ^ keyHash(0xc0de)) & 0x3ffff);
    }

    private static long unscramble(long hi) {
        long serial = (hi ^ keyHash(0xc0de)) & 0x3ffff;
        long upper = (hi >>> 18) ^ (mix(keyHash(0xface) ^ serial) & MASK46);
        return upper << 18 | serial;
    }

    // ------------------------------------------------------------------ decode

    /** A checked code: what it gives. */
    static final class Reward {
        int type, amount, player, serial;
    }

    /** Reads a typed code; null when it is not a real code. */
    static Reward decode(String text) {
        if (text == null) return null;
        int[] v = new int[20];
        int n = 0;
        String t = text.toUpperCase();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '0') c = 'O';
            if (c == '1') c = 'I';
            int k = ALPHABET.indexOf(c);
            if (k < 0) {
                if (c == '-' || c == ' ' || c == '\n' || c == '\r' || c == '\t') continue;
                return null;
            }
            if (n >= 20) return null;
            v[n++] = k;
        }
        if (n != 20) return null;
        long hi = 0, mac = 0;
        for (int i = 0; i < 100; i++) {
            int bit = (v[i / 5] >>> (4 - i % 5)) & 1;
            if (i < 64) hi = hi << 1 | bit;
            else mac = mac << 1 | bit;
        }
        if (signature(hi) != mac) return null;
        long p = unscramble(hi);
        Reward r = new Reward();
        r.type = (int) (p >>> 60) & 15;
        r.amount = (int) (p >>> 48) & 0xfff;
        r.player = (int) (p >>> 18) & 0x3fffffff;
        r.serial = (int) p & 0x3ffff;
        return r;
    }

    // ------------------------------------------------------------------ redeem

    /** Result shown to the player: title and text, plus popup art (see Game.showInfo). */
    static final class Result {
        String title, text;
        int art, artArg;
        boolean ok;
    }

    static Result redeem(Game game, String text) {
        Platform pf = game.gated;
        Profile pr = game.profile;
        Result res = new Result();
        Reward r = decode(text);
        if (r == null) {
            res.title = "WRONG CODE";
            res.text = "That code doesn't exist. Check the letters and try again.";
            return res;
        }
        if (r.player != 0 && r.player != playerId(pf)) {
            res.title = "NOT YOUR CODE";
            res.text = "This code was made for another player. Your player ID is " + playerIdText(pf) + ".";
            return res;
        }
        String key = r.player + ":" + r.serial + ":" + r.type;
        String used = pf.loadString("codesUsed", "");
        if (("," + used + ",").contains("," + key + ",")) {
            res.title = "ALREADY USED";
            res.text = "This code was already used on this phone.";
            return res;
        }
        res.ok = true;
        int a = Math.max(1, r.amount);
        switch (r.type) {
            case COINS:
                pr.coins += a;
                res.title = "+" + a + " COINS!";
                res.text = "The code worked! " + a + " coins were added.";
                res.art = 1;
                break;
            case TROPHIES:
                pr.trophies += a;
                pr.bestTrophies = Math.max(pr.bestTrophies, pr.trophies);
                res.title = "+" + a + " TROPHIES!";
                res.text = "The code worked! You now have " + pr.trophies + " trophies.";
                break;
            case BOX:
            case MEGA_BOX:
                res.title = null; // the box opens with its own popup
                break;
            case SKIN:
                if (a >= Skin.ALL.length || pr.ownsSkin(a)) {
                    pr.coins += 500;
                    res.title = "+500 COINS!";
                    res.text = a < Skin.ALL.length ? "You already have the " + Skin.ALL[a].name + " skin, so you get 500 coins instead."
                            : "You get 500 coins!";
                    res.art = 1;
                } else {
                    pr.ownedSkins |= 1 << a;
                    res.title = "NEW SKIN!";
                    res.text = "You got the " + Skin.ALL[a].name + " skin! Equip it in the shop.";
                    res.art = 2;
                    res.artArg = a;
                }
                break;
            case BRAWLER:
                if (a >= Brawler.ALL.length || pr.isUnlocked(a)) {
                    pr.coins += 1000;
                    res.title = "+1000 COINS!";
                    res.text = a < Brawler.ALL.length ? "You already have " + Brawler.ALL[a].name + ", so you get 1000 coins instead."
                            : "You get 1000 coins!";
                    res.art = 1;
                } else {
                    pr.unlocked |= 1 << a;
                    res.title = "NEW BRAWLER!";
                    res.text = Brawler.ALL[a].name + " joined your team!";
                    res.art = 3;
                    res.artArg = a;
                }
                break;
            case PASS_XP:
                SnakePass.checkSeason(pr);
                pr.passXp += a;
                res.title = "+" + a + " PASS XP!";
                res.text = "Your Snake Pass moved forward. Claim your rewards!";
                break;
            case PASS_PLUS:
                if (pr.passPlus) {
                    pr.coins += 1000;
                    res.title = "+1000 COINS!";
                    res.text = "You already have Snake Pass+, so you get 1000 coins instead.";
                    res.art = 1;
                } else {
                    pr.passPlus = true;
                    res.title = "SNAKE PASS+!";
                    res.text = "The golden Snake Pass track is open for this season!";
                    res.art = 1;
                }
                break;
            default:
                res.ok = false;
                res.title = "UPDATE THE GAME";
                res.text = "This code needs a newer version of Snake Brawl.";
                return res;
        }
        pf.saveString("codesUsed", used.length() == 0 ? key : used + "," + key);
        pr.save();
        if (r.type == BOX || r.type == MEGA_BOX) game.openRewardBox(r.type == MEGA_BOX);
        return res;
    }

    // ------------------------------------------------------------------ making codes (tests; tools/codes.py does the same)

    static String make(int type, int amount, int player, int serial) {
        long p = ((long) (type & 15) << 60) | ((long) (amount & 0xfff) << 48) | ((long) (player & 0x3fffffff) << 18) | (serial & 0x3ffff);
        long hi = scramble(p);
        long mac = signature(hi);
        StringBuilder s = new StringBuilder();
        int acc = 0;
        for (int i = 0; i < 100; i++) {
            int bit = (int) (i < 64 ? (hi >>> (63 - i)) & 1 : (mac >>> (35 - (i - 64))) & 1);
            acc = acc << 1 | bit;
            if (i % 5 == 4) {
                s.append(ALPHABET.charAt(acc));
                acc = 0;
                if (i % 25 == 24 && i < 99) s.append('-');
            }
        }
        return s.toString();
    }
}
