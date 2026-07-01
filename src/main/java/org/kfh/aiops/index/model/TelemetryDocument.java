package org.kfh.aiops.index.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A single indexed telemetry event (§10 document/field model). Typed keyword/text/date fields are
 * first-class; source-specific numerics (duration_ms, cpu_usage, error_count, …) go in
 * {@code attributes}. {@code rawRef} points at the full compressed payload in object storage — the
 * index never stores the whole raw log.
 */
public record TelemetryDocument(
        String id,
        Instant timestamp,
        UUID tenantId,
        String countryCode,
        String environment,
        TelemetryKind kind,
        String sourceSystem,
        String applicationId,
        String serviceId,
        String resourceId,
        String resourceType,
        String severity,
        String traceId,
        String correlationId,
        String transactionId,
        String message,
        String rawRef,
        Map<String, Object> attributes) {

    public TelemetryDocument {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("TelemetryDocument.id is required");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("TelemetryDocument.timestamp is required");
        }
        if (kind == null) {
            throw new IllegalArgumentException("TelemetryDocument.kind is required");
        }
        countryCode = norm(countryCode);
        environment = norm(environment);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    private static String norm(String value) {
        return value == null || value.isBlank() ? "ALL" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
