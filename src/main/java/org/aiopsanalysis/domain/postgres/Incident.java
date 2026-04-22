package org.aiopsanalysis.domain.postgres;

import jakarta.persistence.*;
import org.aiopsanalysis.domain.model.IncidentClassification;
import org.aiopsanalysis.domain.model.IncidentStatus;
import org.aiopsanalysis.domain.model.Severity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA Entity for incident.incidents table.
 * Represents an incident in its lifecycle - the main entity for incident management.
 * 
 * Incident lifecycle:
 * - NEW: Created when new incident_key is detected
 * - ONGOING: Updated when same incident_key has alerts in subsequent runs
 * - REOPENED: Status changes from CLOSED to OPEN within reopen_window
 * - CLOSED: No alerts for quiet_window period
 */
@Entity
@Table(name = "incidents", schema = "incident",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "incident_key"}),
    indexes = {
        @Index(name = "idx_incidents_app_status", columnList = "tenant_id, app_id, status"),
        @Index(name = "idx_incidents_last_seen", columnList = "tenant_id, last_seen DESC"),
        @Index(name = "idx_incidents_incident_key", columnList = "tenant_id, incident_key")
    }
)
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "incident_id")
    private UUID incidentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "app_id", nullable = false)
    private UUID appId;

    /**
     * Stable hash: hash(tenantId + appId + sorted(primary_group_ids))
     * This key identifies "same incident" for reopen logic.
     */
    @Column(name = "incident_key", nullable = false)
    private String incidentKey;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IncidentStatus status = IncidentStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private Severity severity = Severity.MEDIUM;

    /**
     * Classification label: NEW, ONGOING, REOPENED, NEW_KNOWN_PATTERN
     * Determined by deterministic logic, not AI.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "classification_label", nullable = false)
    private IncidentClassification classificationLabel = IncidentClassification.NEW;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    /**
     * Timestamp when incident was last closed.
     * Used for reopen_window calculation.
     */
    @Column(name = "last_closed_at")
    private Instant lastClosedAt;

    /**
     * Number of times this incident has been reopened.
     */
    @Column(name = "reopen_count", nullable = false)
    private Integer reopenCount = 0;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    /**
     * GPT-generated summary and RCA.
     * GPT only writes narrative - never decides new/old/reopen.
     */
    @Column(name = "pro_summary", columnDefinition = "TEXT")
    private String proSummary;

    /**
     * Confidence score from AI reasoning (0.00-1.00).
     */
    @Column(name = "confidence", precision = 3, scale = 2)
    private BigDecimal confidence;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    // Relationships
    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IncidentGroup> incidentGroups = new ArrayList<>();

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IncidentEvidence> evidence = new ArrayList<>();

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IncidentStatusHistory> statusHistory = new ArrayList<>();

    // Constructors
    public Incident() {}

    public Incident(UUID tenantId, UUID appId, String incidentKey, String title) {
        this.tenantId = tenantId;
        this.appId = appId;
        this.incidentKey = incidentKey;
        this.title = title;
        this.firstSeen = Instant.now();
        this.lastSeen = Instant.now();
    }

    // Lifecycle methods
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Update last_seen timestamp when new alerts arrive.
     */
    public void touch() {
        this.lastSeen = Instant.now();
    }

    /**
     * Close the incident and record closure time.
     */
    public void close() {
        this.status = IncidentStatus.CLOSED;
        this.lastClosedAt = Instant.now();
    }

    /**
     * Reopen the incident and increment reopen count.
     */
    public void reopen() {
        this.status = IncidentStatus.OPEN;
        this.classificationLabel = IncidentClassification.REOPENED;
        this.reopenCount++;
        this.lastSeen = Instant.now();
    }

    /**
     * Check if incident can be reopened based on time window.
     * @param reopenWindowDays Number of days within which reopen is allowed
     */
    public boolean canReopen(int reopenWindowDays) {
        if (this.status != IncidentStatus.CLOSED || this.lastClosedAt == null) {
            return false;
        }
        Instant reopenCutoff = Instant.now().minusSeconds(reopenWindowDays * 24L * 60L * 60L);
        return this.lastClosedAt.isAfter(reopenCutoff);
    }

    /**
     * Check if incident should be auto-closed based on quiet window.
     * @param quietWindowHours Number of hours of inactivity before auto-close
     */
    public boolean shouldAutoClose(int quietWindowHours) {
        if (!this.status.isActive()) {
            return false;
        }
        Instant quietCutoff = Instant.now().minusSeconds(quietWindowHours * 60L * 60L);
        return this.lastSeen.isBefore(quietCutoff);
    }

    // Getters and Setters
    public UUID getIncidentId() { return incidentId; }
    public void setIncidentId(UUID incidentId) { this.incidentId = incidentId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getAppId() { return appId; }
    public void setAppId(UUID appId) { this.appId = appId; }

    public String getIncidentKey() { return incidentKey; }
    public void setIncidentKey(String incidentKey) { this.incidentKey = incidentKey; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public IncidentStatus getStatus() { return status; }
    public void setStatus(IncidentStatus status) { this.status = status; }

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }

    public IncidentClassification getClassificationLabel() { return classificationLabel; }
    public void setClassificationLabel(IncidentClassification classificationLabel) { this.classificationLabel = classificationLabel; }

    public Instant getFirstSeen() { return firstSeen; }
    public void setFirstSeen(Instant firstSeen) { this.firstSeen = firstSeen; }

    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }

    public Instant getLastClosedAt() { return lastClosedAt; }
    public void setLastClosedAt(Instant lastClosedAt) { this.lastClosedAt = lastClosedAt; }

    public Integer getReopenCount() { return reopenCount; }
    public void setReopenCount(Integer reopenCount) { this.reopenCount = reopenCount; }

    public UUID getAssignedTo() { return assignedTo; }
    public void setAssignedTo(UUID assignedTo) { this.assignedTo = assignedTo; }

    public String getProSummary() { return proSummary; }
    public void setProSummary(String proSummary) { this.proSummary = proSummary; }

    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public List<IncidentGroup> getIncidentGroups() { return incidentGroups; }
    public void setIncidentGroups(List<IncidentGroup> incidentGroups) { this.incidentGroups = incidentGroups; }

    public List<IncidentEvidence> getEvidence() { return evidence; }
    public void setEvidence(List<IncidentEvidence> evidence) { this.evidence = evidence; }

    public List<IncidentStatusHistory> getStatusHistory() { return statusHistory; }
    public void setStatusHistory(List<IncidentStatusHistory> statusHistory) { this.statusHistory = statusHistory; }

    // Builder pattern
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final Incident incident = new Incident();

        public Builder tenantId(UUID v) { incident.tenantId = v; return this; }
        public Builder appId(UUID v) { incident.appId = v; return this; }
        public Builder incidentKey(String v) { incident.incidentKey = v; return this; }
        public Builder title(String v) { incident.title = v; return this; }
        public Builder status(IncidentStatus v) { incident.status = v; return this; }
        public Builder severity(Severity v) { incident.severity = v; return this; }
        public Builder classificationLabel(IncidentClassification v) { incident.classificationLabel = v; return this; }
        public Builder firstSeen(Instant v) { incident.firstSeen = v; return this; }
        public Builder lastSeen(Instant v) { incident.lastSeen = v; return this; }
        public Builder lastClosedAt(Instant v) { incident.lastClosedAt = v; return this; }
        public Builder reopenCount(Integer v) { incident.reopenCount = v; return this; }
        public Builder assignedTo(UUID v) { incident.assignedTo = v; return this; }
        public Builder proSummary(String v) { incident.proSummary = v; return this; }
        public Builder confidence(BigDecimal v) { incident.confidence = v; return this; }

        public Incident build() {
            if (incident.firstSeen == null) incident.firstSeen = Instant.now();
            if (incident.lastSeen == null) incident.lastSeen = Instant.now();
            return incident;
        }
    }
}
