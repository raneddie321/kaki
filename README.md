# Snake Brawl 🐍💥

A snake.io-style arena game crossed with Brawl Stars, for Android.

Grow your snake by eating orbs like in slither.io. You also carry a weapon: shoot other snakes,
charge a **Super**, hide in bushes, break power-cube boxes, and be the last snake standing while the
poison closes in.

## Gameplay

- **Steer**: drag anywhere on the left half of the screen (floating joystick).
- **Attack**: a lock-on reticle always tracks the nearest visible enemy. On the right stick, *tap* to
  fire at the locked target or *drag* to aim manually, then release.
  Drag back to the centre to cancel. You have 3 ammo bars that reload over time.
- **Super**: hitting snakes charges the star button. When it glows, tap or drag it to unleash.
- **Boost**: hold the blue button to sprint. Boosting burns length and leaves food behind.
- **Ways to knock out a snake**
  - Shoot it until its health runs out.
  - Make it crash its head into your body (classic snake.io).
  - Head-on collisions: the smaller snake loses.
- Knocked-out snakes burst into big orbs and drop their power cubes.
- **Power cube boxes**: shoot the crates to get power cubes (+10% health and damage each).
- **Bushes**: snakes inside bushes are invisible to enemies unless they come close or attack.
- **Regeneration**: health refills after 3 seconds without attacking or taking damage.
- **Combat extras**: head shots crit for +25%, chained knockouts trigger DOUBLE / TRIPLE KNOCKOUT
  banners, and bots sidestep incoming shots.

### Modes

| Mode | Description |
| --- | --- |
| **Showdown** | 10 snakes, no respawns. Poison starts closing in after 25 s. Last snake standing wins. Earn trophies by rank. |
| **Endless** | Classic io mode on a bigger map. Bots respawn. Grow as big as you can. |

### Brawlers

| Brawler | Role | Price | Attack | Super |
| --- | --- | --- | --- | --- |
| **Viper** | Shotgunner | free | Fang Spray: 5 venom pellets | Nova Blast: huge pellet wave with knockback |
| **Volt** | Sniper | free | Spark Bolt: long-range lightning bolt | Rail Storm: 8 bolts that fly through walls |
| **Boomer** | Thrower | free | Fuse Bomb: lobbed over walls, splash damage | Mega Bomb: giant blast |
| **Blaze** | Tank | free | Flame Breath: short-range fire cone | Rampage: dash that slices through snake bodies |
| **Frost** | Controller | 500 | Ice Shards: 3 shards that slow snakes | Blizzard: freezing blast around you |
| **Ziggy** | Ricochet | 800 | Bouncy Balls: 3 balls that bounce off walls | Pinball Party: 12 balls in every direction |
| **Toxin** | Poisoner | 1200 | Venom Glob: leaves a poison puddle | Toxic Cloud: huge toxic swamp |
| **Shade** | Assassin | 2000 | Shuriken Fan: 3 fast shurikens | Shadow Step: teleport and turn invisible |

Bots get smarter, stronger and fancier as your trophy count goes up.

### Coins, shop and upgrades

- Every match pays coins based on your rank, knockouts and length.
- **Shop: Offers** has a free gift every 4 hours, a Brawl Box (coins, a skin or a free upgrade),
  a Mega Box (a guaranteed new skin plus coins), and a daily skin deal at 40% off.
- **Shop: Skins** has 20 snake skins, from Lime and Tiger up to Rainbow, Gold and Diamond.
  An equipped skin applies to every brawler.
- **Shop: Brawlers**: unlock the four new brawlers.
- **Brawlers screen**: pick a brawler and upgrade its power level (1 to 7, +6% health and damage per level).

### Coin store (real money)

The shop's **COINS** tab sells five coin packs (500 to 15,000 coins, $0.99 to $14.99).
Product ids: `coins_500`, `coins_1200`, `coins_2800`, `coins_6500`, `coins_15000`.

Google Play Billing is **not integrated yet**: `MainActivity.launchPurchase` returns false, so the
game shows a simulated test checkout (clearly marked TEST MODE, no money is charged). To go live:
add the Play Billing library, create the products above in the Play Console, start the purchase
flow in `launchPurchase`, and call `game.onPurchaseResult(productId, true)` after the purchase is
verified and consumed. Remove the test checkout before publishing.


### Settings

Sound, vibration, damage numbers, graphics quality (high/low), auto-aim on tap, left-handed controls,
joystick size, camera distance, and resetting your progress.

## Project layout

```
app/src/main/java/com/snakebrawl/myapp/        Android host (Activity, View, Canvas adapter)
app/src/main/java/com/snakebrawl/myapp/game/   The game itself, plain Java with no Android imports
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

- `dist/SnakeBrawl-1.8.apk`: install it directly on a phone.
- `dist/SnakeBrawl-1.8.aab`: the Android App Bundle for Google Play.

Both are signed with an upload key. If `keystore/snakebrawl-upload.jks` does not exist, a new key
is generated and its password is written next to it. **Keep that key safe and never commit it.**
Google Play needs the same key for every update. To use your own key:

```bash
KEYSTORE=/path/to/upload.jks KEY_ALIAS=myalias KEYSTORE_PASS=secret VERSION_CODE=10 VERSION_NAME=1.9 ./build.sh
```

The app targets API 36 and runs on Android 7.0 (API 24) and newer. It needs no permissions.

## Desktop test harness

```bash
mkdir -p /tmp/sb && javac -d /tmp/sb app/src/main/java/com/snakebrawl/myapp/game/*.java desktop/src/com/snakebrawl/myapp/*/*.java
java -cp /tmp/sb com.snakebrawl.myapp.game.SimTest balance 20         # bot-only matches: stats and timings
java -cp /tmp/sb com.snakebrawl.myapp.game.SimTest play 8             # scripted player through the touch API
java -cp /tmp/sb com.snakebrawl.myapp.game.SimTest stress 1280 576 4  # render every frame with random input
java -cp /tmp/sb com.snakebrawl.myapp.game.SimTest shots out 2400 1080  # screenshots of every screen
java -cp /tmp/sb com.snakebrawl.myapp.game.IconGen app/src/main/res store-icon-512.png
```

## Credits

Font: [Lilita One](https://fonts.google.com/specimen/Lilita+One) by Juan Montoreano, SIL Open Font
License 1.1 (see `app/src/main/assets/fonts/OFL.txt`). All other art and sounds are generated in code.
