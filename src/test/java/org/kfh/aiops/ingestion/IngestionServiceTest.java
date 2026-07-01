package org.kfh.aiops.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kfh.aiops.index.IndexWriterService;
import org.kfh.aiops.index.model.TelemetryDocument;
import org.kfh.aiops.normalization.fingerprint.FingerprintDedupService;
import org.kfh.aiops.platform.exception.ForbiddenAccessException;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock
    private FingerprintDedupService dedupService;

    @Mock
    private IndexWriterService indexWriterService;

    private final BmcNormalizer bmc = new BmcNormalizer();

    private IngestionService service() {
        return new IngestionService(dedupService, indexWriterService);
    }

    private static TenantContext ctx() {
        return new TenantContext(UUID.randomUUID(), UUID.randomUUID(), "KW", "PROD", "corr-ingest",
                Set.of("ALERT_INGEST"));
    }

    @Test
    void normalizesDedupesAndIndexesFreshEventsOnly() {
        var ctx = ctx();
        // Two fresh events, one duplicate (dedup returns false on the 3rd).
        when(dedupService.isFirstOccurrence(any(), any(), any(), any()))
                .thenReturn(true, true, false);
        when(indexWriterService.index(any(), anyList()))
                .thenAnswer(invocation -> ((List<?>) invocation.getArgument(1)).size());

        var batch = List.of(
                Map.<String, Object>of("mc_object", "ORACLE-CORE-01", "severity", "MAJOR", "mc_parameter", "buffer_busy_waits"),
                Map.<String, Object>of("mc_object", "API-GATEWAY", "severity", "CRITICAL", "mc_parameter", "http_502"),
                Map.<String, Object>of("mc_object", "ORACLE-CORE-01", "severity", "MAJOR", "mc_parameter", "buffer_busy_waits"));

        var result = service().ingest(ctx, bmc, batch);

        assertThat(result.received()).isEqualTo(3);
        assertThat(result.normalized()).isEqualTo(3);
        assertThat(result.duplicatesDropped()).isEqualTo(1);
        assertThat(result.indexed()).isEqualTo(2);
        assertThat(result.failed()).isZero();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TelemetryDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(indexWriterService).index(any(), captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void countsUnmappableEventsAsFailedWithoutAbortingBatch() {
        var ctx = ctx();
        TelemetryNormalizer boom = new TelemetryNormalizer() {
            @Override
            public String sourceSystem() {
                return "BMC";
            }

            @Override
            public TelemetryDocument normalize(TenantContext c, Map<String, Object> raw) {
                if (raw.containsKey("break")) {
                    throw new IllegalStateException("unmappable");
                }
                return bmc.normalize(c, raw);
            }
        };
        when(dedupService.isFirstOccurrence(any(), any(), any(), any())).thenReturn(true);
        when(indexWriterService.index(any(), anyList()))
                .thenAnswer(invocation -> ((List<?>) invocation.getArgument(1)).size());

        var batch = List.of(
                Map.<String, Object>of("mc_object", "ORACLE-CORE-01", "severity", "MAJOR"),
                Map.<String, Object>of("break", true));

        var result = service().ingest(ctx, boom, batch);

        assertThat(result.received()).isEqualTo(2);
        assertThat(result.normalized()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.indexed()).isEqualTo(1);
    }

    @Test
    void returnsEmptyForEmptyBatch() {
        var result = service().ingest(ctx(), bmc, List.of());

        assertThat(result).isEqualTo(IngestionResult.empty());
        verifyNoInteractions(dedupService, indexWriterService);
    }

    @Test
    void requiresAlertIngestPermission() {
        var noPerm = new TenantContext(UUID.randomUUID(), UUID.randomUUID(), "KW", "PROD", "corr", Set.of("ALERT_READ"));

        assertThatThrownBy(() -> service().ingest(noPerm, bmc,
                List.of(Map.<String, Object>of("mc_object", "X"))))
                .isInstanceOf(ForbiddenAccessException.class)
                .hasMessageContaining("ALERT_INGEST");
    }
}
