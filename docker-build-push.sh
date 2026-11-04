#!/usr/bin/env bash
# ==============================================================================
# E2E Orchestrator Platform - Docker Hub Build & Push Script
# Builds both Frontend and Backend Docker images and pushes them to Docker Hub.
#
# Usage:
#   ./docker-build-push.sh <dockerhub-username> [tag] [--multi-arch]
#
# Examples:
#   ./docker-build-push.sh nalinkumar
#   ./docker-build-push.sh nalinkumar 1.0.0
#   ./docker-build-push.sh nalinkumar 1.0.0 --multi-arch
#   DOCKER_USERNAME=nalinkumar TAG=latest ./docker-build-push.sh
# ==============================================================================

set -euo pipefail

# ANSI color output
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# Determine project root directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo -e "${CYAN}${BOLD}"
echo "=============================================================================="
echo "      E2E Orchestrator Platform - Docker Build & Push to Docker Hub           "
echo "=============================================================================="
echo -e "${NC}"

# Parse Arguments & Environment Variables
DOCKER_USERNAME="${1:-${DOCKER_USERNAME:-${DOCKER_USER:-}}}"
TAG="${2:-${TAG:-latest}}"
MULTI_ARCH=false

for arg in "$@"; do
    if [[ "$arg" == "--multi-arch" || "$arg" == "-m" ]]; then
        MULTI_ARCH=true
    fi
done

# Prompt for Docker Hub username if not provided
if [[ -z "$DOCKER_USERNAME" ]]; then
    echo -e "${YELLOW}Docker Hub username not specified as argument or environment variable.${NC}"
    read -rp "Enter your Docker Hub username or organization: " DOCKER_USERNAME
    if [[ -z "$DOCKER_USERNAME" ]]; then
        echo -e "${RED}Error: Docker Hub username is required.${NC}"
        exit 1
    fi
fi

# Sanitize image names
BACKEND_IMAGE="${DOCKER_USERNAME}/e2e-orchestrator-backend"
FRONTEND_IMAGE="${DOCKER_USERNAME}/e2e-orchestrator-frontend"

echo -e "${BOLD}Target Configuration:${NC}"
echo -e "  • Docker Hub User : ${GREEN}${DOCKER_USERNAME}${NC}"
echo -e "  • Image Tag       : ${GREEN}${TAG}${NC}"
echo -e "  • Multi-Arch Build: ${GREEN}${MULTI_ARCH}${NC} (linux/amd64,linux/arm64)"
echo -e "  • Backend Image   : ${CYAN}${BACKEND_IMAGE}:${TAG}${NC}"
echo -e "  • Frontend Image  : ${CYAN}${FRONTEND_IMAGE}:${TAG}${NC}"
echo ""

# 1. Check if Docker daemon is running
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}Error: Docker daemon is not running. Please start Docker and retry.${NC}"
    exit 1
fi

# 2. Check Docker Hub authentication
echo -e "${CYAN}==> Checking Docker Hub authentication...${NC}"
if ! docker info | grep -qi "Username"; then
    echo -e "${YELLOW}Notice: You may not be logged in to Docker Hub.${NC}"
    echo -e "Attempting 'docker login'..."
    docker login -u "$DOCKER_USERNAME"
fi

# 3. Setup Docker Buildx if multi-arch requested
if [[ "$MULTI_ARCH" == "true" ]]; then
    echo -e "${CYAN}==> Setting up Docker Buildx for multi-architecture builds...${NC}"
    if ! docker buildx inspect e2e-builder > /dev/null 2>&1; then
        docker buildx create --name e2e-builder --driver docker-container --driver-opt network=host --bootstrap --use
    else
        docker buildx use e2e-builder
    fi
    PLATFORMS="linux/amd64,linux/arm64"
fi

# ------------------------------------------------------------------------------
# 4. Build & Push Backend Image
# ------------------------------------------------------------------------------
echo ""
echo -e "${CYAN}${BOLD}==> [1/2] Building and Pushing Backend Image...${NC}"
echo -e "    Source: ${SCRIPT_DIR}/backend"
echo -e "    Target: ${BACKEND_IMAGE}:${TAG}"

if [[ "$MULTI_ARCH" == "true" ]]; then
    EXTRA_TAGS=("-t" "${BACKEND_IMAGE}:${TAG}")
    if [[ "$TAG" != "latest" ]]; then
        EXTRA_TAGS+=("-t" "${BACKEND_IMAGE}:latest")
    fi
    docker buildx build \
        --platform "$PLATFORMS" \
        -f "${SCRIPT_DIR}/backend/Dockerfile" \
        "${EXTRA_TAGS[@]}" \
        --push \
        "${SCRIPT_DIR}/backend"
else
    docker build \
        -f "${SCRIPT_DIR}/backend/Dockerfile" \
        -t "${BACKEND_IMAGE}:${TAG}" \
        "${SCRIPT_DIR}/backend"

    if [[ "$TAG" != "latest" ]]; then
        docker tag "${BACKEND_IMAGE}:${TAG}" "${BACKEND_IMAGE}:latest"
    fi

    echo -e "${CYAN}==> Pushing Backend image to Docker Hub...${NC}"
    docker push "${BACKEND_IMAGE}:${TAG}"
    if [[ "$TAG" != "latest" ]]; then
        docker push "${BACKEND_IMAGE}:latest"
    fi
fi
echo -e "${GREEN}✓ Backend image successfully built and pushed!${NC}"

# ------------------------------------------------------------------------------
# 5. Build & Push Frontend Image
# ------------------------------------------------------------------------------
echo ""
echo -e "${CYAN}${BOLD}==> [2/2] Building and Pushing Frontend Image...${NC}"
echo -e "    Source: ${SCRIPT_DIR}/frontend"
echo -e "    Target: ${FRONTEND_IMAGE}:${TAG}"

if [[ "$MULTI_ARCH" == "true" ]]; then
    EXTRA_TAGS=("-t" "${FRONTEND_IMAGE}:${TAG}")
    if [[ "$TAG" != "latest" ]]; then
        EXTRA_TAGS+=("-t" "${FRONTEND_IMAGE}:latest")
    fi
    docker buildx build \
        --platform "$PLATFORMS" \
        -f "${SCRIPT_DIR}/frontend/Dockerfile" \
        "${EXTRA_TAGS[@]}" \
        --push \
        "${SCRIPT_DIR}/frontend"
else
    docker build \
        -f "${SCRIPT_DIR}/frontend/Dockerfile" \
        -t "${FRONTEND_IMAGE}:${TAG}" \
        "${SCRIPT_DIR}/frontend"

    if [[ "$TAG" != "latest" ]]; then
        docker tag "${FRONTEND_IMAGE}:${TAG}" "${FRONTEND_IMAGE}:latest"
    fi

    echo -e "${CYAN}==> Pushing Frontend image to Docker Hub...${NC}"
    docker push "${FRONTEND_IMAGE}:${TAG}"
    if [[ "$TAG" != "latest" ]]; then
        docker push "${FRONTEND_IMAGE}:latest"
    fi
fi
echo -e "${GREEN}✓ Frontend image successfully built and pushed!${NC}"

# ------------------------------------------------------------------------------
# 6. Generate ready-to-run docker-compose.hub.yml
# ------------------------------------------------------------------------------
cat <<EOF > "${SCRIPT_DIR}/docker-compose.hub.yml"
services:
  postgres:
    image: postgres:16-alpine
    container_name: orchestrator-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: e2e_orchestrator
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres -d e2e_orchestrator"]
      interval: 5s
      timeout: 5s
      retries: 5
    networks:
      - orchestrator-net

  backend:
    image: ${BACKEND_IMAGE}:${TAG}
    container_name: orchestrator-backend
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/e2e_orchestrator
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      SPRING_FLYWAY_ENABLED: "true"
      ORCHESTRATOR_REPORTS_PATH: /app/reports
    volumes:
      - reports_data:/app/reports
    depends_on:
      postgres:
        condition: service_healthy
    networks:
      - orchestrator-net

  frontend:
    image: ${FRONTEND_IMAGE}:${TAG}
    container_name: orchestrator-frontend
    restart: unless-stopped
    ports:
      - "3000:80"
    depends_on:
      - backend
    networks:
      - orchestrator-net

volumes:
  pgdata:
    driver: local
  reports_data:
    driver: local

networks:
  orchestrator-net:
    driver: bridge
EOF

# ------------------------------------------------------------------------------
# Summary Output
# ------------------------------------------------------------------------------
echo ""
echo -e "${GREEN}${BOLD}==============================================================================${NC}"
echo -e "${GREEN}${BOLD}                  Build & Push Completed Successfully!                        ${NC}"
echo -e "${GREEN}${BOLD}==============================================================================${NC}"
echo ""
echo -e "Published Docker Images:"
echo -e "  • ${CYAN}docker pull ${BACKEND_IMAGE}:${TAG}${NC}"
echo -e "  • ${CYAN}docker pull ${FRONTEND_IMAGE}:${TAG}${NC}"
if [[ "$TAG" != "latest" ]]; then
echo -e "  • ${CYAN}docker pull ${BACKEND_IMAGE}:latest${NC}"
echo -e "  • ${CYAN}docker pull ${FRONTEND_IMAGE}:latest${NC}"
fi
echo ""
echo -e "${BOLD}How to run anywhere with a single command:${NC}"
echo -e "  A standalone ${CYAN}docker-compose.hub.yml${NC} has been generated."
echo -e "  Copy that file to any server and run:"
echo -e "  ${YELLOW}docker compose -f docker-compose.hub.yml up -d${NC}"
echo ""
echo -e "Or run individual containers directly:"
echo -e "  ${YELLOW}docker run -d --name backend -p 8080:8080 ${BACKEND_IMAGE}:${TAG}${NC}"
echo -e "  ${YELLOW}docker run -d --name frontend -p 3000:80 ${FRONTEND_IMAGE}:${TAG}${NC}"
echo ""
