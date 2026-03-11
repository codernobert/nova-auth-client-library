package com.nova.auth.client.config;

import com.nova.auth.client.AuthServiceClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Spring Boot Auto-Configuration for the nova-auth-client library.
 *
 * This class is picked up automatically via
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 * when the library is on the classpath.
 *
 * The consumer service just needs ONE property:
 *   nova.auth-client.url=http://nova-auth-service:8081
 *
 * Everything else (timeouts, bean wiring) is handled here.
 */
@AutoConfiguration
@ConditionalOnClass(WebClient.class)
@EnableConfigurationProperties(AuthClientProperties.class)
public class AuthClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean   // allows consumer to override if needed
    public AuthServiceClient authServiceClient(AuthClientProperties properties) {
        return new AuthServiceClient(properties);
    }
}

