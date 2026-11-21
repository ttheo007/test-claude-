#!/usr/bin/env bash
# Kompiliert die reine Logik der Mod (Paket "core") samt Tests mit dem installierten JDK und fuehrt sie aus.
# Braucht weder Gradle noch Minecraft und laeuft ab JDK 21.
set -euo pipefail

cd "$(dirname "$0")/.."

out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT

javac --release 21 -Xlint:all -d "$out" \
	src/main/java/io/github/ttheo007/lowendboost/core/*.java \
	tools/CoreTests.java

java -cp "$out" CoreTests
