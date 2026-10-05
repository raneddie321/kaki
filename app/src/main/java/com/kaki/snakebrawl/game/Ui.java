package com.kaki.snakebrawl.game;

/** Buttons, scrolling and shared drawing helpers for the menu screens. */
final class Ui {
    static final int INK = 0xff14142a;

    static final class Btn {
        int id;
        float l, t, r, b;
        String label, sub;
        int color;
        boolean scrolls;
        boolean enabled = true;

        boolean contains(float x, float y) {
            return x >= l && x <= r && y >= t && y <= b;
        }
    }

    float u = 1f;
    float clock;
    final Btn[] btns = new Btn[96];
    int count;
    int pressed = -1;
    private int pressedPtr = -1;

    // Vertical scroll area
    float scrollY, scrollMax, viewT, viewB, viewL, viewR;
    private int dragPtr = -1;
    private float downY, lastY;
    private boolean dragging;
    private float fling;

    Ui() {
        for (int i = 0; i < btns.length; i++) btns[i] = new Btn();
    }

    void clear() {
        count = 0;
    }

    Btn add(int id, float l, float t, float r, float b, String label, String sub, int color) {
        Btn x = btns[count++];
        x.id = id;
        x.l = l;
        x.t = t;
        x.r = r;
        x.b = b;
        x.label = label;
        x.sub = sub;
        x.color = color;
        x.scrolls = false;
        x.enabled = true;
        return x;
    }

    /** Adds a button inside the scroll area; coordinates are in content space. */
    Btn addScroll(int id, float l, float t, float r, float b, String label, String sub, int color) {
        Btn x = add(id, l, t, r, b, label, sub, color);
        x.scrolls = true;
        return x;
    }

    void setScrollArea(float l, float t, float r, float b, float contentHeight) {
        viewL = l;
        viewT = t;
        viewR = r;
        viewB = b;
        scrollMax = Math.max(0, contentHeight - (b - t));
        scrollY = MathUtil.clamp(scrollY, 0, scrollMax);
    }

    void resetScroll() {
        scrollY = 0;
        fling = 0;
    }

    void update(float dt) {
        clock += dt;
        if (dragPtr < 0 && fling != 0) {
            scrollY = MathUtil.clamp(scrollY + fling * dt, 0, scrollMax);
            fling *= (float) Math.exp(-4f * dt);
            if (Math.abs(fling) < 20) fling = 0;
        }
    }

    private int hit(float x, float y) {
        for (int i = count - 1; i >= 0; i--) {
            Btn b = btns[i];
            if (!b.enabled) continue;
            if (b.scrolls) {
                if (x < viewL || x > viewR || y < viewT || y > viewB) continue;
                if (b.contains(x, y + scrollY)) return b.id;
            } else if (b.contains(x, y)) {
                return b.id;
            }
        }
        return -1;
    }

    private boolean inScroll(float x, float y) {
        return scrollMax > 0 && x >= viewL && x <= viewR && y >= viewT && y <= viewB;
    }

    void down(int ptr, float x, float y) {
        if (dragPtr < 0 && inScroll(x, y)) {
            dragPtr = ptr;
            downY = lastY = y;
            dragging = false;
            fling = 0;
        }
        if (pressed < 0) {
            pressed = hit(x, y);
            pressedPtr = ptr;
        }
    }

    void move(int ptr, float x, float y) {
        if (ptr != dragPtr) return;
        if (!dragging && Math.abs(y - downY) > 18 * u) {
            dragging = true;
            if (pressedPtr == ptr) pressed = -1;
        }
        if (dragging) {
            float dy = lastY - y;
            scrollY = MathUtil.clamp(scrollY + dy, 0, scrollMax);
            fling = dy * 60f;
        }
        lastY = y;
    }

    /** Returns the id of the clicked button, or -1. */
    int up(int ptr, float x, float y) {
        if (ptr == dragPtr) {
            dragPtr = -1;
            if (dragging) {
                dragging = false;
                if (pressedPtr == ptr) pressed = -1;
                return -1;
            }
            fling = 0;
        }
        if (ptr != pressedPtr || pressed < 0) return -1;
        int id = pressed;
        pressed = -1;
        pressedPtr = -1;
        return hit(x, y) == id ? id : -1;
    }

    void cancel() {
        pressed = -1;
        pressedPtr = -1;
        dragPtr = -1;
        dragging = false;
    }

    Btn find(int id) {
        for (int i = 0; i < count; i++) if (btns[i].id == id) return btns[i];
        return null;
    }

    // ------------------------------------------------------------------ drawing

    void button(Gfx g, Btn b) {
        boolean pressedNow = pressed == b.id;
        float l = b.l, t = b.t, r = b.r, bt = b.b;
        if (pressedNow) {
            float cx = (l + r) / 2, cy = (t + bt) / 2, s = 0.94f;
            l = cx + (l - cx) * s;
            r = cx + (r - cx) * s;
            t = cy + (t - cy) * s;
            bt = cy + (bt - cy) * s;
        }
        int color = b.enabled ? b.color : 0xff6a6a7a;
        float rad = Math.min(22 * u, (bt - t) * 0.3f);
        g.color(0x55000000);
        g.fillRoundRect(l + 4 * u, t + 10 * u, r + 4 * u, bt + 10 * u, rad);
        g.color(INK);
        g.fillRoundRect(l - 5 * u, t - 5 * u, r + 5 * u, bt + 5 * u, rad + 4 * u);
        g.color(MathUtil.darker(color, 0.35f));
        g.fillRoundRect(l, t, r, bt, rad);
        g.color(color);
        g.fillRoundRect(l, t, r, bt - Math.min(12 * u, (bt - t) * 0.12f), rad);
        g.color(MathUtil.withAlpha(0xffffffff, 0.25f));
        g.fillRoundRect(l + 12 * u, t + 8 * u, r - 12 * u, t + (bt - t) * 0.35f, rad * 0.6f);
        if (b.label == null) return;
        float cy = (t + bt) / 2;
        g.color(0xffffffff);
        float maxW = (r - l) * 0.88f;
        if (b.sub != null) {
            float size = fit(g, b.label, Math.min(56 * u, (bt - t) * 0.4f), maxW);
            g.text(b.label, (l + r) / 2, cy + size * 0.15f, size, Gfx.ALIGN_CENTER, 6 * u, INK);
            float ss = fit(g, b.sub, size * 0.52f, maxW);
            g.text(b.sub, (l + r) / 2, cy + size * 0.15f + size * 0.72f, ss, Gfx.ALIGN_CENTER, 4 * u, INK);
        } else {
            float size = fit(g, b.label, Math.min(70 * u, (bt - t) * 0.5f), maxW);
            g.text(b.label, (l + r) / 2, cy + size * 0.32f, size, Gfx.ALIGN_CENTER, Math.min(8 * u, size * 0.12f), INK);
        }
    }

    /** Shrinks a font size until the text fits the width. */
    static float fit(Gfx g, String s, float size, float maxW) {
        float w = g.measureText(s, size);
        return w > maxW ? size * maxW / w : size;
    }

    void panel(Gfx g, float l, float t, float r, float b, int color) {
        g.color(0x66000000);
        g.fillRoundRect(l + 6 * u, t + 12 * u, r + 6 * u, b + 12 * u, 30 * u);
        g.color(0xff0e1024);
        g.fillRoundRect(l - 6 * u, t - 6 * u, r + 6 * u, b + 6 * u, 32 * u);
        g.color(color);
        g.fillRoundRect(l, t, r, b, 26 * u);
    }

    void coin(Gfx g, float x, float y, float s) {
        g.color(INK);
        g.fillCircle(x, y, s * 0.55f);
        g.color(0xffd99a1a);
        g.fillCircle(x, y, s * 0.46f);
        g.color(0xffffd23f);
        g.fillCircle(x - s * 0.04f, y - s * 0.04f, s * 0.36f);
        g.color(0xffd99a1a);
        g.fillRoundRect(x - s * 0.08f, y - s * 0.22f, x + s * 0.06f, y + s * 0.18f, s * 0.04f);
        g.color(0xccffffff);
        g.fillCircle(x - s * 0.18f, y - s * 0.18f, s * 0.08f);
    }

    void trophy(Gfx g, float x, float y, float s) {
        g.color(INK);
        g.fillRoundRect(x - s * 0.62f, y - s * 0.62f, x + s * 0.62f, y + s * 0.05f, s * 0.3f);
        g.fillRoundRect(x - s * 0.14f, y - s * 0.05f, x + s * 0.14f, y + s * 0.45f, s * 0.05f);
        g.fillRoundRect(x - s * 0.44f, y + s * 0.32f, x + s * 0.44f, y + s * 0.62f, s * 0.1f);
        g.strokeCircle(x - s * 0.55f, y - s * 0.3f, s * 0.24f, s * 0.2f);
        g.strokeCircle(x + s * 0.55f, y - s * 0.3f, s * 0.24f, s * 0.2f);
        g.color(0xffffc928);
        g.strokeCircle(x - s * 0.55f, y - s * 0.3f, s * 0.24f, s * 0.09f);
        g.strokeCircle(x + s * 0.55f, y - s * 0.3f, s * 0.24f, s * 0.09f);
        g.fillRoundRect(x - s * 0.5f, y - s * 0.52f, x + s * 0.5f, y - s * 0.02f, s * 0.24f);
        g.fillRect(x - s * 0.07f, y - s * 0.05f, x + s * 0.07f, y + s * 0.38f);
        g.fillRoundRect(x - s * 0.34f, y + s * 0.38f, x + s * 0.34f, y + s * 0.54f, s * 0.06f);
        g.color(0xfffff2a8);
        g.fillRoundRect(x - s * 0.34f, y - s * 0.44f, x - s * 0.18f, y - s * 0.14f, s * 0.06f);
    }

    void lock(Gfx g, float x, float y, float s) {
        g.color(INK);
        g.strokeCircle(x, y - s * 0.22f, s * 0.26f, s * 0.2f);
        g.fillRoundRect(x - s * 0.42f, y - s * 0.18f, x + s * 0.42f, y + s * 0.45f, s * 0.1f);
        g.color(0xffd8dcff);
        g.strokeCircle(x, y - s * 0.22f, s * 0.26f, s * 0.08f);
        g.fillRoundRect(x - s * 0.34f, y - s * 0.1f, x + s * 0.34f, y + s * 0.37f, s * 0.07f);
        g.color(INK);
        g.fillCircle(x, y + s * 0.08f, s * 0.08f);
    }

    /** Rounded pill with a coin icon and amount, right-aligned at (r, t). Returns its left edge. */
    float coinPill(Gfx g, float r, float t, int coins) {
        String s = Integer.toString(coins);
        float tw = g.measureText(s, 48 * u);
        float l = r - tw - 120 * u;
        g.color(0xcc0e1024);
        g.fillRoundRect(l, t, r, t + 84 * u, 42 * u);
        coin(g, l + 44 * u, t + 42 * u, 58 * u);
        g.color(0xffffe066);
        g.text(s, l + 88 * u, t + 60 * u, 48 * u, Gfx.ALIGN_LEFT, 6 * u, INK);
        return l;
    }

    private final float[] artX = new float[40], artY = new float[40];

    /** A wavy snake used for previews. */
    void snakeArt(Gfx g, Brawler br, int[] pal, float cx, float cy, float scale, float time) {
        int n = 26;
        float r = 20f, sp = r * 0.55f;
        for (int i = 0; i < n; i++) {
            artX[i] = -i * sp + n * sp * 0.5f;
            artY[i] = MathUtil.sin(time * 3f - i * 0.38f) * 26f * Math.min(1f, i / 6f + 0.15f);
        }
        g.save();
        g.translate(cx, cy);
        g.scale(scale);
        Snake.drawBody(g, artX, artY, n, r, pal, null, 1f, false, 0, time, -1e5f, -1e5f, 1e5f, 1e5f);
        float ang = MathUtil.angleTo(artX[1], artY[1], artX[0], artY[0]);
        int c1 = pal[0], c2 = pal.length > 1 ? pal[1] : pal[0];
        Snake.drawHead(g, artX[0], artY[0], r, ang, ang, c1, c2, br.id, br.accent, 1f, 0, time, 0);
        g.restore();
    }

    /** Draws word-wrapped text; returns the y below the last line. */
    float wrap(Gfx g, String text, float l, float r, float y, float size, int color, float limitY) {
        g.color(color);
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String test = line.length() == 0 ? words[i] : line + " " + words[i];
            if (g.measureText(test, size) > r - l && line.length() > 0) {
                if (y + size > limitY) return y;
                g.text(line.toString(), l, y + size, size, Gfx.ALIGN_LEFT, 3 * u, INK);
                y += size * 1.25f;
                line.setLength(0);
                line.append(words[i]);
            } else {
                line.setLength(0);
                line.append(test);
            }
        }
        if (line.length() > 0 && y + size <= limitY) {
            g.text(line.toString(), l, y + size, size, Gfx.ALIGN_LEFT, 3 * u, INK);
            y += size * 1.25f;
        }
        return y;
    }
}
