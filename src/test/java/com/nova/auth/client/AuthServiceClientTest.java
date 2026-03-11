package com.nova.auth.client;

import com.nova.auth.client.config.AuthClientAutoConfiguration;
import com.nova.auth.client.config.AuthClientProperties;
import com.nova.auth.client.dto.TokenValidationResponse;
import com.nova.auth.client.dto.UserProfileResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import reactor.test.StepVerifier;

import java.io.IOException;

@SpringBootTest(classes = AuthClientAutoConfiguration.class)
class AuthServiceClientTest {

    static MockWebServer mockServer;

    @Autowired
    AuthServiceClient authServiceClient;

    @BeforeAll
    static void startMockServer() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
    }

    @AfterAll
    static void stopMockServer() throws IOException {
        mockServer.shutdown();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("nova.auth-client.url",
                () -> "http://localhost:" + mockServer.getPort());
    }

    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("validateToken returns valid=true on 200 response")
    void validateToken_valid() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""
                        {"valid":true,"userId":1,"username":"john","email":"john@example.com"}
                        """));

        StepVerifier.create(authServiceClient.validateToken("dummy.jwt.token"))
                .expectNextMatches(r -> r.isValid()
                        && "john".equals(r.getUsername())
                        && r.getUserId() == 1L)
                .verifyComplete();
    }

    @Test
    @DisplayName("validateToken returns valid=false on 401 response")
    void validateToken_invalid() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""
                        {"valid":false,"reason":"Token is invalid or expired"}
                        """));

        StepVerifier.create(authServiceClient.validateToken("bad.token"))
                .expectNextMatches(r -> !r.isValid()
                        && "Token is invalid or expired".equals(r.getReason()))
                .verifyComplete();
    }

    @Test
    @DisplayName("getUserProfile returns profile on success")
    void getUserProfile_success() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""
                        {"userId":1,"username":"john","email":"john@example.com",
                         "firstName":"John","lastName":"Doe","status":"ACTIVE"}
                        """));

        StepVerifier.create(authServiceClient.getUserProfile("dummy.jwt.token"))
                .expectNextMatches(u -> "john".equals(u.getUsername())
                        && "ACTIVE".equals(u.getStatus()))
                .verifyComplete();
    }

    @Test
    @DisplayName("validateToken falls back gracefully when auth-service is down")
    void validateToken_serviceDown() {
        // Simulate connection refused by using a port nothing listens on
        AuthClientProperties props = new AuthClientProperties();
        props.setUrl("http://localhost:19999");  // nothing there
        props.setConnectTimeoutMs(500);
        props.setReadTimeoutMs(500);

        AuthServiceClient brokenClient = new AuthServiceClient(props);

        StepVerifier.create(brokenClient.validateToken("any.token"))
                .expectNextMatches(r -> !r.isValid()
                        && r.getReason() != null
                        && r.getReason().startsWith("Auth service unavailable"))
                .verifyComplete();
    }
}

