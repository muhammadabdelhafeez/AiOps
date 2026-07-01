package org.kfh.aiops.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.index.model.TelemetryKind;
import org.kfh.aiops.platform.tenant.TenantContext;

class BmcNormalizerTest {

    private final BmcNormalizer normalizer = new BmcNormalizer();

    private static TenantContext ctx() {
        return new TenantContext(UUID.randomUUID(), UUID.randomUUID(), "KW", "PROD", "corr-bmc",
                Set.of("ALERT_INGEST"));
    }

    @Test
    void mapsBmcOracleEventToCanonicalDocument() {
        // Worked example: BMC Helix event for Oracle buffer-busy waits.
        Map<String, Object> raw = new HashMap<>();
        raw.put("mc_ueid", "bmc-evt-9001");
        raw.put("mc_arrival_time", 1_719_822_000L); // epoch seconds
        raw.put("mc_object", "ORACLE-CORE-01");
        raw.put("mc_object_class", "OracleDatabase");
        raw.put("severity", "MAJOR");
        raw.put("mc_parameter", "buffer_busy_waits");
        raw.put("mc_parameter_value", 480);
        raw.put("mc_host", "db-host-07");
        raw.put("msg", "Buffer busy waits +480% on ORACLE-CORE-01");
        raw.put("mc_smc_alias", "CoreBanking");

        var doc = normalizer.normalize(ctx(), raw);

        assertThat(doc.id()).isEqualTo("bmc-evt-9001");
        assertThat(doc.sourceSystem()).isEqualTo("BMC");
        assertThat(doc.kind()).isEqualTo(TelemetryKind.ALERTS);
        assertThat(doc.countryCode()).isEqualTo("KW");
        assertThat(doc.environment()).isEqualTo("PROD");
        assertThat(doc.resourceId()).isEqualTo("ORACLE-CORE-01");
        assertThat(doc.resourceType()).isEqualTo("OracleDatabase");
        assertThat(doc.severity()).isEqualTo("HIGH"); // MAJOR -> HIGH
        assertThat(doc.applicationId()).isEqualTo("CoreBanking");
        assertThat(doc.message()).contains("Buffer busy waits");
        assertThat(doc.timestamp().getEpochSecond()).isEqualTo(1_719_822_000L);
        assertThat(doc.attributes())
                .containsEntry("errorCode", "buffer_busy_waits")
                .containsEntry("host", "db-host-07")
                .containsEntry("parameterValue", 480);
    }

    @Test
    void mapsBmcSeverityLadderToCanonicalScale() {
        assertThat(severityFor("CRITICAL")).isEqualTo("CRITICAL");
        assertThat(severityFor("MAJOR")).isEqualTo("HIGH");
        assertThat(severityFor("MINOR")).isEqualTo("MEDIUM");
        assertThat(severityFor("WARNING")).isEqualTo("LOW");
        assertThat(severityFor("OK")).isEqualTo("INFO");
        assertThat(severityFor("something-weird")).isEqualTo("MEDIUM"); // safe default
    }

    @Test
    void generatesIdAndUsesFallbacksForSparseEvent() {
        var doc = normalizer.normalize(ctx(), new HashMap<>());

        assertThat(doc.id()).isNotBlank();
        assertThat(doc.resourceId()).isEqualTo("UNKNOWN");
        assertThat(doc.severity()).isEqualTo("INFO");
        assertThat(doc.attributes()).containsEntry("errorCode", "BMC_EVENT");
    }

    private String severityFor(String bmcSeverity) {
        Map<String, Object> raw = new HashMap<>();
        raw.put("severity", bmcSeverity);
        return normalizer.normalize(ctx(), raw).severity();
    }
}
