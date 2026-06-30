package org.kfh.aiops.plugin.implementations.vrops;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.IDN;
import java.net.InetAddress;
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
import org.kfh.aiops.plugin.security.ConnectorTlsSupport;
import org.kfh.aiops.plugin.security.ConnectorTlsWebClientFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

/** HTTP readiness tester for VMware vROps / Aria Operations token authentication and alert API access. */
@Service
public class HttpVropsConnectorLiveTester implements VropsConnectorLiveTester {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_BODY = new ParameterizedTypeReference<>() { };

    private final WebClient verifiedWebClient;
    private final WebClient relaxedTlsWebClient;
    private final Environment environment;
    private final CircuitBreaker circuitBreaker;

    @Autowired
    public HttpVropsConnectorLiveTester(ConnectorTlsWebClientFactory tlsWebClients, Environment environment) {
        this(tlsWebClients.client(true), tlsWebClients.client(false), environment,
                CircuitBreaker.ofDefaults("vropsConnectorLiveTest"));
    }

    HttpVropsConnectorLiveTester(WebClient webClient, Environment environment, CircuitBreaker circuitBreaker) {
        this(webClient, webClient, environment, circuitBreaker);
    }

    HttpVropsConnectorLiveTester(WebClient verifiedWebClient, WebClient relaxedTlsWebClient, Environment environment,
            CircuitBreaker circuitBreaker) {
        this.verifiedWebClient = verifiedWebClient;
        this.relaxedTlsWebClient = relaxedTlsWebClient;
        this.environment = environment;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public Map<String, Object> test(TenantContext ctx, Map<String, Object> connector, Map<String, String> secrets) {
        var started = System.nanoTime();
        var steps = new ArrayList<Map<String, Object>>();
        var connectorId = text(connector, "id", text(connector, "connectorId", ""));
        var verifySsl = ConnectorTlsSupport.verifySsl(connector);
        try {
            var config = VropsConfig.from(connector, secrets);
            verifySsl = config.verifySsl();
            validateConfig(config);
            steps.add(step("Configuration", "pass", "Required vROps host, auth source, username, and password are present."));
            steps.add(step("TLS certificate verification", "pass",
                    ConnectorTlsSupport.verificationModeMessage(config.verifySsl())));
            var token = acquireToken(config);
            steps.add(step("vROps token acquire", "pass", "Aria Operations token endpoint returned a token."));
            var alertCount = probeAlerts(config, token);
            steps.add(step("vROps alerts probe", "pass", "Alerts endpoint returned " + alertCount + " top-level field(s)."));
            return result(ctx, connectorId, true, latencyMs(started),
                    "vROps connector is reachable and ready to read alerts and resource health.",
                    config.baseUrl(), steps, verifySsl);
        } catch (RuntimeException ex) {
            if (steps.isEmpty()) {
                steps.add(step("Configuration", "fail", safeMessage(ex)));
            } else {
                steps.add(step("vROps communication", "fail", safeMessage(ex)));
            }
            return result(ctx, connectorId, false, latencyMs(started),
                    "vROps connector test failed: " + safeMessage(ex), checkedEndpoint(connector), steps, verifySsl);
        }
    }

    private String acquireToken(VropsConfig config) {
        var response = circuitBreaker.executeSupplier(() -> webClient(config).post()
                .uri(config.tokenUri())
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "username", config.username(),
                        "authSource", config.authSource(),
                        "password", config.password()))
                .exchangeToMono(clientResponse -> {
                    if (!clientResponse.statusCode().is2xxSuccessful()) {
                        return clientResponse.releaseBody().thenReturn(Map.of(
                                "__http_status", clientResponse.statusCode().value()));
                    }
                    return clientResponse.bodyToMono(MAP_BODY).defaultIfEmpty(Map.of());
                })
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .retryWhen(Retry.backoff(1, Duration.ofMillis(200)).filter(this::retryable))
                .block());
        if (response.containsKey("__http_status")) {
            throw new IllegalStateException("vROps token endpoint returned HTTP " + response.get("__http_status"));
        }
        var token = text(response, "token", "");
        if (token.isBlank()) {
            throw new IllegalStateException("vROps token endpoint did not return a token");
        }
        return token;
    }

    private int probeAlerts(VropsConfig config, String token) {
        var response = circuitBreaker.executeSupplier(() -> webClient(config).get()
                .uri(config.alertProbeUri())
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.set("Authorization", "vRealizeOpsToken " + token))
                .exchangeToMono(clientResponse -> {
                    if (!clientResponse.statusCode().is2xxSuccessful()) {
                        return clientResponse.releaseBody().thenReturn(Map.of(
                                "__http_status", clientResponse.statusCode().value()));
                    }
                    return clientResponse.bodyToMono(MAP_BODY).defaultIfEmpty(Map.of());
                })
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .retryWhen(Retry.backoff(1, Duration.ofMillis(200)).filter(this::retryable))
                .block());
        if (response.containsKey("__http_status")) {
            throw new IllegalStateException("vROps alerts endpoint returned HTTP " + response.get("__http_status"));
        }
        return response.size();
    }

    private WebClient webClient(VropsConfig config) {
        return config.verifySsl() ? verifiedWebClient : relaxedTlsWebClient;
    }

    private boolean retryable(Throwable throwable) {
        return !(throwable instanceof IllegalArgumentException || throwable instanceof IllegalStateException);
    }

    private void validateConfig(VropsConfig config) {
        if (config.baseUrl().isBlank()) {
            throw new IllegalArgumentException("vROps host is required. Save connector configuration first.");
        }
        if (config.username().isBlank() || config.password().isBlank()) {
            throw new IllegalArgumentException("vROps username and password are required. Save credentials first.");
        }
        if (config.authSource().isBlank()) {
            throw new IllegalArgumentException("vROps auth source is required.");
        }
        validateBaseUri(config.baseUri());
    }

    private void validateBaseUri(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("vROps base URL must use HTTPS");
        }
        if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("vROps base URL must not include user-info, query strings, or fragments");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("vROps base URL host is required");
        }
        var path = uri.getPath() == null ? "" : uri.getPath();
        if (!"/suite-api/api".equals(path)) {
            throw new IllegalArgumentException("vROps base URL must end with /suite-api/api");
        }
        var host = IDN.toASCII(uri.getHost()).toLowerCase(Locale.ROOT);
        rejectUnsafeLiteralHost(host);
        if (isUnsafeLiteralHost(host)) {
            throw new IllegalArgumentException("vROps host targets a loopback, link-local, metadata, or multicast address");
        }
        if (environment.getProperty("kfh.security.ssrf.resolve-hosts", Boolean.class, true)) {
            assertResolvedAddressesAreSafe(host);
        }
    }

    private static void rejectUnsafeLiteralHost(String host) {
        if (host.equals("localhost") || host.endsWith(".localhost") || host.equals("169.254.169.254")) {
            throw new IllegalArgumentException("vROps host is not allowed for SSRF protection");
        }
    }

    private static boolean isUnsafeLiteralHost(String host) {
        if (!host.matches("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")) {
            return false;
        }
        try {
            var address = InetAddress.getByName(host);
            return address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isMulticastAddress();
        } catch (Exception ex) {
            return true;
        }
    }

    private static void assertResolvedAddressesAreSafe(String host) {
        try {
            for (var address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isMulticastAddress()) {
                    throw new IllegalArgumentException("vROps host resolves to a loopback, link-local, metadata, or multicast address");
                }
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("vROps host could not be resolved");
        }
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
            var host = text(connector, "host", "");
            return host.isBlank() ? "" : "https://" + host + "/suite-api/api";
        }
        try {
            var uri = URI.create(baseUrl);
            var port = uri.getPort() > 0 ? ":" + uri.getPort() : "";
            return uri.getScheme() + "://" + uri.getHost() + port + uri.getPath();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String safeMessage(RuntimeException ex) {
        var message = Objects.toString(ex.getMessage(), ex.getClass().getSimpleName());
        return ConnectorTlsSupport.enrichCertificateFailure(message
                .replaceAll("(?i)(password|authorization|token|secret|credential|username)\\s*[:=]\\s*[^,;\\s]+", "$1=masked")
                .replaceAll("(?i)vRealizeOpsToken\\s+[^,;\\s]+", "vRealizeOpsToken masked")
                .replaceAll("(?i)basic\\s+[^,;\\s]+", "basic masked"));
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

    private record VropsConfig(URI baseUri, String baseUrl, String username, String password,
            String authSource, int pageSize, int timeoutSeconds, boolean verifySsl) {

        static VropsConfig from(Map<String, Object> connector, Map<String, String> secrets) {
            var baseUrl = text(connector, "baseUrl", text(connector, "endpointUrl", ""));
            if (baseUrl.isBlank()) {
                var host = text(connector, "host", "");
                baseUrl = host.isBlank() ? "" : "https://" + host + "/suite-api/api";
            }
            return new VropsConfig(
                    baseUrl.isBlank() ? URI.create("https://missing.invalid/suite-api/api") : URI.create(baseUrl),
                    baseUrl,
                    text(secrets, "username", text(secrets, "user", text(secrets, "vropsUsername", ""))),
                    text(secrets, "password", text(secrets, "vropsPassword", "")),
                    text(connector, "authSource", "KFH AD"),
                    integer(connector, "pageSize", 1000, 1, 5000),
                    integer(connector, "timeoutSeconds", 120, 5, 300),
                    ConnectorTlsSupport.verifySsl(connector));
        }

        URI tokenUri() {
            return URI.create(baseUrl + "/auth/token/acquire");
        }

        URI alertProbeUri() {
            return URI.create(baseUrl + "/alerts?page=0&pageSize=" + Math.min(pageSize, 1) + "&_no_links=true");
        }
    }
}

