package org.kfh.aiops.platform.tenant;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.kfh.aiops.platform.exception.ForbiddenAccessException;

/**
 * Explicit tenant/user request context passed into service-layer APIs.
 */
public record TenantContext(
        UUID tenantId,
        UUID userId,
        String countryCode,
        String environment,
        String correlationId,
        Set<String> permissions) {

    public TenantContext {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(userId, "userId is required");
        countryCode = normalize(countryCode, "KW");
        environment = normalize(environment, "PROD");
        correlationId = normalize(correlationId, UUID.randomUUID().toString());
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public void requirePermission(String permission) {
        if (!permissions.contains("*") && !permissions.contains(permission)) {
            throw new ForbiddenAccessException("Missing permission: " + permission);
        }
    }

    public boolean hasPermission(String permission) {
        return permissions.contains("*") || permissions.contains(permission);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase();
    }
}

