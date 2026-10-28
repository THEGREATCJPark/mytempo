#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/jazz-test
java --module jdk.compiler/com.sun.tools.javac.Main --release 8 -encoding UTF-8 -d build/jazz-test app/src/main/java/com/cj/pulse/JazzReference.java tests/jazz/JazzReferenceTest.java
java -cp build/jazz-test JazzReferenceTest
