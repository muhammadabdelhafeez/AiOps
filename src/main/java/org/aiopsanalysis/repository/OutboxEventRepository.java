package org.aiopsanalysis.repository;

import org.aiopsanalysis.domain.postgres.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for OutboxEvent entities.
 * Used for reliable async event processing (Outbox pattern).
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Find pending (unpublished) events for processing.
     */
    @Query("SELECT oe FROM OutboxEvent oe WHERE oe.publishedAt IS NULL ORDER BY oe.createdAt ASC")
    List<OutboxEvent> findPendingEvents(Pageable pageable);

    /**
     * Find pending events by tenant.
     */
    @Query("SELECT oe FROM OutboxEvent oe WHERE oe.tenantId = :tenantId AND oe.publishedAt IS NULL ORDER BY oe.createdAt ASC")
    List<OutboxEvent> findPendingEventsByTenant(@Param("tenantId") UUID tenantId, Pageable pageable);

    /**
     * Mark event as published.
     */
    @Modifying
    @Query("UPDATE OutboxEvent oe SET oe.publishedAt = :publishedAt WHERE oe.eventId = :eventId")
    int markAsPublished(@Param("eventId") UUID eventId, @Param("publishedAt") Instant publishedAt);

    /**
     * Delete old published events (cleanup).
     */
    @Modifying
    @Query("DELETE FROM OutboxEvent oe WHERE oe.publishedAt IS NOT NULL AND oe.publishedAt < :cutoff")
    int deleteOldPublishedEvents(@Param("cutoff") Instant cutoff);

    /**
     * Find events by aggregate (for debugging/history).
     */
    List<OutboxEvent> findByTenantIdAndAggregateTypeAndAggregateIdOrderByCreatedAtDesc(
            UUID tenantId, String aggregateType, String aggregateId);
}
