package com.nova.auth.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body from POST /api/auth/generate-token.
 * Contains the JWT and basic user info returned by nova-auth-service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenGenerationResponse {

    private String token;
    private String username;
    private String email;
    private Long userId;
    private Long expiresIn;
}

