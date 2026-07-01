package org.kfh.aiops.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.index.model.IndexQuery;
import org.kfh.aiops.index.model.TelemetryDocument;
import org.kfh.aiops.index.model.TelemetryKind;

class ShardIndexTest {

    private static final Instant WIDE_FROM = Instant.EPOCH;
    private static final Instant WIDE_TO = Instant.parse("2030-01-01T00:00:00Z");

    private final ShardIndex index = ShardIndex.build(List.of(
            doc("a", "CRITICAL", "TRANSFER", "T1", "SAN storage latency 82ms"),
            doc("b", "HIGH", "ORACLE", "T1", "buffer busy waits"),
            doc("c", "MEDIUM", "TRANSFER", "T2", "db timeout error")), 100L);

    @Test
    void resolvesSeverityFilterViaPostings() {
        var hits = index.search(query("CRITICAL", null, null, null), WIDE_FROM, WIDE_TO, null);
        assertThat(hits).extracting(TelemetryDocument::id).containsExactly("a");
    }

    @Test
    void intersectsServiceAndTraceFilters() {
        var hits = index.search(query(null, "TRANSFER", "T1", null), WIDE_FROM, WIDE_TO, null);
        assertThat(hits).extracting(TelemetryDocument::id).containsExactly("a");
    }

    @Test
    void appliesFreeTextAfterPostings() {
        var hits = index.search(query(null, "TRANSFER", null, "timeout"), WIDE_FROM, WIDE_TO, null);
        assertThat(hits).extracting(TelemetryDocument::id).containsExactly("c");
    }

    @Test
    void noFiltersReturnsAll() {
        var hits = index.search(query(null, null, null, null), WIDE_FROM, WIDE_TO, null);
        assertThat(hits).hasSize(3);
    }

    @Test
    void unknownFilterValueReturnsNothing() {
        var hits = index.search(query("BOGUS", null, null, null), WIDE_FROM, WIDE_TO, null);
        assertThat(hits).isEmpty();
    }

    private static IndexQuery query(String severity, String service, String trace, String text) {
        return new IndexQuery(List.of(TelemetryKind.ALERTS), null, null, null, null, severity,
                null, null, service, null, trace, null, text, null, null);
    }

    private static TelemetryDocument doc(String id, String severity, String service, String trace, String msg) {
        return new TelemetryDocument(id, Instant.parse("2026-06-07T10:00:00Z"), UUID.randomUUID(), "KW", "PROD",
                TelemetryKind.ALERTS, "SCOM", "MOBILE", service, "SRV-1", "SERVER", severity, trace, "C1", "TX1",
                msg, "obj://raw/" + id, Map.of());
    }
}
