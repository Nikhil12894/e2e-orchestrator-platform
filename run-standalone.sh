#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

JAR_PATH="$SCRIPT_DIR/standalone/target/meterflow-standalone.jar"

if [ ! -f "$JAR_PATH" ]; then
    echo "Executable JAR not found at: $JAR_PATH"
    echo "Running build first..."
    ./build-standalone.sh
fi

echo "========================================================================"
echo " Starting MeterFlow Standalone..."
echo " Web UI:     http://localhost:8080"
echo " Database:   ./data/meterflow.mv.db"
echo " Memory cap: 256MB RAM"
echo " Press Ctrl+C to stop"
echo "========================================================================"

mkdir -p "$SCRIPT_DIR/data"
exec java -Xmx256m --enable-native-access=ALL-UNNAMED -jar "$JAR_PATH" "$@"
