package com.payments.platform.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway Application Entry Point
 *
 * This is the main class that starts the API Gateway service.
 * The @SpringBootApplication annotation enables:
 * - Component scanning for @Service, @Controller, @Repository
 * - Auto-configuration of Spring features
 * - Default configuration loading from application.yml
 *
 * When this starts, Spring will:
 * 1. Load configuration from application.yml
 * 2. Create beans for all @Service/@Component classes
 * 3. Initialize Spring Cloud Gateway routes
 * 4. Start the embedded web server on port 8080
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
