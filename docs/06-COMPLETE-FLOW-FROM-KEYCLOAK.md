# Complete Payment Platform Flow - Starting from Keycloak

**Date:** 2026-11-20  
**Purpose:** End-to-end flow from user authentication through payment completion with diagrams

---

## Overview: The Complete Journey

```
┌─────────────────────────────────────────────────────────────────┐
│                    USER'S JOURNEY                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  1. User Login (Keycloak)                                        │
│     ↓                                                            │
│  2. Get JWT Token                                               │
│     ↓                                                            │
│  3. Call API (with JWT token)                                   │
│     ↓                                                            │
│  4. API Gateway validates token                                 │
│     ↓                                                            │
│  5. Route to Payment Service                                    │
│     ↓                                                            │
│  6. Process payment (saga orchestration)                        │
│     ↓                                                            │
│  7. Services communicate (sync + async)                         │
│     ↓                                                            │
│  8. Payment completes                                           │
│     ↓                                                            │
│  9. Notification sent to user                                   │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

---

## STEP 1: USER LOGIN & AUTHENTICATION (Keycloak)

### What is Keycloak?

Keycloak is an **Identity Provider (IdP)** - it manages:
- User accounts and credentials
- Password hashing and storage
- JWT token generation
- OAuth2/OIDC protocols

```
┌──────────────────────────────────────────────────────────────────┐
│                        KEYCLOAK (Port 8090)                      │
│                    Identity & Access Management                  │
│                                                                  │
│  Responsibilities:                                              │
│  ✓ Stores user credentials (securely hashed)                    │
│  ✓ Authenticates login requests                                 │
│  ✓ Issues JWT tokens                                            │
│  ✓ Manages user roles & permissions                             │
│  ✓ Validates/renews tokens                                      │
│  ✓ Handles logout & token revocation                            │
└──────────────────────────────────────────────────────────────────┘
```

### Login Flow

```
TIME: 2026-11-20 14:00:00

T+0s: USER OPENS MOBILE APP
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
┌─────────────────────────────┐
│ Mobile App / Web UI         │
│                             │
│ [Login Screen]              │
│ Email: john@example.com     │
│ Password: ••••••••          │
│ [Sign In Button]            │
└─────────────────────────────┘
              │
              │ User enters credentials and clicks "Sign In"
              ▼

T+1s: KEYCLOAK LOGIN PAGE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
┌────────────────────────────────────────────────────────────┐
│ Browser redirects to:                                      │
│ http://localhost:8090/auth/realms/payment-platform/       │
│ protocol/openid-connect/auth?                             │
│ client_id=payment-mobile-app                              │
│ redirect_uri=https://mobile-app/callback                  │
│ response_type=code                                        │
│ scope=openid+profile+email                                │
│                                                            │
│ User sees Keycloak login form                             │
│ Email: john@example.com                                   │
│ Password: [user enters password]                          │
│ [Login Button]                                            │
└────────────────────────────────────────────────────────────┘
              │
              │ User clicks "Login"
              ▼

T+2s: KEYCLOAK VALIDATES CREDENTIALS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
┌────────────────────────────────────────────────────────────┐
│ Keycloak Database Query:                                  │
│                                                            │
│ 1. Query users table:                                      │
│    SELECT * FROM users                                    │
│    WHERE email = 'john@example.com'                       │
│                                                            │
│ 2. User found:                                            │
│    id: user-123                                           │
│    email: john@example.com                                │
│    password_hash: $2a$12$R9h/cIPz0gi.URNNGU3...          │
│    roles: [CUSTOMER]                                      │
│                                                            │
│ 3. Verify password:                                       │
│    bcrypt_compare(                                        │
│      plaintext_password: "user_entered_password",         │
│      hash: "$2a$12$R9h/cIPz0gi.URNNGU3..."               │
│    ) = ✓ TRUE (password matches)                          │
│                                                            │
│ ✓ AUTHENTICATION SUCCESSFUL                               │
└────────────────────────────────────────────────────────────┘
              │
              ▼

T+3s: KEYCLOAK GENERATES JWT TOKEN
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
┌────────────────────────────────────────────────────────────┐
│ Keycloak creates JWT:                                     │
│                                                            │
│ HEADER:                                                   │
│ {                                                         │
│   "alg": "RS256",                                         │
│   "typ": "JWT"                                            │
│ }                                                         │
│                                                            │
│ PAYLOAD (Claims):                                         │
│ {                                                         │
│   "sub": "user-123",                    ◄─── User ID      │
│   "email": "john@example.com",                            │
│   "name": "John Doe",                                     │
│   "preferred_username": "john",                           │
│   "realm_access": {                                       │
│     "roles": ["CUSTOMER", "user"]                         │
│   },                                                      │
│   "resource_access": {                                    │
│     "payment-api": {                                      │
│       "roles": ["CUSTOMER"]                               │
│     }                                                     │
│   },                                                      │
│   "iat": 1700480400,          ◄─── Issued At (now)        │
│   "exp": 1700484000,          ◄─── Expires in 1 hour     │
│   "iss": "http://keycloak:8090/auth/realms/payment-platform"
│ }                                                         │
│                                                            │
│ SIGNATURE:                                                │
│ HMACSHA256(                                               │
│   header + "." + payload,                                 │
│   private_key                                             │
│ ) = "eyJhbGc...xyz..."                                    │
│                                                            │
│ COMPLETE JWT:                                             │
│ eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ...     │
│ ...eyJpc3MiOiJodHRwOi8va2V5Y2xvYWs6ODA5MC9...           │
│                                                            │
│ Token is SIGNED with Keycloak's private key              │
│ → Can't be forged (only Keycloak can sign)               │
│ → Anyone can verify with Keycloak's public key           │
└────────────────────────────────────────────────────────────┘
              │
              │ Keycloak redirects back to app with token
              ▼

T+4s: TOKEN SENT TO CLIENT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
┌────────────────────────────────────────────────────────────┐
│ Browser redirects to:                                     │
│ https://mobile-app/callback?                              │
│ code=eyJhbGciOiJSUzI1NiI...                              │
│                                                            │
│ Mobile App:                                               │
│ 1. Receives authorization code                            │
│ 2. Exchanges code for tokens (backend-to-backend call)   │
│ 3. Stores token in secure storage:                        │
│    - Android: Android Keystore                            │
│    - iOS: Keychain                                        │
│    - Web: LocalStorage (less secure)                      │
│                                                            │
│ ✓ User is now LOGGED IN                                   │
│ ✓ Token stored locally                                    │
│ ✓ Ready to make API calls                                 │
└────────────────────────────────────────────────────────────┘
```

**Key Security Points:**
- Password is NEVER sent to backend services
- Only JWT token is used for subsequent requests
- Token expires in 1 hour (time-limited)
- Token is cryptographically signed
- Cannot be forged or modified

---

## STEP 2: USER MAKES API CALL WITH JWT TOKEN

### The Request

```
TIME: 2026-11-20 14:30:00 (30 minutes after login)

USER ACTION: Click "Pay $100" in mobile app
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
┌────────────────────────────────────────────────────────────┐
│ HTTP REQUEST HEADERS:                                     │
├────────────────────────────────────────────────────────────┤
│ POST /api/v1/payments/create HTTP/1.1                     │
│ Host: api.payment-platform.com                           │
│ Authorization: Bearer eyJhbGciOiJSUzI1NiI...             │
│                        ▲                                  │
│                        │ JWT token from login             │
│ Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000    │
│                 ▲                                         │
│                 │ Prevents duplicate payments on retry   │
│ Content-Type: application/json                           │
│ Content-Length: 42                                        │
│                                                            │
│ REQUEST BODY:                                             │
│ {                                                         │
│   "amount": 100.00,                                       │
│   "currency": "USD",                                      │
│   "account_id": "user-123",                               │
│   "description": "Coffee"                                 │
│ }                                                         │
└────────────────────────────────────────────────────────────┘
```

---

## STEP 3: API GATEWAY RECEIVES REQUEST & VALIDATES JWT

### The Gateway Processing

```
T+0s: REQUEST ARRIVES AT API GATEWAY
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

┌───────────────────────────────────────────────────────────────┐
│                   API GATEWAY (Port 8080)                      │
│           Spring Cloud Gateway + JwtAuthenticationFilter       │
│                                                                │
│ REQUEST PIPELINE:                                             │
│                                                                │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ STEP 1: Extract JWT Token from Header                   │   │
│ │                                                          │   │
│ │ Authorization header: Bearer eyJhbGciOiJSUzI1NiI...     │   │
│ │ Extract: eyJhbGciOiJSUzI1NiI...                         │   │
│ │ ✓ Token extracted                                        │   │
│ └─────────────────────────────────────────────────────────┘   │
│                           │                                    │
│                           ▼                                    │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ STEP 2: Decode JWT (without verification first)         │   │
│ │                                                          │   │
│ │ Split on dots:                                          │   │
│ │ header . payload . signature                            │   │
│ │   │       │          │                                  │   │
│ │   ▼       ▼          ▼                                  │   │
│ │  {       {           signature                          │   │
│ │   alg    sub:        (kept for                          │   │
│ │  }      user-123     verification)                      │   │
│ │        }                                                │   │
│ │                                                          │   │
│ │ ✓ Token decoded                                          │   │
│ └─────────────────────────────────────────────────────────┘   │
│                           │                                    │
│                           ▼                                    │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ STEP 3: Verify JWT Signature                            │   │
│ │                                                          │   │
│ │ Gateway has Keycloak's PUBLIC KEY (cached locally)      │   │
│ │                                                          │   │
│ │ Verify:                                                 │   │
│ │ HMACSHA256(header.payload, PUBLIC_KEY) == signature     │   │
│ │                                                          │   │
│ │ Calculation:                                            │   │
│ │ ✓ header . payload with PUBLIC_KEY                      │   │
│ │ ✓ Result matches signature in JWT                       │   │
│ │                                                          │   │
│ │ ✓ SIGNATURE VALID (token not forged)                    │   │
│ └─────────────────────────────────────────────────────────┘   │
│                           │                                    │
│                           ▼                                    │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ STEP 4: Check Token Expiration                          │   │
│ │                                                          │   │
│ │ Token Claims:                                           │   │
│ │ iat (issued at): 1700480400 (14:00:00)                  │   │
│ │ exp (expires):   1700484000 (15:00:00)                  │   │
│ │                                                          │   │
│ │ Current time:    1700482200 (14:30:00)                  │   │
│ │                                                          │   │
│ │ Check: current_time < exp_time                          │   │
│ │ 1700482200 < 1700484000 = ✓ TRUE                        │   │
│ │                                                          │   │
│ │ ✓ TOKEN NOT EXPIRED                                      │   │
│ └─────────────────────────────────────────────────────────┘   │
│                           │                                    │
│                           ▼                                    │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ STEP 5: Extract User Information                        │   │
│ │                                                          │   │
│ │ From JWT Payload:                                       │   │
│ │ sub: "user-123"       → User ID                         │   │
│ │ email: "john@..."     → Email                           │   │
│ │ roles: ["CUSTOMER"]   → User Roles/Permissions         │   │
│ │                                                          │   │
│ │ Store in request header:                                │   │
│ │ X-User-Id: user-123                                     │   │
│ │ X-User-Email: john@example.com                          │   │
│ │ X-User-Roles: CUSTOMER                                  │   │
│ │                                                          │   │
│ │ ✓ USER INFORMATION EXTRACTED                             │   │
│ └─────────────────────────────────────────────────────────┘   │
│                           │                                    │
│                           ▼                                    │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ STEP 6: Rate Limiting Check                             │   │
│ │                                                          │   │
│ │ Using token bucket algorithm:                           │   │
│ │ User: user-123                                          │   │
│ │ Limit: 100 requests per minute                          │   │
│ │                                                          │   │
│ │ Query rate limit cache:                                 │   │
│ │ Tokens for user-123: 99 remaining                       │   │
│ │                                                          │   │
│ │ Check: 99 > 0?                                          │   │
│ │ ✓ TRUE                                                   │   │
│ │                                                          │   │
│ │ Deduct 1 token:                                         │   │
│ │ Tokens for user-123: 98 remaining                       │   │
│ │                                                          │   │
│ │ ✓ RATE LIMIT OK (request allowed)                       │   │
│ └─────────────────────────────────────────────────────────┘   │
│                           │                                    │
│                           ▼                                    │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ STEP 7: Route Request to Backend Service                │   │
│ │                                                          │   │
│ │ Parse request URL:                                      │   │
│ │ POST /api/v1/payments/create                            │   │
│ │                                                          │   │
│ │ Pattern matching:                                       │   │
│ │ /api/v1/payments/** → Route to payment-service:8083    │   │
│ │ /api/v1/accounts/** → Route to account-service:8081    │   │
│ │ /api/v1/risk/**     → Route to fraud-service:8082      │   │
│ │                                                          │   │
│ │ ✓ ROUTE: payment-service:8083                           │   │
│ │                                                          │   │
│ │ Forward request with headers:                           │   │
│ │ X-User-Id: user-123 (added by gateway)                  │   │
│ │ Idempotency-Key: 550e8400...  (passed through)          │   │
│ │ Authorization: Bearer ... (kept or removed)             │   │
│ └─────────────────────────────────────────────────────────┘   │
│                           │                                    │
│                           ▼                                    │
│                   ✓ REQUEST FORWARDED                          │
│                     to Payment Service                         │
└───────────────────────────────────────────────────────────────┘
```

**What if JWT validation fails?**

```
SCENARIO 1: Signature Invalid
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Attacker modifies JWT payload:
  Original: {...sub: "user-123"...}
  Modified: {...sub: "admin"...}
  
  But doesn't re-sign (doesn't have Keycloak private key)
  
  Gateway verification:
  HMACSHA256(modified_header.modified_payload, PUBLIC_KEY) 
    ≠ original_signature
  
  ✗ SIGNATURE MISMATCH
  → Return 401 Unauthorized


SCENARIO 2: Token Expired
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Token was issued at 14:00, expires at 15:00
Current time: 15:30 (30 minutes after expiration)

  exp: 1700484000
  current_time: 1700488200
  
  Check: 1700488200 < 1700484000?
  ✗ FALSE (token is expired)
  
  ✗ TOKEN EXPIRED
  → Return 401 Unauthorized
  → Client should refresh token or login again


SCENARIO 3: No Authorization Header
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Request arrives without Authorization header:

  POST /api/v1/payments/create
  [no Authorization header]
  
  Gateway looks for token: null
  
  ✗ NO TOKEN PROVIDED
  → Return 401 Unauthorized
  → Client must login first
```

---

## STEP 4: PAYMENT SERVICE PROCESSES PAYMENT

### Payment Service Receives Request

```
T+5ms: PAYMENT SERVICE (Port 8083) RECEIVES REQUEST
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

┌───────────────────────────────────────────────────────────────┐
│                 PAYMENT SERVICE                               │
│         (Saga Orchestrator for Payment Flow)                  │
│                                                                │
│ RECEIVE REQUEST:                                              │
│ X-User-Id: user-123 (added by gateway)                        │
│ Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000        │
│ Body: { amount: 100.00, currency: "USD", ... }              │
│                                                                │
│ PROCESSING STEPS:                                             │
│                                                                │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ 1. VALIDATE INPUT                                       │   │
│ │                                                          │   │
│ │ amount = 100.00                                         │   │
│ │ ├─ Check: amount > 0?           ✓ YES                   │   │
│ │ ├─ Check: amount < MAX?         ✓ YES                   │   │
│ │ └─ Valid amount                                         │   │
│ │                                                          │   │
│ │ currency = "USD"                                        │   │
│ │ ├─ Check: valid currency?       ✓ YES                   │   │
│ │ └─ Valid currency                                       │   │
│ │                                                          │   │
│ │ account_id = user-123                                   │   │
│ │ ├─ Check: account exists?       ✓ YES                   │   │
│ │ └─ Valid account                                        │   │
│ └─────────────────────────────────────────────────────────┘   │
│                           │                                    │
│                           ▼                                    │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ 2. CHECK IDEMPOTENCY                                    │   │
│ │                                                          │   │
│ │ Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000  │   │
│ │                                                          │   │
│ │ Query idempotency_keys table:                           │   │
│ │ SELECT * FROM idempotency_keys                          │   │
│ │ WHERE key = '550e8400-e29b-41d4-a716-446655440000'     │   │
│ │                                                          │   │
│ │ Result: NOT FOUND (first time)                          │   │
│ │ ✓ This is a new payment request                         │   │
│ │ Proceed to create payment                               │   │
│ │                                                          │   │
│ │ (If found: return cached response from previous call)   │   │
│ └─────────────────────────────────────────────────────────┘   │
│                           │                                    │
│                           ▼                                    │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ 3. START SAGA (State Machine)                           │   │
│ │                                                          │   │
│ │ Create Payment entity:                                  │   │
│ │ ├─ id: 550e8400-e29b-41d4-a716-446655440000             │   │
│ │ ├─ account_id: user-123                                 │   │
│ │ ├─ amount: 100.00                                       │   │
│ │ ├─ currency: USD                                        │   │
│ │ ├─ status: INITIATED                                    │   │
│ │ ├─ created_at: 2026-11-20T14:30:00Z                     │   │
│ │ └─ saga_state: INITIATED                                │   │
│ │                                                          │   │
│ │ ✓ Payment ready for database                            │   │
│ └─────────────────────────────────────────────────────────┘   │
│                           │                                    │
│                           ▼                                    │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ 4. ATOMIC TRANSACTION (Outbox Pattern)                  │   │
│ │                                                          │   │
│ │ ╔═════════════════════════════════════════════════════╗ │   │
│ │ ║ TRANSACTION START                                   ║ │   │
│ │ ║                                                     ║ │   │
│ │ ║ INSERT INTO payments (                              ║ │   │
│ │ ║   id, account_id, amount, currency, status, ...    ║ │   │
│ │ ║ ) VALUES (                                          ║ │   │
│ │ ║   '550e8400...', 'user-123', 100.00, 'USD',        ║ │   │
│ │ ║   'INITIATED', ...                                  ║ │   │
│ │ ║ )                                                   ║ │   │
│ │ ║ ✓ Payment inserted                                  ║ │   │
│ │ ║                                                     ║ │   │
│ │ ║ INSERT INTO payment_outbox (                         ║ │   │
│ │ ║   id, payment_id, event_type, payload,              ║ │   │
│ │ ║   published, created_at                             ║ │   │
│ │ ║ ) VALUES (                                          ║ │   │
│ │ ║   1, '550e8400...', 'payment.initiated',            ║ │   │
│ │ ║   '{"payment_id": "550e8400...", ...}', false,      ║ │   │
│ │ ║   NOW()                                             ║ │   │
│ │ ║ )                                                   ║ │   │
│ │ ║ ✓ Event inserted to outbox                          ║ │   │
│ │ ║                                                     ║ │   │
│ │ ║ INSERT INTO idempotency_keys (                       ║ │   │
│ │ ║   key, response, created_at                          ║ │   │
│ │ ║ ) VALUES (                                          ║ │   │
│ │ ║   '550e8400...', '{"id":"550e8400...",...}',        ║ │   │
│ │ ║   NOW()                                             ║ │   │
│ │ ║ )                                                   ║ │   │
│ │ ║ ✓ Idempotency key stored                            ║ │   │
│ │ ║                                                     ║ │   │
│ │ ║ COMMIT (all 3 inserts happen together)              ║ │   │
│ │ ║ ✓ ALL SUCCEED or ALL FAIL (atomic)                  ║ │   │
│ │ ╚═════════════════════════════════════════════════════╝ │   │
│ │                                                          │   │
│ │ ✓ PAYMENT CREATED IN DATABASE                          │   │
│ │ ✓ EVENT READY IN OUTBOX                                │   │
│ │ ✓ IDEMPOTENCY KEY STORED                               │   │
│ └─────────────────────────────────────────────────────────┘   │
│                           │                                    │
│                           ▼                                    │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ 5. RETURN RESPONSE TO GATEWAY                           │   │
│ │                                                          │   │
│ │ HTTP 201 Created                                        │   │
│ │ {                                                       │   │
│ │   "id": "550e8400-e29b-41d4-a716-446655440000",         │   │
│ │   "account_id": "user-123",                             │   │
│ │   "amount": 100.00,                                     │   │
│ │   "currency": "USD",                                    │   │
│ │   "status": "INITIATED",                                │   │
│ │   "created_at": "2026-11-20T14:30:00Z"                  │   │
│ │ }                                                       │   │
│ └─────────────────────────────────────────────────────────┘   │
│                           │                                    │
│                           ▼                                    │
│                 ✓ RESPONSE SENT TO CLIENT                     │
└───────────────────────────────────────────────────────────────┘
```

---

## STEP 5: OUTBOX POLLER PUBLISHES TO KAFKA

```
T+6ms: OUTBOX POLLER RUNS (every 1 second)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

┌───────────────────────────────────────────────────────────────┐
│          OUTBOX POLLER (Background Task)                      │
│    Polls database for unpublished events                      │
│                                                                │
│ SCHEDULED: Every 1 second                                     │
│                                                                │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ 1. Query unpublished events                             │   │
│ │                                                          │   │
│ │ SELECT * FROM payment_outbox                            │   │
│ │ WHERE published = false                                 │   │
│ │ LIMIT 100                                               │   │
│ │ ORDER BY created_at ASC                                 │   │
│ │                                                          │   │
│ │ Results:                                                │   │
│ │ ┌──────────────────────────────────┐                    │   │
│ │ │ id: 1                             │                    │   │
│ │ │ payment_id: 550e8400...           │                    │   │
│ │ │ event_type: payment.initiated     │                    │   │
│ │ │ payload: {...}                    │                    │   │
│ │ │ published: false                  │                    │   │
│ │ │ created_at: 2026-11-20 14:30:00  │                    │   │
│ │ └──────────────────────────────────┘                    │   │
│ │                                                          │   │
│ │ Found 1 unpublished event                               │   │
│ └─────────────────────────────────────────────────────────┘   │
│                           │                                    │
│                           ▼                                    │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ 2. Publish event to Kafka                               │   │
│ │                                                          │   │
│ │ For each event:                                         │   │
│ │   KafkaTemplate.send(                                   │   │
│ │     topic: "payment.initiated",                         │   │
│ │     key: "550e8400...",        ◄─ same partition        │   │
│ │     value: {payment_id, amount, currency, ...}          │   │
│ │   )                                                     │   │
│ │                                                          │   │
│ │ ✓ Message published to Kafka Topic                      │   │
│ │   "payment.initiated"                                   │   │
│ │   Partition: 0 (determined by key hash)                 │   │
│ └─────────────────────────────────────────────────────────┘   │
│                           │                                    │
│                           ▼                                    │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ 3. Mark as published (if Kafka acks)                    │   │
│ │                                                          │   │
│ │ UPDATE payment_outbox                                   │   │
│ │ SET published = true,                                   │   │
│ │     published_at = NOW()                                │   │
│ │ WHERE id = 1                                            │   │
│ │                                                          │   │
│ │ ✓ Event marked as published                             │   │
│ │   (won't be re-published next cycle)                    │   │
│ │                                                          │   │
│ │ ⚠️ If Kafka publish FAILS:                               │   │
│ │   ├─ published stays false                              │   │
│ │   ├─ Retry in next polling cycle (1 second later)       │   │
│ │   └─ Eventually succeeds when Kafka recovers            │   │
│ └─────────────────────────────────────────────────────────┘   │
│                           │                                    │
│                           ▼                                    │
│          ✓ EVENT PUBLISHED TO KAFKA                           │
│          ✓ AVAILABLE TO ALL SUBSCRIBERS                       │
└───────────────────────────────────────────────────────────────┘
```

---

## STEP 6: SERVICES CONSUME EVENTS FROM KAFKA

### Who Listens to What?

```
T+10ms: EVENT PUBLISHED TO KAFKA
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Kafka Topic: "payment.initiated"
┌─────────────────────────────────────────────────────────────┐
│ Partition 0 (determined by key: 550e8400...)                │
│                                                              │
│ Messages:                                                   │
│ ┌──────────────────────────────────────────────────────┐   │
│ │ Topic: payment.initiated                             │   │
│ │ Key: 550e8400-e29b-41d4-a716-446655440000            │   │
│ │ Value: {                                             │   │
│ │   "payment_id": "550e8400...",                        │   │
│ │   "account_id": "user-123",                           │   │
│ │   "amount": 100.00,                                  │   │
│ │   "currency": "USD"                                  │   │
│ │ }                                                    │   │
│ │ Timestamp: 2026-11-20T14:30:00.010Z                  │   │
│ └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘

MULTIPLE SERVICES SUBSCRIBE (Consumer Groups):

┌─────────────────────────────────┬───────────────────────────┐
│ FRAUD SERVICE                   │ NOTIFICATION SERVICE      │
│ Consumer Group: fraud-service   │ Consumer: notification-svc│
│                                 │                           │
│ @KafkaListener(topics =         │ @KafkaListener(topics =   │
│   "payment.initiated",          │   "payment.initiated",    │
│   groupId = "fraud-service"     │   groupId =               │
│ )                               │   "notification-service"  │
│ void handlePaymentInitiated(    │ )                         │
│   PaymentInitiatedEvent event   │ void handlePaymentEvent(  │
│ ) {                             │   PaymentInitiatedEvent   │
│   // Load customer              │ ) {                       │
│   // Run fraud checks           │   // Send SMS: "Payment   │
│   // Score risk                 │   // of $100 submitted"   │
│ }                               │ }                         │
└─────────────────────────────────┴───────────────────────────┘

                        ┌─────────────────────┐
                        │TRANSACTION HISTORY  │
                        │SERVICE (CQRS)       │
                        │                     │
                        │Consumer Group:      │
                        │transaction-history  │
                        │                     │
                        │Builds denormalized  │
                        │view from events     │
                        └─────────────────────┘


MULTIPLE INSTANCES OF SAME SERVICE:
┌────────────────┐    ┌────────────────┐    ┌────────────────┐
│ Notification   │    │ Notification   │    │ Notification   │
│ Service #1     │    │ Service #2     │    │ Service #3     │
│                │    │                │    │                │
│Consumer Group: │    │Consumer Group: │    │Consumer Group: │
│notification-svc│    │notification-svc│    │notification-svc│
│                │    │                │    │                │
│Partition: 0    │    │Partition: 1    │    │Partition: 2    │
│(offset: 100)   │    │(offset: 100)   │    │(offset: 100)   │
└────────────────┘    └────────────────┘    └────────────────┘
  │                     │                     │
  ├─ Each instance     ├─ Consumes from    ├─ Parallel
  │  consumes          │  different         │ consumption
  │  different         │  partition         │ (load balanced)
  │  partition         │                    │
```

---

## STEP 7: FRAUD SERVICE PROCESSES EVENT

```
T+15ms: FRAUD SERVICE RECEIVES "payment.initiated" EVENT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

┌───────────────────────────────────────────────────────────────┐
│                   FRAUD SERVICE (Port 8082)                   │
│              Kafka Consumer → Risk Scoring Engine              │
│                                                                │
│ RECEIVE EVENT:                                                │
│ Topic: payment.initiated                                      │
│ Message: {                                                    │
│   "payment_id": "550e8400...",                                │
│   "account_id": "user-123",                                   │
│   "amount": 100.00,                                           │
│   "currency": "USD"                                           │
│ }                                                             │
│                                                                │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ FRAUD DETECTION PROCESS                                 │   │
│ │                                                          │   │
│ │ 1. Load customer history:                               │   │
│ │    SELECT * FROM fraud_db.customer_history              │   │
│ │    WHERE account_id = 'user-123'                        │   │
│ │                                                          │   │
│ │    Results:                                             │   │
│ │    ├─ Previous transactions: 50                         │   │
│ │    ├─ Avg amount: $50                                   │   │
│ │    ├─ Current transaction: $100 (2x average) ⚠️          │   │
│ │    ├─ Location: Usually NY, Now NY ✓                    │   │
│ │    └─ Time: Usually 9-5pm, Now 2:30pm ✓                │   │
│ │                                                          │   │
│ │ 2. Calculate risk factors:                              │   │
│ │    amount_risk = 0.15      (high amount vs average)     │   │
│ │    location_risk = 0.00    (same location)              │   │
│ │    time_risk = 0.05        (unusual time)               │   │
│ │    device_risk = 0.00      (known device)               │   │
│ │    velocity_risk = 0.05    (5 txns in 10 min)          │   │
│ │                                                          │   │
│ │ 3. ML Model Risk Score:                                 │   │
│ │    weighted_sum = (                                     │   │
│ │      0.15 * 0.4 +          (amount weight: 40%)         │   │
│ │      0.00 * 0.2 +          (location weight: 20%)       │   │
│ │      0.05 * 0.2 +          (time weight: 20%)           │   │
│ │      0.05 * 0.2             (velocity weight: 20%)       │   │
│ │    ) = 0.07 = 7%                                        │   │
│ │                                                          │   │
│ │ 4. Decision Logic:                                      │   │
│ │    if risk < 0.10: APPROVE                              │   │
│ │    if 0.10 < risk < 0.50: MANUAL_REVIEW                 │   │
│ │    if risk > 0.50: REJECT                               │   │
│ │                                                          │   │
│ │    7% < 10% → APPROVE                                   │   │
│ │                                                          │   │
│ │ ✓ DECISION: APPROVE                                     │   │
│ │ ✓ RISK SCORE: 0.07 (LOW)                                │   │
│ └─────────────────────────────────────────────────────────┘   │
│                           │                                    │
│                           ▼                                    │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ SAVE RESULT & PUBLISH EVENT                             │   │
│ │                                                          │   │
│ │ TRANSACTION:                                            │   │
│ │ ├─ INSERT INTO fraud_db.risk_scores (                   │   │
│ │ │   payment_id, risk_score, decision, ...              │   │
│ │ │ ) VALUES (                                            │   │
│ │ │   '550e8400...', 0.07, 'APPROVE', ...                │   │
│ │ │ )                                                     │   │
│ │ │                                                       │   │
│ │ ├─ INSERT INTO fraud_db.fraud_outbox (                  │   │
│ │ │   payment_id, event_type, payload, ...               │   │
│ │ │ ) VALUES (                                            │   │
│ │ │   '550e8400...', 'payment.fraud_checked',            │   │
│ │ │   '{"payment_id":"550e8400...","decision":"APPROVE"..}│   │
│ │ │ )                                                     │   │
│ │ │                                                       │   │
│ │ └─ COMMIT (both together)                               │   │
│ │                                                          │   │
│ │ ✓ FRAUD CHECK COMPLETE                                  │   │
│ │ ✓ EVENT READY FOR KAFKA                                 │   │
│ └─────────────────────────────────────────────────────────┘   │
│                           │                                    │
│                           ▼                                    │
│          ✓ FRAUD SERVICE PROCESSING COMPLETE                  │
└───────────────────────────────────────────────────────────────┘
```

---

## STEP 8: PAYMENT SERVICE RECEIVES FRAUD RESULT & CALLS ACCOUNT SERVICE

```
T+20ms: PAYMENT SERVICE RECEIVES "payment.fraud_checked" EVENT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

┌───────────────────────────────────────────────────────────────┐
│                   PAYMENT SERVICE (Saga Orchestrator)          │
│              Updates saga state based on fraud result          │
│                                                                │
│ RECEIVE EVENT from Kafka:                                     │
│ Topic: payment.fraud_checked                                  │
│ Message: {                                                    │
│   "payment_id": "550e8400...",                                │
│   "decision": "APPROVE",                                      │
│   "risk_score": 0.07                                          │
│ }                                                             │
│                                                                │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ UPDATE SAGA STATE                                       │   │
│ │                                                          │   │
│ │ Query current payment:                                  │   │
│ │ SELECT * FROM payments                                  │   │
│ │ WHERE id = '550e8400...'                                │   │
│ │                                                          │   │
│ │ Current state: INITIATED                                │   │
│ │ New state: FRAUD_CHECKED                                │   │
│ │                                                          │   │
│ │ UPDATE payments                                         │   │
│ │ SET status = 'FRAUD_CHECKED',                            │   │
│ │     fraud_decision = 'APPROVE',                          │   │
│ │     fraud_score = 0.07                                   │   │
│ │ WHERE id = '550e8400...'                                │   │
│ │                                                          │   │
│ │ ✓ SAGA STATE UPDATED                                    │   │
│ └─────────────────────────────────────────────────────────┘   │
│                           │                                    │
│                           ▼                                    │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ MAKE SYNC CALL TO ACCOUNT SERVICE (Feign HTTP)          │   │
│ │                                                          │   │
│ │ Fraud is approved → NOW debit the account               │   │
│ │                                                          │   │
│ │ Using Feign (synchronous HTTP):                         │   │
│ │ ╔═════════════════════════════════════════════════════╗ │   │
│ │ ║ POST http://account-service:8081/debit              ║ │   │
│ │ ║                                                     ║ │   │
│ │ ║ Headers:                                            ║ │   │
│ │ ║ X-User-Id: user-123 (from gateway)                  ║ │   │
│ │ ║ Idempotency-Key: 550e8400...                        ║ │   │
│ │ ║ Content-Type: application/json                      ║ │   │
│ │ ║                                                     ║ │   │
│ │ ║ Body: {                                             ║ │   │
│ │ ║   "account_id": "user-123",                         ║ │   │
│ │ ║   "amount": 100.00,                                 ║ │   │
│ │ ║   "reference_id": "550e8400...",   ◄─ From payment ║ │   │
│ │ ║   "reference_type": "PAYMENT"                       ║ │   │
│ │ ║ }                                                   ║ │   │
│ │ ╚═════════════════════════════════════════════════════╝ │   │
│ │                                                          │   │
│ │ ⏳ BLOCKING CALL (waits for response)                    │   │
│ │ Timeout: 2000ms (2 seconds)                             │   │
│ └─────────────────────────────────────────────────────────┘   │
│                           │                                    │
│                           │ HTTP goes over network             │
│                           │ to Account Service                 │
│                           ▼                                    │
│                   ACCOUNT SERVICE RECEIVES                     │
│                   (see next section)                           │
└───────────────────────────────────────────────────────────────┘
```

---

## STEP 9: ACCOUNT SERVICE DEBITS ACCOUNT

```
T+25ms: ACCOUNT SERVICE (Port 8081) RECEIVES DEBIT REQUEST
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

┌───────────────────────────────────────────────────────────────┐
│                   ACCOUNT SERVICE (Ledger Manager)             │
│         Handles all account balance changes                    │
│                                                                │
│ RECEIVE FEIGN REQUEST:                                        │
│ POST /debit                                                   │
│ X-User-Id: user-123                                           │
│ Body: {                                                       │
│   "account_id": "user-123",                                   │
│   "amount": 100.00,                                           │
│   "reference_id": "550e8400...",                              │
│   "reference_type": "PAYMENT"                                 │
│ }                                                             │
│                                                                │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ 1. VALIDATE REQUEST                                     │   │
│ │                                                          │   │
│ │ ├─ amount > 0?                    ✓ YES                 │   │
│ │ ├─ account exists?                ✓ YES                 │   │
│ │ └─ reference_id valid?            ✓ YES                 │   │
│ └─────────────────────────────────────────────────────────┘   │
│                           │                                    │
│                           ▼                                    │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ 2. CHECK IDEMPOTENCY                                    │   │
│ │                                                          │   │
│ │ Query idempotency_keys:                                 │   │
│ │ SELECT * FROM idempotency_keys                          │   │
│ │ WHERE key = '550e8400...'                               │   │
│ │                                                          │   │
│ │ NOT FOUND → first time                                  │   │
│ │ Proceed to debit                                        │   │
│ └─────────────────────────────────────────────────────────┘   │
│                           │                                    │
│                           ▼                                    │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ 3. ATOMIC TRANSACTION (Debit with Outbox)               │   │
│ │                                                          │   │
│ │ ╔═════════════════════════════════════════════════════╗ │   │
│ │ ║ TRANSACTION START                                   ║ │   │
│ │ ║                                                     ║ │   │
│ │ ║ STEP 1: Get current balance (with lock)             ║ │   │
│ │ ║ SELECT balance FROM accounts                        ║ │   │
│ │ ║ WHERE id = 'user-123'                               ║ │   │
│ │ ║ FOR UPDATE        ◄─ Row-level lock (no other       ║ │   │
│ │ ║                     txn can modify)                  ║ │   │
│ │ ║                                                     ║ │   │
│ │ ║ Result: balance = 1000.00                            ║ │   │
│ │ ║                                                     ║ │   │
│ │ ║ STEP 2: Verify sufficient balance                    ║ │   │
│ │ ║ if 1000.00 >= 100.00 ✓ YES                           ║ │   │
│ │ ║                                                     ║ │   │
│ │ ║ STEP 3: Create ledger entry (audit trail)            ║ │   │
│ │ ║ INSERT INTO ledger (                                 ║ │   │
│ │ ║   account_id, entry_type, amount,                    ║ │   │
│ │ ║   reference_id, description, ...                     ║ │   │
│ │ ║ ) VALUES (                                           ║ │   │
│ │ ║   'user-123', 'DEBIT', -100.00,                       ║ │   │
│ │ ║   '550e8400...', 'Payment for coffee', ...           ║ │   │
│ │ ║ )                                                    ║ │   │
│ │ ║ ✓ Ledger entry created (immutable record)            ║ │   │
│ │ ║                                                     ║ │   │
│ │ ║ STEP 4: Update account balance                       ║ │   │
│ │ ║ UPDATE accounts                                      ║ │   │
│ │ ║ SET balance = balance - 100.00,                       ║ │   │
│ │ ║     last_modified = NOW()                            ║ │   │
│ │ ║ WHERE id = 'user-123'                                ║ │   │
│ │ ║                                                     ║ │   │
│ │ ║ Calculation: 1000.00 - 100.00 = 900.00               ║ │   │
│ │ ║ ✓ Balance updated                                    ║ │   │
│ │ ║                                                     ║ │   │
│ │ ║ STEP 5: Create event in outbox                       ║ │   │
│ │ ║ INSERT INTO account_outbox (                         ║ │   │
│ │ ║   reference_id, event_type, payload, ...             ║ │   │
│ │ ║ ) VALUES (                                           ║ │   │
│ │ ║   '550e8400...', 'account.debited',                  ║ │   │
│ │ ║   '{"account_id":"user-123","amount":100...}', ...  ║ │   │
│ │ ║ )                                                    ║ │   │
│ │ ║ ✓ Event created for Kafka                            ║ │   │
│ │ ║                                                     ║ │   │
│ │ ║ STEP 6: Store idempotency key                        ║ │   │
│ │ ║ INSERT INTO idempotency_keys (key, response, ...)    ║ │   │
│ │ ║                                                     ║ │   │
│ │ ║ COMMIT (all 4 operations succeed or all fail)        ║ │   │
│ │ ║ ✓ ALL SUCCEED (atomic)                               ║ │   │
│ │ ╚═════════════════════════════════════════════════════╝ │   │
│ │                                                          │   │
│ │ ✓ DEBIT COMPLETE                                        │   │
│ │ ✓ BALANCE: 900.00 (down from 1000.00)                   │   │
│ │ ✓ EVENT: account.debited (in outbox)                    │   │
│ └─────────────────────────────────────────────────────────┘   │
│                           │                                    │
│                           ▼                                    │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ 4. RETURN RESPONSE TO PAYMENT SERVICE                   │   │
│ │                                                          │   │
│ │ HTTP 200 OK                                             │   │
│ │ {                                                       │   │
│ │   "account_id": "user-123",                             │   │
│ │   "amount": -100.00,                                    │   │
│ │   "new_balance": 900.00,                                │   │
│ │   "status": "SUCCESS"                                   │   │
│ │ }                                                       │   │
│ │                                                          │   │
│ │ ✓ RESPONSE SENT TO PAYMENT SERVICE                      │   │
│ └─────────────────────────────────────────────────────────┘   │
└───────────────────────────────────────────────────────────────┘
```

---

## STEP 10: PAYMENT SERVICE MARKS PAYMENT COMPLETED

```
T+30ms: PAYMENT SERVICE RECEIVES DEBIT RESPONSE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

┌───────────────────────────────────────────────────────────────┐
│                   PAYMENT SERVICE                              │
│             Final stage of saga orchestration                  │
│                                                                │
│ RECEIVE RESPONSE from Account Service:                        │
│ {                                                             │
│   "account_id": "user-123",                                   │
│   "amount": -100.00,                                          │
│   "new_balance": 900.00,                                      │
│   "status": "SUCCESS"                                         │
│ }                                                             │
│                                                                │
│ ✓ DEBIT SUCCESSFUL                                            │
│ → Saga can proceed to completion                              │
│                                                                │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ UPDATE PAYMENT TO COMPLETED                             │   │
│ │                                                          │   │
│ │ UPDATE payments                                         │   │
│ │ SET status = 'COMPLETED',                               │   │
│ │     completed_at = NOW(),                               │   │
│ │     final_balance = 900.00                              │   │
│ │ WHERE id = '550e8400...'                                │   │
│ │                                                          │   │
│ │ ✓ SAGA STATE: COMPLETED                                 │   │
│ └─────────────────────────────────────────────────────────┘   │
│                           │                                    │
│                           ▼                                    │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ CREATE FINAL EVENT IN OUTBOX                            │   │
│ │                                                          │   │
│ │ TRANSACTION:                                            │   │
│ │ ├─ UPDATE payment status to COMPLETED                   │   │
│ │ ├─ INSERT INTO payment_outbox (                          │   │
│ │ │   payment_id, event_type, payload, ...                │   │
│ │ │ ) VALUES (                                            │   │
│ │ │   '550e8400...', 'payment.completed',                 │   │
│ │ │   '{"payment_id":"550e8400...","amount":100...}', ... │   │
│ │ │ )                                                     │   │
│ │ └─ COMMIT                                               │   │
│ │                                                          │   │
│ │ ✓ EVENT: payment.completed (in outbox)                  │   │
│ └─────────────────────────────────────────────────────────┘   │
└───────────────────────────────────────────────────────────────┘
```

---

## STEP 11: MULTIPLE SERVICES PROCESS FINAL EVENT

```
T+35ms: Outbox poller publishes "payment.completed" event
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Kafka Topic: "payment.completed"
Message: {
  "payment_id": "550e8400...",
  "amount": 100.00,
  "account_id": "user-123",
  "status": "COMPLETED"
}

SUBSCRIBERS PROCESS IN PARALLEL:

┌─────────────────────────────────────────────────────────────┐
│ NOTIFICATION SERVICE receives event                          │
│                                                              │
│ → Send SMS:                                                 │
│   "Payment of $100 successful.                              │
│    New balance: $900.00"                                    │
│                                                              │
│ → Send Email:                                               │
│   Subject: "Payment Confirmation"                           │
│   Body: "Your payment of $100 has been completed..."       │
│                                                              │
│ ✓ NOTIFICATION SENT TO USER                                 │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ TRANSACTION HISTORY SERVICE (CQRS) receives event           │
│                                                              │
│ Builds denormalized view:                                   │
│ INSERT INTO transactions_view (                              │
│   payment_id, account_id, amount, type,                     │
│   status, created_at, completed_at                          │
│ ) VALUES (                                                  │
│   '550e8400...', 'user-123', 100.00, 'PAYMENT',            │
│   'COMPLETED', 2026-11-20T14:30:00Z, 2026-11-20T14:30:03Z  │
│ )                                                           │
│                                                              │
│ User can now query:                                         │
│ GET /api/v1/transactions/550e8400...                        │
│ ✓ FAST RESPONSE (pre-aggregated data)                      │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ OTHER SUBSCRIBERS (future):                                 │
│ ├─ Analytics Service: Track trends                          │
│ ├─ Compliance Service: Audit logs                           │
│ ├─ Recommendation Engine: Personalized offers               │
│ └─ ...                                                       │
│                                                              │
│ All notified instantly when event published                 │
└─────────────────────────────────────────────────────────────┘
```

---

## STEP 12: CLIENT QUERIES FINAL STATUS

```
T+40ms: USER OPENS APP & CHECKS PAYMENT STATUS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

┌────────────────────────────────────────────────────────────┐
│ Mobile App                                                  │
│ [Payment Details Screen]                                    │
│ [Refresh Button]  ◄─ User taps                              │
│                                                              │
│ Sends HTTP GET request:                                     │
│ GET /api/v1/payments/550e8400-e29b-41d4-a716-446655440000  │
│ Authorization: Bearer <JWT token>                           │
└────────────────────────────────────────────────────────────┘
              │
              │ Routes via API Gateway
              │ (JWT validation, etc.)
              ▼
        ┌──────────────────────────────┐
        │ PAYMENT SERVICE API          │
        │ Query from database:         │
        │                              │
        │ SELECT * FROM payments       │
        │ WHERE id = '550e8400...'     │
        │                              │
        │ Result:                      │
        │ {                            │
        │   "id": "550e8400...",       │
        │   "account_id": "user-123",  │
        │   "amount": 100.00,          │
        │   "currency": "USD",         │
        │   "status": "COMPLETED", ◄─  │
        │   "created_at": "2026-11-20  │
        │                 T14:30:00Z", │
        │   "completed_at": "2026-11-20│
        │                 T14:30:03Z"  │
        │ }                            │
        └──────────────────────────────┘
              │
              │ Return response
              ▼
┌────────────────────────────────────────────────────────────┐
│ Mobile App Shows:                                           │
│                                                              │
│ ┌──────────────────────────────────────┐                    │
│ │ ✓ Payment Successful                 │                    │
│ │                                       │                    │
│ │ Amount: $100.00                       │                    │
│ │ Date: Nov 20, 2:30 PM                │                    │
│ │ Status: Completed                    │                    │
│ │ Transaction ID: 550e8400...          │                    │
│ │                                       │                    │
│ │ New Account Balance: $900.00          │                    │
│ │                                       │                    │
│ │ [View Details] [Share Receipt] [Home]│                    │
│ └──────────────────────────────────────┘                    │
│                                                              │
│ ✓ USER SEES PAYMENT IS COMPLETE                              │
│ ✓ SMS & EMAIL ALREADY RECEIVED                               │
└────────────────────────────────────────────────────────────┘
```

---

## Complete Timeline (0-55ms)

```
TIME   ACTOR                      ACTION
════════════════════════════════════════════════════════════════════════
0ms    CLIENT                     POST /payments/create with JWT token
                                  Authorization: Bearer <JWT>
                                  Idempotency-Key: 550e8400...

5ms    API GATEWAY                ✓ Validate JWT signature
       (JwtAuthenticationFilter)   ✓ Check expiration
                                  ✓ Extract X-User-Id header
                                  ✓ Rate limiting check (100 req/min)
                                  → Route to payment-service

10ms   PAYMENT SERVICE            ✓ Receive request
       (Port 8083)                ✓ Validate input (amount > 0)
                                  ✓ Check idempotency key
                                  
                                  ATOMIC TRANSACTION 1:
                                  └─ INSERT payment (INITIATED)
                                  └─ INSERT into outbox
                                  └─ COMMIT
                                  
                                  Return 201 Created to client

15ms   CLIENT RECEIVES            ✓ Payment ID: 550e8400...
       RESPONSE                   ✓ Status: INITIATED

20ms   OUTBOX POLLER              Publish to Kafka: payment.initiated
       (Scheduled)

25ms   FRAUD SERVICE              ✓ Receive event
       (Port 8082)                → Load customer history
                                  → Calculate fraud score (7%)
                                  → Decision: APPROVE
                                  
                                  ATOMIC TRANSACTION 2:
                                  └─ INSERT risk_scores
                                  └─ INSERT into outbox
                                  └─ COMMIT
                                  
                                  Outbox publishes: payment.fraud_checked

30ms   PAYMENT SERVICE            ✓ Receive fraud approval
       (Kafka consumer)           → Update saga state
                                  → Call Account Service (Feign HTTP)

35ms   ACCOUNT SERVICE            ✓ Receive debit request
       (Port 8081)                ✓ Verify balance (1000 > 100)
                                  
                                  ATOMIC TRANSACTION 3:
                                  └─ INSERT ledger (DEBIT)
                                  └─ UPDATE balance (900)
                                  └─ INSERT into outbox
                                  └─ COMMIT
                                  
                                  Return 200 OK {new_balance: 900}

40ms   PAYMENT SERVICE            ✓ Receive debit response
                                  → Update status to COMPLETED
                                  
                                  ATOMIC TRANSACTION 4:
                                  └─ UPDATE payment status
                                  └─ INSERT into outbox
                                  └─ COMMIT

45ms   OUTBOX POLLER              Publish to Kafka: payment.completed

50ms   NOTIFICATION SERVICE       ✓ Receive completion event
       TRANSACTION HISTORY SVC    → Send SMS/Email to user
       (CQRS)                     → Build denormalized view

55ms   PAYMENT FULLY PROCESSED    ✓ All services notified
                                  ✓ User receives confirmation
                                  ✓ Ready for queries
════════════════════════════════════════════════════════════════════════
```

---

## Security Summary

```
Entry Point: Keycloak (8090)
├─ User authenticates with credentials
├─ Keycloak validates (password hash check)
├─ Issues JWT token (signed with private key)
└─ Token contains claims (user_id, roles, expiration)

API Gateway (8080)
├─ Receives request with JWT in Authorization header
├─ Validates JWT signature (using Keycloak public key)
├─ Checks token expiration
├─ Extracts user info into X-User-Id header
├─ Rate limits per user (100 req/min)
└─ Routes to backend service

Backend Services (8081-8085)
├─ Trust X-User-Id header (from gateway)
├─ All database operations isolated per user
├─ Feign calls include tracing headers
└─ Events published for auditing
```

---

## Why This Architecture?

```
KEYCLOAK (Centralized Auth)
✓ Single source of truth for users
✓ Secure password hashing
✓ Token-based (stateless)
✓ No passwords sent to backend

API GATEWAY (Single Entry Point)
✓ Central JWT validation
✓ Rate limiting protection
✓ Routing all requests
✓ Security policies in one place

SAGA ORCHESTRATION (Distributed Transactions)
✓ Coordinates across services
✓ Handles partial failures
✓ Ensures consistency

OUTBOX PATTERN (Reliability)
✓ Atomic DB + Kafka publish
✓ No data loss on failures
✓ Eventually consistent

EVENT-DRIVEN (Loose Coupling)
✓ Services don't depend on each other
✓ Easy to add new subscribers
✓ Scaling independent services
```

This is the complete journey from user authentication through payment completion!
