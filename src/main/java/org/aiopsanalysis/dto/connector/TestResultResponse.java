package org.aiopsanalysis.dto.connector;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for connector test result.
 */
public record TestResultResponse(
        @JsonProperty("connectorRunId") UUID connectorRunId,
        @JsonProperty("pass") Boolean pass,
        @JsonProperty("latencyMs") Long latencyMs,
        @JsonProperty("message") String message,
        @JsonProperty("httpStatus") Integer httpStatus,
        @JsonProperty("details") JsonNode details,
        @JsonProperty("startedAt") Instant startedAt,
        @JsonProperty("endedAt") Instant endedAt
) {}
