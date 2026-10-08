package com.snakebrawl.myapp.game;

/** The look of an arena: ground, walls, bushes and the sea around it. One per map theme. */
final class Theme {
    final String name;
    /** Sea gradient (top, bottom), flat sea, wave lines. */
    int seaTop, seaBottom, seaFlat, wave;
    /** Shore: foam, wet rim, dry rim. */
    int foam, rimWet, rimDry;
    /** Ground: base, dark checker (two variants), light checker, meadow tint, grain. */
    int ground, checkA, checkB, checkLight, meadow, grain;
    /** Decorations: tuft colour, pebble, pebble highlight, flower petals A/B. */
    int tuft, pebble, pebbleHi, flowerA, flowerB;
    /** Fence around the arena. */
    int fenceDark, fence, fenceHi;
    /** Walls: front face gradient and flat, top outline, top gradient and flat, top highlight, mortar, moss. */
    int wallFrontA, wallFrontB, wallFrontFlat, wallTopEdge, wallTopA, wallTopB, wallTopFlat, wallHi, mortar, moss, mossHi;
    /** Bushes: shadow, then four layers dark to light (fancy), and the two low-graphics layers. */
    int bush0, bush1, bush2, bush3, bush4, bushLowA, bushLowB, bushFlower;
    /** Minimap: ground, walls, bushes. */
    int mapGround, mapWall, mapBush;
    /** Light patches (sun / shade) and floating ambient bits. */
    int sunPatch, shadePatch, ambient, leaf, leafVein;
    /** Ambient style: 0 pollen and leaves, 1 snowflakes, 2 embers. */
    int ambientStyle;
    /** Screen tint: warm top glow and cool bottom shade. */
    int tintTop, tintBottom;

    private Theme(String name) {
        this.name = name;
    }

    static final Theme[] ALL = {sunny(), frost(), lava()};

    static Theme get(int i) {
        return ALL[i >= 0 && i < ALL.length ? i : 0];
    }

    private static Theme sunny() {
        Theme t = new Theme("SUNNY ISLAND");
        t.seaTop = 0xff2f8fd0;
        t.seaBottom = 0xff174a85;
        t.seaFlat = 0xff1d5e8c;
        t.wave = 0x2affffff;
        t.foam = 0x55ffffff;
        t.rimWet = 0xffb08c58;
        t.rimDry = 0xffc9a46a;
        t.ground = 0xffeed49e;
        t.checkA = 0xffe0c085;
        t.checkB = 0xffe5c78d;
        t.checkLight = 0xfff2dba8;
        t.meadow = 0xff9cc45a;
        t.grain = 0x1e6a4a1a;
        t.tuft = 0xff8fb04a;
        t.pebble = 0xffb8a88c;
        t.pebbleHi = 0xffd8ccb4;
        t.flowerA = 0xffff7ab0;
        t.flowerB = 0xffffffff;
        t.fenceDark = 0xff5a3a1a;
        t.fence = 0xff8b5a2b;
        t.fenceHi = 0xffb07a3c;
        t.wallFrontA = 0xff5a6688;
        t.wallFrontB = 0xff3a4260;
        t.wallFrontFlat = 0xff4e5873;
        t.wallTopEdge = 0xff2a3048;
        t.wallTopA = 0xffa6b2d0;
        t.wallTopB = 0xff7a87a6;
        t.wallTopFlat = 0xff7d8aa8;
        t.wallHi = 0xffb8c4e0;
        t.mortar = 0x55404a66;
        t.moss = 0xcc5a9a3a;
        t.mossHi = 0xcc8ac85a;
        t.bush0 = 0x33000000;
        t.bush1 = 0xff1e5426;
        t.bush2 = 0xff3a9a3a;
        t.bush3 = 0xff5cc04c;
        t.bush4 = 0xff8ae070;
        t.bushLowA = 0xff23602a;
        t.bushLowB = 0xff3f9b3c;
        t.bushFlower = 0xffff7ab0;
        t.mapGround = 0xffcdb37e;
        t.mapWall = 0xff5a6582;
        t.mapBush = 0xff3c8a3a;
        t.sunPatch = 0x22fff4c8;
        t.shadePatch = 0x16301a00;
        t.ambient = 0xfffff0b0;
        t.leaf = 0xcc6ab84a;
        t.leafVein = 0xaa3f8a2e;
        t.ambientStyle = 0;
        t.tintTop = 0x1cffe2a0;
        t.tintBottom = 0x18203060;
        return t;
    }

    private static Theme frost() {
        Theme t = new Theme("FROST PEAK");
        t.seaTop = 0xff5aa6d8;
        t.seaBottom = 0xff1f4f86;
        t.seaFlat = 0xff2d6c9c;
        t.wave = 0x40ffffff;
        t.foam = 0x88f2fbff;
        t.rimWet = 0xff9fc2dc;
        t.rimDry = 0xffc4dcef;
        t.ground = 0xfff3f8fd;
        t.checkA = 0xffdde9f4;
        t.checkB = 0xffe4eef7;
        t.checkLight = 0xffffffff;
        t.meadow = 0xff9fd6f0;
        t.grain = 0x1a5c7da0;
        t.tuft = 0xff8fb7c8;
        t.pebble = 0xffa9b9c9;
        t.pebbleHi = 0xffe6eef6;
        t.flowerA = 0xff7fd8ff;
        t.flowerB = 0xffc8f2ff;
        t.fenceDark = 0xff3a4a66;
        t.fence = 0xff6f86a8;
        t.fenceHi = 0xffb4c8e4;
        t.wallFrontA = 0xff5a8ec4;
        t.wallFrontB = 0xff2f5a8c;
        t.wallFrontFlat = 0xff4a7aaa;
        t.wallTopEdge = 0xff23446a;
        t.wallTopA = 0xffe4f6ff;
        t.wallTopB = 0xffa8d4f0;
        t.wallTopFlat = 0xffbfe2f6;
        t.wallHi = 0xffffffff;
        t.mortar = 0x5537668f;
        t.moss = 0xddffffff;
        t.mossHi = 0xffffffff;
        t.bush0 = 0x33001020;
        t.bush1 = 0xff123f3a;
        t.bush2 = 0xff1f6b5a;
        t.bush3 = 0xff2f8f72;
        t.bush4 = 0xffeaf6ff;
        t.bushLowA = 0xff16473f;
        t.bushLowB = 0xff2a7a64;
        t.bushFlower = 0xffffffff;
        t.mapGround = 0xffd8e6f2;
        t.mapWall = 0xff4a7aaa;
        t.mapBush = 0xff2a7a64;
        t.sunPatch = 0x26ffffff;
        t.shadePatch = 0x14203a60;
        t.ambient = 0xffffffff;
        t.leaf = 0xeeffffff;
        t.leafVein = 0x88b8d8f0;
        t.ambientStyle = 1;
        t.tintTop = 0x18d8f0ff;
        t.tintBottom = 0x20203a70;
        return t;
    }

    private static Theme lava() {
        Theme t = new Theme("LAVA CANYON");
        t.seaTop = 0xffff7a1a;
        t.seaBottom = 0xffa8200a;
        t.seaFlat = 0xffd84a12;
        t.wave = 0x55ffe08a;
        t.foam = 0x88ffd23f;
        t.rimWet = 0xff3a2018;
        t.rimDry = 0xff5a3424;
        t.ground = 0xffa86c4a;
        t.checkA = 0xff9a5f40;
        t.checkB = 0xffa06446;
        t.checkLight = 0xffb87a56;
        t.meadow = 0xff6a3a2a;
        t.grain = 0x2a2a1008;
        t.tuft = 0xff6a4a2a;
        t.pebble = 0xff4a3a36;
        t.pebbleHi = 0xff7a6460;
        t.flowerA = 0xffff6a1a;
        t.flowerB = 0xffffc23f;
        t.fenceDark = 0xff1e1210;
        t.fence = 0xff3a2420;
        t.fenceHi = 0xffff7a2a;
        t.wallFrontA = 0xff6a3a30;
        t.wallFrontB = 0xff3a1e18;
        t.wallFrontFlat = 0xff563028;
        t.wallTopEdge = 0xff241210;
        t.wallTopA = 0xff8a5244;
        t.wallTopB = 0xff5e3428;
        t.wallTopFlat = 0xff744234;
        t.wallHi = 0xffa86a56;
        t.mortar = 0x88ff6a1a;
        t.moss = 0xccff7a1a;
        t.mossHi = 0xffffd23f;
        t.bush0 = 0x44000000;
        t.bush1 = 0xff3a3a14;
        t.bush2 = 0xff6a6a1e;
        t.bush3 = 0xff8f8a2a;
        t.bush4 = 0xffc8b84a;
        t.bushLowA = 0xff44441a;
        t.bushLowB = 0xff7a7424;
        t.bushFlower = 0xffff6a1a;
        t.mapGround = 0xff9a6446;
        t.mapWall = 0xff3a1e18;
        t.mapBush = 0xff7a7424;
        t.sunPatch = 0x22ff9a3a;
        t.shadePatch = 0x22200000;
        t.ambient = 0xffffa23a;
        t.leaf = 0xccffd23f;
        t.leafVein = 0xaaff6a1a;
        t.ambientStyle = 2;
        t.tintTop = 0x22ff8a3a;
        t.tintBottom = 0x22300808;
        return t;
    }
}
