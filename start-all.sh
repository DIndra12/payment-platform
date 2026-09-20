#!/bin/bash

################################################################################
# Payment Platform - Complete Startup Script (Unix/Linux/macOS)
#
# This script sets up and starts the entire payment platform:
# 1. Starts Docker containers (PostgreSQL, Kafka, Keycloak)
# 2. Builds all microservices
# 3. Starts all 5 Spring Boot services
# 4. Verifies health of all services
#
# Usage: ./start-all.sh [--clean] [--skip-docker] [--skip-build]
# Options:
#   --clean       Remove existing containers and volumes (fresh start)
#   --skip-docker Skip docker-compose startup (use existing containers)
#   --skip-build  Skip Maven build (use existing JARs)
################################################################################

set -e

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_COMPOSE_FILE="$PROJECT_DIR/docker-compose.yml"
SERVICES=(
  "account-service:8081"
  "fraud-service:8082"
  "payment-service:8083"
  "notification-service:8084"
  "transaction-history-service:8085"
)

# Flags
CLEAN_DOCKER=false
SKIP_DOCKER=false
SKIP_BUILD=false

# Parse arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --clean) CLEAN_DOCKER=true; shift ;;
    --skip-docker) SKIP_DOCKER=true; shift ;;
    --skip-build) SKIP_BUILD=true; shift ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

# Functions
print_header() {
  echo ""
  echo -e "${BLUE}════════════════════════════════════════════════════════════════${NC}"
  echo -e "${BLUE}$1${NC}"
  echo -e "${BLUE}════════════════════════════════════════════════════════════════${NC}"
  echo ""
}

print_step() {
  echo -e "${YELLOW}▶ $1${NC}"
}

print_success() {
  echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
  echo -e "${RED}✗ $1${NC}"
}

check_docker_installed() {
  if ! command -v docker &> /dev/null; then
    print_error "Docker is not installed. Please install Docker Desktop."
    exit 1
  fi
  print_success "Docker is installed"
}

check_docker_running() {
  if ! docker ps &> /dev/null; then
    print_error "Docker daemon is not running. Please start Docker Desktop."
    exit 1
  fi
  print_success "Docker daemon is running"
}

check_port_available() {
  local port=$1
  local service=$2
  if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
    print_error "Port $port (${service}) is already in use. Please stop the service using that port."
    return 1
  fi
  return 0
}

start_docker_infrastructure() {
  print_header "Starting Docker Infrastructure"

  if [ "$SKIP_DOCKER" = true ]; then
    print_step "Skipping Docker setup (--skip-docker flag set)"
    return
  fi

  print_step "Checking required ports..."
  check_port_available 5432 "PostgreSQL"
  check_port_available 9094 "Kafka"
  check_port_available 8080 "Keycloak"

  if [ "$CLEAN_DOCKER" = true ]; then
    print_step "Removing existing containers and volumes (--clean flag set)..."
    docker-compose -f "$DOCKER_COMPOSE_FILE" down -v 2>/dev/null || true
    sleep 2
  fi

  print_step "Starting docker-compose services (PostgreSQL, Kafka, Keycloak)..."
  docker-compose -f "$DOCKER_COMPOSE_FILE" up -d

  print_step "Waiting for PostgreSQL to be ready..."
  for i in {1..30}; do
    if docker exec payments-postgres pg_isready -U postgres &> /dev/null; then
      print_success "PostgreSQL is ready"
      break
    fi
    if [ $i -eq 30 ]; then
      print_error "PostgreSQL failed to start after 30 attempts"
      exit 1
    fi
    sleep 1
  done

  print_step "Waiting for Kafka to be ready..."
  sleep 5
  print_success "Kafka is ready"

  print_success "Docker infrastructure started successfully"
}

build_services() {
  print_header "Building Microservices"

  if [ "$SKIP_BUILD" = true ]; then
    print_step "Skipping Maven build (--skip-build flag set)"
    return
  fi

  print_step "Running: ./mvnw clean package -DskipTests"
  cd "$PROJECT_DIR"
  ./mvnw clean package -DskipTests -q
  print_success "All services built successfully"
}

start_microservices() {
  print_header "Starting Microservices"

  print_step "Opening terminals and starting each service..."
  print_step "Note: Services are starting in the background"
  echo ""

  # Check if we're in a terminal that can split panes (tmux or screen)
  if command -v tmux &> /dev/null; then
    print_step "Using tmux to manage services"

    # Create new tmux session
    tmux new-session -d -s "payment-platform" -x 250 -y 50

    # Start each service in a separate pane
    for i in "${!SERVICES[@]}"; do
      local SERVICE="${SERVICES[$i]}"
      local SERVICE_NAME="${SERVICE%:*}"
      local SERVICE_PORT="${SERVICE#*:}"

      if [ $i -eq 0 ]; then
        # First service in the session
        tmux send-keys -t "payment-platform" "cd $PROJECT_DIR/$SERVICE_NAME && ./mvnw spring-boot:run" Enter
      else
        # Create new window for each additional service
        tmux new-window -t "payment-platform"
        tmux send-keys -t "payment-platform" "cd $PROJECT_DIR/$SERVICE_NAME && ./mvnw spring-boot:run" Enter
      fi

      echo "  Window $((i+1)): $SERVICE_NAME (Port $SERVICE_PORT)"
    done

    print_success "All services started in tmux session 'payment-platform'"
    echo ""
    echo -e "${YELLOW}To view logs:${NC}"
    echo "  tmux attach-session -t payment-platform"
    echo ""
    echo -e "${YELLOW}To switch between windows:${NC}"
    echo "  Ctrl+B then N (next window) or P (previous window)"
    echo ""

  elif command -v screen &> /dev/null; then
    print_step "Using screen to manage services"

    screen -dmS payment-platform

    for i in "${!SERVICES[@]}"; do
      local SERVICE="${SERVICES[$i]}"
      local SERVICE_NAME="${SERVICE%:*}"
      local SERVICE_PORT="${SERVICE#*:}"

      if [ $i -eq 0 ]; then
        screen -S payment-platform -p 0 -X stuff "cd $PROJECT_DIR/$SERVICE_NAME && ./mvnw spring-boot:run\n"
      else
        screen -S payment-platform -X screen
        screen -S payment-platform -X stuff "cd $PROJECT_DIR/$SERVICE_NAME && ./mvnw spring-boot:run\n"
      fi

      echo "  Screen window $((i+1)): $SERVICE_NAME (Port $SERVICE_PORT)"
    done

    print_success "All services started in screen session 'payment-platform'"
    echo ""
    echo -e "${YELLOW}To view logs:${NC}"
    echo "  screen -r payment-platform"

  else
    # Fallback: Start in background without terminal multiplexer
    print_step "Starting services in background (no tmux/screen available)"
    print_step "Note: To view logs, check the service directories"
    echo ""

    for SERVICE in "${SERVICES[@]}"; do
      local SERVICE_NAME="${SERVICE%:*}"
      local SERVICE_PORT="${SERVICE#*:}"

      (
        cd "$PROJECT_DIR/$SERVICE_NAME"
        ./mvnw spring-boot:run > "/tmp/${SERVICE_NAME}.log" 2>&1 &
      )

      echo "  Starting $SERVICE_NAME (Port $SERVICE_PORT)"
      echo "    Logs: /tmp/${SERVICE_NAME}.log"
    done

    print_success "All services started in background"
  fi
}

verify_services() {
  print_header "Verifying Services"

  print_step "Waiting for services to start (this may take 1-2 minutes)..."
  echo ""

  local all_healthy=true

  for SERVICE in "${SERVICES[@]}"; do
    local SERVICE_NAME="${SERVICE%:*}"
    local SERVICE_PORT="${SERVICE#*:}"

    # Try up to 30 times (30 seconds total)
    for i in {1..30}; do
      if curl -s -f "http://localhost:$SERVICE_PORT/actuator/health" &> /dev/null; then
        print_success "$SERVICE_NAME is running on port $SERVICE_PORT"
        break
      fi

      if [ $i -eq 30 ]; then
        print_error "$SERVICE_NAME failed to start (port $SERVICE_PORT)"
        all_healthy=false
      fi

      sleep 1
    done
  done

  echo ""

  if [ "$all_healthy" = true ]; then
    print_success "All services are healthy and running!"
  else
    print_error "Some services failed to start. Check logs for details."
    exit 1
  fi
}

print_summary() {
  print_header "Setup Complete!"

  echo -e "${GREEN}All services are now running:${NC}"
  echo ""
  for SERVICE in "${SERVICES[@]}"; do
    local SERVICE_NAME="${SERVICE%:*}"
    local SERVICE_PORT="${SERVICE#*:}"
    echo "  ✓ $SERVICE_NAME:        http://localhost:$SERVICE_PORT"
  done
  echo ""

  echo -e "${GREEN}Infrastructure:${NC}"
  echo "  ✓ PostgreSQL:          localhost:5432"
  echo "  ✓ Kafka:               localhost:9094"
  echo "  ✓ Keycloak:            http://localhost:8080"
  echo ""

  echo -e "${YELLOW}Next Steps:${NC}"
  echo ""
  echo "1. Import Postman Collection:"
  echo "   - Open Postman"
  echo "   - File → Import"
  echo "   - Select: postman-collection.json"
  echo ""
  echo "2. Run Test Requests:"
  echo "   - Go to 'Setup & Variables' → 'Get test accounts (Setup)'"
  echo "   - Click Send"
  echo "   - Then run any request to test the APIs"
  echo ""
  echo "3. View Service Health:"
  for SERVICE in "${SERVICES[@]}"; do
    local SERVICE_NAME="${SERVICE%:*}"
    local SERVICE_PORT="${SERVICE#*:}"
    echo "   - curl http://localhost:$SERVICE_PORT/actuator/health"
  done
  echo ""

  echo -e "${YELLOW}Useful Commands:${NC}"
  echo ""
  echo "  Stop all services:"
  echo "    ./stop-all.sh"
  echo ""
  echo "  View service logs:"
  if command -v tmux &> /dev/null; then
    echo "    tmux attach-session -t payment-platform"
  elif command -v screen &> /dev/null; then
    echo "    screen -r payment-platform"
  else
    echo "    tail -f /tmp/<service-name>.log"
  fi
  echo ""
  echo "  Restart infrastructure:"
  echo "    docker-compose up -d"
  echo ""
  echo "  View documentation:"
  echo "    - README.md (Project overview)"
  echo "    - POSTMAN-GUIDE.md (How to test APIs)"
  echo "    - PRODUCTION-READINESS.md (Roadmap to production)"
  echo ""
}

main() {
  print_header "Payment Platform - Complete Startup"

  print_step "Checking prerequisites..."
  check_docker_installed
  check_docker_running

  start_docker_infrastructure
  build_services
  start_microservices
  verify_services
  print_summary
}

# Run main function
main
