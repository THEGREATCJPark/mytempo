#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$PROJECT_ROOT/build/audio-test"
java --module jdk.compiler/com.sun.tools.javac.Main --release 8 -d "$PROJECT_ROOT/build/audio-test" \
 "$PROJECT_ROOT"/tests/audio/android/os/Process.java \
 "$PROJECT_ROOT"/tests/audio/android/media/*.java \
 "$PROJECT_ROOT"/tests/audio/com/cj/pulse/*.java \
 "$PROJECT_ROOT/app/src/main/java/com/cj/pulse/AudioEngine.java" \
 "$PROJECT_ROOT/app/src/main/java/com/cj/pulse/Rhythm.java"
java -cp "$PROJECT_ROOT/build/audio-test" com.cj.pulse.AudioEngineTest
