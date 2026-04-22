package org.aiopsanalysis.domain.postgres;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity for ops.connector_run_logs table.
 * Stores detailed log entries for each connector run.
 */
@Entity
@Table(name = "connector_run_logs", schema = "ops")
public class ConnectorRunLog {

    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "connector_run_id", nullable = false)
    private UUID connectorRunId;

    @Column(name = "at", nullable = false)
    private Instant at = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false)
    private LogLevel level = LogLevel.INFO;

    @Column(name = "message", nullable = false)
    private String message;

    // Default constructor
    public ConnectorRunLog() {}

    // All-args constructor
    public ConnectorRunLog(Long id, UUID tenantId, UUID connectorRunId, Instant at, LogLevel level, String message) {
        this.id = id;
        this.tenantId = tenantId;
        this.connectorRunId = connectorRunId;
        this.at = at;
        this.level = level;
        this.message = message;
    }

    // Getters
    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getConnectorRunId() { return connectorRunId; }
    public Instant getAt() { return at; }
    public LogLevel getLevel() { return level; }
    public String getMessage() { return message; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setConnectorRunId(UUID connectorRunId) { this.connectorRunId = connectorRunId; }
    public void setAt(Instant at) { this.at = at; }
    public void setLevel(LogLevel level) { this.level = level; }
    public void setMessage(String message) { this.message = message; }

    public static ConnectorRunLog info(UUID tenantId, UUID connectorRunId, String message) {
        return ConnectorRunLog.builder()
                .tenantId(tenantId)
                .connectorRunId(connectorRunId)
                .level(LogLevel.INFO)
                .message(message)
                .at(Instant.now())
                .build();
    }

    public static ConnectorRunLog error(UUID tenantId, UUID connectorRunId, String message) {
        return ConnectorRunLog.builder()
                .tenantId(tenantId)
                .connectorRunId(connectorRunId)
                .level(LogLevel.ERROR)
                .message(message)
                .at(Instant.now())
                .build();
    }

    public static ConnectorRunLog warn(UUID tenantId, UUID connectorRunId, String message) {
        return ConnectorRunLog.builder()
                .tenantId(tenantId)
                .connectorRunId(connectorRunId)
                .level(LogLevel.WARN)
                .message(message)
                .at(Instant.now())
                .build();
    }

    public static ConnectorRunLog debug(UUID tenantId, UUID connectorRunId, String message) {
        return ConnectorRunLog.builder()
                .tenantId(tenantId)
                .connectorRunId(connectorRunId)
                .level(LogLevel.DEBUG)
                .message(message)
                .at(Instant.now())
                .build();
    }

    // Builder
    public static ConnectorRunLogBuilder builder() {
        return new ConnectorRunLogBuilder();
    }

    public static class ConnectorRunLogBuilder {
        private Long id;
        private UUID tenantId;
        private UUID connectorRunId;
        private Instant at = Instant.now();
        private LogLevel level = LogLevel.INFO;
        private String message;

        public ConnectorRunLogBuilder id(Long id) { this.id = id; return this; }
        public ConnectorRunLogBuilder tenantId(UUID tenantId) { this.tenantId = tenantId; return this; }
        public ConnectorRunLogBuilder connectorRunId(UUID connectorRunId) { this.connectorRunId = connectorRunId; return this; }
        public ConnectorRunLogBuilder at(Instant at) { this.at = at; return this; }
        public ConnectorRunLogBuilder level(LogLevel level) { this.level = level; return this; }
        public ConnectorRunLogBuilder message(String message) { this.message = message; return this; }

        public ConnectorRunLog build() {
            return new ConnectorRunLog(id, tenantId, connectorRunId, at, level, message);
        }
    }
}
