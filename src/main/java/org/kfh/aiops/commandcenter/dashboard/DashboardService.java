package org.kfh.aiops.commandcenter.dashboard;

import java.util.List;
import java.util.Map;
import org.kfh.aiops.commandcenter.support.CommandCenterReadModel;
import org.kfh.aiops.commandcenter.support.UiQuerySupport;
import org.kfh.aiops.platform.country.CountryAccessGuard;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final CommandCenterReadModel readModel;
    private final CountryAccessGuard countryGuard;

    public DashboardService(CommandCenterReadModel readModel, CountryAccessGuard countryGuard) {
        this.readModel = readModel;
        this.countryGuard = countryGuard;
    }

    public Map<String, Object> kpis(TenantContext ctx, String country, String environment) {
        ctx.requirePermission("DASHBOARD_READ");
        var c = UiQuerySupport.country(ctx, countryGuard, country);
        var e = UiQuerySupport.environment(ctx, environment);
        var incidents = readModel.incidents(c, e);
        var critical = incidents.stream().filter(row -> "CRITICAL".equals(row.get("severity"))).count();
        return Map.of("openIncidents", incidents.size(), "criticalIncidents", critical,
                "degradedResources", readModel.inventory(c, e).stream().filter(row -> !"HEALTHY".equals(row.get("health"))).count(),
                "sourceCount", readModel.connectors(c, e).size(), "countryCode", c, "environment", e);
    }

    public List<Map<String, Object>> trends(TenantContext ctx, String country, String environment) {
        kpis(ctx, country, environment);
        return List.of(Map.of("hour", "10:00", "incidents", 1, "alerts", 3), Map.of("hour", "11:00", "incidents", 1, "alerts", 2));
    }

    public List<Map<String, Object>> topApps(TenantContext ctx, String country, String environment) {
        ctx.requirePermission("DASHBOARD_READ");
        return readModel.applications(UiQuerySupport.country(ctx, countryGuard, country), UiQuerySupport.environment(ctx, environment));
    }

    public Map<String, Object> summary(TenantContext ctx) {
        ctx.requirePermission("DASHBOARD_READ");
        return Map.of("summary", "Banking-flow-aware command center is serving tenant-scoped KPI and RCA summaries.",
                "aiStatus", "PENDING_INTEGRATION", "correlationId", ctx.correlationId());
    }

    public List<Map<String, Object>> sources(TenantContext ctx) {
        ctx.requirePermission("DASHBOARD_READ");
        return readModel.connectors(ctx.countryCode(), ctx.environment()).stream()
                .map(row -> Map.of("sourceSystem", row.getOrDefault("pluginType", "UNKNOWN"), "status", row.getOrDefault("health", "UNKNOWN"), "count", 1))
                .toList();
    }
}

