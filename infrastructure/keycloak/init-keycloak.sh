#!/bin/sh

# Keycloak Initialization Script
# This script sets up:
# 1. Realm: "payment-platform"
# 2. Client: "payment-api"
# 3. Test users with roles

set -e

# Ensure curl is available (Alpine Linux doesn't include it by default)
if ! command -v curl > /dev/null 2>&1; then
    echo "Installing curl..."
    apk add --no-cache curl || {
        echo "Failed to install curl"
        exit 1
    }
fi

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8090}"
ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
REALM="payment-platform"
CLIENT_ID="payment-api"

echo "Starting Keycloak initialization..."
echo "Keycloak URL: $KEYCLOAK_URL"

# Wait for Keycloak to be ready
echo "Waiting for Keycloak to be ready..."
for i in $(seq 1 60); do
  # Try the health endpoint with better error handling
  HEALTH_RESPONSE=$(curl -s -w "\n%{http_code}" "$KEYCLOAK_URL/health/ready" 2>/dev/null)
  HTTP_CODE=$(echo "$HEALTH_RESPONSE" | tail -n1)

  if [ "$HTTP_CODE" = "200" ]; then
    echo "Keycloak is ready!"
    break
  fi

  # Also try alternate endpoint if health/ready fails
  if curl -s "$KEYCLOAK_URL/" > /dev/null 2>&1; then
    echo "Keycloak is reachable! Proceeding with initialization..."
    sleep 2
    break
  fi

  echo "Attempt $i/60: Waiting for Keycloak (HTTP $HTTP_CODE)..."
  sleep 1
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

# Create realm roles first, so users can be mapped to them
echo "Creating roles..."
for ROLE in USER ADMIN SUPPORT; do
  curl -s -X POST \
    "$KEYCLOAK_URL/admin/realms/$REALM/roles" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d '{"name": "'$ROLE'"}' || echo "Role $ROLE may already exist"
done

# Create a test user and give it one realm role.
# Keycloak 24's default user profile requires email/first/last name; without them
# token requests fail with "Account is not fully set up".
create_user() {
  USERNAME=$1; FIRST=$2; LAST=$3; ROLE=$4
  echo "Creating user: $USERNAME (role $ROLE)"
  curl -s -X POST \
    "$KEYCLOAK_URL/admin/realms/$REALM/users" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d '{
      "username": "'$USERNAME'",
      "email": "'$USERNAME'@example.com",
      "emailVerified": true,
      "firstName": "'$FIRST'",
      "lastName": "'$LAST'",
      "enabled": true,
      "requiredActions": [],
      "credentials": [{"type": "password", "value": "password123", "temporary": false}]
    }' || echo "User $USERNAME may already exist"

  USER_ID=$(curl -s "$KEYCLOAK_URL/admin/realms/$REALM/users?username=$USERNAME&exact=true" \
    -H "Authorization: Bearer $ADMIN_TOKEN" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)
  ROLE_JSON=$(curl -s "$KEYCLOAK_URL/admin/realms/$REALM/roles/$ROLE" \
    -H "Authorization: Bearer $ADMIN_TOKEN")
  if [ -n "$USER_ID" ]; then
    curl -s -X POST \
      "$KEYCLOAK_URL/admin/realms/$REALM/users/$USER_ID/role-mappings/realm" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $ADMIN_TOKEN" \
      -d "[$ROLE_JSON]" || echo "Could not map role $ROLE to $USERNAME"
  fi
}

echo "Creating test users..."
create_user test-user Test User USER
create_user admin-user Admin User ADMIN
create_user support-user Support User SUPPORT

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
