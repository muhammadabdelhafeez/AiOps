package org.aiopsanalysis.controller;

import org.aiopsanalysis.domain.model.IncidentClassification;
import org.aiopsanalysis.domain.model.IncidentStatus;
import org.aiopsanalysis.domain.postgres.Incident;
import org.aiopsanalysis.service.GraphService;
import org.aiopsanalysis.service.IncidentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST Controller for Incident management.
 * 
 * Provides endpoints for:
 * - Listing and retrieving incidents
 * - Acknowledging and closing incidents
 * - Getting incident topology for visualization
 * - Incident statistics and reports
 */
@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private static final Logger log = LoggerFactory.getLogger(IncidentController.class);

    private final IncidentService incidentService;
    private final GraphService graphService;

    public IncidentController(IncidentService incidentService, GraphService graphService) {
        this.incidentService = incidentService;
        this.graphService = graphService;
    }

    /**
     * Get all active incidents for a tenant.
     */
    @GetMapping
    public ResponseEntity<List<IncidentDto>> getActiveIncidents(
            @RequestHeader("X-Tenant-Id") UUID tenantId) {
        
        List<Incident> incidents = incidentService.getActiveIncidents(tenantId);
        List<IncidentDto> dtos = incidents.stream()
            .map(this::toDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get all active incidents for a specific application.
     */
    @GetMapping("/app/{appId}")
    public ResponseEntity<List<IncidentDto>> getIncidentsByApp(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID appId) {
        
        List<Incident> incidents = incidentService.getActiveIncidentsByApp(tenantId, appId);
        List<IncidentDto> dtos = incidents.stream()
            .map(this::toDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get a single incident by ID.
     */
    @GetMapping("/{incidentId}")
    public ResponseEntity<IncidentDto> getIncident(@PathVariable UUID incidentId) {
        return incidentService.getIncident(incidentId)
            .map(this::toDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Acknowledge an incident.
     */
    @PostMapping("/{incidentId}/acknowledge")
    public ResponseEntity<IncidentDto> acknowledgeIncident(
            @PathVariable UUID incidentId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody(required = false) AcknowledgeRequest request) {
        
        try {
            String notes = request != null ? request.notes() : null;
            Incident incident = incidentService.acknowledgeIncident(incidentId, userId, notes);
            return ResponseEntity.ok(toDto(incident));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Close an incident.
     */
    @PostMapping("/{incidentId}/close")
    public ResponseEntity<IncidentDto> closeIncident(
            @PathVariable UUID incidentId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody(required = false) CloseRequest request) {
        
        try {
            String notes = request != null ? request.notes() : null;
            Incident incident = incidentService.closeIncident(incidentId, userId, notes);
            return ResponseEntity.ok(toDto(incident));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get incident topology for visualization.
     * Returns nodes[] and edges[] for graph rendering.
     */
    @GetMapping("/{incidentId}/topology")
    public ResponseEntity<TopologyDto> getIncidentTopology(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID incidentId) {
        
        GraphService.TopologyResult topology = graphService.getIncidentTopology(
            tenantId.toString(), incidentId.toString()
        );
        
        return ResponseEntity.ok(new TopologyDto(topology.nodes(), topology.edges()));
    }

    /**
     * Get incidents updated since a specific time (for polling).
     */
    @GetMapping("/updated")
    public ResponseEntity<List<IncidentDto>> getUpdatedIncidents(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(defaultValue = "1") int hoursAgo) {
        
        Instant since = Instant.now().minus(hoursAgo, ChronoUnit.HOURS);
        List<Incident> incidents = incidentService.getIncidentsUpdatedSince(tenantId, since);
        List<IncidentDto> dtos = incidents.stream()
            .map(this::toDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get reopened incidents (for reports/alerts).
     */
    @GetMapping("/reopened")
    public ResponseEntity<List<IncidentDto>> getReopenedIncidents(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(defaultValue = "24") int hoursAgo) {
        
        Instant since = Instant.now().minus(hoursAgo, ChronoUnit.HOURS);
        List<Incident> incidents = incidentService.getReopenedIncidentsSince(tenantId, since);
        List<IncidentDto> dtos = incidents.stream()
            .map(this::toDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get incident statistics summary.
     */
    @GetMapping("/stats")
    public ResponseEntity<IncidentStatsDto> getIncidentStats(
            @RequestHeader("X-Tenant-Id") UUID tenantId) {
        
        List<Incident> active = incidentService.getActiveIncidents(tenantId);
        Instant lastHour = Instant.now().minus(1, ChronoUnit.HOURS);
        List<Incident> recentlyUpdated = incidentService.getIncidentsUpdatedSince(tenantId, lastHour);
        List<Incident> reopened = incidentService.getReopenedIncidentsSince(tenantId, lastHour);
        
        // Count by classification
        Map<IncidentClassification, Long> byClassification = active.stream()
            .collect(Collectors.groupingBy(Incident::getClassificationLabel, Collectors.counting()));
        
        // Count by status
        Map<IncidentStatus, Long> byStatus = active.stream()
            .collect(Collectors.groupingBy(Incident::getStatus, Collectors.counting()));
        
        return ResponseEntity.ok(new IncidentStatsDto(
            active.size(),
            recentlyUpdated.size(),
            reopened.size(),
            byClassification,
            byStatus
        ));
    }

    /**
     * Convert Incident entity to DTO.
     */
    private IncidentDto toDto(Incident incident) {
        return new IncidentDto(
            incident.getIncidentId(),
            incident.getTenantId(),
            incident.getAppId(),
            incident.getIncidentKey(),
            incident.getTitle(),
            incident.getStatus().name(),
            incident.getStatus().getDisplayName(),
            incident.getSeverity().name(),
            incident.getClassificationLabel().name(),
            incident.getClassificationLabel().getLabel(),
            incident.getFirstSeen(),
            incident.getLastSeen(),
            incident.getLastClosedAt(),
            incident.getReopenCount(),
            incident.getAssignedTo(),
            incident.getProSummary(),
            incident.getConfidence() != null ? incident.getConfidence().doubleValue() : null,
            incident.getUpdatedAt()
        );
    }

    // DTOs
    public record IncidentDto(
        UUID incidentId,
        UUID tenantId,
        UUID appId,
        String incidentKey,
        String title,
        String status,
        String statusDisplay,
        String severity,
        String classification,
        String classificationLabel,
        Instant firstSeen,
        Instant lastSeen,
        Instant lastClosedAt,
        Integer reopenCount,
        UUID assignedTo,
        String proSummary,
        Double confidence,
        Instant updatedAt
    ) {}

    public record TopologyDto(
        List<Map<String, Object>> nodes,
        List<Map<String, Object>> edges
    ) {}

    public record IncidentStatsDto(
        int activeCount,
        int recentlyUpdatedCount,
        int reopenedCount,
        Map<IncidentClassification, Long> byClassification,
        Map<IncidentStatus, Long> byStatus
    ) {}

    public record AcknowledgeRequest(String notes) {}
    public record CloseRequest(String notes) {}
}
