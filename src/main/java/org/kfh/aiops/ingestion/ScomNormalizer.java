package org.kfh.aiops.ingestion;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.kfh.aiops.index.model.TelemetryDocument;
import org.kfh.aiops.index.model.TelemetryKind;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.stereotype.Component;

/**
 * Normalizes SCOM (System Center Operations Manager) alerts into {@link TelemetryDocument}. Reads the
 * PowerShell {@code Get-SCOMAlert} property names defensively. SCOM severity is Information/Warning/
 * Error; combined with Priority (Low/Normal/High) an Error at High priority escalates to CRITICAL.
 */
@Component
public class ScomNormalizer implements TelemetryNormalizer {

    public static final String SOURCE = "SCOM";

    @Override
    public String sourceSystem() {
        return SOURCE;
    }

    @Override
    public TelemetryDocument normalize(TenantContext ctx, Map<String, Object> raw) {
        var id = RawFields.strOr(raw, UUID.randomUUID().toString(), "Id", "id");
        var timestamp = RawFields.instant(raw, Instant.now(),
                "TimeRaised", "LastModified", "TimeAdded", "timestamp");
        var resourceId = RawFields.strOr(raw, "UNKNOWN",
                "MonitoringObjectDisplayName", "MonitoringObjectPath", "NetbiosComputerName", "PrincipalName");
        var resourceType = RawFields.str(raw, "MonitoringObjectFullName", "ClassName", "MonitoringClassId");
        var severity = mapSeverity(RawFields.str(raw, "Severity"), RawFields.str(raw, "Priority"));
        var message = RawFields.str(raw, "Description", "Name");
        var errorCode = RawFields.strOr(raw, "SCOM_ALERT", "MonitoringRuleId", "ProblemId", "Name");

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("errorCode", errorCode);
        putIfPresent(attributes, "category", RawFields.str(raw, "Category"));
        putIfPresent(attributes, "priority", RawFields.str(raw, "Priority"));
        putIfPresent(attributes, "resolutionState", RawFields.str(raw, "ResolutionState"));
        putIfPresent(attributes, "managementGroup", RawFields.str(raw, "ManagementGroup"));

        return new TelemetryDocument(id, timestamp, ctx.tenantId(), ctx.countryCode(), ctx.environment(),
                TelemetryKind.ALERTS, SOURCE, null, null, resourceId, resourceType, severity,
                null, null, null, message, null, attributes);
    }

    private static void putIfPresent(Map<String, Object> attributes, String key, String value) {
        if (value != null) {
            attributes.put(key, value);
        }
    }

    private static String mapSeverity(String severity, String priority) {
        if (severity == null) {
            return "INFO";
        }
        var high = priority != null && priority.trim().equalsIgnoreCase("High");
        return switch (severity.toUpperCase(Locale.ROOT)) {
            case "ERROR", "2" -> high ? "CRITICAL" : "HIGH";
            case "WARNING", "1" -> high ? "HIGH" : "MEDIUM";
            case "INFORMATION", "INFO", "0" -> "INFO";
            default -> "MEDIUM";
        };
    }
}
