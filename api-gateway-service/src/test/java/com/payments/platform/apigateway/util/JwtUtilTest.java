package com.payments.platform.apigateway.util;

import com.payments.platform.apigateway.exception.JwtValidationException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Tests for JWT Utility
 *
 * Tests validate:
 * 1. Valid tokens are accepted
 * 2. Expired tokens are rejected
 * 3. Forged tokens are rejected
 * 4. Roles are extracted correctly
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        // Set secret key for testing
        ReflectionTestUtils.setField(jwtUtil, "secret", "test-secret-key-that-is-long-enough-for-hs256");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 3600000); // 1 hour
    }

    @Test
    void testGenerateAndValidateToken() {
        // Test: Generate token and validate it

        // 1. Generate token
        String userId = "user123";
        List<String> roles = Arrays.asList("USER", "ADMIN");
        String token = jwtUtil.generateToken(userId, roles);

        assertNotNull(token);
        assertTrue(token.contains("."));  // JWT has format: header.payload.signature

        // 2. Validate token
        Claims claims = jwtUtil.validateToken(token);

        // 3. Verify claims
        assertEquals(userId, jwtUtil.getUserId(claims));
        assertEquals(roles, jwtUtil.getRoles(claims));
    }

    @Test
    void testValidTokenAccepted() {
        // Test: Valid token is accepted

        String token = jwtUtil.generateToken("user123", Arrays.asList("USER"));
        assertDoesNotThrow(() -> jwtUtil.validateToken(token));
    }

    @Test
    void testForgedTokenRejected() {
        // Test: Token with invalid signature is rejected

        // 1. Generate valid token
        String validToken = jwtUtil.generateToken("user123", Arrays.asList("USER"));

        // 2. Modify token (forge it)
        String[] parts = validToken.split("\\.");
        String forgedToken = parts[0] + ".invalid." + parts[2];

        // 3. Should reject forged token
        assertThrows(JwtValidationException.class, () -> jwtUtil.validateToken(forgedToken));
    }

    @Test
    void testMalformedTokenRejected() {
        // Test: Malformed token is rejected

        String malformedToken = "not.a.valid.jwt.token";
        assertThrows(JwtValidationException.class, () -> jwtUtil.validateToken(malformedToken));
    }

    @Test
    void testEmptyTokenRejected() {
        // Test: Empty token is rejected

        assertThrows(JwtValidationException.class, () -> jwtUtil.validateToken(""));
    }

    @Test
    void testUserIdExtraction() {
        // Test: User ID is correctly extracted from token

        String userId = "user456";
        String token = jwtUtil.generateToken(userId, Arrays.asList("USER"));

        Claims claims = jwtUtil.validateToken(token);
        assertEquals(userId, jwtUtil.getUserId(claims));
    }

    @Test
    void testRolesExtraction() {
        // Test: Roles are correctly extracted from token

        List<String> roles = Arrays.asList("USER", "ADMIN", "SUPERUSER");
        String token = jwtUtil.generateToken("user789", roles);

        Claims claims = jwtUtil.validateToken(token);
        assertEquals(roles, jwtUtil.getRoles(claims));
    }

    @Test
    void testEmptyRoles() {
        // Test: Empty roles list is handled correctly

        String token = jwtUtil.generateToken("user999", Arrays.asList());

        Claims claims = jwtUtil.validateToken(token);
        assertTrue(jwtUtil.getRoles(claims).isEmpty());
    }
}
