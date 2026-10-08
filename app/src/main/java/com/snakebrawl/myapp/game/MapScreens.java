package com.snakebrawl.myapp.game;

/**
 * Map picker (built-in maps and the player's own maps) and the map maker, where players paint
 * walls and bushes on a grid, pick a look, test the map and share it as a code.
 */
final class MapScreens {
    static final int B_FIRST = 820;
    private static final int B_NEW = 820, B_IMPORT = 821, B_PICK = 830, B_EDIT = 845, B_SHARE = 855;
    private static final int B_TOOL = 870, B_MIRROR = 875, B_THEME = 876, B_NAME = 877, B_CLEAR = 878, B_TEST = 879, B_SAVE = 880,
            B_DELETE = 881, B_FILL = 882;
    static final int B_LAST = 890;

    private static final int CARDS = Maps.BUILT_IN + Maps.SLOTS;
    private static final int TOOL_WALL = 0, TOOL_BUSH = 1, TOOL_ERASE = 2;
    private static final String[] TOOL_NAMES = {"WALL", "BUSH", "ERASE"};
    private static final String[] MIRROR_NAMES = {"MIRROR: OFF", "MIRROR: 2", "MIRROR: 4"};

    private final Game game;
    private final Ui ui;
    private final Profile pr;

    /** Built-in map previews (walls and bushes on a Showdown arena), made once. */
    private byte[][] previewTiles;
    private boolean[][] previewBush;
    private int previewN;
    /** Saved player maps by slot (null for empty), reloaded when the picker opens. */
    private final Maps.Custom[] saved = new Maps.Custom[Maps.SLOTS];

    // Editor state
    private Maps.Custom edit;
    private int editSlot = -1;
    private int tool = TOOL_WALL, mirror = 2;
    private boolean dirty;
    private int paintPtr = -1;
    private float gridL, gridT, gridS;

    MapScreens(Game game) {
        this.game = game;
        this.ui = game.ui;
        this.pr = game.profile;
    }

    static boolean handles(int id) {
        return id >= B_FIRST && id < B_LAST;
    }

    void reload() {
        for (int i = 0; i < Maps.SLOTS; i++) saved[i] = Maps.load(game.gated, i);
    }

    private void makePreviews() {
        if (previewTiles != null) return;
        previewTiles = new byte[Maps.BUILT_IN][];
        previewBush = new boolean[Maps.BUILT_IN][];
        Rng prev = MathUtil.RNG;
        try {
            for (int m = 0; m < Maps.BUILT_IN; m++) {
                MathUtil.RNG = new Rng(9137L + m * 31L);
                World wd = new World(game.gated, World.MODE_SHOWDOWN, new Brawler[]{Brawler.ALL[0]}, null, null, 0, 0, 0, 2, m, null);
                previewTiles[m] = wd.tiles.clone();
                previewBush[m] = wd.bush.clone();
                previewN = wd.n;
            }
        } finally {
            MathUtil.RNG = prev;
        }
    }

    // ------------------------------------------------------------------ picker

    private float cardW() {
        return Math.min(560 * game.u, (game.w - game.padL - game.padR - 120 * game.u) / 3f);
    }

    void layoutPicker() {
        float u = game.u, w = game.w, h = game.h;
        ui.add(Game.B_BACK, game.padL + 10 * u, game.padT + 10 * u, game.padL + 230 * u, game.padT + 110 * u, "BACK", null, 0xffff5a5a);
        float r = w - game.padR - 20 * u;
        ui.add(B_IMPORT, r - 330 * u, game.padT + 10 * u, r, game.padT + 120 * u, "IMPORT", "Paste a map code", 0xff3fa0ff);
        if (Maps.freeSlot(game.gated) >= 0) {
            ui.add(B_NEW, r - 690 * u, game.padT + 10 * u, r - 350 * u, game.padT + 120 * u, "MAP MAKER", "Draw a new map", 0xff4ad04a);
        }
        float cw = cardW(), ch = 330 * u, gap = 30 * u;
        float total = 3 * cw + 2 * gap, l0 = w / 2 - total / 2;
        float top = game.padT + 160 * u, bottom = h - game.padB - 10 * u;
        int shown = Maps.BUILT_IN;
        for (int i = 0; i < Maps.SLOTS; i++) if (saved[i] != null) shown++;
        int rows = (shown + 2) / 3;
        ui.setScrollArea(game.padL, top, w - game.padR, bottom, rows * (ch + gap) + 20 * u);
        int k = 0;
        for (int c = 0; c < CARDS; c++) {
            if (c >= Maps.BUILT_IN && saved[c - Maps.BUILT_IN] == null) continue;
            float l = l0 + (k % 3) * (cw + gap), t = top + 10 * u + (k / 3) * (ch + gap);
            ui.addScroll(B_PICK + c, l, t, l + cw, t + ch, null, null, 0);
            if (c >= Maps.BUILT_IN) {
                int s = c - Maps.BUILT_IN;
                ui.addScroll(B_EDIT + s, l + 16 * u, t + ch - 86 * u, l + cw / 2 - 8 * u, t + ch - 14 * u, "EDIT", null, 0xff6a5cff);
                ui.addScroll(B_SHARE + s, l + cw / 2 + 8 * u, t + ch - 86 * u, l + cw - 16 * u, t + ch - 14 * u, "SHARE", null, 0xff3fa0ff);
            }
            k++;
        }
    }

    private static int idOfCard(int c) {
        return c < Maps.BUILT_IN ? c : Maps.CUSTOM + c - Maps.BUILT_IN;
    }

    void renderPicker(Gfx g) {
        float u = game.u, w = game.w, h = game.h;
        g.color(0xcc0d1030);
        g.fillRect(0, 0, w, h);
        g.color(0xffffd23f);
        g.text("MAPS", w / 2, game.padT + 92 * u, 84 * u, Gfx.ALIGN_CENTER, 9 * u, Ui.INK);
        makePreviews();
        g.save();
        g.clip(ui.viewL, ui.viewT, ui.viewR, ui.viewB);
        g.translate(0, -ui.scrollY);
        int sel = pr.map;
        for (int i = 0; i < ui.count; i++) {
            Ui.Btn b = ui.btns[i];
            if (b.id < B_PICK || b.id >= B_PICK + CARDS) continue;
            int c = b.id - B_PICK;
            int id = idOfCard(c);
            boolean custom = c >= Maps.BUILT_IN;
            Maps.Custom m = custom ? saved[c - Maps.BUILT_IN] : null;
            float d = ui.pressed == b.id ? 5 * u : 0;
            float l = b.l + d, t = b.t + d, r = b.r - d, bt = b.b - d;
            boolean on = id == sel;
            ui.panel(g, l, t, r, bt, on ? 0xff2f5a3a : 0xff262a54);
            if (on) {
                g.color(0xff6dff6d);
                g.strokeRoundRect(l - 4 * u, t - 4 * u, r + 4 * u, bt + 4 * u, 26 * u, 6 * u);
            }
            // Preview
            float ps = Math.min(r - l - 40 * u, (bt - t) - (custom ? 190 * u : 110 * u));
            float px = (l + r) / 2 - ps / 2, py = t + 20 * u;
            Theme th = Theme.get(custom ? m.theme : id);
            if (custom) drawCustom(g, m, px, py, ps, th, false);
            else drawPreview(g, c, px, py, ps, th);
            String name = custom ? m.name : th.name;
            g.color(0xffffffff);
            float ny = py + ps + 46 * u;
            g.text(name, (l + r) / 2, ny, Ui.fit(g, name, 40 * u, r - l - 30 * u), Gfx.ALIGN_CENTER, 5 * u, Ui.INK);
            g.color(0xffb8bdf0);
            String sub = custom ? th.name + " look" : Maps.SUBS[id];
            g.text(sub, (l + r) / 2, ny + 34 * u, Ui.fit(g, sub, 26 * u, r - l - 30 * u), Gfx.ALIGN_CENTER, 3 * u, Ui.INK);
            if (on) {
                // Check mark badge in the corner
                float bx = r - 34 * u, by = t + 34 * u;
                g.color(Ui.INK);
                g.fillCircle(bx, by, 26 * u);
                g.color(0xff3ac04a);
                g.fillCircle(bx, by, 21 * u);
                g.color(0xffffffff);
                g.line(bx - 10 * u, by, bx - 3 * u, by + 8 * u, 5 * u);
                g.line(bx - 3 * u, by + 8 * u, bx + 11 * u, by - 9 * u, 5 * u);
            }
        }
        for (int i = 0; i < ui.count; i++) {
            Ui.Btn b = ui.btns[i];
            if (b.scrolls && (b.id >= B_EDIT && b.id < B_EDIT + Maps.SLOTS || b.id >= B_SHARE && b.id < B_SHARE + Maps.SLOTS)) ui.button(g, b);
        }
        g.restore();
        for (int i = 0; i < ui.count; i++) if (!ui.btns[i].scrolls) ui.button(g, ui.btns[i]);
        g.color(0xffb8bdf0);
        g.text("Tap a map to play on it. Your map is used in Showdown, Duo, Endless and with friends.", w / 2, h - game.padB - 14 * u,
                26 * u, Gfx.ALIGN_CENTER, 3 * u, Ui.INK);
    }

    private void drawPreview(Gfx g, int m, float x, float y, float s, Theme th) {
        int n = previewN;
        float ts = s / n;
        g.color(th.mapGround);
        g.fillRoundRect(x, y, x + s, y + s, 8 * game.u);
        byte[] tl = previewTiles[m];
        boolean[] bs = previewBush[m];
        g.color(th.mapBush);
        for (int ty = 0; ty < n; ty++)
            for (int tx = 0; tx < n; tx++)
                if (bs[ty * n + tx]) g.fillRect(x + tx * ts, y + ty * ts, x + (tx + 1) * ts + 0.5f, y + (ty + 1) * ts + 0.5f);
        g.color(th.mapWall);
        for (int ty = 0; ty < n; ty++)
            for (int tx = 0; tx < n; tx++)
                if (tl[ty * n + tx] == World.WALL) g.fillRect(x + tx * ts, y + ty * ts, x + (tx + 1) * ts + 0.5f, y + (ty + 1) * ts + 0.5f);
    }

    /** Draws a player map's grid; with {@code editor} also the grid lines and the start zones. */
    private void drawCustom(Gfx g, Maps.Custom m, float x, float y, float s, Theme th, boolean editor) {
        float u = game.u;
        int n = Maps.G;
        float cs = s / n;
        g.color(th.mapGround);
        g.fillRoundRect(x, y, x + s, y + s, 8 * u);
        if (editor) {
            g.color(0x18000000);
            for (int ty = 0; ty < n; ty++)
                for (int tx = 0; tx < n; tx++)
                    if (((tx + ty) & 1) == 0) g.fillRect(x + tx * cs, y + ty * cs, x + (tx + 1) * cs, y + (ty + 1) * cs);
            // Start zones: snakes spawn on this ring, walls there are left out
            g.color(0x55ffffff);
            g.strokeCircle(x + s / 2, y + s / 2, s * 0.36f, 3 * u);
            g.color(0xffffffff);
            g.text("START RING", x + s / 2, y + s / 2 - s * 0.36f - 10 * u, 22 * u, Gfx.ALIGN_CENTER, 3 * u, Ui.INK);
        }
        for (int ty = 0; ty < n; ty++) {
            for (int tx = 0; tx < n; tx++) {
                byte c = m.cells[ty * n + tx];
                if (c == Maps.EMPTY) continue;
                float l = x + tx * cs, t = y + ty * cs;
                if (c == Maps.WALL) {
                    g.color(th.wallTopEdge);
                    g.fillRect(l, t, l + cs + 0.5f, t + cs + 0.5f);
                    g.color(th.wallTopFlat);
                    float in = editor ? cs * 0.08f : 0;
                    g.fillRect(l + in, t + in, l + cs - in, t + cs - in);
                } else {
                    g.color(th.bushLowA);
                    g.fillCircle(l + cs / 2, t + cs / 2, cs * 0.6f);
                    g.color(th.bushLowB);
                    g.fillCircle(l + cs / 2 - cs * 0.1f, t + cs / 2 - cs * 0.1f, cs * 0.42f);
                }
            }
        }
    }

    void onPickerButton(int id) {
        if (id == B_NEW) {
            int slot = Maps.freeSlot(game.gated);
            if (slot < 0) {
                game.showInfo("NO ROOM", "You can keep " + Maps.SLOTS + " maps. Delete one in the map maker first.", 0, 0);
                return;
            }
            Maps.Custom c = Maps.starter();
            c.name = "MY MAP " + (slot + 1);
            c.theme = Maps.SUNNY;
            openEditor(slot, c);
            return;
        }
        if (id == B_IMPORT) {
            game.gated.requestText("Paste a map code (starts with SBMAP1-)", "", 400, false, new Platform.TextCallback() {
                @Override
                public void onText(String text) {
                    if (text == null || text.trim().length() == 0) return;
                    Maps.Custom c = Maps.fromCode(text.trim());
                    if (c == null) {
                        game.showInfo("NOT A MAP CODE", "That code didn't work. Ask your friend to tap SHARE on their map again and copy all of it.",
                                0, 0);
                        return;
                    }
                    int slot = Maps.freeSlot(game.gated);
                    if (slot < 0) {
                        game.showInfo("NO ROOM", "You can keep " + Maps.SLOTS + " maps. Delete one in the map maker first.", 0, 0);
                        return;
                    }
                    Maps.save(game.gated, slot, c);
                    pr.map = Maps.CUSTOM + slot;
                    pr.save();
                    reload();
                    game.layout();
                    game.showInfo("MAP ADDED!", c.name + " is in your maps and selected. Have fun!", 0, 0);
                }
            });
            return;
        }
        if (id >= B_PICK && id < B_PICK + CARDS) {
            int mid = idOfCard(id - B_PICK);
            if (Maps.valid(game.gated, mid)) {
                pr.map = mid;
                pr.save();
                game.gated.playSound(Platform.SND_POWER, 0.6f);
            }
        } else if (id >= B_EDIT && id < B_EDIT + Maps.SLOTS) {
            int s = id - B_EDIT;
            if (saved[s] != null) openEditor(s, saved[s].copy());
            return;
        } else if (id >= B_SHARE && id < B_SHARE + Maps.SLOTS) {
            int s = id - B_SHARE;
            if (saved[s] != null) shareMap(saved[s]);
            return;
        }
        game.layout();
    }

    private void shareMap(Maps.Custom c) {
        String code = Maps.toCode(c);
        String text = "Try my Snake Brawl map \"" + c.name + "\"! In the game tap MAP, then IMPORT, and paste this code:\n" + code;
        if (!game.gated.share(text)) {
            game.showInfo("MAP CODE", "Sharing isn't available here. Your code: " + code, 0, 0);
        }
    }

    // ------------------------------------------------------------------ editor

    private void openEditor(int slot, Maps.Custom c) {
        edit = c;
        editSlot = slot;
        dirty = false;
        paintPtr = -1;
        game.setScreen(Game.EDITOR);
    }

    private void saveEdit() {
        if (edit == null || editSlot < 0) return;
        Maps.save(game.gated, editSlot, edit);
        dirty = false;
        reload();
    }

    /** Leaving the editor saves the map. */
    void leaveEditor() {
        saveEdit();
        paintPtr = -1;
    }

    void layoutEditor() {
        float u = game.u, w = game.w, h = game.h;
        float avail = h - game.padT - game.padB - 40 * u;
        gridS = Math.min(avail, w * 0.52f);
        gridL = game.padL + 20 * u;
        gridT = (h - gridS) / 2;
        float l = gridL + gridS + 50 * u, r = w - game.padR - 20 * u;
        float colW = (r - l - 20 * u) / 2;
        float t = game.padT + 20 * u, bh = 110 * u, gap = 18 * u;
        ui.add(Game.B_BACK, l, t, l + colW, t + bh, "DONE", "Saves the map", 0xffff5a5a);
        ui.add(B_TEST, l + colW + 20 * u, t, r, t + bh, "TEST PLAY", "Solo Showdown", 0xffffc928);
        t += bh + gap + 20 * u;
        float tw = (r - l - 40 * u) / 3;
        int[] cols = {0xff7d8aa8, 0xff3f9b3c, 0xffff7a6a};
        for (int i = 0; i < 3; i++) {
            Ui.Btn b = ui.add(B_TOOL + i, l + i * (tw + 20 * u), t, l + i * (tw + 20 * u) + tw, t + bh, TOOL_NAMES[i],
                    tool == i ? "SELECTED" : null, cols[i]);
            b.enabled = true;
        }
        t += bh + gap;
        ui.add(B_MIRROR, l, t, l + colW, t + bh, MIRROR_NAMES[mirror], "Paint on all sides", 0xff6a5cff);
        ui.add(B_THEME, l + colW + 20 * u, t, r, t + bh, Theme.get(edit.theme).name, "Tap for another look", 0xff3fb6a8);
        t += bh + gap;
        ui.add(B_NAME, l, t, l + colW, t + bh, "NAME", edit.name, 0xff3fa0ff);
        ui.add(B_FILL, l + colW + 20 * u, t, r, t + bh, "RANDOM", "Fill with a surprise", 0xffb35cff);
        t += bh + gap;
        ui.add(B_CLEAR, l, t, l + colW, t + bh, "CLEAR", "Start over", 0xff8a8fb8);
        ui.add(B_DELETE, l + colW + 20 * u, t, r, t + bh, "DELETE", "Remove this map", 0xffff5a5a);
    }

    void renderEditor(Gfx g) {
        float u = game.u, w = game.w, h = game.h;
        g.color(0xe60d1030);
        g.fillRect(0, 0, w, h);
        Theme th = Theme.get(edit.theme);
        g.color(Ui.INK);
        g.fillRoundRect(gridL - 10 * u, gridT - 10 * u, gridL + gridS + 10 * u, gridT + gridS + 10 * u, 18 * u);
        drawCustom(g, edit, gridL, gridT, gridS, th, true);
        for (int i = 0; i < ui.count; i++) ui.button(g, ui.btns[i]);
        float l = gridL + gridS + 50 * u;
        g.color(0xffb8bdf0);
        int walls = edit.count(Maps.WALL), bushes = edit.count(Maps.BUSH);
        g.text("Drag on the map to paint.  Walls " + walls + "  •  Bushes " + bushes, l, h - game.padB - 30 * u, 28 * u,
                Gfx.ALIGN_LEFT, 4 * u, Ui.INK);
    }

    boolean onGrid(float x, float y) {
        return x >= gridL && y >= gridT && x < gridL + gridS && y < gridT + gridS;
    }

    void paintDown(int ptr, float x, float y) {
        if (paintPtr >= 0) return;
        paintPtr = ptr;
        paint(x, y);
    }

    void paintMove(int ptr, float x, float y) {
        if (ptr == paintPtr) paint(x, y);
    }

    boolean paintUp(int ptr) {
        if (ptr != paintPtr) return false;
        paintPtr = -1;
        return true;
    }

    boolean painting(int ptr) {
        return ptr == paintPtr;
    }

    private void paint(float x, float y) {
        int n = Maps.G;
        int gx = (int) ((x - gridL) / gridS * n), gy = (int) ((y - gridT) / gridS * n);
        if (gx < 0 || gy < 0 || gx >= n || gy >= n) return;
        byte v = tool == TOOL_WALL ? Maps.WALL : tool == TOOL_BUSH ? Maps.BUSH : Maps.EMPTY;
        set(gx, gy, v);
        if (mirror >= 1) set(n - 1 - gx, n - 1 - gy, v);
        if (mirror >= 2) {
            set(n - 1 - gx, gy, v);
            set(gx, n - 1 - gy, v);
        }
    }

    private void set(int x, int y, byte v) {
        int i = y * Maps.G + x;
        if (edit.cells[i] != v) {
            edit.cells[i] = v;
            dirty = true;
        }
    }

    void onEditorButton(int id) {
        if (id >= B_TOOL && id < B_TOOL + 3) {
            tool = id - B_TOOL;
        } else if (id == B_MIRROR) {
            mirror = (mirror + 1) % 3;
        } else if (id == B_THEME) {
            edit.theme = (edit.theme + 1) % Theme.ALL.length;
            dirty = true;
        } else if (id == B_NAME) {
            game.gated.requestText("Map name", edit.name, Maps.NAME_MAX, false, new Platform.TextCallback() {
                @Override
                public void onText(String text) {
                    if (text == null) return;
                    String nm = Maps.cleanName(text);
                    if (nm.length() == 0) return;
                    edit.name = nm;
                    dirty = true;
                    game.layout();
                }
            });
            return;
        } else if (id == B_CLEAR) {
            for (int i = 0; i < edit.cells.length; i++) edit.cells[i] = Maps.EMPTY;
            dirty = true;
        } else if (id == B_FILL) {
            randomFill();
        } else if (id == B_DELETE) {
            game.confirm("DELETE MAP?", "Delete \"" + edit.name + "\"? This can't be undone.", 0, 0, Game.ACT_DELETE_MAP, editSlot);
            return;
        } else if (id == B_TEST) {
            saveEdit();
            pr.map = Maps.CUSTOM + editSlot;
            pr.save();
            game.startTestMatch();
            return;
        }
        game.layout();
    }

    /** Confirmed DELETE. */
    void deleteMap(int slot) {
        Maps.save(game.gated, slot, null);
        if (pr.map == Maps.CUSTOM + slot) pr.map = Maps.SUNNY;
        pr.save();
        edit = null;
        editSlot = -1;
        reload();
        game.setScreen(Game.MAPS);
    }

    /** A random symmetric layout to start from. */
    private void randomFill() {
        int n = Maps.G;
        for (int i = 0; i < edit.cells.length; i++) edit.cells[i] = Maps.EMPTY;
        int h = n / 2;
        for (int k = 0; k < 9; k++) {
            int x = 1 + MathUtil.randInt(h - 1), y = 1 + MathUtil.randInt(h - 1);
            boolean horiz = MathUtil.rand() < 0.5f;
            int len = 2 + MathUtil.randInt(3);
            byte v = MathUtil.rand() < 0.7f ? Maps.WALL : Maps.BUSH;
            for (int i = 0; i < len; i++) {
                int px = horiz ? x + i : x, py = horiz ? y : y + i;
                if (px >= h || py >= h) continue;
                edit.cells[py * n + px] = v;
                edit.cells[py * n + n - 1 - px] = v;
                edit.cells[(n - 1 - py) * n + px] = v;
                edit.cells[(n - 1 - py) * n + n - 1 - px] = v;
            }
        }
        dirty = true;
    }
}
