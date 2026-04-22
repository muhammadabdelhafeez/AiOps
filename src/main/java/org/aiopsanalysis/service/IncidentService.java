package org.aiopsanalysis.service;

import org.aiopsanalysis.domain.model.IncidentClassification;
import org.aiopsanalysis.domain.model.IncidentStatus;
import org.aiopsanalysis.domain.model.Severity;
import org.aiopsanalysis.domain.postgres.Incident;
import org.aiopsanalysis.domain.postgres.IncidentGroup;
import org.aiopsanalysis.domain.postgres.IncidentStatusHistory;
import org.aiopsanalysis.repository.IncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for deterministic incident lifecycle management.
 * 
 * Key responsibilities:
 * - Generate stable incident_key from tenant + app + primary group IDs
 * - Classify incidents as NEW, ONGOING, REOPENED, or NEW_KNOWN_PATTERN
 * - Handle reopen window logic (closed incidents can be reopened within window)
 * - Handle quiet window auto-close (no alerts for X hours = auto-close)
 * 
 * IMPORTANT: Classification is purely database-driven. GPT never decides new/old/reopen.
 */
@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    private final IncidentRepository incidentRepository;

    @Value("${aiops.incident.reopen-window-days:7}")
    private int reopenWindowDays;

    @Value("${aiops.incident.quiet-window-hours:6}")
    private int quietWindowHours;

    @Value("${aiops.incident.primary-groups-count:10}")
    private int primaryGroupsCount;

    public IncidentService(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    /**
     * Generate a stable incident key from tenant, app, and primary group IDs.
     * incident_key = hash(tenantId + appId + sorted(primary_group_ids))
     * 
     * This prevents "one extra minor group" from creating a new incident every hour.
     */
    public String generateIncidentKey(UUID tenantId, UUID appId, List<String> groupIds) {
        // Sort and take top N groups to ensure stability
        List<String> primaryGroups = groupIds.stream()
            .sorted()
            .limit(primaryGroupsCount)
            .collect(Collectors.toList());

        String keySource = tenantId.toString() + "|" + appId.toString() + "|" + String.join(",", primaryGroups);
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(keySource.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash).substring(0, 32); // Use first 32 chars of hex
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 not available, using simple hash", e);
            return String.valueOf(keySource.hashCode());
        }
    }

    /**
     * Process an incident candidate - creates new, updates existing, or reopens closed incident.
     * This is the main entry point for the hourly incident processing.
     * 
     * @param tenantId Tenant identifier
     * @param appId Application identifier
     * @param groupIds List of AlertGroup IDs in this incident candidate
     * @param title Incident title
     * @param severity Maximum severity from the groups
     * @param hasHistoricalPatterns Whether the AlertGroups have historical matches beyond reopen window
     * @return The created or updated incident with its classification
     */
    @Transactional
    public IncidentResult processIncidentCandidate(
            UUID tenantId,
            UUID appId,
            List<String> groupIds,
            String title,
            Severity severity,
            boolean hasHistoricalPatterns) {
        
        String incidentKey = generateIncidentKey(tenantId, appId, groupIds);
        Instant now = Instant.now();
        Instant reopenCutoff = now.minusSeconds(reopenWindowDays * 24L * 60L * 60L);

        // Step 1: Check if there's an active incident with this key
        Optional<Incident> existingActive = incidentRepository.findByTenantIdAndIncidentKey(tenantId, incidentKey)
            .filter(i -> i.getStatus().isActive());

        if (existingActive.isPresent()) {
            // ONGOING: Same incident_key exists and is OPEN/ACKNOWLEDGED
            Incident incident = existingActive.get();
            incident.touch();
            incident.setClassificationLabel(IncidentClassification.ONGOING);
            if (severity.getWeight() > incident.getSeverity().getWeight()) {
                incident.setSeverity(severity);
            }
            incidentRepository.save(incident);
            log.info("Incident {} classified as ONGOING", incident.getIncidentId());
            return new IncidentResult(incident, IncidentClassification.ONGOING, false);
        }

        // Step 2: Check if there's a closed incident that can be reopened
        Optional<Incident> reopenable = incidentRepository.findReopenableIncident(tenantId, incidentKey, reopenCutoff);
        
        if (reopenable.isPresent()) {
            // REOPENED: Was CLOSED, now active again within reopen window
            Incident incident = reopenable.get();
            IncidentStatus previousStatus = incident.getStatus();
            incident.reopen();
            incident.setSeverity(severity);
            
            // Add status history
            IncidentStatusHistory history = IncidentStatusHistory.systemChange(
                tenantId, incident.getIncidentId(), previousStatus, IncidentStatus.OPEN,
                "Incident reopened - new alerts within reopen window"
            );
            incident.getStatusHistory().add(history);
            
            incidentRepository.save(incident);
            log.info("Incident {} REOPENED (reopen count: {})", incident.getIncidentId(), incident.getReopenCount());
            return new IncidentResult(incident, IncidentClassification.REOPENED, true);
        }

        // Step 3: Create a new incident
        IncidentClassification classification = hasHistoricalPatterns 
            ? IncidentClassification.NEW_KNOWN_PATTERN 
            : IncidentClassification.NEW;

        Incident newIncident = Incident.builder()
            .tenantId(tenantId)
            .appId(appId)
            .incidentKey(incidentKey)
            .title(title)
            .status(IncidentStatus.OPEN)
            .severity(severity)
            .classificationLabel(classification)
            .firstSeen(now)
            .lastSeen(now)
            .build();

        // Add incident groups
        for (String groupId : groupIds) {
            IncidentGroup ig = new IncidentGroup(tenantId, newIncident.getIncidentId(), groupId);
            newIncident.getIncidentGroups().add(ig);
        }

        // Add initial status history
        IncidentStatusHistory history = IncidentStatusHistory.systemChange(
            tenantId, newIncident.getIncidentId(), null, IncidentStatus.OPEN,
            "Incident created - " + classification.getDisplayName()
        );
        newIncident.getStatusHistory().add(history);

        incidentRepository.save(newIncident);
        log.info("Created {} incident {} for app {}", classification, newIncident.getIncidentId(), appId);
        return new IncidentResult(newIncident, classification, true);
    }

    /**
     * Auto-close incidents that have been quiet (no new alerts) for the quiet window period.
     * This should be called at the end of each hourly run.
     */
    @Transactional
    public List<Incident> autoCloseQuietIncidents(UUID tenantId) {
        Instant quietCutoff = Instant.now().minusSeconds(quietWindowHours * 60L * 60L);
        List<Incident> incidentsToClose = incidentRepository.findIncidentsToAutoClose(tenantId, quietCutoff);

        for (Incident incident : incidentsToClose) {
            IncidentStatus previousStatus = incident.getStatus();
            incident.close();
            
            IncidentStatusHistory history = IncidentStatusHistory.systemChange(
                tenantId, incident.getIncidentId(), previousStatus, IncidentStatus.CLOSED,
                "Auto-closed after " + quietWindowHours + " hours of inactivity"
            );
            incident.getStatusHistory().add(history);
            
            log.info("Auto-closed incident {} after {} hours quiet", incident.getIncidentId(), quietWindowHours);
        }

        if (!incidentsToClose.isEmpty()) {
            incidentRepository.saveAll(incidentsToClose);
        }

        return incidentsToClose;
    }

    /**
     * Manually close an incident.
     */
    @Transactional
    public Incident closeIncident(UUID incidentId, UUID actorUserId, String notes) {
        Incident incident = incidentRepository.findById(incidentId)
            .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));

        if (!incident.getStatus().canTransitionTo(IncidentStatus.CLOSED)) {
            throw new IllegalStateException("Cannot close incident in status: " + incident.getStatus());
        }

        IncidentStatus previousStatus = incident.getStatus();
        incident.close();

        IncidentStatusHistory history = IncidentStatusHistory.userChange(
            incident.getTenantId(), incidentId, previousStatus, IncidentStatus.CLOSED, actorUserId, notes
        );
        incident.getStatusHistory().add(history);

        return incidentRepository.save(incident);
    }

    /**
     * Acknowledge an incident.
     */
    @Transactional
    public Incident acknowledgeIncident(UUID incidentId, UUID actorUserId, String notes) {
        Incident incident = incidentRepository.findById(incidentId)
            .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));

        if (!incident.getStatus().canTransitionTo(IncidentStatus.ACKNOWLEDGED)) {
            throw new IllegalStateException("Cannot acknowledge incident in status: " + incident.getStatus());
        }

        IncidentStatus previousStatus = incident.getStatus();
        incident.setStatus(IncidentStatus.ACKNOWLEDGED);
        incident.setAssignedTo(actorUserId);

        IncidentStatusHistory history = IncidentStatusHistory.userChange(
            incident.getTenantId(), incidentId, previousStatus, IncidentStatus.ACKNOWLEDGED, actorUserId, notes
        );
        incident.getStatusHistory().add(history);

        return incidentRepository.save(incident);
    }

    /**
     * Update GPT-generated summary for an incident.
     * Note: GPT only writes narrative - it never decides classification.
     */
    @Transactional
    public Incident updateProSummary(UUID incidentId, String proSummary, java.math.BigDecimal confidence) {
        Incident incident = incidentRepository.findById(incidentId)
            .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));

        incident.setProSummary(proSummary);
        incident.setConfidence(confidence);

        return incidentRepository.save(incident);
    }

    /**
     * Get all active incidents for a tenant.
     */
    public List<Incident> getActiveIncidents(UUID tenantId) {
        return incidentRepository.findActiveByTenantId(tenantId);
    }

    /**
     * Get all active incidents for a specific application.
     */
    public List<Incident> getActiveIncidentsByApp(UUID tenantId, UUID appId) {
        return incidentRepository.findActiveByTenantIdAndAppId(tenantId, appId);
    }

    /**
     * Get incident by ID.
     */
    public Optional<Incident> getIncident(UUID incidentId) {
        return incidentRepository.findById(incidentId);
    }

    /**
     * Get incidents updated since a specific time (for reports).
     */
    public List<Incident> getIncidentsUpdatedSince(UUID tenantId, Instant since) {
        return incidentRepository.findUpdatedSince(tenantId, since);
    }

    /**
     * Get reopened incidents since a specific time (for reports).
     */
    public List<Incident> getReopenedIncidentsSince(UUID tenantId, Instant since) {
        return incidentRepository.findReopenedSince(tenantId, since);
    }

    /**
     * Helper method to convert bytes to hex string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Result class for incident processing.
     */
    public record IncidentResult(
        Incident incident,
        IncidentClassification classification,
        boolean created
    ) {}
}
