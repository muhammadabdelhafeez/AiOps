package org.kfh.aiops.platform.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kfh.aiops.platform.config.SettingsService;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RedisSettingsResolverTest {

    @Mock
    private SettingsService settingsService;

    private TenantContext ctx() {
        return new TenantContext(UUID.randomUUID(), UUID.randomUUID(), "KW", "PROD", "corr-1", Set.of());
    }

    @Test
    void mapsDecryptedRowToTypedSettings() {
        var ctx = ctx();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", "Infrastructure Server 1");
        row.put("host", "10.0.0.5");
        row.put("port", 6379);
        row.put("username", "");
        row.put("password", "s3cret");
        row.put("tlsEnabled", true);
        row.put("healthIndicatorEnabled", "true");
        when(settingsService.resolveRedisConnection(ctx)).thenReturn(Optional.of(row));

        var resolved = new RedisSettingsResolver(settingsService).resolve(ctx);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().host()).isEqualTo("10.0.0.5");
        assertThat(resolved.get().port()).isEqualTo(6379);
        assertThat(resolved.get().password()).isEqualTo("s3cret");
        assertThat(resolved.get().tlsEnabled()).isTrue();
        assertThat(resolved.get().healthIndicatorEnabled()).isTrue();
    }

    @Test
    void emptyWhenNoRedisConfigured() {
        var ctx = ctx();
        when(settingsService.resolveRedisConnection(ctx)).thenReturn(Optional.empty());

        assertThat(new RedisSettingsResolver(settingsService).resolve(ctx)).isEmpty();
    }
}
