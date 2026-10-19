package com.snakebrawl.myapp.game;

import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.File;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import com.snakebrawl.myapp.desktop.AwtGfx;

/**
 * Tests RanEddie ID on the game side against a stand-in for the website's API (same rules as
 * raneddie-id/app/api/game): connect with PKCE, first upload, a second device picking the progress
 * up, sync back on start, and the "which progress" choice. Run: RidTest
 */
public final class RidTest {
    /** The website, reduced to what the game talks to. */
    static final class FakeSite {
        final Map<String, String> codes = new HashMap<>(); // code -> challenge
        String save;
        long savedAt;
        int uploads, conflicts;
        String summary;

        String approve(String challenge) {
            String code = "code" + codes.size();
            codes.put(code, challenge);
            return code;
        }

        synchronized String handle(String method, String path, String bearer, String body) {
            if (path.equals("/api/game/token")) {
                String code = RanEddieId.Json.string(body, "code"), verifier = RanEddieId.Json.string(body, "verifier");
                String ch = codes.remove(code);
                if (ch == null || !ch.equals(jdkChallenge(verifier))) return "400\n{\"error\":\"bad_verifier\"}";
                return "200\n{\"token\":\"tok-ran\",\"username\":\"RanEddie\"}";
            }
            if (!"tok-ran".equals(bearer)) return "401\n{\"error\":\"unauthorized\"}";
            if (path.equals("/api/game/save") && method.equals("GET")) {
                return "200\n{\"username\":\"RanEddie\",\"save\":" + (save == null ? "null" : RanEddieId.Json.quote(save))
                        + ",\"updatedAt\":" + savedAt + "}";
            }
            if (path.equals("/api/game/save") && method.equals("PUT")) {
                if (body.contains("\"baseAt\"") && save != null && savedAt > RanEddieId.Json.number(body, "baseAt")) {
                    conflicts++;
                    return "409\n{\"error\":\"conflict\",\"save\":" + RanEddieId.Json.quote(save) + ",\"updatedAt\":" + savedAt + "}";
                }
                save = RanEddieId.Json.string(body, "save");
                summary = body.substring(body.indexOf("\"summary\""));
                savedAt = Math.max(savedAt + 1, System.currentTimeMillis());
                uploads++;
                return "200\n{\"updatedAt\":" + savedAt + "}";
            }
            return "404\n{}";
        }
    }

    /** What the website computes, using the JDK (checks the game's own SHA-256 and base64url). */
    static String jdkChallenge(String verifier) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes("US-ASCII"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(d);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static final class Phone extends SimTest.DesktopPlatform {
        final FakeSite site;
        String openedUrl, deepLink;
        boolean offline;

        Phone(FakeSite site, int age) {
            this.site = site;
            prefs.put("age", age);
            prefs.put("onboarded", 1);
            strings.put("nickname", "Tester");
        }

        @Override
        public void openUrl(String url) {
            openedUrl = url;
        }

        @Override
        public String httpJson(String method, String url, String bearer, String body) {
            if (offline) return null;
            return site.handle(method, url.substring(RanEddieId.SITE.length()), bearer, body);
        }

        @Override
        public String takeDeepLink() {
            String d = deepLink;
            deepLink = null;
            return d;
        }

        /** The player approves on the website: it sends them back to the game. */
        void approveInBrowser() {
            String state = RanEddieId.queryParam(openedUrl, "state"), ch = RanEddieId.queryParam(openedUrl, "ch");
            deepLink = "snakebrawl://raneddie?state=" + state + "&code=" + site.approve(ch);
        }
    }

    static Gfx gfx;

    static void run(Game g, float seconds) throws Exception {
        for (int i = 0; i < seconds * 60; i++) {
            g.frame(1f / 60f, gfx);
            Thread.sleep(1);
        }
    }

    static String popup(Game g) throws Exception {
        java.lang.reflect.Field open = Game.class.getDeclaredField("popup");
        java.lang.reflect.Field title = Game.class.getDeclaredField("popTitle");
        title.setAccessible(true);
        return open.getBoolean(g) ? (String) title.get(g) : null;
    }

    static void closePopup(Game g) throws Exception {
        java.lang.reflect.Method m = Game.class.getDeclaredMethod("closePopup");
        m.setAccessible(true);
        m.invoke(g);
    }

    static int fails;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "ok   " : "FAIL ") + what);
        if (!ok) fails++;
    }

    public static void main(String[] args) throws Exception {
        Font font = Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/assets/fonts/LilitaOne-Regular.ttf"));
        gfx = new AwtGfx(new BufferedImage(960, 540, BufferedImage.TYPE_INT_RGB), font);

        // SHA-256 against a known vector
        StringBuilder hex = new StringBuilder();
        for (byte b : RanEddieId.sha256("abc".getBytes("US-ASCII"))) hex.append(String.format("%02x", b));
        check(hex.toString().equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"), "sha256(abc)");
        byte[] long1 = new byte[1000];
        for (int i = 0; i < long1.length; i++) long1[i] = (byte) i;
        check(java.util.Arrays.equals(RanEddieId.sha256(long1), MessageDigest.getInstance("SHA-256").digest(long1)), "sha256(1000 bytes)");

        // Profile export/import round trip
        Phone p0 = new Phone(new FakeSite(), 20);
        Profile pr0 = new Profile(p0);
        pr0.trophies = 321;
        pr0.coins = 4567;
        pr0.nickname = "Line\\with\nbreak";
        pr0.levels[2] = 5;
        p0.saveString("codesUsed", "1:2:0,3:4:5");
        String text = pr0.exportText();
        Phone p1 = new Phone(new FakeSite(), 20);
        Profile pr1 = new Profile(p1);
        pr1.importText(text);
        check(pr1.trophies == 321 && pr1.coins == 4567 && pr1.levels[2] == 5 && pr1.nickname.equals("Line\\with\nbreak"),
                "profile export/import keeps progress");
        check("1:2:0,3:4:5".equals(p1.loadString("codesUsed", "")), "used redeem codes travel with the save");
        check(text.length() < 6000, "save fits the 6000-character limit (" + text.length() + ")");

        // Device A: existing player connects; the cloud is empty so A's progress is uploaded
        FakeSite site = new FakeSite();
        Phone a = new Phone(site, 20);
        Game ga = new Game(a);
        ga.resize(1920, 1080);
        run(ga, 4); // loading screen
        ga.profile.trophies = 150;
        ga.profile.games = 5;
        ga.profile.save();
        ga.rid.connect();
        check(a.openedUrl != null && a.openedUrl.startsWith(RanEddieId.SITE + "/connect?game=snakebrawl&state="), "connect opens the website");
        a.approveInBrowser();
        run(ga, 1);
        check(ga.rid.linked() && ga.rid.username().equals("RanEddie"), "A is connected as RanEddie");
        check("RANEDDIE ID CONNECTED!".equals(popup(ga)), "A sees the connected popup");
        closePopup(ga);
        run(ga, 10);
        check(site.save != null && site.save.contains("i:trophies=150"), "A's progress was uploaded");
        check(site.summary != null && site.summary.contains("\"trophies\":150"), "summary for the dashboard was sent");

        // A replayed link (wrong state) is refused
        a.deepLink = "snakebrawl://raneddie?state=old&code=code0";
        run(ga, 0.2f);
        check("RANEDDIE ID".equals(popup(ga)), "an out-of-date link is refused");
        closePopup(ga);

        // Device B: new install, connects to the same RanEddie ID and gets A's progress
        Phone b = new Phone(site, 20);
        Game gb = new Game(b);
        gb.resize(1920, 1080);
        run(gb, 4); // loading screen
        gb.rid.connect();
        b.approveInBrowser();
        run(gb, 1);
        check("WELCOME BACK!".equals(popup(gb)) && gb.profile.trophies == 150, "B loaded A's progress (150 trophies)");
        closePopup(gb);

        // B plays; A, started again, picks B's progress up
        gb.profile.trophies = 210;
        gb.profile.save();
        run(gb, 10);
        check(site.save.contains("i:trophies=210"), "B's progress was uploaded");
        Game ga2 = new Game(a);
        ga2.resize(1920, 1080);
        run(ga2, 4); // loading screen
        run(ga2, 1);
        check(ga2.profile.trophies == 210, "A, restarted, has B's progress (210 trophies)");

        // Device C: has its own progress, connects: asked which progress to keep
        Phone c = new Phone(site, 20);
        Game gc = new Game(c);
        gc.resize(1920, 1080);
        run(gc, 4); // loading screen
        gc.profile.trophies = 40;
        gc.profile.games = 3;
        gc.profile.save();
        gc.rid.connect();
        c.approveInBrowser();
        run(gc, 1);
        check("WELCOME BACK!".equals(popup(gc)) && gc.profile.trophies == 210, "C gets the RanEddie ID progress without a question (210 trophies)");
        closePopup(gc);

        // Offline: changes wait, then upload when back online
        c.offline = true;
        gc.profile.trophies = 222;
        gc.profile.save();
        run(gc, 10);
        check(site.save.contains("i:trophies=210"), "offline: nothing uploaded");
        c.offline = false;
        run(gc, 10);
        check(site.save.contains("i:trophies=222"), "back online: uploaded");

        // Bug report: bought a snake on the phone; the tablet (app left open) still had the old
        // coins and no snake. Now the tablet picks it up when it comes back, and when both
        // devices changed things the progress is merged instead of overwritten.
        FakeSite s2 = new FakeSite();
        Phone phone = new Phone(s2, 20), tablet = new Phone(s2, 20);
        Game gp = new Game(phone), gt = new Game(tablet);
        gp.resize(1920, 1080);
        gt.resize(1920, 1080);
        run(gp, 4);
        run(gt, 4);
        gp.profile.coins = 1000;
        gp.profile.save();
        gp.rid.connect();
        phone.approveInBrowser();
        run(gp, 1);
        closePopup(gp);
        run(gp, 10);
        gt.rid.connect();
        tablet.approveInBrowser();
        run(gt, 1);
        closePopup(gt);
        check(gt.profile.coins == 1000, "tablet connected with the phone's 1000 coins");
        int snake = 0;
        while ((gp.profile.unlocked & (1 << snake)) != 0) snake++;
        gp.profile.unlocked |= 1 << snake;
        gp.profile.coins -= 300;
        gp.profile.save();
        run(gp, 10);
        gt.onPause();
        gt.onResume();
        run(gt, 2);
        check(gt.profile.coins == 700 && (gt.profile.unlocked & (1 << snake)) != 0,
                "tablet back from the background has the snake bought on the phone (" + gt.profile.coins + " coins)");
        // Both change things before syncing: phone spends 200, tablet wins 50 coins and 8 trophies
        int trophies = gt.profile.trophies;
        gp.profile.coins -= 200;
        gp.profile.ownedSkins |= 1 << 3;
        gp.profile.save();
        gt.profile.coins += 50;
        gt.profile.trophies += 8;
        gt.profile.save();
        run(gp, 10);
        run(gt, 12);
        check(s2.conflicts > 0, "the tablet's upload was refused because the phone saved first");
        run(gp, 50); // phone's menu check picks up the merged save
        check(gt.profile.coins == 550 && gt.profile.trophies == trophies + 8 && (gt.profile.ownedSkins & (1 << 3)) != 0,
                "tablet merged both: 550 coins, +8 trophies, phone's skin (" + gt.profile.coins + ")");
        check(gp.profile.coins == 550 && gp.profile.trophies == trophies + 8 && (gp.profile.unlocked & (1 << snake)) != 0,
                "phone has the same merged progress (" + gp.profile.coins + ")");

        // Under 13: not offered
        Phone kid = new Phone(site, 11);
        Game gk = new Game(kid);
        check(!gk.rid.allowed(), "players under 13 aren't offered RanEddie ID");

        // Disconnect keeps progress
        gc.rid.disconnect();
        check(!gc.rid.linked() && gc.profile.trophies == 222, "sign out keeps progress on the device");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
