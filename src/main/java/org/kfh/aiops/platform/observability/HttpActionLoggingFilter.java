package org.kfh.aiops.platform.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Emits one secret-safe web action log line per HTTP request.
 *
 * <p>The filter intentionally logs only metadata (method, path, status, duration, tenant/user/country/environment,
 * correlation ID). It never logs request bodies, query strings, cookies, authorization headers, passwords, tokens, or raw
 * telemetry payloads.</p>
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class HttpActionLoggingFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpActionLoggingFilter.class);
    private static final int MAX_FIELD_LENGTH = 160;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        var started = System.nanoTime();
        Throwable failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException ex) {
            failure = ex;
            throw ex;
        } finally {
            logAction(request, response, started, failure);
        }
    }

    private void logAction(HttpServletRequest request, HttpServletResponse response, long started, Throwable failure) {
        var durationMs = (System.nanoTime() - started) / 1_000_000L;
        var status = failure == null || response.getStatus() >= HttpServletResponse.SC_BAD_REQUEST
                ? response.getStatus()
                : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        var message = "http action method={} path={} status={} durationMs={} tenantId={} userId={} countryCode={} environment={} correlationId={}";
        var args = new Object[] {
                safe(request.getMethod()),
                safe(request.getRequestURI()),
                status,
                durationMs,
                firstPresent(MDC.get("tenantId"), request.getHeader("X-Tenant-Id")),
                firstPresent(MDC.get("userId"), request.getHeader("X-User-Id")),
                firstPresent(MDC.get("countryCode"), request.getHeader("X-Country-Code")),
                safeEnvironment(request.getHeader("X-Environment")),
                firstPresent(MDC.get("correlationId"), response.getHeader(CorrelationIdFilter.HEADER), request.getHeader(CorrelationIdFilter.HEADER))
        };
        if (status >= HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
            LOGGER.warn(message, args);
        } else {
            LOGGER.info(message, args);
        }
    }

    private String firstPresent(String... values) {
        for (var value : values) {
            if (value != null && !value.isBlank()) {
                return safe(value);
            }
        }
        return "-";
    }

    private String safeEnvironment(String value) {
        return value == null || value.isBlank() ? "-" : safe(value.toUpperCase(Locale.ROOT));
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        var sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= MAX_FIELD_LENGTH ? sanitized : sanitized.substring(0, MAX_FIELD_LENGTH);
    }
}

