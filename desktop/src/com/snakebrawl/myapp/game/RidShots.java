package com.snakebrawl.myapp.game;

import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import com.snakebrawl.myapp.desktop.AwtGfx;

/** Screenshots of the RanEddie ID screens in the game: RidShots outDir */
public final class RidShots {
    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "rid-shots";
        new File(out).mkdirs();
        Font font = Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/assets/fonts/LilitaOne-Regular.ttf"));
        int w = 1600, h = 900;

        // 1. Right after the first-launch name and age: the RanEddie ID offer
        RidTest.Phone p = new RidTest.Phone(new RidTest.FakeSite(), 15);
        p.prefs.put("onboarded", 0);
        Game g = new Game(p);
        g.resize(w, h);
        shot(g, font, w, h, 4f, null);
        g.profile.nickname = "Ran";
        g.profile.age = 15;
        g.profile.onboarded = true;
        g.setScreen(Game.MENU);
        g.confirm("WELCOME, RAN!", "Save your progress with a RanEddie ID: keep your snakes, coins and trophies safe and keep "
                + "playing on any device.", 3, g.profile.selected, Game.ACT_RID_CONNECT, 0, "CONNECT", "LATER");
        shot(g, font, w, h, 1f, out + "/game-1-welcome-offer.png");

        // 2. Settings, not connected
        RidTest.closePopup(g);
        g.setScreen(Game.SETTINGS);
        shot(g, font, w, h, 1f, out + "/game-2-settings.png");

        // 3. Settings, connected
        p.strings.put("rid_token", "tok-ran");
        p.strings.put("rid_user", "RanEddie");
        Game g2 = new Game(p);
        g2.resize(w, h);
        shot(g2, font, w, h, 4f, null);
        g2.setScreen(Game.SETTINGS);
        shot(g2, font, w, h, 1f, out + "/game-3-settings-connected.png");

        System.exit(0);
    }

    static void shot(Game g, Font font, int w, int h, float seconds, String path) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Gfx gfx = new AwtGfx(img, font);
        for (int i = 0; i < seconds * 60; i++) g.frame(1f / 60f, gfx);
        if (path != null) ImageIO.write(img, "png", new File(path));
    }
}
