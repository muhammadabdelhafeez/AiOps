package org.kfh.aiops.platform.tenant;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.kfh.aiops.platform.exception.MissingTenantContextException;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class TenantContextResolver implements HandlerMethodArgumentResolver {

    private static final Pattern CORRELATION_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,64}$");

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return TenantContext.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(@NonNull MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
            @NonNull NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) {
        var request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null || !request.getRequestURI().startsWith("/api/")) {
            return null;
        }
        var ctx = new TenantContext(
                uuidHeader(request, "X-Tenant-Id"),
                uuidHeader(request, "X-User-Id"),
                headerOrDefault(request, "X-Country-Code", "KW"),
                headerOrDefault(request, "X-Environment", "PROD"),
                correlationId(request),
                permissions(request));
        MDC.put("tenantId", ctx.tenantId().toString());
        MDC.put("userId", ctx.userId().toString());
        MDC.put("countryCode", ctx.countryCode());
        MDC.put("correlationId", ctx.correlationId());
        return ctx;
    }

    private UUID uuidHeader(HttpServletRequest request, String name) {
        var value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            throw new MissingTenantContextException("Required header " + name + " must be a valid UUID");
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new MissingTenantContextException("Required header " + name + " must be a valid UUID");
        }
    }

    private String correlationId(HttpServletRequest request) {
        var value = headerOrDefault(request, "X-Correlation-Id", UUID.randomUUID().toString());
        if (!CORRELATION_ID.matcher(value).matches()) {
            throw new MissingTenantContextException("X-Correlation-Id contains unsupported characters");
        }
        return value;
    }

    private String headerOrDefault(HttpServletRequest request, String name, String fallback) {
        var value = request.getHeader(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private Set<String> permissions(HttpServletRequest request) {
        var value = request.getHeader("X-Permissions");
        if (value == null || value.isBlank()) {
            return Set.of("*");
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(permission -> !permission.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }
}

