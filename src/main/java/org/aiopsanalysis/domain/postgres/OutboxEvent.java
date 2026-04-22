package org.aiopsanalysis.domain.postgres;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity for ops.outbox_events table.
 * Implements the Outbox pattern for reliable async event processing.
 */
@Entity
@Table(name = "outbox_events", schema = "ops")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "published_at")
    private Instant publishedAt;

    // Default constructor
    public OutboxEvent() {}

    // All-args constructor
    public OutboxEvent(UUID eventId, UUID tenantId, Instant createdAt, String eventType,
                       String aggregateType, String aggregateId, JsonNode payload, Instant publishedAt) {
        this.eventId = eventId;
        this.tenantId = tenantId;
        this.createdAt = createdAt;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.publishedAt = publishedAt;
    }

    // Getters
    public UUID getEventId() { return eventId; }
    public UUID getTenantId() { return tenantId; }
    public Instant getCreatedAt() { return createdAt; }
    public String getEventType() { return eventType; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public JsonNode getPayload() { return payload; }
    public Instant getPublishedAt() { return publishedAt; }

    // Setters
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
    public void setPayload(JsonNode payload) { this.payload = payload; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

    /**
     * Mark event as published.
     */
    public void markPublished() {
        this.publishedAt = Instant.now();
    }

    /**
     * Check if event is pending (not yet published).
     */
    public boolean isPending() {
        return publishedAt == null;
    }

    // Event type constants for connectors
    public static final String CONNECTOR_CREATED = "CONNECTOR_CREATED";
    public static final String CONNECTOR_UPDATED = "CONNECTOR_UPDATED";
    public static final String CONNECTOR_DELETED = "CONNECTOR_DELETED";
    public static final String CONNECTOR_TOGGLED = "CONNECTOR_TOGGLED";
    public static final String CONNECTOR_TEST_REQUESTED = "CONNECTOR_TEST_REQUESTED";
    public static final String CONNECTOR_COLLECT_REQUESTED = "CONNECTOR_COLLECT_REQUESTED";

    /**
     * Factory method for creating connector events.
     */
    public static OutboxEvent forConnector(UUID tenantId, String eventType, UUID connectorId, JsonNode payload) {
        return OutboxEvent.builder()
                .tenantId(tenantId)
                .eventType(eventType)
                .aggregateType("CONNECTOR")
                .aggregateId(connectorId.toString())
                .payload(payload)
                .createdAt(Instant.now())
                .build();
    }

    // Builder
    public static OutboxEventBuilder builder() {
        return new OutboxEventBuilder();
    }

    public static class OutboxEventBuilder {
        private UUID eventId;
        private UUID tenantId;
        private Instant createdAt = Instant.now();
        private String eventType;
        private String aggregateType;
        private String aggregateId;
        private JsonNode payload;
        private Instant publishedAt;

        public OutboxEventBuilder eventId(UUID eventId) { this.eventId = eventId; return this; }
        public OutboxEventBuilder tenantId(UUID tenantId) { this.tenantId = tenantId; return this; }
        public OutboxEventBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public OutboxEventBuilder eventType(String eventType) { this.eventType = eventType; return this; }
        public OutboxEventBuilder aggregateType(String aggregateType) { this.aggregateType = aggregateType; return this; }
        public OutboxEventBuilder aggregateId(String aggregateId) { this.aggregateId = aggregateId; return this; }
        public OutboxEventBuilder payload(JsonNode payload) { this.payload = payload; return this; }
        public OutboxEventBuilder publishedAt(Instant publishedAt) { this.publishedAt = publishedAt; return this; }

        public OutboxEvent build() {
            return new OutboxEvent(eventId, tenantId, createdAt, eventType, aggregateType, aggregateId, payload, publishedAt);
        }
    }
}
