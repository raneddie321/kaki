package com.snakebrawl.myapp.game;

/** Static description of a playable snake class. */
final class Brawler {
    static final int VIPER = 0;
    static final int VOLT = 1;
    static final int BOOMER = 2;
    static final int BLAZE = 3;
    static final int FROST = 4;
    static final int ZIGGY = 5;
    static final int TOXIN = 6;
    static final int SHADE = 7;
    static final int COBRA = 8;
    static final int THORN = 9;
    static final int RUMBLE = 10;
    static final int NOVA = 11;
    // The crazy four: premium brawlers
    static final int JOKER = 12;
    static final int REAPER = 13;
    static final int MAGMA = 14;
    static final int GLITCH = 15;

    static final int MAX_LEVEL = 7;
    /** Coins to go from level i+1 to i+2. */
    static final int[] UPGRADE_COST = {100, 200, 350, 550, 800, 1100};

    static final String[] RARITY_NAMES = {"STARTER", "RARE", "EPIC", "MYTHIC", "LEGENDARY"};
    static final int[] RARITY_COLORS = {0xff9ad0ff, 0xff5ae05a, 0xffc65aff, 0xffff4a6a, 0xffffd23f};

    final int id;
    final String name;
    final String role;
    final String attackName;
    final String attackDesc;
    final String superName;
    final String superDesc;
    final int color1;
    final int color2;
    final int accent;
    final float hp;
    final float damage;
    final float range;
    final float reload;
    final float speed;
    final float superCost;
    final float superRange;
    /** Card stat bars, 1..5. */
    final int statHp, statDamage, statRange, statSpeed;
    final int rarity;
    final int price;
    /** Projectile speed used for leading targets; 0 for lobbed attacks. */
    final float projSpeed;

    private Brawler(int id, String name, String role, String attackName, String attackDesc,
                    String superName, String superDesc, int color1, int color2, int accent,
                    float hp, float damage, float range, float reload, float speed,
                    float superCost, float superRange,
                    int statHp, int statDamage, int statRange, int statSpeed,
                    int rarity, int price, float projSpeed) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.attackName = attackName;
        this.attackDesc = attackDesc;
        this.superName = superName;
        this.superDesc = superDesc;
        this.color1 = color1;
        this.color2 = color2;
        this.accent = accent;
        this.hp = hp;
        this.damage = damage;
        this.range = range;
        this.reload = reload;
        this.speed = speed;
        this.superCost = superCost;
        this.superRange = superRange;
        this.statHp = statHp;
        this.statDamage = statDamage;
        this.statRange = statRange;
        this.statSpeed = statSpeed;
        this.rarity = rarity;
        this.price = price;
        this.projSpeed = projSpeed;
    }

    boolean lobbed() {
        return id == BOOMER || id == TOXIN || id == MAGMA;
    }

    static final Brawler[] ALL = {
            new Brawler(VIPER, "VIPER", "Shotgunner",
                    "Fang Spray", "Blasts 5 venom pellets. Brutal up close.",
                    "Nova Blast", "A huge pellet wave that knocks snakes away.",
                    0xff3ddc5a, 0xff1f8f3a, 0xffffd23f,
                    3600, 280, 430, 1.45f, 1.0f, 3000, 560,
                    4, 4, 2, 3, 0, 0, 1300),
            new Brawler(VOLT, "VOLT", "Sniper",
                    "Spark Bolt", "Fires one fast long-range lightning bolt.",
                    "Rail Storm", "Unloads 8 bolts that fly through walls.",
                    0xff3fb6ff, 0xff1d5fd6, 0xfffff04a,
                    2800, 880, 800, 2.0f, 1.02f, 3300, 900,
                    2, 4, 5, 3, 0, 0, 2000),
            new Brawler(BOOMER, "BOOMER", "Thrower",
                    "Fuse Bomb", "Lobs a bomb over walls. Splash damage.",
                    "Mega Bomb", "A giant bomb with a massive blast.",
                    0xffff9a2e, 0xffd9461f, 0xff3a2a20,
                    3000, 1000, 620, 1.9f, 0.98f, 3200, 680,
                    3, 4, 4, 2, 0, 0, 0),
            new Brawler(BLAZE, "BLAZE", "Tank",
                    "Flame Breath", "Spits a short-range cone of fire.",
                    "Rampage", "Dashes forward, slicing through snake bodies.",
                    0xffe0344a, 0xff6a1030, 0xfff2f2f2,
                    5800, 220, 330, 1.0f, 1.08f, 2800, 520,
                    5, 3, 1, 4, 0, 0, 950),
            new Brawler(FROST, "FROST", "Controller",
                    "Ice Shards", "3 icy shards that slow snakes down.",
                    "Blizzard", "Freezing blast around you that nearly stops enemies.",
                    0xffbff0ff, 0xff4aa8e0, 0xffffffff,
                    3300, 580, 560, 1.35f, 1.0f, 3000, 320,
                    3, 3, 3, 3, 1, 500, 1300),
            new Brawler(ZIGGY, "ZIGGY", "Ricochet",
                    "Bouncy Balls", "Three balls that bounce off walls.",
                    "Pinball Party", "12 balls in every direction, bouncing 3 times.",
                    0xffff6fb5, 0xfffff04a, 0xff7a3cff,
                    3000, 580, 700, 1.15f, 1.04f, 3000, 750,
                    2, 4, 4, 3, 2, 800, 1400),
            new Brawler(TOXIN, "TOXIN", "Poisoner",
                    "Venom Glob", "Lobs a glob that leaves a poison puddle.",
                    "Toxic Cloud", "A huge toxic swamp that melts snakes.",
                    0xffa6ff3a, 0xff6a2bd1, 0xfff2ff8a,
                    3200, 550, 600, 1.7f, 1.0f, 3100, 640,
                    3, 3, 4, 3, 3, 1200, 0),
            new Brawler(SHADE, "SHADE", "Assassin",
                    "Shuriken Fan", "Throws 3 fast shurikens.",
                    "Shadow Step", "Teleports ahead and turns invisible.",
                    0xff4a4a66, 0xff1c1c2a, 0xffff3a6a,
                    3300, 560, 540, 1.15f, 1.12f, 2800, 420,
                    2, 4, 3, 5, 4, 2000, 1600),
            new Brawler(COBRA, "COBRA", "Gunslinger",
                    "Twin Revolvers", "Fires 4 quick bullets in a row.",
                    "Bullet Storm", "Two rings of bullets blast out in every direction.",
                    0xffe0b04a, 0xff8a5a24, 0xff3a2418,
                    3400, 360, 620, 1.35f, 1.05f, 3000, 520,
                    3, 3, 4, 4, 1, 900, 1700),
            new Brawler(THORN, "THORN", "Spiker",
                    "Cactus Spike", "A spike that bursts into 6 needles where it lands.",
                    "Spike Seed", "Lobs a seed that explodes into 12 needles.",
                    0xff7ad85a, 0xff2e7a34, 0xffff7ab0,
                    3300, 760, 640, 1.4f, 1.0f, 3000, 640,
                    3, 4, 4, 3, 2, 1300, 1150),
            new Brawler(RUMBLE, "RUMBLE", "Bruiser",
                    "Shock Punch", "A wide shockwave punch that knocks snakes back.",
                    "Earthquake", "Slams the ground: big damage and slows everyone close.",
                    0xffc8763e, 0xff5e3018, 0xffff3a3a,
                    6200, 860, 320, 1.25f, 1.06f, 2900, 380,
                    5, 4, 1, 4, 3, 1600, 950),
            new Brawler(NOVA, "NOVA", "Star Mage",
                    "Star Orb", "A magic orb that curves toward enemies.",
                    "Meteor Rain", "5 meteors crash down around the target.",
                    0xff9a7aff, 0xff35208a, 0xffffe066,
                    3300, 700, 720, 1.35f, 1.0f, 3000, 700,
                    2, 4, 5, 3, 4, 2500, 950),
            new Brawler(JOKER, "JOKER", "Maniac",
                    "Wild Cards", "3 crazy cards: some bounce, some freeze, some hit extra hard!",
                    "Jack-in-the-Box", "A spiral of 18 bouncing cards in every direction. Total chaos!",
                    0xffff2e8a, 0xff7a1fd6, 0xffffe14a,
                    3400, 540, 650, 1.2f, 1.08f, 2900, 720,
                    3, 4, 4, 4, 4, 3000, 1500),
            new Brawler(REAPER, "REAPER", "Soul Eater",
                    "Scythe Slash", "Three slashes up close. Heals you for every hit!",
                    "Soul Harvest", "Pulls nearby snakes in and steals their life.",
                    0xff3a3a52, 0xff12121e, 0xff8affd8,
                    4600, 470, 380, 1.15f, 1.1f, 2900, 400,
                    4, 5, 1, 5, 4, 3500, 1100),
            new Brawler(MAGMA, "MAGMA", "Volcano",
                    "Lava Burst", "Lobs 3 lava blobs that leave burning ground.",
                    "Eruption", "Erupts! 9 lava rocks rain down all around.",
                    0xffff5a1a, 0xff3a1410, 0xffffd23f,
                    4200, 520, 600, 1.75f, 0.98f, 3100, 470,
                    4, 4, 4, 2, 4, 4000, 0),
            new Brawler(GLITCH, "GLITCH", "Hacker",
                    "Data Beam", "Two laser beams that pass through walls.",
                    "System Crash", "Teleports to the target and explodes on both ends.",
                    0xff2affd0, 0xffff2aa8, 0xffffffff,
                    3100, 430, 780, 1.45f, 1.12f, 3000, 560,
                    2, 4, 5, 5, 4, 5000, 2300),
    };

    /** Colors used for bot snakes so each one is easy to tell apart. */
    static final int[][] BOT_SKINS = {
            {0xffb35cff, 0xff6a2bd1}, {0xffffe14a, 0xffe0a21a}, {0xff27e0d0, 0xff12908a},
            {0xffff6fb5, 0xffc2337a}, {0xff9be35a, 0xff4f9a26}, {0xff6f8cff, 0xff3346c4},
            {0xffff7d4a, 0xffc4421a}, {0xfff2f2f2, 0xff9aa3b5}, {0xff4ae08a, 0xff1a9a5a},
            {0xffffb0f0, 0xffd06ad0}, {0xffc9a26b, 0xff7a5a32}, {0xff59d7ff, 0xff2b86c4},
    };

    static final String[] BOT_NAMES = {
            "Slinky", "NoodleKing", "Hissy", "Mamba", "Zigzag", "Coily", "Fangtastic",
            "Sir Slither", "Wormzilla", "Boa Boss", "Kazoo", "Sssam", "Rattler", "Python",
            "Twisty", "Noodlina", "Spaghetti", "Cobra Kid", "Danger Noodle", "Snek",
            "Viperella", "Medusa", "Slippy", "Bitey", "Coilin", "Scales", "Venom",
            "Hiss Lord", "Lil Fang", "Tanglez",
    };
}
