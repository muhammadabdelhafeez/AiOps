package org.kfh.aiops.commandcenter.support;

import java.util.LinkedHashMap;
import java.util.Map;
import org.kfh.aiops.commandcenter.dto.UiWriteRequest;
import org.kfh.aiops.platform.country.CountryAccessGuard;
import org.kfh.aiops.platform.tenant.TenantContext;

public final class UiQuerySupport {

    private UiQuerySupport() {
    }

    public static String country(TenantContext ctx, CountryAccessGuard guard, String country) {
        var requested = country == null || country.isBlank() ? ctx.countryCode() : country.trim().toUpperCase();
        guard.requireAccess(ctx, requested);
        return requested;
    }

    public static String environment(TenantContext ctx, String environment) {
        return environment == null || environment.isBlank() ? ctx.environment() : environment.trim().toUpperCase();
    }

    public static Map<String, Object> fields(UiWriteRequest request) {
        var fields = new LinkedHashMap<String, Object>();
        if (request == null) {
            return fields;
        }
        if (request.title() != null) {
            fields.put("title", request.title());
        }
        if (request.status() != null) {
            fields.put("status", request.status().toUpperCase());
        }
        if (request.severity() != null) {
            fields.put("severity", request.severity().toUpperCase());
        }
        if (request.enabled() != null) {
            fields.put("enabled", request.enabled());
        }
        if (request.attributes() != null) {
            fields.putAll(request.attributes());
        }
        return fields;
    }

    public static String name(UiWriteRequest request, String fallback) {
        return request != null && request.name() != null && !request.name().isBlank()
                ? request.name().trim()
                : fallback;
    }
}

