#!/bin/bash

# Keycloak Initialization Script
# This script sets up:
# 1. Realm: "payment-platform"
# 2. Client: "payment-api"
# 3. Test users with roles

set -e

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8090}"
ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
REALM="payment-platform"
CLIENT_ID="payment-api"

echo "Starting Keycloak initialization..."
echo "Keycloak URL: $KEYCLOAK_URL"

# Wait for Keycloak to be ready
echo "Waiting for Keycloak to be ready..."
for i in {1..30}; do
  if curl -s "$KEYCLOAK_URL/health/ready" > /dev/null 2>&1; then
    echo "Keycloak is ready!"
    break
  fi
  echo "Attempt $i/30: Waiting for Keycloak..."
  sleep 2
done

# Get admin token
echo "Authenticating as admin..."
TOKEN_RESPONSE=$(curl -s -X POST \
  "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=admin-cli" \
  -d "username=$ADMIN_USER" \
  -d "password=$ADMIN_PASSWORD" \
  -d "grant_type=password")

ADMIN_TOKEN=$(echo $TOKEN_RESPONSE | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)

if [ -z "$ADMIN_TOKEN" ]; then
  echo "Failed to get admin token"
  echo "Response: $TOKEN_RESPONSE"
  exit 1
fi

echo "Admin authenticated successfully"

# Create realm
echo "Creating realm: $REALM"
curl -s -X POST \
  "$KEYCLOAK_URL/admin/realms" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "realm": "'$REALM'",
    "enabled": true,
    "displayName": "Payment Platform"
  }' || echo "Realm may already exist"

# Create client
echo "Creating client: $CLIENT_ID"
CLIENT_RESPONSE=$(curl -s -X POST \
  "$KEYCLOAK_URL/admin/realms/$REALM/clients" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "clientId": "'$CLIENT_ID'",
    "enabled": true,
    "publicClient": true,
    "directAccessGrantsEnabled": true,
    "standardFlowEnabled": true,
    "redirectUris": ["http://localhost:*/*"],
    "webOrigins": ["http://localhost:*"],
    "protocol": "openid-connect"
  }')

CLIENT_UUID=$(echo $CLIENT_RESPONSE | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)

if [ -z "$CLIENT_UUID" ]; then
  echo "Failed to create client or it already exists"
  # Try to find existing client
  SEARCH_RESPONSE=$(curl -s "$KEYCLOAK_URL/admin/realms/$REALM/clients?clientId=$CLIENT_ID" \
    -H "Authorization: Bearer $ADMIN_TOKEN")

  CLIENT_UUID=$(echo $SEARCH_RESPONSE | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)
  echo "Using existing client UUID: $CLIENT_UUID"
fi

# Create test users
echo "Creating test users..."

# User 1: test-user with USER role
echo "Creating user: test-user"
curl -s -X POST \
  "$KEYCLOAK_URL/admin/realms/$REALM/users" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "username": "test-user",
    "firstName": "Test",
    "lastName": "User",
    "enabled": true,
    "credentials": [{
      "type": "password",
      "value": "password123",
      "temporary": false
    }],
    "clientRoles": {
      "'$CLIENT_ID'": ["USER"]
    }
  }' || echo "User may already exist"

# User 2: admin-user with ADMIN role
echo "Creating user: admin-user"
curl -s -X POST \
  "$KEYCLOAK_URL/admin/realms/$REALM/users" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "username": "admin-user",
    "firstName": "Admin",
    "lastName": "User",
    "enabled": true,
    "credentials": [{
      "type": "password",
      "value": "password123",
      "temporary": false
    }],
    "clientRoles": {
      "'$CLIENT_ID'": ["ADMIN"]
    }
  }' || echo "User may already exist"

# Create roles if they don't exist
echo "Creating roles..."
for ROLE in USER ADMIN SUPPORT; do
  curl -s -X POST \
    "$KEYCLOAK_URL/admin/realms/$REALM/roles" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d '{
      "name": "'$ROLE'",
      "enabled": true
    }' || echo "Role $ROLE may already exist"
done

echo ""
echo "============================================"
echo "Keycloak initialization completed!"
echo ""
echo "Realm: $REALM"
echo "Client: $CLIENT_ID"
echo ""
echo "Test Users:"
echo "  Username: test-user / Password: password123 (Role: USER)"
echo "  Username: admin-user / Password: password123 (Role: ADMIN)"
echo ""
echo "Access Keycloak Admin Console:"
echo "  $KEYCLOAK_URL"
echo "============================================"
