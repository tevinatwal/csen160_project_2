#!/bin/sh
# Compiles and runs Still Asleep without an IDE.
cd "$(dirname "$0")" || exit 1
mkdir -p out/run
javac -cp "lib/*" -d out/run $(find src -name '*.java') && java -cp "out/run:src:lib/*" Main "$@"
