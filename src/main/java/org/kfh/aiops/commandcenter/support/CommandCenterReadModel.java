package org.kfh.aiops.commandcenter.support;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.kfh.aiops.platform.country.CountryAccessGuard;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.stereotype.Component;

/**
 * Temporary tenant-aware read model for the static frontend while JPA adapters are added.
 * It stores summaries and raw/evidence references only, never raw telemetry or secrets.
 */
@Component
public class CommandCenterReadModel {

    private final Map<UUID, Map<String, Object>> incidents = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Object>> alerts = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Object>> applications = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Object>> inventory = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Object>> connectors = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Object>> schedules = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Object>> reports = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Object>> users = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Object>> roles = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Object>> audit = new ConcurrentHashMap<>();

    public CommandCenterReadModel() {
        seed();
    }

    public List<Map<String, Object>> incidents(String country, String environment) {
        return list(incidents, country, environment);
    }

    public List<Map<String, Object>> alerts(String country, String environment) {
        return list(alerts, country, environment);
    }

    public List<Map<String, Object>> applications(String country, String environment) {
        return list(applications, country, environment);
    }

    public List<Map<String, Object>> inventory(String country, String environment) {
        return list(inventory, country, environment);
    }

    public List<Map<String, Object>> connectors(String country, String environment) {
        return list(connectors, country, environment);
    }

    public List<Map<String, Object>> schedules(String country, String environment) {
        return list(schedules, country, environment);
    }

    public List<Map<String, Object>> reports(String country, String environment) {
        return list(reports, country, environment);
    }

    public List<Map<String, Object>> users() {
        return sorted(users);
    }

    public List<Map<String, Object>> users(String country, String environment) {
        return list(users, country, environment);
    }

    public List<Map<String, Object>> roles() {
        return sorted(roles);
    }

    public List<Map<String, Object>> audit() {
        return sorted(audit);
    }

    public List<Map<String, Object>> audit(TenantContext ctx) {
        var requestedCountry = normalize(ctx.countryCode());
        var requestedEnvironment = normalize(ctx.environment());
        return sorted(audit).stream()
                .filter(row -> String.valueOf(ctx.tenantId()).equals(row.get("tenantId")))
                .filter(row -> CountryAccessGuard.ALL_COUNTRIES_SCOPE.equals(requestedCountry)
                        || requestedCountry.equals(row.get("countryCode")))
                .filter(row -> requestedEnvironment.equals(row.get("environment")))
                .toList();
    }

    public Map<String, Object> find(Map<UUID, Map<String, Object>> source, UUID id) {
        var row = source.get(id);
        return row == null ? Map.of() : copy(row);
    }

    public Map<String, Object> findIncident(UUID id) {
        return find(incidents, id);
    }

    public Map<String, Object> findAlert(UUID id) {
        return find(alerts, id);
    }

    public Map<String, Object> findApplication(UUID id) {
        return find(applications, id);
    }

    public Map<String, Object> findInventory(UUID id) {
        return find(inventory, id);
    }

    public Map<String, Object> findConnector(UUID id) {
        return find(connectors, id);
    }

    public Map<String, Object> findSchedule(UUID id) {
        return find(schedules, id);
    }

    public Map<String, Object> findReport(UUID id) {
        return find(reports, id);
    }

    public Map<String, Object> findUser(UUID id) {
        return find(users, id);
    }

    public Map<String, Object> findRole(UUID id) {
        return find(roles, id);
    }

    public Map<String, Object> findAudit(UUID id) {
        return find(audit, id);
    }

    public Map<String, Object> findAudit(TenantContext ctx, UUID id) {
        var row = findAudit(id);
        if (row.isEmpty() || !String.valueOf(ctx.tenantId()).equals(row.get("tenantId"))) {
            return Map.of();
        }
        var requestedCountry = normalize(ctx.countryCode());
        if (!CountryAccessGuard.ALL_COUNTRIES_SCOPE.equals(requestedCountry)
                && !requestedCountry.equals(row.get("countryCode"))) {
            return Map.of();
        }
        if (!normalize(ctx.environment()).equals(row.get("environment"))) {
            return Map.of();
        }
        return row;
    }

    public Map<String, Object> create(Map<UUID, Map<String, Object>> target, TenantContext ctx,
            String country, String environment, String name, Map<String, Object> fields) {
        var id = UUID.randomUUID();
        var row = row(id, country, environment, name, fields);
        row.put("tenantId", ctx.tenantId().toString());
        target.put(id, row);
        return copy(row);
    }

    public Map<String, Object> createIncident(TenantContext ctx, String name, Map<String, Object> fields) {
        return create(incidents, ctx, ctx.countryCode(), ctx.environment(), name, fields);
    }

    public Map<String, Object> createApplication(TenantContext ctx, String name, Map<String, Object> fields) {
        return create(applications, ctx, ctx.countryCode(), ctx.environment(), name, fields);
    }

    public Map<String, Object> createInventory(TenantContext ctx, String name, Map<String, Object> fields) {
        return create(inventory, ctx, ctx.countryCode(), ctx.environment(), name, fields);
    }

    public Map<String, Object> createConnector(TenantContext ctx, String name, Map<String, Object> fields) {
        return createConnector(ctx, ctx.countryCode(), ctx.environment(), name, fields);
    }

    public Map<String, Object> createConnector(TenantContext ctx, String country, String environment,
            String name, Map<String, Object> fields) {
        var safe = safeConnectorFields(fields);
        safe.putIfAbsent("secretsMask", "configured");
        return create(connectors, ctx, country, environment, name, safe);
    }

    public Map<String, Object> createSchedule(TenantContext ctx, String name, Map<String, Object> fields) {
        return create(schedules, ctx, ctx.countryCode(), ctx.environment(), name, fields);
    }

    public Map<String, Object> createUser(TenantContext ctx, String name, Map<String, Object> fields) {
        return create(users, ctx, ctx.countryCode(), ctx.environment(), name, scopedUserFields(fields));
    }

    public Map<String, Object> createRole(TenantContext ctx, String name, Map<String, Object> fields) {
        return create(roles, ctx, ctx.countryCode(), ctx.environment(), name, fields);
    }

    public Map<String, Object> update(Map<UUID, Map<String, Object>> target, UUID id, Map<String, Object> fields) {
        target.computeIfPresent(id, (key, existing) -> {
            existing.putAll(fields);
            existing.put("updatedAt", Instant.now().toString());
            return existing;
        });
        return find(target, id);
    }

    public Map<String, Object> updateIncident(UUID id, Map<String, Object> fields) {
        return update(incidents, id, fields);
    }

    public Map<String, Object> updateApplication(UUID id, Map<String, Object> fields) {
        return update(applications, id, fields);
    }

    public Map<String, Object> updateInventory(UUID id, Map<String, Object> fields) {
        return update(inventory, id, fields);
    }

    public Map<String, Object> updateConnector(UUID id, Map<String, Object> fields) {
        var safe = safeConnectorFields(fields);
        if (fields.containsKey("countryCode")) {
            safe.put("countryCode", normalize(String.valueOf(fields.get("countryCode"))));
        }
        if (fields.containsKey("environment")) {
            safe.put("environment", normalize(String.valueOf(fields.get("environment"))));
        }
        return update(connectors, id, safe);
    }

    public Map<String, Object> updateSchedule(UUID id, Map<String, Object> fields) {
        return update(schedules, id, fields);
    }

    public Map<String, Object> updateUser(UUID id, Map<String, Object> fields) {
        return update(users, id, scopedUserFields(fields));
    }

    public Map<String, Object> updateRole(UUID id, Map<String, Object> fields) {
        return update(roles, id, fields);
    }

    public void deleteApplication(UUID id) {
        applications.remove(id);
    }

    public void deleteInventory(UUID id) {
        inventory.remove(id);
    }

    public void deleteConnector(UUID id) {
        connectors.remove(id);
    }

    public void deleteSchedule(UUID id) {
        schedules.remove(id);
    }

    public void deleteUser(UUID id) {
        users.remove(id);
    }

    public void deleteRole(UUID id) {
        roles.remove(id);
    }

    public void appendAudit(TenantContext ctx, String action, String entityType, String entityId) {
        appendAudit(ctx, action, entityType, entityId, Map.of());
    }

    public void appendAudit(TenantContext ctx, String action, String entityType, String entityId,
            Map<String, Object> details) {
        var id = UUID.randomUUID();
        var fields = new LinkedHashMap<String, Object>();
        fields.put("tenantId", ctx.tenantId().toString());
        fields.put("action", action);
        fields.put("entityType", entityType);
        fields.put("entityId", entityId);
        fields.put("userId", ctx.userId().toString());
        fields.put("actorName", ctx.userId().toString());
        fields.put("category", entityType);
        fields.put("result", "Success");
        fields.put("severity", "Info");
        fields.put("message", action + " on " + entityType + " " + entityId);
        fields.put("correlationId", ctx.correlationId());
        fields.putAll(safeAuditDetails(details));
        audit.put(id, row(id, ctx.countryCode(), ctx.environment(), action, fields));
    }

    public Map<UUID, Map<String, Object>> alertMap() { return alerts; }

    private List<Map<String, Object>> list(Map<UUID, Map<String, Object>> source, String country, String environment) {
        var requestedCountry = normalize(country);
        var requestedEnvironment = normalize(environment);
        return sorted(source).stream()
                .filter(row -> CountryAccessGuard.ALL_COUNTRIES_SCOPE.equals(requestedCountry)
                        || requestedCountry.equals(row.get("countryCode")))
                .filter(row -> requestedEnvironment.equals(row.get("environment")))
                .toList();
    }

    private List<Map<String, Object>> sorted(Map<UUID, Map<String, Object>> source) {
        return source.values().stream()
                .map(CommandCenterReadModel::copy)
                .sorted(Comparator.comparing(row -> String.valueOf(row.get("updatedAt")), Comparator.reverseOrder()))
                .toList();
    }

    private static Map<String, Object> row(UUID id, String country, String environment,
            String name, Map<String, Object> fields) {
        var now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        var row = new LinkedHashMap<String, Object>();
        row.put("id", id.toString());
        row.put("countryCode", normalize(country));
        row.put("environment", normalize(environment));
        row.put("name", name);
        row.put("createdAt", now);
        row.put("updatedAt", now);
        row.putAll(fields);
        return row;
    }

    private static Map<String, Object> copy(Map<String, Object> row) {
        return new LinkedHashMap<>(row);
    }

    private static Map<String, Object> scopedUserFields(Map<String, Object> fields) {
        var safe = new LinkedHashMap<>(fields);
        safe.remove("tenantId");
        safe.remove("countryCode");
        safe.remove("countryName");
        safe.remove("environment");
        safe.remove("password");
        safe.remove("confirmPassword");
        return safe;
    }

    private static Map<String, Object> safeAuditDetails(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return Map.of();
        }
        var safe = new LinkedHashMap<String, Object>();
        details.forEach((key, value) -> {
            if (!isSecretLike(key)) {
                safe.put(key, value);
            }
        });
        return safe;
    }

    private static Map<String, Object> safeConnectorFields(Map<String, Object> fields) {
        var safe = new LinkedHashMap<String, Object>();
        if (fields == null || fields.isEmpty()) {
            return safe;
        }
        fields.forEach((key, value) -> {
            if ("tenantId".equals(key) || "countryCode".equals(key) || "countryName".equals(key)
                    || "environment".equals(key) || "secretsPlain".equals(key)) {
                return;
            }
            if ("secretsMask".equals(key) || !isSecretLike(key)) {
                safe.put(key, sanitizeConnectorValue(value));
            }
        });
        return safe;
    }

    private static Object sanitizeConnectorValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            var sanitized = new LinkedHashMap<String, Object>();
            map.forEach((nestedKey, nestedValue) -> {
                var key = String.valueOf(nestedKey);
                if ("secretsMask".equals(key) || !isSecretLike(key)) {
                    sanitized.put(key, sanitizeConnectorValue(nestedValue));
                }
            });
            return sanitized;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(CommandCenterReadModel::sanitizeConnectorValue).toList();
        }
        return value;
    }

    private static boolean isSecretLike(String key) {
        var normalized = key == null ? "" : key.toLowerCase();
        return normalized.contains("password") || normalized.contains("token") || normalized.contains("secret")
                || normalized.contains("apikey") || normalized.contains("api_key") || normalized.contains("credential");
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "PROD" : value.trim().toUpperCase();
    }

    private void seed() {
        var journey = "Mobile Banking > Fund Transfer";
        var storage = UUID.fromString("10000000-0000-0000-0000-000000000001");
        var db = UUID.fromString("10000000-0000-0000-0000-000000000002");
        var transfer = UUID.fromString("10000000-0000-0000-0000-000000000003");
        var mobileApp = UUID.fromString("20000000-0000-0000-0000-000000000001");
        inventory.put(storage, row(storage, "KW", "PROD", "SAN-STORAGE-02", Map.of(
                "resourceType", "STORAGE", "resourceRole", "STORAGE_ARRAY", "health", "DEGRADED", "applicationId", mobileApp.toString())));
        inventory.put(db, row(db, "KW", "PROD", "DB-CORE-ORACLE-01", Map.of(
                "resourceType", "DB", "resourceRole", "DB_SERVER", "health", "DEGRADED", "applicationId", mobileApp.toString())));
        inventory.put(transfer, row(transfer, "KW", "PROD", "Transfer Service", Map.of(
                "resourceType", "APP", "resourceRole", "APP_SERVER", "health", "AT_RISK", "applicationId", mobileApp.toString())));
        applications.put(mobileApp, row(mobileApp, "KW", "PROD", "Mobile Banking", Map.of(
                "code", "MOBILE_BANKING", "businessDomain", "Digital Banking", "criticality", "CRITICAL", "health", "AT_RISK")));
        var incidentId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        incidents.put(incidentId, row(incidentId, "KW", "PROD", "Fund Transfer degradation", Map.of(
                "incidentNumber", "INC-20260610-001", "title", "Mobile fund transfer timeout spike", "severity", "CRITICAL",
                "status", "OPEN", "businessJourney", journey, "applicationId", mobileApp.toString(), "rootCauseEntityId", storage.toString(),
                "confidence", 91, "summary", "Storage latency preceded DB waits and transfer service timeouts.",
                "recommendedAction", "Storage team to validate SAN-STORAGE-02 latency and Oracle wait events.")));
        var alertId = UUID.fromString("40000000-0000-0000-0000-000000000001");
        alerts.put(alertId, row(alertId, "KW", "PROD", "Storage latency high", Map.of(
                "severity", "CRITICAL", "status", "OPEN", "sourceSystem", "SolarWinds", "resourceId", storage.toString(),
                "message", "SAN-STORAGE-02 latency exceeded baseline", "rawRef", "object://evidence/kw/prod/2026-06-10/storage-latency.json.gz")));
        schedules.put(UUID.fromString("60000000-0000-0000-0000-000000000001"), row(UUID.fromString("60000000-0000-0000-0000-000000000001"), "KW", "PROD", "Hourly production collection", Map.of(
                "cronExpression", "0 0 * * * *", "timezone", "Asia/Kuwait", "enabled", true, "lastRunStatus", "SUCCESS")));
        reports.put(UUID.fromString("70000000-0000-0000-0000-000000000001"), row(UUID.fromString("70000000-0000-0000-0000-000000000001"), "KW", "PROD", "Executive RCA pack", Map.of(
                "reportType", "RCA", "incidentId", incidentId.toString(), "artifactRef", "object://reports/kw/prod/INC-20260610-001.zip")));
        users.put(UUID.fromString("80000000-0000-0000-0000-000000000001"), row(UUID.fromString("80000000-0000-0000-0000-000000000001"), "KW", "PROD", "NOC Operator", Map.of(
                "username", "noc.operator", "email", "noc.operator@example.invalid", "status", "ACTIVE", "roles", List.of("NOC_OPERATOR"))));
        roles.put(UUID.fromString("90000000-0000-0000-0000-000000000001"), row(UUID.fromString("90000000-0000-0000-0000-000000000001"), "KW", "PROD", "NOC_OPERATOR", Map.of(
                "description", "NOC triage and incident operation role", "permissions", List.of("DASHBOARD_READ", "INCIDENT_READ"))));
    }
}

