#!/bin/sh
# Compiles the game and its JUnit tests, then runs every test.
cd "$(dirname "$0")" || exit 1
mkdir -p out/test
# JUnit does not understand "lib/*", so list every engine jar by name.
ENGINE=$(ls lib/*.jar | tr '\n' ':')
javac -cp "lib/*:testlib/*" -d out/test $(find src test -name '*.java') \
    && java -Djava.awt.headless=true -jar testlib/junit-platform-console-standalone-1.10.2.jar \
        execute --class-path "out/test:src:$ENGINE" --scan-class-path out/test
