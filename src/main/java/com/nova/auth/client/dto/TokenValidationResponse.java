package com.nova.auth.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from POST /api/auth/validate.
 * Other microservices use this to verify an inbound JWT
 * without holding the JWT secret themselves.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenValidationResponse {

    /** Whether the token is valid and not expired. */
    private boolean valid;

    /** The user ID embedded in the token (null if invalid). */
    private Long userId;

    /** The username embedded in the token (null if invalid). */
    private String username;

    /** The user's email (null if invalid). */
    private String email;

    /** Human-readable reason when valid=false. */
    private String reason;
}

