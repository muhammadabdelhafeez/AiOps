package org.kfh.aiops.incident.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.kfh.aiops.commandcenter.dto.PageResponse;
import org.kfh.aiops.commandcenter.dto.UiWriteRequest;
import org.kfh.aiops.commandcenter.support.CommandCenterReadModel;
import org.kfh.aiops.commandcenter.support.UiQuerySupport;
import org.kfh.aiops.incident.model.IncidentStatus;
import org.kfh.aiops.platform.audit.AuditService;
import org.kfh.aiops.platform.country.CountryAccessGuard;
import org.kfh.aiops.platform.exception.NotFoundException;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.kfh.aiops.rca.CorrelatedEvidence;
import org.kfh.aiops.rca.CorrelatedIncident;
import org.kfh.aiops.rca.CorrelationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    private final CommandCenterReadModel readModel;
    private final CountryAccessGuard countryGuard;
    private final AuditService auditService;
    private final CorrelationService correlationService;
    private final int windowHours;

    public IncidentService(CommandCenterReadModel readModel, CountryAccessGuard countryGuard,
            AuditService auditService, CorrelationService correlationService,
            @Value("${kfh.incidents.window-hours:24}") int windowHours) {
        this.readModel = readModel;
        this.countryGuard = countryGuard;
        this.auditService = auditService;
        this.correlationService = correlationService;
        this.windowHours = windowHours > 0 ? windowHours : 24;
    }

    /**
     * Incidents are produced live by the correlation engine over the trailing window: it reads the
     * ingested alerts from the Custom Index, groups them by shared causal path over the topology, and
     * yields candidate incidents (root cause + impacted apps + evidence). Falls back to the (empty)
     * read model if correlation is unavailable.
     */
    public PageResponse<Map<String, Object>> list(TenantContext ctx, String country, String environment, int page, int size) {
        ctx.requirePermission("INCIDENT_READ");
        try {
            var to = Instant.now();
            var from = to.minus(windowHours, ChronoUnit.HOURS);
            var result = correlationService.correlateWindow(ctx, from, to);
            var rows = result.incidents().stream().map(IncidentService::toRow).collect(Collectors.toList());
            return PageResponse.of(rows, page, size);
        } catch (RuntimeException ex) {
            log.warn("[INCIDENTS] live correlation read failed ({}); falling back to read model", ex.getMessage());
            var rows = readModel.incidents(UiQuerySupport.country(ctx, countryGuard, country), UiQuerySupport.environment(ctx, environment));
            return PageResponse.of(rows, page, size);
        }
    }

    /** Map a live {@link CorrelatedIncident} into the row shape the Incidents UI's {@code mapIncident} consumes. */
    private static Map<String, Object> toRow(CorrelatedIncident inc) {
        var evidence = inc.evidence().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("resourceId", e.resourceId());
            m.put("componentName", e.componentName());
            m.put("severity", e.severity());
            m.put("source", e.source());
            m.put("message", e.message());
            m.put("timestamp", e.timestamp() == null ? null : e.timestamp().toString());
            return m;
        }).collect(Collectors.toList());
        var sources = inc.evidence().stream().map(CorrelatedEvidence::source)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        var row = new LinkedHashMap<String, Object>();
        row.put("id", inc.incidentKey());
        row.put("incidentKey", inc.incidentKey());
        row.put("title", inc.title());
        row.put("severity", inc.severity());
        row.put("status", "Open");
        row.put("classification", "New");
        row.put("category", "Availability");
        row.put("startedAt", inc.started() == null ? null : inc.started().toString());
        row.put("apps", inc.impactedApplications());
        row.put("impactedApplications", inc.impactedApplications());
        row.put("alerts", inc.alertCount());
        row.put("alertCount", inc.alertCount());
        row.put("rootCauseComponentName", inc.rootCauseComponentName());
        row.put("rootCauseAssetCi", inc.rootCauseAssetCi());
        row.put("sources", sources);
        row.put("evidence", evidence);
        return row;
    }

    public Map<String, Object> get(TenantContext ctx, UUID id) {
        ctx.requirePermission("INCIDENT_READ");
        return requireIncident(ctx, id);
    }

    public Map<String, Object> create(TenantContext ctx, UiWriteRequest request) {
        ctx.requirePermission("INCIDENT_CREATE");
        var fields = UiQuerySupport.fields(request);
        fields.putIfAbsent("status", IncidentStatus.NEW.name());
        fields.putIfAbsent("firstEventAt", Instant.now().toString());
        fields.putIfAbsent("lastEventAt", Instant.now().toString());
        var created = readModel.createIncident(ctx, UiQuerySupport.name(request, "New incident"), fields);
        audit(ctx, "INCIDENT_CREATED", created.get("id"));
        return created;
    }

    public Map<String, Object> update(TenantContext ctx, UUID id, UiWriteRequest request) {
        ctx.requirePermission("INCIDENT_UPDATE");
        requireIncident(ctx, id);
        var updated = readModel.updateIncident(id, UiQuerySupport.fields(request));
        audit(ctx, "INCIDENT_UPDATED", id);
        return updated;
    }

    public Map<String, Object> updateStatus(TenantContext ctx, UUID id, String status) {
        ctx.requirePermission("INCIDENT_UPDATE");
        IncidentStatus.valueOf(status.toUpperCase());
        requireIncident(ctx, id);
        var updated = readModel.updateIncident(id, Map.of("status", status.toUpperCase(), "lastStatusChangedAt", Instant.now().toString()));
        audit(ctx, "INCIDENT_STATUS_UPDATED", id);
        return updated;
    }

    public List<Map<String, Object>> evidence(TenantContext ctx, UUID id) {
        ctx.requirePermission("INCIDENT_READ");
        var incident = requireIncident(ctx, id);
        return List.of(
                Map.of("id", id + ":e1", "type", "METRIC", "summary", "Storage latency started before downstream failures", "observedAt", incident.get("createdAt")),
                Map.of("id", id + ":e2", "type", "TRACE", "summary", "Transfer service DB waits align with Oracle latency", "observedAt", incident.get("updatedAt")));
    }

    public List<Map<String, Object>> related(TenantContext ctx, UUID id) {
        ctx.requirePermission("INCIDENT_READ");
        var incident = requireIncident(ctx, id);
        var appId = String.valueOf(incident.get("applicationId"));
        return readModel.incidents(ctx.countryCode(), ctx.environment()).stream()
                .filter(row -> appId.equals(String.valueOf(row.get("applicationId"))))
                .filter(row -> !id.toString().equals(String.valueOf(row.get("id"))))
                .toList();
    }

    public List<Map<String, Object>> timeline(TenantContext ctx, UUID id) {
        ctx.requirePermission("INCIDENT_READ");
        var incident = requireIncident(ctx, id);
        return List.of(
                Map.of("timestamp", incident.get("createdAt"), "eventType", "ROOT_CAUSE_SIGNAL", "summary", "Upstream dependency degradation detected"),
                Map.of("timestamp", incident.get("updatedAt"), "eventType", "INCIDENT_STATE", "summary", "Incident is " + incident.get("status")));
    }

    private Map<String, Object> requireIncident(TenantContext ctx, UUID id) {
        var incident = readModel.findIncident(id);
        if (incident.isEmpty()) {
            throw new NotFoundException("Incident not found");
        }
        countryGuard.requireAccess(ctx, String.valueOf(incident.get("countryCode")));
        return incident;
    }

    private void audit(TenantContext ctx, String action, Object id) {
        auditService.recordWrite(ctx, action, "Incident", String.valueOf(id), null, Map.of("id", String.valueOf(id)));
        readModel.appendAudit(ctx, action, "Incident", String.valueOf(id));
    }
}

