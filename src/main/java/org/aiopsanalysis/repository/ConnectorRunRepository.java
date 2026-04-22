package org.aiopsanalysis.repository;

import org.aiopsanalysis.domain.postgres.ConnectorRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ConnectorRun entities with tenant-scoped queries.
 */
@Repository
public interface ConnectorRunRepository extends JpaRepository<ConnectorRun, UUID> {

    /**
     * Find run by ID and tenant ID (prevents IDOR).
     */
    Optional<ConnectorRun> findByConnectorRunIdAndTenantId(UUID connectorRunId, UUID tenantId);

    /**
     * Find runs by connector with pagination.
     */
    List<ConnectorRun> findByTenantIdAndConnectorIdOrderByStartedAtDesc(
            UUID tenantId, UUID connectorId, Pageable pageable);

    /**
     * Find all runs for tenant with pagination.
     */
    List<ConnectorRun> findByTenantIdOrderByStartedAtDesc(UUID tenantId, Pageable pageable);

    /**
     * Find latest run for a connector (for lastRun display).
     */
    @Query("SELECT cr FROM ConnectorRun cr WHERE cr.tenantId = :tenantId AND cr.connectorId = :connectorId AND cr.runType = 'COLLECT' ORDER BY cr.startedAt DESC LIMIT 1")
    Optional<ConnectorRun> findLatestCollectRun(@Param("tenantId") UUID tenantId, @Param("connectorId") UUID connectorId);

    /**
     * Find latest test for a connector (for lastTest display).
     */
    @Query("SELECT cr FROM ConnectorRun cr WHERE cr.tenantId = :tenantId AND cr.connectorId = :connectorId AND cr.runType = 'TEST' ORDER BY cr.startedAt DESC LIMIT 1")
    Optional<ConnectorRun> findLatestTestRun(@Param("tenantId") UUID tenantId, @Param("connectorId") UUID connectorId);

    /**
     * Find latest run per connector using window function approach.
     * Returns the most recent run for each connector.
     */
    @Query(value = """
            SELECT DISTINCT ON (connector_id) * FROM ops.connector_runs
            WHERE tenant_id = :tenantId
            ORDER BY connector_id, started_at DESC
            """, nativeQuery = true)
    List<ConnectorRun> findLatestRunPerConnector(@Param("tenantId") UUID tenantId);

    /**
     * Find latest test per connector.
     */
    @Query(value = """
            SELECT DISTINCT ON (connector_id) * FROM ops.connector_runs
            WHERE tenant_id = :tenantId AND run_type = 'TEST'
            ORDER BY connector_id, started_at DESC
            """, nativeQuery = true)
    List<ConnectorRun> findLatestTestPerConnector(@Param("tenantId") UUID tenantId);

    /**
     * Find queued runs (for worker processing).
     */
    List<ConnectorRun> findByTenantIdAndStatusOrderByStartedAtAsc(UUID tenantId, ConnectorRun.Status status);

    /**
     * Count runs by status for a connector.
     */
    @Query("SELECT cr.status, COUNT(cr) FROM ConnectorRun cr WHERE cr.tenantId = :tenantId AND cr.connectorId = :connectorId GROUP BY cr.status")
    List<Object[]> countByStatus(@Param("tenantId") UUID tenantId, @Param("connectorId") UUID connectorId);

    /**
     * Find runs in time range.
     */
    @Query("SELECT cr FROM ConnectorRun cr WHERE cr.tenantId = :tenantId AND cr.startedAt >= :from AND cr.startedAt <= :to ORDER BY cr.startedAt DESC")
    List<ConnectorRun> findByTenantIdAndTimeRange(
            @Param("tenantId") UUID tenantId,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
