#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p ui-artifacts
trap 'adb pull /sdcard/Android/data/com.cj.pulse/files/qa ui-artifacts/ >/dev/null 2>&1 || true' EXIT
adb install -r dist/mytempo.apk
adb install -r build/ui-tests/tests.apk
adb shell pm grant com.cj.pulse android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
run_mode() {
  local mode="$1" size="$2" font="$3"
  adb shell am force-stop com.cj.pulse
  adb shell wm size "$size"
  adb shell wm density 320
  adb shell settings put system font_scale "$font"
  adb shell am instrument -w -e mode "$mode" com.cj.pulse.tests/com.cj.pulse.WindowSmokeTest | tee "ui-artifacts/$mode.txt"
  grep -q "PASS $mode:" "ui-artifacts/$mode.txt"
  ! grep -q 'FAIL\|INSTRUMENTATION_FAILED\|Process crashed' "ui-artifacts/$mode.txt"
}
run_mode portrait 1200x2000 1.0
run_mode landscape 2000x1200 1.0
run_mode popup 480x640 1.0
run_mode popup-font 480x640 1.4
run_mode pip 720x1280 1.0
adb shell settings put system font_scale 1.0
