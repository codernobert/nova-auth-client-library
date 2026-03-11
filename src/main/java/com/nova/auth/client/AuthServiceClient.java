package com.nova.auth.client;

import com.nova.auth.client.config.AuthClientProperties;
import com.nova.auth.client.dto.TokenGenerationRequest;
import com.nova.auth.client.dto.TokenGenerationResponse;
import com.nova.auth.client.dto.TokenValidationRequest;
import com.nova.auth.client.dto.TokenValidationResponse;
import com.nova.auth.client.dto.UserProfileResponse;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Pre-built reactive HTTP client for nova-auth-service.
 *
 * This bean is auto-registered by {@link com.nova.auth.client.config.AuthClientAutoConfiguration}.
 * Consumer services can inject it directly:
 *
 * <pre>
 * {@code
 * @Autowired
 * private AuthServiceClient authClient;
 * }
 * </pre>
 */
@Slf4j
public class AuthServiceClient {

    private final WebClient webClient;

    public AuthServiceClient(AuthClientProperties props) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, props.getConnectTimeoutMs())
                .responseTimeout(Duration.ofMillis(props.getReadTimeoutMs()))
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(props.getReadTimeoutMs(), TimeUnit.MILLISECONDS)));

        this.webClient = WebClient.builder()
                .baseUrl(props.getUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    // ------------------------------------------------------------------ //
    //  POST /api/auth/validate                                             //
    //  Call this to verify any inbound JWT without knowing the secret.    //
    // ------------------------------------------------------------------ //

    /**
     * Validates a JWT by asking nova-auth-service.
     *
     * @param token Raw Bearer token (without the "Bearer " prefix)
     * @return {@link TokenValidationResponse} — always emits one item, never errors
     */
    public Mono<TokenValidationResponse> validateToken(String token) {
        log.debug("Validating token with nova-auth-service");
        return webClient.post()
                .uri("/api/auth/validate")
                .bodyValue(new TokenValidationRequest(token))
                .retrieve()
                .onStatus(HttpStatus.UNAUTHORIZED::equals, response ->
                        Mono.empty())   // handled via response body's valid=false
                .bodyToMono(TokenValidationResponse.class)
                .doOnNext(r -> log.debug("Token validation result: valid={}, user={}", r.isValid(), r.getUsername()))
                .onErrorResume(ex -> {
                    log.error("Auth-service unreachable during token validation: {}", ex.getMessage());
                    return Mono.just(TokenValidationResponse.builder()
                            .valid(false)
                            .reason("Auth service unavailable: " + ex.getMessage())
                            .build());
                });
    }

    // ------------------------------------------------------------------ //
    //  POST /api/auth/generate-token                                       //
    //  Call this to obtain a JWT for a given username + password.          //
    // ------------------------------------------------------------------ //

    /**
     * Generates a JWT by authenticating against nova-auth-service.
     *
     * <p>Use this when a service needs to obtain a token on behalf of a user
     * (e.g. a batch job, internal service-to-service call, or a BFF layer).
     *
     * @param username The user's username
     * @param password The user's password (plain text — sent over HTTPS only)
     * @return {@link TokenGenerationResponse} containing the JWT and user info,
     *         or empty if credentials are invalid
     */
    public Mono<TokenGenerationResponse> generateToken(String username, String password) {
        log.debug("Requesting token generation for user: {}", username);
        return webClient.post()
                .uri("/api/auth/generate-token")
                .bodyValue(new TokenGenerationRequest(username, password))
                .retrieve()
                .onStatus(HttpStatus.UNAUTHORIZED::equals, response -> Mono.empty())
                .bodyToMono(TokenGenerationResponse.class)
                .doOnNext(r -> log.debug("Token generated successfully for user: {}", r.getUsername()))
                .onErrorResume(ex -> {
                    log.error("Auth-service unreachable during token generation: {}", ex.getMessage());
                    return Mono.empty();
                });
    }

    // ------------------------------------------------------------------ //
    //  GET /api/auth/me?token=<JWT>                                        //
    //  Call this to load the full user profile for an authenticated user. //
    // ------------------------------------------------------------------ //

    /**
     * Retrieves the full user profile associated with the given JWT.
     *
     * @param token Raw Bearer token (without the "Bearer " prefix)
     * @return {@link UserProfileResponse} or empty if the token is invalid / user not found
     */
    public Mono<UserProfileResponse> getUserProfile(String token) {
        log.debug("Fetching user profile from nova-auth-service");
        return webClient.get()
                .uri(uri -> uri.path("/api/auth/me")
                        .queryParam("token", token)
                        .build())
                .retrieve()
                .onStatus(HttpStatus.UNAUTHORIZED::equals, response -> Mono.empty())
                .bodyToMono(UserProfileResponse.class)
                .doOnNext(u -> log.debug("Fetched profile for user: {}", u.getUsername()))
                .onErrorResume(ex -> {
                    log.error("Auth-service unreachable during profile fetch: {}", ex.getMessage());
                    return Mono.empty();
                });
    }

    // ------------------------------------------------------------------ //
    //  Convenience helper                                                  //
    // ------------------------------------------------------------------ //

    /**
     * Convenience method: validates the token AND returns the profile in one call.
     * Only resolves if the token is valid; returns empty otherwise.
     *
     * @param token Raw Bearer token
     * @return UserProfileResponse or empty Mono
     */
    public Mono<UserProfileResponse> validateAndGetProfile(String token) {
        return validateToken(token)
                .filter(TokenValidationResponse::isValid)
                .flatMap(ignored -> getUserProfile(token));
    }
}

