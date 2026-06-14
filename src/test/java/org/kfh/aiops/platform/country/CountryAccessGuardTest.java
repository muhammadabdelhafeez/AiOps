package org.kfh.aiops.platform.country;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.kfh.aiops.platform.exception.ForbiddenAccessException;
import org.kfh.aiops.platform.tenant.TenantContext;

class CountryAccessGuardTest {

    private final CountryAccessGuard guard = new CountryAccessGuard(
            new CountryRegistry(Set.of("KW", "BH", "EG")));

    @Test
    void shouldDenyCrossCountryAccessWithoutGlobalPermission() {
        var ctx = ctx("KW", Set.of());
        assertThrows(ForbiddenAccessException.class, () -> guard.requireAccess(ctx, "BH"));
    }

    @Test
    void shouldAllowSameCountryAccess() {
        var ctx = ctx("KW", Set.of());
        assertDoesNotThrow(() -> guard.requireAccess(ctx, "KW"));
    }

    @Test
    void shouldAllowCrossCountryWhenGlobalPermissionGranted() {
        var ctx = ctx("KW", Set.of("COUNTRY_GLOBAL_VIEW"));
        assertDoesNotThrow(() -> guard.requireAccess(ctx, "BH"));
    }

    @Test
    void shouldAllowAllCountriesScopeWhenGlobalPermissionGranted() {
        var ctx = ctx("ALL", Set.of("COUNTRY_GLOBAL_VIEW"));
        assertDoesNotThrow(() -> guard.requireAccess(ctx, "ALL"));
    }

    @Test
    void shouldDenyAllCountriesScopeWithoutGlobalPermission() {
        var ctx = ctx("ALL", Set.of());
        assertThrows(ForbiddenAccessException.class, () -> guard.requireAccess(ctx, "ALL"));
    }

    @Test
    void shouldDenyAccessToDisabledCountry() {
        var ctx = ctx("KW", Set.of("COUNTRY_GLOBAL_VIEW"));
        assertThrows(ForbiddenAccessException.class, () -> guard.requireAccess(ctx, "ZZ"));
    }

    private static TenantContext ctx(String country, Set<String> perms) {
        return new TenantContext(UUID.randomUUID(), UUID.randomUUID(),
                country, "PROD", "corr-1", perms);
    }
}

