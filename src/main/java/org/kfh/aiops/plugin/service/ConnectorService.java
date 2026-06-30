package org.kfh.aiops.plugin.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.kfh.aiops.commandcenter.dto.PageResponse;
import org.kfh.aiops.commandcenter.dto.UiWriteRequest;
import org.kfh.aiops.commandcenter.support.CommandCenterReadModel;
import org.kfh.aiops.commandcenter.support.UiQuerySupport;
import org.kfh.aiops.platform.audit.AuditService;
import org.kfh.aiops.platform.country.CountryAccessGuard;
import org.kfh.aiops.platform.exception.NotFoundException;
import org.kfh.aiops.platform.exception.ServiceUnavailableException;
import org.kfh.aiops.platform.exception.ValidationException;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.kfh.aiops.plugin.implementations.appdynamics.AppDynamicsConnectorConfigValidator;
import org.kfh.aiops.plugin.implementations.appdynamics.AppDynamicsConnectorLiveTester;
import org.kfh.aiops.plugin.implementations.bmc.BmcConnectorConfigValidator;
import org.kfh.aiops.plugin.implementations.bmc.BmcConnectorLiveTester;
import org.kfh.aiops.plugin.implementations.emco.EmcoConnectorConfigValidator;
import org.kfh.aiops.plugin.implementations.emco.EmcoConnectorLiveTester;
import org.kfh.aiops.plugin.implementations.scom.ScomConnectorConfigValidator;
import org.kfh.aiops.plugin.implementations.scom.ScomConnectorLiveTester;
import org.kfh.aiops.plugin.implementations.vrops.VropsConnectorConfigValidator;
import org.kfh.aiops.plugin.implementations.vrops.VropsConnectorLiveTester;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ConnectorService {

    private final CommandCenterReadModel readModel;
    private final CountryAccessGuard countryGuard;
    private final AuditService auditService;
    private final ConnectorCatalogService catalogService;
    private final BmcConnectorConfigValidator bmcValidator;
    private final AppDynamicsConnectorConfigValidator appDynamicsValidator;
    private final VropsConnectorConfigValidator vropsValidator;
    private final ScomConnectorConfigValidator scomValidator;
    private final EmcoConnectorConfigValidator emcoValidator;
    private final Optional<BmcConnectorLiveTester> bmcLiveTester;
    private final Optional<AppDynamicsConnectorLiveTester> appDynamicsLiveTester;
    private final Optional<VropsConnectorLiveTester> vropsLiveTester;
    private final Optional<ScomConnectorLiveTester> scomLiveTester;
    private final Optional<EmcoConnectorLiveTester> emcoLiveTester;
    private final Optional<ConnectorPersistenceStore> persistenceStore;

    public ConnectorService(CommandCenterReadModel readModel, CountryAccessGuard countryGuard, AuditService auditService,
            ConnectorCatalogService catalogService, BmcConnectorConfigValidator bmcValidator) {
        this.readModel = readModel;
        this.countryGuard = countryGuard;
        this.auditService = auditService;
        this.catalogService = catalogService;
        this.bmcValidator = bmcValidator;
        this.appDynamicsValidator = new AppDynamicsConnectorConfigValidator();
        this.vropsValidator = new VropsConnectorConfigValidator();
        this.scomValidator = new ScomConnectorConfigValidator();
        this.emcoValidator = new EmcoConnectorConfigValidator();
        this.persistenceStore = Optional.empty();
        this.bmcLiveTester = Optional.empty();
        this.appDynamicsLiveTester = Optional.empty();
        this.vropsLiveTester = Optional.empty();
        this.scomLiveTester = Optional.empty();
        this.emcoLiveTester = Optional.empty();
    }

    public ConnectorService(CommandCenterReadModel readModel, CountryAccessGuard countryGuard, AuditService auditService,
            ConnectorCatalogService catalogService, BmcConnectorConfigValidator bmcValidator,
            ObjectProvider<ConnectorPersistenceStore> persistenceStore) {
        this.readModel = readModel;
        this.countryGuard = countryGuard;
        this.auditService = auditService;
        this.catalogService = catalogService;
        this.bmcValidator = bmcValidator;
        this.appDynamicsValidator = new AppDynamicsConnectorConfigValidator();
        this.vropsValidator = new VropsConnectorConfigValidator();
        this.scomValidator = new ScomConnectorConfigValidator();
        this.emcoValidator = new EmcoConnectorConfigValidator();
        this.persistenceStore = Optional.ofNullable(persistenceStore.getIfAvailable());
        this.bmcLiveTester = Optional.empty();
        this.appDynamicsLiveTester = Optional.empty();
        this.vropsLiveTester = Optional.empty();
        this.scomLiveTester = Optional.empty();
        this.emcoLiveTester = Optional.empty();
    }

    public ConnectorService(CommandCenterReadModel readModel, CountryAccessGuard countryGuard, AuditService auditService,
            ConnectorCatalogService catalogService, BmcConnectorConfigValidator bmcValidator,
            AppDynamicsConnectorConfigValidator appDynamicsValidator, VropsConnectorConfigValidator vropsValidator,
            ScomConnectorConfigValidator scomValidator,
            ObjectProvider<ConnectorPersistenceStore> persistenceStore,
            ObjectProvider<BmcConnectorLiveTester> bmcLiveTester,
            ObjectProvider<AppDynamicsConnectorLiveTester> appDynamicsLiveTester,
            ObjectProvider<VropsConnectorLiveTester> vropsLiveTester,
            ObjectProvider<ScomConnectorLiveTester> scomLiveTester) {
        this(readModel, countryGuard, auditService, catalogService, bmcValidator, appDynamicsValidator, vropsValidator,
                scomValidator, new EmcoConnectorConfigValidator(), persistenceStore.getIfAvailable(),
                bmcLiveTester.getIfAvailable(), appDynamicsLiveTester.getIfAvailable(), vropsLiveTester.getIfAvailable(),
                scomLiveTester.getIfAvailable(), null);
    }

    @Autowired
    public ConnectorService(CommandCenterReadModel readModel, CountryAccessGuard countryGuard, AuditService auditService,
            ConnectorCatalogService catalogService, BmcConnectorConfigValidator bmcValidator,
            AppDynamicsConnectorConfigValidator appDynamicsValidator, VropsConnectorConfigValidator vropsValidator,
            ScomConnectorConfigValidator scomValidator, EmcoConnectorConfigValidator emcoValidator,
            ObjectProvider<ConnectorPersistenceStore> persistenceStore,
            ObjectProvider<BmcConnectorLiveTester> bmcLiveTester,
            ObjectProvider<AppDynamicsConnectorLiveTester> appDynamicsLiveTester,
            ObjectProvider<VropsConnectorLiveTester> vropsLiveTester,
            ObjectProvider<ScomConnectorLiveTester> scomLiveTester,
            ObjectProvider<EmcoConnectorLiveTester> emcoLiveTester) {
        this(readModel, countryGuard, auditService, catalogService, bmcValidator, appDynamicsValidator, vropsValidator, scomValidator, emcoValidator,
                persistenceStore.getIfAvailable(), bmcLiveTester.getIfAvailable(), appDynamicsLiveTester.getIfAvailable(),
                vropsLiveTester.getIfAvailable(), scomLiveTester.getIfAvailable(), emcoLiveTester.getIfAvailable());
    }

    private ConnectorService(CommandCenterReadModel readModel, CountryAccessGuard countryGuard, AuditService auditService,
            ConnectorCatalogService catalogService, BmcConnectorConfigValidator bmcValidator,
            AppDynamicsConnectorConfigValidator appDynamicsValidator, VropsConnectorConfigValidator vropsValidator,
            ScomConnectorConfigValidator scomValidator, EmcoConnectorConfigValidator emcoValidator,
            ConnectorPersistenceStore persistenceStore, BmcConnectorLiveTester bmcLiveTester,
            AppDynamicsConnectorLiveTester appDynamicsLiveTester, VropsConnectorLiveTester vropsLiveTester,
            ScomConnectorLiveTester scomLiveTester, EmcoConnectorLiveTester emcoLiveTester) {
        this.readModel = readModel;
        this.countryGuard = countryGuard;
        this.auditService = auditService;
        this.catalogService = catalogService;
        this.bmcValidator = bmcValidator;
        this.appDynamicsValidator = appDynamicsValidator;
        this.vropsValidator = vropsValidator;
        this.scomValidator = scomValidator;
        this.emcoValidator = emcoValidator;
        this.persistenceStore = Optional.ofNullable(persistenceStore);
        this.bmcLiveTester = Optional.ofNullable(bmcLiveTester);
        this.appDynamicsLiveTester = Optional.ofNullable(appDynamicsLiveTester);
        this.vropsLiveTester = Optional.ofNullable(vropsLiveTester);
        this.scomLiveTester = Optional.ofNullable(scomLiveTester);
        this.emcoLiveTester = Optional.ofNullable(emcoLiveTester);
    }

    public List<?> types(TenantContext ctx) {
        ctx.requirePermission("CONNECTOR_READ");
        return catalogService.types();
    }

    public PageResponse<Map<String, Object>> list(TenantContext ctx, int page, int size) {
        ctx.requirePermission("CONNECTOR_READ");
        countryGuard.requireAccess(ctx, ctx.countryCode());
        var rows = persistenceStore.map(store -> store.list(ctx))
                .orElseGet(() -> readModel.connectors(ctx.countryCode(), ctx.environment()));
        return PageResponse.of(rows, page, size);
    }

    public Map<String, Object> get(TenantContext ctx, UUID id) {
        ctx.requirePermission("CONNECTOR_READ");
        return require(ctx, id);
    }

    public Map<String, Object> create(TenantContext ctx, UiWriteRequest request) {
        ctx.requirePermission("CONNECTOR_WRITE");
        var rawFields = UiQuerySupport.fields(request);
        var pluginType = catalogService.normalizePluginType(rawFields.get("pluginType"));
        var fields = normalizeCredentialFields(pluginType, rawFields);
        if (!catalogService.isAvailable(pluginType)) {
            throw new ValidationException("Connector type is not available yet: " + pluginType);
        }
        var country = connectorCountry(ctx, fields);
        var environment = connectorEnvironment(ctx, fields);
        var safeFields = validate(pluginType, fields, Map.of(), environment, true);
        copySecretsForPersistence(fields, safeFields);
        var name = UiQuerySupport.name(request, "BMC Helix connector");
        var enabled = booleanField(safeFields.get("enabled"), request.enabled());
        safeFields.putIfAbsent("enabled", enabled);
        var created = durableStore().create(ctx, country, environment, name, pluginType, enabled, safeFields);
        // Determine whether this is an Install (from Marketplace) or a fresh Save
        // so the audit page shows the right operator activity.
        var isInstallOnly = booleanField(fields.get("installOnly"), false)
                || "PENDING".equalsIgnoreCase(String.valueOf(fields.getOrDefault("configurationStatus", "")));
        audit(ctx, isInstallOnly ? "CONNECTOR_INSTALLED" : "CONNECTOR_CREATED", created.get("id"),
                connectorAuditPayload(created, Map.of(
                        "name", String.valueOf(created.getOrDefault("name", name)),
                        "pluginType", pluginType,
                        "countryCode", country,
                        "environment", environment,
                        "enabled", enabled,
                        "result", "Success",
                        "message", (isInstallOnly ? "Installed " : "Created ") + pluginType + " connector "
                                + created.getOrDefault("name", name) + " (" + country + " " + environment + ")")));
        return created;
    }

    public Map<String, Object> update(TenantContext ctx, UUID id, UiWriteRequest request) {
        ctx.requirePermission("CONNECTOR_WRITE");
        var existing = require(ctx, id);
        var rawFields = UiQuerySupport.fields(request);
        var pluginType = catalogService.normalizePluginType(rawFields.getOrDefault("pluginType", existing.get("pluginType")));
        var fields = normalizeCredentialFields(pluginType, rawFields);
        var environment = connectorEnvironment(ctx, fields.isEmpty() ? existing : fields);
        var safeFields = validate(pluginType, fields, existing, environment, false);
        copySecretsForPersistence(fields, safeFields);
        var country = fields.containsKey("countryCode") ? connectorCountry(ctx, fields) : String.valueOf(existing.get("countryCode"));
        safeFields.put("countryCode", country);
        safeFields.put("environment", environment);
        if (request.enabled() != null) {
            safeFields.put("enabled", request.enabled());
        }
        if (request.name() != null && !request.name().isBlank()) {
            safeFields.put("name", request.name());
        }
        var updated = durableStore().update(id, safeFields);
        // Distinguish credential rotation from a plain configuration change so
        // the audit trail makes the operator intent clear.
        var hadSecrets = fields.keySet().stream().anyMatch(ConnectorService::looksLikeSecretField);
        var action = hadSecrets ? "CONNECTOR_CONFIGURED" : "CONNECTOR_UPDATED";
        audit(ctx, action, id, connectorAuditPayload(updated, Map.of(
                "name", String.valueOf(updated.getOrDefault("name", existing.get("name"))),
                "pluginType", String.valueOf(updated.getOrDefault("pluginType", pluginType)),
                "countryCode", country,
                "environment", environment,
                "secretsRotated", hadSecrets,
                "result", "Success",
                "message", (hadSecrets ? "Configured credentials for " : "Updated ") + pluginType + " connector "
                        + updated.getOrDefault("name", existing.get("name")) + " (" + country + " " + environment + ")")));
        return updated;
    }

    public void delete(TenantContext ctx, UUID id) {
        ctx.requirePermission("CONNECTOR_WRITE");
        var existing = require(ctx, id);
        durableStore().delete(id);
        audit(ctx, "CONNECTOR_UNINSTALLED", id, connectorAuditPayload(existing, Map.of(
                "name", String.valueOf(existing.getOrDefault("name", "")),
                "pluginType", String.valueOf(existing.getOrDefault("pluginType", existing.getOrDefault("type", "")))
                        ,
                "countryCode", String.valueOf(existing.getOrDefault("countryCode", "")),
                "environment", String.valueOf(existing.getOrDefault("environment", "")),
                "result", "Success",
                "message", "Uninstalled connector " + existing.getOrDefault("name", id))));
    }

    public Map<String, Object> toggle(TenantContext ctx, UUID id, boolean enabled) {
        ctx.requirePermission("CONNECTOR_WRITE");
        var existing = require(ctx, id);
        var updated = durableStore().update(id, Map.of("enabled", enabled));
        audit(ctx, enabled ? "CONNECTOR_ENABLED" : "CONNECTOR_DISABLED", id,
                connectorAuditPayload(updated, Map.of(
                        "name", String.valueOf(updated.getOrDefault("name", existing.get("name"))),
                        "pluginType", String.valueOf(updated.getOrDefault("pluginType",
                                existing.getOrDefault("pluginType", existing.getOrDefault("type", "")))),
                        "countryCode", String.valueOf(updated.getOrDefault("countryCode",
                                existing.getOrDefault("countryCode", ""))),
                        "environment", String.valueOf(updated.getOrDefault("environment",
                                existing.getOrDefault("environment", ""))),
                        "enabled", enabled,
                        "result", "Success",
                        "message", "Connector " + updated.getOrDefault("name", existing.get("name"))
                                + (enabled ? " enabled" : " disabled"))));
        return updated;
    }

    public Map<String, Object> test(TenantContext ctx, UUID id) {
        ctx.requirePermission("CONNECTOR_TEST");
        var connector = require(ctx, id);
        audit(ctx, "CONNECTOR_TEST_REQUESTED", id, connectorAuditPayload(connector, Map.of(
                "name", String.valueOf(connector.getOrDefault("name", "")),
                "pluginType", String.valueOf(connector.getOrDefault("pluginType", connector.getOrDefault("type", ""))),
                "countryCode", String.valueOf(connector.getOrDefault("countryCode", "")),
                "environment", String.valueOf(connector.getOrDefault("environment", "")),
                "result", "Pending",
                "severity", "Info",
                "message", "Connection test requested for " + connector.getOrDefault("name", id))));
        var store = durableStore();
        var result = executeLiveTest(ctx, id, connector, store);
        // Emit a follow-up audit so the operator can see test outcomes (not just requests)
        // on the Audit page, including the resolved root cause when the test fails.
        var passed = Boolean.TRUE.equals(result.get("pass"));
        audit(ctx, passed ? "CONNECTOR_TEST_SUCCEEDED" : "CONNECTOR_TEST_FAILED", id,
                connectorTestAuditPayload(connector, result, passed));
        return result;
    }

    public Map<String, Object> heartbeat(TenantContext ctx) {
        ctx.requirePermission("CONNECTOR_TEST");
        countryGuard.requireAccess(ctx, ctx.countryCode());
        var store = durableStore();
        audit(ctx, "CONNECTOR_HEARTBEAT_REQUESTED", ctx.countryCode() + ":" + ctx.environment());
        var results = store.list(ctx).stream()
                .filter(connector -> Boolean.TRUE.equals(connector.get("enabled")))
                .map(connector -> heartbeatOne(ctx, connector, store))
                .toList();
        var passCount = results.stream().filter(result -> Boolean.TRUE.equals(result.get("pass"))).count();
        var response = new LinkedHashMap<String, Object>();
        response.put("checkedAt", Instant.now().toString());
        response.put("correlationId", ctx.correlationId());
        response.put("totalEnabled", results.size());
        response.put("healthy", passCount);
        response.put("down", results.size() - passCount);
        response.put("results", results);
        return response;
    }

    private Map<String, Object> executeLiveTest(TenantContext ctx, UUID id, Map<String, Object> connector,
            ConnectorPersistenceStore store) {
        var pluginType = catalogService.normalizePluginType(connector.get("pluginType"));
        Map<String, String> secrets;
        try {
            secrets = store.secrets(id).orElse(Map.of());
        } catch (ServiceUnavailableException ex) {
            var failed = failedSecretResult(ctx, connector, ex);
            store.recordTestResult(id, false, failed);
            return failed;
        }
        var result = switch (pluginType) {
            case ConnectorCatalogService.BMC_PLUGIN_TYPE -> bmcLiveTester.orElseThrow(() -> new ServiceUnavailableException(
                    "CONNECTOR_TEST_UNAVAILABLE", "BMC live connector tester is unavailable"))
                    .test(ctx, connector, secrets);
            case ConnectorCatalogService.APPDYNAMICS_PLUGIN_TYPE -> appDynamicsLiveTester.orElseThrow(() -> new ServiceUnavailableException(
                    "CONNECTOR_TEST_UNAVAILABLE", "AppDynamics live connector tester is unavailable"))
                    .test(ctx, connector, secrets);
            case ConnectorCatalogService.VROPS_PLUGIN_TYPE -> vropsLiveTester.orElseThrow(() -> new ServiceUnavailableException(
                    "CONNECTOR_TEST_UNAVAILABLE", "vROps live connector tester is unavailable"))
                    .test(ctx, connector, secrets);
            case ConnectorCatalogService.SCOM_PLUGIN_TYPE -> scomLiveTester.orElseThrow(() -> new ServiceUnavailableException(
                    "CONNECTOR_TEST_UNAVAILABLE", "SCOM live connector tester is unavailable"))
                    .test(ctx, connector, secrets);
            case ConnectorCatalogService.EMCO_PLUGIN_TYPE -> emcoLiveTester.orElseThrow(() -> new ServiceUnavailableException(
                    "CONNECTOR_TEST_UNAVAILABLE", "EMCO live connector tester is unavailable"))
                    .test(ctx, connector, secrets);
            default -> throw new ValidationException("Live connector test is not available yet for connector type: " + pluginType);
        };
        store.recordTestResult(id, Boolean.TRUE.equals(result.get("pass")), result);
        return result;
    }

    private Map<String, Object> failedSecretResult(TenantContext ctx, Map<String, Object> connector,
            ServiceUnavailableException ex) {
        var result = new LinkedHashMap<String, Object>();
        result.put("pass", false);
        result.put("readyToCollect", false);
        result.put("status", "FAIL");
        result.put("connectorId", String.valueOf(connector.get("id")));
        result.put("pluginType", String.valueOf(connector.getOrDefault("pluginType", connector.get("type"))));
        result.put("checkedEndpoint", checkedEndpoint(connector));
        result.put("testedAt", Instant.now().toString());
        result.put("correlationId", ctx.correlationId());
        result.put("errorCode", ex.code());
        result.put("credentialRecoveryRequired", "SECRET_DECRYPTION_FAILED".equals(ex.code()));
        result.put("message", secretFailureMessage(ex));
        result.put("steps", List.of(Map.of("name", "Encrypted connector credentials", "status", "fail",
                "message", secretFailureMessage(ex))));
        return result;
    }

    private static String secretFailureMessage(ServiceUnavailableException ex) {
        return switch (ex.code()) {
            case "SECRET_DECRYPTION_FAILED" -> "Saved connector secrets cannot be decrypted with the current platform "
                    + "master key. Restore the original stable KFH_AIOPS_SECRET_KEY/deployment secret file used when "
                    + "these credentials were saved, or re-enter all credential fields for this connector so they are "
                    + "encrypted with the current key.";
            case "SECRET_MASTER_KEY_MISSING" -> "Connector secrets cannot be decrypted because the running backend "
                    + "does not have a configured platform secret master key. Set KFH_AIOPS_SECRET_KEY in the Java launcher "
                    + "or create the protected deployment secret file, then retry.";
            case "SECRET_MASTER_KEY_FILE_UNREADABLE" -> "Connector secrets cannot be decrypted because the configured "
                    + "platform secret master key file is missing, unreadable, or blank. Fix the deployment secret file, then retry.";
            default -> safeExceptionMessage(ex);
        };
    }

    private Map<String, Object> heartbeatOne(TenantContext ctx, Map<String, Object> connector,
            ConnectorPersistenceStore store) {
        var id = UUID.fromString(String.valueOf(connector.get("id")));
        try {
            var result = executeLiveTest(ctx, id, connector, store);
            var passed = Boolean.TRUE.equals(result.get("pass"));
            audit(ctx, passed ? "CONNECTOR_HEARTBEAT_TESTED" : "CONNECTOR_HEARTBEAT_FAILED", id,
                    connectorTestAuditPayload(connector, result, passed));
            return result;
        } catch (RuntimeException ex) {
            var result = failedHeartbeatResult(ctx, connector, ex);
            store.recordTestResult(id, false, result);
            audit(ctx, "CONNECTOR_HEARTBEAT_FAILED", id, connectorTestAuditPayload(connector, result, false));
            return result;
        }
    }

    private static Map<String, Object> failedHeartbeatResult(TenantContext ctx, Map<String, Object> connector, RuntimeException ex) {
        var failureMessage = safeExceptionMessage(ex);
        var result = new LinkedHashMap<String, Object>();
        result.put("pass", false);
        result.put("readyToCollect", false);
        result.put("status", "FAIL");
        result.put("connectorId", String.valueOf(connector.get("id")));
        result.put("pluginType", String.valueOf(connector.getOrDefault("pluginType", connector.get("type"))));
        result.put("checkedEndpoint", checkedEndpoint(connector));
        result.put("testedAt", Instant.now().toString());
        result.put("correlationId", ctx.correlationId());
        result.put("message", "Connector heartbeat failed before live validation completed: " + failureMessage);
        result.put("steps", List.of(Map.of("name", "Heartbeat execution", "status", "fail",
                "message", failureMessage)));
        return result;
    }

    private static String safeExceptionMessage(RuntimeException ex) {
        var message = Objects.toString(ex.getMessage(), ex.getClass().getSimpleName())
                .replaceAll("(?i)(\"?(?:password|authorization|token|secret|credential|access[_-]?key|access[_-]?secret[_-]?key|username)\"?\\s*[:=]\\s*\"?)[^\",;\\s}]+", "$1masked")
                .replaceAll("(?i)(bearer|basic|vRealizeOpsToken)\\s+[^,;\\s]+", "$1 masked")
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim();
        if (message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() <= 300 ? message : message.substring(0, 297) + "...";
    }

    private static String checkedEndpoint(Map<String, Object> connector) {
        return String.valueOf(connector.getOrDefault("controllerUrl",
                connector.getOrDefault("baseUrl", connector.getOrDefault("endpointUrl",
                        connector.getOrDefault("managementServer",
                                connector.getOrDefault("sqlServer", connector.getOrDefault("host", "")))))));
    }

    public List<Map<String, Object>> logs(TenantContext ctx, UUID id) {
        require(ctx, id);
        return List.of(Map.of("level", "INFO", "message", "Last connector run completed", "connectorId", id.toString()));
    }

    private Map<String, Object> require(TenantContext ctx, UUID id) {
        var row = persistenceStore.flatMap(store -> store.find(id)).orElseGet(() -> readModel.findConnector(id));
        if (row.isEmpty()) {
            throw new NotFoundException("Connector not found");
        }
        if (row.containsKey("tenantId") && !ctx.tenantId().toString().equals(String.valueOf(row.get("tenantId")))) {
            throw new NotFoundException("Connector not found");
        }
        countryGuard.requireAccess(ctx, String.valueOf(row.get("countryCode")));
        return row;
    }

    private ConnectorPersistenceStore durableStore() {
        return persistenceStore.orElseThrow(() -> new ServiceUnavailableException("CONNECTOR_PERSISTENCE_UNAVAILABLE",
                "Connector database persistence is unavailable; connector changes were not saved. Restart with PostgreSQL/JDBC enabled."));
    }

    private Map<String, Object> validate(String pluginType, Map<String, Object> fields,
            Map<String, Object> existing, String environment, boolean create) {
        if (ConnectorCatalogService.BMC_PLUGIN_TYPE.equals(pluginType)) {
            return create
                    ? bmcValidator.validateForCreate(fields, environment)
                    : bmcValidator.validateForUpdate(fields, existing, environment);
        }
        if (ConnectorCatalogService.APPDYNAMICS_PLUGIN_TYPE.equals(pluginType)) {
            return create
                    ? appDynamicsValidator.validateForCreate(fields, environment)
                    : appDynamicsValidator.validateForUpdate(fields, existing, environment);
        }
        if (ConnectorCatalogService.VROPS_PLUGIN_TYPE.equals(pluginType)) {
            return create
                    ? vropsValidator.validateForCreate(fields, environment)
                    : vropsValidator.validateForUpdate(fields, existing, environment);
        }
        if (ConnectorCatalogService.SCOM_PLUGIN_TYPE.equals(pluginType)) {
            return create
                    ? scomValidator.validateForCreate(fields, environment)
                    : scomValidator.validateForUpdate(fields, existing, environment);
        }
        if (ConnectorCatalogService.EMCO_PLUGIN_TYPE.equals(pluginType)) {
            return create
                    ? emcoValidator.validateForCreate(fields, environment)
                    : emcoValidator.validateForUpdate(fields, existing, environment);
        }
        if (!create) {
            return fields;
        }
        throw new ValidationException("Connector type is not available yet: " + pluginType);
    }

    private static void copySecretsForPersistence(Map<String, Object> fields, Map<String, Object> safeFields) {
        if (fields.containsKey("secretsPlain")) {
            safeFields.put("secretsPlain", fields.get("secretsPlain"));
        }
    }

    private static Map<String, Object> normalizeCredentialFields(String pluginType, Map<String, Object> rawFields) {
        var fields = new LinkedHashMap<>(rawFields);
        var secrets = objectMap(fields.get("secretsPlain"));
        credentialAliases(pluginType).forEach((canonicalName, aliases) -> {
            for (var alias : aliases) {
                var secret = stringOrNull(fields.remove(alias));
                if (secret != null && !secrets.containsKey(canonicalName)) {
                    secrets.put(canonicalName, secret);
                }
            }
        });
        if (!secrets.isEmpty() || rawFields.containsKey("secretsPlain")) {
            fields.put("secretsPlain", secrets);
        }
        return fields;
    }

    private static Map<String, List<String>> credentialAliases(String pluginType) {
        return switch (pluginType) {
            case ConnectorCatalogService.BMC_PLUGIN_TYPE -> Map.of(
                    "accessKey", List.of("accessKey", "access_key"),
                    "accessSecretKey", List.of("accessSecretKey", "access_secret_key", "accessSecret"));
            case ConnectorCatalogService.APPDYNAMICS_PLUGIN_TYPE -> Map.of(
                    "username", List.of("username", "user", "appdynamicsUsername"),
                    "password", List.of("password", "appdynamicsPassword"));
            case ConnectorCatalogService.VROPS_PLUGIN_TYPE -> Map.of(
                    "username", List.of("username", "user", "vropsUsername"),
                    "password", List.of("password", "vropsPassword"));
            case ConnectorCatalogService.SCOM_PLUGIN_TYPE -> Map.of(
                    "username", List.of("username", "user", "scomUsername"),
                    "password", List.of("password", "scomPassword"));
            case ConnectorCatalogService.EMCO_PLUGIN_TYPE -> Map.of(
                    "kfhUsername", List.of("kfhUsername", "kfhUser", "kfh_username"),
                    "kfhPassword", List.of("kfhPassword", "kfh_password"),
                    "cctvUsername", List.of("cctvUsername", "cctvUser", "cctv_username"),
                    "cctvPassword", List.of("cctvPassword", "cctv_password"));
            default -> Map.of();
        };
    }

    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        var result = new LinkedHashMap<String, Object>();
        map.forEach((key, entry) -> {
            var secret = stringOrNull(entry);
            if (secret != null) {
                result.put(String.valueOf(key), secret);
            }
        });
        return result;
    }

    private String connectorCountry(TenantContext ctx, Map<String, Object> fields) {
        var requested = UiQuerySupport.country(ctx, countryGuard, stringField(fields, "countryCode"));
        if (CountryAccessGuard.ALL_COUNTRIES_SCOPE.equals(requested)) {
            throw new ValidationException("Connector must be installed for a physical country scope");
        }
        return requested;
    }

    private String connectorEnvironment(TenantContext ctx, Map<String, Object> fields) {
        var environment = UiQuerySupport.environment(ctx, stringField(fields, "environment"));
        if (!List.of("PROD", "UAT", "DEV").contains(environment)) {
            throw new ValidationException("Connector environment must be PROD, UAT, or DEV");
        }
        return environment;
    }

    private static String stringField(Map<String, Object> fields, String key) {
        var value = fields.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static String stringOrNull(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return String.valueOf(value).trim();
    }

    private static boolean booleanField(Object primary, Boolean fallback) {
        if (primary instanceof Boolean value) {
            return value;
        }
        return Boolean.TRUE.equals(fallback);
    }

    private void audit(TenantContext ctx, String action, Object id) {
        auditService.recordWrite(ctx, action, "Connector", String.valueOf(id), null, Map.of("id", String.valueOf(id)));
        readModel.appendAudit(ctx, action, "Connector", String.valueOf(id));
    }

    /**
     * Overload accepting an enriched audit payload. Always includes the
     * connector id, plus any extra metadata supplied by the caller (name,
     * pluginType, country, environment, result, errorCode, message). The
     * payload becomes the afterState of the audit row, which the audit page
     * renders as the activity details so operators can trace what changed.
     */
    private void audit(TenantContext ctx, String action, Object id, Map<String, Object> payload) {
        var safe = new LinkedHashMap<String, Object>();
        safe.put("id", String.valueOf(id));
        if (payload != null) {
            payload.forEach((k, v) -> { if (v != null) safe.put(k, v); });
        }
        auditService.recordWrite(ctx, action, "Connector", String.valueOf(id), null, safe);
        readModel.appendAudit(ctx, action, "Connector", String.valueOf(id));
    }

    /**
     * Build a compact, secret-free payload describing the connector for audit
     * purposes. Never includes credential fields.
     */
    private static Map<String, Object> connectorAuditPayload(Map<String, Object> connector, Map<String, Object> extras) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("targetType", "Connector");
        payload.put("targetId", String.valueOf(connector.getOrDefault("id", "")));
        payload.put("targetName", String.valueOf(connector.getOrDefault("name", "")));
        if (extras != null) {
            extras.forEach((k, v) -> { if (v != null) payload.put(k, v); });
        }
        return payload;
    }

    /**
     * Build a test-outcome audit payload from the live-tester result. Status
     * fields are surfaced so the Audit page shows the actual outcome of a
     * connector test or heartbeat (pass/fail, error code, masked message,
     * endpoint, correlation id).
     */
    private static Map<String, Object> connectorTestAuditPayload(Map<String, Object> connector,
            Map<String, Object> result, boolean passed) {
        var extras = new LinkedHashMap<String, Object>();
        extras.put("pluginType", String.valueOf(connector.getOrDefault("pluginType", connector.getOrDefault("type", ""))));
        extras.put("countryCode", String.valueOf(connector.getOrDefault("countryCode", "")));
        extras.put("environment", String.valueOf(connector.getOrDefault("environment", "")));
        extras.put("checkedEndpoint", String.valueOf(result.getOrDefault("checkedEndpoint", "")));
        extras.put("status", String.valueOf(result.getOrDefault("status", passed ? "PASS" : "FAIL")));
        extras.put("errorCode", String.valueOf(result.getOrDefault("errorCode", "")));
        extras.put("credentialRecoveryRequired", result.getOrDefault("credentialRecoveryRequired", false));
        extras.put("testCorrelationId", String.valueOf(result.getOrDefault("correlationId", "")));
        extras.put("testedAt", String.valueOf(result.getOrDefault("testedAt", "")));
        extras.put("result", passed ? "Success" : "Fail");
        extras.put("severity", passed ? "Info" : "Warn");
        var name = String.valueOf(connector.getOrDefault("name", ""));
        extras.put("message", passed
                ? "Connection test succeeded for " + name
                : "Connection test failed for " + name + ": " + result.getOrDefault("message", ""));
        return connectorAuditPayload(connector, extras);
    }

    /** Identify fields the user likely typed to rotate credentials. */
    private static boolean looksLikeSecretField(String key) {
        if (key == null) return false;
        var k = key.toLowerCase(java.util.Locale.ROOT);
        return k.contains("password") || k.contains("secret") || k.contains("token")
                || k.contains("apikey") || k.contains("api_key") || k.endsWith("key")
                || k.contains("credential");
    }
}

