package org.kfh.aiops.normalization.fingerprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kfh.aiops.platform.redis.RedisConnectionProvider;
import org.kfh.aiops.platform.redis.RedisConnectionSettings;
import org.kfh.aiops.platform.redis.RedisSettingsResolver;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FingerprintDedupServiceTest {

    @Mock
    private RedisSettingsResolver resolver;

    @Mock
    private RedisConnectionProvider provider;

    private TenantContext ctx() {
        return new TenantContext(UUID.randomUUID(), UUID.randomUUID(), "KW", "PROD", "corr-1", Set.of());
    }

    private RedisConnectionSettings settings() {
        return new RedisConnectionSettings("10.0.0.5", 6379, "", "pw", false, true, "Infrastructure Server 1");
    }

    private FingerprintDedupService service() {
        return new FingerprintDedupService(resolver, provider, 600);
    }

    @Test
    void treatsAsNewWhenRedisNotConfigured() {
        var ctx = ctx();
        when(resolver.resolve(ctx)).thenReturn(Optional.empty());

        assertThat(service().isFirstOccurrence(ctx, "SCOM", "KW-APP-014", "CPU_HIGH")).isTrue();
    }

    @Test
    void returnsTrueOnFirstOccurrence() {
        var ctx = ctx();
        when(resolver.resolve(ctx)).thenReturn(Optional.of(settings()));
        when(provider.execute(any(), any())).thenReturn(true);

        assertThat(service().isFirstOccurrence(ctx, "SCOM", "KW-APP-014", "CPU_HIGH")).isTrue();
    }

    @Test
    void returnsFalseOnDuplicate() {
        var ctx = ctx();
        when(resolver.resolve(ctx)).thenReturn(Optional.of(settings()));
        when(provider.execute(any(), any())).thenReturn(false);

        assertThat(service().isFirstOccurrence(ctx, "SCOM", "KW-APP-014", "CPU_HIGH")).isFalse();
    }

    @Test
    void failsOpenWhenRedisUnavailable() {
        var ctx = ctx();
        when(resolver.resolve(ctx)).thenReturn(Optional.of(settings()));
        when(provider.execute(any(), any())).thenThrow(new RuntimeException("connection refused"));

        assertThat(service().isFirstOccurrence(ctx, "SCOM", "KW-APP-014", "CPU_HIGH")).isTrue();
    }
}
