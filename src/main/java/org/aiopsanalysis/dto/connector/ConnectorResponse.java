package org.aiopsanalysis.dto.connector;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for connector data.
 * Never includes plaintext secrets.
 */
public record ConnectorResponse(
        UUID connectorId,
        String type,
        String name,
        Boolean enabled,
        JsonNode config,
        @JsonProperty("configSummary") ConfigSummary configSummary,
        @JsonProperty("secretsMasked") Boolean secretsMasked,
        @JsonProperty("secretsMask") JsonNode secretsMask,
        @JsonProperty("lastTest") RunSummary lastTest,
        @JsonProperty("lastRun") RunSummary lastRun,
        @JsonProperty("health") HealthBadge health,
        @JsonProperty("usedBySchedulesCount") Integer usedBySchedulesCount,
        List<ScheduleSummary> schedules,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Summary of configuration for list view.
     */
    public record ConfigSummary(
            String endpointBaseUrl,
            String method,
            Integer windowMinutes,
            String env,
            String domain
    ) {}

    /**
     * Summary of a run (test or collect).
     */
    public record RunSummary(
            UUID runId,
            String status,
            Instant at,
            Long latencyMs,
            Long durationSec,
            JsonNode metrics,
            String message
    ) {}

    /**
     * Health badge computed from last run/test status.
     */
    public record HealthBadge(
            String badge,  // Healthy, Degraded, Down, Disabled
            String reason
    ) {}

    /**
     * Summary of schedule using this connector.
     */
    public record ScheduleSummary(
            UUID scheduleId,
            String name,
            String cron,
            Boolean enabled
    ) {}

    /**
     * Builder helper for creating response from entity.
     */
    public static ConnectorResponseBuilder builder() {
        return new ConnectorResponseBuilder();
    }

    public static class ConnectorResponseBuilder {
        private UUID connectorId;
        private String type;
        private String name;
        private Boolean enabled;
        private JsonNode config;
        private ConfigSummary configSummary;
        private Boolean secretsMasked = true;
        private JsonNode secretsMask;
        private RunSummary lastTest;
        private RunSummary lastRun;
        private HealthBadge health;
        private Integer usedBySchedulesCount;
        private List<ScheduleSummary> schedules;
        private Instant createdAt;
        private Instant updatedAt;

        public ConnectorResponseBuilder connectorId(UUID connectorId) {
            this.connectorId = connectorId;
            return this;
        }

        public ConnectorResponseBuilder type(String type) {
            this.type = type;
            return this;
        }

        public ConnectorResponseBuilder name(String name) {
            this.name = name;
            return this;
        }

        public ConnectorResponseBuilder enabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public ConnectorResponseBuilder config(JsonNode config) {
            this.config = config;
            return this;
        }

        public ConnectorResponseBuilder configSummary(ConfigSummary configSummary) {
            this.configSummary = configSummary;
            return this;
        }

        public ConnectorResponseBuilder secretsMasked(Boolean secretsMasked) {
            this.secretsMasked = secretsMasked;
            return this;
        }

        public ConnectorResponseBuilder secretsMask(JsonNode secretsMask) {
            this.secretsMask = secretsMask;
            return this;
        }

        public ConnectorResponseBuilder lastTest(RunSummary lastTest) {
            this.lastTest = lastTest;
            return this;
        }

        public ConnectorResponseBuilder lastRun(RunSummary lastRun) {
            this.lastRun = lastRun;
            return this;
        }

        public ConnectorResponseBuilder health(HealthBadge health) {
            this.health = health;
            return this;
        }

        public ConnectorResponseBuilder usedBySchedulesCount(Integer count) {
            this.usedBySchedulesCount = count;
            return this;
        }

        public ConnectorResponseBuilder schedules(List<ScheduleSummary> schedules) {
            this.schedules = schedules;
            return this;
        }

        public ConnectorResponseBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public ConnectorResponseBuilder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public ConnectorResponse build() {
            return new ConnectorResponse(
                    connectorId, type, name, enabled, config, configSummary,
                    secretsMasked, secretsMask, lastTest, lastRun, health,
                    usedBySchedulesCount, schedules, createdAt, updatedAt
            );
        }
    }
}
