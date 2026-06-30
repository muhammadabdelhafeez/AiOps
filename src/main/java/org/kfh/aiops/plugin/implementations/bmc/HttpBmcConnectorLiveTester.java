package org.kfh.aiops.plugin.implementations.bmc;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.IDN;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.kfh.aiops.plugin.security.ConnectorEndpointGuard;
import org.kfh.aiops.plugin.security.ConnectorTlsSupport;
import org.kfh.aiops.plugin.security.ConnectorTlsWebClientFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

@Service
public class HttpBmcConnectorLiveTester implements BmcConnectorLiveTester {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_BODY = new ParameterizedTypeReference<>() { };

    private final WebClient verifiedWebClient;
    private final WebClient relaxedTlsWebClient;
    private final CircuitBreaker circuitBreaker;
    private final ConnectorEndpointGuard endpointGuard;

    @Autowired
    public HttpBmcConnectorLiveTester(ConnectorTlsWebClientFactory tlsWebClients, Environment environment) {
        this(tlsWebClients.client(true), tlsWebClients.client(false), environment,
                CircuitBreaker.ofDefaults("bmcConnectorLiveTest"));
    }

    HttpBmcConnectorLiveTester(WebClient webClient, Environment environment, CircuitBreaker circuitBreaker) {
        this(webClient, webClient, environment, circuitBreaker);
    }

    HttpBmcConnectorLiveTester(WebClient verifiedWebClient, WebClient relaxedTlsWebClient, Environment environment,
            CircuitBreaker circuitBreaker) {
        this.verifiedWebClient = verifiedWebClient;
        this.relaxedTlsWebClient = relaxedTlsWebClient;
        this.circuitBreaker = circuitBreaker;
        this.endpointGuard = new ConnectorEndpointGuard(environment);
    }

    @Override
    public Map<String, Object> test(TenantContext ctx, Map<String, Object> connector, Map<String, String> secrets) {
        var started = System.nanoTime();
        var steps = new ArrayList<Map<String, Object>>();
        var connectorId = text(connector, "id", text(connector, "connectorId", ""));
        var verifySsl = ConnectorTlsSupport.verifySsl(connector);
        try {
            var config = BmcConfig.from(connector, secrets);
            verifySsl = config.verifySsl();
            validateConfig(config);
            steps.add(step("Configuration", "pass", "Required BMC URL, endpoints, and credentials are present."));
            steps.add(step("TLS certificate verification", "pass",
                    ConnectorTlsSupport.verificationModeMessage(config.verifySsl())));
            var token = login(config);
            steps.add(step("BMC access-key login", "pass", "BMC returned a JSON web token."));
            var searchStatus = probeEvents(config, token);
            if (searchStatus.is2xxSuccessful()) {
                steps.add(step("BMC events msearch", "pass", "Events endpoint accepted a readiness query."));
                return result(ctx, connectorId, true, latencyMs(started), "BMC connector is reachable and ready to collect events.",
                        config.origin(), steps, verifySsl);
            }
            steps.add(step("BMC events msearch", "fail", "BMC events endpoint returned HTTP " + searchStatus.value() + "."));
            return result(ctx, connectorId, false, latencyMs(started),
                    "BMC login succeeded, but event search failed with HTTP " + searchStatus.value() + ".", config.origin(), steps, verifySsl);
        } catch (RuntimeException ex) {
            if (steps.isEmpty()) {
                steps.add(step("Configuration", "fail", safeMessage(ex)));
            } else {
                steps.add(step("BMC communication", "fail", safeMessage(ex)));
            }
            return result(ctx, connectorId, false, latencyMs(started), "BMC connector test failed: " + safeMessage(ex),
                    checkedEndpoint(connector), steps, verifySsl);
        }
    }

    private String login(BmcConfig config) {
        var body = Map.of("access_key", config.accessKey(), "access_secret_key", config.accessSecretKey());
        var response = circuitBreaker.executeSupplier(() -> webClient(config).post()
                .uri(config.loginUri())
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchangeToMono(clientResponse -> {
                    if (!clientResponse.statusCode().is2xxSuccessful()) {
                        return clientResponse.releaseBody().thenReturn(Map.of(
                                "__http_status", clientResponse.statusCode().value()));
                    }
                    return clientResponse.bodyToMono(MAP_BODY);
                })
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .retryWhen(Retry.backoff(1, Duration.ofMillis(200)).filter(this::retryable))
                .block());
        var httpStatus = response.get("__http_status");
        if (httpStatus != null) {
            throw new IllegalStateException("BMC login returned HTTP " + httpStatus);
        }
        var token = text(response, "json_web_token", "");
        if (token.isBlank()) {
            throw new IllegalStateException("BMC login response did not include json_web_token");
        }
        return token;
    }

    private HttpStatusCode probeEvents(BmcConfig config, String token) {
        return circuitBreaker.executeSupplier(() -> webClient(config).post()
                .uri(config.eventsUri())
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(token))
                .bodyValue(readinessQuery(config.minutesBack(), config.pageSize()))
                .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode()))
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .retryWhen(Retry.backoff(1, Duration.ofMillis(200)).filter(this::retryable))
                .block());
    }

    private WebClient webClient(BmcConfig config) {
        return config.verifySsl() ? verifiedWebClient : relaxedTlsWebClient;
    }

    private boolean retryable(Throwable throwable) {
        return !(throwable instanceof IllegalArgumentException || throwable instanceof IllegalStateException);
    }

    private void validateConfig(BmcConfig config) {
        if (config.baseUrl().isBlank()) {
            throw new IllegalArgumentException("BMC base URL is required. Save connector configuration first.");
        }
        if (config.accessKey().isBlank() || config.accessSecretKey().isBlank()) {
            throw new IllegalArgumentException("BMC access key and access secret key are required. Save credentials first.");
        }
        validateBaseUri(config.baseUri());
        validateRelativePath(config.loginEndpoint(), "loginEndpoint");
        validateRelativePath(config.eventsEndpoint(), "eventsEndpoint");
    }

    private void validateBaseUri(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("BMC base URL must use HTTPS");
        }
        if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("BMC base URL must not include user-info, query strings, or fragments");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("BMC base URL host is required");
        }
        var host = IDN.toASCII(uri.getHost()).toLowerCase(Locale.ROOT);
        endpointGuard.validateLiteralHost("BMC", host);
        endpointGuard.validateResolvedAddresses("BMC", host);
    }

    private static void validateRelativePath(String value, String fieldName) {
        if (value.isBlank() || !value.startsWith("/") || value.contains("://") || value.contains("..")
                || value.contains("\\") || value.contains("?") || value.contains("#")
                || value.contains("\r") || value.contains("\n")) {
            throw new IllegalArgumentException("BMC " + fieldName + " must be a safe relative API path");
        }
    }

    private static Map<String, Object> readinessQuery(int minutesBack, int pageSize) {
        return Map.of(
                "size", Math.max(1, Math.min(pageSize, 10)),
                "query", Map.of("bool", Map.of("filter", List.of(
                        Map.of("range", Map.of("creation_time", Map.of("gte", "now-" + minutesBack + "m", "lte", "now"))),
                        Map.of("query_string", Map.of("analyze_wildcard", true, "query", "!severity:OK"))))),
                "sort", Map.of("creation_time", Map.of("order", "desc", "unmapped_type", "boolean")),
                "script_fields", Map.of());
    }

    private static Map<String, Object> result(TenantContext ctx, String connectorId, boolean pass, long latencyMs,
            String message, String checkedEndpoint, List<Map<String, Object>> steps, boolean verifySsl) {
        var result = new LinkedHashMap<String, Object>();
        result.put("connectorRunId", java.util.UUID.randomUUID().toString());
        result.put("connectorId", connectorId);
        result.put("pass", pass);
        result.put("readyToCollect", pass);
        result.put("status", pass ? "Pass" : "Fail");
        result.put("latencyMs", latencyMs);
        result.put("message", message);
        result.put("checkedEndpoint", checkedEndpoint);
        result.put("testedAt", Instant.now().toString());
        result.put("correlationId", ctx.correlationId());
        result.put("verifySsl", verifySsl);
        result.put("steps", steps);
        return result;
    }

    private static Map<String, Object> step(String name, String status, String message) {
        return Map.of("name", name, "status", status, "message", message);
    }

    private static long latencyMs(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private static String checkedEndpoint(Map<String, Object> connector) {
        var baseUrl = text(connector, "baseUrl", text(connector, "endpointUrl", ""));
        if (baseUrl.isBlank()) {
            return "";
        }
        try {
            var uri = URI.create(baseUrl);
            var port = uri.getPort() > 0 ? ":" + uri.getPort() : "";
            return uri.getScheme() + "://" + uri.getHost() + port;
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String safeMessage(RuntimeException ex) {
        var message = Objects.toString(ex.getMessage(), ex.getClass().getSimpleName());
        return ConnectorTlsSupport.enrichCertificateFailure(message
                .replaceAll("(?i)(access[_-]?key|access[_-]?secret[_-]?key|authorization|token|secret)\\s*[:=]\\s*[^,;\\s]+", "$1=masked")
                .replaceAll("(?i)bearer\\s+[^,;\\s]+", "bearer masked"));
    }

    private static String text(Map<String, ?> values, String key, String fallback) {
        var value = values == null ? null : values.get(key);
        return value == null ? fallback : String.valueOf(value).trim();
    }

    private static int integer(Map<String, Object> values, String key, int fallback, int min, int max) {
        try {
            var parsed = Integer.parseInt(text(values, key, String.valueOf(fallback)));
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private record BmcConfig(URI baseUri, String baseUrl, String loginEndpoint, String eventsEndpoint,
            String accessKey, String accessSecretKey, int minutesBack, int pageSize, int timeoutSeconds,
            boolean verifySsl) {

        static BmcConfig from(Map<String, Object> connector, Map<String, String> secrets) {
            var baseUrl = text(connector, "baseUrl", text(connector, "endpointUrl", ""));
            return new BmcConfig(
                    baseUrl.isBlank() ? URI.create("https://missing.invalid") : URI.create(baseUrl),
                    baseUrl,
                    text(connector, "loginEndpoint", "/ims/api/v1/access_keys/login"),
                    text(connector, "eventsEndpoint", "/events-service/api/v1.0/events/msearch"),
                    text(secrets, "accessKey", text(secrets, "access_key", "")),
                    text(secrets, "accessSecretKey", text(secrets, "access_secret_key", text(secrets, "accessSecret", ""))),
                    integer(connector, "minutesBack", 60, 1, 1440),
                    integer(connector, "pageSize", 100, 1, 500),
                    integer(connector, "timeoutSeconds", 120, 5, 300),
                    ConnectorTlsSupport.verifySsl(connector));
        }

        URI loginUri() {
            return URI.create(origin() + loginEndpoint);
        }

        URI eventsUri() {
            return URI.create(origin() + eventsEndpoint);
        }

        String origin() {
            var port = baseUri.getPort() > 0 ? ":" + baseUri.getPort() : "";
            return baseUri.getScheme() + "://" + baseUri.getHost() + port;
        }
    }
}

