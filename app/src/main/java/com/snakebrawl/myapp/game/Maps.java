package com.snakebrawl.myapp.game;

/**
 * Arenas: three built-in maps and up to {@link #SLOTS} maps the player draws in the map maker.
 * A player-made map is a {@link #G} x {@link #G} grid of walls and bushes that is stretched over
 * the arena, so the same map works in every mode. Maps are saved and shared as short text codes.
 */
final class Maps {
    private Maps() {}

    static final int SUNNY = 0, FROST = 1, LAVA = 2, BUILT_IN = 3;
    static final String[] SUBS = {"A new island every match", "Mirror-symmetric ice fortress", "Rock canyon around a hot plaza"};

    /** Map ids of player maps are CUSTOM + slot. */
    static final int CUSTOM = 100, SLOTS = 6;
    /** Editor grid size: each cell covers 3 x 3 tiles of a 66-tile Showdown arena. */
    static final int G = 22;
    static final byte EMPTY = 0, WALL = 1, BUSH = 2;
    static final int NAME_MAX = 14;
    private static final String PREFIX = "SBMAP1-";

    /** A player-made map. */
    static final class Custom {
        String name = "MY MAP";
        int theme;
        final byte[] cells = new byte[G * G];

        Custom copy() {
            Custom c = new Custom();
            c.name = name;
            c.theme = theme;
            System.arraycopy(cells, 0, c.cells, 0, cells.length);
            return c;
        }

        int count(byte kind) {
            int n = 0;
            for (byte b : cells) if (b == kind) n++;
            return n;
        }
    }

    static boolean isCustom(int id) {
        return id >= CUSTOM && id < CUSTOM + SLOTS;
    }

    static boolean valid(Platform p, int id) {
        return id >= 0 && id < BUILT_IN || isCustom(id) && load(p, id - CUSTOM) != null;
    }

    static String name(Platform p, int id) {
        if (id >= 0 && id < BUILT_IN) return Theme.get(id).name;
        Custom c = isCustom(id) ? load(p, id - CUSTOM) : null;
        return c != null ? c.name : Theme.get(SUNNY).name;
    }

    static Custom load(Platform p, int slot) {
        return fromCode(p.loadString("map" + slot, ""));
    }

    static void save(Platform p, int slot, Custom c) {
        p.saveString("map" + slot, c == null ? "" : toCode(c));
    }

    static int freeSlot(Platform p) {
        for (int i = 0; i < SLOTS; i++) if (load(p, i) == null) return i;
        return -1;
    }

    /** Next map in the picker order: built-in maps, then the player's maps. */
    static int next(Platform p, int id) {
        int total = BUILT_IN + SLOTS;
        int idx = id < BUILT_IN ? id : BUILT_IN + id - CUSTOM;
        for (int k = 1; k <= total; k++) {
            int j = (idx + k) % total;
            int cand = j < BUILT_IN ? j : CUSTOM + j - BUILT_IN;
            if (valid(p, cand)) return cand;
        }
        return SUNNY;
    }

    /** Keeps only letters, digits and spaces (the map name is shown to friends). */
    static String cleanName(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < NAME_MAX; i++) {
            char c = Character.toUpperCase(s.charAt(i));
            if (c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == ' ' && b.length() > 0) b.append(c);
        }
        return b.toString().trim();
    }

    // ------------------------------------------------------------------ binary form (sent with START)

    static byte[] encode(Custom c) {
        String nm = cleanName(c.name);
        byte[] out = new byte[3 + nm.length() + (G * G + 3) / 4];
        out[0] = 1;
        out[1] = (byte) c.theme;
        out[2] = (byte) nm.length();
        for (int i = 0; i < nm.length(); i++) out[3 + i] = (byte) nm.charAt(i);
        int at = 3 + nm.length();
        for (int i = 0; i < G * G; i++) out[at + i / 4] |= (byte) ((c.cells[i] & 3) << ((i % 4) * 2));
        return out;
    }

    static Custom decode(byte[] b) {
        if (b == null || b.length < 3 || b[0] != 1) return null;
        int nl = b[2] & 0xff;
        if (nl > NAME_MAX || b.length != 3 + nl + (G * G + 3) / 4) return null;
        Custom c = new Custom();
        c.theme = Math.max(0, Math.min(Theme.ALL.length - 1, b[1]));
        StringBuilder nm = new StringBuilder();
        for (int i = 0; i < nl; i++) nm.append((char) (b[3 + i] & 0xff));
        c.name = cleanName(nm.toString());
        if (c.name.length() == 0) c.name = "MY MAP";
        int at = 3 + nl;
        for (int i = 0; i < G * G; i++) {
            int v = (b[at + i / 4] >> ((i % 4) * 2)) & 3;
            c.cells[i] = (byte) (v == WALL || v == BUSH ? v : EMPTY);
        }
        return c;
    }

    // ------------------------------------------------------------------ share codes

    private static final String B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    static String toCode(Custom c) {
        byte[] b = encode(c);
        StringBuilder s = new StringBuilder(PREFIX);
        for (int i = 0; i < b.length; i += 3) {
            int v = (b[i] & 0xff) << 16;
            if (i + 1 < b.length) v |= (b[i + 1] & 0xff) << 8;
            if (i + 2 < b.length) v |= b[i + 2] & 0xff;
            int chars = i + 2 < b.length ? 4 : (i + 1 < b.length ? 3 : 2);
            for (int k = 0; k < chars; k++) s.append(B64.charAt((v >> (18 - 6 * k)) & 63));
        }
        return s.toString();
    }

    /** Reads a map code (pasted text may contain spaces or the invite sentence around it). */
    static Custom fromCode(String code) {
        if (code == null) return null;
        int at = code.indexOf(PREFIX);
        if (at < 0) return null;
        StringBuilder clean = new StringBuilder();
        for (int i = at + PREFIX.length(); i < code.length(); i++) {
            char ch = code.charAt(i);
            if (B64.indexOf(ch) >= 0) clean.append(ch);
            else if (ch != ' ' && ch != '\n' && ch != '\r') break;
        }
        int n = clean.length();
        if (n < 4) return null;
        byte[] out = new byte[n * 3 / 4];
        int o = 0;
        for (int i = 0; i < n; i += 4) {
            int v = 0, chars = Math.min(4, n - i);
            for (int k = 0; k < 4; k++) v = v << 6 | (k < chars ? B64.indexOf(clean.charAt(i + k)) : 0);
            for (int k = 0; k < chars - 1 && o < out.length; k++) out[o++] = (byte) (v >> (16 - 8 * k));
        }
        return decode(out);
    }

    // ------------------------------------------------------------------ starter maps for the editor

    /** A blank map with a little cover in the middle, so a new map is never totally empty. */
    static Custom starter() {
        Custom c = new Custom();
        int m = G / 2;
        for (int k = -2; k <= 1; k++) {
            c.cells[(m - 4) * G + m + k] = WALL;
            c.cells[(m + 3) * G + m + k] = WALL;
        }
        for (int y = m - 1; y <= m; y++) for (int x = m - 1; x <= m; x++) c.cells[y * G + x] = BUSH;
        return c;
    }
}
