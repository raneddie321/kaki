# Snake Brawl 🐍💥

A hero-versus-snakes arena game for Android, mixing snake.io with Brawl Stars.

You play one hero on foot, armed with a blaster, against a pit of slither.io-style snakes. The
snakes grow by eating orbs, bite you, and fight each other. Shoot them, dodge their bites, break
power-cube crates, hide in bushes, and be the last one standing while the poison closes in.

## Gameplay

- **Move**: drag anywhere on the left half of the screen. The hero stops when you let go.
- **Attack (Blaster)**: on the right stick, *tap* to auto-aim or *drag* to aim, then release.
  Each shot is a 3-bolt burst. You have 3 ammo bars that reload over time.
- **Super (Rocket Rain)**: hitting snakes charges the star button. When it glows, aim it to call
  down 6 rockets.
- **Dash**: the blue button does a quick dodge that makes snake bites miss (2.5 s cooldown).
- **Snakes**: their heads bite you; bigger snakes bite harder. They can't run through you.
  Snakes also kill each other: a snake that crashes its head into another snake's body dies.
- **Power cubes** (from crates and knocked-out snakes): +10% health and damage each.
- **Bushes**: snakes inside bushes are hidden unless you get close.

### Modes

| Mode | Description |
| --- | --- |
| **Showdown** | 10 snakes, no respawns. Poison starts closing in after 25 s. Last snake standing wins. Earn trophies by rank. |
| **Endless** | Classic io mode on a bigger map. Bots respawn. Grow as big as you can. |

### Snakes

Bot snakes come in 8 types, each with its own weapon: Viper (shotgun), Volt (sniper),
Boomer (bombs), Blaze (flamethrower and dash), Frost (slowing ice), Ziggy (bouncing balls),
Toxin (poison puddles) and Shade (shurikens and invisibility). They get smarter and stronger as
your trophy count goes up.

### Coins, shop and upgrades

- Every match pays coins based on your rank, knockouts and score.
- **Shop**: a free gift every 4 hours, a Brawl Box (coins or a free upgrade), a Mega Box
  (a guaranteed upgrade plus coins), and a daily upgrade deal at 40% off.
- **Hero screen**: upgrade your power level from 1 to 7 (+6% health and damage per level).

## Hero model

`HeroArt` draws `assets/hero/hero_00.png` … `hero_15.png` if they exist: 16 directional sprites,
where frame *i* faces *i* × 22.5° clockwise from screen-right. Otherwise it draws a vector
placeholder. Render the sprites from a `.glb` model with:

```bash
cd tools/render_hero && npm install
CHROMIUM=/path/to/chrome node render.mjs /path/to/hero.glb        # add --yaw-offset=90 if it faces the wrong way
```

### Settings

Sound, vibration, damage numbers, graphics quality (high/low), auto-aim on tap, left-handed controls,
joystick size, camera distance, and resetting your progress.

## Project layout

```
app/src/main/java/com/kaki/snakebrawl/        Android host (Activity, View, Canvas adapter)
app/src/main/java/com/kaki/snakebrawl/game/   The game itself, plain Java with no Android imports
app/src/main/res, assets                       Icons, sounds, Lilita One font (SIL OFL)
desktop/src/                                   Desktop test harness: headless simulation and screenshots
tools/gen_sounds.py                            Regenerates the sound effects
build.sh                                       Builds the signed APK and AAB
```

All game logic and rendering goes through a small `Gfx` interface, so the same code runs on
Android (`android.graphics.Canvas`) and on the desktop (Java2D) for testing.

## Building

`build.sh` builds without Gradle, using `aapt2`, `javac`, `d8`/`dx`, `zipalign`, `apksigner` and
`bundletool`. On Ubuntu/Debian:

```bash
sudo apt install openjdk-21-jdk android-sdk-build-tools android-sdk-platform-23 apksigner zipalign dalvik-exchange
./build.sh
```

This produces:

- `dist/SnakeBrawl-1.2.apk`: install it directly on a phone.
- `dist/SnakeBrawl-1.2.aab`: the Android App Bundle for Google Play.

Both are signed with an upload key. If `keystore/snakebrawl-upload.jks` does not exist, a new key
is generated and its password is written next to it. **Keep that key safe and never commit it.**
Google Play needs the same key for every update. To use your own key:

```bash
KEYSTORE=/path/to/upload.jks KEY_ALIAS=myalias KEYSTORE_PASS=secret VERSION_CODE=4 VERSION_NAME=1.3 ./build.sh
```

The app targets API 35 and runs on Android 5.0 (API 21) and newer. It needs no permissions.

## Desktop test harness

```bash
mkdir -p /tmp/sb && javac -d /tmp/sb app/src/main/java/com/kaki/snakebrawl/game/*.java desktop/src/com/kaki/snakebrawl/*/*.java
java -cp /tmp/sb com.kaki.snakebrawl.game.SimTest balance 20         # bot-only matches: stats and timings
java -cp /tmp/sb com.kaki.snakebrawl.game.SimTest play 8             # scripted player through the touch API
java -cp /tmp/sb com.kaki.snakebrawl.game.SimTest stress 1280 576 4  # render every frame with random input
java -cp /tmp/sb com.kaki.snakebrawl.game.SimTest shots out 2400 1080  # screenshots of every screen
java -cp /tmp/sb com.kaki.snakebrawl.game.IconGen app/src/main/res store-icon-512.png
```

## Credits

Font: [Lilita One](https://fonts.google.com/specimen/Lilita+One) by Juan Montoreano, SIL Open Font
License 1.1 (see `app/src/main/assets/fonts/OFL.txt`). All other art and sounds are generated in code.
