package org.kfh.aiops.index.model;

import java.time.Instant;
import java.util.List;

/**
 * Telemetry search request. All filters are optional; blank/null means "no constraint". {@code from}
 * and {@code to} drive time-partition pruning; keyword filters are exact-match; {@code text} is a
 * case-insensitive substring match on the message (full inverted-index text search is increment 2).
 */
public record IndexQuery(
        List<TelemetryKind> kinds,
        String country,
        String environment,
        Instant from,
        Instant to,
        String severity,
        String sourceSystem,
        String applicationId,
        String serviceId,
        String resourceId,
        String traceId,
        String correlationId,
        String text,
        Integer page,
        Integer size) {

    private static final int DEFAULT_SIZE = 50;
    private static final int MAX_SIZE = 500;

    public IndexQuery {
        kinds = kinds == null ? List.of() : List.copyOf(kinds);
    }

    public List<TelemetryKind> kindsOrAll() {
        return kinds.isEmpty() ? List.of(TelemetryKind.values()) : kinds;
    }

    public int pageOrDefault() {
        return page == null || page < 0 ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null || size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }
}
