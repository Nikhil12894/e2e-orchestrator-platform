# Multi-stage Dockerfile for Spring Boot 4 + Java 25 Backend
FROM maven:3.9.9-eclipse-temurin-25-alpine AS builder

WORKDIR /build

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Runtime Image
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Create reports directory
RUN mkdir -p /app/reports && chown -R appuser:appgroup /app

COPY --from=builder /build/target/e2e-orchestrator-platform-1.0.0-SNAPSHOT.jar app.jar

USER appuser

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -XX:MaxRAMPercentage=75.0"
ENV ORCHESTRATOR_REPORTS_PATH="/app/reports"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
