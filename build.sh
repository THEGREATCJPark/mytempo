#!/usr/bin/env bash
set -euo pipefail

# Dependency-free native Android build. Requires JDK 17+, Android SDK API 35,
# build-tools 35.0.0, zip, and Python 3. No Gradle or network access at build time.
PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$PROJECT_DIR/../toolchain/android-sdk}}"
BUILD_TOOLS="$SDK_DIR/build-tools/35.0.0"
ANDROID_JAR="$SDK_DIR/platforms/android-35/android.jar"
MAIN_DIR="$PROJECT_DIR/app/src/main"
BUILD_DIR="$PROJECT_DIR/build"
OUT_DIR="$PROJECT_DIR/dist"
SIGN_DIR="$PROJECT_DIR/signing"
APK_NAME="Pulse-Metronome-1.2.apk"

if [[ ! -f "$ANDROID_JAR" || ! -x "$BUILD_TOOLS/aapt2" ]]; then
  echo 'Android SDK missing. Set ANDROID_SDK_ROOT with platforms;android-35 and build-tools;35.0.0 installed.' >&2
  exit 1
fi

rm -rf -- "$BUILD_DIR"
mkdir -p "$BUILD_DIR/compiled" "$BUILD_DIR/generated" "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$OUT_DIR" "$SIGN_DIR"

"$BUILD_TOOLS/aapt2" compile --dir "$MAIN_DIR/res" -o "$BUILD_DIR/resources.zip"
"$BUILD_TOOLS/aapt2" link \
  -o "$BUILD_DIR/base.apk" \
  --manifest "$MAIN_DIR/AndroidManifest.xml" \
  -I "$ANDROID_JAR" \
  --java "$BUILD_DIR/generated" \
  --auto-add-overlay \
  --min-sdk-version 26 --target-sdk-version 35 \
  "$BUILD_DIR/resources.zip"

find "$MAIN_DIR/java" "$BUILD_DIR/generated" -type f -name '*.java' > "$BUILD_DIR/sources.txt"
java --module jdk.compiler/com.sun.tools.javac.Main \
  -encoding UTF-8 --release 8 -classpath "$ANDROID_JAR" \
  -d "$BUILD_DIR/classes" @"$BUILD_DIR/sources.txt"
java --module jdk.jartool/sun.tools.jar.Main cf "$BUILD_DIR/classes.jar" -C "$BUILD_DIR/classes" .
"$BUILD_TOOLS/d8" --release --min-api 26 --lib "$ANDROID_JAR" --output "$BUILD_DIR/dex" "$BUILD_DIR/classes.jar"
cp "$BUILD_DIR/base.apk" "$BUILD_DIR/unsigned.apk"
(cd "$BUILD_DIR/dex" && zip -q -j "$BUILD_DIR/unsigned.apk" classes*.dex)
"$BUILD_TOOLS/zipalign" -P 16 -f 4 "$BUILD_DIR/unsigned.apk" "$BUILD_DIR/aligned.apk"

if [[ ! -f "$SIGN_DIR/pulse-release.jks" ]]; then
  umask 077
  python3 -c 'import secrets; print(secrets.token_urlsafe(32))' > "$SIGN_DIR/.keystore-password"
  keytool -genkeypair -keystore "$SIGN_DIR/pulse-release.jks" \
    -storepass:file "$SIGN_DIR/.keystore-password" -keypass:file "$SIGN_DIR/.keystore-password" \
    -alias pulse -keyalg RSA -keysize 2048 -validity 10000 \
    -dname 'CN=Pulse Metronome, OU=Personal App, O=CJ, C=KR' \
    -storetype JKS -noprompt
fi
"$BUILD_TOOLS/apksigner" sign \
  --ks "$SIGN_DIR/pulse-release.jks" \
  --ks-key-alias pulse \
  --ks-pass "file:$SIGN_DIR/.keystore-password" \
  --out "$OUT_DIR/$APK_NAME" "$BUILD_DIR/aligned.apk"
"$BUILD_TOOLS/apksigner" verify --verbose "$OUT_DIR/$APK_NAME"
"$BUILD_TOOLS/zipalign" -c -P 16 4 "$OUT_DIR/$APK_NAME"
sha256sum "$OUT_DIR/$APK_NAME" > "$OUT_DIR/$APK_NAME.sha256"
echo "APK ready: $OUT_DIR/$APK_NAME"
