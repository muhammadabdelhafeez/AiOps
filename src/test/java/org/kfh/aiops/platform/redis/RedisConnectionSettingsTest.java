package org.kfh.aiops.platform.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RedisConnectionSettingsTest {

    @Test
    void requiresHost() {
        assertThatThrownBy(() -> new RedisConnectionSettings(" ", 6379, "", "", false, false, "r"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defaultsInvalidPortAndTrimsTextFields() {
        var settings = new RedisConnectionSettings(" 10.0.0.5 ", 0, " default ", "pw", true, true, " redis ");
        assertThat(settings.host()).isEqualTo("10.0.0.5");
        assertThat(settings.port()).isEqualTo(6379);
        assertThat(settings.username()).isEqualTo("default");
        assertThat(settings.name()).isEqualTo("redis");
    }

    @Test
    void cacheKeyExcludesPassword() {
        var settings = new RedisConnectionSettings("h", 6380, "u", "topsecret", true, false, "n");
        assertThat(settings.cacheKey()).isEqualTo("h:6380:true:u");
        assertThat(settings.cacheKey()).doesNotContain("topsecret");
    }

    @Test
    void cacheKeyUsesDashWhenNoUser() {
        var settings = new RedisConnectionSettings("h", 6379, "", "pw", false, false, "n");
        assertThat(settings.cacheKey()).isEqualTo("h:6379:false:-");
    }
}
