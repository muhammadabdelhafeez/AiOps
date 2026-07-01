package org.kfh.aiops.ingestion;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Map;

/**
 * Null-safe readers for the loosely-typed raw event maps handed in by connectors. Every source names
 * its fields differently (and inconsistently), so readers accept a fallback and a list of candidate
 * keys, returning the first non-blank hit.
 */
final class RawFields {

    private RawFields() {
    }

    /** First non-blank string among {@code keys}, or {@code null}. */
    static String str(Map<String, Object> raw, String... keys) {
        for (var key : keys) {
            var value = raw.get(key);
            if (value != null) {
                var text = String.valueOf(value).trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return null;
    }

    /** {@link #str} with a non-null fallback. */
    static String strOr(Map<String, Object> raw, String fallback, String... keys) {
        var value = str(raw, keys);
        return value != null ? value : fallback;
    }

    /**
     * First non-blank value among {@code keys}, joining {@link Collection} values with {@code ", "}.
     * BMC Helix fields such as {@code _service_name} / {@code _impacted_service_name} arrive as either
     * a scalar or an array, so callers must not assume a single type.
     */
    static String joined(Map<String, Object> raw, String... keys) {
        for (var key : keys) {
            var value = raw.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Collection<?> collection) {
                var text = collection.stream()
                        .filter(java.util.Objects::nonNull)
                        .map(item -> String.valueOf(item).trim())
                        .filter(item -> !item.isEmpty())
                        .collect(java.util.stream.Collectors.joining(", "));
                if (!text.isEmpty()) {
                    return text;
                }
                continue;
            }
            var text = String.valueOf(value).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return null;
    }

    /**
     * First parseable timestamp among {@code keys}, else {@code fallback}. Accepts {@link Instant},
     * epoch seconds/millis (as {@link Number} or numeric string), ISO-8601 instants, and zone-less
     * ISO local date-times (interpreted as UTC).
     */
    static Instant instant(Map<String, Object> raw, Instant fallback, String... keys) {
        for (var key : keys) {
            var value = raw.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Instant instant) {
                return instant;
            }
            if (value instanceof Number number) {
                return fromEpoch(number.longValue());
            }
            var text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                continue;
            }
            try {
                return Instant.parse(text);
            } catch (RuntimeException ignored) {
                // not an ISO instant — try other shapes
            }
            try {
                return fromEpoch(Long.parseLong(text));
            } catch (RuntimeException ignored) {
                // not epoch numeric
            }
            try {
                return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
            } catch (RuntimeException ignored) {
                // not a zone-less ISO local date-time — give up on this key
            }
        }
        return fallback;
    }

    private static Instant fromEpoch(long value) {
        // Heuristic: 13-digit values are millis, 10-digit are seconds.
        return value >= 1_000_000_000_000L ? Instant.ofEpochMilli(value) : Instant.ofEpochSecond(value);
    }
}
