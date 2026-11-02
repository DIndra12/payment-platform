package com.payments.platform.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Spring Security setup for the gateway.
 *
 * Authentication is done per route by {@link com.payments.platform.apigateway.filter.JwtAuthenticationFilter},
 * which validates the Keycloak bearer token. Without this class Spring Boot's default security
 * also applies: it demands HTTP Basic credentials (so /actuator/health returned 401) and a CSRF
 * token on every POST (so token-less POSTs such as account creation returned 403).
 * This is a stateless bearer-token API, so CSRF, Basic and form login are turned off here.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .build();
    }
}
