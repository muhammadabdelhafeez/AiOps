package org.kfh.aiops.plugin.implementations.vrops;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class HttpVropsConnectorLiveTesterTest {

    @Test
    void shouldPassWhenTokenAndAlertsEndpointsRespondSuccessfully() {
        var requests = new ArrayList<ClientRequest>();
        var webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    requests.add(request);
                    if (request.url().getPath().endsWith("/auth/token/acquire")) {
                        return Mono.just(json(HttpStatus.OK, "{\"token\":\"vrops-token\",\"expiresAt\":\"2026-06-18T09:00:00Z\"}"));
                    }
                    return Mono.just(json(HttpStatus.OK, "{\"alerts\":[]}"));
                })
                .build();
        var tester = tester(webClient, new MockEnvironment().withProperty("kfh.security.ssrf.resolve-hosts", "false"));

        var result = tester.test(context(), connector(), Map.of("username", "vrops-user", "password", "vrops-password"));

        assertThat(result)
                .containsEntry("pass", true)
                .containsEntry("readyToCollect", true)
                .containsEntry("checkedEndpoint", "https://vrops.example.com/suite-api/api");
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).method()).isEqualTo(HttpMethod.POST);
        assertThat(requests.get(0).url().toString())
                .isEqualTo("https://vrops.example.com/suite-api/api/auth/token/acquire");
        assertThat(requests.get(1).method()).isEqualTo(HttpMethod.GET);
        assertThat(requests.get(1).url().toString())
                .isEqualTo("https://vrops.example.com/suite-api/api/alerts?page=0&pageSize=1&_no_links=true");
        assertThat(requests.get(1).headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("vRealizeOpsToken vrops-token");
        assertThat(result.toString()).doesNotContain("vrops-user", "vrops-password", "vrops-token");
    }

    @Test
    void shouldFailSafelyWhenVropsCredentialsAreMissing() {
        var requests = new ArrayList<ClientRequest>();
        var webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    requests.add(request);
                    return Mono.just(json(HttpStatus.OK, "{}"));
                })
                .build();
        var tester = tester(webClient, new MockEnvironment().withProperty("kfh.security.ssrf.resolve-hosts", "false"));

        var result = tester.test(context(), connector(), Map.of());

        assertThat(result)
                .containsEntry("pass", false)
                .containsEntry("readyToCollect", false);
        assertThat(requests).isEmpty();
        assertThat(result.toString()).doesNotContain("vrops-password", "vRealizeOpsToken");
    }

    @Test
    void shouldFailSafelyWhenTokenEndpointRejectsCredentials() {
        var webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(json(HttpStatus.UNAUTHORIZED, "{\"message\":\"unauthorized\"}")))
                .build();
        var tester = tester(webClient, new MockEnvironment().withProperty("kfh.security.ssrf.resolve-hosts", "false"));

        var result = tester.test(context(), connector(), Map.of("username", "vrops-user", "password", "vrops-password"));

        assertThat(result)
                .containsEntry("pass", false)
                .containsEntry("readyToCollect", false);
        assertThat(result.toString()).doesNotContain("vrops-password");
        assertThat(String.valueOf(result.get("message"))).contains("HTTP 401");
    }

    @Test
    void shouldUseRelaxedTlsClientWhenCertificateVerificationDisabled() {
        var verifiedClient = WebClient.builder()
                .exchangeFunction(request -> Mono.error(new AssertionError("verified TLS client should not be used")))
                .build();
        var relaxedRequests = new ArrayList<ClientRequest>();
        var relaxedClient = WebClient.builder()
                .exchangeFunction(request -> {
                    relaxedRequests.add(request);
                    if (request.url().getPath().endsWith("/auth/token/acquire")) {
                        return Mono.just(json(HttpStatus.OK, "{\"token\":\"vrops-token\"}"));
                    }
                    return Mono.just(json(HttpStatus.OK, "{\"alerts\":[]}"));
                })
                .build();
        var environment = new MockEnvironment().withProperty("kfh.security.ssrf.resolve-hosts", "false");
        var tester = new HttpVropsConnectorLiveTester(verifiedClient, relaxedClient, environment,
                CircuitBreaker.ofDefaults("vropsRelaxedTlsTest"));

        var result = tester.test(context(), Map.of(
                "id", UUID.randomUUID().toString(),
                "pluginType", "VROPS",
                "baseUrl", "https://vrops.example.com/suite-api/api",
                "authSource", "KFH AD",
                "verifySsl", false,
                "timeoutSeconds", 5), Map.of("username", "vrops-user", "password", "vrops-password"));

        assertThat(result)
                .containsEntry("pass", true)
                .containsEntry("verifySsl", false);
        assertThat(relaxedRequests).hasSize(2);
        assertThat(result.toString()).contains("TLS certificate chain verification is disabled");
    }

    @Test
    void shouldAllowConfiguredInternalVropsHostWithoutDisablingResolutionGlobally() {
        var requests = new ArrayList<ClientRequest>();
        var webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    requests.add(request);
                    if (request.url().getPath().endsWith("/auth/token/acquire")) {
                        return Mono.just(json(HttpStatus.OK, "{\"token\":\"vrops-token\"}"));
                    }
                    return Mono.just(json(HttpStatus.OK, "{\"alerts\":[]}"));
                })
                .build();
        var environment = new MockEnvironment().withProperty("kfh.security.ssrf.allowed-host-suffixes", "10.2.243.66");
        var tester = tester(webClient, environment);

        var result = tester.test(context(), Map.of(
                "id", UUID.randomUUID().toString(),
                "pluginType", "VROPS",
                "baseUrl", "https://10.2.243.66/suite-api/api",
                "authSource", "KFH AD",
                "pageSize", 1000,
                "timeoutSeconds", 5), Map.of("username", "vrops-user", "password", "vrops-password"));

        assertThat(result).containsEntry("pass", true);
        assertThat(requests.getFirst().url().toString())
                .isEqualTo("https://10.2.243.66/suite-api/api/auth/token/acquire");
        assertThat(result.toString()).doesNotContain("vrops-password", "vrops-token");
    }

    @Test
    void shouldRejectMetadataVropsIpDuringLiveTest() {
        var requests = new ArrayList<ClientRequest>();
        var webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    requests.add(request);
                    return Mono.just(json(HttpStatus.OK, "{\"token\":\"vrops-token\"}"));
                })
                .build();
        var environment = new MockEnvironment().withProperty("kfh.security.ssrf.resolve-hosts", "false");
        var tester = tester(webClient, environment);

        var result = tester.test(context(), Map.of(
                "id", UUID.randomUUID().toString(),
                "pluginType", "VROPS",
                "baseUrl", "https://169.254.169.254/suite-api/api",
                "authSource", "KFH AD",
                "pageSize", 1000,
                "timeoutSeconds", 5), Map.of("username", "vrops-user", "password", "vrops-password"));

        assertThat(result)
                .containsEntry("pass", false)
                .containsEntry("readyToCollect", false);
        assertThat(String.valueOf(result.get("message"))).contains("SSRF protection");
        assertThat(requests).isEmpty();
        assertThat(result.toString()).doesNotContain("vrops-password", "vrops-token");
    }

    private static HttpVropsConnectorLiveTester tester(WebClient webClient, MockEnvironment environment) {
        return new HttpVropsConnectorLiveTester(webClient, environment, CircuitBreaker.ofDefaults("vropsTest"));
    }

    private static TenantContext context() {
        return new TenantContext(UUID.randomUUID(), UUID.randomUUID(), "KW", "PROD", "corr-vrops-test", Set.of("CONNECTOR_TEST"));
    }

    private static Map<String, Object> connector() {
        return Map.of(
                "id", UUID.randomUUID().toString(),
                "pluginType", "VROPS",
                "baseUrl", "https://vrops.example.com/suite-api/api",
                "authSource", "KFH AD",
                "pageSize", 1000,
                "timeoutSeconds", 5);
    }

    private static ClientResponse json(HttpStatus status, String body) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }
}

