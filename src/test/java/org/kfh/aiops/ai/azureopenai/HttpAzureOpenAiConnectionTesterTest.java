package org.kfh.aiops.ai.azureopenai;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class HttpAzureOpenAiConnectionTesterTest {

    @Test
    void shouldPassWhenAzureDeploymentMetadataEndpointRespondsSuccessfully() {
        var requestRef = new AtomicReference<ClientRequest>();
        var webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    requestRef.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                })
                .build();
        var tester = tester(webClient);

        var result = tester.test(context(), Map.of(
                "id", "azure-openai-ui-1",
                "name", "Critical GPT",
                "provider", "AZURE_OPENAI_GPT",
                "purpose", "GPT",
                "countryCodes", java.util.List.of("KW", "BH"),
                "endpoint", "https://unit.openai.azure.com",
                "apiKey", "test-api-key",
                "deployment", "gpt-critical-a",
                "apiVersion", "2024-02-15-preview"));

        assertThat(result)
                .containsEntry("status", "Pass")
                .containsEntry("countryCode", "KW")
                .containsEntry("checkedEndpoint", "https://unit.openai.azure.com")
                .containsEntry("correlationId", "AZURE-TEST");
        assertThat(result.get("countryCodes").toString()).contains("KW", "BH");
        assertThat(requestRef.get().url().toString())
                .isEqualTo("https://unit.openai.azure.com/openai/deployments/gpt-critical-a?api-version=2024-02-15-preview");
        assertThat(result.toString()).doesNotContain("test-api-key");
    }

    @Test
    void shouldPassWhenGpt54ResponsesApiRespondsUsingEntraId() {
        var requestRef = new AtomicReference<ClientRequest>();
        var webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    requestRef.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                })
                .build();
        var credential = tokenCredential("unit-bearer-token");
        var tester = tester(webClient, credential);

        var result = tester.test(context(), Map.ofEntries(
                Map.entry("id", "azure-openai-ui-1"),
                Map.entry("name", "Azure OpenAI GPT 5.4"),
                Map.entry("provider", "AZURE_OPENAI_GPT54"),
                Map.entry("purpose", "GPT"),
                Map.entry("countryCodes", java.util.List.of("ALL")),
                Map.entry("endpoint", "https://unit.services.ai.azure.com/openai/v1"),
                Map.entry("deployment", "gpt-5.4"),
                Map.entry("modelName", "gpt-5.4"),
                Map.entry("authMode", "ENTRA_ID"),
                Map.entry("apiStyle", "RESPONSES"),
                Map.entry("entraScope", "https://ai.azure.com/.default")));

        assertThat(result)
                .containsEntry("status", "Pass")
                .containsEntry("checkedEndpoint", "https://unit.services.ai.azure.com/openai/v1")
                .extracting("message")
                .asString()
                .contains("Responses API")
                .contains("Microsoft Entra ID");
        assertThat(requestRef.get().url().toString())
                .isEqualTo("https://unit.services.ai.azure.com/openai/v1/responses");
        assertThat(requestRef.get().headers().getFirst("Authorization")).isEqualTo("Bearer unit-bearer-token");
        assertThat(requestRef.get().headers()).doesNotContainKey("api-key");
        assertThat(result.toString()).doesNotContain("unit-bearer-token");
    }

    @Test
    void shouldPassWhenGpt54ResponsesApiRespondsUsingApiKey() {
        var requestRef = new AtomicReference<ClientRequest>();
        var webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    requestRef.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                })
                .build();
        var tester = tester(webClient);

        var result = tester.test(context(), Map.ofEntries(
                Map.entry("id", "azure-openai-ui-1"),
                Map.entry("name", "Azure OpenAI GPT 5.4"),
                Map.entry("provider", "AZURE_OPENAI"),
                Map.entry("purpose", "GPT"),
                Map.entry("countryCodes", java.util.List.of("ALL")),
                Map.entry("endpoint", "https://unit.services.ai.azure.com/openai/v1"),
                Map.entry("apiKey", "unit-api-key"),
                Map.entry("deployment", "gpt-5.4"),
                Map.entry("modelName", "gpt-5.4"),
                Map.entry("authMode", "API_KEY"),
                Map.entry("apiStyle", "RESPONSES")));

        assertThat(result)
                .containsEntry("status", "Pass")
                .containsEntry("checkedEndpoint", "https://unit.services.ai.azure.com/openai/v1")
                .extracting("message")
                .asString()
                .contains("Responses API")
                .contains("API key");
        assertThat(requestRef.get().url().toString())
                .isEqualTo("https://unit.services.ai.azure.com/openai/v1/responses");
        assertThat(requestRef.get().headers().getFirst("api-key")).isEqualTo("unit-api-key");
        assertThat(requestRef.get().headers()).doesNotContainKey("Authorization");
        assertThat(result.toString()).doesNotContain("unit-api-key");
    }

    @Test
    void shouldNormalizeAzurePortalRootEndpointForGpt54ResponsesApi() {
        var requestRef = new AtomicReference<ClientRequest>();
        var webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    requestRef.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                })
                .build();
        var tester = tester(webClient);

        var result = tester.test(context(), Map.ofEntries(
                Map.entry("id", "azure-openai-ui-1"),
                Map.entry("name", "Azure OpenAI GPT 5.4"),
                Map.entry("provider", "AZURE_OPENAI"),
                Map.entry("purpose", "GPT"),
                Map.entry("countryCodes", List.of("ALL")),
                Map.entry("endpoint", "https://unit.cognitiveservices.azure.com/"),
                Map.entry("apiKey", "unit-api-key"),
                Map.entry("deployment", "gpt-5.4"),
                Map.entry("modelName", "gpt-5.4"),
                Map.entry("authMode", "API_KEY"),
                Map.entry("apiStyle", "RESPONSES")));

        assertThat(result)
                .containsEntry("status", "Pass")
                .containsEntry("checkedEndpoint", "https://unit.cognitiveservices.azure.com/openai/v1");
        assertThat(requestRef.get().url().toString())
                .isEqualTo("https://unit.cognitiveservices.azure.com/openai/v1/responses");
        assertThat(requestRef.get().headers().getFirst("api-key")).isEqualTo("unit-api-key");
        assertThat(result.toString()).doesNotContain("unit-api-key");
    }

    @Test
    void shouldNotDuplicateResponsesPathWhenFullResponsesEndpointIsSubmitted() {
        var requestRef = new AtomicReference<ClientRequest>();
        var webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    requestRef.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                })
                .build();
        var tester = tester(webClient);

        var result = tester.test(context(), Map.ofEntries(
                Map.entry("id", "azure-openai-ui-1"),
                Map.entry("name", "Azure OpenAI GPT 5.4"),
                Map.entry("provider", "AZURE_OPENAI"),
                Map.entry("purpose", "GPT"),
                Map.entry("countryCodes", List.of("ALL")),
                Map.entry("endpoint", "https://unit.cognitiveservices.azure.com/openai/v1/responses"),
                Map.entry("apiKey", "unit-api-key"),
                Map.entry("deployment", "gpt-5.4"),
                Map.entry("modelName", "gpt-5.4"),
                Map.entry("authMode", "API_KEY"),
                Map.entry("apiStyle", "RESPONSES")));

        assertThat(result)
                .containsEntry("status", "Pass")
                .containsEntry("checkedEndpoint", "https://unit.cognitiveservices.azure.com/openai/v1");
        assertThat(requestRef.get().url().toString())
                .isEqualTo("https://unit.cognitiveservices.azure.com/openai/v1/responses");
        assertThat(result.toString()).doesNotContain("unit-api-key");
    }

    @Test
    void shouldRetryGpt54ResponsesApiWithBearerApiKeyWhenFoundryRejectsApiKeyHeader() {
        var requests = new ArrayList<ClientRequest>();
        var webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    requests.add(request);
                    if (request.headers().containsKey("api-key")) {
                        return Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED).build());
                    }
                    return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                })
                .build();
        var tester = tester(webClient);

        var result = tester.test(context(), Map.ofEntries(
                Map.entry("id", "azure-openai-ui-1"),
                Map.entry("name", "Azure OpenAI GPT 5.4"),
                Map.entry("provider", "AZURE_OPENAI"),
                Map.entry("purpose", "GPT"),
                Map.entry("countryCodes", List.of("ALL")),
                Map.entry("endpoint", "https://unit.services.ai.azure.com/openai/v1"),
                Map.entry("apiKey", "unit-api-key"),
                Map.entry("deployment", "gpt-5.4"),
                Map.entry("modelName", "gpt-5.4"),
                Map.entry("authMode", "API_KEY"),
                Map.entry("apiStyle", "RESPONSES")));

        assertThat(result)
                .containsEntry("status", "Pass")
                .containsEntry("checkedEndpoint", "https://unit.services.ai.azure.com/openai/v1");
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).headers().getFirst("api-key")).isEqualTo("unit-api-key");
        assertThat(requests.get(0).headers()).doesNotContainKey("Authorization");
        assertThat(requests.get(1).headers().getFirst("Authorization")).isEqualTo("Bearer unit-api-key");
        assertThat(requests.get(1).headers()).doesNotContainKey("api-key");
        assertThat(result.toString()).doesNotContain("unit-api-key");
    }

    @Test
    void shouldRequirePlainApiKeyWhenSubmittedKeyIsMasked() {
        var tester = tester(WebClient.builder().exchangeFunction(request -> Mono.error(new AssertionError("unexpected call"))).build());

        var result = tester.test(context(), Map.of(
                "name", "Masked GPT",
                "provider", "AZURE_OPENAI",
                "endpoint", "https://unit.openai.azure.com",
                "apiKey", "••••••••••••",
                "deployment", "gpt-critical-a"));

        assertThat(result)
                .containsEntry("status", "Fail")
                .extracting("message")
                .asString()
                .contains("API key is required");
    }

    @Test
    void shouldRejectNonAzureEndpointWhenTestingAzureOpenAiConnection() {
        var tester = tester(WebClient.builder().exchangeFunction(request -> Mono.error(new AssertionError("unexpected call"))).build());

        var result = tester.test(context(), Map.of(
                "name", "Unsafe GPT",
                "provider", "AZURE_OPENAI",
                "endpoint", "https://localhost:8443",
                "apiKey", "test-api-key",
                "deployment", "gpt-critical-a"));

        assertThat(result)
                .containsEntry("status", "Fail")
                .extracting("message")
                .asString()
                .contains("allowlist");
        assertThat(result.toString()).doesNotContain("test-api-key");
    }

    private static HttpAzureOpenAiConnectionTester tester(WebClient webClient) {
        return tester(webClient, tokenCredential("unused-unit-token"));
    }

    private static HttpAzureOpenAiConnectionTester tester(WebClient webClient, TokenCredential credential) {
        var environment = new MockEnvironment().withProperty("kfh.security.ssrf.resolve-hosts", "false");
        return new HttpAzureOpenAiConnectionTester(webClient, environment, CircuitBreaker.ofDefaults("azureOpenAiUnitTest"), credential);
    }

    private static TokenCredential tokenCredential(String token) {
        return request -> Mono.just(new AccessToken(token, OffsetDateTime.now().plusMinutes(5)));
    }

    private static TenantContext context() {
        return new TenantContext(UUID.fromString("00000000-0000-4000-8000-000000000001"),
                UUID.fromString("00000000-0000-4000-8000-000000000101"), "KW", "PROD", "azure-test",
                Set.of("SETTINGS_READ", "SETTINGS_WRITE"));
    }
}

