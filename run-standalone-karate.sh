#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

JAR_PATH="$SCRIPT_DIR/backend/target/meterflow-karate-standalone.jar"

if [ ! -f "$JAR_PATH" ]; then
    echo "Standalone Karate JAR not found at: $JAR_PATH"
    echo "Running build first..."
    ./build-standalone-karate.sh
fi

exec java --enable-native-access=ALL-UNNAMED -jar "$JAR_PATH" "$@"
