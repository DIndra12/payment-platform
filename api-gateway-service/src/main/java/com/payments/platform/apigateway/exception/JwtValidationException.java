package com.payments.platform.apigateway.exception;

/**
 * Exception thrown when JWT token validation fails
 *
 * This could be due to:
 * - Invalid signature (token was forged or corrupted)
 * - Expired token (exp claim is in the past)
 * - Malformed token (doesn't have expected format)
 * - Missing required claims (sub, exp, iat, etc.)
 *
 * When this is thrown, gateway returns 401 Unauthorized
 */
public class JwtValidationException extends RuntimeException {

    public JwtValidationException(String message) {
        super(message);
    }

    public JwtValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
