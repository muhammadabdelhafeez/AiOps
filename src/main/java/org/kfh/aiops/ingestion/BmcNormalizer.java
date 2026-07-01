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
 * Normalizes BMC Helix / TrueSight event-management events into {@link TelemetryDocument}. Reads the
 * common {@code mc_*} cell fields defensively and maps the BMC severity ladder
 * (CRITICAL/MAJOR/MINOR/WARNING/INFO/OK) onto the canonical CRITICAL/HIGH/MEDIUM/LOW/INFO scale.
 */
@Component
public class BmcNormalizer implements TelemetryNormalizer {

    public static final String SOURCE = "BMC";

    @Override
    public String sourceSystem() {
        return SOURCE;
    }

    @Override
    public TelemetryDocument normalize(TenantContext ctx, Map<String, Object> raw) {
        var id = RawFields.strOr(raw, UUID.randomUUID().toString(), "mc_ueid", "id", "event_id");
        var timestamp = RawFields.instant(raw, Instant.now(),
                "mc_arrival_time", "date_reception", "mc_incident_time", "timestamp");
        var resourceId = RawFields.strOr(raw, "UNKNOWN", "mc_object", "mc_host", "mc_smc_id", "object");
        var resourceType = RawFields.str(raw, "mc_object_class", "object_class");
        var severity = mapSeverity(RawFields.str(raw, "severity", "mc_priority"));
        var message = RawFields.str(raw, "msg", "mc_long_msg", "message");
        var applicationId = RawFields.str(raw, "mc_smc_alias", "application", "app");
        var errorCode = RawFields.strOr(raw, "BMC_EVENT", "mc_parameter", "mc_object_class", "event_class");

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("errorCode", errorCode);
        putIfPresent(attributes, "host", RawFields.str(raw, "mc_host"));
        putIfPresent(attributes, "tool", RawFields.str(raw, "mc_tool"));
        putIfPresent(attributes, "parameter", RawFields.str(raw, "mc_parameter"));
        if (raw.get("mc_parameter_value") != null) {
            attributes.put("parameterValue", raw.get("mc_parameter_value"));
        }

        return new TelemetryDocument(id, timestamp, ctx.tenantId(), ctx.countryCode(), ctx.environment(),
                TelemetryKind.ALERTS, SOURCE, applicationId, null, resourceId, resourceType, severity,
                null, null, null, message, null, attributes);
    }

    private static void putIfPresent(Map<String, Object> attributes, String key, String value) {
        if (value != null) {
            attributes.put(key, value);
        }
    }

    private static String mapSeverity(String raw) {
        if (raw == null) {
            return "INFO";
        }
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "CRITICAL", "5", "FATAL" -> "CRITICAL";
            case "MAJOR", "4" -> "HIGH";
            case "MINOR", "3" -> "MEDIUM";
            case "WARNING", "2" -> "LOW";
            case "INFO", "INFORMATION", "OK", "NORMAL", "1", "0" -> "INFO";
            default -> "MEDIUM";
        };
    }
}
