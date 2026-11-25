#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "========================================================================"
echo " Building MeterFlow Standalone (Zero-Docker / Bundled UI & H2 DB)"
echo "========================================================================"

echo ""
echo "[1/2] Compiling React Visual Canvas UI (frontend)..."
cd frontend
npm run build

echo ""
echo "[2/2] Packaging Standalone Executable JAR (standalone)..."
cd ../standalone
mvn clean package -DskipTests

echo ""
echo "========================================================================"
echo " BUILD SUCCESSFUL!"
echo " Output JAR: standalone/target/meterflow-standalone.jar"
echo " Run via:    ./run-standalone.sh"
echo "========================================================================"
