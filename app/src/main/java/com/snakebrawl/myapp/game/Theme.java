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

    static final Theme[] ALL = {sunny(), frost(), lava(), dunes(), candy(), neon(), jungle()};

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

    private static Theme dunes() {
        Theme t = new Theme("SUNSET DUNES");
        t.seaTop = 0xff3fb6c8;
        t.seaBottom = 0xff1a6f8a;
        t.seaFlat = 0xff258596;
        t.wave = 0x35ffffff;
        t.foam = 0x66ffffff;
        t.rimWet = 0xffc8964e;
        t.rimDry = 0xffdcae64;
        t.ground = 0xfff0c27a;
        t.checkA = 0xffe4b06a;
        t.checkB = 0xffe8b770;
        t.checkLight = 0xfff6d08e;
        t.meadow = 0xffd89a52;
        t.grain = 0x22804a10;
        t.tuft = 0xffb88a40;
        t.pebble = 0xffc09a6a;
        t.pebbleHi = 0xffe8cca0;
        t.flowerA = 0xffff8a3a;
        t.flowerB = 0xffffe08a;
        t.fenceDark = 0xff6a4220;
        t.fence = 0xff9a6230;
        t.fenceHi = 0xffc88a46;
        t.wallFrontA = 0xffc87a46;
        t.wallFrontB = 0xff8a4a26;
        t.wallFrontFlat = 0xffb06a3c;
        t.wallTopEdge = 0xff5a3018;
        t.wallTopA = 0xffeeaa6a;
        t.wallTopB = 0xffd08a50;
        t.wallTopFlat = 0xffdc9a5c;
        t.wallHi = 0xfffcc68a;
        t.mortar = 0x55804020;
        t.moss = 0xcc8aa040;
        t.mossHi = 0xccb8c860;
        t.bush0 = 0x33000000;
        t.bush1 = 0xff3f6a22;
        t.bush2 = 0xff6a9a2e;
        t.bush3 = 0xff8ebc3e;
        t.bush4 = 0xffb4d85a;
        t.bushLowA = 0xff4a7426;
        t.bushLowB = 0xff7aa836;
        t.bushFlower = 0xffffd23f;
        t.mapGround = 0xffdcaa66;
        t.mapWall = 0xffa0602e;
        t.mapBush = 0xff6a9a2e;
        t.sunPatch = 0x26fff0b0;
        t.shadePatch = 0x18402000;
        t.ambient = 0xfffff0c0;
        t.leaf = 0xccd8b060;
        t.leafVein = 0xaa9a7030;
        t.ambientStyle = 0;
        t.tintTop = 0x28ff9a40;
        t.tintBottom = 0x1c402040;
        return t;
    }

    private static Theme candy() {
        Theme t = new Theme("CANDY KINGDOM");
        t.seaTop = 0xffff9ad2;
        t.seaBottom = 0xffc85aa8;
        t.seaFlat = 0xffe478bc;
        t.wave = 0x40ffffff;
        t.foam = 0x88ffffff;
        t.rimWet = 0xffe88ab4;
        t.rimDry = 0xfff6aecb;
        t.ground = 0xfffff0f6;
        t.checkA = 0xffffe0ee;
        t.checkB = 0xffffe6f1;
        t.checkLight = 0xffffffff;
        t.meadow = 0xffffc2e0;
        t.grain = 0x16a04a7a;
        t.tuft = 0xffff8ac0;
        t.pebble = 0xffa8e4ff;
        t.pebbleHi = 0xffe6f8ff;
        t.flowerA = 0xff8af0c8;
        t.flowerB = 0xffffe066;
        t.fenceDark = 0xff7a2a5a;
        t.fence = 0xffff6aa8;
        t.fenceHi = 0xffffb8d8;
        t.wallFrontA = 0xff8a6aff;
        t.wallFrontB = 0xff5a3ad0;
        t.wallFrontFlat = 0xff7458e8;
        t.wallTopEdge = 0xff3a2296;
        t.wallTopA = 0xffd8c8ff;
        t.wallTopB = 0xffa890ff;
        t.wallTopFlat = 0xffb8a4ff;
        t.wallHi = 0xfff0e8ff;
        t.mortar = 0x55ffffff;
        t.moss = 0xccff6aa8;
        t.mossHi = 0xccffd2e8;
        t.bush0 = 0x33500030;
        t.bush1 = 0xff2aa078;
        t.bush2 = 0xff3fd29a;
        t.bush3 = 0xff7aecc0;
        t.bush4 = 0xffb8ffe0;
        t.bushLowA = 0xff30a882;
        t.bushLowB = 0xff52d8a4;
        t.bushFlower = 0xffff6aa8;
        t.mapGround = 0xfff6c8dc;
        t.mapWall = 0xff6a50e0;
        t.mapBush = 0xff3fd29a;
        t.sunPatch = 0x22ffffff;
        t.shadePatch = 0x14600040;
        t.ambient = 0xffffffff;
        t.leaf = 0xccff8ac0;
        t.leafVein = 0xaaffffff;
        t.ambientStyle = 1;
        t.tintTop = 0x22ffd0f0;
        t.tintBottom = 0x183a1060;
        return t;
    }

    private static Theme neon() {
        Theme t = new Theme("NEON NIGHTS");
        t.seaTop = 0xff1a1440;
        t.seaBottom = 0xff07051a;
        t.seaFlat = 0xff100c2a;
        t.wave = 0x3a8a6aff;
        t.foam = 0x6658e8ff;
        t.rimWet = 0xff2a2050;
        t.rimDry = 0xff3a2e6a;
        t.ground = 0xff2c2a48;
        t.checkA = 0xff262440;
        t.checkB = 0xff292744;
        t.checkLight = 0xff34325a;
        t.meadow = 0xff3a2a6a;
        t.grain = 0x1a8a6aff;
        t.tuft = 0xff5a3aaa;
        t.pebble = 0xff444066;
        t.pebbleHi = 0xff6a66a0;
        t.flowerA = 0xff3ff0ff;
        t.flowerB = 0xffff4ad7;
        t.fenceDark = 0xff0a0818;
        t.fence = 0xff1a1838;
        t.fenceHi = 0xff3ff0ff;
        t.wallFrontA = 0xff2a2860;
        t.wallFrontB = 0xff141236;
        t.wallFrontFlat = 0xff20204a;
        t.wallTopEdge = 0xff07061a;
        t.wallTopA = 0xff4a4890;
        t.wallTopB = 0xff34327a;
        t.wallTopFlat = 0xff3c3a84;
        t.wallHi = 0xff3ff0ff;
        t.mortar = 0x88ff4ad7;
        t.moss = 0xccff4ad7;
        t.mossHi = 0xff3ff0ff;
        t.bush0 = 0x55000000;
        t.bush1 = 0xff0f3a4a;
        t.bush2 = 0xff1a6a7a;
        t.bush3 = 0xff2aa0b0;
        t.bush4 = 0xff5ae0f0;
        t.bushLowA = 0xff12505e;
        t.bushLowB = 0xff22889a;
        t.bushFlower = 0xffff4ad7;
        t.mapGround = 0xff2a2846;
        t.mapWall = 0xff3ff0ff;
        t.mapBush = 0xff2aa0b0;
        t.sunPatch = 0x1a3ff0ff;
        t.shadePatch = 0x30000010;
        t.ambient = 0xffff4ad7;
        t.leaf = 0xcc3ff0ff;
        t.leafVein = 0xaaff4ad7;
        t.ambientStyle = 2;
        t.tintTop = 0x1c8a3aff;
        t.tintBottom = 0x30000018;
        return t;
    }

    private static Theme jungle() {
        Theme t = new Theme("JUNGLE RUINS");
        t.seaTop = 0xff2fa08a;
        t.seaBottom = 0xff12544a;
        t.seaFlat = 0xff1a6e5e;
        t.wave = 0x30ffffff;
        t.foam = 0x55e8fff6;
        t.rimWet = 0xff6a5a32;
        t.rimDry = 0xff8a7442;
        t.ground = 0xff9ab85a;
        t.checkA = 0xff8aab4e;
        t.checkB = 0xff90b052;
        t.checkLight = 0xffa8c868;
        t.meadow = 0xff5a9a3a;
        t.grain = 0x1e203a10;
        t.tuft = 0xff4a8a2a;
        t.pebble = 0xff8a8a70;
        t.pebbleHi = 0xffb8b8a0;
        t.flowerA = 0xffff5a5a;
        t.flowerB = 0xffffd23f;
        t.fenceDark = 0xff3a2a14;
        t.fence = 0xff6a4a24;
        t.fenceHi = 0xff8a6a3a;
        t.wallFrontA = 0xff6a7a6a;
        t.wallFrontB = 0xff3a4a40;
        t.wallFrontFlat = 0xff56665a;
        t.wallTopEdge = 0xff22302a;
        t.wallTopA = 0xffa8b498;
        t.wallTopB = 0xff7a8a74;
        t.wallTopFlat = 0xff8a9a82;
        t.wallHi = 0xffc0ccb2;
        t.mortar = 0x55304030;
        t.moss = 0xcc4aa03a;
        t.mossHi = 0xcc7ad05a;
        t.bush0 = 0x40000000;
        t.bush1 = 0xff14421a;
        t.bush2 = 0xff22742a;
        t.bush3 = 0xff38a03a;
        t.bush4 = 0xff62c850;
        t.bushLowA = 0xff1a5a22;
        t.bushLowB = 0xff2e8a32;
        t.bushFlower = 0xffff5a8a;
        t.mapGround = 0xff86a64e;
        t.mapWall = 0xff56665a;
        t.mapBush = 0xff22742a;
        t.sunPatch = 0x20fff4b0;
        t.shadePatch = 0x22001a00;
        t.ambient = 0xffd8ffa0;
        t.leaf = 0xcc4ab83a;
        t.leafVein = 0xaa2a7a20;
        t.ambientStyle = 0;
        t.tintTop = 0x1cfff0a0;
        t.tintBottom = 0x22102a20;
        return t;
    }
}
