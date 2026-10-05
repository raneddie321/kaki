package com.kaki.snakebrawl.game;

/** Static description of a playable snake class. */
final class Brawler {
    static final int VIPER = 0;
    static final int VOLT = 1;
    static final int BOOMER = 2;
    static final int BLAZE = 3;

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

    private Brawler(int id, String name, String role, String attackName, String attackDesc,
                    String superName, String superDesc, int color1, int color2, int accent,
                    float hp, float damage, float range, float reload, float speed,
                    float superCost, float superRange,
                    int statHp, int statDamage, int statRange, int statSpeed) {
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
    }

    static final Brawler[] ALL = {
            new Brawler(VIPER, "VIPER", "Shotgunner",
                    "Fang Spray", "Blasts 5 venom pellets. Brutal up close.",
                    "Nova Blast", "A huge pellet wave that knocks snakes away.",
                    0xff3ddc5a, 0xff1f8f3a, 0xffffd23f,
                    3600, 315, 430, 1.45f, 1.0f, 3000, 560,
                    4, 4, 2, 3),
            new Brawler(VOLT, "VOLT", "Sniper",
                    "Spark Bolt", "Fires one fast long-range lightning bolt.",
                    "Rail Storm", "Unloads 8 bolts that fly through walls.",
                    0xff3fb6ff, 0xff1d5fd6, 0xfffff04a,
                    2800, 950, 800, 2.0f, 1.02f, 3300, 900,
                    2, 4, 5, 3),
            new Brawler(BOOMER, "BOOMER", "Thrower",
                    "Fuse Bomb", "Lobs a bomb over walls. Splash damage.",
                    "Mega Bomb", "A giant bomb with a massive blast.",
                    0xffff9a2e, 0xffd9461f, 0xff3a2a20,
                    3000, 1000, 620, 1.9f, 0.98f, 3200, 680,
                    3, 4, 4, 2),
            new Brawler(BLAZE, "BLAZE", "Tank",
                    "Flame Breath", "Spits a short-range cone of fire.",
                    "Rampage", "Dashes forward, slicing through snake bodies.",
                    0xffe0344a, 0xff6a1030, 0xfff2f2f2,
                    5600, 210, 330, 1.0f, 1.08f, 2800, 520,
                    5, 3, 1, 4),
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
            "Sir Slither", "Wormzilla", "Boa Boss", "Kaa", "Sssam", "Rattler", "Python",
            "Twisty", "Nagini", "Spaghetti", "Cobra Kai", "Danger Noodle", "Snek",
            "Viperella", "Medusa", "Slippy", "Bitey", "Coilin", "Scales", "Venom",
            "Hiss Lord", "Lil Fang", "Tanglez",
    };
}
