package org.kfh.aiops.platform.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RedisKeysTest {

    @Test
    void buildsCountryAndEnvironmentScopedDedupKey() {
        assertThat(RedisKeys.dedup("KW", "PROD", "SCOM", "KW-APP-014", "CPU_HIGH"))
                .isEqualTo("dedup:KW:PROD:SCOM:KW-APP-014:CPU_HIGH");
    }

    @Test
    void buildsHealthKey() {
        assertThat(RedisKeys.health("KW", "PROD", "server", "KW-APP-014"))
                .isEqualTo("health:KW:PROD:server:KW-APP-014");
    }

    @Test
    void rejectsBlankSegment() {
        assertThatThrownBy(() -> RedisKeys.dedup("KW", "PROD", "SCOM", "  ", "CPU_HIGH"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsColonInsideSegment() {
        assertThatThrownBy(() -> RedisKeys.dedup("KW", "PROD", "SC:OM", "ci", "code"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsControlCharactersInSegment() {
        assertThatThrownBy(() -> RedisKeys.dedup("KW", "PROD", "SCOM", "ci\ncode", "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
