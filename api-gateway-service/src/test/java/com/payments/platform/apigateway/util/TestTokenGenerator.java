package com.payments.platform.apigateway.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.util.*;

public class TestTokenGenerator {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256";

    public static String generateToken(String userId, List<String> roles) {
        return generateToken(userId, roles, 3600000);
    }

    public static String generateToken(String userId, List<String> roles, long expirationMs) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", roles);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(SignatureAlgorithm.HS256, SECRET.getBytes())
                .compact();
    }

    public static String generateToken(String userId, String... roles) {
        return generateToken(userId, Arrays.asList(roles));
    }

    public static String generateExpiredToken(String userId) {
        return generateToken(userId, Collections.singletonList("USER"), -1000);
    }
}
