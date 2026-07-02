package org.kfh.aiops.commandcenter.alerts;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.kfh.aiops.commandcenter.dto.PageResponse;
import org.kfh.aiops.commandcenter.support.CommandCenterReadModel;
import org.kfh.aiops.commandcenter.support.UiQuerySupport;
import org.kfh.aiops.index.IndexSearchService;
import org.kfh.aiops.index.model.IndexQuery;
import org.kfh.aiops.index.model.TelemetryDocument;
import org.kfh.aiops.index.model.TelemetryKind;
import org.kfh.aiops.platform.audit.AuditService;
import org.kfh.aiops.platform.country.CountryAccessGuard;
import org.kfh.aiops.platform.exception.NotFoundException;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Alerts are served <b>live from the Custom Index</b> (the ingested BMC/SCOM telemetry, causal-funnel
 * Stage&nbsp;3 output), so enabling a connector and letting it collect surfaces real alerts on the
 * Alert Explorer page. If the index is unavailable the read falls back to the in-memory read model
 * (empty of demo data), so the page degrades to an empty state rather than erroring.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    private static final int MAX_HITS = 500;

    private final CommandCenterReadModel readModel;
    private final CountryAccessGuard countryGuard;
    private final AuditService auditService;
    private final IndexSearchService indexSearchService;
    private final int windowHours;

    public AlertService(CommandCenterReadModel readModel, CountryAccessGuard countryGuard, AuditService auditService,
                        IndexSearchService indexSearchService,
                        @Value("${kfh.alerts.window-hours:168}") int windowHours) {
        this.readModel = readModel;
        this.countryGuard = countryGuard;
        this.auditService = auditService;
        this.indexSearchService = indexSearchService;
        this.windowHours = windowHours > 0 ? windowHours : 168;
    }

    public PageResponse<Map<String, Object>> list(TenantContext ctx, String country, String environment, int page, int size) {
        ctx.requirePermission("ALERT_READ");
        var scopedCountry = UiQuerySupport.country(ctx, countryGuard, country);
        var scopedEnv = UiQuerySupport.environment(ctx, environment);
        try {
            var to = Instant.now();
            var from = to.minus(windowHours, ChronoUnit.HOURS);
            var query = new IndexQuery(List.of(TelemetryKind.ALERTS), scopedCountry, scopedEnv, from, to,
                    null, null, null, null, null, null, null, null, 0, MAX_HITS);
            var hits = indexSearchService.search(ctx, query).hits();
            var rows = hits.stream()
                    .sorted(Comparator.comparing(TelemetryDocument::timestamp,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .map(AlertService::toRow)
                    .collect(Collectors.toList());
            return PageResponse.of(rows, page, size);
        } catch (RuntimeException ex) {
            log.warn("[ALERTS] live index read failed ({}); falling back to read model", ex.getMessage());
            return PageResponse.of(readModel.alerts(scopedCountry, scopedEnv), page, size);
        }
    }

    /** Map an indexed {@link TelemetryDocument} into the row shape the Alerts UI's {@code mapAlert} consumes. */
    private static Map<String, Object> toRow(TelemetryDocument d) {
        Map<String, Object> attrs = d.attributes() == null ? Map.of() : d.attributes();
        var row = new LinkedHashMap<String, Object>();
        row.put("id", d.id());
        row.put("timestamp", d.timestamp() == null ? null : d.timestamp().toString());
        row.put("severity", d.severity());
        row.put("sourceSystem", d.sourceSystem());
        row.put("resourceId", d.resourceId());
        row.put("resourceType", d.resourceType());
        row.put("serviceId", d.serviceId());
        row.put("category", attrs.getOrDefault("category", d.resourceType()));
        row.put("message", d.message());
        row.put("count", attrs.getOrDefault("count", attrs.getOrDefault("dedupeCount", 1)));
        row.put("incidentId", attrs.get("incidentId"));
        row.put("status", attrs.getOrDefault("status", "OPEN"));
        row.put("countryCode", d.countryCode());
        row.put("environment", d.environment());
        row.put("attributes", attrs);
        return row;
    }

    public Map<String, Object> get(TenantContext ctx, UUID id) {
        ctx.requirePermission("ALERT_READ");
        var alert = readModel.findAlert(id);
        if (alert.isEmpty()) {
            throw new NotFoundException("Alert not found");
        }
        countryGuard.requireAccess(ctx, String.valueOf(alert.get("countryCode")));
        return alert;
    }

    public Map<String, Object> acknowledge(TenantContext ctx, List<UUID> ids) {
        ctx.requirePermission("ALERT_ACKNOWLEDGE");
        ids.forEach(id -> readModel.update(readModel.alertMap(), id, Map.of("status", "ACKNOWLEDGED")));
        auditService.recordWrite(ctx, "ALERT_ACKNOWLEDGED", "Alert", ids.toString(), null, Map.of("ids", ids));
        readModel.appendAudit(ctx, "ALERT_ACKNOWLEDGED", "Alert", ids.toString());
        return Map.of("acknowledged", ids.size(), "correlationId", ctx.correlationId());
    }

    public List<Map<String, Object>> activity(TenantContext ctx) {
        ctx.requirePermission("ALERT_READ");
        return readModel.audit(ctx).stream().filter(row -> String.valueOf(row.get("action")).contains("ALERT")).toList();
    }
}
