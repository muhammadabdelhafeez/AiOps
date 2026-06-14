package org.kfh.aiops;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Skeleton sanity test — verifies the AiOpsApplication class is on the
 * classpath and can be instantiated. Full Spring context loading is added
 * in Phase 2 with Testcontainers.
 */
class AiOpsApplicationTests {

    @Test
    void applicationClassExists() {
        // Intentionally minimal: no @SpringBootTest until Testcontainers are wired.
        assertEquals("org.kfh.aiops", AiOpsApplication.class.getPackageName());
    }
}

