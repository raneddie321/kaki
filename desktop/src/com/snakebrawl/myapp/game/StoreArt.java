package com.snakebrawl.myapp.game;

import com.snakebrawl.myapp.desktop.AwtGfx;

import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Renders the Google Play feature graphic (1024x500) from the game itself. Usage: StoreArt out.png */
public final class StoreArt {
    public static void main(String[] args) throws Exception {
        int w = 1024, h = 500;
        Font font = Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/assets/fonts/LilitaOne-Regular.ttf"));
        SimTest.DesktopPlatform pf = new SimTest.DesktopPlatform();
        World world = new World(pf, World.MODE_DEMO, null, null, 1, 9, 600);
        for (int i = 0; i < 60 * 12; i++) world.update(1f / 60f);
        world.updateCamera(1f, w, h);
        world.zoom = h / 900f * 1.25f;
        world.updateCamera(1f, w, h);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Gfx g = new AwtGfx(img, font);
        world.render(g);
        // Darken towards the left for the title
        g.vertical(0, 0, w, h, 0x66101236, 0xaa0a0c24);
        g.radial(300, 200, 400, 0x55ffc94a, 0x00ffc94a);
        Game game = new Game(pf);
        game.resize(w, h);
        Ui ui = game.ui;
        float u = h / 1080f;
        g.color(0xff2a1a56);
        g.text("SNAKE BRAWL", 300 + 5, 215 + 7, 92, Gfx.ALIGN_CENTER, 11, 0xff2a1a56);
        g.color(0xffffd23f);
        g.text("SNAKE BRAWL", 300, 215, 92, Gfx.ALIGN_CENTER, 9, 0xff1a1030);
        g.color(0xffffffff);
        g.text("SLITHER  •  SHOOT  •  SURVIVE", 300, 275, 30, Gfx.ALIGN_CENTER, 5, 0xff1a1030);
        // Three brawlers on the right
        int[] ids = {Brawler.NOVA, Brawler.VIPER, Brawler.COBRA};
        for (int i = 0; i < ids.length; i++) {
            Brawler b = Brawler.ALL[ids[i]];
            float cy = 120 + i * 130, cx = 800 + (i == 1 ? -30 : 20);
            g.radial(cx, cy, 120, MathUtil.withAlpha(b.color1, 0.35f), b.color1 & 0x00ffffff);
            ui.snakeArt(g, b, new int[]{b.color1, b.color2}, cx, cy, 0.62f, 0.4f + i);
        }
        ImageIO.write(img, "png", new File(args[0]));
        System.out.println("wrote " + args[0]);
    }
}
