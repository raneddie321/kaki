package com.snakebrawl.myapp.game;

import com.snakebrawl.myapp.desktop.AwtGfx;

import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/** Renders the launcher icons with the game's own snake renderer. Usage: IconGen resDir storeIconPath */
public final class IconGen {
    private static final String[] DENSITIES = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
    private static final float[] SCALES = {1f, 1.5f, 2f, 3f, 4f};

    public static void main(String[] args) throws Exception {
        String res = args[0];
        Font font = Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/assets/fonts/LilitaOne-Regular.ttf"));
        for (int i = 0; i < DENSITIES.length; i++) {
            File dir = new File(res, "mipmap-" + DENSITIES[i]);
            dir.mkdirs();
            int legacy = Math.round(48 * SCALES[i]);
            write(render(legacy, font, true, true, true), new File(dir, "ic_launcher.png"));
            int adaptive = Math.round(108 * SCALES[i]);
            write(render(adaptive, font, false, true, false), new File(dir, "ic_launcher_foreground.png"));
            write(render(adaptive, font, true, false, false), new File(dir, "ic_launcher_background.png"));
        }
        if (args.length > 1) write(render(512, font, true, true, false), new File(args[1]));
    }

    private static void write(BufferedImage img, File f) throws Exception {
        ImageIO.write(img, "png", f);
        System.out.println("wrote " + f);
    }

    /**
     * Draws in a 108x108 design space. Legacy icons use a rounded square and a bigger snake;
     * adaptive foregrounds keep the snake inside the 66dp safe zone.
     */
    private static BufferedImage render(int px, Font font, boolean bg, boolean fg, boolean legacy) {
        BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
        AwtGfx g = new AwtGfx(img, font);
        g.scale(px / 108f);
        if (bg) {
            if (legacy) {
                g.color(0xff14142a);
                g.fillRoundRect(2, 2, 106, 106, 22);
                g.color(0xff3a2fb0);
                g.fillRoundRect(5, 5, 103, 103, 19);
            } else {
                g.color(0xff3a2fb0);
                g.fillRect(0, 0, 108, 108);
            }
            // Brawl-style starburst
            float[] poly = new float[6];
            for (int k = 0; k < 12; k++) {
                double a0 = k * Math.PI * 2 / 12, a1 = a0 + Math.PI / 12;
                poly[0] = 54;
                poly[1] = 54;
                poly[2] = 54 + (float) Math.cos(a0) * 90;
                poly[3] = 54 + (float) Math.sin(a0) * 90;
                poly[4] = 54 + (float) Math.cos(a1) * 90;
                poly[5] = 54 + (float) Math.sin(a1) * 90;
                g.color(0x1affffff);
                if (legacy) {
                    // keep rays inside the rounded square
                    poly[2] = 54 + (float) Math.cos(a0) * 50;
                    poly[3] = 54 + (float) Math.sin(a0) * 50;
                    poly[4] = 54 + (float) Math.cos(a1) * 50;
                    poly[5] = 54 + (float) Math.sin(a1) * 50;
                }
                g.fillPoly(poly, 3);
            }
            g.color(0x33ffd23f);
            g.fillCircle(54, 54, legacy ? 40 : 34);
        }
        if (fg) {
            float scale = legacy ? 1.35f : 1f;
            g.save();
            g.translate(54, 56);
            g.scale(scale);
            drawCoil(g);
            g.restore();
        }
        return img;
    }

    private static void drawCoil(Gfx g) {
        Brawler b = Brawler.ALL[Brawler.VIPER];
        float r = 8.5f;
        float sp = r * 0.55f;
        float rad = 22f;
        float sweep = (float) (Math.PI * 1.62);
        int n = (int) (sweep * rad / sp);
        float[] xs = new float[n], ys = new float[n];
        float theta = -0.15f;
        for (int i = 0; i < n; i++) {
            xs[i] = (float) Math.cos(theta) * rad;
            ys[i] = (float) Math.sin(theta) * rad;
            theta -= sp / rad;
        }
        Icons.bolt(g, 0, 0, 30, 0xff14142a);
        Icons.bolt(g, 0, 0, 24, 0xffffd23f);
        Snake.drawBody(g, xs, ys, n, r, new int[]{b.color1, b.color2}, null, 1f, false, 0, 0, -1e4f, -1e4f, 1e4f, 1e4f);
        float ang = (float) Math.atan2(ys[0] - ys[1], xs[0] - xs[1]);
        Snake.drawHead(g, xs[0], ys[0], r * 1.1f, ang, ang, b.color1, b.color2, b.id, b.accent, 1f, 0, 0, 0.9f);
    }
}
