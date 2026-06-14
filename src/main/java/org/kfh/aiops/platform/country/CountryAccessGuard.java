package org.kfh.aiops.platform.country;

import org.kfh.aiops.platform.exception.ForbiddenAccessException;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.stereotype.Component;

@Component
public class CountryAccessGuard {

    public static final String GLOBAL_COUNTRY_VIEW = "COUNTRY_GLOBAL_VIEW";
    public static final String ALL_COUNTRIES_SCOPE = "ALL";

    private final CountryRegistry countryRegistry;

    public CountryAccessGuard(CountryRegistry countryRegistry) {
        this.countryRegistry = countryRegistry;
    }

    public void requireAccess(TenantContext ctx, String countryCode) {
        var requested = normalize(countryCode);
        if (ALL_COUNTRIES_SCOPE.equals(requested)) {
            if (!ctx.hasPermission(GLOBAL_COUNTRY_VIEW)) {
                throw new ForbiddenAccessException("All-country access is not permitted");
            }
            return;
        }
        if (!countryRegistry.isEnabled(requested)) {
            throw new ForbiddenAccessException("Country is not enabled: " + requested);
        }
        if (!ctx.countryCode().equals(requested) && !ctx.hasPermission(GLOBAL_COUNTRY_VIEW)) {
            throw new ForbiddenAccessException("Cross-country access is not permitted");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}

