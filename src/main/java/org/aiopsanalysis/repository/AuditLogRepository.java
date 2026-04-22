package org.aiopsanalysis.repository;

import org.aiopsanalysis.domain.postgres.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for AuditLog entities with tenant-scoped queries.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /**
     * Find audit logs for a tenant with pagination.
     */
    List<AuditLog> findByTenantIdOrderByAtDesc(UUID tenantId, Pageable pageable);

    /**
     * Find audit logs for an entity.
     */
    List<AuditLog> findByTenantIdAndEntityTypeAndEntityIdOrderByAtDesc(
            UUID tenantId, String entityType, String entityId);

    /**
     * Find audit logs by action.
     */
    List<AuditLog> findByTenantIdAndActionOrderByAtDesc(UUID tenantId, String action, Pageable pageable);

    /**
     * Find audit logs by user.
     */
    List<AuditLog> findByTenantIdAndActorUserIdOrderByAtDesc(UUID tenantId, UUID actorUserId, Pageable pageable);

    /**
     * Find audit logs in time range.
     */
    @Query("SELECT al FROM AuditLog al WHERE al.tenantId = :tenantId AND al.at >= :from AND al.at <= :to ORDER BY al.at DESC")
    List<AuditLog> findByTenantIdAndTimeRange(
            @Param("tenantId") UUID tenantId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}
