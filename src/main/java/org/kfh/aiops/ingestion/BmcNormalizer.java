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
 * Normalizes BMC Helix events into {@link TelemetryDocument}. Primary field names come from the
 * Helix Events REST API {@code _source} (docs/BMC_Helix_response.md): {@code creation_time},
 * {@code severity}, {@code class}, {@code source_hostname}, {@code msg}, {@code alert_name},
 * {@code _service_name}, {@code _impacted_service_name}; legacy {@code mc_*} cell fields are accepted
 * as fallbacks. The BMC severity ladder (CRITICAL/MAJOR/MINOR/WARNING/INFO/OK) maps onto the canonical
 * CRITICAL/HIGH/MEDIUM/LOW/INFO scale.
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
        var id = RawFields.strOr(raw, UUID.randomUUID().toString(), "_id", "id", "mc_ueid", "event_id");
        var timestamp = RawFields.instant(raw, Instant.now(),
                "creation_time", "mc_arrival_time", "date_reception", "mc_incident_time", "timestamp");
        // CI/node the alert is about — the topology key. Prefer the affected host, then service.
        var resourceId = RawFields.strOr(raw, "UNKNOWN",
                "source_hostname", "source_address", "mc_object", "mc_host", "mc_smc_id", "object");
        var resourceType = RawFields.str(raw, "class", "mc_object_class", "object_class");
        var severity = mapSeverity(RawFields.str(raw, "severity", "mc_priority"));
        var message = RawFields.str(raw, "msg", "mc_long_msg", "message", "alert_name");
        var serviceId = RawFields.joined(raw, "_service_name", "service_name");
        var applicationId = RawFields.joined(raw, "_impacted_service_name", "mc_smc_alias", "application", "app");
        var errorCode = RawFields.strOr(raw, "BMC_EVENT", "alert_name", "class", "mc_parameter", "event_class");

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("errorCode", errorCode);
        putIfPresent(attributes, "status", RawFields.str(raw, "status"));
        putIfPresent(attributes, "alertName", RawFields.str(raw, "alert_name"));
        putIfPresent(attributes, "serviceName", serviceId);
        putIfPresent(attributes, "impactedService", applicationId);
        putIfPresent(attributes, "sourceAddress", RawFields.str(raw, "source_address"));
        putIfPresent(attributes, "host", RawFields.str(raw, "source_hostname", "mc_host"));
        putIfPresent(attributes, "parameter", RawFields.str(raw, "mc_parameter"));
        if (raw.get("mc_parameter_value") != null) {
            attributes.put("parameterValue", raw.get("mc_parameter_value"));
        }

        return new TelemetryDocument(id, timestamp, ctx.tenantId(), ctx.countryCode(), ctx.environment(),
                TelemetryKind.ALERTS, SOURCE, applicationId, serviceId, resourceId, resourceType, severity,
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
