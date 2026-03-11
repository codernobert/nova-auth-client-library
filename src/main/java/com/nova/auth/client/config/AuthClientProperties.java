package com.nova.auth.client.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for the auth-service client.
 *
 * Bind in any consumer service's application.properties:
 *
 *   nova.auth-client.url=http://nova-auth-service:8081
 *   nova.auth-client.connect-timeout-ms=3000
 *   nova.auth-client.read-timeout-ms=5000
 */
@Data
@ConfigurationProperties(prefix = "nova.auth-client")
public class AuthClientProperties {

    /**
     * Base URL of the running nova-auth-service instance.
     * Example: http://localhost:8081  or  http://nova-auth-service:8081
     */
    private String url = "http://localhost:8081";

    /** HTTP connection timeout in milliseconds. */
    private int connectTimeoutMs = 3000;

    /** HTTP read timeout in milliseconds. */
    private int readTimeoutMs = 5000;
}

