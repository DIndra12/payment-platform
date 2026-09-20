package com.payments.platform.apigateway.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.platform.apigateway.exception.JwtValidationException;
import io.jsonwebtoken.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * Keycloak Token Validator
 *
 * Validates JWT tokens from Keycloak by:
 * 1. Fetching public key from Keycloak's JWKS endpoint
 * 2. Validating token signature using public key
 * 3. Checking token expiration
 * 4. Extracting claims (sub, roles, etc.)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KeycloakTokenValidator {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://keycloak:8080/realms/payment-platform}")
    private String issuerUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:http://keycloak:8080/realms/payment-platform/protocol/openid-connect/certs}")
    private String jwkSetUri;

    @Value("${jwt.secret:your-super-secret-key-that-is-at-least-32-characters-long-for-hs256}")
    private String fallbackSecret;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Map<String, PublicKey> cachedKeys;
    private long keysCacheTime;
    private static final long CACHE_DURATION = 3600000; // 1 hour

    public Claims validateToken(String token) {
        try {
            // Try to validate with Keycloak public key first
            Claims claims = validateWithKeycloak(token);
            log.debug("Token validated successfully with Keycloak");
            return claims;

        } catch (Exception keycloakException) {
            log.debug("Keycloak validation failed ({}), trying fallback...", keycloakException.getMessage());

            // Fallback to local secret validation (useful for testing)
            try {
                return validateWithLocalSecret(token);
            } catch (Exception e) {
                log.warn("Token validation failed: {}", e.getMessage());
                throw new JwtValidationException("Token validation failed", e);
            }
        }
    }

    private Claims validateWithKeycloak(String token) {
        try {
            // Decode header to get kid (key ID)
            Jws<Claims> jws = Jwts.parserBuilder()
                    .requireIssuer(issuerUri)
                    .build()
                    .parseClaimsJws(token);

            String kid = jws.getHeader().getKeyId();
            log.debug("Token kid: {}", kid);

            // Get public key from Keycloak
            PublicKey publicKey = getPublicKey(kid);

            // Validate with public key
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .requireIssuer(issuerUri)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            // Check expiration
            if (claims.getExpiration().before(new Date())) {
                throw new JwtValidationException("Token has expired");
            }

            log.debug("JWT token validated successfully for user: {}", claims.getSubject());
            return claims;

        } catch (ExpiredJwtException e) {
            throw new JwtValidationException("Token has expired", e);
        } catch (SignatureException e) {
            throw new JwtValidationException("Invalid token signature", e);
        } catch (MalformedJwtException e) {
            throw new JwtValidationException("Malformed token format", e);
        } catch (Exception e) {
            throw new JwtValidationException("Token validation with Keycloak failed: " + e.getMessage(), e);
        }
    }

    private Claims validateWithLocalSecret(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(fallbackSecret.getBytes())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            if (claims.getExpiration().before(new Date())) {
                throw new JwtValidationException("Token has expired");
            }

            log.debug("JWT token validated with local secret for user: {}", claims.getSubject());
            return claims;

        } catch (ExpiredJwtException e) {
            throw new JwtValidationException("Token has expired", e);
        } catch (SignatureException e) {
            throw new JwtValidationException("Invalid token signature", e);
        } catch (MalformedJwtException e) {
            throw new JwtValidationException("Malformed token format", e);
        } catch (IllegalArgumentException e) {
            throw new JwtValidationException("Token claims are empty", e);
        }
    }

    private PublicKey getPublicKey(String kid) throws Exception {
        // Check cache
        if (cachedKeys != null && System.currentTimeMillis() - keysCacheTime < CACHE_DURATION) {
            PublicKey cachedKey = cachedKeys.get(kid);
            if (cachedKey != null) {
                log.debug("Using cached public key for kid: {}", kid);
                return cachedKey;
            }
        }

        // Fetch JWKS from Keycloak
        log.debug("Fetching JWKS from: {}", jwkSetUri);
        String jwksJson = new String(new URL(jwkSetUri).openStream().readAllBytes(), StandardCharsets.UTF_8);
        JsonNode jwks = objectMapper.readTree(jwksJson);

        // Find key with matching kid
        for (JsonNode key : jwks.get("keys")) {
            if (key.get("kid").asText().equals(kid)) {
                PublicKey publicKey = parsePublicKey(key);
                if (cachedKeys == null) {
                    cachedKeys = new java.util.HashMap<>();
                }
                cachedKeys.put(kid, publicKey);
                keysCacheTime = System.currentTimeMillis();
                return publicKey;
            }
        }

        throw new JwtValidationException("Key not found: " + kid);
    }

    private PublicKey parsePublicKey(JsonNode keyNode) throws Exception {
        String kty = keyNode.get("kty").asText();
        String n = keyNode.get("n").asText();
        String e = keyNode.get("e").asText();

        if (!"RSA".equals(kty)) {
            throw new JwtValidationException("Unsupported key type: " + kty);
        }

        // Decode base64url values
        byte[] nBytes = Base64.getUrlDecoder().decode(n);
        byte[] eBytes = Base64.getUrlDecoder().decode(e);

        java.math.BigInteger modulus = new java.math.BigInteger(1, nBytes);
        java.math.BigInteger exponent = new java.math.BigInteger(1, eBytes);

        java.security.spec.RSAPublicKeySpec spec = new java.security.spec.RSAPublicKeySpec(modulus, exponent);
        KeyFactory factory = KeyFactory.getInstance("RSA");

        return factory.generatePublic(spec);
    }
}
