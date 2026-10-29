#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-../toolchain/android-sdk}}"
bt="$sdk/build-tools/35.0.0"
jar="$sdk/platforms/android-35/android.jar"
out="$PWD/build/ui-tests"
mkdir -p "$out/classes" "$out/dex"
"$bt/aapt2" link -I "$jar" --manifest tests/instrumentation/AndroidManifest.xml -o "$out/base.apk"
java --module jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 --release 8 -classpath "$jar:build/classes" -d "$out/classes" tests/instrumentation/WindowSmokeTest.java
java --module jdk.jartool/sun.tools.jar.Main cf "$out/classes.jar" -C "$out/classes" .
"$bt/d8" --release --min-api 26 --lib "$jar" --classpath build/classes.jar --output "$out/dex" "$out/classes.jar"
cp "$out/base.apk" "$out/unsigned.apk"
(cd "$out/dex" && zip -q -j "$out/unsigned.apk" classes*.dex)
"$bt/zipalign" -f 4 "$out/unsigned.apk" "$out/aligned.apk"
"$bt/apksigner" sign --ks signing/pulse-release.jks --ks-key-alias pulse --ks-pass file:signing/.keystore-password --out "$out/tests.apk" "$out/aligned.apk"
