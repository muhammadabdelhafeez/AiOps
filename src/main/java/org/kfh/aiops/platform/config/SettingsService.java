package org.kfh.aiops.platform.config;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.kfh.aiops.ai.azureopenai.AzureOpenAiConnectionTester;
import org.kfh.aiops.commandcenter.support.CommandCenterReadModel;
import org.kfh.aiops.platform.audit.AuditService;
import org.kfh.aiops.platform.exception.ServiceUnavailableException;
import org.kfh.aiops.platform.security.SecretCipherService;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);
    private static final String MASKED_SECRET = "••••••••••••";
    private static final String DEFAULT_COUNTRY_SCOPE = "ALL";
    private static final List<String> METADATA_SETTING_KEYS = List.of(
            "azureOpenAI", "databases", "neo4j", "sharepoint", "teams", "infrastructure", "system");
    /**
     * System Variables that must never be persisted as runtime metadata because they are owned by
     * application.properties / deployment configuration and applied at JVM startup only.
     */
    private static final Set<String> STARTUP_ONLY_SYSTEM_KEYS = Set.of(
            "serverPort", "sslEnabled", "countriesEnabled",
            "secretMasterKeyConfigured", "secretMasterKeySource");
    private static final Map<String, String> SYSTEM_SECRET_FIELDS = Map.of(
            "redisPassword", "redisPasswordSecret",
            "kafkaPassword", "kafkaPasswordSecret");
    private static final Set<String> ENCRYPTED_SECRET_KEYS = Set.of(
            "apiKeySecret", "passwordSecret", "secretSecret", "webhookUrlSecret",
            "redisPasswordSecret", "kafkaPasswordSecret");

    private final Map<String, Map<String, Object>> runtimeOverrides = new ConcurrentHashMap<>();
    private final AuditService auditService;
    private final CommandCenterReadModel readModel;
    private final Environment environment;
    private final AzureOpenAiConnectionTester azureOpenAiConnectionTester;
    private final Neo4jConnectionTester neo4jConnectionTester;
    private final InfrastructureConnectionTester infrastructureConnectionTester;
    private final SecretCipherService secretCipherService;
    private final Optional<SettingsMetadataStore> metadataStore;

    public SettingsService(AuditService auditService, CommandCenterReadModel readModel, Environment environment,
            AzureOpenAiConnectionTester azureOpenAiConnectionTester, Neo4jConnectionTester neo4jConnectionTester,
            InfrastructureConnectionTester infrastructureConnectionTester, SecretCipherService secretCipherService,
            Optional<SettingsMetadataStore> metadataStore) {
        this.auditService = auditService;
        this.readModel = readModel;
        this.environment = environment;
        this.azureOpenAiConnectionTester = azureOpenAiConnectionTester;
        this.neo4jConnectionTester = neo4jConnectionTester;
        this.infrastructureConnectionTester = infrastructureConnectionTester;
        this.secretCipherService = secretCipherService;
        this.metadataStore = metadataStore;
    }

    public Map<String, Object> get(TenantContext ctx) {
        ctx.requirePermission("SETTINGS_READ");
        return settingsSnapshot(ctx);
    }

    public Map<String, Object> update(TenantContext ctx, Map<String, Object> request) {
        ctx.requirePermission("SETTINGS_WRITE");
        var existingSettings = privateSettings(ctx);
        var sanitized = sanitizeSettings(ctx, request, existingSettings);
        var metadata = metadataSettings(sanitized);
        mergeInto(scopedRuntimeOverrides(ctx), sanitized);
        persistMetadataSettings(ctx, metadata);
        auditService.recordWrite(ctx, "SETTINGS_UPDATED", "Settings", ctx.tenantId().toString(), null,
                Map.of("updatedKeys", safeKeys(request)));
        readModel.appendAudit(ctx, "SETTINGS_UPDATED", "Settings", ctx.tenantId().toString(), Map.of(
                "message", "Application settings updated",
                "updatedKeys", safeKeys(request),
                "details", Map.of("updatedKeys", safeKeys(request))));
        return settingsSnapshot(ctx);
    }

    public Map<String, Object> test(TenantContext ctx, String section, Map<String, Object> request) {
        ctx.requirePermission("SETTINGS_WRITE");
        auditService.recordWrite(ctx, "SETTINGS_TEST_REQUESTED", "Settings", section, null,
                Map.of("section", section, "submittedKeys", safeKeys(request)));
        readModel.appendAudit(ctx, "SETTINGS_TEST_REQUESTED", "Settings", section, Map.of(
                "message", "Settings test requested for " + section,
                "details", Map.of("section", section, "submittedKeys", safeKeys(request))));
        var normalizedSection = safe(section);
        if (normalizedSection.startsWith("azureOpenAI")) {
            try {
                return azureOpenAiConnectionTester.test(ctx, azureTestRequest(ctx, request));
            } catch (ServiceUnavailableException ex) {
                return failedTestResult(ctx, normalizedSection,
                        "Saved AI provider secret is unavailable. Re-enter the API key and retry Test & Save.");
            } catch (RuntimeException ex) {
                return failedTestResult(ctx, normalizedSection, "Azure OpenAI settings test failed: " + safeExceptionMessage(ex));
            }
        }
        if (isNeo4jTest(normalizedSection, request)) {
            return neo4jConnectionTester.test(ctx, normalizedSection, neo4jTestRequest(ctx, normalizedSection, request));
        }
        if (isInfrastructureTest(normalizedSection, request)) {
            return infrastructureConnectionTester.test(ctx, normalizedSection, infrastructureTestRequest(ctx, request));
        }
        return unsupportedTestResult(ctx, normalizedSection);
    }

    private static boolean isNeo4jTest(String section, Map<String, Object> request) {
        var lowerSection = section.toLowerCase(Locale.ROOT);
        var type = safe(request == null ? null : request.get("type")).toUpperCase(Locale.ROOT);
        return lowerSection.equals("neo4j") || lowerSection.startsWith("databases.connections") && "NEO4J".equals(type);
    }

    private static boolean isInfrastructureTest(String section, Map<String, Object> request) {
        var lowerSection = section.toLowerCase(Locale.ROOT);
        var type = safe(request == null ? null : request.get("type")).toUpperCase(Locale.ROOT);
        return lowerSection.startsWith("infrastructure.connections")
                && ("REDIS".equals(type) || "KAFKA".equals(type) || "INDEX_STORAGE".equals(type));
    }

    private static Map<String, Object> unsupportedTestResult(TenantContext ctx, String section) {
        var normalized = section.isBlank() ? "settings" : section;
        var lower = normalized.toLowerCase(Locale.ROOT);
        var notificationTest = lower.contains("teams") || lower.contains("notification");
        var message = notificationTest
                ? "Live notification test is not implemented for section '" + normalized + "'. No Teams webhook call was sent."
                : "Live settings test is not implemented for section '" + normalized + "'.";
        return failedTestResult(ctx, normalized, message);
    }

    private static Map<String, Object> failedTestResult(TenantContext ctx, String section, String message) {
        return mapOf(
                "section", section,
                "status", "Fail",
                "pass", false,
                "latencyMs", 0,
                "message", message,
                "testedAt", Instant.now().toString(),
                "correlationId", ctx.correlationId());
    }

    private static String safeExceptionMessage(RuntimeException ex) {
        var message = Objects.toString(ex.getMessage(), ex.getClass().getSimpleName())
                .replaceAll("(?i)(\"?(?:password|authorization|token|secret|credential|api[_-]?key|apikey|username|webhookUrl|webhook_url|webhook-url)\"?\\s*[:=]\\s*\"?)[^\",;\\s}]+", "$1masked")
                .replaceAll("(?i)(bearer|basic)\\s+[^,;\\s]+", "$1 masked")
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim();
        if (message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() <= 300 ? message : message.substring(0, 297) + "...";
    }

    private Map<String, Object> settingsSnapshot(TenantContext ctx) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("azureOpenAI", azureOpenAi());
        snapshot.put("databases", mapOf("connections", List.of()));
        snapshot.put("neo4j", neo4j());
        snapshot.put("postgresql", postgresql());
        snapshot.put("sharepoint", sharepoint());
        snapshot.put("teams", teams());
        snapshot.put("infrastructure", mapOf("connections", List.of()));
        snapshot.put("system", systemSettings());
        snapshot.put("configurationOwnership", configurationOwnership());
        mergeInto(snapshot, loadMetadataSettings(ctx));
        mergeInto(snapshot, runtimeOverridesFor(ctx));
        return publicSettingsSnapshot(filterCountryScopedSettings(snapshot, ctx));
    }

    private Map<String, Object> privateSettings(TenantContext ctx) {
        var settings = new LinkedHashMap<>(loadMetadataSettings(ctx));
        mergeInto(settings, runtimeOverridesFor(ctx));
        return settings;
    }

    private Map<String, Object> loadMetadataSettings(TenantContext ctx) {
        if (metadataStore.isEmpty()) {
            return Map.of();
        }
        try {
            return metadataStore.get().load(ctx);
        } catch (RuntimeException ex) {
            log.warn("settings metadata load unavailable tenantId={} countryCode={} environment={} correlationId={} errorType={}",
                    ctx.tenantId(), ctx.countryCode(), ctx.environment(), ctx.correlationId(), ex.getClass().getSimpleName());
            return Map.of();
        }
    }

    private void persistMetadataSettings(TenantContext ctx, Map<String, Object> metadata) {
        if (metadata.isEmpty()) {
            return;
        }
        if (metadataStore.isEmpty()) {
            log.warn("settings metadata save skipped: no SettingsMetadataStore bean available "
                    + "tenantId={} countryCode={} environment={} correlationId={} keys={}",
                    ctx.tenantId(), ctx.countryCode(), ctx.environment(), ctx.correlationId(), metadata.keySet());
            throw new ServiceUnavailableException("SETTINGS_PERSISTENCE_UNAVAILABLE",
                    "Settings cannot be persisted because metadata storage is not wired. Configure the primary "
                            + "PostgreSQL datasource so config.integration_settings is available, then retry.");
        }
        try {
            metadataStore.get().save(ctx, metadata);
        } catch (RuntimeException ex) {
            log.warn("settings metadata save failed tenantId={} countryCode={} environment={} correlationId={} "
                    + "errorType={} keys={}",
                    ctx.tenantId(), ctx.countryCode(), ctx.environment(), ctx.correlationId(),
                    ex.getClass().getSimpleName(), metadata.keySet());
            throw new ServiceUnavailableException("SETTINGS_PERSISTENCE_FAILED",
                    "Settings could not be saved permanently. Verify the primary PostgreSQL datasource is reachable "
                            + "and config.integration_settings is migrated, then retry.");
        }
    }

    private static Map<String, Object> metadataSettings(Map<String, Object> sanitized) {
        var metadata = new LinkedHashMap<String, Object>();
        METADATA_SETTING_KEYS.forEach(key -> {
            if (!sanitized.containsKey(key)) {
                return;
            }
            if ("system".equals(key)) {
                var system = persistableSystem(sanitized.get(key));
                if (!system.isEmpty()) {
                    metadata.put(key, system);
                }
                return;
            }
            metadata.put(key, sanitized.get(key));
        });
        return metadata;
    }

    private static Map<String, Object> persistableSystem(Object value) {
        if (!(value instanceof Map<?, ?> system)) {
            return Map.of();
        }
        var filtered = new LinkedHashMap<String, Object>();
        system.forEach((rawKey, nestedValue) -> {
            var key = String.valueOf(rawKey);
            if (STARTUP_ONLY_SYSTEM_KEYS.contains(key)) {
                return;
            }
            filtered.put(key, nestedValue);
        });
        return filtered;
    }

    private Map<String, Object> azureOpenAi() {
        var embeddings = azureModel("kfh.ai.azure-openai.embeddings");
        var gpt = azureModel("kfh.ai.azure-openai.gpt");
        return mapOf(
                "integrations", azureOpenAiIntegrations(embeddings, gpt),
                "embeddings", embeddings,
                "gpt", gpt);
    }

    private Map<String, Object> azureModel(String prefix) {
        return mapOf(
                "roundRobin", bool(prefix + ".round-robin", false),
                "endpoint", prop(prefix + ".endpoint"),
                "apiKey", maskedProperty(prefix + ".api-key"),
                "deploymentA", prop(prefix + ".deployment-a"),
                "deploymentB", prop(prefix + ".deployment-b"),
                "circuitBreakerA", prop(prefix + ".circuit-breaker-a", "unknown"),
                "circuitBreakerB", prop(prefix + ".circuit-breaker-b", "unknown"),
                "lastTest", null);
    }

    private List<Map<String, Object>> azureOpenAiIntegrations(Map<String, Object> embeddings, Map<String, Object> gpt) {
        var integrations = configuredAzureOpenAiIntegrations();
        if (configuredModel(embeddings)) {
            integrations.add(legacyAzureIntegration("azure-openai-embeddings", "Azure OpenAI Embeddings", "EMBEDDINGS", embeddings));
        }
        if (configuredModel(gpt)) {
            integrations.add(legacyAzureIntegration("azure-openai-gpt", "Azure OpenAI GPT", "GPT", gpt));
        }
        return integrations;
    }

    private List<Map<String, Object>> configuredAzureOpenAiIntegrations() {
        var integrations = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 10; i++) {
            var prefix = "kfh.ai.azure-openai.integrations[" + i + "]";
            var name = prop(prefix + ".name");
            var endpoint = prop(prefix + ".endpoint");
            var deployment = prop(prefix + ".deployment");
            if (!name.isBlank() || !endpoint.isBlank() || !deployment.isBlank()) {
                var countryCodes = normalizeCountryScopes(prop(prefix + ".country-code", DEFAULT_COUNTRY_SCOPE));
                integrations.add(mapOf(
                        "id", "azure-openai-configured-" + (i + 1),
                        "name", name.isBlank() ? "Azure OpenAI " + (i + 1) : name,
                        "provider", prop(prefix + ".provider", "AZURE_OPENAI"),
                        "purpose", prop(prefix + ".purpose", "GPT"),
                        "endpoint", endpoint,
                        "apiKey", maskedProperty(prefix + ".api-key"),
                        "deployment", deployment,
                        "modelName", prop(prefix + ".model-name", deployment),
                        "apiVersion", prop(prefix + ".api-version", prop("kfh.ai.azure-openai.default-api-version", "2024-02-15-preview")),
                        "authMode", prop(prefix + ".auth-mode", "API_KEY"),
                        "apiStyle", prop(prefix + ".api-style", "DEPLOYMENTS"),
                        "entraScope", prop(prefix + ".entra-scope", "https://ai.azure.com/.default"),
                        "countryCode", primaryCountryScope(countryCodes),
                        "countryCodes", countryCodes,
                        "timeoutSeconds", integer(prefix + ".timeout-seconds", 5),
                        "enabled", bool(prefix + ".enabled", true),
                        "lastTest", null));
            }
        }
        return integrations;
    }

    private Map<String, Object> legacyAzureIntegration(String id, String name, String purpose, Map<String, Object> model) {
        return mapOf(
                "id", id,
                "name", name,
                "provider", "AZURE_OPENAI",
                "purpose", purpose,
                "endpoint", safe(model.get("endpoint")),
                "apiKey", safe(model.get("apiKey")),
                "deployment", firstNonBlank(model.get("deploymentA"), model.get("deploymentB")),
                "modelName", firstNonBlank(model.get("deploymentA"), model.get("deploymentB")),
                "apiVersion", prop("kfh.ai.azure-openai.default-api-version", "2024-02-15-preview"),
                "authMode", "API_KEY",
                "apiStyle", "DEPLOYMENTS",
                "entraScope", "https://ai.azure.com/.default",
                "countryCode", DEFAULT_COUNTRY_SCOPE,
                "countryCodes", List.of(DEFAULT_COUNTRY_SCOPE),
                "timeoutSeconds", 5,
                "enabled", true,
                "roundRobin", model.get("roundRobin"),
                "lastTest", null);
    }

    private static boolean configuredModel(Map<String, Object> model) {
        return !firstNonBlank(model.get("endpoint"), model.get("deploymentA"), model.get("deploymentB"), model.get("apiKey")).isBlank();
    }

    private static String firstNonBlank(Object... values) {
        for (var value : values) {
            var text = safe(value);
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private static String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Map<String, Object> neo4j() {
        return mapOf(
                "boltUrl", prop("spring.neo4j.uri"),
                "user", prop("spring.neo4j.authentication.username"),
                "password", maskedProperty("spring.neo4j.authentication.password"),
                "database", prop("spring.data.neo4j.database"),
                "healthIndicatorEnabled", bool("management.health.neo4j.enabled", false),
                "countryCode", DEFAULT_COUNTRY_SCOPE,
                "countryCodes", List.of(DEFAULT_COUNTRY_SCOPE),
                "lastTest", null);
    }

    private Map<String, Object> postgresql() {
        return mapOf(
                "jdbcUrl", prop("spring.datasource.url"),
                "user", prop("spring.datasource.username"),
                "password", maskedProperty("spring.datasource.password"),
                "driver", prop("spring.datasource.driver-class-name"),
                "flywayEnabled", bool("spring.flyway.enabled", true),
                "lastTest", null);
    }

    private Map<String, Object> sharepoint() {
        return mapOf(
                "tenant", prop("kfh.integrations.sharepoint.tenant"),
                "site", prop("kfh.integrations.sharepoint.site"),
                "clientId", prop("kfh.integrations.sharepoint.client-id"),
                "clientSecret", maskedProperty("kfh.integrations.sharepoint.client-secret"),
                "lastTest", null);
    }

    private Map<String, Object> teams() {
        return mapOf("mappings", teamsMappings());
    }

    private List<Map<String, Object>> teamsMappings() {
        var mappings = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 10; i++) {
            var prefix = "kfh.notifications.teams.mappings[" + i + "]";
            var domain = prop(prefix + ".domain");
            var team = prop(prefix + ".team");
            var channel = prop(prefix + ".channel-name");
            var webhook = prop(prefix + ".webhook-url");
            if (!domain.isBlank() || !team.isBlank() || !channel.isBlank() || !webhook.isBlank()) {
                mappings.add(mapOf(
                        "id", i + 1,
                        "domain", domain,
                        "team", team,
                        "channelName", channel,
                        "webhookUrl", webhook.isBlank() ? "" : MASKED_SECRET,
                        "enabled", bool(prefix + ".enabled", false)));
            }
        }
        return mappings;
    }

    private Map<String, Object> systemSettings() {
        return mapOf(
                "dashboardRefreshSeconds", integer("kfh.ui.dashboard-refresh-seconds", 30),
                "quietPeriodMinutes", integer("kfh.incident.quiet-period-minutes", 15),
                "aiMode", prop("kfh.ai.mode", "EVIDENCE_PACK_ONLY"),
                "countriesEnabled", prop("kfh.countries.enabled"),
                "redisHost", prop("spring.data.redis.host"),
                "redisPort", integer("spring.data.redis.port", 6379),
                "redisPassword", maskedProperty("spring.data.redis.password"),
                "redisHealthIndicatorEnabled", bool("management.health.redis.enabled", false),
                "kafkaBootstrapServers", prop("kfh.streaming.kafka.bootstrap-servers"),
                "kafkaSecurityProtocol", prop("kfh.streaming.kafka.security-protocol", "SSL"),
                "kafkaUsername", prop("kfh.streaming.kafka.username"),
                "kafkaPassword", maskedProperty("kfh.streaming.kafka.password"),
                "indexStorageProvider", prop("kfh.index.storage.provider", "FILESYSTEM"),
                "indexStoragePath", prop("kfh.index.storage.path", "/data/aiops-index"),
                "indexStorageBucket", prop("kfh.index.storage.bucket"),
                "serverPort", integer("server.port", 8443),
                "sslEnabled", bool("server.ssl.enabled", true),
                "secretMasterKeyConfigured", SecretCipherService.isMasterKeyConfigured(environment),
                "secretMasterKeySource", SecretCipherService.masterKeySource(environment));
    }

    private Map<String, Object> configurationOwnership() {
        return mapOf(
                "startupRequiredApplicationProperties", List.of(
                        "spring.datasource.url",
                        "spring.datasource.username",
                        "spring.datasource.password",
                        "spring.datasource.driver-class-name",
                        "spring.flyway.enabled",
                        "server.port",
                        "server.ssl.*",
                        "kfh.identity.bootstrap.*",
                        "kfh.security.master-key",
                        "kfh.security.master-key-file",
                        "kfh.countries.enabled"),
                "databaseBackedMetadata", List.of(
                        "azureOpenAI.integrations[]",
                        "databases.connections[] (optional monitored/additional databases, not the primary PostgreSQL datasource)",
                        "neo4j",
                        "sharepoint.connections[]",
                        "teams.mappings[]",
                        "infrastructure.connections[]",
                        "Redis/Kafka/index storage connector metadata"),
                "startupPolicy", "Application startup depends on the primary PostgreSQL datasource from application properties; that PostgreSQL database stores Settings metadata in config.integration_settings. Optional tools remain unconfigured until completed from Settings.",
                "metadataStore", "config.integration_settings");
    }

    private Map<String, Object> sanitizeSettings(TenantContext ctx, Map<String, Object> request, Map<String, Object> existingSettings) {
        var sanitized = new LinkedHashMap<String, Object>();
        if (request == null || request.isEmpty()) {
            return sanitized;
        }
        request.forEach((key, value) -> sanitized.put(key, sanitizeValue(ctx, key, value,
                existingSettings == null ? null : existingSettings.get(key))));
        return sanitized;
    }

    private Map<String, Object> scopedRuntimeOverrides(TenantContext ctx) {
        return runtimeOverrides.computeIfAbsent(scopeKey(ctx, ctx.countryCode()), ignored -> new ConcurrentHashMap<>());
    }

    private Map<String, Object> runtimeOverridesFor(TenantContext ctx) {
        var scoped = new LinkedHashMap<String, Object>();
        mergeInto(scoped, runtimeOverrides.getOrDefault(scopeKey(ctx, DEFAULT_COUNTRY_SCOPE), Map.of()));
        if (!DEFAULT_COUNTRY_SCOPE.equalsIgnoreCase(ctx.countryCode())) {
            mergeInto(scoped, runtimeOverrides.getOrDefault(scopeKey(ctx, ctx.countryCode()), Map.of()));
        }
        return scoped;
    }

    private static String scopeKey(TenantContext ctx, String countryCode) {
        return ctx.tenantId() + "|" + normalizeCountryScope(countryCode) + "|" + normalizeEnvironment(ctx.environment());
    }

    @SuppressWarnings("unchecked")
    private static void mergeInto(Map<String, Object> target, Map<String, Object> source) {
        source.forEach((key, value) -> {
            var existing = target.get(key);
            if (existing instanceof Map<?, ?> existingMap && value instanceof Map<?, ?> valueMap) {
                mergeInto((Map<String, Object>) existingMap, (Map<String, Object>) valueMap);
            } else {
                target.put(key, value);
            }
        });
    }

    private Object sanitizeValue(TenantContext ctx, String key, Object value, Object existingValue) {
        if ("azureOpenAI".equals(key) && value instanceof Map<?, ?> azure) {
            return sanitizeAzureOpenAi(azure, asMap(existingValue));
        }
        if ("neo4j".equals(key) && value instanceof Map<?, ?> neo4j) {
            return sanitizeNeo4j(ctx, neo4j, asMap(existingValue));
        }
        if ("teams".equals(key) && value instanceof Map<?, ?> teams) {
            return sanitizeTeams(teams, asMap(existingValue));
        }
        if ("system".equals(key) && value instanceof Map<?, ?> system) {
            return sanitizeSystem(system, asMap(existingValue));
        }
        if (("databases".equals(key) || "sharepoint".equals(key) || "infrastructure".equals(key))
                && value instanceof Map<?, ?> connectorSection) {
            return sanitizeConnectorSection(ctx, connectorSection, asMap(existingValue));
        }
        if (isSecretLike(key)) {
            return secretReplacement(value);
        }
        if (value instanceof Map<?, ?> map) {
            var sanitized = new LinkedHashMap<String, Object>();
            var existingMap = asMap(existingValue);
            map.forEach((nestedKey, nestedValue) -> sanitized.put(String.valueOf(nestedKey), sanitizeValue(ctx, String.valueOf(nestedKey),
                    nestedValue, existingMap.get(String.valueOf(nestedKey)))));
            return sanitized;
        }
        if (value instanceof Iterable<?> iterable) {
            var sanitized = new ArrayList<>();
            iterable.forEach(item -> sanitized.add(sanitizeValue(ctx, key, item, null)));
            return sanitized;
        }
        return value;
    }

    private Map<String, Object> sanitizeAzureOpenAi(Map<?, ?> azure, Map<String, Object> existingAzure) {
        var sanitized = new LinkedHashMap<String, Object>();
        azure.forEach((rawKey, value) -> {
            var key = String.valueOf(rawKey);
            if ("integrations".equals(key) && value instanceof Iterable<?> integrations) {
                sanitized.put(key, sanitizeAzureIntegrations(integrations, existingAzureIntegrations(existingAzure)));
            } else {
                sanitized.put(key, sanitizeValue(null, key, value, existingAzure.get(key)));
            }
        });
        return sanitized;
    }

    private Map<String, Object> sanitizeNeo4j(TenantContext ctx, Map<?, ?> neo4j, Map<String, Object> existingNeo4j) {
        var sanitized = new LinkedHashMap<String, Object>();
        neo4j.forEach((rawKey, value) -> {
            var key = String.valueOf(rawKey);
            if (!"password".equals(key) && !"passwordSecret".equals(key)
                    && !"countryCode".equals(key) && !"countryCodes".equals(key)) {
                sanitized.put(key, sanitizeValue(null, key, value, existingNeo4j.get(key)));
            }
        });
        // Country scope: Neo4j now persists per (tenant, country, environment) just like
        // azureOpenAI integrations and databases.connections. The selected country here drives
        // both response filtering and JdbcSettingsMetadataStore persistence scope.
        var countryCodes = normalizeCountryScopes(countryScopeValue(neo4j, ctx));
        sanitized.put("countryCode", primaryCountryScope(countryCodes));
        sanitized.put("countryCodes", countryCodes);
        var password = safe(neo4j.get("password"));
        sanitized.put("password", MASKED_SECRET.equals(password) ? MASKED_SECRET : secretReplacement(password));
        if (!password.isBlank() && !MASKED_SECRET.equals(password)) {
            sanitized.put("passwordSecret", secretCipherService.encrypt(password));
        } else if (MASKED_SECRET.equals(password) && existingNeo4j.containsKey("passwordSecret")) {
            sanitized.put("passwordSecret", existingNeo4j.get("passwordSecret"));
        }
        return sanitized;
    }

    private Map<String, Object> sanitizeTeams(Map<?, ?> teams, Map<String, Object> existingTeams) {
        var sanitized = new LinkedHashMap<String, Object>();
        teams.forEach((rawKey, value) -> {
            var key = String.valueOf(rawKey);
            if ("mappings".equals(key) && value instanceof Iterable<?> iterable) {
                sanitized.put(key, sanitizeTeamsMappings(iterable, existingTeamsMappings(existingTeams)));
            } else {
                sanitized.put(key, sanitizeValue(null, key, value, existingTeams.get(key)));
            }
        });
        return sanitized;
    }

    private List<Object> sanitizeTeamsMappings(Iterable<?> mappings, Map<String, Map<String, Object>> existingById) {
        var sanitized = new ArrayList<>();
        mappings.forEach(item -> {
            if (item instanceof Map<?, ?> map) {
                sanitized.add(sanitizeTeamsMapping(map, existingById.get(safe(map.get("id")))));
            } else {
                sanitized.add(item);
            }
        });
        return sanitized;
    }

    private Map<String, Object> sanitizeTeamsMapping(Map<?, ?> item, Map<String, Object> existing) {
        var sanitized = new LinkedHashMap<String, Object>();
        item.forEach((rawKey, value) -> {
            var key = String.valueOf(rawKey);
            if (!"webhookUrl".equals(key) && !"webhookUrlSecret".equals(key)) {
                sanitized.put(key, sanitizeValue(null, key, value, existing == null ? null : existing.get(key)));
            }
        });
        var webhook = safe(item.get("webhookUrl"));
        sanitized.put("webhookUrl", MASKED_SECRET.equals(webhook) ? MASKED_SECRET : secretReplacement(webhook));
        if (!webhook.isBlank() && !MASKED_SECRET.equals(webhook)) {
            sanitized.put("webhookUrlSecret", secretCipherService.encrypt(webhook));
        } else if (existing != null && MASKED_SECRET.equals(webhook) && existing.containsKey("webhookUrlSecret")) {
            sanitized.put("webhookUrlSecret", existing.get("webhookUrlSecret"));
        }
        return sanitized;
    }

    private static Map<String, Map<String, Object>> existingTeamsMappings(Map<String, Object> existingTeams) {
        var indexed = new LinkedHashMap<String, Map<String, Object>>();
        var mappings = existingTeams.get("mappings");
        if (mappings instanceof Iterable<?> iterable) {
            iterable.forEach(item -> {
                if (item instanceof Map<?, ?> map) {
                    indexed.put(safe(map.get("id")), asMap(map));
                }
            });
        }
        return indexed;
    }

    private Map<String, Object> sanitizeSystem(Map<?, ?> system, Map<String, Object> existingSystem) {
        var sanitized = new LinkedHashMap<String, Object>();
        system.forEach((rawKey, value) -> {
            var key = String.valueOf(rawKey);
            if (SYSTEM_SECRET_FIELDS.containsKey(key) || SYSTEM_SECRET_FIELDS.containsValue(key)) {
                return;
            }
            sanitized.put(key, sanitizeValue(null, key, value, existingSystem.get(key)));
        });
        SYSTEM_SECRET_FIELDS.forEach((plainKey, secretKey) -> {
            if (!system.containsKey(plainKey)) {
                if (existingSystem.containsKey(plainKey)) {
                    sanitized.put(plainKey, existingSystem.get(plainKey));
                }
                if (existingSystem.containsKey(secretKey)) {
                    sanitized.put(secretKey, existingSystem.get(secretKey));
                }
                return;
            }
            var submitted = safe(system.get(plainKey));
            sanitized.put(plainKey, MASKED_SECRET.equals(submitted) ? MASKED_SECRET : secretReplacement(submitted));
            if (!submitted.isBlank() && !MASKED_SECRET.equals(submitted)) {
                sanitized.put(secretKey, secretCipherService.encrypt(submitted));
            } else if (MASKED_SECRET.equals(submitted) && existingSystem.containsKey(secretKey)) {
                sanitized.put(secretKey, existingSystem.get(secretKey));
            }
        });
        return sanitized;
    }

    private List<Object> sanitizeAzureIntegrations(Iterable<?> integrations, Map<String, Map<String, Object>> existingById) {
        var sanitized = new ArrayList<>();
        integrations.forEach(item -> {
            if (item instanceof Map<?, ?> map) {
                sanitized.add(sanitizeAzureIntegration(map, existingById.get(safe(map.get("id")))));
            } else {
                sanitized.add(item);
            }
        });
        return sanitized;
    }

    private Map<String, Object> sanitizeAzureIntegration(Map<?, ?> item, Map<String, Object> existing) {
        var sanitized = new LinkedHashMap<String, Object>();
        item.forEach((rawKey, value) -> {
            var key = String.valueOf(rawKey);
            if (!"apiKey".equals(key) && !"apiKeySecret".equals(key)) {
                sanitized.put(key, sanitizeValue(null, key, value, existing == null ? null : existing.get(key)));
            }
        });
        var countryCodes = normalizeCountryScopes(item.containsKey("countryCodes") ? item.get("countryCodes") : item.get("countryCode"));
        sanitized.put("countryCode", primaryCountryScope(countryCodes));
        sanitized.put("countryCodes", countryCodes);
        sanitized.put("apiKey", MASKED_SECRET.equals(safe(item.get("apiKey"))) ? MASKED_SECRET : secretReplacement(item.get("apiKey")));
        var apiKeySecret = encryptedSubmittedApiKey(item.get("apiKey"));
        if (apiKeySecret.isPresent()) {
            sanitized.put("apiKeySecret", apiKeySecret.get());
        } else if (existing != null && MASKED_SECRET.equals(safe(item.get("apiKey"))) && existing.containsKey("apiKeySecret")) {
            sanitized.put("apiKeySecret", existing.get("apiKeySecret"));
        }
        return sanitized;
    }

    private Optional<Map<String, Object>> encryptedSubmittedApiKey(Object value) {
        var apiKey = safe(value);
        if (apiKey.isBlank() || MASKED_SECRET.equals(apiKey)) {
            return Optional.empty();
        }
        return Optional.of(secretCipherService.encrypt(apiKey));
    }

    private Map<String, Object> sanitizeConnectorSection(TenantContext ctx, Map<?, ?> section,
            Map<String, Object> existingSection) {
        var sanitized = new LinkedHashMap<String, Object>();
        section.forEach((rawKey, value) -> {
            var key = String.valueOf(rawKey);
            if ("connections".equals(key) && value instanceof Iterable<?> connections) {
                sanitized.put(key, sanitizeConnectorConnections(ctx, connections, existingConnectorConnections(existingSection)));
            } else {
                sanitized.put(key, sanitizeValue(ctx, key, value, existingSection.get(key)));
            }
        });
        return sanitized;
    }

    private List<Object> sanitizeConnectorConnections(TenantContext ctx, Iterable<?> connections,
            Map<String, Map<String, Object>> existingById) {
        var sanitized = new ArrayList<>();
        connections.forEach(item -> {
            if (item instanceof Map<?, ?> map) {
                sanitized.add(sanitizeConnectorConnection(ctx, map, existingById.get(safe(map.get("id")))));
            } else {
                sanitized.add(item);
            }
        });
        return sanitized;
    }

    private Map<String, Object> sanitizeConnectorConnection(TenantContext ctx, Map<?, ?> item, Map<String, Object> existing) {
        var sanitized = new LinkedHashMap<String, Object>();
        item.forEach((rawKey, value) -> {
            var key = String.valueOf(rawKey);
            if (!"secret".equals(key) && !"secretSecret".equals(key)) {
                sanitized.put(key, sanitizeValue(ctx, key, value, existing == null ? null : existing.get(key)));
            }
        });
        var countryCodes = normalizeCountryScopes(countryScopeValue(item, ctx));
        sanitized.put("countryCode", primaryCountryScope(countryCodes));
        sanitized.put("countryCodes", countryCodes);
        var secret = safe(item.get("secret"));
        sanitized.put("secret", MASKED_SECRET.equals(secret) ? MASKED_SECRET : secretReplacement(secret));
        if (!secret.isBlank() && !MASKED_SECRET.equals(secret)) {
            sanitized.put("secretSecret", secretCipherService.encrypt(secret));
        } else if (existing != null && MASKED_SECRET.equals(secret) && existing.containsKey("secretSecret")) {
            sanitized.put("secretSecret", existing.get("secretSecret"));
        }
        return sanitized;
    }

    private static Object countryScopeValue(Map<?, ?> item, TenantContext ctx) {
        var countryCodes = item.get("countryCodes");
        if (!countryValueBlank(countryCodes)) {
            return countryCodes;
        }
        var countryCode = item.get("countryCode");
        return countryValueBlank(countryCode) ? (ctx == null ? DEFAULT_COUNTRY_SCOPE : ctx.countryCode()) : countryCode;
    }

    private static boolean countryValueBlank(Object value) {
        if (value instanceof Iterable<?> iterable) {
            for (var item : iterable) {
                if (!safe(item).isBlank()) {
                    return false;
                }
            }
            return true;
        }
        return safe(value).isBlank();
    }

    private static Map<String, Map<String, Object>> existingConnectorConnections(Map<String, Object> section) {
        var indexed = new LinkedHashMap<String, Map<String, Object>>();
        var connections = section.get("connections");
        if (connections instanceof Iterable<?> iterable) {
            iterable.forEach(item -> {
                if (item instanceof Map<?, ?> map) {
                    indexed.put(safe(map.get("id")), asMap(map));
                }
            });
        }
        return indexed;
    }

    private Map<String, Object> azureTestRequest(TenantContext ctx, Map<String, Object> request) {
        var copy = new LinkedHashMap<>(request == null ? Map.of() : request);
        var countryCodes = normalizeCountryScopes(copy.containsKey("countryCodes") ? copy.get("countryCodes") : firstNonBlank(copy.get("countryCode"), ctx.countryCode()));
        copy.put("countryCode", primaryCountryScope(countryCodes));
        copy.put("countryCodes", countryCodes);
        if (!MASKED_SECRET.equals(safe(copy.get("apiKey")))) {
            return copy;
        }
        var savedSecret = savedAzureApiKey(ctx, copy);
        if (!savedSecret.isBlank()) {
            copy.put("apiKey", savedSecret);
        }
        return copy;
    }

    private Map<String, Object> neo4jTestRequest(TenantContext ctx, String section, Map<String, Object> request) {
        var copy = new LinkedHashMap<>(request == null ? Map.of() : request);
        if ("neo4j".equalsIgnoreCase(section)) {
            copy.putIfAbsent("boltUrl", prop("spring.neo4j.uri"));
            copy.putIfAbsent("user", prop("spring.neo4j.authentication.username"));
            copy.putIfAbsent("database", prop("spring.data.neo4j.database", "neo4j"));
            if (MASKED_SECRET.equals(safe(copy.get("password")))) {
                copy.put("password", savedNeo4jPassword(ctx).orElseGet(() -> prop("spring.neo4j.authentication.password")));
            }
            var countryCodes = normalizeCountryScopes(countryScopeValue(copy, ctx));
            copy.put("countryCode", primaryCountryScope(countryCodes));
            copy.put("countryCodes", countryCodes);
        }
        return copy;
    }

    private Map<String, Object> infrastructureTestRequest(TenantContext ctx, Map<String, Object> request) {
        var copy = new LinkedHashMap<>(request == null ? Map.of() : request);
        var countryCodes = normalizeCountryScopes(countryScopeValue(copy, ctx));
        copy.put("countryCode", primaryCountryScope(countryCodes));
        copy.put("countryCodes", countryCodes);
        if (MASKED_SECRET.equals(safe(copy.get("secret")))) {
            savedInfrastructureSecret(ctx, copy).ifPresent(secret -> copy.put("secret", secret));
        }
        return copy;
    }

    private Optional<String> savedNeo4jPassword(TenantContext ctx) {
        var secret = asMap(asMap(privateSettings(ctx).get("neo4j")).get("passwordSecret"));
        return secret.isEmpty() ? Optional.empty() : Optional.of(secretCipherService.decrypt(secret));
    }

    private String savedAzureApiKey(TenantContext ctx, Map<String, Object> request) {
        var azure = asMap(privateSettings(ctx).get("azureOpenAI"));
        var integration = existingAzureIntegrations(azure).get(safe(request.get("id")));
        if (integration == null) {
            return "";
        }
        var secret = asMap(integration.get("apiKeySecret"));
        return secret.isEmpty() ? "" : secretCipherService.decrypt(secret);
    }

    private Optional<String> savedInfrastructureSecret(TenantContext ctx, Map<String, Object> request) {
        var section = asMap(privateSettings(ctx).get("infrastructure"));
        var connector = existingConnectorConnections(section).get(safe(request.get("id")));
        if (connector == null) {
            return Optional.empty();
        }
        var secret = asMap(connector.get("secretSecret"));
        return secret.isEmpty() ? Optional.empty() : Optional.of(secretCipherService.decrypt(secret));
    }

    /**
     * Resolves the active Redis server for this tenant/country/environment from
     * {@code infrastructure.connections} and returns a decrypted, secret-bearing view for
     * server-side runtime use only (never returned by an API). The first enabled REDIS connection
     * in scope wins. Returned keys: {@code name, host, port, username, password (plaintext),
     * tlsEnabled, healthIndicatorEnabled}.
     */
    public Optional<Map<String, Object>> resolveRedisConnection(TenantContext ctx) {
        var section = asMap(privateSettings(ctx).get("infrastructure"));
        if (!(section.get("connections") instanceof Iterable<?> iterable)) {
            return Optional.empty();
        }
        for (var item : iterable) {
            var connector = asMap(item);
            if (!"REDIS".equalsIgnoreCase(safe(connector.get("type"))) || Boolean.FALSE.equals(connector.get("enabled"))) {
                continue;
            }
            var resolved = new LinkedHashMap<String, Object>();
            resolved.put("name", safe(connector.get("name")));
            resolved.put("host", safe(connector.get("endpoint")));
            resolved.put("port", connector.get("port"));
            resolved.put("username", safe(connector.get("username")));
            resolved.put("tlsEnabled", connector.get("tlsEnabled"));
            resolved.put("healthIndicatorEnabled", connector.get("redisHealthIndicatorEnabled"));
            var secret = asMap(connector.get("secretSecret"));
            resolved.put("password", secret.isEmpty() ? "" : secretCipherService.decrypt(secret));
            return Optional.of(resolved);
        }
        return Optional.empty();
    }

    /**
     * Resolves the configured custom-index storage path for this tenant/country/environment from
     * {@code infrastructure.connections} (first enabled INDEX_STORAGE row with a LOCAL/NFS provider).
     * Returns the filesystem path; empty when none is configured (callers fall back to
     * {@code kfh.index.storage.path}).
     */
    public Optional<Map<String, Object>> resolveIndexStorage(TenantContext ctx) {
        var section = asMap(privateSettings(ctx).get("infrastructure"));
        if (!(section.get("connections") instanceof Iterable<?> iterable)) {
            return Optional.empty();
        }
        for (var item : iterable) {
            var connector = asMap(item);
            if (!"INDEX_STORAGE".equalsIgnoreCase(safe(connector.get("type"))) || Boolean.FALSE.equals(connector.get("enabled"))) {
                continue;
            }
            var resolved = new LinkedHashMap<String, Object>();
            resolved.put("provider", safe(connector.get("provider")));
            resolved.put("endpoint", safe(connector.get("endpoint")));
            resolved.put("bucket", safe(connector.get("bucket")));
            resolved.put("region", safe(connector.get("region")));
            return Optional.of(resolved);
        }
        return Optional.empty();
    }

    private static Map<String, Map<String, Object>> existingAzureIntegrations(Map<String, Object> existingAzure) {
        var indexed = new LinkedHashMap<String, Map<String, Object>>();
        var integrations = existingAzure.get("integrations");
        if (integrations instanceof Iterable<?> iterable) {
            iterable.forEach(item -> {
                if (item instanceof Map<?, ?> map) {
                    indexed.put(safe(map.get("id")), asMap(map));
                }
            });
        }
        return indexed;
    }

    private static String normalizeCountryScope(String value) {
        var normalized = safe(value).toUpperCase(Locale.ROOT);
        return normalized.isBlank() || "GLOBAL".equals(normalized) || "DEFAULT".equals(normalized) ? DEFAULT_COUNTRY_SCOPE : normalized;
    }

    private static String normalizeEnvironment(String value) {
        var normalized = safe(value).toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? DEFAULT_COUNTRY_SCOPE : normalized;
    }

    private static List<String> normalizeCountryScopes(Object value) {
        var normalized = new ArrayList<String>();
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> addCountryScope(normalized, item));
        } else {
            addCountryScope(normalized, value);
        }
        if (normalized.isEmpty() || normalized.contains(DEFAULT_COUNTRY_SCOPE)) {
            return List.of(DEFAULT_COUNTRY_SCOPE);
        }
        return normalized.stream().distinct().toList();
    }

    private static void addCountryScope(List<String> scopes, Object value) {
        Arrays.stream(safe(value).split(","))
                .map(SettingsService::normalizeCountryScope)
                .filter(scope -> !scope.isBlank())
                .forEach(scopes::add);
    }

    private static String primaryCountryScope(List<String> countryCodes) {
        return countryCodes.contains(DEFAULT_COUNTRY_SCOPE) || countryCodes.isEmpty() ? DEFAULT_COUNTRY_SCOPE : countryCodes.getFirst();
    }

    private static Map<String, Object> publicSettingsSnapshot(Map<String, Object> snapshot) {
        return asMap(publicValue(snapshot));
    }

    private static Object publicValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            var copy = new LinkedHashMap<String, Object>();
            map.forEach((rawKey, nestedValue) -> {
                var key = String.valueOf(rawKey);
                if (!ENCRYPTED_SECRET_KEYS.contains(key)) {
                    copy.put(key, publicValue(nestedValue));
                }
            });
            return copy;
        }
        if (value instanceof Iterable<?> iterable) {
            var copy = new ArrayList<>();
            iterable.forEach(item -> copy.add(publicValue(item)));
            return copy;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static String secretReplacement(Object value) {
        var text = value == null ? "" : String.valueOf(value).trim();
        return text.isBlank() ? "" : MASKED_SECRET;
    }

    private String prop(String key) {
        return prop(key, "");
    }

    private String prop(String key, String fallback) {
        var value = environment.getProperty(key);
        return value == null ? fallback : value.trim();
    }

    private String maskedProperty(String key) {
        return prop(key).isBlank() ? "" : MASKED_SECRET;
    }

    private boolean bool(String key, boolean fallback) {
        return environment.getProperty(key, Boolean.class, fallback);
    }

    private int integer(String key, int fallback) {
        return environment.getProperty(key, Integer.class, fallback);
    }

    private static Map<String, Object> mapOf(Object... entries) {
        var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return map;
    }

    private static List<String> safeKeys(Map<String, Object> request) {
        if (request == null || request.isEmpty()) {
            return List.of();
        }
        return request.keySet().stream()
                .filter(key -> !isSecretLike(key))
                .sorted()
                .toList();
    }

    private static boolean isSecretLike(String key) {
        var normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return normalized.contains("password") || normalized.contains("token") || normalized.contains("secret")
                || normalized.contains("apikey") || normalized.contains("api_key") || normalized.contains("api-key")
                || normalized.contains("credential") || "webhookurl".equals(normalized) || "webhook_url".equals(normalized)
                || "webhook-url".equals(normalized);
    }

    private static final Set<String> COUNTRY_SCOPED_LIST_SECTIONS = Set.of(
            "azureOpenAI.integrations",
            "databases.connections",
            "sharepoint.connections",
            "infrastructure.connections");

    private static Map<String, Object> filterCountryScopedSettings(Map<String, Object> snapshot, TenantContext ctx) {
        var filtered = new LinkedHashMap<String, Object>();
        snapshot.forEach((key, value) -> filtered.put(key, filterCountryScopedValue(key, value, ctx)));
        return filtered;
    }

    private static Object filterCountryScopedValue(String path, Object value, TenantContext ctx) {
        if (value instanceof Map<?, ?> map) {
            var filtered = new LinkedHashMap<String, Object>();
            map.forEach((rawKey, nestedValue) -> {
                var key = String.valueOf(rawKey);
                filtered.put(key, filterCountryScopedValue(path.isBlank() ? key : path + "." + key, nestedValue, ctx));
            });
            return filtered;
        }
        if (value instanceof Iterable<?> iterable) {
            var filtered = new ArrayList<>();
            iterable.forEach(item -> {
                if (!COUNTRY_SCOPED_LIST_SECTIONS.contains(path) || isVisibleForCountry(item, ctx)) {
                    filtered.add(filterCountryScopedValue(path, item, ctx));
                }
            });
            return filtered;
        }
        return value;
    }

    private static boolean isVisibleForCountry(Object item, TenantContext ctx) {
        if (!(item instanceof Map<?, ?> map)) {
            return true;
        }
        var scopes = normalizeCountryScopes(map.containsKey("countryCodes") ? map.get("countryCodes") : map.get("countryCode"));
        var activeCountry = normalizeCountryScope(ctx.countryCode());
        return scopes.contains(DEFAULT_COUNTRY_SCOPE) || scopes.contains(activeCountry);
    }
}
