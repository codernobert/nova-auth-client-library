package com.nova.auth.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /api/auth/validate.
 * Sent by consumer microservices when they need to verify an inbound JWT.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenValidationRequest {

    private String token;
}

