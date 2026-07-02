package org.kfh.aiops.rca.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.kfh.aiops.rca.CorrelationResult;
import org.kfh.aiops.rca.CorrelationService;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CorrelationControllerTest {

    @Mock
    private CorrelationService correlationService;

    private TenantContext ctx() {
        return new TenantContext(UUID.randomUUID(), UUID.randomUUID(), "KW", "PROD", "corr", Set.of("ALERT_READ"));
    }

    @Test
    void delegatesWithRequestedWindow() {
        var ctx = ctx();
        var expected = new CorrelationResult(List.of(), List.of(), 0, 0);
        when(correlationService.correlateWindow(any(), any(), any())).thenReturn(expected);

        var result = new CorrelationController(correlationService).correlate(ctx, 120);

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(correlationService).correlateWindow(eq(ctx), from.capture(), to.capture());
        assertThat(Duration.between(from.getValue(), to.getValue()).toMinutes()).isEqualTo(120);
    }

    @Test
    void capsWindowToSevenDaysAndFloorsToOneMinute() {
        var ctx = ctx();
        when(correlationService.correlateWindow(any(), any(), any())).thenReturn(new CorrelationResult(List.of(), List.of(), 0, 0));
        var controller = new CorrelationController(correlationService);

        controller.correlate(ctx, 999_999);
        controller.correlate(ctx, 0);

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(correlationService, org.mockito.Mockito.times(2)).correlateWindow(any(), from.capture(), to.capture());
        var mins = new java.util.ArrayList<Long>();
        for (int i = 0; i < from.getAllValues().size(); i++) {
            mins.add(Duration.between(from.getAllValues().get(i), to.getAllValues().get(i)).toMinutes());
        }
        assertThat(mins).containsExactly(10_080L, 1L);
    }
}
