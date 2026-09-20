#!/bin/bash

################################################################################
# Payment Platform - Stop All Services Script (Unix/Linux/macOS)
#
# This script gracefully shuts down all services:
# 1. Kills Spring Boot services (if running in tmux/screen)
# 2. Stops Docker containers
# 3. Displays cleanup summary
#
# Usage: ./stop-all.sh [--remove-volumes]
# Options:
#   --remove-volumes  Remove Docker volumes (clean database)
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

# Flags
REMOVE_VOLUMES=false

# Parse arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --remove-volumes) REMOVE_VOLUMES=true; shift ;;
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

stop_services() {
  print_header "Stopping Services"

  print_step "Checking for running service managers..."

  if command -v tmux &> /dev/null && tmux list-sessions 2>/dev/null | grep -q "payment-platform"; then
    print_step "Killing tmux session 'payment-platform'..."
    tmux kill-session -t payment-platform 2>/dev/null || true
    print_success "Tmux session terminated"

  elif command -v screen &> /dev/null && screen -list | grep -q "payment-platform"; then
    print_step "Killing screen session 'payment-platform'..."
    screen -S payment-platform -X quit 2>/dev/null || true
    print_success "Screen session terminated"

  else
    print_step "No terminal multiplexer sessions found"
    print_step "If services are running in separate terminal windows, close them manually"
  fi

  # Kill any lingering Java processes
  print_step "Checking for lingering Java processes..."
  if pgrep -f "spring-boot:run" > /dev/null; then
    print_step "Killing lingering Java processes..."
    pkill -f "spring-boot:run" 2>/dev/null || true
    sleep 2
    print_success "Java processes terminated"
  fi
}

stop_docker() {
  print_header "Stopping Docker Infrastructure"

  if [ ! -f "$DOCKER_COMPOSE_FILE" ]; then
    print_error "docker-compose.yml not found at $DOCKER_COMPOSE_FILE"
    return
  fi

  print_step "Stopping docker-compose services..."
  cd "$PROJECT_DIR"

  if [ "$REMOVE_VOLUMES" = true ]; then
    print_step "Removing containers and volumes (--remove-volumes flag set)..."
    docker-compose -f "$DOCKER_COMPOSE_FILE" down -v
    print_success "Containers and volumes removed"
  else
    print_step "Stopping containers (volumes preserved)..."
    docker-compose -f "$DOCKER_COMPOSE_FILE" stop
    print_success "Containers stopped"
  fi
}

print_summary() {
  print_header "Shutdown Complete!"

  echo -e "${GREEN}All services have been stopped:${NC}"
  echo ""
  echo "  ✓ Spring Boot services terminated"
  echo "  ✓ Docker containers stopped"
  if [ "$REMOVE_VOLUMES" = true ]; then
    echo "  ✓ Volumes removed (databases cleared)"
  fi
  echo ""

  echo -e "${YELLOW}To restart services:${NC}"
  echo ""
  if [ "$REMOVE_VOLUMES" = true ]; then
    echo "  ./start-all.sh --clean"
  else
    echo "  ./start-all.sh"
  fi
  echo ""

  echo -e "${YELLOW}Useful commands:${NC}"
  echo ""
  echo "  Start only Docker (no services):"
  echo "    docker-compose up -d"
  echo ""
  echo "  Check Docker status:"
  echo "    docker-compose ps"
  echo ""
  echo "  View Docker logs:"
  echo "    docker-compose logs -f postgres"
  echo ""
  echo "  Remove all data and containers:"
  echo "    docker-compose down -v"
  echo ""
}

main() {
  print_header "Payment Platform - Stop All Services"

  stop_services
  stop_docker
  print_summary
}

# Run main function
main
