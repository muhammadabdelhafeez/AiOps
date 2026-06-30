package org.kfh.aiops.plugin.implementations.bmc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class HttpBmcConnectorLiveTesterTest {

    @Test
    void shouldPassWhenBmcLoginAndEventSearchRespondSuccessfully() {
        var requests = new ArrayList<ClientRequest>();
        var webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    requests.add(request);
                    if (request.url().getPath().endsWith("/access_keys/login")) {
                        return Mono.just(ClientResponse.create(HttpStatus.OK)
                                .header("Content-Type", "application/json")
                                .body("{\"json_web_token\":\"jwt-token\"}")
                                .build());
                    }
                    return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                })
                .build();
        var tester = tester(webClient);

        var result = tester.test(context(), connector(), Map.of("accessKey", "plainCredentialA", "accessSecretKey", "plainCredentialB"));

        assertThat(result)
                .containsEntry("pass", true)
                .containsEntry("readyToCollect", true)
                .containsEntry("checkedEndpoint", "https://unit.onbmc.com");
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).url().toString()).isEqualTo("https://unit.onbmc.com/ims/api/v1/access_keys/login");
        assertThat(requests.get(1).url().toString()).isEqualTo("https://unit.onbmc.com/events-service/api/v1.0/events/msearch");
        assertThat(requests.get(1).headers().getFirst("Authorization")).isEqualTo("Bearer jwt-token");
        assertThat(result.toString()).doesNotContain("plainCredentialA").doesNotContain("plainCredentialB").doesNotContain("jwt-token");
    }

    @Test
    void shouldFailWithoutSavedCredentials() {
        var tester = tester(WebClient.builder().exchangeFunction(request -> Mono.error(new AssertionError("unexpected call"))).build());

        var result = tester.test(context(), connector(), Map.of());

        assertThat(result)
                .containsEntry("pass", false)
                .extracting("message")
                .asString()
                .contains("access key and access secret key are required");
    }

    @Test
    void shouldFailWhenBmcLoginDoesNotReturnToken() {
        var webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body("{}")
                        .build()))
                .build();
        var tester = tester(webClient);

        var result = tester.test(context(), connector(), Map.of("accessKey", "plainCredentialA", "accessSecretKey", "plainCredentialB"));

        assertThat(result)
                .containsEntry("pass", false)
                .extracting("message")
                .asString()
                .contains("json_web_token");
        assertThat(result.toString()).doesNotContain("plainCredentialA").doesNotContain("plainCredentialB");
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
                    if (request.url().getPath().endsWith("/access_keys/login")) {
                        return Mono.just(ClientResponse.create(HttpStatus.OK)
                                .header("Content-Type", "application/json")
                                .body("{\"json_web_token\":\"jwt-token\"}")
                                .build());
                    }
                    return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                })
                .build();
        var environment = new MockEnvironment().withProperty("kfh.security.ssrf.resolve-hosts", "false");
        var tester = new HttpBmcConnectorLiveTester(verifiedClient, relaxedClient, environment,
                CircuitBreaker.ofDefaults("bmcRelaxedTlsTest"));

        var result = tester.test(context(), Map.of(
                "id", UUID.randomUUID().toString(),
                "baseUrl", "https://unit.onbmc.com",
                "loginEndpoint", "/ims/api/v1/access_keys/login",
                "eventsEndpoint", "/events-service/api/v1.0/events/msearch",
                "verifySsl", false,
                "timeoutSeconds", 5), Map.of("accessKey", "plainCredentialA", "accessSecretKey", "plainCredentialB"));

        assertThat(result)
                .containsEntry("pass", true)
                .containsEntry("verifySsl", false);
        assertThat(relaxedRequests).hasSize(2);
        assertThat(result.toString()).contains("TLS certificate chain verification is disabled");
    }

    @Test
    void shouldAllowExplicitlyAllowlistedPrivateBmcIpDuringLiveTest() {
        var webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    if (request.url().getPath().endsWith("/access_keys/login")) {
                        return Mono.just(ClientResponse.create(HttpStatus.OK)
                                .header("Content-Type", "application/json")
                                .body("{\"json_web_token\":\"jwt-token\"}")
                                .build());
                    }
                    return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                })
                .build();
        var environment = new MockEnvironment()
                .withProperty("kfh.security.ssrf.allowed-host-suffixes", "10.17.134.119");
        var tester = new HttpBmcConnectorLiveTester(webClient, environment, CircuitBreaker.ofDefaults("bmcPrivateIpAllowlistTest"));

        var result = tester.test(context(), Map.of(
                "id", UUID.randomUUID().toString(),
                "baseUrl", "https://10.17.134.119",
                "loginEndpoint", "/ims/api/v1/access_keys/login",
                "eventsEndpoint", "/events-service/api/v1.0/events/msearch",
                "minutesBack", 60,
                "pageSize", 100,
                "timeoutSeconds", 5), Map.of("accessKey", "plainCredentialA", "accessSecretKey", "plainCredentialB"));

        assertThat(result).containsEntry("pass", true);
        assertThat(result.toString()).doesNotContain("plainCredentialA", "plainCredentialB", "jwt-token");
    }

    private static HttpBmcConnectorLiveTester tester(WebClient webClient) {
        var environment = new MockEnvironment().withProperty("kfh.security.ssrf.resolve-hosts", "false");
        return new HttpBmcConnectorLiveTester(webClient, environment, CircuitBreaker.ofDefaults("bmcUnitTest"));
    }

    private static Map<String, Object> connector() {
        return Map.of(
                "id", UUID.randomUUID().toString(),
                "baseUrl", "https://unit.onbmc.com",
                "loginEndpoint", "/ims/api/v1/access_keys/login",
                "eventsEndpoint", "/events-service/api/v1.0/events/msearch",
                "minutesBack", 60,
                "pageSize", 100,
                "timeoutSeconds", 5);
    }

    private static TenantContext context() {
        return new TenantContext(UUID.fromString("00000000-0000-4000-8000-000000000001"),
                UUID.fromString("00000000-0000-4000-8000-000000000101"), "KW", "PROD", "bmc-test",
                Set.of("CONNECTOR_TEST"));
    }
}

