package com.nova.auth.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /api/auth/generate-token.
 * Used by consumer microservices to obtain a JWT on behalf of a user.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenGenerationRequest {

    private String username;
    private String password;
}

