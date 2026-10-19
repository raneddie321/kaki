package com.snakebrawl.myapp.game;

/**
 * Opening a crate: it drops in, the player taps it a few times (it shakes harder each time), it
 * bursts open in a flash of light, and the reward is shown on a card in front of spinning rays.
 */
final class BoxReveal {
    private static final int TAPS = 3;
    private static final float AUTO_OPEN = 6f, BURST = 0.7f;

    private final Game game;
    boolean active;
    private boolean mega;
    private int phase; // 0 waiting for taps, 1 burst, 2 reward
    private float t, shake;
    private int taps;
    private String title, text;
    private int artType, art;
    /** Crates waiting to be opened after this one (true = Mega). */
    final java.util.ArrayDeque<Boolean> queue = new java.util.ArrayDeque<Boolean>();

    BoxReveal(Game game) {
        this.game = game;
    }

    /** Shows the crate; the reward (already granted) is revealed when it opens. */
    void start(boolean mega, String title, String text, int artType, int art) {
        this.mega = mega;
        this.title = title;
        this.text = text;
        this.artType = artType;
        this.art = art;
        active = true;
        phase = 0;
        t = 0;
        taps = 0;
        shake = 0;
        game.gated.playSound(Platform.SND_BOX, 0.8f);
        game.layout();
    }

    void update(float dt) {
        if (!active) return;
        t += dt;
        shake = Math.max(0, shake - dt * 3f);
        if (phase == 0 && t > AUTO_OPEN) burst();
        else if (phase == 1 && t > BURST) {
            phase = 2;
            t = 0;
            game.gated.playSound(artType == 1 ? Platform.SND_POWER : Platform.SND_VICTORY, 0.9f);
        }
    }

    void tap() {
        if (phase == 0) {
            if (t < 0.45f) return; // still dropping in
            taps++;
            shake = 1f;
            game.gated.playSound(Platform.SND_CLICK, 1f);
            game.gated.vibrate(25);
            if (taps >= TAPS) burst();
        } else if (phase == 2 && t > 0.5f) {
            active = false;
            if (!queue.isEmpty()) game.openRewardBox(queue.poll());
            else game.layout();
        }
    }

    private void burst() {
        phase = 1;
        t = 0;
        game.gated.playSound(Platform.SND_EXPLODE, 1f);
        game.gated.vibrate(60);
    }

    // ------------------------------------------------------------------ drawing

    void render(Gfx g) {
        float w = game.w, h = game.h, u = game.u, cx = w / 2, cy = h / 2;
        float clock = game.clock;
        int ray = mega ? 0xffc77dff : 0xffffd23f;

        g.color(0xff0a0c22);
        g.fillRect(0, 0, w, h);
        g.radial(cx, cy, h * 0.9f, mega ? 0xff2a1850 : 0xff1a2250, 0xff0a0c22);

        if (phase == 0) {
            float drop = Math.min(1f, t / 0.45f);
            float y = cy - (1f - bounce(drop)) * h * 0.7f;
            rays(g, cx, y, h * 0.55f, clock * 0.4f, MathUtil.withAlpha(ray, 0.10f + 0.05f * taps));
            g.radial(cx, y, 420 * u, MathUtil.withAlpha(ray, 0.35f + 0.15f * taps), 0x00000000);
            float sx = MathUtil.sin(t * 55f) * shake * (10 + 6 * taps) * u;
            float s = (400 + 30 * taps) * u * (1f + shake * 0.06f);
            MetaScreens.drawBox(g, cx + sx, y, s, mega, clock, u);
            // tap dots
            for (int i = 0; i < TAPS; i++) {
                float dx = cx + (i - 1) * 54 * u, dy = cy + 290 * u;
                g.color(Ui.INK);
                g.fillCircle(dx, dy, 16 * u);
                g.color(i < taps ? ray : 0xff3a3f6a);
                g.fillCircle(dx, dy, 12 * u);
            }
            float p = 0.6f + 0.4f * MathUtil.sin(clock * 6f);
            g.color(MathUtil.withAlpha(0xffffffff, p));
            g.text("TAP TO OPEN!", cx, cy + 380 * u, 54 * u, Gfx.ALIGN_CENTER, 7 * u, Ui.INK);
            g.color(mega ? 0xffd8a0ff : 0xff8ad8ff);
            g.text(mega ? "MEGA CRATE" : "SNAKE CRATE", cx, cy - 330 * u, 64 * u, Gfx.ALIGN_CENTER, 8 * u, Ui.INK);
            return;
        }

        if (phase == 1) {
            float k = t / BURST;
            rays(g, cx, cy, h * (0.5f + k), clock * 2f, MathUtil.withAlpha(ray, 0.35f * (1f - k)));
            // box swells and vanishes
            float s = 490 * u * (1f + k * 0.8f);
            if (k < 0.45f) MetaScreens.drawBox(g, cx, cy, s, mega, clock, u);
            // flying stars and coins
            for (int i = 0; i < 26; i++) {
                float a = i * 0.2417f * MathUtil.PI * 2 + i * 0.37f;
                float d = (180 + (i * 53) % 220) * u * (0.3f + k * 1.6f);
                float px = cx + MathUtil.cos(a) * d, py = cy + MathUtil.sin(a) * d - k * k * 120 * u;
                if (i % 3 == 0) game.ui.coin(g, px, py, 40 * u * (1f - k * 0.5f));
                else star(g, px, py, (14 + i % 4 * 5) * u * (1f - k * 0.6f), i % 2 == 0 ? 0xffffffff : ray);
            }
            g.color(MathUtil.withAlpha(0xffffffff, Math.max(0, 1f - k * 1.6f)));
            g.fillRect(0, 0, w, h);
            return;
        }

        // reward card
        float in = Math.min(1f, t / 0.35f);
        float sc = overshoot(in);
        rays(g, cx, cy - 40 * u, h * 0.8f, clock * 0.5f, MathUtil.withAlpha(ray, 0.16f));
        g.radial(cx, cy - 60 * u, 420 * u, MathUtil.withAlpha(ray, 0.45f), 0x00000000);
        g.save();
        g.translate(cx, cy);
        g.scale(Math.max(0.01f, sc));
        float pw = Math.min(860 * u, w - 80 * u), ph = 640 * u, l = -pw / 2, top = -ph / 2 - 20 * u;
        game.ui.panel(g, l, top, l + pw, top + ph, mega ? 0xff3a2a66 : 0xff2a2f66);
        g.color(mega ? 0xff5a3a96 : 0xff3a3f80);
        g.fillRoundRect(l, top, l + pw, top + 110 * u, 26 * u);
        g.color(0xffffd23f);
        g.text(title, 0, top + 78 * u, Ui.fit(g, title, 70 * u, pw * 0.9f), Gfx.ALIGN_CENTER, 8 * u, Ui.INK);
        float artY = top + 270 * u;
        float bob = MathUtil.sin(clock * 3f) * 8 * u;
        switch (artType) {
            case 1:
                for (int k = 0; k < 5; k++) {
                    game.ui.coin(g, -140 * u + k * 70 * u, artY + bob + MathUtil.sin(clock * 4 + k) * 10 * u, 100 * u);
                }
                break;
            case 2: {
                Brawler b = Brawler.ALL[game.profile.selected];
                game.ui.snakeArt(g, b, Skin.ALL[art].paletteFor(b), 0, artY + bob, 1.5f * u, clock);
                break;
            }
            case 3: {
                Brawler b = Brawler.ALL[art];
                game.ui.snakeArt(g, b, new int[]{b.color1, b.color2}, 0, artY + bob, 1.5f * u, clock);
                break;
            }
            default:
                break;
        }
        g.save();
        game.ui.wrap(g, text, l + 60 * u, l + pw - 60 * u, top + 430 * u, 38 * u, 0xffffffff, top + ph - 40 * u);
        g.restore();
        g.restore();
        if (t > 0.5f) {
            float p = 0.6f + 0.4f * MathUtil.sin(clock * 6f);
            g.color(MathUtil.withAlpha(0xffffffff, p));
            g.text("TAP TO CONTINUE", cx, h - 60 * u, 40 * u, Gfx.ALIGN_CENTER, 5 * u, Ui.INK);
        }
    }

    /** Light rays spinning around (x, y). */
    private static void rays(Gfx g, float x, float y, float r, float rot, int color) {
        float[] xy = new float[6];
        g.color(color);
        for (int i = 0; i < 14; i++) {
            float a = rot + i * MathUtil.PI * 2 / 14;
            float half = 0.09f;
            xy[0] = x;
            xy[1] = y;
            xy[2] = x + MathUtil.cos(a - half) * r;
            xy[3] = y + MathUtil.sin(a - half) * r;
            xy[4] = x + MathUtil.cos(a + half) * r;
            xy[5] = y + MathUtil.sin(a + half) * r;
            g.fillPoly(xy, 3);
        }
    }

    private static void star(Gfx g, float x, float y, float r, int color) {
        float[] xy = new float[16];
        for (int i = 0; i < 8; i++) {
            float a = i * MathUtil.PI / 4, rr = i % 2 == 0 ? r : r * 0.35f;
            xy[i * 2] = x + MathUtil.cos(a) * rr;
            xy[i * 2 + 1] = y + MathUtil.sin(a) * rr;
        }
        g.color(color);
        g.fillPoly(xy, 8);
    }

    private static float bounce(float k) {
        if (k < 0.7f) return (k / 0.7f) * (k / 0.7f);
        float b = (k - 0.85f) / 0.15f;
        return 1f - 0.08f * (1f - b * b);
    }

    private static float overshoot(float k) {
        float s = 1.7f;
        k -= 1;
        return k * k * ((s + 1) * k + s) + 1;
    }
}
