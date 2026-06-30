package org.kfh.aiops.ai.azureopenai;

import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredentialBuilder;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

@Service
public class HttpAzureOpenAiConnectionTester implements AzureOpenAiConnectionTester {

    private static final String MASKED_SECRET = "••••••••••••";
    private static final String DEFAULT_API_VERSION = "2024-02-15-preview";
    private static final String DEFAULT_ENTRA_SCOPE = "https://ai.azure.com/.default";
    private static final List<String> DEFAULT_ALLOWED_SUFFIXES = List.of(
            ".openai.azure.com", ".cognitiveservices.azure.com", ".services.ai.azure.com");
    private static final List<String> ALLOWED_ENTRA_SCOPES = List.of(
            DEFAULT_ENTRA_SCOPE, "https://cognitiveservices.azure.com/.default");

    private final WebClient webClient;
    private final Environment environment;
    private final CircuitBreaker circuitBreaker;
    private final TokenCredential tokenCredential;

    @Autowired
    public HttpAzureOpenAiConnectionTester(WebClient.Builder builder, Environment environment) {
        this(builder.build(), environment, CircuitBreaker.ofDefaults("settingsAzureOpenAiTest"),
                new DefaultAzureCredentialBuilder().build());
    }

    HttpAzureOpenAiConnectionTester(WebClient webClient, Environment environment, CircuitBreaker circuitBreaker) {
        this(webClient, environment, circuitBreaker, new DefaultAzureCredentialBuilder().build());
    }

    HttpAzureOpenAiConnectionTester(WebClient webClient, Environment environment, CircuitBreaker circuitBreaker,
            TokenCredential tokenCredential) {
        this.webClient = webClient;
        this.environment = environment;
        this.circuitBreaker = circuitBreaker;
        this.tokenCredential = tokenCredential;
    }

    @Override
    public Map<String, Object> test(TenantContext ctx, Map<String, Object> request) {
        var started = System.nanoTime();
        var integration = AzureIntegration.from(request, environment);
        var errors = integration.validationErrors();
        if (!errors.isEmpty()) {
            return result(ctx, integration, "Fail", latencyMs(started), "Configuration error: " + String.join(", ", errors), "");
        }
        var apiKey = integration.usesApiKey() ? resolveApiKey(integration) : "";
        if (integration.usesApiKey() && (apiKey.isBlank() || MASKED_SECRET.equals(apiKey))) {
            return result(ctx, integration, "Fail", latencyMs(started),
                    "API key is required for a live Azure OpenAI test. Paste a key before testing; saved secrets stay masked.",
                    origin(integration.endpoint()));
        }
        try {
            validateEndpoint(integration.endpoint());
            var status = "RESPONSES".equals(integration.apiStyle())
                    ? executeResponsesApi(responsesUri(integration), integration, apiKey)
                    : executeDeploymentMetadata(deploymentUri(integration), apiKey, integration.timeoutSeconds());
            if (status.is2xxSuccessful()) {
                return result(ctx, integration, "Pass", latencyMs(started),
                        successMessage(integration), checkedEndpoint(integration));
            }
            return result(ctx, integration, "Fail", latencyMs(started),
                    "Azure OpenAI responded with HTTP " + status.value() + ". Verify endpoint, model/deployment, authentication, and API style.",
                    checkedEndpoint(integration));
        } catch (IllegalArgumentException ex) {
            return result(ctx, integration, "Fail", latencyMs(started), "Invalid Azure OpenAI endpoint: " + ex.getMessage(), "");
        } catch (RuntimeException ex) {
            return result(ctx, integration, "Fail", latencyMs(started), "Connection failed: " + safeMessage(ex),
                    checkedEndpoint(integration));
        }
    }

    private HttpStatusCode executeDeploymentMetadata(URI uri, String apiKey, int timeoutSeconds) {
        return circuitBreaker.executeSupplier(() -> webClient.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .header("api-key", apiKey)
                .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode()))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .retryWhen(Retry.backoff(1, Duration.ofMillis(200)).filter(this::retryable))
                .block());
    }

    private HttpStatusCode executeResponsesApi(URI uri, AzureIntegration integration, String apiKey) {
        var status = executeResponsesApi(uri, integration, apiKey, false);
        if (integration.usesApiKey() && status.value() == 401) {
            return executeResponsesApi(uri, integration, apiKey, true);
        }
        return status;
    }

    private HttpStatusCode executeResponsesApi(URI uri, AzureIntegration integration, String apiKey,
            boolean bearerApiKeyAuth) {
        var body = Map.of(
                "model", integration.deployment(),
                "input", "KFH AIOps provider readiness check. Return only OK.");
        return circuitBreaker.executeSupplier(() -> webClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> applyResponsesAuth(headers, integration, apiKey, bearerApiKeyAuth))
                .bodyValue(body)
                .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode()))
                .timeout(Duration.ofSeconds(integration.timeoutSeconds()))
                .retryWhen(Retry.backoff(1, Duration.ofMillis(200)).filter(this::retryable))
                .block());
    }

    private void applyResponsesAuth(HttpHeaders headers, AzureIntegration integration, String apiKey,
            boolean bearerApiKeyAuth) {
        if (integration.usesEntraId()) {
            headers.setBearerAuth(bearerToken(integration));
            return;
        }
        if (bearerApiKeyAuth) {
            headers.setBearerAuth(apiKey);
            return;
        }
        headers.set("api-key", apiKey);
    }

    private String bearerToken(AzureIntegration integration) {
        var token = tokenCredential.getToken(new TokenRequestContext().addScopes(integration.entraScope()))
                .timeout(Duration.ofSeconds(integration.timeoutSeconds()))
                .block();
        if (token == null || token.getToken().isBlank()) {
            throw new IllegalArgumentException("Microsoft Entra ID token could not be acquired");
        }
        return token.getToken();
    }

    private boolean retryable(Throwable throwable) {
        return !(throwable instanceof IllegalArgumentException);
    }

    private URI deploymentUri(AzureIntegration integration) {
        var endpoint = integration.endpoint();
        var deployment = encodePathSegment(integration.deployment());
        var apiVersion = encodeQueryValue(integration.apiVersion());
        return URI.create(origin(endpoint) + "/openai/deployments/" + deployment + "?api-version=" + apiVersion);
    }

    private URI responsesUri(AzureIntegration integration) {
        return URI.create(responsesEndpoint(integration.endpoint()));
    }

    private void validateEndpoint(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("only HTTPS Azure endpoints are allowed");
        }
        if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("user-info, query strings, and fragments are not allowed");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("host is required");
        }
        var host = IDN.toASCII(uri.getHost()).toLowerCase(Locale.ROOT);
        if (!allowedHost(host)) {
            throw new IllegalArgumentException("host is not in the Azure OpenAI allowlist");
        }
        if (environment.getProperty("kfh.security.ssrf.resolve-hosts", Boolean.class, true)) {
            assertResolvedAddressesArePublic(host);
        }
    }

    private boolean allowedHost(String host) {
        return allowedSuffixes().stream().anyMatch(suffix -> matchesSuffix(host, suffix));
    }

    private List<String> allowedSuffixes() {
        var configured = environment.getProperty("kfh.security.ssrf.allowed-host-suffixes", "").trim();
        if (configured.isBlank()) {
            return DEFAULT_ALLOWED_SUFFIXES;
        }
        var suffixes = new ArrayList<>(DEFAULT_ALLOWED_SUFFIXES);
        Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .forEach(suffixes::add);
        return suffixes;
    }

    private static boolean matchesSuffix(String host, String suffix) {
        var normalized = suffix.startsWith(".") ? suffix : "." + suffix;
        return host.equals(suffix.replaceFirst("^\\.", "")) || host.endsWith(normalized);
    }

    private static void assertResolvedAddressesArePublic(String host) {
        try {
            for (var address : InetAddress.getAllByName(host)) {
                if (blockedAddress(address)) {
                    throw new IllegalArgumentException("host resolves to a non-public address");
                }
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("host could not be resolved");
        }
    }

    private static boolean blockedAddress(InetAddress address) {
        return address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress();
    }

    private String resolveApiKey(AzureIntegration integration) {
        if (!MASKED_SECRET.equals(integration.apiKey())) {
            return integration.apiKey();
        }
        if (integration.id().contains("embeddings")) {
            return property("kfh.ai.azure-openai.embeddings.api-key");
        }
        if (integration.id().contains("gpt")) {
            return property("kfh.ai.azure-openai.gpt.api-key");
        }
        var configuredIndex = configuredIndex(integration.id());
        if (configuredIndex >= 0) {
            return property("kfh.ai.azure-openai.integrations[" + configuredIndex + "].api-key");
        }
        return "";
    }

    private static int configuredIndex(String id) {
        var prefix = "azure-openai-configured-";
        if (!id.startsWith(prefix)) {
            return -1;
        }
        try {
            return Integer.parseInt(id.substring(prefix.length())) - 1;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private String property(String key) {
        return Objects.toString(environment.getProperty(key), "").trim();
    }

    private static String origin(URI uri) {
        if (uri == null || uri.getHost() == null) {
            return "";
        }
        var port = uri.getPort() > 0 ? ":" + uri.getPort() : "";
        return uri.getScheme() + "://" + uri.getHost() + port;
    }

    private static String baseEndpoint(URI uri) {
        var path = uri.getRawPath() == null ? "" : uri.getRawPath().replaceAll("/+$", "");
        return origin(uri) + path;
    }

    private static String checkedEndpoint(AzureIntegration integration) {
        return "RESPONSES".equals(integration.apiStyle())
                ? responsesBaseEndpoint(integration.endpoint())
                : origin(integration.endpoint());
    }

    private static String responsesEndpoint(URI uri) {
        var base = baseEndpoint(uri);
        if (hasRootPath(uri)) {
            return origin(uri) + "/openai/v1/responses";
        }
        if (endsWithResponsesPath(uri)) {
            return base;
        }
        return base + "/responses";
    }

    private static String responsesBaseEndpoint(URI uri) {
        var base = baseEndpoint(uri);
        if (hasRootPath(uri)) {
            return origin(uri) + "/openai/v1";
        }
        if (endsWithResponsesPath(uri)) {
            return base.substring(0, base.length() - "/responses".length());
        }
        return base;
    }

    private static boolean hasRootPath(URI uri) {
        var path = normalizedPath(uri);
        return path.isBlank() || "/".equals(path);
    }

    private static boolean endsWithResponsesPath(URI uri) {
        return normalizedPath(uri).endsWith("/responses");
    }

    private static String normalizedPath(URI uri) {
        var path = uri.getRawPath() == null ? "" : uri.getRawPath().replaceAll("/+$", "");
        return path.toLowerCase(Locale.ROOT);
    }

    private static String successMessage(AzureIntegration integration) {
        if (integration.usesEntraId() && "RESPONSES".equals(integration.apiStyle())) {
            return "Azure OpenAI Responses API responded successfully using Microsoft Entra ID.";
        }
        if ("RESPONSES".equals(integration.apiStyle())) {
            return "Azure OpenAI Responses API responded successfully using API key authentication.";
        }
        return "Azure OpenAI deployment metadata endpoint responded successfully.";
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String encodeQueryValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static long latencyMs(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private static String safeMessage(RuntimeException ex) {
        var message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.replaceAll("(?i)(api-key|authorization|token|secret)[^,;\\s]*", "$1=masked");
    }

    private static Map<String, Object> result(TenantContext ctx, AzureIntegration integration, String status, long latencyMs,
            String message, String checkedEndpoint) {
        var result = new LinkedHashMap<String, Object>();
        result.put("section", "azureOpenAI");
        result.put("provider", integration.provider());
        result.put("integrationId", integration.id());
        result.put("name", integration.name());
        result.put("countryCode", integration.countryCode());
        result.put("countryCodes", integration.countryCodes());
        result.put("status", status);
        result.put("latencyMs", latencyMs);
        result.put("message", message);
        result.put("checkedEndpoint", checkedEndpoint);
        result.put("testedAt", Instant.now().toString());
        result.put("correlationId", ctx.correlationId());
        return result;
    }

    private record AzureIntegration(String id, String name, String provider, String purpose, String countryCode,
            List<String> countryCodes, URI endpoint, String apiKey, String deployment, String modelName, String apiVersion,
            String authMode, String apiStyle, String entraScope, int timeoutSeconds) {

        static AzureIntegration from(Map<String, Object> request, Environment environment) {
            var body = request == null ? Map.<String, Object>of() : request;
            var provider = upper(text(body, "provider", "AZURE_OPENAI"));
            var purpose = upper(text(body, "purpose", provider.contains("EMBEDDINGS") ? "EMBEDDINGS" : "GPT"));
            var countryCodes = countryCodes(body);
            return new AzureIntegration(
                    text(body, "id", "azure-openai-manual"),
                    text(body, "name", "Azure OpenAI"),
                    provider,
                    purpose,
                    primaryCountryCode(countryCodes),
                    countryCodes,
                    endpoint(text(body, "endpoint", "")),
                    text(body, "apiKey", ""),
                    deployment(body),
                    text(body, "modelName", deployment(body)),
                    text(body, "apiVersion", environment.getProperty("kfh.ai.azure-openai.default-api-version", DEFAULT_API_VERSION)),
                    upper(text(body, "authMode", "API_KEY")),
                    upper(text(body, "apiStyle", "DEPLOYMENTS")),
                    text(body, "entraScope", DEFAULT_ENTRA_SCOPE),
                    timeoutSeconds(body.get("timeoutSeconds")));
        }

        boolean usesApiKey() {
            return !usesEntraId();
        }

        boolean usesEntraId() {
            return "ENTRA_ID".equals(authMode);
        }

        List<String> validationErrors() {
            var errors = new ArrayList<String>();
            if (!provider.startsWith("AZURE_OPENAI")) {
                errors.add("provider must be Azure OpenAI");
            }
            if (!("GPT".equals(purpose) || "EMBEDDINGS".equals(purpose) || "GENERAL".equals(purpose))) {
                errors.add("usage must be GPT, EMBEDDINGS, or GENERAL");
            }
            if (!countryCode.matches("ALL|[A-Z]{2}")) {
                errors.add("countryCode must be ALL or a two-letter country code");
            }
            if (countryCodes.stream().anyMatch(scope -> !scope.matches("ALL|[A-Z]{2}"))) {
                errors.add("countryCodes must contain ALL or two-letter country codes");
            }
            if (endpoint == null) {
                errors.add("endpoint URL is required");
            }
            if (!("API_KEY".equals(authMode) || "ENTRA_ID".equals(authMode))) {
                errors.add("authMode must be API_KEY or ENTRA_ID");
            }
            if (!("DEPLOYMENTS".equals(apiStyle) || "RESPONSES".equals(apiStyle))) {
                errors.add("apiStyle must be DEPLOYMENTS or RESPONSES");
            }
            if (usesEntraId() && !ALLOWED_ENTRA_SCOPES.contains(entraScope)) {
                errors.add("entraScope must be an approved Azure OpenAI scope");
            }
            if (!deployment.matches("[A-Za-z0-9._-]{1,128}")) {
                errors.add("deployment must be 1-128 characters using letters, numbers, dot, underscore, or hyphen");
            }
            if ("DEPLOYMENTS".equals(apiStyle) && !apiVersion.matches("20\\d{2}-\\d{2}-\\d{2}(-preview)?")) {
                errors.add("apiVersion must match yyyy-MM-dd or yyyy-MM-dd-preview");
            }
            return errors;
        }

        private static URI endpoint(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return URI.create(value.trim());
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }

        private static String text(Map<String, Object> body, String key, String fallback) {
            var value = body.get(key);
            return value == null ? fallback : String.valueOf(value).trim();
        }

        private static String deployment(Map<String, Object> body) {
            var deployment = text(body, "deployment", "");
            if (!deployment.isBlank()) {
                return deployment;
            }
            var deploymentA = text(body, "deploymentA", "");
            return deploymentA.isBlank() ? text(body, "deploymentB", "") : deploymentA;
        }

        private static String upper(String value) {
            return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        }

        private static List<String> countryCodes(Map<String, Object> body) {
            var normalized = new ArrayList<String>();
            var raw = body.get("countryCodes");
            if (raw instanceof Iterable<?> iterable) {
                iterable.forEach(value -> addCountryCode(normalized, value));
            } else {
                addCountryCode(normalized, text(body, "countryCode", "ALL"));
            }
            if (normalized.isEmpty() || normalized.contains("ALL")) {
                return List.of("ALL");
            }
            return normalized.stream().distinct().toList();
        }

        private static void addCountryCode(List<String> countryCodes, Object value) {
            var countryCode = upper(value == null ? "" : String.valueOf(value));
            if (!countryCode.isBlank() && !"GLOBAL".equals(countryCode) && !"DEFAULT".equals(countryCode)) {
                countryCodes.add(countryCode);
            } else if ("GLOBAL".equals(countryCode) || "DEFAULT".equals(countryCode)) {
                countryCodes.add("ALL");
            }
        }

        private static String primaryCountryCode(List<String> countryCodes) {
            return countryCodes.contains("ALL") || countryCodes.isEmpty() ? "ALL" : countryCodes.getFirst();
        }

        private static int timeoutSeconds(Object value) {
            if (value == null) {
                return 5;
            }
            try {
                var parsed = Integer.parseInt(String.valueOf(value));
                if (parsed < 3) {
                    return 3;
                }
                return Math.min(30, parsed);
            } catch (NumberFormatException ex) {
                return 5;
            }
        }
    }
}
