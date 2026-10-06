package com.snakebrawl.myapp.game;

import com.snakebrawl.myapp.desktop.AwtGfx;

import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Desktop test harness. Usage:
 *   SimTest balance [matches]   bot-only showdown matches, prints stats and timing
 *   SimTest play [games]        scripted player through the real touch API, prints results
 *   SimTest shots outDir        renders screenshots of every screen
 */
public final class SimTest {
    private static final float DT = 1f / 60f;

    static final class DesktopPlatform implements Platform {
        final Map<String, Integer> prefs = new HashMap<>();
        int sounds;

        @Override
        public void playSound(int id, float volume) {
            if (id < 0 || id >= SND_COUNT || volume < 0 || volume > 1.0001f) throw new IllegalStateException("bad sound " + id + " " + volume);
            sounds++;
        }

        @Override
        public int loadInt(String key, int def) {
            Integer v = prefs.get(key);
            return v == null ? def : v;
        }

        @Override
        public void saveInt(String key, int value) {
            prefs.put(key, value);
        }

        @Override
        public void vibrate(int millis) {}

        @Override
        public boolean launchPurchase(String productId) {
            return false;
        }
    }

    public static void main(String[] args) throws Exception {
        String cmd = args.length > 0 ? args[0] : "balance";
        switch (cmd) {
            case "balance":
                balance(args.length > 1 ? Integer.parseInt(args[1]) : 10);
                break;
            case "play":
                play(args.length > 1 ? Integer.parseInt(args[1]) : 5, args.length > 2 ? Integer.parseInt(args[2]) : 0);
                break;
            case "stress":
                stress(Integer.parseInt(args[1]), Integer.parseInt(args[2]), args.length > 3 ? Integer.parseInt(args[3]) : 1);
                break;
            case "shots":
                shots(args.length > 1 ? args[1] : "shots", args.length > 2 ? Integer.parseInt(args[2]) : 2400,
                        args.length > 3 ? Integer.parseInt(args[3]) : 1080);
                break;
            default:
                throw new IllegalArgumentException(cmd);
        }
    }

    // ------------------------------------------------------------------ balance

    static void balance(int matches) {
        DesktopPlatform pf = new DesktopPlatform();
        long totalSteps = 0, totalNanos = 0, worstNanos = 0;
        int[] causes = new int[4];
        float totalTime = 0;
        int unfinished = 0;
        for (int m = 0; m < matches; m++) {
            World w = new World(pf, World.MODE_SHOWDOWN, null, null, 1, 10, 200);
            int steps = 0;
            while (w.aliveCount > 1 && w.matchTime < 320) {
                long t0 = System.nanoTime();
                w.update(DT);
                long el = System.nanoTime() - t0;
                totalNanos += el;
                if (steps > 60) worstNanos = Math.max(worstNanos, el);
                steps++;
                for (int i = 0; i < w.feedCount; i++) if (w.feedTime[i] == 0) causes[w.feedCause[i]]++;
            }
            if (w.aliveCount > 1) unfinished++;
            totalSteps += steps;
            totalTime += w.matchTime;
            Snake winner = null;
            for (int i = 0; i < w.snakeCount; i++) if (w.snakes[i].alive) winner = w.snakes[i];
            System.out.printf("match %d: %.1fs alive=%d winner=%s(%s) mass=%.0f kills=%d orbs=%d%n", m, w.matchTime, w.aliveCount,
                    winner == null ? "-" : winner.name, winner == null ? "-" : winner.type.name,
                    winner == null ? 0f : winner.mass, winner == null ? 0 : winner.kills, w.orbCount);
        }
        System.out.printf("avg match %.1fs, unfinished %d%n", totalTime / matches, unfinished);
        System.out.printf("deaths: shot=%d crash=%d poison=%d dash=%d%n", causes[0], causes[1], causes[2], causes[3]);
        System.out.printf("avg step %.3f ms, worst %.3f ms%n", totalNanos / 1e6 / totalSteps, worstNanos / 1e6);

        // Endless stress: bigger map, lots of growth
        World w = new World(pf, World.MODE_ENDLESS, null, null, 1, 14, 400);
        long t0 = System.nanoTime();
        for (int i = 0; i < 60 * 240; i++) w.update(DT);
        float maxMass = 0;
        int maxSegs = 0;
        for (int i = 0; i < w.snakeCount; i++) {
            maxMass = Math.max(maxMass, w.snakes[i].mass);
            maxSegs = Math.max(maxSegs, w.snakes[i].segs);
        }
        System.out.printf("endless 240s: %.3f ms/step, maxMass=%.0f maxSegs=%d orbs=%d alive=%d%n",
                (System.nanoTime() - t0) / 1e6 / (60 * 240), maxMass, maxSegs, w.orbCount, w.aliveCount);
    }

    // ------------------------------------------------------------------ scripted player

    static final float W = 2400, H = 1080;

    /** Simple autopilot that only uses the touch API, like a real player. */
    static final class Pilot {
        final Game game;
        boolean moving;
        float fireTimer;
        int fireCount;

        Pilot(Game game) {
            this.game = game;
        }

        void step(float dt) {
            World w = game.currentWorld();
            Snake p = w.player;
            if (p == null || !p.alive) {
                if (moving) {
                    game.touchUp(1, 228, 870);
                    moving = false;
                }
                return;
            }
            // Steer: towards the safe zone centre if poison is near, otherwise towards food, avoiding obstacles
            float goal;
            float cx = (w.zoneL + w.zoneR) / 2, cy = (w.zoneT + w.zoneB) / 2;
            if (w.zoneActive() && (!w.inZone(p.hx(), p.hy()) || Math.min(Math.min(p.hx() - w.zoneL, w.zoneR - p.hx()),
                    Math.min(p.hy() - w.zoneT, w.zoneB - p.hy())) < 250)) {
                goal = MathUtil.angleTo(p.hx(), p.hy(), cx, cy);
            } else {
                float best = 0;
                goal = p.ang;
                for (int i = 0; i < w.orbCount; i++) {
                    float d = MathUtil.dist(p.hx(), p.hy(), w.ox[i], w.oy[i]);
                    float s = w.ov[i] / (d + 80);
                    if (s > best) {
                        best = s;
                        goal = MathUtil.angleTo(p.hx(), p.hy(), w.ox[i], w.oy[i]);
                    }
                }
            }
            float look = 250;
            if (w.rayClear(p, goal, look) < look) {
                float bestScore = -1e9f;
                for (int k = -6; k <= 6; k++) {
                    float a = p.ang + k * 0.4f;
                    float c = w.rayClear(p, a, look);
                    float score = c / look * 2.5f + MathUtil.cos(MathUtil.wrap(a - goal));
                    if (score > bestScore) {
                        bestScore = score;
                        goal = a;
                    }
                }
            }
            float ox = 228, oy = 870;
            if (!moving) {
                game.touchDown(1, ox, oy);
                moving = true;
            }
            game.touchMove(1, ox + MathUtil.cos(goal) * 80, oy + MathUtil.sin(goal) * 80);

            fireTimer -= dt;
            if (fireTimer <= 0) {
                fireTimer = 0.35f;
                // Tap = auto-aim attack; super when ready
                if (p.superReady()) {
                    game.touchDown(3, 1972, 940);
                    game.touchUp(3, 1972, 940);
                } else {
                    game.touchDown(2, 2197, 885);
                    game.touchUp(2, 2197, 885);
                }
                fireCount++;
            }
        }
    }

    static void play(int games, int modeSel) {
        DesktopPlatform pf = new DesktopPlatform();
        pf.prefs.put("mode", modeSel);
        Game game = new Game(pf);
        game.resize(W, H);
        for (int gi = 0; gi < games; gi++) {
            pf.prefs.put("brawler", gi % 4);
            Game g2 = new Game(pf);
            g2.resize(W, H);
            game = g2;
            tapBtn(game, Game.B_PLAY);
            if (game.screenId() != 2) throw new IllegalStateException("did not start, screen=" + game.screenId());
            Pilot pilot = new Pilot(game);
            float lastHp = 1e9f;
            int steps = 0;
            while (game.screenId() == 2 && steps < 60 * 400) {
                pilot.step(DT);
                game.tick(DT);
                steps++;
                World cw = game.currentWorld();
                if (System.getenv("HURT") != null && cw.player != null) {
                    Snake pl = cw.player;
                    if (pl.hp < lastHp - 1 && pl.lastAttacker != null)
                        System.out.printf("   %.2fs hit by %s(%s) -%.0f hp=%.0f/%.0f dist=%.0f%n", cw.matchTime, pl.lastAttacker.name,
                                pl.lastAttacker.type.name, lastHp - pl.hp, pl.hp, pl.maxHp,
                                MathUtil.dist(pl.hx(), pl.hy(), pl.lastAttacker.hx(), pl.lastAttacker.hy()));
                    lastHp = pl.hp;
                }
                if (System.getenv("FEED") != null)
                    for (int i = 0; i < cw.feedCount; i++)
                        if (cw.feedTime[i] == 0) System.out.printf("   %.1fs %s -[%d]-> %s%n", cw.matchTime, cw.feedA[i], cw.feedCause[i], cw.feedB[i]);
            }
            World w = game.currentWorld();
            Snake p = w.player;
            System.out.printf("game %d [%s]: screen=%d time=%.1fs alive=%s rank=%d kills=%d mass=%.0f cubes=%d shots=%d trophies=%d sounds=%d%n",
                    gi, p.type.name, game.screenId(), w.matchTime, p.alive, p.rank, p.kills, p.mass, p.cubes, pilot.fireCount,
                    pf.loadInt("trophies", -1), pf.sounds);
        }
    }

    static void tap(Game g, float x, float y) {
        g.touchDown(9, x, y);
        g.touchUp(9, x, y);
    }

    // ------------------------------------------------------------------ stress

    /** Renders every frame through full games with random multi-touch noise on top of the autopilot. */
    static void stress(int width, int height, int games) throws Exception {
        Font font = Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/assets/fonts/LilitaOne-Regular.ttf"));
        DesktopPlatform pf = new DesktopPlatform();
        java.util.Random rnd = new java.util.Random(42);
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        AwtGfx gfx = new AwtGfx(img, font);
        long frames = 0, renderNanos = 0;
        for (int gi = 0; gi < games; gi++) {
            pf.prefs.put("brawler", gi % 4);
            pf.prefs.put("mode", gi % 2);
            Game game = new Game(pf);
            game.resize(width, height);
            game.setInsets(gi % 3 == 0 ? 80 : 0, 0, 0, 0);
            // Random taps on the menu until a game starts (exercises every menu button)
            int guard = 0;
            while (game.screenId() != 2 && guard++ < 400) {
                float x = rnd.nextFloat() * width, y = rnd.nextFloat() * height;
                game.touchDown(7, x, y);
                game.touchUp(7, x, y);
                game.frame(DT, gfx);
                if (game.screenId() == 1 && rnd.nextInt(4) == 0) game.onBack();
            }
            if (game.screenId() != 2) {
                // Fall back to the PLAY button
                while (game.onBack()) game.tick(DT);
                if (game.screenId() != 0) game.onBack();
                tapBtn(game, Game.B_PLAY);
            }
            Pilot pilot = new Pilot(game);
            int steps = 0;
            while ((game.screenId() == 2 || game.screenId() == 3) && steps < 60 * 300) {
                if (game.screenId() == 2) pilot.step(DT);
                // Random extra fingers: super drags, boost holds, pause/resume
                int r = rnd.nextInt(400);
                if (r == 0) {
                    game.onBack();
                    game.frame(DT, gfx);
                    game.onBack();
                } else if (r < 6) {
                    float x = rnd.nextFloat() * width, y = rnd.nextFloat() * height;
                    game.touchDown(4, x, y);
                    game.touchMove(4, x + rnd.nextFloat() * 300 - 150, y + rnd.nextFloat() * 300 - 150);
                    game.frame(DT, gfx);
                    game.touchUp(4, x, y);
                } else if (r == 7) {
                    game.touchCancelAll();
                } else if (r == 8) {
                    game.onPause();
                    game.onBack();
                }
                long t0 = System.nanoTime();
                game.frame(DT, gfx);
                renderNanos += System.nanoTime() - t0;
                frames++;
                steps++;
                World w = game.currentWorld();
                for (int i = 0; i < w.snakeCount; i++) {
                    Snake sn = w.snakes[i];
                    if (!sn.alive) continue;
                    for (int j = 0; j < sn.segs; j++)
                        if (Float.isNaN(sn.sx[j]) || Float.isNaN(sn.sy[j]) || Float.isInfinite(sn.sx[j]))
                            throw new IllegalStateException("NaN segment in " + sn.name);
                    if (Float.isNaN(sn.hp) || Float.isNaN(sn.mass)) throw new IllegalStateException("NaN stats " + sn.name);
                }
                if (game.screenId() == 3 && steps % 120 == 0) {
                    // Result screen: press PLAY AGAIN sometimes, MENU otherwise
                    break;
                }
            }
            System.out.printf("stress game %d: screen=%d steps=%d%n", gi, game.screenId(), steps);
        }
        System.out.printf("%dx%d: %d frames, avg java2d frame %.2f ms%n", width, height, frames, renderNanos / 1e6 / Math.max(1, frames));
    }

    // ------------------------------------------------------------------ screenshots

    static void tapBtn(Game g, int id) {
        Ui.Btn b = g.ui.find(id);
        if (b == null) throw new IllegalStateException("no button " + id + " on screen " + g.screenId());
        float y = (b.t + b.b) / 2 - (b.scrolls ? g.ui.scrollY : 0);
        tap(g, (b.l + b.r) / 2, y);
        g.tick(DT);
    }

    static void shots(String outDir, int width, int height) throws Exception {
        new File(outDir).mkdirs();
        Font font = Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/assets/fonts/LilitaOne-Regular.ttf"));
        DesktopPlatform pf = new DesktopPlatform();
        pf.prefs.put("coins", 5000);
        pf.prefs.put("unlocked", 0x3f);
        pf.prefs.put("ownedSkins", 0x1 | 1 << 8 | 1 << 18);
        pf.prefs.put("skin", 18);
        pf.prefs.put("lvl0", 4);
        pf.prefs.put("brawler", 2);
        Game game = new Game(pf);
        game.resize(width, height);

        run(game, 1.0f);
        shot(game, font, width, height, outDir + "/1_menu.png");

        tapBtn(game, Game.B_BRAWLERS);
        tapBtn(game, 100 + 6); // view TOXIN (locked)
        run(game, 0.5f);
        shot(game, font, width, height, outDir + "/2_brawlers.png");
        tapBtn(game, 100 + 4); // FROST
        tapBtn(game, 151); // upgrade -> confirm popup
        run(game, 0.4f);
        shot(game, font, width, height, outDir + "/2b_upgrade_popup.png");
        tapBtn(game, Game.B_YES);
        run(game, 0.4f);
        tapBtn(game, Game.B_OK);
        tapBtn(game, Game.B_BACK);

        tapBtn(game, Game.B_SHOP);
        run(game, 0.5f);
        shot(game, font, width, height, outDir + "/3_shop_offers.png");
        tapBtn(game, 311); // brawl box
        tapBtn(game, Game.B_YES);
        run(game, 0.5f);
        shot(game, font, width, height, outDir + "/3b_box_reward.png");
        tapBtn(game, Game.B_OK);
        tapBtn(game, 301); // skins tab
        run(game, 0.5f);
        shot(game, font, width, height, outDir + "/4_shop_skins.png");
        // Scroll down with a drag
        float cx = width / 2f, cy = height * 0.7f;
        game.touchDown(3, cx, cy);
        for (int i = 1; i <= 10; i++) {
            game.touchMove(3, cx, cy - i * 40);
            game.tick(DT);
        }
        game.touchUp(3, cx, cy - 400);
        run(game, 0.5f);
        shot(game, font, width, height, outDir + "/4b_shop_skins_scrolled.png");
        tapBtn(game, 302); // brawlers tab
        run(game, 0.3f);
        shot(game, font, width, height, outDir + "/5_shop_brawlers.png");
        tapBtn(game, 303); // coins tab
        run(game, 0.4f);
        shot(game, font, width, height, outDir + "/5b_shop_coins.png");
        int before = pf.loadInt("coins", 0);
        tapBtn(game, 351);
        run(game, 0.4f);
        shot(game, font, width, height, outDir + "/5c_checkout.png");
        tapBtn(game, Game.B_SHEET_BUY);
        run(game, 0.5f);
        shot(game, font, width, height, outDir + "/5d_processing.png");
        run(game, 1.1f);
        shot(game, font, width, height, outDir + "/5e_success.png");
        run(game, 1.4f);
        shot(game, font, width, height, outDir + "/5f_coins_added.png");
        game.onPause();
        System.out.println("coins before " + before + " after " + pf.loadInt("coins", 0));
        // Cancelling must not grant coins
        tapBtn(game, 352);
        run(game, 0.4f);
        tap(game, width / 2f, 40);
        run(game, 0.5f);
        game.onPause();
        System.out.println("after cancel " + pf.loadInt("coins", 0) + " sheet=" + game.sheetPack);
        tapBtn(game, Game.B_BACK);

        tapBtn(game, Game.B_SETTINGS);
        run(game, 0.3f);
        shot(game, font, width, height, outDir + "/6_settings.png");
        tapBtn(game, Game.B_BACK);

        int[] brawlers = {4, 5, 6, 7};
        for (int b : brawlers) {
            pf.prefs.put("brawler", b);
            pf.prefs.put("unlocked", 0xff);
            pf.prefs.put("hints", 5);
            game = new Game(pf);
            game.resize(width, height);
            tapBtn(game, Game.B_PLAY);
            Pilot pilot = new Pilot(game);
            float t = 0;
            boolean shotTaken = false;
            while (game.screenId() == 2 && t < 60) {
                pilot.step(DT);
                game.tick(DT);
                t += DT;
                World w = game.currentWorld();
                if (!shotTaken && t > 14 && (w.player.superReady() || t > 30)) {
                    // Drag the super stick to show its aim preview
                    float u = Math.min(height / 1080f, width / 1920f);
                    float sxp = width - 28 * u - 175 * u - 225 * u, syp = height - 20 * u - 175 * u + 55 * u;
                    game.touchDown(6, sxp, syp);
                    game.touchMove(6, sxp - 70 * u, syp - 40 * u);
                    game.tick(DT);
                    shot(game, font, width, height, outDir + "/7_play_" + Brawler.ALL[b].name.toLowerCase() + ".png");
                    game.touchUp(6, sxp - 70 * u, syp - 40 * u);
                    for (int i = 0; i < 20; i++) game.tick(DT);
                    shot(game, font, width, height, outDir + "/7_play_" + Brawler.ALL[b].name.toLowerCase() + "_super.png");
                    shotTaken = true;
                }
            }
            if (b == 7) {
                while (game.screenId() == 2) {
                    pilot.step(DT);
                    game.tick(DT);
                }
                run(game, 1.0f);
                shot(game, font, width, height, outDir + "/8_result.png");
            }
        }
    }

    static void run(Game g, float secs) {
        for (float t = 0; t < secs; t += DT) g.tick(DT);
    }

    static void shot(Game g, Font font, int w, int h, String path) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        AwtGfx gfx = new AwtGfx(img, font);
        g.frame(0f, gfx);
        ImageIO.write(img, "png", new File(path));
        System.out.println("wrote " + path);
    }
}
