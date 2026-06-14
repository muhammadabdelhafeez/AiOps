package org.kfh.aiops.platform.identity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IdentitySignInResponse(
        UUID tenantId,
        UUID userId,
        String username,
        String displayName,
        String email,
        String countryCode,
        String countryName,
        String countryGroupName,
        String environment,
        String roleId,
        String userRole,
        List<String> permissions,
        Instant authenticatedAt) {
}

