#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "========================================================================"
echo " Building MeterFlow Standalone Karate CLI Runner"
echo " (Karate Core 2.1.4 + DbHelper + KafkaHelper + Universal JDBC Drivers)"
echo "========================================================================"

echo ""
echo "Packaging standalone-karate JAR..."
cd backend
mvn clean package -Pstandalone-karate -DskipTests

echo ""
echo "========================================================================"
echo " BUILD SUCCESSFUL!"
echo " Output JAR: backend/target/meterflow-karate-standalone.jar"
echo " Run via:    ./run-standalone-karate.sh <feature-file> [options]"
echo " Example:    ./run-standalone-karate.sh path/to/workflow.feature"
echo "========================================================================"
