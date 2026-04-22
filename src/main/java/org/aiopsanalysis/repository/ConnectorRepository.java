package org.aiopsanalysis.repository;

import org.aiopsanalysis.domain.postgres.Connector;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Connector entities with tenant-scoped queries.
 */
@Repository
public interface ConnectorRepository extends JpaRepository<Connector, UUID> {

    /**
     * Find all connectors for a tenant.
     */
    List<Connector> findByTenantIdOrderByUpdatedAtDesc(UUID tenantId);

    /**
     * Find all connectors for a tenant with pagination.
     */
    Page<Connector> findByTenantIdOrderByUpdatedAtDesc(UUID tenantId, Pageable pageable);

    /**
     * Find connector by ID and tenant ID (prevents IDOR).
     */
    Optional<Connector> findByConnectorIdAndTenantId(UUID connectorId, UUID tenantId);

    /**
     * Find connector by name within a tenant.
     */
    Optional<Connector> findByTenantIdAndName(UUID tenantId, String name);

    /**
     * Find connectors by type within a tenant.
     */
    List<Connector> findByTenantIdAndType(UUID tenantId, String type);

    /**
     * Find enabled connectors for a tenant.
     */
    List<Connector> findByTenantIdAndEnabled(UUID tenantId, Boolean enabled);

    /**
     * Search connectors by name or type.
     */
    @Query("SELECT c FROM Connector c WHERE c.tenantId = :tenantId " +
           "AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(c.type) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<Connector> searchByTenantId(@Param("tenantId") UUID tenantId, @Param("search") String search);

    /**
     * Filter connectors by type and enabled status.
     */
    @Query("SELECT c FROM Connector c WHERE c.tenantId = :tenantId " +
           "AND (:type IS NULL OR c.type = :type) " +
           "AND (:enabled IS NULL OR c.enabled = :enabled) " +
           "ORDER BY c.updatedAt DESC")
    List<Connector> findByTenantIdWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("type") String type,
            @Param("enabled") Boolean enabled);

    /**
     * Check if connector name exists for tenant.
     */
    boolean existsByTenantIdAndName(UUID tenantId, String name);

    /**
     * Check if connector name exists for tenant (excluding specific connector).
     */
    @Query("SELECT COUNT(c) > 0 FROM Connector c WHERE c.tenantId = :tenantId AND c.name = :name AND c.connectorId != :connectorId")
    boolean existsByTenantIdAndNameExcluding(@Param("tenantId") UUID tenantId, @Param("name") String name, @Param("connectorId") UUID connectorId);

    /**
     * Delete connector by ID and tenant ID (prevents IDOR).
     */
    @Modifying
    @Query("DELETE FROM Connector c WHERE c.connectorId = :connectorId AND c.tenantId = :tenantId")
    int deleteByConnectorIdAndTenantId(@Param("connectorId") UUID connectorId, @Param("tenantId") UUID tenantId);

    /**
     * Count connectors by type for a tenant.
     */
    @Query("SELECT c.type, COUNT(c) FROM Connector c WHERE c.tenantId = :tenantId GROUP BY c.type")
    List<Object[]> countByType(@Param("tenantId") UUID tenantId);

    /**
     * Count enabled vs disabled connectors.
     */
    @Query("SELECT c.enabled, COUNT(c) FROM Connector c WHERE c.tenantId = :tenantId GROUP BY c.enabled")
    List<Object[]> countByEnabled(@Param("tenantId") UUID tenantId);
}
