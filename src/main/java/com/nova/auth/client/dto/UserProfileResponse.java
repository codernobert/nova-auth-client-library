package com.nova.auth.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Full user profile returned by GET /api/auth/me.
 * Use this to enrich audit logs, authorisation decisions, or user context
 * inside any consumer microservice.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

    private Long   userId;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String status;
}

