package org.kfh.aiops.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.ai.azureopenai.AzureOpenAiConnectionTester;
import org.kfh.aiops.commandcenter.support.CommandCenterReadModel;
import org.kfh.aiops.platform.audit.AuditService;
import org.kfh.aiops.platform.security.SecretCipherService;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.mock.env.MockEnvironment;

class SettingsServiceTest {

    private static final String MASKED_SECRET = "••••••••••••";

    @Test
    void shouldAppendAuditActivityWhenSettingsUpdated() {
        var readModel = new CommandCenterReadModel();
        var service = service(readModel);
        var ctx = context();

        service.update(ctx, Map.of("dashboardRefreshSeconds", 20, "apiToken", "do-not-show"));

        var rows = readModel.audit(ctx);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst())
                .containsEntry("action", "SETTINGS_UPDATED")
                .containsEntry("entityType", "Settings")
                .containsEntry("result", "Success")
                .doesNotContainKeys("apiToken", "password", "secret");
        assertThat(rows.getFirst().get("details").toString()).contains("dashboardRefreshSeconds").doesNotContain("do-not-show");
    }

    @Test
    void shouldReturnSettingsFromApplicationPropertiesWithMaskedSecrets() {
        var environment = environment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://db.example/Kfh_AiOps")
                .withProperty("spring.datasource.username", "aiops_app")
                .withProperty("spring.datasource.password", "do-not-return")
                .withProperty("kfh.security.master-key", "do-not-return-master-key")
                .withProperty("spring.neo4j.uri", "bolt://neo4j.example:7687")
                .withProperty("spring.neo4j.authentication.username", "neo4j_app")
                .withProperty("spring.neo4j.authentication.password", "neo-secret")
                .withProperty("kfh.ai.azure-openai.gpt.endpoint", "https://aoai.example.openai.azure.com")
                .withProperty("kfh.ai.azure-openai.gpt.api-key", "aoai-secret")
                .withProperty("kfh.ai.azure-openai.gpt.deployment-a", "gpt-prod-a")
                .withProperty("kfh.integrations.sharepoint.client-secret", "sp-secret")
                .withProperty("kfh.notifications.teams.mappings[0].domain", "Payments")
                .withProperty("kfh.notifications.teams.mappings[0].webhook-url", "https://example.invalid/webhook");
        var service = service(new CommandCenterReadModel(), environment);

        var settings = service.get(context());

        assertThat(settings.toString())
                .contains("jdbc:postgresql://db.example/Kfh_AiOps", "aiops_app", "bolt://neo4j.example:7687",
                        "neo4j_app", "https://aoai.example.openai.azure.com", "gpt-prod-a", "Payments",
                        "secretMasterKeyConfigured=true")
                .contains("••••••••••••")
                .doesNotContain("do-not-return", "do-not-return-master-key", "neo-secret", "aoai-secret", "sp-secret",
                        "https://example.invalid/webhook");
    }

    @Test
    void shouldReturnNamedAzureOpenAiIntegrationsFromApplicationPropertiesWithMaskedSecrets() {
        var environment = environment()
                .withProperty("kfh.ai.azure-openai.integrations[0].name", "Critical GPT EastUS")
                .withProperty("kfh.ai.azure-openai.integrations[0].provider", "AZURE_OPENAI_GPT")
                .withProperty("kfh.ai.azure-openai.integrations[0].purpose", "GPT")
                .withProperty("kfh.ai.azure-openai.integrations[0].endpoint", "https://kfh-eastus.openai.azure.com")
                .withProperty("kfh.ai.azure-openai.integrations[0].api-key", "do-not-return")
                .withProperty("kfh.ai.azure-openai.integrations[0].deployment", "gpt-critical-a")
                .withProperty("kfh.ai.azure-openai.integrations[0].api-version", "2024-02-15-preview")
                .withProperty("kfh.ai.azure-openai.integrations[0].timeout-seconds", "7");
        var service = service(new CommandCenterReadModel(), environment);

        var settings = service.get(context());

        assertThat(settings.toString())
                .contains("Critical GPT EastUS", "AZURE_OPENAI_GPT", "https://kfh-eastus.openai.azure.com", "gpt-critical-a")
                .contains("••••••••••••")
                .doesNotContain("do-not-return");
    }

    @Test
    void shouldNotReturnBlankDefaultAzureOpenAiIntegrationsWhenNoAiPropertiesConfigured() {
        var service = service(new CommandCenterReadModel(), environment());

        var settings = service.get(context());
        var azureOpenAi = (Map<?, ?>) settings.get("azureOpenAI");

        assertThat((java.util.List<?>) azureOpenAi.get("integrations")).isEmpty();
    }

    @Test
    void shouldNotEchoSubmittedSecretsWhenSettingsUpdated() {
        var stored = new AtomicReference<Map<String, Object>>(Map.of());
        SettingsMetadataStore metadataStore = new SettingsMetadataStore() {
            @Override
            public Map<String, Object> load(TenantContext ctx) {
                return stored.get();
            }

            @Override
            public void save(TenantContext ctx, Map<String, Object> settings) {
                stored.set(settings);
            }
        };
        var service = service(new CommandCenterReadModel(), environment(), Optional.of(metadataStore));

        var response = service.update(context(), Map.of(
                "postgresql", Map.of("password", "new-db-secret"),
                "teams", Map.of("mappings", java.util.List.of(Map.of(
                        "id", "team-secret", "domain", "Payments", "team", "SRE",
                        "channelName", "alerts-prod",
                        "webhookUrl", "https://example.invalid/secret-hook",
                        "enabled", true)))));

        assertThat(response.toString())
                .contains("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022")
                .doesNotContain("new-db-secret", "https://example.invalid/secret-hook", "webhookUrlSecret");
        assertThat(stored.get().toString())
                .contains("webhookUrlSecret")
                .doesNotContain("https://example.invalid/secret-hook");
    }

    @Test
    void shouldPersistIntegrationMetadataButKeepStartupPropertiesInApplicationProperties() {
        var stored = new AtomicReference<Map<String, Object>>(Map.of());
        SettingsMetadataStore metadataStore = new SettingsMetadataStore() {
            @Override
            public Map<String, Object> load(TenantContext ctx) {
                return stored.get();
            }

            @Override
            public void save(TenantContext ctx, Map<String, Object> settings) {
                stored.set(settings);
            }
        };
        var service = service(new CommandCenterReadModel(), environment(), Optional.of(metadataStore));

        service.update(context(), Map.of(
                "azureOpenAI", Map.of("integrations", java.util.List.of(Map.of("name", "Primary GPT"))),
                "postgresql", Map.of("jdbcUrl", "jdbc:postgresql://must-stay-in-properties"),
                "system", Map.of("serverPort", 9443)));

        assertThat(stored.get()).containsOnlyKeys("azureOpenAI");
        assertThat(stored.get().toString()).contains("Primary GPT").doesNotContain("must-stay-in-properties", "9443");
        assertThat(service.get(context()).toString()).contains("Primary GPT", "configurationOwnership", "spring.datasource.url");
    }

    @Test
    void shouldKeepSettingsIsolatedByCountryWithDurableMetadataStore() {
        var stored = new AtomicReference<Map<String, Map<String, Object>>>(
                new java.util.LinkedHashMap<>());
        SettingsMetadataStore metadataStore = new SettingsMetadataStore() {
            @Override
            public Map<String, Object> load(TenantContext ctx) {
                return stored.get().getOrDefault(scope(ctx), Map.of());
            }

            @Override
            public void save(TenantContext ctx, Map<String, Object> settings) {
                var snapshot = new java.util.LinkedHashMap<>(stored.get());
                var existing = new java.util.LinkedHashMap<>(snapshot.getOrDefault(scope(ctx), Map.of()));
                existing.putAll(settings);
                snapshot.put(scope(ctx), existing);
                stored.set(snapshot);
            }

            private String scope(TenantContext ctx) {
                return ctx.tenantId() + "|" + ctx.countryCode() + "|" + ctx.environment();
            }
        };
        var service = service(new CommandCenterReadModel(), environment(), Optional.of(metadataStore));

        service.update(context("KW"), Map.of("infrastructure", Map.of("connections", java.util.List.of(Map.of(
                "id", "kafka-kw", "name", "KW Kafka", "type", "KAFKA", "endpoint", "kw-broker:9092",
                "countryCodes", java.util.List.of("KW"))))));

        assertThat(service.get(context("KW")).toString()).contains("KW Kafka", "kw-broker:9092", "KW");
        assertThat(service.get(context("BH")).toString()).doesNotContain("KW Kafka", "kw-broker:9092");
    }

    @Test
    void shouldAppendAuditActivityWhenSettingsTestRequested() {
        var readModel = new CommandCenterReadModel();
        var service = service(readModel);
        var ctx = context();

        service.test(ctx, "notifications", Map.of("webhookUrl", "https://example.invalid/hook"));

        assertThat(readModel.audit(ctx)).hasSize(1);
        assertThat(readModel.audit(ctx).getFirst())
                .containsEntry("action", "SETTINGS_TEST_REQUESTED")
                .containsEntry("entityType", "Settings")
                .containsEntry("entityId", "notifications");
    }

    @Test
    void shouldReturnNotificationTestFailureMessageWhenLiveNotifierIsNotImplemented() {
        var service = service(new CommandCenterReadModel());

        var response = service.test(context(), "teams", Map.of("webhookUrl", "https://example.invalid/secret-hook"));

        assertThat(response)
                .containsEntry("status", "Fail")
                .containsEntry("pass", false)
                .containsEntry("correlationId", context().correlationId());
        assertThat(response.get("message").toString())
                .contains("Live notification test is not implemented", "No Teams webhook call was sent")
                .doesNotContain("https://example.invalid/secret-hook");
    }

    @Test
    void shouldReturnSecretSafeFailureMessageWhenSettingsTesterThrowsJavaException() {
        var readModel = new CommandCenterReadModel();
        AzureOpenAiConnectionTester tester = (ctx, request) -> {
            throw new IllegalStateException("Azure probe returned HTTP 401 authorization=Bearer-token apiKey=plain-key");
        };
        AuditService audit = (ctx, action, entityType, entityId, beforeState, afterState) -> { };
        var env = environment();
        var service = new SettingsService(audit, readModel, env, tester, passingNeo4jTester(), passingInfrastructureTester(),
                new SecretCipherService(env), Optional.empty());

        var response = service.test(context(), "azureOpenAI.integrations.0", Map.of("apiKey", "plain-key"));

        assertThat(response).containsEntry("status", "Fail").containsEntry("pass", false);
        assertThat(response.get("message").toString())
                .contains("HTTP 401", "authorization=masked", "apiKey=masked")
                .doesNotContain("Bearer-token", "plain-key");
        assertThat(readModel.audit(context()).getFirst().toString()).doesNotContain("plain-key");
    }

    @Test
    void shouldDelegateAzureOpenAiSettingsTestToConnectionTester() {
        var readModel = new CommandCenterReadModel();
        var submitted = new AtomicReference<Map<String, Object>>();
        AzureOpenAiConnectionTester tester = (ctx, request) -> {
            submitted.set(request);
            return Map.of("section", "azureOpenAI", "status", "Pass", "correlationId", ctx.correlationId());
        };
        AuditService audit = (ctx, action, entityType, entityId, beforeState, afterState) -> { };
        var env = environment();
        var service = new SettingsService(audit, readModel, env, tester, passingNeo4jTester(), passingInfrastructureTester(),
                new SecretCipherService(env), Optional.empty());

        var response = service.test(context(), "azureOpenAI.integrations.0",
                Map.of("name", "Primary GPT", "apiKey", "do-not-log"));

        assertThat(response).containsEntry("status", "Pass");
        assertThat(submitted.get()).containsEntry("name", "Primary GPT");
        assertThat(readModel.audit(context()).getFirst().toString()).doesNotContain("do-not-log");
    }

    @Test
    void shouldEncryptSettingsManagedAiProviderSecretAndResolveItForMaskedCountryTest() {
        var stored = new AtomicReference<Map<String, Object>>(Map.of());
        SettingsMetadataStore metadataStore = new SettingsMetadataStore() {
            @Override
            public Map<String, Object> load(TenantContext ctx) {
                return stored.get();
            }

            @Override
            public void save(TenantContext ctx, Map<String, Object> settings) {
                stored.set(settings);
            }
        };
        var submitted = new AtomicReference<Map<String, Object>>();
        AzureOpenAiConnectionTester tester = (ctx, request) -> {
            submitted.set(request);
            return Map.of("section", "azureOpenAI", "status", "Pass", "countryCode", request.get("countryCode"),
                    "countryCodes", request.get("countryCodes"),
                    "correlationId", ctx.correlationId());
        };
        var env = environment().withProperty("kfh.security.master-key", "unit-test-master-key");
        AuditService audit = (ctx, action, entityType, entityId, beforeState, afterState) -> { };
        var service = new SettingsService(audit, new CommandCenterReadModel(), env, tester, passingNeo4jTester(),
                passingInfrastructureTester(), new SecretCipherService(env), Optional.of(metadataStore));

        var response = service.update(context(), Map.of("azureOpenAI", Map.of("integrations", java.util.List.of(Map.of(
                "id", "aoai-kw", "name", "KW GPT", "provider", "AZURE_OPENAI", "purpose", "GPT",
                "countryCodes", java.util.List.of("KW", "BH"), "endpoint", "https://unit.openai.azure.com", "apiKey", "plain-ai-key",
                "deployment", "gpt-kw")))));

        assertThat(response.toString()).contains("••••••••••••", "KW", "BH").doesNotContain("plain-ai-key", "apiKeySecret");
        assertThat(stored.get().toString()).contains("apiKeySecret").doesNotContain("plain-ai-key");

        var testResponse = service.test(context(), "azureOpenAI.integrations.0", Map.of(
                "id", "aoai-kw", "name", "KW GPT", "provider", "AZURE_OPENAI", "purpose", "GPT",
                "countryCodes", java.util.List.of("KW", "BH"), "endpoint", "https://unit.openai.azure.com", "apiKey", MASKED_SECRET,
                "deployment", "gpt-kw"));

        assertThat(testResponse).containsEntry("status", "Pass").containsEntry("countryCode", "KW");
        assertThat(testResponse.get("countryCodes").toString()).contains("KW", "BH");
        assertThat(submitted.get()).containsEntry("apiKey", "plain-ai-key").containsEntry("countryCode", "KW");
        assertThat(submitted.get().get("countryCodes").toString()).contains("KW", "BH");
    }

    @Test
    void shouldDelegateNeo4jSettingsTestToConnectionTesterWithStartupPassword() {
        var submitted = new AtomicReference<Map<String, Object>>();
        Neo4jConnectionTester neo4jTester = (ctx, section, request) -> {
            submitted.set(request);
            return Map.of("section", section, "status", "Pass", "correlationId", ctx.correlationId());
        };
        AuditService audit = (ctx, action, entityType, entityId, beforeState, afterState) -> { };
        var env = environment()
                .withProperty("spring.neo4j.uri", "bolt://neo4j.example:7687")
                .withProperty("spring.neo4j.authentication.username", "neo4j_app")
                .withProperty("spring.neo4j.authentication.password", "neo-secret")
                .withProperty("spring.data.neo4j.database", "topology");
        var service = new SettingsService(audit, new CommandCenterReadModel(), env,
                (ctx, request) -> Map.of("section", "azureOpenAI", "status", "Pass"), neo4jTester,
                passingInfrastructureTester(), new SecretCipherService(env), Optional.empty());

        var response = service.test(context(), "neo4j", Map.of(
                "boltUrl", "bolt://neo4j.example:7687", "user", "neo4j_app", "password", "••••••••••••",
                "database", "topology"));

        assertThat(response).containsEntry("status", "Pass");
        assertThat(submitted.get())
                .containsEntry("boltUrl", "bolt://neo4j.example:7687")
                .containsEntry("password", "neo-secret")
                .containsEntry("database", "topology");
    }

    @Test
    void shouldEncryptSettingsManagedNeo4jPasswordAndResolveItForMaskedTest() {
        var stored = new AtomicReference<Map<String, Object>>(Map.of());
        SettingsMetadataStore metadataStore = new SettingsMetadataStore() {
            @Override
            public Map<String, Object> load(TenantContext ctx) {
                return stored.get();
            }

            @Override
            public void save(TenantContext ctx, Map<String, Object> settings) {
                stored.set(settings);
            }
        };
        var submitted = new AtomicReference<Map<String, Object>>();
        Neo4jConnectionTester neo4jTester = (ctx, section, request) -> {
            submitted.set(request);
            return Map.of("section", section, "status", "Pass", "correlationId", ctx.correlationId());
        };
        AuditService audit = (ctx, action, entityType, entityId, beforeState, afterState) -> { };
        var env = environment().withProperty("kfh.security.master-key", "neo4j-settings-test-master-key");
        var service = new SettingsService(audit, new CommandCenterReadModel(), env,
                (ctx, request) -> Map.of("section", "azureOpenAI", "status", "Pass"), neo4jTester,
                passingInfrastructureTester(), new SecretCipherService(env), Optional.of(metadataStore));

        var response = service.update(context(), Map.of("neo4j", Map.of(
                "boltUrl", "bolt://neo4j.example:7687", "user", "neo4j_app", "password", "plain-neo-secret",
                "database", "topology", "healthIndicatorEnabled", true)));

        assertThat(response.toString()).contains("••••••••••••", "bolt://neo4j.example:7687").doesNotContain("plain-neo-secret", "passwordSecret");
        assertThat(stored.get().toString()).contains("passwordSecret").doesNotContain("plain-neo-secret");

        var testResponse = service.test(context(), "neo4j", Map.of(
                "boltUrl", "bolt://neo4j.example:7687", "user", "neo4j_app", "password", "••••••••••••",
                "database", "topology"));

        assertThat(testResponse).containsEntry("status", "Pass");
        assertThat(submitted.get()).containsEntry("password", "plain-neo-secret");
    }

    @Test
    void shouldDelegateInfrastructureSettingsTestsToConnectionTester() {
        var submitted = new AtomicReference<Map<String, Object>>();
        InfrastructureConnectionTester infrastructureTester = (ctx, section, request) -> {
            submitted.set(request);
            return Map.of("section", section, "status", "Pass", "type", request.get("type"),
                    "correlationId", ctx.correlationId());
        };
        AuditService audit = (ctx, action, entityType, entityId, beforeState, afterState) -> { };
        var env = environment();
        var service = new SettingsService(audit, new CommandCenterReadModel(), env,
                (ctx, request) -> Map.of("section", "azureOpenAI", "status", "Pass"), passingNeo4jTester(),
                infrastructureTester, new SecretCipherService(env), Optional.empty());

        var kafka = service.test(context(), "infrastructure.connections.0", Map.of(
                "type", "KAFKA", "endpoint", "broker1:9092", "protocol", "PLAINTEXT", "secret", "do-not-log"));

        assertThat(kafka).containsEntry("status", "Pass").containsEntry("type", "KAFKA");
        assertThat(submitted.get()).containsEntry("endpoint", "broker1:9092");
        assertThat(service.test(context(), "infrastructure.connections.1", Map.of(
                "type", "INDEX_STORAGE", "endpoint", "file:/data/aiops-index", "provider", "LOCAL")))
                .containsEntry("status", "Pass")
                .containsEntry("type", "INDEX_STORAGE");
    }

    @Test
    void shouldEncryptInfrastructureConnectorSecretAndResolveMaskedTestRequest() {
        var stored = new AtomicReference<Map<String, Object>>(Map.of());
        SettingsMetadataStore metadataStore = new SettingsMetadataStore() {
            @Override
            public Map<String, Object> load(TenantContext ctx) {
                return stored.get();
            }

            @Override
            public void save(TenantContext ctx, Map<String, Object> settings) {
                stored.set(settings);
            }
        };
        var submitted = new AtomicReference<Map<String, Object>>();
        InfrastructureConnectionTester infrastructureTester = (ctx, section, request) -> {
            submitted.set(request);
            return Map.of("section", section, "status", "Pass", "type", request.get("type"),
                    "countryCodes", request.get("countryCodes"), "correlationId", ctx.correlationId());
        };
        var env = environment().withProperty("kfh.security.master-key", "infrastructure-secret-test-master-key");
        AuditService audit = (ctx, action, entityType, entityId, beforeState, afterState) -> { };
        var service = new SettingsService(audit, new CommandCenterReadModel(), env,
                (ctx, request) -> Map.of("section", "azureOpenAI", "status", "Pass"), passingNeo4jTester(),
                infrastructureTester, new SecretCipherService(env), Optional.of(metadataStore));

        var response = service.update(context("KW"), Map.of("infrastructure", Map.of("connections", java.util.List.of(Map.of(
                "id", "kafka-kw", "name", "KW Kafka", "type", "KAFKA", "endpoint", "kw-broker:9093",
                "protocol", "SASL_SSL", "username", "svc-kafka", "secret", "plain-kafka-secret",
                "countryCodes", java.util.List.of("KW"))))));

        assertThat(response.toString()).contains("••••••••••••", "KW Kafka", "KW").doesNotContain("plain-kafka-secret", "secretSecret");
        assertThat(stored.get().toString()).contains("secretSecret", "countryCodes").doesNotContain("plain-kafka-secret");

        var testResponse = service.test(context("KW"), "infrastructure.connections.0", Map.of(
                "id", "kafka-kw", "name", "KW Kafka", "type", "KAFKA", "endpoint", "kw-broker:9093",
                "protocol", "SASL_SSL", "username", "svc-kafka", "secret", "••••••••••••",
                "countryCodes", java.util.List.of("KW")));

        assertThat(testResponse).containsEntry("status", "Pass").containsEntry("type", "KAFKA");
        assertThat(submitted.get()).containsEntry("secret", "plain-kafka-secret");
        assertThat(submitted.get().get("countryCodes").toString()).contains("KW");
    }

    private static SettingsService service(CommandCenterReadModel readModel) {
        return service(readModel, environment());
    }

    private static SettingsService service(CommandCenterReadModel readModel, MockEnvironment environment) {
        return service(readModel, environment, Optional.empty());
    }

    private static SettingsService service(CommandCenterReadModel readModel, MockEnvironment environment,
            Optional<SettingsMetadataStore> metadataStore) {
        AuditService audit = (ctx, action, entityType, entityId, beforeState, afterState) -> { };
        return new SettingsService(audit, readModel, environment,
                (ctx, request) -> Map.of("section", "azureOpenAI", "status", "Pass", "correlationId", ctx.correlationId()),
                passingNeo4jTester(),
                passingInfrastructureTester(),
                new SecretCipherService(environment),
                metadataStore);
    }

    @Test
    void shouldPreserveEverySettingsSectionAcrossSimulatedRestart() {
        var stored = new AtomicReference<Map<String, Object>>(Map.of());
        SettingsMetadataStore metadataStore = new SettingsMetadataStore() {
            @Override
            public Map<String, Object> load(TenantContext ctx) {
                return stored.get();
            }

            @Override
            public void save(TenantContext ctx, Map<String, Object> settings) {
                var merged = new java.util.LinkedHashMap<String, Object>(stored.get());
                merged.putAll(settings);
                stored.set(merged);
            }
        };
        var env = environment().withProperty("kfh.security.master-key", "restart-regression-master-key");
        var serviceBeforeRestart = service(new CommandCenterReadModel(), env, Optional.of(metadataStore));

        serviceBeforeRestart.update(context("KW"), Map.of(
                "azureOpenAI", Map.of("integrations", java.util.List.of(Map.of(
                        "id", "aoai-kw", "name", "KW GPT", "provider", "AZURE_OPENAI", "purpose", "GPT",
                        "countryCodes", java.util.List.of("KW"), "endpoint", "https://unit.openai.azure.com",
                        "apiKey", "plain-ai-key", "deployment", "gpt-kw"))),
                "databases", Map.of("connections", java.util.List.of(Map.of(
                        "id", "db-kw", "name", "KW Postgres", "type", "POSTGRESQL", "endpoint", "kw-db:5432",
                        "username", "svc", "secret", "plain-db-secret",
                        "countryCodes", java.util.List.of("KW")))),
                "neo4j", Map.of("boltUrl", "bolt://neo4j.example:7687", "user", "neo4j_app",
                        "password", "plain-neo-secret", "database", "topology", "healthIndicatorEnabled", true),
                "sharepoint", Map.of("connections", java.util.List.of(Map.of(
                        "id", "sp-kw", "name", "KW SharePoint", "type", "SHAREPOINT",
                        "endpoint", "https://kfh.sharepoint.com/sites/aiops",
                        "secret", "plain-sp-secret",
                        "countryCodes", java.util.List.of("KW")))),
                "teams", Map.of("mappings", java.util.List.of(Map.of(
                        "id", "team-kw", "domain", "Payments", "team", "SRE-KW",
                        "channelName", "alerts-prod",
                        "webhookUrl", "https://outlook.office.com/webhook/plain-secret-hook",
                        "enabled", true))),
                "infrastructure", Map.of("connections", java.util.List.of(Map.of(
                        "id", "kafka-kw", "name", "KW Kafka", "type", "KAFKA",
                        "endpoint", "kw-broker:9093", "protocol", "SASL_SSL",
                        "username", "svc-kafka", "secret", "plain-kafka-secret",
                        "countryCodes", java.util.List.of("KW")))),
                "system", Map.of("dashboardRefreshSeconds", 45, "quietPeriodMinutes", 25, "aiMode", "EVIDENCE_PACK_ONLY",
                        "redisHost", "redis.example", "redisPort", 6380, "redisPassword", "plain-redis-secret",
                        "kafkaBootstrapServers", "kw-broker:9092", "kafkaPassword", "plain-kafka-secret",
                        "indexStorageProvider", "LOCAL", "indexStoragePath", "/data/aiops-index")));

        // Simulate restart: build a NEW service instance backed by the SAME metadata store.
        var serviceAfterRestart = service(new CommandCenterReadModel(), env, Optional.of(metadataStore));
        var reloaded = serviceAfterRestart.get(context("KW"));

        assertThat(reloaded.toString()).contains(
                "KW GPT", "https://unit.openai.azure.com",
                "KW Postgres", "kw-db:5432",
                "bolt://neo4j.example:7687", "topology",
                "KW SharePoint", "https://kfh.sharepoint.com/sites/aiops",
                "SRE-KW", "alerts-prod",
                "KW Kafka", "kw-broker:9093",
                "redis.example", "kw-broker:9092",
                "/data/aiops-index", "EVIDENCE_PACK_ONLY",
                "dashboardRefreshSeconds=45");
        assertThat(reloaded.toString())
                .doesNotContain("plain-ai-key", "plain-db-secret", "plain-neo-secret",
                        "plain-sp-secret", "plain-secret-hook", "plain-kafka-secret", "plain-redis-secret");
        assertThat(reloaded.toString())
                .doesNotContain("apiKeySecret", "passwordSecret", "secretSecret",
                        "webhookUrlSecret", "redisPasswordSecret", "kafkaPasswordSecret");
        assertThat(stored.get()).containsKeys("azureOpenAI", "databases", "neo4j", "sharepoint", "teams",
                "infrastructure", "system");
    }

    @Test
    void shouldFailLoudlyWhenSettingsPersistenceIsNotAvailable() {
        var service = service(new CommandCenterReadModel(), environment(), Optional.empty());

        Assertions.assertThatThrownBy(() -> service.update(context(), Map.of("azureOpenAI", Map.of("integrations",
                        java.util.List.of(Map.of("id", "aoai", "name", "x", "endpoint", "https://x.openai.azure.com",
                                "deployment", "gpt-1", "apiKey", "plain"))))))
                .isInstanceOf(org.kfh.aiops.platform.exception.ServiceUnavailableException.class)
                .hasMessageContaining("Settings cannot be persisted");
    }

    @Test
    void shouldNotPersistStartupOnlySystemFields() {
        var stored = new AtomicReference<Map<String, Object>>(Map.of());
        SettingsMetadataStore metadataStore = new SettingsMetadataStore() {
            @Override
            public Map<String, Object> load(TenantContext ctx) {
                return stored.get();
            }

            @Override
            public void save(TenantContext ctx, Map<String, Object> settings) {
                stored.set(settings);
            }
        };
        var service = service(new CommandCenterReadModel(), environment(), Optional.of(metadataStore));

        service.update(context(), Map.of("system", Map.of(
                "dashboardRefreshSeconds", 90,
                "serverPort", 9443,
                "sslEnabled", false,
                "countriesEnabled", "ZZ",
                "secretMasterKeyConfigured", true)));

        assertThat(stored.get().toString())
                .contains("dashboardRefreshSeconds=90")
                .doesNotContain("9443", "ZZ");
    }

    private static Neo4jConnectionTester passingNeo4jTester() {
        return (ctx, section, request) -> Map.of("section", section, "status", "Pass", "correlationId", ctx.correlationId());
    }

    private static InfrastructureConnectionTester passingInfrastructureTester() {
        return (ctx, section, request) -> Map.of("section", section, "status", "Pass", "type", request.get("type"),
                "correlationId", ctx.correlationId());
    }

    private static MockEnvironment environment() {
        return new MockEnvironment()
                .withProperty("spring.datasource.driver-class-name", "org.postgresql.Driver")
                .withProperty("spring.flyway.enabled", "true")
                .withProperty("spring.data.neo4j.database", "neo4j")
                .withProperty("spring.data.redis.port", "6379")
                .withProperty("server.port", "8443")
                .withProperty("server.ssl.enabled", "true")
                .withProperty("kfh.security.master-key", "settings-service-test-master-key");
    }

    private static TenantContext context() {
        return context("KW");
    }

    private static TenantContext context(String countryCode) {
        return new TenantContext(UUID.fromString("00000000-0000-4000-8000-000000000001"),
                UUID.fromString("00000000-0000-4000-8000-000000000101"), countryCode, "PROD", "settings-test",
                Set.of("SETTINGS_READ", "SETTINGS_WRITE", "AUDIT_READ"));
    }

    @Test
    void shouldReloadCountryScopedAiProviderAfterRestartWhenSessionCountryMatchesProviderScope() {
        var stored = new AtomicReference<Map<String, Map<String, Object>>>(new java.util.LinkedHashMap<>());
        SettingsMetadataStore metadataStore = new SettingsMetadataStore() {
            @Override
            public Map<String, Object> load(TenantContext ctx) {
                var merged = new java.util.LinkedHashMap<String, Object>();
                var allScope = stored.get().get(scope(ctx, "ALL"));
                if (allScope != null) {
                    merged.putAll(allScope);
                }
                var countryScope = stored.get().get(scope(ctx, ctx.countryCode()));
                if (countryScope != null) {
                    merged.putAll(countryScope);
                }
                return merged;
            }

            @Override
            public void save(TenantContext ctx, Map<String, Object> settings) {
                var azure = (Map<?, ?>) settings.get("azureOpenAI");
                var integrations = azure == null ? java.util.List.of() : (java.util.List<?>) azure.get("integrations");
                var scopes = new java.util.LinkedHashSet<String>();
                integrations.forEach(item -> {
                    if (item instanceof Map<?, ?> map) {
                        var countryCodes = map.get("countryCodes");
                        if (countryCodes instanceof Iterable<?> iterable) {
                            iterable.forEach(code -> scopes.add(String.valueOf(code)));
                        } else if (map.get("countryCode") != null) {
                            scopes.add(String.valueOf(map.get("countryCode")));
                        }
                    }
                });
                if (scopes.isEmpty()) {
                    scopes.add(ctx.countryCode());
                }
                var snapshot = new java.util.LinkedHashMap<>(stored.get());
                scopes.forEach(scope -> snapshot.put(scope(ctx, scope), settings));
                stored.set(snapshot);
            }

            private String scope(TenantContext ctx, String countryCode) {
                return ctx.tenantId() + "|" + countryCode + "|" + ctx.environment();
            }
        };
        var env = environment().withProperty("kfh.security.master-key", "restart-country-scope-master-key");
        var serviceBeforeRestart = service(new CommandCenterReadModel(), env, Optional.of(metadataStore));

        serviceBeforeRestart.update(context("KW"), Map.of("azureOpenAI", Map.of("integrations", java.util.List.of(Map.of(
                "id", "aoai-kw", "name", "KW GPT", "provider", "AZURE_OPENAI", "purpose", "GPT",
                "countryCodes", java.util.List.of("KW"), "endpoint", "https://kw.openai.azure.com",
                "apiKey", "plain-ai-key", "deployment", "gpt-kw")))));

        var serviceAfterRestart = service(new CommandCenterReadModel(), env, Optional.of(metadataStore));
        var reloaded = serviceAfterRestart.get(context("KW"));

        assertThat(reloaded.toString()).contains("KW GPT", "https://kw.openai.azure.com");
        assertThat(reloaded.toString()).doesNotContain("plain-ai-key", "apiKeySecret");
    }

    @Test
    void shouldReloadAllCountryAiProviderAfterRestartForDifferentCountrySession() {
        var stored = new AtomicReference<Map<String, Map<String, Object>>>(new java.util.LinkedHashMap<>());
        SettingsMetadataStore metadataStore = new SettingsMetadataStore() {
            @Override
            public Map<String, Object> load(TenantContext ctx) {
                var merged = new java.util.LinkedHashMap<String, Object>();
                var allScope = stored.get().get(scope(ctx, "ALL"));
                if (allScope != null) {
                    merged.putAll(allScope);
                }
                var countryScope = stored.get().get(scope(ctx, ctx.countryCode()));
                if (countryScope != null) {
                    merged.putAll(countryScope);
                }
                return merged;
            }

            @Override
            public void save(TenantContext ctx, Map<String, Object> settings) {
                var snapshot = new java.util.LinkedHashMap<>(stored.get());
                snapshot.put(scope(ctx, "ALL"), settings);
                stored.set(snapshot);
            }

            private String scope(TenantContext ctx, String countryCode) {
                return ctx.tenantId() + "|" + countryCode + "|" + ctx.environment();
            }
        };
        var env = environment().withProperty("kfh.security.master-key", "restart-all-scope-master-key");
        var serviceBeforeRestart = service(new CommandCenterReadModel(), env, Optional.of(metadataStore));

        serviceBeforeRestart.update(context("KW"), Map.of("azureOpenAI", Map.of("integrations", java.util.List.of(Map.of(
                "id", "aoai-global", "name", "Global GPT", "provider", "AZURE_OPENAI", "purpose", "GPT",
                "countryCodes", java.util.List.of("ALL"), "endpoint", "https://global.openai.azure.com",
                "apiKey", "plain-global-key", "deployment", "gpt-global")))));

        var serviceAfterRestart = service(new CommandCenterReadModel(), env, Optional.of(metadataStore));
        var reloadedForBh = serviceAfterRestart.get(context("BH"));

        assertThat(reloadedForBh.toString()).contains("Global GPT", "https://global.openai.azure.com", "ALL");
        assertThat(reloadedForBh.toString()).doesNotContain("plain-global-key", "apiKeySecret");
    }

    @Test
    void shouldShowAiProviderOnlyForSelectedCountriesWhenSwitchingCountry() {
        var stored = new AtomicReference<Map<String, Map<String, Object>>>(new java.util.LinkedHashMap<>());
        SettingsMetadataStore metadataStore = new SettingsMetadataStore() {
            @Override
            public Map<String, Object> load(TenantContext ctx) {
                var merged = new java.util.LinkedHashMap<String, Object>();
                var allScope = stored.get().get(scope(ctx, "ALL"));
                if (allScope != null) {
                    merged.putAll(allScope);
                }
                var countryScope = stored.get().get(scope(ctx, ctx.countryCode()));
                if (countryScope != null) {
                    merged.putAll(countryScope);
                }
                return merged;
            }

            @Override
            public void save(TenantContext ctx, Map<String, Object> settings) {
                var azure = (Map<?, ?>) settings.get("azureOpenAI");
                var integrations = azure == null ? java.util.List.of() : (java.util.List<?>) azure.get("integrations");
                var scopes = new java.util.LinkedHashSet<String>();
                integrations.forEach(item -> {
                    if (item instanceof Map<?, ?> map) {
                        var countryCodes = map.get("countryCodes");
                        if (countryCodes instanceof Iterable<?> iterable) {
                            iterable.forEach(code -> scopes.add(String.valueOf(code)));
                        }
                    }
                });
                var snapshot = new java.util.LinkedHashMap<>(stored.get());
                scopes.forEach(scope -> snapshot.put(scope(ctx, scope), settings));
                stored.set(snapshot);
            }

            private String scope(TenantContext ctx, String countryCode) {
                return ctx.tenantId() + "|" + countryCode + "|" + ctx.environment();
            }
        };
        var service = service(new CommandCenterReadModel(), environment(), Optional.of(metadataStore));

        service.update(context("KW"), Map.of("azureOpenAI", Map.of("integrations", java.util.List.of(
                Map.of("id", "aoai-kw-bh", "name", "KW BH GPT", "provider", "AZURE_OPENAI", "purpose", "GPT",
                        "countryCodes", java.util.List.of("KW", "BH"), "endpoint", "https://kwbh.openai.azure.com",
                        "apiKey", "plain-ai-key", "deployment", "gpt-kwbh"),
                Map.of("id", "aoai-all", "name", "Global GPT", "provider", "AZURE_OPENAI", "purpose", "GPT",
                        "countryCodes", java.util.List.of("ALL"), "endpoint", "https://global.openai.azure.com",
                        "apiKey", "plain-global-key", "deployment", "gpt-global")))));

        assertThat(service.get(context("KW")).toString()).contains("KW BH GPT", "Global GPT");
        assertThat(service.get(context("BH")).toString()).contains("KW BH GPT", "Global GPT");
        assertThat(service.get(context("EG")).toString()).contains("Global GPT").doesNotContain("KW BH GPT", "https://kwbh.openai.azure.com");
    }

    @Test
    void shouldHideCountryScopedAiProviderAfterRestartForNonMatchingCountrySession() {
        var stored = new AtomicReference<Map<String, Map<String, Object>>>(new java.util.LinkedHashMap<>());
        SettingsMetadataStore metadataStore = new SettingsMetadataStore() {
            @Override
            public Map<String, Object> load(TenantContext ctx) {
                var merged = new java.util.LinkedHashMap<String, Object>();
                var allScope = stored.get().get(scope(ctx, "ALL"));
                if (allScope != null) {
                    merged.putAll(allScope);
                }
                var countryScope = stored.get().get(scope(ctx, ctx.countryCode()));
                if (countryScope != null) {
                    merged.putAll(countryScope);
                }
                return merged;
            }

            @Override
            public void save(TenantContext ctx, Map<String, Object> settings) {
                var azure = (Map<?, ?>) settings.get("azureOpenAI");
                var integrations = azure == null ? java.util.List.of() : (java.util.List<?>) azure.get("integrations");
                var scopes = new java.util.LinkedHashSet<String>();
                integrations.forEach(item -> {
                    if (item instanceof Map<?, ?> map) {
                        var countryCodes = map.get("countryCodes");
                        if (countryCodes instanceof Iterable<?> iterable) {
                            iterable.forEach(code -> scopes.add(String.valueOf(code)));
                        }
                    }
                });
                var snapshot = new java.util.LinkedHashMap<>(stored.get());
                scopes.forEach(scope -> snapshot.put(scope(ctx, scope), settings));
                stored.set(snapshot);
            }

            private String scope(TenantContext ctx, String countryCode) {
                return ctx.tenantId() + "|" + countryCode + "|" + ctx.environment();
            }
        };
        var env = environment().withProperty("kfh.security.master-key", "restart-country-visibility-master-key");
        var serviceBeforeRestart = service(new CommandCenterReadModel(), env, Optional.of(metadataStore));

        serviceBeforeRestart.update(context("KW"), Map.of("azureOpenAI", Map.of("integrations", java.util.List.of(Map.of(
                "id", "aoai-kw-bh", "name", "KW BH GPT", "provider", "AZURE_OPENAI", "purpose", "GPT",
                "countryCodes", java.util.List.of("KW", "BH"), "endpoint", "https://kwbh.openai.azure.com",
                "apiKey", "plain-ai-key", "deployment", "gpt-kwbh")))));

        var serviceAfterRestart = service(new CommandCenterReadModel(), env, Optional.of(metadataStore));

        assertThat(serviceAfterRestart.get(context("BH")).toString()).contains("KW BH GPT");
        assertThat(serviceAfterRestart.get(context("EG")).toString()).doesNotContain("KW BH GPT", "https://kwbh.openai.azure.com");
    }

    @Test
    void shouldReplaceCountryScopedAiProviderListWhenSameScopeIsSavedAgain() {
        var stored = new AtomicReference<Map<String, Map<String, Object>>>(new java.util.LinkedHashMap<>());
        SettingsMetadataStore metadataStore = new SettingsMetadataStore() {
            @Override
            public Map<String, Object> load(TenantContext ctx) {
                var merged = new java.util.LinkedHashMap<String, Object>();
                var allScope = stored.get().get(scope(ctx, "ALL"));
                if (allScope != null) {
                    merged.putAll(allScope);
                }
                var countryScope = stored.get().get(scope(ctx, ctx.countryCode()));
                if (countryScope != null) {
                    merged.putAll(countryScope);
                }
                return merged;
            }

            @Override
            public void save(TenantContext ctx, Map<String, Object> settings) {
                var snapshot = new java.util.LinkedHashMap<>(stored.get());
                var azure = (Map<?, ?>) settings.get("azureOpenAI");
                var integrations = azure == null ? java.util.List.of() : (java.util.List<?>) azure.get("integrations");
                var scopes = new java.util.LinkedHashSet<String>();
                integrations.forEach(item -> {
                    if (item instanceof Map<?, ?> map) {
                        var countryCodes = map.get("countryCodes");
                        if (countryCodes instanceof Iterable<?> iterable) {
                            iterable.forEach(code -> scopes.add(String.valueOf(code)));
                        }
                    }
                });
                if (scopes.isEmpty()) {
                    scopes.add(ctx.countryCode());
                }
                scopes.forEach(scope -> snapshot.put(scope(ctx, scope), settings));
                stored.set(snapshot);
            }

            private String scope(TenantContext ctx, String countryCode) {
                return ctx.tenantId() + "|" + countryCode + "|" + ctx.environment();
            }
        };
        var env = environment().withProperty("kfh.security.master-key", "replace-country-scope-master-key");
        var service = service(new CommandCenterReadModel(), env, Optional.of(metadataStore));

        service.update(context("KW"), Map.of("azureOpenAI", Map.of("integrations", java.util.List.of(
                Map.of("id", "aoai-old", "name", "Old KW GPT", "provider", "AZURE_OPENAI", "purpose", "GPT",
                        "countryCodes", java.util.List.of("KW"), "endpoint", "https://old-kw.openai.azure.com",
                        "apiKey", "plain-old-key", "deployment", "gpt-old")))));

        service.update(context("KW"), Map.of("azureOpenAI", Map.of("integrations", java.util.List.of(
                Map.of("id", "aoai-new", "name", "New KW GPT", "provider", "AZURE_OPENAI", "purpose", "GPT",
                        "countryCodes", java.util.List.of("KW"), "endpoint", "https://new-kw.openai.azure.com",
                        "apiKey", "plain-new-key", "deployment", "gpt-new")))));

        var reloaded = service.get(context("KW"));

        assertThat(reloaded.toString()).contains("New KW GPT", "https://new-kw.openai.azure.com");
        assertThat(reloaded.toString()).doesNotContain("Old KW GPT", "https://old-kw.openai.azure.com", "plain-new-key", "apiKeySecret");
    }

    @Test
    void shouldKeepAllCountryAiProviderVisibleAfterRestartWhenSavedFromCountrySession() {
        var stored = new AtomicReference<Map<String, Map<String, Object>>>(new java.util.LinkedHashMap<>());
        SettingsMetadataStore metadataStore = new SettingsMetadataStore() {
            @Override
            public Map<String, Object> load(TenantContext ctx) {
                var merged = new java.util.LinkedHashMap<String, Object>();
                var allScope = stored.get().get(scope(ctx, "ALL"));
                if (allScope != null) {
                    merged.putAll(allScope);
                }
                var countryScope = stored.get().get(scope(ctx, ctx.countryCode()));
                if (countryScope != null) {
                    merged.putAll(countryScope);
                }
                return merged;
            }

            @Override
            public void save(TenantContext ctx, Map<String, Object> settings) {
                var snapshot = new java.util.LinkedHashMap<>(stored.get());
                var azure = (Map<?, ?>) settings.get("azureOpenAI");
                var integrations = azure == null ? java.util.List.of() : (java.util.List<?>) azure.get("integrations");
                var scopes = new java.util.LinkedHashSet<String>();
                integrations.forEach(item -> {
                    if (item instanceof Map<?, ?> map) {
                        var countryCodes = map.get("countryCodes");
                        if (countryCodes instanceof Iterable<?> iterable) {
                            iterable.forEach(code -> scopes.add(String.valueOf(code)));
                        }
                    }
                });
                if (scopes.isEmpty()) {
                    scopes.add(ctx.countryCode());
                }
                scopes.forEach(scope -> snapshot.put(scope(ctx, scope), settings));
                stored.set(snapshot);
            }

            private String scope(TenantContext ctx, String countryCode) {
                return ctx.tenantId() + "|" + countryCode + "|" + ctx.environment();
            }
        };
        var env = environment().withProperty("kfh.security.master-key", "all-country-restart-master-key");
        var serviceBeforeRestart = service(new CommandCenterReadModel(), env, Optional.of(metadataStore));

        serviceBeforeRestart.update(context("KW"), Map.of("azureOpenAI", Map.of("integrations", java.util.List.of(
                Map.of("id", "aoai-all", "name", "All Country GPT", "provider", "AZURE_OPENAI", "purpose", "GPT",
                        "countryCodes", java.util.List.of("ALL"), "endpoint", "https://all.openai.azure.com",
                        "apiKey", "plain-all-key", "deployment", "gpt-all")))));

        var serviceAfterRestart = service(new CommandCenterReadModel(), env, Optional.of(metadataStore));
        var reloadedForKw = serviceAfterRestart.get(context("KW"));
        var reloadedForBh = serviceAfterRestart.get(context("BH"));

        assertThat(reloadedForKw.toString()).contains("All Country GPT", "https://all.openai.azure.com", "ALL");
        assertThat(reloadedForBh.toString()).contains("All Country GPT", "https://all.openai.azure.com", "ALL");
        assertThat(reloadedForBh.toString()).doesNotContain("plain-all-key", "apiKeySecret");
    }
}
