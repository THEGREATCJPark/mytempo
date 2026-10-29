#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/window-test
java --module jdk.compiler/com.sun.tools.javac.Main --release 8 -d build/window-test app/src/main/java/com/cj/pulse/WindowLayout.java tests/window/WindowLayoutTest.java
java -cp build/window-test WindowLayoutTest
