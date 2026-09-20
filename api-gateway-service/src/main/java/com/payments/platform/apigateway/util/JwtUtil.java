package com.payments.platform.apigateway.util;

import com.payments.platform.apigateway.exception.JwtValidationException;
import io.jsonwebtoken.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * JWT Token Utility
 *
 * Handles JWT token operations:
 * 1. Validate token signature and expiration
 * 2. Extract claims (user info) from token
 * 3. Generate new tokens (for testing)
 *
 * JWT Structure:
 *   Header.Payload.Signature
 *
 * Header: {"typ":"JWT","alg":"HS256"}
 * Payload: {"sub":"user123","exp":1234567890,"iat":1234567890,"roles":["user"]}
 * Signature: HMACSHA256(header.payload, SECRET_KEY)
 *
 * How validation works:
 * 1. Extract header.payload.signature from token
 * 2. Recalculate: HMACSHA256(header.payload, SECRET_KEY)
 * 3. Compare calculated signature with provided signature
 * 4. If they match: token is valid (wasn't forged)
 * 5. If they don't match: token is invalid (was forged)
 * 6. Check exp claim: is it >= current time?
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtUtil {

    @Value("${jwt.secret:your-secret-key-min-32-chars-for-production}")
    private String secret;

    @Value("${jwt.expiration:3600000}")
    private long expiration;

    /**
     * Validate JWT token
     *
     * Steps:
     * 1. Parse token (verify signature using secret)
     * 2. Check expiration
     * 3. Extract and return claims
     *
     * Throws JwtValidationException if:
     * - Signature is invalid (token was forged)
     * - Token is expired
     * - Token is malformed
     *
     * @param token JWT token from Authorization header (without "Bearer " prefix)
     * @return Claims (user info) if token is valid
     * @throws JwtValidationException if validation fails
     */
    public Claims validateToken(String token) {
        try {
            // Parse token with secret key
            // This validates the signature automatically
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secret.getBytes())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            // Check expiration
            if (claims.getExpiration().before(new Date())) {
                throw new JwtValidationException("Token has expired");
            }

            log.debug("JWT token validated for user: {}", claims.getSubject());
            return claims;

        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
            throw new JwtValidationException("Token has expired", e);

        } catch (SignatureException e) {
            log.warn("JWT signature validation failed: {}", e.getMessage());
            throw new JwtValidationException("Invalid token signature (token was forged)", e);

        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT token: {}", e.getMessage());
            throw new JwtValidationException("Malformed token format", e);

        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT: {}", e.getMessage());
            throw new JwtValidationException("Unsupported token format", e);

        } catch (IllegalArgumentException e) {
            log.warn("JWT claims empty: {}", e.getMessage());
            throw new JwtValidationException("Token claims are empty", e);
        }
    }

    /**
     * Extract user ID from token
     *
     * @param claims Claims extracted from validated token
     * @return User ID (subject claim)
     */
    public String getUserId(Claims claims) {
        return claims.getSubject();
    }

    /**
     * Extract user roles from token
     *
     * @param claims Claims extracted from validated token
     * @return List of roles (or empty list if not present)
     */
    @SuppressWarnings("unchecked")
    public List<String> getRoles(Claims claims) {
        Object rolesObj = claims.get("roles");
        if (rolesObj instanceof List) {
            return (List<String>) rolesObj;
        }
        return Collections.emptyList();
    }

    /**
     * Generate JWT token (for testing purposes)
     *
     * @param userId Subject claim (user ID)
     * @param roles User roles
     * @return Signed JWT token
     */
    public String generateToken(String userId, List<String> roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", roles);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(SignatureAlgorithm.HS256, secret.getBytes())
                .compact();
    }
}
