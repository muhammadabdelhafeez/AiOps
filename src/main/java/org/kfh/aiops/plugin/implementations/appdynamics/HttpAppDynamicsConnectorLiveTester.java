package org.kfh.aiops.plugin.implementations.appdynamics;

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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

/** HTTP readiness tester for AppDynamics Controller Basic Auth and application discovery. */
@Service
public class HttpAppDynamicsConnectorLiveTester implements AppDynamicsConnectorLiveTester {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_BODY = new ParameterizedTypeReference<>() { };

    private final WebClient verifiedWebClient;
    private final WebClient relaxedTlsWebClient;
    private final CircuitBreaker circuitBreaker;
    private final ConnectorEndpointGuard endpointGuard;

    @Autowired
    public HttpAppDynamicsConnectorLiveTester(ConnectorTlsWebClientFactory tlsWebClients, Environment environment) {
        this(tlsWebClients.client(true), tlsWebClients.client(false), environment,
                CircuitBreaker.ofDefaults("appDynamicsConnectorLiveTest"));
    }

    HttpAppDynamicsConnectorLiveTester(WebClient webClient, Environment environment, CircuitBreaker circuitBreaker) {
        this(webClient, webClient, environment, circuitBreaker);
    }

    HttpAppDynamicsConnectorLiveTester(WebClient verifiedWebClient, WebClient relaxedTlsWebClient, Environment environment,
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
            var config = AppDynamicsConfig.from(connector, secrets);
            verifySsl = config.verifySsl();
            validateConfig(config);
            steps.add(step("Configuration", "pass", "Required AppDynamics controller URL and credentials are present."));
            steps.add(step("TLS certificate verification", "pass",
                    ConnectorTlsSupport.verificationModeMessage(config.verifySsl())));
            var appCount = discoverApplications(config);
            steps.add(step("AppDynamics application discovery", "pass",
                    "Controller returned " + appCount + " application record(s)."));
            return result(ctx, connectorId, true, latencyMs(started),
                    "AppDynamics connector is reachable and ready to discover applications and APM events.",
                    config.controllerUrl(), steps, verifySsl);
        } catch (RuntimeException ex) {
            if (steps.isEmpty()) {
                steps.add(step("Configuration", "fail", safeMessage(ex)));
            } else {
                steps.add(step("AppDynamics communication", "fail", safeMessage(ex)));
            }
            return result(ctx, connectorId, false, latencyMs(started),
                    "AppDynamics connector test failed: " + safeMessage(ex), checkedEndpoint(connector), steps, verifySsl);
        }
    }

    private int discoverApplications(AppDynamicsConfig config) {
        var response = circuitBreaker.executeSupplier(() -> webClient(config).get()
                .uri(config.applicationsUri())
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBasicAuth(config.username(), config.password()))
                .exchangeToMono(clientResponse -> {
                    if (!clientResponse.statusCode().is2xxSuccessful()) {
                        var status = clientResponse.statusCode().value();
                        return clientResponse.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> List.of(errorResponse(status, body)));
                    }
                    return clientResponse.bodyToMono(LIST_BODY).defaultIfEmpty(List.of());
                })
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .retryWhen(Retry.backoff(1, Duration.ofMillis(200)).filter(this::retryable))
                .block());
        if (!response.isEmpty() && response.getFirst().containsKey("__http_status")) {
            throw new IllegalStateException("AppDynamics applications endpoint returned HTTP "
                    + response.getFirst().get("__http_status") + failureSuffix(response.getFirst()));
        }
        return response.size();
    }

    private WebClient webClient(AppDynamicsConfig config) {
        return config.verifySsl() ? verifiedWebClient : relaxedTlsWebClient;
    }

    private static Map<String, Object> errorResponse(int status, String body) {
        var response = new LinkedHashMap<String, Object>();
        response.put("__http_status", status);
        if (body != null && !body.isBlank()) {
            response.put("__http_body", body);
        }
        return response;
    }

    private static String failureSuffix(Map<String, Object> response) {
        var body = sanitizeText(text(response, "__http_body", ""));
        return body.isBlank() ? "" : ": " + body;
    }

    private boolean retryable(Throwable throwable) {
        return !(throwable instanceof IllegalArgumentException || throwable instanceof IllegalStateException);
    }

    private void validateConfig(AppDynamicsConfig config) {
        if (config.controllerUrl().isBlank()) {
            throw new IllegalArgumentException("AppDynamics controller URL is required. Save connector configuration first.");
        }
        if (config.username().isBlank() || config.password().isBlank()) {
            throw new IllegalArgumentException("AppDynamics username and password are required. Save credentials first.");
        }
        validateControllerUri(config.controllerUri());
    }

    private void validateControllerUri(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("AppDynamics controller URL must use HTTPS");
        }
        if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("AppDynamics controller URL must not include user-info, query strings, or fragments");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("AppDynamics controller URL host is required");
        }
        var path = uri.getPath() == null ? "" : uri.getPath();
        if (!"/controller".equals(path)) {
            throw new IllegalArgumentException("AppDynamics controller URL must end with /controller");
        }
        var host = IDN.toASCII(uri.getHost()).toLowerCase(Locale.ROOT);
        endpointGuard.validateLiteralHost("AppDynamics", host);
        endpointGuard.validateResolvedAddresses("AppDynamics", host);
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
        var controllerUrl = text(connector, "controllerUrl", text(connector, "baseUrl", text(connector, "endpointUrl", "")));
        if (controllerUrl.isBlank()) {
            return "";
        }
        try {
            var uri = URI.create(controllerUrl);
            var port = uri.getPort() > 0 ? ":" + uri.getPort() : "";
            return uri.getScheme() + "://" + uri.getHost() + port + uri.getPath();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String safeMessage(RuntimeException ex) {
        return ConnectorTlsSupport.enrichCertificateFailure(sanitizeText(exceptionMessage(ex)));
    }

    private static String exceptionMessage(Throwable throwable) {
        var messages = new ArrayList<String>();
        var current = throwable;
        while (current != null && messages.size() < 3) {
            var message = Objects.toString(current.getMessage(), "").trim();
            if (message.isBlank()) {
                message = current.getClass().getSimpleName();
            }
            if (messages.stream().noneMatch(message::equals)) {
                messages.add(message);
            }
            current = current.getCause();
        }
        return String.join(": ", messages);
    }

    private static String sanitizeText(String value) {
        return abbreviate(Objects.toString(value, "")
                .replaceAll("(?i)(\"?(?:password|authorization|token|secret|credential|username)\"?\\s*[:=]\\s*\"?)[^\",;\\s}]+", "$1masked")
                .replaceAll("(?i)basic\\s+[^,;\\s]+", "basic masked")
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim());
    }

    private static String abbreviate(String value) {
        return value.length() <= 300 ? value : value.substring(0, 297) + "...";
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

    private record AppDynamicsConfig(URI controllerUri, String controllerUrl, String username, String password,
            int timeoutSeconds, boolean verifySsl) {

        static AppDynamicsConfig from(Map<String, Object> connector, Map<String, String> secrets) {
            var controllerUrl = text(connector, "controllerUrl", text(connector, "baseUrl", text(connector, "endpointUrl", "")));
            return new AppDynamicsConfig(
                    controllerUrl.isBlank() ? URI.create("https://missing.invalid/controller") : URI.create(controllerUrl),
                    controllerUrl,
                    text(secrets, "username", text(secrets, "user", text(secrets, "appdynamicsUsername", ""))),
                    text(secrets, "password", text(secrets, "appdynamicsPassword", "")),
                    integer(connector, "timeoutSeconds", 120, 5, 300),
                    ConnectorTlsSupport.verifySsl(connector));
        }

        URI applicationsUri() {
            return URI.create(controllerUrl + "/rest/applications?output=JSON");
        }
    }
}

