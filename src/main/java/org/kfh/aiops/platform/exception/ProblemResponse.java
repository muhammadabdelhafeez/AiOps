package org.kfh.aiops.platform.exception;

import java.time.Instant;
import java.util.Map;

/** Standard API error body without stack traces or sensitive details. */
public record ProblemResponse(
        String code,
        String message,
        Instant timestamp,
        String correlationId,
        Map<String, Object> details) {
}

