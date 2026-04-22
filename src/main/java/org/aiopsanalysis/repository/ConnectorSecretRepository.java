package org.aiopsanalysis.repository;

import org.aiopsanalysis.domain.postgres.ConnectorSecret;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ConnectorSecret entities with tenant-scoped queries.
 */
@Repository
public interface ConnectorSecretRepository extends JpaRepository<ConnectorSecret, UUID> {

    /**
     * Find secret by connector ID and tenant ID (prevents IDOR).
     */
    Optional<ConnectorSecret> findByConnectorIdAndTenantId(UUID connectorId, UUID tenantId);

    /**
     * Check if secret exists for connector.
     */
    boolean existsByConnectorIdAndTenantId(UUID connectorId, UUID tenantId);

    /**
     * Delete secret by connector ID and tenant ID (prevents IDOR).
     */
    @Modifying
    @Query("DELETE FROM ConnectorSecret cs WHERE cs.connectorId = :connectorId AND cs.tenantId = :tenantId")
    int deleteByConnectorIdAndTenantId(@Param("connectorId") UUID connectorId, @Param("tenantId") UUID tenantId);
}
