package org.aiopsanalysis.repository;

import org.aiopsanalysis.domain.postgres.ConnectorRunLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for ConnectorRunLog entities with tenant-scoped queries.
 */
@Repository
public interface ConnectorRunLogRepository extends JpaRepository<ConnectorRunLog, Long> {

    /**
     * Find logs by run ID and tenant ID.
     */
    List<ConnectorRunLog> findByTenantIdAndConnectorRunIdOrderByAtDesc(
            UUID tenantId, UUID connectorRunId, Pageable pageable);

    /**
     * Find logs by run ID and tenant ID (all logs).
     */
    List<ConnectorRunLog> findByTenantIdAndConnectorRunIdOrderByAtAsc(UUID tenantId, UUID connectorRunId);

    /**
     * Find logs by level for a run.
     */
    List<ConnectorRunLog> findByTenantIdAndConnectorRunIdAndLevelOrderByAtDesc(
            UUID tenantId, UUID connectorRunId, ConnectorRunLog.LogLevel level);
}
