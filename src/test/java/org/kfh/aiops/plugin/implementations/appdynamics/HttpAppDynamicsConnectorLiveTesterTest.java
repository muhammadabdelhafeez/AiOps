package org.kfh.aiops.plugin.implementations.appdynamics;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class HttpAppDynamicsConnectorLiveTesterTest {

    @Test
    void shouldPassWhenApplicationsEndpointRespondsSuccessfully() {
        var requests = new ArrayList<ClientRequest>();
        var webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    requests.add(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "application/json")
                            .body("[{\"id\":101,\"name\":\"KFHOnline\"}]")
                            .build());
                })
                .build();
        var tester = tester(webClient);

        var result = tester.test(context(), connector(), Map.of("username", "appd-user", "password", "plain-password"));

        assertThat(result)
                .containsEntry("pass", true)
                .containsEntry("readyToCollect", true)
                .containsEntry("checkedEndpoint", "https://appd.example.com/controller");
        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().url().toString())
                .isEqualTo("https://appd.example.com/controller/rest/applications?output=JSON");
        assertThat(requests.getFirst().headers().getFirst(HttpHeaders.AUTHORIZATION)).startsWith("Basic ");
        assertThat(result.toString()).doesNotContain("appd-user").doesNotContain("plain-password");
    }

    @Test
    void shouldFailWithoutSavedCredentials() {
        var tester = tester(WebClient.builder().exchangeFunction(request -> Mono.error(new AssertionError("unexpected call"))).build());

        var result = tester.test(context(), connector(), Map.of());

        assertThat(result)
                .containsEntry("pass", false)
                .extracting("message")
                .asString()
                .contains("username and password are required");
    }

    @Test
    void shouldFailSecretSafelyWhenApplicationsEndpointRejectsAuthentication() {
        var webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED)
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .body("{\"error\":\"Login failed for username=appd-user password=plain-password\"}")
                        .build()))
                .build();
        var tester = tester(webClient);

        var result = tester.test(context(), connector(), Map.of("username", "appd-user", "password", "plain-password"));

        assertThat(result)
                .containsEntry("pass", false)
                .extracting("message")
                .asString()
                .contains("HTTP 401", "Login failed", "username=masked", "password=masked");
        assertThat(result.toString()).doesNotContain("appd-user").doesNotContain("plain-password");
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
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "application/json")
                            .body("[]")
                            .build());
                })
                .build();
        var environment = new MockEnvironment().withProperty("kfh.security.ssrf.resolve-hosts", "false");
        var tester = new HttpAppDynamicsConnectorLiveTester(verifiedClient, relaxedClient, environment,
                CircuitBreaker.ofDefaults("appDynamicsRelaxedTlsTest"));

        var result = tester.test(context(), Map.of(
                "id", UUID.randomUUID().toString(),
                "pluginType", "APPDYNAMICS",
                "controllerUrl", "https://appd.example.com/controller",
                "verifySsl", false,
                "timeoutSeconds", 5), Map.of("username", "appd-user", "password", "plain-password"));

        assertThat(result)
                .containsEntry("pass", true)
                .containsEntry("verifySsl", false);
        assertThat(relaxedRequests).hasSize(1);
        assertThat(result.toString()).contains("TLS certificate chain verification is disabled");
    }

    @Test
    void shouldAllowConfiguredInternalHostSuffixWithoutDisablingResolutionGlobally() {
        var webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .body("[]")
                        .build()))
                .build();
        var environment = new MockEnvironment().withProperty("kfh.security.ssrf.allowed-host-suffixes", "corp.kfh.kw");
        var tester = new HttpAppDynamicsConnectorLiveTester(webClient, environment, CircuitBreaker.ofDefaults("appDynamicsAllowlistTest"));

        var result = tester.test(context(), Map.of(
                "id", UUID.randomUUID().toString(),
                "pluginType", "APPDYNAMICS",
                "controllerUrl", "https://appd.corp.kfh.kw/controller",
                "timeoutSeconds", 5), Map.of("username", "appd-user", "password", "plain-password"));

        assertThat(result).containsEntry("pass", true);
        assertThat(result.toString()).doesNotContain("plain-password");
    }

    @Test
    void shouldAllowExplicitlyAllowlistedPrivateAppDynamicsIpDuringLiveTest() {
        var webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .body("[]")
                        .build()))
                .build();
        var environment = new MockEnvironment()
                .withProperty("kfh.security.ssrf.allowed-host-suffixes", "10.17.134.118");
        var tester = new HttpAppDynamicsConnectorLiveTester(webClient, environment, CircuitBreaker.ofDefaults("appDynamicsPrivateIpAllowlistTest"));

        var result = tester.test(context(), Map.of(
                "id", UUID.randomUUID().toString(),
                "pluginType", "APPDYNAMICS",
                "controllerUrl", "https://10.17.134.118/controller",
                "timeoutSeconds", 5), Map.of("username", "appd-user", "password", "plain-password"));

        assertThat(result).containsEntry("pass", true);
        assertThat(result.toString()).doesNotContain("plain-password");
    }

    private static HttpAppDynamicsConnectorLiveTester tester(WebClient webClient) {
        var environment = new MockEnvironment().withProperty("kfh.security.ssrf.resolve-hosts", "false");
        return new HttpAppDynamicsConnectorLiveTester(webClient, environment, CircuitBreaker.ofDefaults("appDynamicsUnitTest"));
    }

    private static Map<String, Object> connector() {
        return Map.of(
                "id", UUID.randomUUID().toString(),
                "pluginType", "APPDYNAMICS",
                "controllerUrl", "https://appd.example.com/controller",
                "timeoutSeconds", 5);
    }

    private static TenantContext context() {
        return new TenantContext(UUID.fromString("00000000-0000-4000-8000-000000000001"),
                UUID.fromString("00000000-0000-4000-8000-000000000101"), "KW", "PROD", "appd-test",
                Set.of("CONNECTOR_TEST"));
    }
}

