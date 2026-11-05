#!/bin/sh
set -e

# Ensure report directory exists and is writable by appuser
if [ -d "/app/reports" ]; then
    chown -R appuser:appgroup /app/reports 2>/dev/null || true
    chmod -R 777 /app/reports 2>/dev/null || true
else
    mkdir -p /app/reports /app/reports/karate-features
    chown -R appuser:appgroup /app/reports 2>/dev/null || true
    chmod -R 777 /app/reports 2>/dev/null || true
fi

# Execute application as appuser if running as root, otherwise exec directly
if [ "$(id -u)" = '0' ]; then
    exec su-exec appuser java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher "$@"
else
    exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher "$@"
fi
