package org.kfh.aiops.commandcenter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConnectorStaticUiTest {

    private static final Path CONNECTORS_JS = Path.of("src/main/resources/static/pages/connectors/connectors.js");
    private static final Path CONFIG_JS = Path.of("src/main/resources/static/shared/js/config.js");

    @Test
    void shouldExposeConfigurationEditActionFromHealthTabWhenTestPasses() throws IOException {
        String script = Files.readString(CONNECTORS_JS);

        assertThat(script).contains("Edit Configuration");
        assertThat(script).contains("Connectors.setDrawerTab('configuration')");
        assertThat(script).contains("A PASS validates the saved profile; it does not lock endpoint or credential updates.");
    }

    @Test
    void shouldKeepConnectorCredentialsWriteOnlyWhenConfigurationIsEdited() throws IOException {
        String script = Files.readString(CONNECTORS_JS);

        assertThat(script).contains("leave blank to keep existing");
        assertThat(script).contains("Plaintext values are never shown after saving.");
        assertThat(script).contains("Encrypted credentials saved");
        assertThat(script).contains("connectorSecretPayload");
        assertThat(script).contains("first ? { username: first } : {}");
        assertThat(script).contains("second ? { accessSecretKey: second } : {}");
    }

    @Test
    void shouldGuideCredentialRecoveryWhenSavedSecretsCannotBeDecrypted() throws IOException {
        String script = Files.readString(CONNECTORS_JS);
        String styles = Files.readString(Path.of("src/main/resources/static/pages/connectors/connectors.css"));

        assertThat(script).contains("credentialRecoveryRequired");
        assertThat(script).contains("SECRET_DECRYPTION_FAILED");
        assertThat(script).contains("Credentials need re-entry");
        assertThat(script).contains("Credential recovery required.");
        assertThat(script).contains("Required to recover credentials");
        assertThat(script).contains("await loadConnectors()");
        assertThat(styles).contains(".connector-error-callout");
    }

    @Test
    void shouldEnableAppDynamicsConnectorConfigurationFromMarketplace() throws IOException {
        String script = Files.readString(CONNECTORS_JS);

        assertThat(script).contains("{ value: 'APPDYNAMICS', label: 'AppDynamics', icon: 'activity', available: true");
        assertThat(script).contains("AppDynamics Controller");
        assertThat(script).contains("Controller URL");
        assertThat(script).contains("TLS Certificate Verification");
        assertThat(script).contains("Public and private KFH hybrid hosts/IPs are supported; metadata and localhost targets are blocked.");
        assertThat(script).contains("connector.type === 'APPDYNAMICS' || connector.type === 'VROPS'");
        assertThat(script).contains("username: first");
        assertThat(script).contains("password: second");
        assertThat(script).contains("verifySsl: connectorVerifySslSetting(selectedConnector)");
    }

    @Test
    void shouldPreserveDisabledTlsVerificationWhenBackendReturnsStringFalse() throws IOException {
        String script = Files.readString(CONNECTORS_JS);

        assertThat(script).contains("function booleanSetting(value, fallback = true)");
        assertThat(script).contains("['false', '0', 'no', 'off'].includes(text)");
        assertThat(script).contains("verifySsl: booleanSetting(row.verifySsl, true)");
        assertThat(script).contains("function connectorVerifySslSetting(connector)");
        assertThat(script).contains("return checkedSetting('edit-verify-ssl', connector?.verifySsl)");
    }

    @Test
    void shouldExposeConnectorHeartbeatWarningAction() throws IOException {
        String script = Files.readString(CONNECTORS_JS);

        assertThat(script).contains("Heartbeat enabled");
        assertThat(script).contains("APIClient.connectors.heartbeat");
        assertThat(script).contains("renderKpiCard('Warning'");
        assertThat(script).contains("lastTestStatus === 'FAIL'");
        assertThat(script).contains("testRun.message || 'Test failed'");
    }

    @Test
    void shouldEnableVropsConnectorConfigurationFromMarketplace() throws IOException {
        String script = Files.readString(CONNECTORS_JS);

        assertThat(script).contains("{ value: 'VROPS', label: 'VMware vROps', icon: 'cloud', available: true");
        assertThat(script).contains("VMware vROps / Aria Operations");
        assertThat(script).contains("vROps Host or URL");
        assertThat(script).contains("Auth Source");
        assertThat(script).contains("connector.type === 'VROPS'");
        assertThat(script).contains("Public and private KFH hybrid IPs/hostnames supported");
        assertThat(script).contains("Keep enabled by default; clear only for governed dev/hybrid tests when Java truststore CA import is pending.");
        assertThat(script).contains("Enter both vROps username and password when rotating vROps credentials.");
    }

    @Test
    void shouldEnableScomConnectorConfigurationFromMarketplace() throws IOException {
        String script = Files.readString(CONNECTORS_JS);

        assertThat(script).contains("{ value: 'SCOM', label: 'Microsoft SCOM', icon: 'server', available: true");
        assertThat(script).contains("Microsoft SCOM WinRM");
        assertThat(script).contains("SCOM Management Server");
        assertThat(script).contains("PowerShell Authentication");
        assertThat(script).contains("OperationsManager module and Get-SCOMAlert probe support");
        assertThat(script).contains("connector.type === 'APPDYNAMICS' || connector.type === 'VROPS' || connector.type === 'SCOM'");
        assertThat(script).contains("Enter both SCOM username and password to complete pending setup.");
        assertThat(script).contains("Enter both SCOM username and password when rotating SCOM credentials.");
    }

    @Test
    void shouldEnableEmcoConnectorConfigurationFromMarketplace() throws IOException {
        String script = Files.readString(CONNECTORS_JS);

        assertThat(script).contains("{ value: 'EMCO', label: 'EMCO Ping Monitor', icon: 'database', available: true");
        assertThat(script).contains("EMCO Ping Monitor SQL Server");
        assertThat(script).contains("SQL Server Host");
        assertThat(script).contains("KFH Database");
        assertThat(script).contains("CCTV Database");
        assertThat(script).contains("SqlServerCredentials");
        assertThat(script).contains("edit-emco-kfh-username");
        assertThat(script).contains("edit-emco-cctv-password");
        assertThat(script).contains("SQL Server JDBC readiness test for KFH and CCTV EMCO databases");
        assertThat(script).contains("Enter KFH and CCTV SQL usernames and passwords to complete EMCO setup.");
        assertThat(script).contains("EMCO KFH SQL username and password when rotating KFH credentials.");
    }

    @Test
    void shouldExposeScomWinRmCertificateValidationBypassOption() throws IOException {
        String script = Files.readString(CONNECTORS_JS);

        assertThat(script).contains("WinRM Certificate Validation");
        assertThat(script).contains("id=\"edit-scom-skip-cert-validation\"");
        assertThat(script).contains("Disable certificate validation for this SCOM test");
        assertThat(script).contains("Keeps HTTPS/5986 encryption");
        assertThat(script).contains("-SkipCACheck");
        assertThat(script).contains("-SkipCNCheck");
        assertThat(script).contains("-SkipRevocationCheck");
        assertThat(script).contains("const skipWinRmCertificateValidation = document.getElementById('edit-scom-skip-cert-validation')");
        assertThat(script).contains("if (skipWinRmCertificateValidation) return !skipWinRmCertificateValidation.checked");
        assertThat(script).contains("verifySsl: connectorVerifySslSetting(selectedConnector)");
    }

    @Test
    void shouldRenderTenConnectorCardsPerPageWithThreeCardsPerRow() throws IOException {
        String script = Files.readString(CONNECTORS_JS);
        String styles = Files.readString(Path.of("src/main/resources/static/pages/connectors/connectors.css"));

        assertThat(script).contains("const CONNECTOR_CARDS_PER_PAGE = 10");
        assertThat(script).contains("return filtered.slice(start, start + CONNECTOR_CARDS_PER_PAGE)");
        assertThat(script).contains("renderConnectorPagination(filtered.length, visibleConnectors.length)");
        assertThat(script).contains("aria-label=\"Connector pagination\"");
        assertThat(script).contains("Connectors.setPage");
        assertThat(script).contains("resetConnectorPage();");
        assertThat(styles).contains("grid-template-columns:repeat(3,minmax(360px,1fr))");
    }

    @Test
    void shouldColorConnectorEnablementStateForOperatorClarity() throws IOException {
        String script = Files.readString(CONNECTORS_JS);
        String styles = Files.readString(Path.of("src/main/resources/static/pages/connectors/connectors.css"));

        assertThat(script).contains("config-collection-control ${c.enabled ? 'is-enabled' : 'is-disabled'}");
        assertThat(script).contains("config-collection-state ${c.enabled ? 'enabled' : 'disabled'}");
        assertThat(styles).contains(".config-collection-state.enabled");
        assertThat(styles).contains("color: #0E6B42;");
        assertThat(styles).contains(".collection-state-pill.disabled");
        assertThat(styles).contains("border: 1px solid rgba(100, 116, 139, .22);");
        assertThat(styles).contains("background: linear-gradient(180deg, #f8fafc 0%, #eef2f4 100%);");
        assertThat(styles).contains(".config-collection-state.disabled");
        assertThat(styles).contains("color: #64748b;");
        assertThat(styles).contains(".config-collection-toggle.enabled .config-collection-switch");
        assertThat(styles).contains("background: #128754;");
        assertThat(styles).contains(".config-collection-toggle.disabled .config-collection-switch");
        assertThat(styles).contains("background: #94a3b8;");
    }

    @Test
    void shouldRenderCompactModernConnectorCards() throws IOException {
        String script = Files.readString(CONNECTORS_JS);
        String styles = Files.readString(Path.of("src/main/resources/static/pages/connectors/connectors.css"));

        assertThat(script).contains("const iconClass = c.enabled === false ? 'disabled' : statusClass(c.status);");
        assertThat(script).contains("const lagClass = c.enabled === false ? 'color: #64748b'");
        assertThat(script).contains("connector-card-status-${iconClass}");
        assertThat(script).contains("connector-card-collection-${c.enabled ? 'enabled' : 'disabled'}");
        assertThat(styles).contains("Compact modern connector cards");
        assertThat(styles).contains(".connectors-modern-grid .connector-card");
        assertThat(styles).contains("padding:16px 18px!important");
        assertThat(styles).contains(".connectors-modern-grid .connector-card-status-down");
        assertThat(styles).contains("#fff5f5");
        assertThat(styles).contains(".connectors-modern-grid .connector-card-status-disabled");
        assertThat(styles).contains(".connectors-modern-grid .connector-card-collection-disabled");
        assertThat(styles).contains("#f6f8f7");
        assertThat(styles).contains(".connector-card-collection-disabled .connector-detail-row");
        assertThat(styles).contains("background:rgba(248,250,252,.82)!important");
        assertThat(styles).contains(".connector-card-collection-disabled .connector-stats");
        assertThat(styles).contains("background:linear-gradient(135deg,#f8fafc,#ffffff)!important");
        assertThat(styles).contains(".connector-card-collection-disabled .scope-badge");
        assertThat(styles).doesNotContain(".connectors-modern-grid .connector-card::before");
        assertThat(styles).contains(".connectors-modern-grid .connector-details");
        assertThat(styles).contains("grid-template-columns:repeat(2,minmax(0,1fr))");
        assertThat(styles).contains(".connectors-modern-grid .connector-stats");
        assertThat(styles).contains("min-height:44px");
        assertThat(styles).contains(".connectors-modern-grid .connector-action-btn");
        assertThat(styles).contains("min-height:34px!important");
    }

    @Test
    void shouldRenderModernConnectorDrawerHeaderAndSlimFeedback() throws IOException {
        String script = Files.readString(CONNECTORS_JS);
        String styles = Files.readString(Path.of("src/main/resources/static/pages/connectors/connectors.css"));

        assertThat(script).contains("drawer-title-group");
        assertThat(script).contains("drawer-eyebrow");
        assertThat(script).contains("Connector details");
        assertThat(script).contains("aria-label=\"Close connector details\"");
        assertThat(script).contains("<div class=\"drawer-action-feedback-slot\">${renderDrawerFeedback()}</div>");
        assertThat(script).contains("function renderDrawerFeedback()");
        assertThat(script).contains("function updateDrawerFeedback()");
        assertThat(script).contains("drawer?.classList.contains('open') && selectedConnector");
        assertThat(script).contains("toast-notification toast-notification-${type} fixed bottom-6 right-6");
        assertThat(script).contains("bottom: '24px'");
        assertThat(styles).contains(".drawer-title-group");
        assertThat(styles).contains(".drawer-title-icon");
        assertThat(styles).contains(".drawer-eyebrow");
        assertThat(styles).contains(".drawer-close-btn:focus-visible");
        assertThat(styles).contains(".drawer-action-feedback-slot");
        assertThat(styles).contains("position: sticky;");
        assertThat(styles).contains(".drawer-action-feedback-success");
        assertThat(styles).contains("min-height: 38px;");
    }

    @Test
    void shouldPreserveTenantWhenGlobalUserSwitchesConnectorCountryScope() throws IOException {
        String config = Files.readString(CONFIG_JS);

        assertThat(config).contains("{ code: 'BH', name: 'KFH Bahrain', groupName: COUNTRY_GROUP_NAME, tenantId: DEFAULT_TENANT_ID");
        assertThat(config).contains("{ code: 'EG', name: 'KFH Egypt', groupName: COUNTRY_GROUP_NAME, tenantId: DEFAULT_TENANT_ID");
        assertThat(config).contains("tenantId: session.tenantId || selected.tenantId");
        assertThat(config).contains("userId: session.userId || selected.defaultUserId");
        assertThat(config).doesNotContain("tenantId: selected.tenantId,");
        assertThat(config).doesNotContain("tenantId: '00000000-0000-4000-8000-000000000002'");
        assertThat(config).doesNotContain("tenantId: '00000000-0000-4000-8000-000000000003'");
    }
}

