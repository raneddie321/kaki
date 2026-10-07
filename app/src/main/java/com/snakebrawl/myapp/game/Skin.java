package com.snakebrawl.myapp.game;

/** Cosmetic snake color patterns sold in the shop. Skin 0 uses the brawler's own colors. */
final class Skin {
    final String name;
    final int price;
    final int rarity;
    /**
     * Body colors, cycled every three segments. Null means "use the brawler colors". A special
     * pattern is stored as a last entry whose alpha byte is {@link #STYLE_MARK}, so it travels
     * with the palette everywhere (saves, bots, multiplayer).
     */
    final int[] palette;

    static final int STYLE_NONE = 0, STYLE_SPIKES = 1, STYLE_SPONGE = 2;
    private static final int STYLE_MARK = 0x01;

    private static int style(int s) {
        return (STYLE_MARK << 24) | s;
    }

    /** Pattern of a palette (see {@link #palette}). */
    static int styleOf(int[] pal) {
        int last = pal[pal.length - 1];
        return pal.length > 1 && (last >>> 24) == STYLE_MARK ? last & 0xff : STYLE_NONE;
    }

    /** Number of real colors in a palette. */
    static int colorCount(int[] pal) {
        return styleOf(pal) != STYLE_NONE ? pal.length - 1 : pal.length;
    }

    private Skin(String name, int price, int rarity, int... palette) {
        this.name = name;
        this.price = price;
        this.rarity = rarity;
        this.palette = palette.length == 0 ? null : palette;
    }

    int[] paletteFor(Brawler b) {
        return palette != null ? palette : new int[]{b.color1, b.color2};
    }

    static final Skin[] ALL = {
            new Skin("Classic", 0, 0),
            new Skin("Lime", 150, 0, 0xff9be35a, 0xff4f9a26),
            new Skin("Ocean", 150, 0, 0xff59d7ff, 0xff2b6fc4),
            new Skin("Bubblegum", 200, 0, 0xffffb0f0, 0xffff6fb5),
            new Skin("Mint", 200, 0, 0xffb4ffd8, 0xff3fd08a),
            new Skin("Tiger", 300, 1, 0xffff9a2e, 0xffff9a2e, 0xff2a1a10),
            new Skin("Zebra", 300, 1, 0xfff4f4f4, 0xff222230),
            new Skin("Candy Cane", 400, 1, 0xffffffff, 0xffe8304a),
            new Skin("Watermelon", 400, 1, 0xff4ad04a, 0xffb4ff8a, 0xffff5a6a),
            new Skin("Bee", 500, 1, 0xffffd23f, 0xff2a2a2a),
            new Skin("Lava", 600, 2, 0xffff3a1a, 0xffff9a2e, 0xffffe066),
            new Skin("Toxic", 600, 2, 0xffa6ff3a, 0xff1c2a10),
            new Skin("Ice Queen", 700, 2, 0xffffffff, 0xffbff0ff, 0xff7ad8ff),
            new Skin("Sunset", 800, 2, 0xff8a3cff, 0xffff5ab5, 0xffffa64a),
            new Skin("Clown", 900, 2, 0xffff4a4a, 0xffffe14a, 0xff3f8cff),
            new Skin("Neon", 1000, 3, 0xff27f0ff, 0xffff2ad7),
            new Skin("Shadow", 1200, 3, 0xff2a2a36, 0xff111118, 0xffff2a4a),
            new Skin("Galaxy", 1400, 3, 0xff2a1a66, 0xff6a2bd1, 0xff151540, 0xffe8e8ff),
            new Skin("Rainbow", 1800, 4, 0xffff4a4a, 0xffffa62e, 0xffffe14a, 0xff4ae04a, 0xff3fa0ff, 0xffb35cff),
            new Skin("Gold", 2200, 4, 0xffffe066, 0xffffc928, 0xffd99a1a),
            new Skin("Diamond", 2800, 4, 0xffe8ffff, 0xff8ae8ff, 0xffc8f4ff),
            new Skin("Speedster", 2500, 4, 0xff2f72ff, 0xff2f72ff, 0xff1d48c8, style(STYLE_SPIKES)),
            new Skin("Sea Sponge", 2500, 4, 0xfffff05a, 0xffffe640, 0xfff0d238, style(STYLE_SPONGE)),
    };
}
