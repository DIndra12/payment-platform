package com.payments.platform.apigateway.filter;

import com.payments.platform.apigateway.exception.JwtValidationException;
import com.payments.platform.apigateway.util.KeycloakTokenValidator;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * JWT Authentication Filter
 *
 * This filter intercepts EVERY request to the gateway and:
 * 1. Extracts JWT token from Authorization header
 * 2. Validates token signature and expiration
 * 3. Extracts user info (ID, roles)
 * 4. Adds user info to request headers for downstream services
 * 5. Returns 401 Unauthorized if token is invalid
 *
 * Flow for each request:
 *
 *   Request arrives: GET /api/v1/accounts/123 with Authorization: Bearer <token>
 *         ↓
 *   Filter checks: Is Authorization header present?
 *         ↓ YES
 *   Extract token: "Bearer xyz123" → "xyz123"
 *         ↓
 *   Validate signature: Token hasn't been forged?
 *         ↓ VALID
 *   Validate expiration: Token hasn't expired?
 *         ↓ NOT EXPIRED
 *   Extract user ID: claims.getSubject() → "user123"
 *         ↓
 *   Add to headers: X-User-Id: user123
 *         ↓
 *   Forward request to account-service
 *
 * If any validation fails:
 *         ↓
 *   Return: 401 Unauthorized
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    @Autowired
    private KeycloakTokenValidator tokenValidator;

    public JwtAuthenticationFilter() {
        super(Config.class);
    }

    /**
     * This method is called for each request
     *
     * @param config Filter configuration
     * @return GatewayFilter that processes the request
     */
    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            try {
                // 1. Get request headers
                HttpHeaders headers = exchange.getRequest().getHeaders();

                // 2. Extract Authorization header
                String authHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);
                if (authHeader == null || authHeader.isBlank()) {
                    log.warn("Missing Authorization header");
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }

                // 3. Extract token from "Bearer <token>" format
                if (!authHeader.startsWith("Bearer ")) {
                    log.warn("Invalid Authorization header format");
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }

                String token = authHeader.substring(7);

                // 4. Validate token (this throws JwtValidationException if invalid)
                Claims claims = tokenValidator.validateToken(token);

                // 5. Extract user info
                String userId = claims.getSubject();
                var roles = extractRoles(claims);

                log.debug("Request authenticated for user: {}", userId);

                // 6. Add user info to request headers for downstream services
                exchange.getRequest().mutate()
                        .header("X-User-Id", userId)
                        .header("X-User-Roles", String.join(",", roles))
                        .build();

                // 7. Continue to next filter/service
                return chain.filter(exchange);

            } catch (JwtValidationException e) {
                // Token validation failed
                log.warn("JWT validation failed: {}", e.getMessage());
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();

            } catch (Exception e) {
                // Unexpected error
                log.error("Unexpected error in JWT filter", e);
                exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                return exchange.getResponse().setComplete();
            }
        };
    }

    private java.util.List<String> extractRoles(Claims claims) {
        Object rolesObj = claims.get("roles");
        if (rolesObj instanceof java.util.List) {
            return (java.util.List<String>) rolesObj;
        }
        return java.util.Collections.emptyList();
    }

    // Configuration class (empty for now, can be extended later for dynamic config)
    public static class Config {
    }
}
