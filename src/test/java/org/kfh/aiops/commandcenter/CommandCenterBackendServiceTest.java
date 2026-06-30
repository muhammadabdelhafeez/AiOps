package org.kfh.aiops.commandcenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.commandcenter.dashboard.DashboardService;
import org.kfh.aiops.commandcenter.dto.UiWriteRequest;
import org.kfh.aiops.commandcenter.support.CommandCenterReadModel;
import org.kfh.aiops.platform.audit.AuditService;
import org.kfh.aiops.platform.country.CountryAccessGuard;
import org.kfh.aiops.platform.country.CountryRegistry;
import org.kfh.aiops.platform.exception.ForbiddenAccessException;
import org.kfh.aiops.platform.exception.NotFoundException;
import org.kfh.aiops.platform.exception.ServiceUnavailableException;
import org.kfh.aiops.platform.exception.ValidationException;
import org.kfh.aiops.platform.identity.IdentityAdminService;
import org.kfh.aiops.platform.identity.IdentityJdbcRepository;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.kfh.aiops.plugin.implementations.appdynamics.AppDynamicsConnectorConfigValidator;
import org.kfh.aiops.plugin.implementations.appdynamics.AppDynamicsConnectorLiveTester;
import org.kfh.aiops.plugin.implementations.bmc.BmcConnectorConfigValidator;
import org.kfh.aiops.plugin.implementations.bmc.BmcConnectorLiveTester;
import org.kfh.aiops.plugin.implementations.scom.ScomConnectorConfigValidator;
import org.kfh.aiops.plugin.implementations.scom.ScomConnectorLiveTester;
import org.kfh.aiops.plugin.implementations.vrops.VropsConnectorConfigValidator;
import org.kfh.aiops.plugin.implementations.vrops.VropsConnectorLiveTester;
import org.kfh.aiops.plugin.service.ConnectorCatalogService;
import org.kfh.aiops.plugin.service.ConnectorPersistenceStore;
import org.kfh.aiops.plugin.service.ConnectorService;
import org.springframework.beans.factory.ObjectProvider;

class CommandCenterBackendServiceTest {

    private final CommandCenterReadModel readModel = new CommandCenterReadModel();
    private final CountryAccessGuard guard = new CountryAccessGuard(new CountryRegistry(Set.of("KW", "BH", "EG")));
    private final AuditService audit = (ctx, action, entityType, entityId, beforeState, afterState) -> { };
    private final ConnectorCatalogService connectorCatalog = new ConnectorCatalogService();
    private final BmcConnectorConfigValidator bmcValidator = new BmcConnectorConfigValidator();
    private final AppDynamicsConnectorConfigValidator appDynamicsValidator = new AppDynamicsConnectorConfigValidator();
    private final VropsConnectorConfigValidator vropsValidator = new VropsConnectorConfigValidator();
    private final ScomConnectorConfigValidator scomValidator = new ScomConnectorConfigValidator();

    @Test
    void shouldReturnTenantScopedDashboardKpisWhenCountryAllowed() {
        var service = new DashboardService(readModel, guard);
        var kpis = service.kpis(ctx("KW", Set.of("DASHBOARD_READ")), "KW", "PROD");
        assertEquals("KW", kpis.get("countryCode"));
        assertEquals(1, kpis.get("openIncidents"));
    }

    @Test
    void shouldDenyDashboardWhenCrossCountryWithoutPermission() {
        var service = new DashboardService(readModel, guard);
        assertThrows(ForbiddenAccessException.class,
                () -> service.kpis(ctx("KW", Set.of("DASHBOARD_READ")), "BH", "PROD"));
    }

    @Test
    void shouldNotExposePlainConnectorSecretsWhenCreatingConnector() {
        var service = connectorService();
        var request = new UiWriteRequest("BMC Helix", null, null, null, true,
                Map.of(
                        "pluginType", "BMC",
                        "baseUrl", "https://kfh-itom.onbmc.com",
                        "secretsPlain", Map.of("accessKey", "access-key", "accessSecretKey", "access-secret")));
        var created = service.create(ctx("KW", Set.of("CONNECTOR_WRITE")), request);
        assertFalse(created.containsKey("secretsPlain"));
        assertEquals("configured", created.get("secretsMask"));
    }

    @Test
    void shouldReturnBmcConnectorTypeMetadataWhenListingTypes() {
        var service = connectorService();
        var types = service.types(ctx("KW", Set.of("CONNECTOR_READ")));
        assertTrue(types.stream().anyMatch(type -> String.valueOf(type).contains("BMC Helix")));
    }

    @Test
    void shouldNotSeedDummyConnectorsWhenListingConnectorInventory() {
        var service = connectorService();
        var page = service.list(ctx("KW", Set.of("CONNECTOR_READ")), 0, 20);
        assertEquals(0, page.totalElements());
    }

    @Test
    void shouldInstallBmcConnectorAsDisabledPlaceholderBeforeConfiguration() {
        var service = connectorService();
        var request = new UiWriteRequest("BMC Helix KW PROD", null, null, null, true,
                Map.of("pluginType", "BMC", "countryCode", "KW", "environment", "PROD", "installOnly", true));

        var created = service.create(ctx("KW", Set.of("CONNECTOR_WRITE")), request);

        assertEquals("BMC", created.get("pluginType"));
        assertEquals(true, created.get("enabled"));
        assertEquals("PENDING", created.get("configurationStatus"));
        assertEquals("not_configured", created.get("secretsMask"));
        assertFalse(created.containsKey("secretsPlain"));
    }

    @Test
    void shouldRequireSecretsWhenCompletingPendingBmcConnectorConfiguration() {
        var service = connectorService();
        var ctx = ctx("KW", Set.of("CONNECTOR_WRITE"));
        var installed = service.create(ctx, new UiWriteRequest("BMC Helix KW PROD", null, null, null, true,
                Map.of("pluginType", "BMC", "countryCode", "KW", "environment", "PROD", "installOnly", true)));
        var connectorId = UUID.fromString(String.valueOf(installed.get("id")));

        var update = new UiWriteRequest("BMC Helix KW PROD", null, null, null, false,
                Map.of("pluginType", "BMC", "baseUrl", "https://kfh-itom.onbmc.com"));

        assertThrows(ValidationException.class, () -> service.update(ctx, connectorId, update));
    }

    @Test
    void shouldCreateBmcConnectorForSelectedCountryWhenGlobalAdmin() {
        var service = connectorService();
        var request = new UiWriteRequest("BMC Helix BH PROD", null, null, null, true,
                Map.ofEntries(
                        Map.entry("pluginType", "BMC"),
                        Map.entry("countryCode", "BH"),
                        Map.entry("environment", "PROD"),
                        Map.entry("baseUrl", "https://bmc-bh.onbmc.com"),
                        Map.entry("loginEndpoint", "/ims/api/v1/access_keys/login"),
                        Map.entry("eventsEndpoint", "/events-service/api/v1.0/events/msearch"),
                        Map.entry("minutesBack", 60),
                        Map.entry("pageSize", 100),
                        Map.entry("maxEvents", 500),
                        Map.entry("timeoutSeconds", 120),
                        Map.entry("secretsPlain", Map.of("accessKey", "access-key", "accessSecretKey", "access-secret"))));

        var created = service.create(ctx("ALL", Set.of("CONNECTOR_WRITE", "COUNTRY_GLOBAL_VIEW")), request);

        assertEquals("BH", created.get("countryCode"));
        assertEquals("BMC", created.get("pluginType"));
        assertEquals("https://bmc-bh.onbmc.com", created.get("baseUrl"));
        assertFalse(created.containsKey("secretsPlain"));
    }

    @Test
    void shouldRejectBmcConnectorWhenBaseUrlTargetsMetadataIp() {
        var service = connectorService();
        var request = new UiWriteRequest("Unsafe BMC", null, null, null, true,
                Map.of(
                        "pluginType", "BMC",
                        "baseUrl", "https://169.254.169.254",
                        "secretsPlain", Map.of("accessKey", "access-key", "accessSecretKey", "access-secret")));

        assertThrows(ValidationException.class,
                () -> service.create(ctx("KW", Set.of("CONNECTOR_WRITE")), request));
    }

    @Test
    void shouldDenyBmcConnectorCreationForCrossCountryWithoutGlobalPermission() {
        var service = connectorService();
        var request = new UiWriteRequest("BMC Helix BH", null, null, null, true,
                Map.of(
                        "pluginType", "BMC",
                        "countryCode", "BH",
                        "baseUrl", "https://bmc-bh.onbmc.com",
                        "secretsPlain", Map.of("accessKey", "access-key", "accessSecretKey", "access-secret")));

        assertThrows(ForbiddenAccessException.class,
                () -> service.create(ctx("KW", Set.of("CONNECTOR_WRITE")), request));
    }

    @Test
    void shouldKeepInstalledConnectorAfterServiceRefreshWhenPersistenceStoreAvailable() {
        var store = new InMemoryConnectorPersistenceStore();
        var writer = connectorService(store);
        var tenantId = UUID.randomUUID();
        var writeCtx = ctx(tenantId, "KW", Set.of("CONNECTOR_WRITE", "CONNECTOR_READ"));
        var request = new UiWriteRequest("BMC Helix KW PROD", null, null, null, true,
                Map.of("pluginType", "BMC", "countryCode", "KW", "environment", "PROD", "installOnly", true));

        var created = writer.create(writeCtx, request);
        var refreshedService = connectorService(store);

        var listed = refreshedService.list(ctx(tenantId, "KW", Set.of("CONNECTOR_READ")), 0, 20);
        assertEquals(1, listed.totalElements());
        assertEquals(created.get("id"), listed.content().getFirst().get("id"));

        refreshedService.delete(writeCtx, UUID.fromString(String.valueOf(created.get("id"))));

        var afterDelete = refreshedService.list(ctx(tenantId, "KW", Set.of("CONNECTOR_READ")), 0, 20);
        assertEquals(0, afterDelete.totalElements());
    }

    @Test
    void shouldListAllPhysicalCountryConnectorsWhenGlobalCountryScopeSelected() {
        var service = connectorService(new InMemoryConnectorPersistenceStore());
        var tenantId = UUID.randomUUID();
        var writeCtx = ctx(tenantId, "ALL", Set.of("CONNECTOR_WRITE", "CONNECTOR_READ", "COUNTRY_GLOBAL_VIEW"));
        service.create(writeCtx, new UiWriteRequest("BMC Helix KW PROD", null, null, null, true,
                Map.of("pluginType", "BMC", "countryCode", "KW", "environment", "PROD", "installOnly", true)));
        service.create(writeCtx, new UiWriteRequest("BMC Helix BH PROD", null, null, null, true,
                Map.of("pluginType", "BMC", "countryCode", "BH", "environment", "PROD", "installOnly", true)));

        var allCountries = service.list(ctx(tenantId, "ALL", Set.of("CONNECTOR_READ", "COUNTRY_GLOBAL_VIEW")), 0, 20).content();
        var kuwaitOnly = service.list(ctx(tenantId, "KW", Set.of("CONNECTOR_READ")), 0, 20).content();

        assertEquals(2, allCountries.size());
        assertTrue(allCountries.stream().anyMatch(connector -> "KW".equals(connector.get("countryCode"))));
        assertTrue(allCountries.stream().anyMatch(connector -> "BH".equals(connector.get("countryCode"))));
        assertEquals(1, kuwaitOnly.size());
        assertEquals("KW", kuwaitOnly.getFirst().get("countryCode"));
    }

    @Test
    void shouldRejectConnectorInstallWhenPersistenceStoreUnavailable() {
        var service = connectorServiceWithoutPersistence();
        var request = new UiWriteRequest("BMC Helix KW PROD", null, null, null, true,
                Map.of("pluginType", "BMC", "countryCode", "KW", "environment", "PROD", "installOnly", true));

        assertThrows(ServiceUnavailableException.class,
                () -> service.create(ctx("KW", Set.of("CONNECTOR_WRITE")), request));
    }

    @Test
    void shouldRejectPersistedConnectorLookupWhenTenantDoesNotMatch() {
        var store = new InMemoryConnectorPersistenceStore();
        var service = connectorService(store);
        var ownerCtx = ctx("KW", Set.of("CONNECTOR_WRITE", "CONNECTOR_READ"));
        var request = new UiWriteRequest("BMC Helix KW PROD", null, null, null, true,
                Map.of("pluginType", "BMC", "countryCode", "KW", "environment", "PROD", "installOnly", true));
        var created = service.create(ownerCtx, request);
        var connectorId = UUID.fromString(String.valueOf(created.get("id")));

        assertThrows(NotFoundException.class,
                () -> service.get(ctx("KW", Set.of("CONNECTOR_READ")), connectorId));
    }

    @Test
    void shouldTestBmcConnectorUsingSavedConfigurationAndSecrets() {
        var store = new InMemoryConnectorPersistenceStore();
        var liveTester = mock(BmcConnectorLiveTester.class);
        var service = connectorService(store, liveTester);
        var ctx = ctx("KW", Set.of("CONNECTOR_WRITE", "CONNECTOR_TEST"));
        var created = service.create(ctx, new UiWriteRequest("BMC Helix KW PROD", null, null, null, true,
                Map.of("pluginType", "BMC",
                        "baseUrl", "https://kfh-itom.onbmc.com",
                        "secretsPlain", Map.of("accessKey", "access-key", "accessSecretKey", "access-secret"))));
        when(liveTester.test(any(TenantContext.class), any(), any()))
                .thenReturn(Map.of("pass", true, "message", "ready", "steps", List.of()));

        var result = service.test(ctx, UUID.fromString(String.valueOf(created.get("id"))));

        assertEquals(true, result.get("pass"));
        assertFalse(result.toString().contains("access-secret"));
        verify(liveTester).test(any(TenantContext.class), argThat(connector -> "https://kfh-itom.onbmc.com".equals(connector.get("baseUrl"))),
                argThat(secrets -> "access-key".equals(secrets.get("accessKey"))
                        && "access-secret".equals(secrets.get("accessSecretKey"))));
    }

    @Test
    void shouldUseDisabledTlsVerificationWhenTestingSavedAppDynamicsConnector() {
        var store = new InMemoryConnectorPersistenceStore();
        var liveTester = mock(AppDynamicsConnectorLiveTester.class);
        var service = connectorService(store, null, liveTester);
        var ctx = ctx("KW", Set.of("CONNECTOR_WRITE", "CONNECTOR_TEST"));
        var created = service.create(ctx, new UiWriteRequest("AppDynamics KW PROD", null, null, null, true,
                Map.of("pluginType", "APPDYNAMICS",
                        "controllerUrl", "https://appd.example.com/controller",
                        "verifySsl", true,
                        "secretsPlain", Map.of("username", "appd-user", "password", "appd-password"))));
        var connectorId = UUID.fromString(String.valueOf(created.get("id")));
        service.update(ctx, connectorId, new UiWriteRequest("AppDynamics KW PROD", null, null, null, true,
                Map.of("pluginType", "APPDYNAMICS",
                        "controllerUrl", "https://appd.example.com/controller",
                        "verifySsl", false)));
        when(liveTester.test(any(TenantContext.class), any(), any()))
                .thenReturn(Map.of("pass", true, "verifySsl", false, "message", "ready", "steps", List.of()));

        var result = service.test(ctx, connectorId);

        assertEquals(true, result.get("pass"));
        verify(liveTester).test(any(TenantContext.class),
                argThat(connector -> Boolean.FALSE.equals(connector.get("verifySsl"))),
                argThat(secrets -> "appd-user".equals(secrets.get("username"))
                        && "appd-password".equals(secrets.get("password"))));
    }

    @Test
    void shouldRetainConnectorSecretsWhenConfigurationUpdatedWithoutReenteringSecrets() {
        var store = new InMemoryConnectorPersistenceStore();
        var liveTester = mock(BmcConnectorLiveTester.class);
        var service = connectorService(store, liveTester);
        var ctx = ctx("KW", Set.of("CONNECTOR_WRITE", "CONNECTOR_TEST"));
        var created = service.create(ctx, new UiWriteRequest("BMC Helix KW PROD", null, null, null, true,
                Map.of("pluginType", "BMC",
                        "baseUrl", "https://kfh-itom.onbmc.com",
                        "secretsPlain", Map.of("accessKey", "access-key", "accessSecretKey", "access-secret"))));
        var connectorId = UUID.fromString(String.valueOf(created.get("id")));
        service.update(ctx, connectorId, new UiWriteRequest("BMC Helix KW PROD", null, null, null, true,
                Map.of("pluginType", "BMC", "baseUrl", "https://kfh-itom.onbmc.com", "minutesBack", 30)));
        when(liveTester.test(any(TenantContext.class), any(), any()))
                .thenReturn(Map.of("pass", true, "message", "ready", "steps", List.of()));

        var result = service.test(ctx, connectorId);

        assertEquals(true, result.get("pass"));
        verify(liveTester).test(any(TenantContext.class),
                argThat(connector -> Integer.valueOf(30).equals(connector.get("minutesBack"))),
                argThat(secrets -> "access-key".equals(secrets.get("accessKey"))
                        && "access-secret".equals(secrets.get("accessSecretKey"))));
    }

    @Test
    void shouldReturnFailedConnectorTestWhenSavedSecretsCannotBeDecryptedWithCurrentMasterKey() {
        var ctx = ctx("KW", Set.of("CONNECTOR_TEST"));
        var connectorId = UUID.randomUUID();
        var row = new LinkedHashMap<String, Object>();
        row.put("id", connectorId.toString());
        row.put("tenantId", ctx.tenantId().toString());
        row.put("countryCode", "KW");
        row.put("environment", "PROD");
        row.put("pluginType", "BMC");
        row.put("type", "BMC");
        row.put("name", "BMC Helix KW PROD");
        row.put("enabled", true);
        row.put("baseUrl", "https://kfh-itom.onbmc.com");
        var store = new DecryptionFailureConnectorStore(connectorId, row);
        var liveTester = mock(BmcConnectorLiveTester.class);
        var service = connectorService(store, liveTester);

        var result = service.test(ctx, connectorId);

        assertEquals(false, result.get("pass"));
        assertEquals("FAIL", result.get("status"));
        assertEquals("SECRET_DECRYPTION_FAILED", result.get("errorCode"));
        assertEquals(true, result.get("credentialRecoveryRequired"));
        assertTrue(String.valueOf(result.get("message")).contains("Restore the original stable KFH_AIOPS_SECRET_KEY"));
        assertTrue(String.valueOf(result.get("message")).contains("re-enter all credential fields"));
        assertEquals("FAIL", row.get("lastTestStatus"));
        assertEquals("PENDING", row.get("configurationStatus"));
        assertEquals("needs_reentry", row.get("secretsMask"));
        verify(liveTester, never()).test(any(), any(), any());
    }

    @Test
    void shouldInstallAndConfigureAppDynamicsConnectorWithEncryptedBasicAuth() {
        var service = connectorService();
        var ctx = ctx("KW", Set.of("CONNECTOR_WRITE"));
        var installed = service.create(ctx, new UiWriteRequest("AppDynamics KW PROD", null, null, null, true,
                Map.of("pluginType", "APPDYNAMICS", "countryCode", "KW", "environment", "PROD", "installOnly", true)));
        var connectorId = UUID.fromString(String.valueOf(installed.get("id")));

        var updated = service.update(ctx, connectorId, new UiWriteRequest("AppDynamics KW PROD", null, null, null, true,
                Map.of(
                        "pluginType", "APPDYNAMICS",
                        "controllerUrl", "https://appd.example.com/controller",
                        "durationMinutes", 60,
                        "maxWorkers", 15,
                        "timeoutSeconds", 120,
                        "secretsPlain", Map.of("username", "appd-user", "password", "appd-password"))));

        assertEquals("APPDYNAMICS", updated.get("pluginType"));
        assertEquals("https://appd.example.com/controller", updated.get("controllerUrl"));
        assertEquals("configured", updated.get("secretsMask"));
        assertFalse(updated.containsKey("secretsPlain"));
        assertFalse(updated.toString().contains("appd-password"));
    }

    @Test
    void shouldTestAppDynamicsConnectorUsingSavedConfigurationAndSecrets() {
        var store = new InMemoryConnectorPersistenceStore();
        var liveTester = mock(AppDynamicsConnectorLiveTester.class);
        var service = connectorService(store, null, liveTester);
        var ctx = ctx("KW", Set.of("CONNECTOR_WRITE", "CONNECTOR_TEST"));
        var created = service.create(ctx, new UiWriteRequest("AppDynamics KW PROD", null, null, null, true,
                Map.of("pluginType", "APPDYNAMICS",
                        "controllerUrl", "https://appd.example.com/controller",
                        "secretsPlain", Map.of("username", "appd-user", "password", "appd-password"))));
        when(liveTester.test(any(TenantContext.class), any(), any()))
                .thenReturn(Map.of("pass", true, "message", "ready", "steps", List.of()));

        var result = service.test(ctx, UUID.fromString(String.valueOf(created.get("id"))));

        assertEquals(true, result.get("pass"));
        assertFalse(result.toString().contains("appd-password"));
        verify(liveTester).test(any(TenantContext.class),
                argThat(connector -> "https://appd.example.com/controller".equals(connector.get("controllerUrl"))),
                argThat(secrets -> "appd-user".equals(secrets.get("username"))
                        && "appd-password".equals(secrets.get("password"))));
    }

    @Test
    void shouldSaveBasicAuthCredentialsWhenSubmittedAsTopLevelFields() {
        var store = new InMemoryConnectorPersistenceStore();
        var liveTester = mock(AppDynamicsConnectorLiveTester.class);
        var service = connectorService(store, null, liveTester);
        var ctx = ctx("KW", Set.of("CONNECTOR_WRITE", "CONNECTOR_TEST"));

        var created = service.create(ctx, new UiWriteRequest("AppDynamics KW PROD", null, null, null, true,
                Map.of("pluginType", "APPDYNAMICS",
                        "controllerUrl", "https://appd.example.com/controller",
                        "username", "appd-user",
                        "password", "appd-password")));
        when(liveTester.test(any(TenantContext.class), any(), any()))
                .thenReturn(Map.of("pass", true, "message", "ready", "steps", List.of()));

        var result = service.test(ctx, UUID.fromString(String.valueOf(created.get("id"))));

        assertEquals(true, result.get("pass"));
        assertEquals("configured", created.get("secretsMask"));
        assertFalse(created.containsKey("username"));
        assertFalse(created.containsKey("password"));
        assertFalse(created.toString().contains("appd-password"));
        verify(liveTester).test(any(TenantContext.class), any(),
                argThat(secrets -> "appd-user".equals(secrets.get("username"))
                        && "appd-password".equals(secrets.get("password"))));
    }

    @Test
    void shouldRetainSavedBasicAuthPasswordWhenOnlyUsernameRotated() {
        var store = new InMemoryConnectorPersistenceStore();
        var liveTester = mock(AppDynamicsConnectorLiveTester.class);
        var service = connectorService(store, null, liveTester);
        var ctx = ctx("KW", Set.of("CONNECTOR_WRITE", "CONNECTOR_TEST"));
        var created = service.create(ctx, new UiWriteRequest("AppDynamics KW PROD", null, null, null, true,
                Map.of("pluginType", "APPDYNAMICS",
                        "controllerUrl", "https://appd.example.com/controller",
                        "secretsPlain", Map.of("username", "appd-user", "password", "appd-password"))));
        var connectorId = UUID.fromString(String.valueOf(created.get("id")));
        service.update(ctx, connectorId, new UiWriteRequest("AppDynamics KW PROD", null, null, null, true,
                Map.of("pluginType", "APPDYNAMICS",
                        "controllerUrl", "https://appd.example.com/controller",
                        "username", "appd-user-rotated")));
        when(liveTester.test(any(TenantContext.class), any(), any()))
                .thenReturn(Map.of("pass", true, "message", "ready", "steps", List.of()));

        var result = service.test(ctx, connectorId);

        assertEquals(true, result.get("pass"));
        verify(liveTester).test(any(TenantContext.class), any(),
                argThat(secrets -> "appd-user-rotated".equals(secrets.get("username"))
                        && "appd-password".equals(secrets.get("password"))));
    }

    @Test
    void shouldInstallAndConfigureVropsConnectorWithEncryptedTokenAuth() {
        var service = connectorService();
        var ctx = ctx("KW", Set.of("CONNECTOR_WRITE"));
        var installed = service.create(ctx, new UiWriteRequest("vROps KW PROD", null, null, null, true,
                Map.of("pluginType", "VROPS", "countryCode", "KW", "environment", "PROD", "installOnly", true)));
        var connectorId = UUID.fromString(String.valueOf(installed.get("id")));

        var updated = service.update(ctx, connectorId, new UiWriteRequest("vROps KW PROD", null, null, null, true,
                Map.of(
                        "pluginType", "VROPS",
                        "host", "vrops.example.com",
                        "authSource", "KFH AD",
                        "hours", 1,
                        "pageSize", 1000,
                        "maxPages", 200,
                        "maxWorkers", 12,
                        "timeoutSeconds", 120,
                        "secretsPlain", Map.of("username", "vrops-user", "password", "vrops-password"))));

        assertEquals("VROPS", updated.get("pluginType"));
        assertEquals("vrops.example.com", updated.get("host"));
        assertEquals("https://vrops.example.com/suite-api/api", updated.get("baseUrl"));
        assertEquals("configured", updated.get("secretsMask"));
        assertFalse(updated.containsKey("secretsPlain"));
        assertFalse(updated.toString().contains("vrops-password"));
    }

    @Test
    void shouldTestVropsConnectorUsingSavedConfigurationAndSecrets() {
        var store = new InMemoryConnectorPersistenceStore();
        var liveTester = mock(VropsConnectorLiveTester.class);
        var service = connectorService(store, null, null, liveTester);
        var ctx = ctx("KW", Set.of("CONNECTOR_WRITE", "CONNECTOR_TEST"));
        var created = service.create(ctx, new UiWriteRequest("vROps KW PROD", null, null, null, true,
                Map.of("pluginType", "VROPS",
                        "host", "vrops.example.com",
                        "authSource", "KFH AD",
                        "secretsPlain", Map.of("username", "vrops-user", "password", "vrops-password"))));
        when(liveTester.test(any(TenantContext.class), any(), any()))
                .thenReturn(Map.of("pass", true, "message", "ready", "steps", List.of()));

        var result = service.test(ctx, UUID.fromString(String.valueOf(created.get("id"))));

        assertEquals(true, result.get("pass"));
        assertFalse(result.toString().contains("vrops-password"));
        verify(liveTester).test(any(TenantContext.class),
                argThat(connector -> "https://vrops.example.com/suite-api/api".equals(connector.get("baseUrl"))),
                argThat(secrets -> "vrops-user".equals(secrets.get("username"))
                        && "vrops-password".equals(secrets.get("password"))));
    }

    @Test
    void shouldInstallAndConfigureScomConnectorWithEncryptedWinRmCredentials() {
        var service = connectorService();
        var ctx = ctx("KW", Set.of("CONNECTOR_WRITE"));
        var installed = service.create(ctx, new UiWriteRequest("SCOM KW PROD", null, null, null, true,
                Map.of("pluginType", "SCOM", "countryCode", "KW", "environment", "PROD", "installOnly", true)));
        var connectorId = UUID.fromString(String.valueOf(installed.get("id")));

        var updated = service.update(ctx, connectorId, new UiWriteRequest("SCOM KW PROD", null, null, null, true,
                Map.of(
                        "pluginType", "SCOM",
                        "managementServer", "dcvscoap12.corp.kfh.kw",
                        "domain", "corp.kfh.kw",
                        "winrmPort", 5986,
                        "authMethod", "Kerberos",
                        "hoursBack", 1,
                        "connectionTimeoutSeconds", 60,
                        "secretsPlain", Map.of("username", "scom-user", "password", "scom-password"))));

        assertEquals("SCOM", updated.get("pluginType"));
        assertEquals("dcvscoap12.corp.kfh.kw", updated.get("managementServer"));
        assertEquals("https://dcvscoap12.corp.kfh.kw:5986/wsman", updated.get("baseUrl"));
        assertEquals("configured", updated.get("secretsMask"));
        assertFalse(updated.containsKey("secretsPlain"));
        assertFalse(updated.toString().contains("scom-password"));
    }

    @Test
    void shouldTestScomConnectorUsingSavedConfigurationAndSecrets() {
        var store = new InMemoryConnectorPersistenceStore();
        var liveTester = mock(ScomConnectorLiveTester.class);
        var service = connectorService(store, null, null, null, liveTester);
        var ctx = ctx("KW", Set.of("CONNECTOR_WRITE", "CONNECTOR_TEST"));
        var created = service.create(ctx, new UiWriteRequest("SCOM KW PROD", null, null, null, true,
                Map.of("pluginType", "SCOM",
                        "managementServer", "dcvscoap12.corp.kfh.kw",
                        "domain", "corp.kfh.kw",
                        "secretsPlain", Map.of("username", "scom-user", "password", "scom-password"))));
        when(liveTester.test(any(TenantContext.class), any(), any()))
                .thenReturn(Map.of("pass", true, "message", "ready", "steps", List.of()));

        var result = service.test(ctx, UUID.fromString(String.valueOf(created.get("id"))));

        assertEquals(true, result.get("pass"));
        assertFalse(result.toString().contains("scom-password"));
        verify(liveTester).test(any(TenantContext.class),
                argThat(connector -> "https://dcvscoap12.corp.kfh.kw:5986/wsman".equals(connector.get("endpointUrl"))),
                argThat(secrets -> "scom-user".equals(secrets.get("username"))
                        && "scom-password".equals(secrets.get("password"))));
    }

    @Test
    void shouldMarkEnabledConnectorDownWhenLiveTestFails() {
        var store = new InMemoryConnectorPersistenceStore();
        var liveTester = mock(BmcConnectorLiveTester.class);
        var service = connectorService(store, liveTester);
        var ctx = ctx("KW", Set.of("CONNECTOR_WRITE", "CONNECTOR_TEST", "CONNECTOR_READ"));
        var created = service.create(ctx, new UiWriteRequest("BMC Helix KW PROD", null, null, null, true,
                Map.of("pluginType", "BMC",
                        "baseUrl", "https://kfh-itom.onbmc.com",
                        "secretsPlain", Map.of("accessKey", "access-key", "accessSecretKey", "access-secret"))));
        when(liveTester.test(any(TenantContext.class), any(), any()))
                .thenReturn(Map.of("pass", false, "status", "FAIL", "message", "BMC endpoint is offline", "steps", List.of()));

        var result = service.test(ctx, UUID.fromString(String.valueOf(created.get("id"))));
        var listed = service.list(ctx, 0, 20).content().getFirst();

        assertEquals(false, result.get("pass"));
        assertEquals("FAIL", listed.get("lastTestStatus"));
        assertEquals("DOWN", listed.get("health"));
        assertFalse(listed.toString().contains("access-secret"));
    }

    @Test
    void shouldHeartbeatEnabledConnectorsAndRecordDownConnectors() {
        var store = new InMemoryConnectorPersistenceStore();
        var bmcLiveTester = mock(BmcConnectorLiveTester.class);
        var appDynamicsLiveTester = mock(AppDynamicsConnectorLiveTester.class);
        var vropsLiveTester = mock(VropsConnectorLiveTester.class);
        var service = connectorService(store, bmcLiveTester, appDynamicsLiveTester, vropsLiveTester);
        var ctx = ctx("KW", Set.of("CONNECTOR_WRITE", "CONNECTOR_TEST", "CONNECTOR_READ"));
        service.create(ctx, new UiWriteRequest("BMC Helix KW PROD", null, null, null, true,
                Map.of("pluginType", "BMC", "baseUrl", "https://kfh-itom.onbmc.com",
                        "secretsPlain", Map.of("accessKey", "access-key", "accessSecretKey", "access-secret"))));
        service.create(ctx, new UiWriteRequest("AppDynamics KW PROD", null, null, null, true,
                Map.of("pluginType", "APPDYNAMICS", "controllerUrl", "https://appd.example.com/controller",
                        "secretsPlain", Map.of("username", "appd-user", "password", "appd-password"))));
        service.create(ctx, new UiWriteRequest("vROps KW PROD", null, null, null, true,
                Map.of("pluginType", "VROPS", "host", "vrops.example.com", "authSource", "KFH AD",
                        "secretsPlain", Map.of("username", "vrops-user", "password", "vrops-password"))));
        when(bmcLiveTester.test(any(TenantContext.class), any(), any()))
                .thenReturn(Map.of("pass", false, "status", "FAIL", "message", "BMC endpoint is offline", "steps", List.of()));
        when(appDynamicsLiveTester.test(any(TenantContext.class), any(), any()))
                .thenReturn(Map.of("pass", true, "status", "PASS", "message", "AppDynamics ready", "steps", List.of()));
        when(vropsLiveTester.test(any(TenantContext.class), any(), any()))
                .thenReturn(Map.of("pass", true, "status", "PASS", "message", "vROps ready", "steps", List.of()));

        var heartbeat = service.heartbeat(ctx);
        var rows = service.list(ctx, 0, 20).content();

        assertEquals(3, heartbeat.get("totalEnabled"));
        assertEquals(2L, heartbeat.get("healthy"));
        assertEquals(1L, heartbeat.get("down"));
        assertTrue(rows.stream().anyMatch(row -> "DOWN".equals(row.get("health"))));
        assertTrue(rows.stream().anyMatch(row -> "HEALTHY".equals(row.get("health"))));
        assertFalse(heartbeat.toString().contains("access-secret"));
        assertFalse(heartbeat.toString().contains("appd-password"));
        assertFalse(heartbeat.toString().contains("vrops-password"));
    }

    @Test
    void shouldListOnlyCurrentCountryUsersWhenCountryScopeSelected() {
        var repository = mock(IdentityJdbcRepository.class);
        var service = new IdentityAdminService(readModel, audit, guard, repository);
        when(repository.users(any(UUID.class), eq("KW"), eq("PROD")))
                .thenReturn(List.of(Map.of("countryCode", "KW", "username", "kw.operator")));
        when(repository.users(any(UUID.class), eq("BH"), eq("PROD")))
                .thenReturn(List.of(Map.of("countryCode", "BH", "username", "bh.operator")));

        var kwUsers = service.users(ctx("KW", Set.of("IDENTITY_READ")), "KW", "PROD", 0, 100);
        var bhUsers = service.users(ctx("BH", Set.of("IDENTITY_READ")), "BH", "PROD", 0, 100);

        assertEquals(1, kwUsers.totalElements());
        assertEquals(1, bhUsers.totalElements());
        assertEquals("KW", kwUsers.content().getFirst().get("countryCode"));
        assertEquals("BH", bhUsers.content().getFirst().get("countryCode"));
    }

    @Test
    void shouldDenyCrossCountryUserListingWithoutGlobalPermission() {
        var service = new IdentityAdminService(readModel, audit, guard, mock(IdentityJdbcRepository.class));
        assertThrows(ForbiddenAccessException.class,
                () -> service.users(ctx("KW", Set.of("IDENTITY_READ")), "BH", "PROD", 0, 20));
    }

    @Test
    void shouldIgnoreUserPayloadCountryWhenCreatingScopedUser() {
        var repository = mock(IdentityJdbcRepository.class);
        var service = new IdentityAdminService(readModel, audit, guard, repository);
        var created = Map.<String, Object>of("id", UUID.randomUUID(), "countryCode", "BH", "environment", "PROD");
        when(repository.createUser(any(TenantContext.class), anyString(), any())).thenReturn(created);

        service.createUser(ctx("BH", Set.of("IDENTITY_WRITE")),
                new UiWriteRequest("Scoped Operator", null, null, null, true,
                        Map.of("countryCode", "KW", "environment", "DEV")));

        verify(repository).createUser(any(TenantContext.class), eq("Scoped Operator"), any());
    }

    @Test
    void shouldNotExposePasswordFieldsWhenCreatingUser() {
        var repository = mock(IdentityJdbcRepository.class);
        var service = new IdentityAdminService(readModel, audit, guard, repository);
        when(repository.createUser(any(TenantContext.class), anyString(), any()))
                .thenReturn(Map.of("id", UUID.randomUUID(), "username", "secure.operator"));

        var created = service.createUser(ctx("KW", Set.of("IDENTITY_WRITE")),
                new UiWriteRequest("Secure Operator", null, null, null, true,
                        Map.of("username", "secure.operator", "password", "DoNotReturn123!",
                                "confirmPassword", "DoNotReturn123!")));

        assertFalse(created.containsKey("password"));
        assertFalse(created.containsKey("confirmPassword"));
        assertFalse(created.containsKey("passwordHash"));
    }

    @Test
    void shouldUpdateUserProfileWhenEditSubmitted() {
        var repository = mock(IdentityJdbcRepository.class);
        var service = new IdentityAdminService(readModel, audit, guard, repository);
        var userId = UUID.randomUUID();
        when(repository.findUser(any(UUID.class), eq(userId)))
                .thenReturn(Optional.of(Map.of("id", userId.toString(), "countryCode", "KW")));
        when(repository.updateUser(any(UUID.class), eq(userId), eq("Ahmed Salem"), any()))
                .thenReturn(Map.of("id", userId.toString(), "username", "ahmed", "countryCode", "KW", "status", "ACTIVE"));

        var updated = service.updateUser(ctx("KW", Set.of("IDENTITY_READ", "IDENTITY_WRITE")), userId,
                new UiWriteRequest("Ahmed Salem", null, null, "Active", true,
                        Map.of("username", "ahmed", "email", "92338@kfh.com", "roleIds", List.of("COUNTRY_ADMIN"))));

        assertEquals("ahmed", updated.get("username"));
        verify(repository).updateUser(any(UUID.class), eq(userId), eq("Ahmed Salem"), any());
    }

    @Test
    void shouldUpdateUserCountryWhenNewCountryIsAllowed() {
        var repository = mock(IdentityJdbcRepository.class);
        var service = new IdentityAdminService(readModel, audit, guard, repository);
        var userId = UUID.randomUUID();
        when(repository.findUser(any(UUID.class), eq(userId)))
                .thenReturn(Optional.of(Map.of("id", userId.toString(), "countryCode", "KW")));
        when(repository.updateUser(any(UUID.class), eq(userId), eq("Ahmed Salem"), any()))
                .thenReturn(Map.of("id", userId.toString(), "username", "ahmed", "countryCode", "BH", "status", "ACTIVE"));

        var updated = service.updateUser(ctx("KW", Set.of("IDENTITY_READ", "IDENTITY_WRITE", "COUNTRY_GLOBAL_VIEW")), userId,
                new UiWriteRequest("Ahmed Salem", null, null, "Active", true,
                        Map.of("username", "ahmed", "email", "92338@kfh.com", "countryCode", "BH", "roleIds", List.of("ADMIN"))));

        assertEquals("BH", updated.get("countryCode"));
        verify(repository).updateUser(any(UUID.class), eq(userId), eq("Ahmed Salem"),
                argThat(fields -> "BH".equals(fields.get("countryCode"))
                        && List.of("COUNTRY_ADMIN").equals(fields.get("roleIds"))));
    }

    @Test
    void shouldDenyUserCountryUpdateWhenNewCountryIsNotAllowed() {
        var repository = mock(IdentityJdbcRepository.class);
        var service = new IdentityAdminService(readModel, audit, guard, repository);
        var userId = UUID.randomUUID();
        when(repository.findUser(any(UUID.class), eq(userId)))
                .thenReturn(Optional.of(Map.of("id", userId.toString(), "countryCode", "KW")));

        assertThrows(ForbiddenAccessException.class, () -> service.updateUser(ctx("KW", Set.of("IDENTITY_READ", "IDENTITY_WRITE")), userId,
                new UiWriteRequest("Ahmed Salem", null, null, "Active", true,
                        Map.of("username", "ahmed", "countryCode", "BH", "roleIds", List.of("OPERATOR")))));

        verify(repository, never()).updateUser(any(UUID.class), eq(userId), anyString(), any());
    }

    @Test
    void shouldCreateAllCountriesUserWhenGlobalScopeAllowed() {
        var repository = mock(IdentityJdbcRepository.class);
        var service = new IdentityAdminService(readModel, audit, guard, repository);
        when(repository.createUser(any(TenantContext.class), anyString(), any()))
                .thenReturn(Map.of("id", UUID.randomUUID(), "countryCode", "ALL"));

        var created = service.createUser(
                ctx("KW", Set.of("IDENTITY_WRITE", "COUNTRY_GLOBAL_VIEW")),
                new UiWriteRequest("Global Operator", null, null, null, true,
                        Map.of("username", "global.operator", "countryCode", "ALL", "roleIds", List.of("ADMIN"))));

        assertEquals("ALL", created.get("countryCode"));
        verify(repository).createUser(argThat(context -> "ALL".equals(context.countryCode())), eq("Global Operator"),
                argThat(fields -> List.of("GLOBAL_ADMIN").equals(fields.get("roleIds"))));
    }

    @Test
    void shouldCreateCountryAdminWhenAdminRoleIsCountryScoped() {
        var repository = mock(IdentityJdbcRepository.class);
        var service = new IdentityAdminService(readModel, audit, guard, repository);
        when(repository.createUser(any(TenantContext.class), anyString(), any()))
                .thenReturn(Map.of("id", UUID.randomUUID(), "countryCode", "KW"));

        service.createUser(ctx("KW", Set.of("IDENTITY_WRITE")),
                new UiWriteRequest("Country Admin", null, null, null, true,
                        Map.of("username", "kw.admin", "roleIds", List.of("ADMIN"))));

        verify(repository).createUser(argThat(context -> "KW".equals(context.countryCode())), eq("Country Admin"),
                argThat(fields -> List.of("COUNTRY_ADMIN").equals(fields.get("roleIds"))));
    }

    @Test
    void shouldResetUserPasswordThroughIdentityRepository() {
        var repository = mock(IdentityJdbcRepository.class);
        var service = new IdentityAdminService(readModel, audit, guard, repository);
        var userId = UUID.randomUUID();
        when(repository.findUser(any(UUID.class), eq(userId)))
                .thenReturn(Optional.of(Map.of("id", userId.toString(), "countryCode", "KW")));
        when(repository.updatePassword(any(UUID.class), eq(userId), any()))
                .thenReturn(Map.of("id", userId.toString(), "countryCode", "KW"));

        var updated = service.resetPassword(ctx("KW", Set.of("IDENTITY_READ", "IDENTITY_WRITE")), userId,
                new UiWriteRequest("Country Admin", null, null, null, true,
                        Map.of("password", "New-Strong-Password-123")));

        assertEquals(userId.toString(), updated.get("id"));
        verify(repository).updatePassword(any(UUID.class), eq(userId),
                argThat(fields -> "New-Strong-Password-123".equals(fields.get("password"))));
    }

    @Test
    void shouldDenyAllCountriesUserCreationWithoutGlobalScope() {
        var service = new IdentityAdminService(readModel, audit, guard, mock(IdentityJdbcRepository.class));
        assertThrows(ForbiddenAccessException.class, () -> service.createUser(ctx("ALL", Set.of("IDENTITY_WRITE")),
                new UiWriteRequest("Blocked Global Operator", null, null, null, true,
                        Map.of("username", "blocked.global"))));
    }

    private static TenantContext ctx(String country, Set<String> permissions) {
        return ctx(UUID.randomUUID(), country, permissions);
    }

    private static TenantContext ctx(UUID tenantId, String country, Set<String> permissions) {
        return new TenantContext(tenantId, UUID.randomUUID(), country, "PROD", "test-corr", permissions);
    }

    private ConnectorService connectorService() {
        return connectorService(new InMemoryConnectorPersistenceStore());
    }

    private ConnectorService connectorServiceWithoutPersistence() {
        return new ConnectorService(readModel, guard, audit, connectorCatalog, bmcValidator);
    }

    private ConnectorService connectorService(ConnectorPersistenceStore store) {
        return connectorService(store, null, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private ConnectorService connectorService(ConnectorPersistenceStore store, BmcConnectorLiveTester liveTester) {
        return connectorService(store, liveTester, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private ConnectorService connectorService(ConnectorPersistenceStore store, BmcConnectorLiveTester liveTester,
            AppDynamicsConnectorLiveTester appDynamicsLiveTester) {
        return connectorService(store, liveTester, appDynamicsLiveTester, null, null);
    }

    @SuppressWarnings("unchecked")
    private ConnectorService connectorService(ConnectorPersistenceStore store, BmcConnectorLiveTester liveTester,
            AppDynamicsConnectorLiveTester appDynamicsLiveTester, VropsConnectorLiveTester vropsLiveTester) {
        return connectorService(store, liveTester, appDynamicsLiveTester, vropsLiveTester, null);
    }

    @SuppressWarnings("unchecked")
    private ConnectorService connectorService(ConnectorPersistenceStore store, BmcConnectorLiveTester liveTester,
            AppDynamicsConnectorLiveTester appDynamicsLiveTester, VropsConnectorLiveTester vropsLiveTester,
            ScomConnectorLiveTester scomLiveTester) {
        ObjectProvider<ConnectorPersistenceStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(store);
        ObjectProvider<BmcConnectorLiveTester> testerProvider = mock(ObjectProvider.class);
        when(testerProvider.getIfAvailable()).thenReturn(liveTester);
        ObjectProvider<AppDynamicsConnectorLiveTester> appDynamicsTesterProvider = mock(ObjectProvider.class);
        when(appDynamicsTesterProvider.getIfAvailable()).thenReturn(appDynamicsLiveTester);
        ObjectProvider<VropsConnectorLiveTester> vropsTesterProvider = mock(ObjectProvider.class);
        when(vropsTesterProvider.getIfAvailable()).thenReturn(vropsLiveTester);
        ObjectProvider<ScomConnectorLiveTester> scomTesterProvider = mock(ObjectProvider.class);
        when(scomTesterProvider.getIfAvailable()).thenReturn(scomLiveTester);
        return new ConnectorService(new CommandCenterReadModel(), guard, audit, connectorCatalog, bmcValidator,
                appDynamicsValidator, vropsValidator, scomValidator, provider, testerProvider, appDynamicsTesterProvider,
                vropsTesterProvider, scomTesterProvider);
    }

    private static final class DecryptionFailureConnectorStore implements ConnectorPersistenceStore {
        private final UUID connectorId;
        private final Map<String, Object> row;

        private DecryptionFailureConnectorStore(UUID connectorId, Map<String, Object> row) {
            this.connectorId = connectorId;
            this.row = row;
        }

        @Override
        public List<Map<String, Object>> list(TenantContext ctx) {
            return List.of(row);
        }

        @Override
        public Optional<Map<String, Object>> find(UUID id) {
            return connectorId.equals(id) ? Optional.of(row) : Optional.empty();
        }

        @Override
        public Map<String, Object> create(TenantContext ctx, String countryCode, String environment, String name,
                String pluginType, boolean enabled, Map<String, Object> fields) {
            return Map.of();
        }

        @Override
        public Map<String, Object> update(UUID id, Map<String, Object> fields) {
            row.putAll(fields);
            return row;
        }

        @Override
        public Optional<Map<String, String>> secrets(UUID id) {
            throw new ServiceUnavailableException("SECRET_DECRYPTION_FAILED", "Connector secret decryption failed");
        }

        @Override
        public void recordTestResult(UUID id, boolean pass, Map<String, Object> result) {
            row.put("lastTestStatus", pass ? "PASS" : "FAIL");
            row.put("lastTestMessage", result.get("message"));
            row.put("lastTestErrorCode", result.get("errorCode"));
            if (Boolean.TRUE.equals(result.get("credentialRecoveryRequired"))) {
                row.put("credentialRecoveryRequired", true);
                row.put("configurationStatus", "PENDING");
                row.put("secretsMask", "needs_reentry");
            }
        }

        @Override
        public void delete(UUID id) {
            row.clear();
        }
    }

    private static final class InMemoryConnectorPersistenceStore implements ConnectorPersistenceStore {
        private final Map<UUID, Map<String, Object>> rows = new ConcurrentHashMap<>();
        private final Map<UUID, Map<String, String>> secrets = new ConcurrentHashMap<>();

        @Override
        public List<Map<String, Object>> list(TenantContext ctx) {
            return rows.values().stream()
                    .filter(row -> ctx.tenantId().toString().equals(row.get("tenantId")))
                    .filter(row -> "ALL".equals(ctx.countryCode()) || ctx.countryCode().equals(row.get("countryCode")))
                    .filter(row -> ctx.environment().equals(row.get("environment")))
                    .<Map<String, Object>>map(LinkedHashMap::new)
                    .toList();
        }

        @Override
        public Optional<Map<String, Object>> find(UUID id) {
            return Optional.ofNullable(rows.get(id)).map(LinkedHashMap::new);
        }

        @Override
        public Map<String, Object> create(TenantContext ctx, String countryCode, String environment,
                String name, String pluginType, boolean enabled, Map<String, Object> fields) {
            var id = UUID.randomUUID();
            var row = new LinkedHashMap<String, Object>();
            row.putAll(fields);
            storeSecrets(id, row.remove("secretsPlain"));
            row.put("id", id.toString());
            row.put("tenantId", ctx.tenantId().toString());
            row.put("countryCode", countryCode);
            row.put("environment", environment);
            row.put("name", name);
            row.put("pluginType", pluginType);
            row.put("enabled", enabled);
            rows.put(id, row);
            return new LinkedHashMap<>(row);
        }

        @Override
        public Map<String, Object> update(UUID id, Map<String, Object> fields) {
            rows.computeIfPresent(id, (key, row) -> {
                row.putAll(fields);
                storeSecrets(id, row.remove("secretsPlain"));
                return row;
            });
            return find(id).orElse(Map.of());
        }

        @Override
        public Optional<Map<String, String>> secrets(UUID id) {
            return Optional.ofNullable(secrets.get(id)).map(LinkedHashMap::new);
        }

        @Override
        public void recordTestResult(UUID id, boolean pass, Map<String, Object> result) {
            rows.computeIfPresent(id, (key, row) -> {
                row.put("lastTestStatus", pass ? "PASS" : "FAIL");
                row.put("health", pass ? "HEALTHY" : "DOWN");
                if (result != null && result.get("message") != null) {
                    row.put("lastTestMessage", result.get("message"));
                }
                return row;
            });
        }

        @Override
        public void delete(UUID id) {
            rows.remove(id);
            secrets.remove(id);
        }

        private void storeSecrets(UUID id, Object value) {
            if (!(value instanceof Map<?, ?> raw)) {
                return;
            }
            var current = new LinkedHashMap<>(secrets.getOrDefault(id, Map.of()));
            raw.forEach((key, secret) -> current.put(String.valueOf(key), String.valueOf(secret)));
            secrets.put(id, current);
        }
    }
}

