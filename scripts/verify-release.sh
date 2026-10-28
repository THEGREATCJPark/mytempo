#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
sha256sum -c downloads/SHA256SUMS.txt
sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-../toolchain/android-sdk}}"
bt="$sdk/build-tools/35.0.0"
expected=eee82f46ef11efd5a22d2cd12bbb3b3990b4b562c65e9c8f21e58d01bb14916b
actual="$("$bt/apksigner" verify --print-certs downloads/mytempo.apk | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')"
[[ "$actual" == "$expected" ]] || { echo 'Official APK has the wrong signing certificate.' >&2; exit 1; }
version="$(python3 -c 'import xml.etree.ElementTree as E; print(E.parse("app/src/main/AndroidManifest.xml").getroot().attrib["{http://schemas.android.com/apk/res/android}versionName"])')"
version_code="$(python3 -c 'import xml.etree.ElementTree as E; print(E.parse("app/src/main/AndroidManifest.xml").getroot().attrib["{http://schemas.android.com/apk/res/android}versionCode"])')"
badging="$("$bt/aapt2" dump badging downloads/mytempo.apk)"
[[ "$badging" == *"name='com.cj.pulse' versionCode='$version_code' versionName='$version'"* ]] || { echo 'APK version does not match source manifest.' >&2; exit 1; }
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || exit 1
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then echo "tag=v$version" >> "$GITHUB_OUTPUT"; fi
echo "Verified official APK v$version"
