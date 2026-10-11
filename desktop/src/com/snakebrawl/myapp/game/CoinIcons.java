package com.snakebrawl.myapp.game;

import com.snakebrawl.myapp.desktop.AwtGfx;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Square icons of the coin packs (for the Google Play product listings): CoinIcons outDir [size]. */
public final class CoinIcons {
    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "coin-icons";
        int s = args.length > 1 ? Integer.parseInt(args[1]) : 512;
        new File(out).mkdirs();
        Font font = Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/assets/fonts/LilitaOne-Regular.ttf"));
        Game game = new Game(new SimTest.DesktopPlatform());
        game.resize(s, s);
        Ui ui = game.ui;
        int[] tint = {0xff3fa0ff, 0xff4ad04a, 0xffb35cff, 0xffff9a2e, 0xffff4a6a};
        for (int i = 0; i < CoinStore.PRODUCT_IDS.length; i++) {
            BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_RGB);
            Gfx g = new AwtGfx(img, font);
            float u = s / 512f, cx = s / 2f;
            g.vertical(0, 0, s, s, 0xff2e3466, 0xff141838);
            g.radial(cx, s * 0.5f, s * 0.75f, MathUtil.withAlpha(tint[i], 0.55f), 0x00000000);
            float artY = s * 0.56f;
            g.radial(cx, artY, s * 0.42f, 0x88ffd23f, 0x00ffd23f);
            int coins = 3 + i * 3;
            float cs = s * (0.25f - i * 0.018f);
            for (int k = 0; k < coins; k++) {
                int row = k < 5 ? 0 : (k < 9 ? 1 : (k < 12 ? 2 : 3));
                int inRow = row == 0 ? k : (row == 1 ? k - 5 : (row == 2 ? k - 9 : k - 12));
                int rowN = row == 0 ? Math.min(coins, 5) : (row == 1 ? Math.min(coins - 5, 4) : (row == 2 ? Math.min(coins - 9, 3) : coins - 12));
                float x = cx + (inRow - (rowN - 1) / 2f) * cs * 0.6f;
                float y = artY + cs * 0.6f - row * cs * 0.42f;
                ui.coin(g, x, y, cs);
            }
            // Sparkles
            g.color(0xeeffffff);
            float sx = s * 0.84f, sy = s * 0.36f, sr = cs * 0.22f;
            g.line(sx - sr, sy, sx + sr, sy, 6 * u);
            g.line(sx, sy - sr, sx, sy + sr, 6 * u);
            String amount = CoinStore.format(CoinStore.COINS[i]);
            g.color(0xffffe066);
            g.text(amount, cx, 120 * u, Ui.fit(g, amount, 96 * u, s * 0.9f), Gfx.ALIGN_CENTER, 12 * u, Ui.INK);
            g.color(0xffffffff);
            g.text("COINS", cx, s - 40 * u, 60 * u, Gfx.ALIGN_CENTER, 8 * u, Ui.INK);
            ImageIO.write(img, "png", new File(out, CoinStore.PRODUCT_IDS[i] + ".png"));
        }
    }
}
