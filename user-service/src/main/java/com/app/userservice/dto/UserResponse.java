package com.app.userservice.dto;

import java.time.Instant;

public record UserResponse(
        Long id,
        String name,
        String email,
        String givenName,
        String familyName,
        String pictureUrl,
        Boolean emailVerified,
        String role,
        String authProvider,
        Integer credits,
        Instant createdAt,
        Instant lastLoginAt
) {
}
