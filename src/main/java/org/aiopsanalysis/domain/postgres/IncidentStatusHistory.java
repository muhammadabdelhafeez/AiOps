package org.aiopsanalysis.domain.postgres;

import jakarta.persistence.*;
import org.aiopsanalysis.domain.model.IncidentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity for incident.incident_status_history table.
 * Tracks all status transitions for audit and analysis purposes.
 */
@Entity
@Table(name = "incident_status_history", schema = "incident",
    indexes = {
        @Index(name = "idx_incident_history", columnList = "tenant_id, incident_id, at DESC")
    }
)
public class IncidentStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    /**
     * Previous status before transition.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private IncidentStatus fromStatus;

    /**
     * New status after transition.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private IncidentStatus toStatus;

    /**
     * Timestamp of status change.
     */
    @Column(name = "at", nullable = false)
    private Instant at = Instant.now();

    /**
     * User who triggered the status change (null for system actions).
     */
    @Column(name = "actor_user_id")
    private UUID actorUserId;

    /**
     * Optional notes about the status change.
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", insertable = false, updatable = false)
    private Incident incident;

    // Constructors
    public IncidentStatusHistory() {}

    public IncidentStatusHistory(UUID tenantId, UUID incidentId, IncidentStatus fromStatus, IncidentStatus toStatus) {
        this.tenantId = tenantId;
        this.incidentId = incidentId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    public IncidentStatusHistory(UUID tenantId, UUID incidentId, IncidentStatus fromStatus, 
                                  IncidentStatus toStatus, UUID actorUserId, String notes) {
        this.tenantId = tenantId;
        this.incidentId = incidentId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorUserId = actorUserId;
        this.notes = notes;
    }

    /**
     * Create a history entry for system-triggered changes.
     */
    public static IncidentStatusHistory systemChange(UUID tenantId, UUID incidentId, 
                                                      IncidentStatus from, IncidentStatus to, String reason) {
        IncidentStatusHistory history = new IncidentStatusHistory(tenantId, incidentId, from, to);
        history.setNotes("SYSTEM: " + reason);
        return history;
    }

    /**
     * Create a history entry for user-triggered changes.
     */
    public static IncidentStatusHistory userChange(UUID tenantId, UUID incidentId, 
                                                    IncidentStatus from, IncidentStatus to, 
                                                    UUID userId, String notes) {
        return new IncidentStatusHistory(tenantId, incidentId, from, to, userId, notes);
    }

    /**
     * Check if this was a reopen transition.
     */
    public boolean isReopen() {
        return fromStatus == IncidentStatus.CLOSED && toStatus == IncidentStatus.OPEN;
    }

    /**
     * Check if this was a closure transition.
     */
    public boolean isClosure() {
        return toStatus == IncidentStatus.CLOSED;
    }

    /**
     * Check if this was a system-triggered change.
     */
    public boolean isSystemAction() {
        return actorUserId == null;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getIncidentId() { return incidentId; }
    public void setIncidentId(UUID incidentId) { this.incidentId = incidentId; }

    public IncidentStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(IncidentStatus fromStatus) { this.fromStatus = fromStatus; }

    public IncidentStatus getToStatus() { return toStatus; }
    public void setToStatus(IncidentStatus toStatus) { this.toStatus = toStatus; }

    public Instant getAt() { return at; }
    public void setAt(Instant at) { this.at = at; }

    public UUID getActorUserId() { return actorUserId; }
    public void setActorUserId(UUID actorUserId) { this.actorUserId = actorUserId; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Incident getIncident() { return incident; }
    public void setIncident(Incident incident) { this.incident = incident; }

    // Builder pattern
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final IncidentStatusHistory history = new IncidentStatusHistory();

        public Builder tenantId(UUID v) { history.tenantId = v; return this; }
        public Builder incidentId(UUID v) { history.incidentId = v; return this; }
        public Builder fromStatus(IncidentStatus v) { history.fromStatus = v; return this; }
        public Builder toStatus(IncidentStatus v) { history.toStatus = v; return this; }
        public Builder at(Instant v) { history.at = v; return this; }
        public Builder actorUserId(UUID v) { history.actorUserId = v; return this; }
        public Builder notes(String v) { history.notes = v; return this; }

        public IncidentStatusHistory build() {
            if (history.at == null) history.at = Instant.now();
            return history;
        }
    }
}
