package org.aiopsanalysis.repository;

import org.aiopsanalysis.domain.model.IncidentClassification;
import org.aiopsanalysis.domain.model.IncidentStatus;
import org.aiopsanalysis.domain.postgres.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA Repository for Incident entities.
 * Provides CRUD operations and custom queries for incident lifecycle management.
 */
@Repository
public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    /**
     * Find incident by tenant and incident key.
     * Used for determining NEW vs ONGOING vs REOPENED classification.
     */
    Optional<Incident> findByTenantIdAndIncidentKey(UUID tenantId, String incidentKey);

    /**
     * Find all incidents for a tenant and application.
     */
    List<Incident> findByTenantIdAndAppId(UUID tenantId, UUID appId);

    /**
     * Find all incidents for a tenant with a specific status.
     */
    List<Incident> findByTenantIdAndStatus(UUID tenantId, IncidentStatus status);

    /**
     * Find all active incidents (OPEN or ACKNOWLEDGED) for a tenant.
     */
    @Query("SELECT i FROM Incident i WHERE i.tenantId = :tenantId AND i.status IN ('OPEN', 'ACKNOWLEDGED')")
    List<Incident> findActiveByTenantId(@Param("tenantId") UUID tenantId);

    /**
     * Find all active incidents for a specific application.
     */
    @Query("SELECT i FROM Incident i WHERE i.tenantId = :tenantId AND i.appId = :appId AND i.status IN ('OPEN', 'ACKNOWLEDGED')")
    List<Incident> findActiveByTenantIdAndAppId(@Param("tenantId") UUID tenantId, @Param("appId") UUID appId);

    /**
     * Find incidents that should be auto-closed (no activity for quiet window).
     */
    @Query("SELECT i FROM Incident i WHERE i.tenantId = :tenantId AND i.status IN ('OPEN', 'ACKNOWLEDGED') AND i.lastSeen < :cutoff")
    List<Incident> findIncidentsToAutoClose(@Param("tenantId") UUID tenantId, @Param("cutoff") Instant cutoff);

    /**
     * Find closed incidents that can be reopened (within reopen window).
     */
    @Query("SELECT i FROM Incident i WHERE i.tenantId = :tenantId AND i.incidentKey = :incidentKey AND i.status = 'CLOSED' AND i.lastClosedAt > :reopenCutoff")
    Optional<Incident> findReopenableIncident(
        @Param("tenantId") UUID tenantId,
        @Param("incidentKey") String incidentKey,
        @Param("reopenCutoff") Instant reopenCutoff
    );

    /**
     * Find incidents by classification label.
     */
    List<Incident> findByTenantIdAndClassificationLabel(UUID tenantId, IncidentClassification classificationLabel);

    /**
     * Find top N incidents by severity and last seen (for reports).
     */
    @Query("SELECT i FROM Incident i WHERE i.tenantId = :tenantId AND i.status IN ('OPEN', 'ACKNOWLEDGED') ORDER BY i.severity DESC, i.lastSeen DESC")
    List<Incident> findTopActiveIncidents(@Param("tenantId") UUID tenantId);

    /**
     * Find incidents created or updated since a specific time (for hourly reports).
     */
    @Query("SELECT i FROM Incident i WHERE i.tenantId = :tenantId AND i.updatedAt >= :since ORDER BY i.updatedAt DESC")
    List<Incident> findUpdatedSince(@Param("tenantId") UUID tenantId, @Param("since") Instant since);

    /**
     * Find reopened incidents (for reports).
     */
    @Query("SELECT i FROM Incident i WHERE i.tenantId = :tenantId AND i.classificationLabel = 'REOPENED' AND i.updatedAt >= :since")
    List<Incident> findReopenedSince(@Param("tenantId") UUID tenantId, @Param("since") Instant since);

    /**
     * Count active incidents by application.
     */
    @Query("SELECT i.appId, COUNT(i) FROM Incident i WHERE i.tenantId = :tenantId AND i.status IN ('OPEN', 'ACKNOWLEDGED') GROUP BY i.appId")
    List<Object[]> countActiveByApp(@Param("tenantId") UUID tenantId);

    /**
     * Count incidents by classification label.
     */
    @Query("SELECT i.classificationLabel, COUNT(i) FROM Incident i WHERE i.tenantId = :tenantId AND i.updatedAt >= :since GROUP BY i.classificationLabel")
    List<Object[]> countByClassificationSince(@Param("tenantId") UUID tenantId, @Param("since") Instant since);

    /**
     * Update incident status (batch operation).
     */
    @Modifying
    @Query("UPDATE Incident i SET i.status = :status, i.updatedAt = :now WHERE i.incidentId = :incidentId")
    int updateStatus(@Param("incidentId") UUID incidentId, @Param("status") IncidentStatus status, @Param("now") Instant now);

    /**
     * Update last_seen for active incidents (touch operation).
     */
    @Modifying
    @Query("UPDATE Incident i SET i.lastSeen = :now, i.updatedAt = :now WHERE i.tenantId = :tenantId AND i.incidentKey = :incidentKey AND i.status IN ('OPEN', 'ACKNOWLEDGED')")
    int touchIncident(@Param("tenantId") UUID tenantId, @Param("incidentKey") String incidentKey, @Param("now") Instant now);

    /**
     * Check if an incident with the given key exists and is active.
     */
    @Query("SELECT COUNT(i) > 0 FROM Incident i WHERE i.tenantId = :tenantId AND i.incidentKey = :incidentKey AND i.status IN ('OPEN', 'ACKNOWLEDGED')")
    boolean existsActiveIncident(@Param("tenantId") UUID tenantId, @Param("incidentKey") String incidentKey);

    /**
     * Find all incidents for a tenant ordered by last_seen descending.
     */
    List<Incident> findByTenantIdOrderByLastSeenDesc(UUID tenantId);
}
