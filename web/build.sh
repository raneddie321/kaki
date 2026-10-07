#!/usr/bin/env bash
# Builds the browser version of Snake Brawl into dist/web (and dist/SnakeBrawl-web-<version>.zip).
# Needs a JDK 11+ and Maven; TeaVM is fetched from Maven Central.
set -euo pipefail
cd "$(dirname "$0")"
VERSION_NAME=${VERSION_NAME:-3.1}
ROOT=..
T=target
rm -rf "$T/src" "$T/js" "$T/wasm"
mkdir -p "$T/src/com"
# Shared game code, minus the socket-based Wi-Fi session (browsers cannot open sockets)
cp -r "$ROOT/app/src/main/java/com/snakebrawl" "$T/src/com/"
rm -f "$T/src/com/snakebrawl/myapp/"*.java "$T/src/com/snakebrawl/myapp/game/LanSession.java" "$T/src/com/snakebrawl/myapp/game/Lan.java"
cp -r src/com "$T/src/"
mvn -q -B package -DskipTests

OUT="$ROOT/dist/web"
rm -rf "$OUT"
mkdir -p "$OUT/sounds"
cp -r "$T/js/classes.js" "$T/wasm/classes.wasm" static/index.html static/sb.js static/sb-net.js static/wasm-runtime.js static/privacy.html static/vendor \
    static/manifest.json static/sw.js "$OUT/"
sed -i "s/snakebrawl-v[0-9.]*/snakebrawl-v$VERSION_NAME/" "$OUT/sw.js"
cp "$ROOT/app/src/main/assets/fonts/LilitaOne-Regular.ttf" "$ROOT/app/src/main/assets/fonts/OFL.txt" "$OUT/"
cp "$ROOT"/app/src/main/res/raw/*.wav "$OUT/sounds/"
python3 - "$ROOT/app/src/main/res/mipmap-xxxhdpi" "$OUT" <<'PY'
import sys
from PIL import Image
src, out = sys.argv[1], sys.argv[2]
bg = Image.open(src + '/ic_launcher_background.png').convert('RGBA')
fg = Image.open(src + '/ic_launcher_foreground.png').convert('RGBA')
icon = Image.alpha_composite(bg, fg)
for s in (512, 192, 180):
    icon.resize((s, s), Image.LANCZOS).save('%s/icon-%d.png' % (out, s))
PY
(cd "$ROOT/dist" && rm -f "SnakeBrawl-web-$VERSION_NAME.zip" && zip -q -r "SnakeBrawl-web-$VERSION_NAME.zip" web)
echo "Web build: $OUT ($(du -sh "$OUT" | cut -f1))"
