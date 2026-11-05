# ==============================================================================
# Stage 1: Build Custom Minimal JRE & Extract Spring Boot Layers
# ==============================================================================
FROM maven:3.9.11-eclipse-temurin-25-alpine AS builder

WORKDIR /build

# Build minimal custom JRE via jlink (shrinks JRE footprint from 225MB to ~55MB)
RUN $JAVA_HOME/bin/jlink \
    --add-modules java.base,java.compiler,java.desktop,java.instrument,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.transaction.xa,java.xml,java.xml.crypto,jdk.unsupported,jdk.crypto.ec,jdk.management,jdk.zipfs \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=zip-6 \
    --output /custom-jre

# Cache maven dependencies via BuildKit cache mount
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn dependency:go-offline -B

# Compile and package Spring Boot application
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests -B

# Extract Spring Boot layers (dependencies, loader, snapshot-dependencies, application)
WORKDIR /build/extracted
RUN java -Djarmode=tools -jar /build/target/e2e-orchestrator-platform-1.0.0-SNAPSHOT.jar extract --layers dependencies,spring-boot-loader,snapshot-dependencies,application --launcher --destination .

# ==============================================================================
# Stage 2: Ultra-Minimal Hardened Production Runtime
# ==============================================================================
FROM alpine:latest

# Install runtime dependencies: musl-locales, curl (for healthchecks), tzdata, ca-certificates
RUN apk add --no-cache curl tzdata musl-locales ca-certificates

WORKDIR /app

# Create unprivileged system user and group (least privilege)
RUN addgroup -S -g 10001 appgroup && \
    adduser -S -u 10001 -G appgroup -s /sbin/nologin -D -h /app appuser

# Copy custom lightweight JRE from builder (55MB vs 225MB standard Temurin JRE)
COPY --from=builder /custom-jre /opt/jre
ENV JAVA_HOME="/opt/jre"
ENV PATH="/opt/jre/bin:$PATH"

# Prepare writable report storage directory with least privilege
RUN mkdir -p /app/reports && chown -R appuser:appgroup /app

# Copy extracted Spring Boot layers in order of change frequency
# Layers that rarely change (dependencies) are cached indefinitely by Docker
COPY --from=builder --chown=appuser:appgroup /build/extracted/dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=appuser:appgroup /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /build/extracted/application/ ./

USER appuser

EXPOSE 8080

# Production-tuned JVM flags for Java 25 + Virtual Threads + Container Environments
# - Generational ZGC for sub-millisecond GC pauses with high throughput
# - Automatic container RAM scaling (75% max, 25% initial)
# - String deduplication for lower memory footprint
# - ExitOnOutOfMemoryError for rapid orchestrator self-healing
ENV JAVA_OPTS="-XX:+UseZGC \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=25.0 \
    -XX:+UseStringDeduplication \
    -XX:+ExitOnOutOfMemoryError \
    --enable-native-access=ALL-UNNAMED"

ENV ORCHESTRATOR_REPORTS_PATH="/app/reports"
ENV SPRING_PROFILES_ACTIVE="default"

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
