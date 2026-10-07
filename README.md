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
| **Duo Showdown** | You and a clubmate against 4 other pairs. Teammates can't hurt each other, a knocked-out partner respawns after 5 s while their mate is alive, and the last pair standing wins. |

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
| **Cobra** | Gunslinger | 900 | Twin Revolvers: 4 quick bullets | Bullet Storm: two rings of bullets in every direction |
| **Thorn** | Spiker | 1300 | Cactus Spike: splits into 6 needles where it stops | Spike Seed: lobbed seed that bursts into 12 needles |
| **Rumble** | Bruiser | 1600 | Shock Punch: wide shockwave with big knockback | Earthquake: heavy damage and a long slow around him |
| **Nova** | Star Mage | 2500 | Star Orb: homes in on the nearest enemy | Meteor Rain: 5 meteors around the target |

Bots get smarter, stronger and fancier as your trophy count goes up.

### Coins, shop and upgrades

- Every match pays coins based on your rank, knockouts and length.
- **Shop: Offers** has a free gift every 4 hours, a Brawl Box (coins, a skin or a free upgrade),
  a Mega Box (a guaranteed new skin plus coins), and a daily skin deal at 40% off.
- **Shop: Skins** has 22 snake skins, from Lime and Tiger up to Rainbow, Gold, Diamond and the
  patterned Speedster (swept-back quills) and Sea Sponge (sponge holes). An equipped skin applies to
  every brawler.
- **Shop: Brawlers**: unlock the eight non-starter brawlers.
- **Brawlers screen**: pick a brawler and upgrade its power level (1 to 7, +6% health and damage per level).

### Snake Pass

The **SNAKE PASS** card on the main menu opens a 30-tier season pass. Seasons last 30 days.

- Every match earns pass XP (by rank and knockouts); 150 XP per tier.
- Each tier has a free reward and a **Snake Pass+** reward. Pass+ costs 1,200 coins (no real money).
- Rewards: coins, Brawl Boxes, Mega Boxes, the Sea Sponge skin (free tier 20), the Cobra brawler
  (Pass+ tier 15) and the Speedster skin (Pass+ tier 30). Owned items turn into coins.
- **CLAIM ALL** collects everything reached; a red badge on the menu card counts ready rewards.

### First launch, clubs and duo

- Start-up shows a RanEddie Games splash screen with a loading bar.
- On first launch the game asks for a **nickname** (2 to 14 characters, or tap RANDOM NAME) and an
  **age**. Players under 13 can't buy coins with real money.
- **Club**: join one of six clubs (some need trophies) or create your own with a name and badge.
  The club screen lists the members and their trophies.
- **Play Duo** from the club screen to team up with your club's duo partner. The partner card under
  the minimap shows their health and respawn timer.
- There is no online server yet, so club members and your duo partner are computer players.
  Playing with real friends needs an online backend or local Wi-Fi play.

### Play with friends (online and Wi-Fi, app and browser together)

**FRIENDS** on the main menu has two connection types (switch with the button at the top right in the app):

- **ONLINE** (default): the host gets a 5-character room code and the friend types it in, from
  anywhere. Works between the app and the browser version. The app runs the browser version's own
  WebRTC code (`web/static/sb-net.js` + PeerJS) in a hidden WebView (`WebNetLink`), so both use the
  same connection. PeerJS's free public server only introduces the two players.
- **WI-FI** (app only): rooms on the same Wi-Fi are found automatically; if not, type the address
  shown on the host's screen.
- One player taps **HOST A ROOM**, the other **JOIN A ROOM**.
- The host picks **TOGETHER** (team up against 4 bot pairs) or **VERSUS** (fight each other plus 8 bots).
- Both phones run the same match in lockstep: they share a random seed, exchange only their controls
  (60 small packets a second) and compare a checksum every 2 seconds. If a phone disconnects or the
  games get out of sync, the friend's snake is taken over by a bot.
- Wi-Fi ports: TCP 47321 (game), UDP 47322 (room announcements).
- Cross-play needs bit-identical simulation: the game uses its own random generator (`Rng`) and
  math (`MathUtil`), and the browser runs a WebAssembly build whose 32-bit float math matches Java's.
  Browsers without WebAssembly GC (older than Safari 18.2 / Chrome 119) fall back to a JavaScript
  build, which can only play with other such browsers; the lobby explains this when it happens.
- `SimTest replay <seed> <ticks>` and `?replay=<seed>` in the browser print state hashes that must match.
- Test: `java -cp /tmp/sb com.snakebrawl.myapp.game.SimTest net 0 60` runs two games over localhost
  and checks they stay identical every tick.

### Privacy and safety for younger players

- First launch asks for the **age first**. Players under 13 pick a generated nickname (no free typing).
- **Online play is for ages 16+** (WebRTC lets the friend and the connection server see IP addresses);
  younger players can still use Wi-Fi play with a friend nearby.
- Nothing is sent to a server we run; there are no ads, analytics or accounts. Settings has a
  **PRIVACY** button with a short notice; the full policy is `web/static/privacy.html`
  (published with the browser build at `/privacy.html`).

### Coin store (real money)

The shop's **COINS** tab sells five coin packs (500 to 15,000 coins, $0.99 to $14.99).
Product ids: `coins_500`, `coins_1200`, `coins_2800`, `coins_6500`, `coins_15000`.

Google Play Billing is **not integrated yet**, so the store is **switched off** in release builds
(`Game.coinStoreEnabled = false`: no COINS tab). Play policy requires Play Billing for digital goods.
To go live: add the Play Billing library, create the products above in the Play Console, start the
purchase flow in `MainActivity.launchPurchase`, call `game.onPurchaseResult(productId, true)` after the
purchase is verified and consumed, then set `coinStoreEnabled = true`. The simulated checkout is
only used by the desktop tests.


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

- `dist/SnakeBrawl-3.1.apk`: install it directly on a phone.
- `dist/SnakeBrawl-3.1.aab`: the Android App Bundle for Google Play.

Both are signed with an upload key. If `keystore/snakebrawl-upload.jks` does not exist, a new key
is generated and its password is written next to it. **Keep that key safe and never commit it.**
Google Play needs the same key for every update. To use your own key:

```bash
KEYSTORE=/path/to/upload.jks KEY_ALIAS=myalias KEYSTORE_PASS=secret VERSION_CODE=14 VERSION_NAME=2.3 ./build.sh
```

The app targets API 36 and runs on Android 7.0 (API 24) and newer. It asks only for network permissions (granted automatically), used for playing with friends.

## Browser version (iPhone, iPad and any phone)

`web/build.sh` compiles the same game code to WebAssembly and JavaScript with [TeaVM](https://teavm.org) and writes a
static site to `dist/web` (plus `dist/SnakeBrawl-web-<version>.zip`). Host the folder on any static
web host (GitHub Pages, Netlify, ...), open it in Safari and choose **Share > Add to Home Screen** to
get a full-screen app icon. After the first visit it also works offline.

Everything works, including online play with friends in the app or another browser (WebRTC through
[PeerJS](https://peerjs.com), bundled in `web/static/vendor`). Some mobile networks block direct
connections, in which case joining fails with an error; Wi-Fi usually works.
Progress is saved in the browser's local storage. Needs JDK 11+ and Maven.

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
