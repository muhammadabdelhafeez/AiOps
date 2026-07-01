package org.kfh.aiops.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.index.model.TelemetryKind;
import org.kfh.aiops.platform.tenant.TenantContext;

class ScomNormalizerTest {

    private final ScomNormalizer normalizer = new ScomNormalizer();

    private static TenantContext ctx() {
        return new TenantContext(UUID.randomUUID(), UUID.randomUUID(), "KW", "PROD", "corr-scom",
                Set.of("ALERT_INGEST"));
    }

    @Test
    void mapsScomStorageAlertToCanonicalDocument() {
        // Worked example: SCOM alert for SAN storage write-latency spike.
        Map<String, Object> raw = new HashMap<>();
        raw.put("Id", "scom-alert-4402");
        raw.put("TimeRaised", "2026-07-01T10:00:00Z");
        raw.put("MonitoringObjectDisplayName", "SAN-STORAGE-02");
        raw.put("MonitoringObjectFullName", "Storage.SAN.Lun");
        raw.put("Severity", "Error");
        raw.put("Priority", "High");
        raw.put("Description", "LUN-04 write latency 2ms -> 82ms");
        raw.put("MonitoringRuleId", "SAN.WriteLatency.High");
        raw.put("Category", "PerformanceHealth");

        var doc = normalizer.normalize(ctx(), raw);

        assertThat(doc.id()).isEqualTo("scom-alert-4402");
        assertThat(doc.sourceSystem()).isEqualTo("SCOM");
        assertThat(doc.kind()).isEqualTo(TelemetryKind.ALERTS);
        assertThat(doc.resourceId()).isEqualTo("SAN-STORAGE-02");
        assertThat(doc.resourceType()).isEqualTo("Storage.SAN.Lun");
        assertThat(doc.severity()).isEqualTo("CRITICAL"); // Error + High priority
        assertThat(doc.message()).contains("write latency");
        assertThat(doc.timestamp().toString()).isEqualTo("2026-07-01T10:00:00Z");
        assertThat(doc.attributes())
                .containsEntry("errorCode", "SAN.WriteLatency.High")
                .containsEntry("category", "PerformanceHealth")
                .containsEntry("priority", "High");
    }

    @Test
    void mapsScomSeverityWithPriorityEscalation() {
        assertThat(severityFor("Error", "Normal")).isEqualTo("HIGH");
        assertThat(severityFor("Error", "High")).isEqualTo("CRITICAL");
        assertThat(severityFor("Warning", "Normal")).isEqualTo("MEDIUM");
        assertThat(severityFor("Warning", "High")).isEqualTo("HIGH");
        assertThat(severityFor("Information", "Low")).isEqualTo("INFO");
    }

    private String severityFor(String severity, String priority) {
        Map<String, Object> raw = new HashMap<>();
        raw.put("Severity", severity);
        raw.put("Priority", priority);
        return normalizer.normalize(ctx(), raw).severity();
    }
}
