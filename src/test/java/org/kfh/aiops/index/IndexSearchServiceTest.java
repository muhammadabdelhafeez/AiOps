package org.kfh.aiops.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kfh.aiops.index.model.IndexQuery;
import org.kfh.aiops.index.model.TelemetryDocument;
import org.kfh.aiops.index.model.TelemetryKind;
import org.kfh.aiops.platform.config.SettingsService;
import org.kfh.aiops.platform.country.CountryAccessGuard;
import org.kfh.aiops.platform.country.CountryRegistry;
import org.kfh.aiops.platform.exception.ForbiddenAccessException;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IndexSearchServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private final SegmentStore store = new SegmentStore(new ObjectMapper().registerModule(new JavaTimeModule()));

    @Mock
    private SettingsService settingsService;

    @Mock
    private CountryRegistry countryRegistry;

    private IndexSearchService search;

    @BeforeEach
    void setup(@TempDir Path root) {
        var props = new IndexProperties();
        props.getStorage().setPath(root.toString());
        props.setShardsPerDay(4);
        props.setSearchParallelism(4);
        when(settingsService.resolveIndexStorage(any())).thenReturn(Optional.empty());
        when(countryRegistry.isEnabled(any())).thenReturn(true);
        var resolver = new IndexStorageResolver(settingsService, props);
        var writer = new IndexWriterService(store, props, resolver);
        search = new IndexSearchService(new ShardIndexCache(store), props, resolver,
                new CountryAccessGuard(countryRegistry));
        writer.index(ctx(), List.of(
                doc("a", "2026-06-07T10:00:00Z", "CRITICAL", "TRANSFER", "T1", "SAN storage write latency 82ms"),
                doc("b", "2026-06-07T10:14:00Z", "HIGH", "ORACLE", "T1", "buffer busy waits high"),
                doc("c", "2026-06-07T10:15:00Z", "MEDIUM", "TRANSFER", "T2", "db timeout error"),
                doc("d", "2026-06-08T09:00:00Z", "LOW", "GATEWAY", "T3", "info heartbeat")));
    }

    @Test
    void returnsAllNewestFirst() {
        var result = search.search(ctx(), query().build());
        assertThat(result.total()).isEqualTo(4);
        assertThat(result.hits()).extracting(TelemetryDocument::id).containsExactly("d", "c", "b", "a");
    }

    @Test
    void filtersBySeverity() {
        var result = search.search(ctx(), query().severity("CRITICAL").build());
        assertThat(result.hits()).extracting(TelemetryDocument::id).containsExactly("a");
    }

    @Test
    void filtersByServiceAndTrace() {
        var result = search.search(ctx(), query().serviceId("TRANSFER").traceId("T1").build());
        assertThat(result.hits()).extracting(TelemetryDocument::id).containsExactly("a");
    }

    @Test
    void filtersByFreeTextOnMessage() {
        var result = search.search(ctx(), query().text("latency").build());
        assertThat(result.hits()).extracting(TelemetryDocument::id).containsExactly("a");
    }

    @Test
    void prunesByTimeWindow() {
        var result = search.search(ctx(), query()
                .from(Instant.parse("2026-06-07T10:10:00Z"))
                .to(Instant.parse("2026-06-07T10:59:00Z")).build());
        assertThat(result.hits()).extracting(TelemetryDocument::id).containsExactly("c", "b");
    }

    @Test
    void paginates() {
        var page0 = search.search(ctx(), query().size(2).page(0).build());
        var page1 = search.search(ctx(), query().size(2).page(1).build());
        assertThat(page0.total()).isEqualTo(4);
        assertThat(page0.hits()).extracting(TelemetryDocument::id).containsExactly("d", "c");
        assertThat(page1.hits()).extracting(TelemetryDocument::id).containsExactly("b", "a");
    }

    @Test
    void deniesCrossCountrySearchWithoutGlobalView() {
        var otherCountry = new IndexQuery(List.of(TelemetryKind.ALERTS), "BH", null, null, null, null,
                null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> search.search(ctx(), otherCountry))
                .isInstanceOf(ForbiddenAccessException.class);
    }

    private static TenantContext ctx() {
        return new TenantContext(TENANT, UUID.randomUUID(), "KW", "PROD", "corr-1", Set.of("ALERT_READ"));
    }

    private static TelemetryDocument doc(String id, String ts, String severity, String service, String trace, String msg) {
        return new TelemetryDocument(id, Instant.parse(ts), TENANT, "KW", "PROD", TelemetryKind.ALERTS,
                "SCOM", "MOBILE", service, "SRV-1", "SERVER", severity, trace, "C1", "TX1", msg, "obj://raw/" + id, Map.of());
    }

    private static QueryBuilder query() {
        return new QueryBuilder();
    }

    private static final class QueryBuilder {
        private String severity;
        private String service;
        private String trace;
        private String text;
        private Instant from;
        private Instant to;
        private Integer page;
        private Integer size;

        QueryBuilder severity(String v) { this.severity = v; return this; }
        QueryBuilder serviceId(String v) { this.service = v; return this; }
        QueryBuilder traceId(String v) { this.trace = v; return this; }
        QueryBuilder text(String v) { this.text = v; return this; }
        QueryBuilder from(Instant v) { this.from = v; return this; }
        QueryBuilder to(Instant v) { this.to = v; return this; }
        QueryBuilder page(int v) { this.page = v; return this; }
        QueryBuilder size(int v) { this.size = v; return this; }

        IndexQuery build() {
            return new IndexQuery(List.of(TelemetryKind.ALERTS), null, null, from, to, severity,
                    null, null, service, null, trace, null, text, page, size);
        }
    }
}
