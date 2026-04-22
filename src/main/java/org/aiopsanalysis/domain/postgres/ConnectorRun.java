package org.aiopsanalysis.domain.postgres;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity for ops.connector_runs table.
 * Tracks each connector test or collect operation.
 */
@Entity
@Table(name = "connector_runs", schema = "ops")
public class ConnectorRun {

    public enum Status {
        QUEUED, RUNNING, SUCCESS, FAILED
    }

    public enum RunType {
        TEST, COLLECT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "connector_run_id")
    private UUID connectorRunId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "connector_id", nullable = false)
    private UUID connectorId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.QUEUED;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false)
    private RunType runType = RunType.COLLECT;

    @Column(name = "summary")
    private String summary;

    /**
     * Metrics stored as JSONB.
     * Structure:
     * {
     *   "pulled": 1234,
     *   "normalized": 1200,
     *   "errors": 34,
     *   "latencyMs": 2500,
     *   "artifactUrl": "sharepoint://...",
     *   "httpStatus": 200,
     *   "message": "..."
     * }
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics", columnDefinition = "jsonb")
    private JsonNode metrics;

    // Default constructor
    public ConnectorRun() {}

    // All-args constructor
    public ConnectorRun(UUID connectorRunId, UUID tenantId, UUID connectorId, Instant startedAt,
                        Instant endedAt, Status status, RunType runType, String summary, JsonNode metrics) {
        this.connectorRunId = connectorRunId;
        this.tenantId = tenantId;
        this.connectorId = connectorId;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.status = status;
        this.runType = runType;
        this.summary = summary;
        this.metrics = metrics;
    }

    // Getters
    public UUID getConnectorRunId() { return connectorRunId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getConnectorId() { return connectorId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public Status getStatus() { return status; }
    public RunType getRunType() { return runType; }
    public String getSummary() { return summary; }
    public JsonNode getMetrics() { return metrics; }

    // Setters
    public void setConnectorRunId(UUID connectorRunId) { this.connectorRunId = connectorRunId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setConnectorId(UUID connectorId) { this.connectorId = connectorId; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
    public void setStatus(Status status) { this.status = status; }
    public void setRunType(RunType runType) { this.runType = runType; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setMetrics(JsonNode metrics) { this.metrics = metrics; }

    /**
     * Factory method for creating a new test run.
     */
    public static ConnectorRun createTestRun(UUID tenantId, UUID connectorId) {
        return ConnectorRun.builder()
                .tenantId(tenantId)
                .connectorId(connectorId)
                .runType(RunType.TEST)
                .status(Status.RUNNING)
                .startedAt(Instant.now())
                .build();
    }

    /**
     * Factory method for creating a new collect run.
     */
    public static ConnectorRun createCollectRun(UUID tenantId, UUID connectorId) {
        return ConnectorRun.builder()
                .tenantId(tenantId)
                .connectorId(connectorId)
                .runType(RunType.COLLECT)
                .status(Status.QUEUED)
                .startedAt(Instant.now())
                .build();
    }

    /**
     * Mark run as started/running.
     */
    public void markRunning() {
        this.status = Status.RUNNING;
        this.startedAt = Instant.now();
    }

    /**
     * Mark run as successful.
     */
    public void markSuccess(String summary, JsonNode metrics) {
        this.status = Status.SUCCESS;
        this.endedAt = Instant.now();
        this.summary = summary;
        this.metrics = metrics;
    }

    /**
     * Mark run as failed.
     */
    public void markFailed(String summary, JsonNode metrics) {
        this.status = Status.FAILED;
        this.endedAt = Instant.now();
        this.summary = summary;
        this.metrics = metrics;
    }

    /**
     * Calculate duration in milliseconds.
     */
    public Long getDurationMs() {
        if (startedAt == null || endedAt == null) {
            return null;
        }
        return endedAt.toEpochMilli() - startedAt.toEpochMilli();
    }

    // Builder
    public static ConnectorRunBuilder builder() {
        return new ConnectorRunBuilder();
    }

    public static class ConnectorRunBuilder {
        private UUID connectorRunId;
        private UUID tenantId;
        private UUID connectorId;
        private Instant startedAt = Instant.now();
        private Instant endedAt;
        private Status status = Status.QUEUED;
        private RunType runType = RunType.COLLECT;
        private String summary;
        private JsonNode metrics;

        public ConnectorRunBuilder connectorRunId(UUID connectorRunId) { this.connectorRunId = connectorRunId; return this; }
        public ConnectorRunBuilder tenantId(UUID tenantId) { this.tenantId = tenantId; return this; }
        public ConnectorRunBuilder connectorId(UUID connectorId) { this.connectorId = connectorId; return this; }
        public ConnectorRunBuilder startedAt(Instant startedAt) { this.startedAt = startedAt; return this; }
        public ConnectorRunBuilder endedAt(Instant endedAt) { this.endedAt = endedAt; return this; }
        public ConnectorRunBuilder status(Status status) { this.status = status; return this; }
        public ConnectorRunBuilder runType(RunType runType) { this.runType = runType; return this; }
        public ConnectorRunBuilder summary(String summary) { this.summary = summary; return this; }
        public ConnectorRunBuilder metrics(JsonNode metrics) { this.metrics = metrics; return this; }

        public ConnectorRun build() {
            return new ConnectorRun(connectorRunId, tenantId, connectorId, startedAt, endedAt, status, runType, summary, metrics);
        }
    }
}
