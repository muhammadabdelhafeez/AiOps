package org.kfh.aiops.rca;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.index.model.TelemetryDocument;
import org.kfh.aiops.index.model.TelemetryKind;
import org.kfh.aiops.topology.TopologyService;

class CorrelationServiceTest {

    private static final UUID TID = UUID.fromString("00000000-0000-4000-8000-000000000001");

    // indexSearchService is null: correlate() is pure and never touches it.
    private final CorrelationService svc = new CorrelationService(null, new TopologyService());

    private static TelemetryDocument alert(String resourceId, String sev, String src, String msg, String iso) {
        return new TelemetryDocument(UUID.randomUUID().toString(), Instant.parse(iso), TID, "KW", "PROD",
                TelemetryKind.ALERTS, src, null, null, resourceId, null, sev, null, null, null, msg, null, Map.of());
    }

    @Test
    void correlatesBmcAndScomAlertsIntoOneIncidentWithSanRootCause() {
        var alerts = List.of(
                alert("SAN-STORAGE-02", "CRITICAL", "SCOM", "LUN-04 write latency 20x", "2026-07-01T10:00:00Z"),
                alert("ORACLE-CORE-01", "HIGH", "SCOM", "buffer-busy waits +480%", "2026-07-01T10:14:00Z"),
                alert("SVC-TRANSFER", "HIGH", "BMC", "DB timeouts 1247", "2026-07-01T10:15:00Z"),
                alert("API-GATEWAY", "CRITICAL", "BMC", "HTTP 502 412/min", "2026-07-01T10:16:00Z"),
                alert("WIN-APP-07", "LOW", "SCOM", "memory 82% (unmapped CI)", "2026-07-01T10:18:00Z"));

        var r = svc.correlate(alerts);

        assertThat(r.alertsProcessed()).isEqualTo(5);
        assertThat(r.alertsMapped()).isEqualTo(4);
        assertThat(r.unmappedCis()).containsExactly("WIN-APP-07");

        assertThat(r.incidents()).hasSize(1);
        var inc = r.incidents().get(0);
        assertThat(inc.rootCauseComponentId()).isEqualTo("san-storage");
        assertThat(inc.rootCauseAssetCi()).isEqualTo("SAN-STORAGE-02");
        assertThat(inc.impactedApplications()).contains("Fund Transfer", "KFHOnline", "WAMD");
        assertThat(inc.alertCount()).isEqualTo(4);
        assertThat(inc.severity()).isEqualTo("CRITICAL");
        assertThat(inc.started()).isEqualTo(Instant.parse("2026-07-01T10:00:00Z"));
        assertThat(inc.incidentKey()).isEqualTo("KW|PROD|SAN-STORAGE");
        // evidence ordered oldest-first
        assertThat(inc.evidence().get(0).resourceId()).isEqualTo("SAN-STORAGE-02");
    }

    @Test
    void splitsDisconnectedFailuresIntoSeparateIncidents() {
        var alerts = List.of(
                alert("SAN-STORAGE-02", "CRITICAL", "SCOM", "storage latency", "2026-07-01T10:00:00Z"),
                alert("LDAP-01", "HIGH", "SCOM", "bind time +300%", "2026-07-01T09:42:00Z"));

        var r = svc.correlate(alerts);

        assertThat(r.incidents()).hasSize(2);
        var roots = r.incidents().stream().map(CorrelatedIncident::rootCauseComponentId).collect(Collectors.toList());
        assertThat(roots).containsExactlyInAnyOrder("san-storage", "ldap");
    }

    @Test
    void emptyInputProducesNoIncidents() {
        var r = svc.correlate(List.of());
        assertThat(r.incidents()).isEmpty();
        assertThat(r.alertsProcessed()).isZero();
    }
}
