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
 * Emits a secret-safe web action log line only for <b>errors</b> (HTTP status &ge; 400) and
 * <b>state-changing actions</b> (POST/PUT/PATCH/DELETE under {@code /api}, excluding read-style
 * {@code /search} and {@code /query} POSTs). Page opens, static assets and read APIs (GET) are not
 * logged — they add no value and flood the log.
 *
 * <p>The line carries only metadata (method, path, status, duration, tenant/user/country, correlation
 * ID). It never logs request bodies, query strings, cookies, authorization headers, passwords, tokens,
 * or raw telemetry payloads.</p>
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
        var method = request.getMethod();
        var path = request.getRequestURI();
        var isError = status >= HttpServletResponse.SC_BAD_REQUEST;
        var isAction = isStateChangingAction(method, path);
        // Log only errors and state-changing actions. Page opens, static assets, read APIs (GET) and
        // read-style POSTs (/search) are intentionally not logged — they flood the log with no value.
        if (!isError && !isAction) {
            return;
        }
        var message = "http action method={} path={} status={} durationMs={} tenantId={} userId={} countryCode={} correlationId={}";
        var args = new Object[] {
                safe(method),
                safe(path),
                status,
                durationMs,
                firstPresent(MDC.get("tenantId"), request.getHeader("X-Tenant-Id")),
                firstPresent(MDC.get("userId"), request.getHeader("X-User-Id")),
                firstPresent(MDC.get("countryCode"), request.getHeader("X-Country-Code")),
                firstPresent(MDC.get("correlationId"), response.getHeader(CorrelationIdFilter.HEADER), request.getHeader(CorrelationIdFilter.HEADER))
        };
        if (isError) {
            LOGGER.warn(message, args);
        } else {
            LOGGER.info(message, args);
        }
    }

    /** A state-changing API call (POST/PUT/PATCH/DELETE under /api), excluding read-style POSTs (search/query). */
    private static boolean isStateChangingAction(String method, String path) {
        if (method == null || path == null) {
            return false;
        }
        var m = method.toUpperCase(Locale.ROOT);
        var mutating = m.equals("POST") || m.equals("PUT") || m.equals("PATCH") || m.equals("DELETE");
        if (!mutating || !path.startsWith("/api/")) {
            return false;
        }
        return !path.endsWith("/search") && !path.endsWith("/query");
    }

    private String firstPresent(String... values) {
        for (var value : values) {
            if (value != null && !value.isBlank()) {
                return safe(value);
            }
        }
        return "-";
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        var sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= MAX_FIELD_LENGTH ? sanitized : sanitized.substring(0, MAX_FIELD_LENGTH);
    }
}

