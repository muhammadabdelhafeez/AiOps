package org.kfh.aiops.platform.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.kfh.aiops.platform.exception.ForbiddenAccessException;

class TenantContextTest {

    @Test
    void shouldRejectApiRequestWithoutTenantHeader() {
        assertThrows(NullPointerException.class, () ->
                new TenantContext(null, UUID.randomUUID(), "KW", "PROD", "corr-1", Set.of()));
    }

    @Test
    void shouldThrowWhenPermissionMissing() {
        var ctx = new TenantContext(UUID.randomUUID(), UUID.randomUUID(),
                "KW", "PROD", "corr-1", Set.of("INCIDENT_READ"));
        assertThrows(ForbiddenAccessException.class, () -> ctx.requirePermission("INCIDENT_CREATE"));
    }

    @Test
    void shouldAcceptWhenPermissionPresent() {
        var ctx = new TenantContext(UUID.randomUUID(), UUID.randomUUID(),
                "KW", "PROD", "corr-1", Set.of("INCIDENT_CREATE"));
        ctx.requirePermission("INCIDENT_CREATE");
        assertEquals("KW", ctx.countryCode());
    }
}

