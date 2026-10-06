#!/usr/bin/env bash
# Builds Snake Brawl into a signed APK and a signed AAB without Gradle.
#
# Tools: aapt2, d8 (or dx), zipalign, apksigner, an android.jar, JDK (javac/jarsigner/keytool)
# and bundletool. On Ubuntu/Debian everything except bundletool comes from:
#   sudo apt install android-sdk-build-tools android-sdk-platform-23 apksigner zipalign dalvik-exchange
# bundletool is downloaded from GitHub on first use.
#
# Signing: set KEYSTORE, KEYSTORE_PASS and KEY_ALIAS to use your own upload key. If the keystore
# does not exist a new one is generated (keep it safe - Google Play needs the same key for updates).
set -euo pipefail
cd "$(dirname "$0")"

VERSION_CODE=${VERSION_CODE:-9}
VERSION_NAME=${VERSION_NAME:-1.8}
MIN_SDK=24
TARGET_SDK=36

SDK=${ANDROID_SDK:-/usr/lib/android-sdk}
PLATFORM_JAR=${PLATFORM_JAR:-$(ls -d "$SDK"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1)}
BT=${BUILD_TOOLS:-$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)}
BUNDLETOOL_VERSION=1.18.1
BUNDLETOOL=${BUNDLETOOL:-.cache/bundletool-all-$BUNDLETOOL_VERSION.jar}

KEYSTORE=${KEYSTORE:-keystore/snakebrawl-upload.jks}
KEY_ALIAS=${KEY_ALIAS:-snakebrawl}
KEYSTORE_PASS=${KEYSTORE_PASS:-}

OUT=build
DIST=dist

need() { command -v "$1" >/dev/null 2>&1 || [ -x "$1" ] || { echo "missing tool: $1" >&2; exit 1; }; }
need "$BT/aapt2"
need "$BT/zipalign"
need javac
need jarsigner
[ -f "$PLATFORM_JAR" ] || { echo "android.jar not found (set PLATFORM_JAR)" >&2; exit 1; }

APKSIGNER=$(command -v apksigner || echo "$BT/apksigner")
need "$APKSIGNER"

if [ ! -f "$BUNDLETOOL" ]; then
    mkdir -p "$(dirname "$BUNDLETOOL")"
    echo "Downloading bundletool $BUNDLETOOL_VERSION"
    curl -fsSL -o "$BUNDLETOOL" "https://github.com/google/bundletool/releases/download/$BUNDLETOOL_VERSION/bundletool-all-$BUNDLETOOL_VERSION.jar"
fi

if [ ! -f "$KEYSTORE" ]; then
    mkdir -p "$(dirname "$KEYSTORE")"
    if [ -z "$KEYSTORE_PASS" ]; then
        KEYSTORE_PASS=$(head -c 18 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 20)
    fi
    echo "Generating new upload key: $KEYSTORE"
    keytool -genkeypair -keystore "$KEYSTORE" -storetype PKCS12 -alias "$KEY_ALIAS" -keyalg RSA -keysize 2048 \
        -validity 10000 -storepass "$KEYSTORE_PASS" -keypass "$KEYSTORE_PASS" \
        -dname "CN=Snake Brawl, OU=Games, O=Kaki" >/dev/null 2>&1
    printf 'keystore=%s\nalias=%s\npassword=%s\n' "$KEYSTORE" "$KEY_ALIAS" "$KEYSTORE_PASS" > "$KEYSTORE.properties"
    echo "Key password saved to $KEYSTORE.properties"
elif [ -z "$KEYSTORE_PASS" ] && [ -f "$KEYSTORE.properties" ]; then
    KEYSTORE_PASS=$(sed -n 's/^password=//p' "$KEYSTORE.properties")
fi
[ -n "$KEYSTORE_PASS" ] || { echo "set KEYSTORE_PASS for $KEYSTORE" >&2; exit 1; }

echo "android.jar: $PLATFORM_JAR"
echo "build-tools: $BT"
rm -rf "$OUT"
mkdir -p "$OUT"/res "$OUT"/gen "$OUT"/classes "$OUT"/dex "$OUT"/apk "$OUT"/aab/base "$DIST"

LINK_FLAGS=(-I "$PLATFORM_JAR" --manifest app/src/main/AndroidManifest.xml -A app/src/main/assets
    --min-sdk-version "$MIN_SDK" --target-sdk-version "$TARGET_SDK"
    --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" -0 wav --auto-add-overlay)

echo "[1/6] Compiling resources"
"$BT/aapt2" compile --dir app/src/main/res -o "$OUT/res/compiled.zip"

echo "[2/6] Linking resources"
"$BT/aapt2" link -o "$OUT/apk/base.apk" --java "$OUT/gen" "${LINK_FLAGS[@]}" "$OUT/res/compiled.zip"
"$BT/aapt2" link --proto-format -o "$OUT/aab/proto.apk" "${LINK_FLAGS[@]}" "$OUT/res/compiled.zip"

echo "[3/6] Compiling Java"
find app/src/main/java "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 -bootclasspath "$PLATFORM_JAR" \
    -d "$OUT/classes" @"$OUT/sources.txt"

echo "[4/6] Dexing"
if command -v d8 >/dev/null 2>&1; then
    d8 --release --min-api "$MIN_SDK" --lib "$PLATFORM_JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
elif [ -x "$BT/d8" ]; then
    "$BT/d8" --release --min-api "$MIN_SDK" --lib "$PLATFORM_JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
else
    "$BT/dx" --dex --min-sdk-version="$MIN_SDK" --output="$OUT/dex/classes.dex" "$OUT/classes"
fi

echo "[5/6] Packaging APK"
cp "$OUT/apk/base.apk" "$OUT/apk/unsigned.apk"
(cd "$OUT/dex" && zip -q -X ../apk/unsigned.apk classes.dex)
"$BT/zipalign" -p -f 4 "$OUT/apk/unsigned.apk" "$OUT/apk/aligned.apk"
"$APKSIGNER" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" --ks-pass "pass:$KEYSTORE_PASS" \
    --key-pass "pass:$KEYSTORE_PASS" --v4-signing-enabled false --out "$DIST/SnakeBrawl-$VERSION_NAME.apk" "$OUT/apk/aligned.apk"
"$APKSIGNER" verify "$DIST/SnakeBrawl-$VERSION_NAME.apk"

echo "[6/6] Packaging AAB"
B="$OUT/aab/base"
(cd "$B" && unzip -q ../proto.apk)
mkdir -p "$B/manifest" "$B/dex"
mv "$B/AndroidManifest.xml" "$B/manifest/AndroidManifest.xml"
cp "$OUT/dex/classes.dex" "$B/dex/classes.dex"
(cd "$B" && zip -q -r -X ../base.zip manifest dex res assets resources.pb)
java -jar "$BUNDLETOOL" build-bundle --modules="$OUT/aab/base.zip" --output="$OUT/aab/unsigned.aab"
jarsigner -keystore "$KEYSTORE" -storepass "$KEYSTORE_PASS" -keypass "$KEYSTORE_PASS" \
    -sigalg SHA256withRSA -digestalg SHA-256 -signedjar "$DIST/SnakeBrawl-$VERSION_NAME.aab" \
    "$OUT/aab/unsigned.aab" "$KEY_ALIAS" >/dev/null
java -jar "$BUNDLETOOL" validate --bundle="$DIST/SnakeBrawl-$VERSION_NAME.aab" >/dev/null

echo
echo "Done:"
ls -la "$DIST"
