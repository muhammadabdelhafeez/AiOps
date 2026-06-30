package org.kfh.aiops.commandcenter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SettingsStaticUiTest {

    private static final Path SETTINGS_JS = Path.of("src/main/resources/static/pages/settings/settings.js");
    private static final Path SETTINGS_CSS = Path.of("src/main/resources/static/pages/settings/settings.css");
    private static final Path API_CLIENT_JS = Path.of("src/main/resources/static/shared/js/api-client.js");
    private static final Path ROUTER_JS = Path.of("src/main/resources/static/shared/js/router.js");
    private static final Path SIDEBAR_COLLAPSE_JS = Path.of("src/main/resources/static/shared/js/sidebar-collapse.js");
    private static final Path PARITY_CSS = Path.of("src/main/resources/static/shared/css/kfh-aiops-parity.css");

    @Test
    void shouldShowBackendMessageWhenSettingsTestFails() throws IOException {
        var script = Files.readString(SETTINGS_JS);

        assertThat(script).contains("toast(status === 'Pass' ? 'Test Passed' : (message || 'Test Failed')");
        assertThat(script).contains("errorMessage(error, 'Test Failed')");
        assertThat(script).contains("handleSettingChange(`${path}.lastTest`, { status: 'Fail', latency: null, message })");
        assertThat(script).contains("error?.details?.message || error?.message || fallback");
    }

    @Test
    void shouldKeepSettingsModalInsideViewport() throws IOException {
        var styles = Files.readString(SETTINGS_CSS);

        assertThat(styles)
                .contains("--settings-modal-viewport-height: 100vh")
                .contains("@supports (height: 100dvh)")
                .contains("max-height: calc(var(--settings-modal-viewport-height) - var(--settings-modal-height-gap))")
                .contains("overflow-y: auto")
                .contains("width: min(100%, 960px)")
                .contains("--settings-modal-height-gap: 24px")
                .contains("grid-template-columns: repeat(2, minmax(0, 1fr))")
                .contains(".settings-page-shell .azure-modal-grid .ai-country-multiselect")
                .contains(".settings-page-shell .azure-modal-test-result")
                .contains("min-height: 74px !important")
                .contains(".settings-page-shell .azure-modal-test-result-placeholder")
                .contains("visibility: hidden !important")
                .contains("@media (max-width: 760px)")
                .contains("grid-template-columns: 1fr !important");
    }

    @Test
    void shouldRenderCountryAwareAiProviderTestAndSaveControls() throws IOException {
        var script = Files.readString(SETTINGS_JS);

        assertThat(script)
                .contains("Countries")
                .contains("multiple size=\"4\"")
                .contains("Settings.updateAzureCountries")
                .contains("Test & Save")
                .contains("Settings.testAzureDraft(event)")
                .contains("Settings.testAndSaveAzureDraft(event)")
                .contains("azure-modal-test-result")
                .contains("azure-modal-test-result-placeholder")
                .contains("Test Pending")
                .contains("Testing…")
                .contains("API key is required before running Test Only")
                .contains("defaultAiProviderCountries()")
                .contains("GPT 5.4")
                .contains("['AZURE_OPENAI', 'Azure OpenAI']")
                .contains("API Key")
                .contains("Max Output Tokens")
                .contains("Monthly Token Limit")
                .contains("apiStyle: 'RESPONSES'")
                .doesNotContain("AZURE_OPENAI_GPT54")
                .doesNotContain(">Model Usage</label>")
                .doesNotContain(">Authentication</label>")
                .doesNotContain(">API Style</label>")
                .doesNotContain("Microsoft Entra ID (DefaultAzureCredential)")
                .doesNotContain("DefaultAzureCredential on the server")
                .doesNotContain("Entra ID Scope")
                .doesNotContain("Azure OpenAI — Ready GPT 5.4")
                .doesNotContain("Azure OpenAI — GPT / Chat")
                .doesNotContain("Custom GPT deployment")
                .doesNotContain("Custom embeddings deployment")
                .doesNotContain("Country Provider Test Defaults")
                .doesNotContain("azureOpenAI.countryTestValues")
                .doesNotContain("Default Test Value (All Countries)");
    }

    @Test
    void shouldEncodeSettingsTestSectionInApiClient() throws IOException {
        var apiClient = Files.readString(API_CLIENT_JS);

        assertThat(apiClient)
                .contains("encodeURIComponent(section)")
                .contains("/settings/${encodeURIComponent(section)}/test")
                .doesNotContain("/settings/${section}/test");
    }

    @Test
    void shouldRenderTypeSpecificInfrastructureServerFields() throws IOException {
        var script = Files.readString(SETTINGS_JS);
        var styles = Files.readString(SETTINGS_CSS);

        assertThat(script)
                .contains("infrastructure: [['REDIS', 'Redis Server'], ['KAFKA', 'Kafka Server'], ['INDEX_STORAGE', 'Index Storage Server']]")
                .contains("function renderInfrastructureConnectionFields(draft, secretKey)")
                .contains("function renderRedisFields(draft, secretKey)")
                .contains("function renderKafkaFields(draft, secretKey)")
                .contains("function renderIndexStorageFields(draft, secretKey)")
                .contains("function countryScopeSelectorHtml(selected, onchangeHandler, helpText)")
                .contains("function updateConnectorCountries(values)")
                .contains("Settings.updateConnectorCountries(Array.from(this.selectedOptions).map(option => option.value))")
                .contains("Choose Kuwait, Bahrain, Egypt, or All Countries for this provider metadata")
                .contains("function connectorScopeLabel(connector)")
                .contains("Redis Host / IP")
                .contains("172.17.133.47")
                .contains("Redis Port")
                .contains("Redis Database")
                .contains("TLS Enabled")
                .contains("Kafka Bootstrap Servers")
                .contains("Security Protocol")
                .contains("SASL Mechanism")
                .contains("Kafka Username / Principal")
                .contains("Truststore / CA Path")
                .contains("Storage Provider")
                .contains("Index Storage Path / URI")
                .contains("Bucket / Share")
                .contains("connectorTypeDefaults(kind, value)")
                .contains("protocol: 'PLAINTEXT'")
                .contains("clientId: 'kfh-aiops-settings'")
                .contains("provider: 'LOCAL'")
                .contains("countryCodes")
                .contains("countryCode: primaryCountryScope(countryCodes)")
                .contains("function testConnectorDraft(event)")
                .contains("Settings.testConnectorDraft(event)")
                .contains("connectorTesting")
                .contains("Connection test passed")
                .contains("`${kind}.connections.preview`");
        assertThat(styles)
                .contains(".settings-page-shell .settings-infra-modal-section")
                .contains("text-transform: uppercase !important");
    }

    @Test
    void shouldRenderBuiltInNeo4jDatabaseTestConnection() throws IOException {
        var script = Files.readString(SETTINGS_JS);
        var styles = Files.readString(SETTINGS_CSS);

        assertThat(script)
                .contains("SETTINGS_ACTIVE_TAB_KEY = 'kfh.aiops.settings.activeTab'")
                .contains("SETTINGS_TAB_IDS = ['azure', 'databases', 'sharepoint', 'teams', 'infrastructure', 'system']")
                .contains("function restoreActiveTab()")
                .contains("function persistActiveTab(tab)")
                .contains("restoreActiveTab();")
                .contains("persistActiveTab(tab)")
                .contains("Neo4j Topology Graph")
                .contains("Built-in • Banking flow and dependency graph")
                .contains("Settings.runTest('neo4j')")
                .contains("Settings.openNeo4jModal()")
                .contains("Settings.removeNeo4jConfig()")
                .contains("Edit Neo4j Topology Graph")
                .contains("Settings.updateNeo4jDraft('boltUrl', this.value)")
                .contains("Settings.updateNeo4jDraft('password', this.value)")
                .contains("Settings.saveNeo4jDraft()")
                .contains("renderBuiltInNeo4jRow(neo4j)")
                .contains("<small>${neo4j?.lastTest?.message ? esc(neo4j.lastTest.message) : '&nbsp;'}</small>")
                .contains("<small>${connector.lastTest?.message ? esc(connector.lastTest.message) : '&nbsp;'}</small>")
                .contains("bolt://host:7687")
                .contains("['NEO4J', 'Neo4j']")
                .contains("connectorEndpointPlaceholder(kind, type)");
        assertThat(styles)
                .contains(".settings-page-shell .database-connector-list .azure-connector-list-head")
                .contains(".settings-page-shell .database-connector-list .database-connector-row")
                .contains("minmax(220px, 0.9fr) 300px")
                .contains(".settings-page-shell .database-connector-row .azure-connector-actions")
                .contains("flex-wrap: wrap !important")
                .contains(".settings-page-shell .database-connector-row .azure-connector-actions .settings-btn:first-child")
                .contains("min-width: 132px !important")
                .contains("white-space: nowrap !important")
                .contains(".settings-page-shell .settings-btn:hover")
                .contains("transform: none !important")
                .contains(".settings-page-shell .settings-card")
                .contains("contain: layout paint !important")
                .contains(".settings-page-shell .animate-fade-in")
                .contains("animation: none !important");
    }

    @Test
    void shouldShowCompactMainNavigationWhenSettingsPageOpens() throws IOException {
        var router = Files.readString(ROUTER_JS);
        var sidebarCollapse = Files.readString(SIDEBAR_COLLAPSE_JS);
        var styles = Files.readString(PARITY_CSS);
        var script = Files.readString(SETTINGS_JS);
        var settingsStyles = Files.readString(SETTINGS_CSS);

        assertThat(router)
                .contains("SETTINGS_FOCUS_CLASS = 'kfh-settings-focus-mode'")
                .contains("updateShellMode(pageId)")
                .contains("pageId === 'settings'")
                .contains("app.classList.toggle(SETTINGS_FOCUS_CLASS, settingsFocus)")
                .contains("sidebar.classList.toggle(SETTINGS_FOCUS_CLASS, settingsFocus)")
                .contains("sidebar.setAttribute('aria-hidden', 'false')")
                .contains("window.KFHSidebarCollapse.setCollapsed(settingsFocus ? true : null, { persist: false })")
                .doesNotContain("SETTINGS_MAIN_MENU_OPEN_CLASS")
                .doesNotContain("toggleSettingsMainMenu")
                .doesNotContain("isSettingsMainMenuOpen");
        assertThat(sidebarCollapse)
                .contains("function setCollapsed(collapsed, options = {})")
                .contains("const effectiveCollapsed = collapsed == null ? readCollapsed() : Boolean(collapsed)")
                .contains("window.KFHSidebarCollapse = { init, setCollapsed }");
        assertThat(styles)
                .contains(".kfh-app-layout.kfh-settings-focus-mode .kfh-sidebar")
                .contains("#sidebar-container.kfh-settings-focus-mode")
                .contains("display: flex !important")
                .contains(".kfh-app-layout.kfh-settings-focus-mode .kfh-main-content")
                .contains("flex: 1 1 auto !important")
                .contains("#sidebar-container.kfh-sidebar-collapsed")
                .contains("width: 76px !important")
                .contains("/* Collapsed rail: keep the collapse control icon-only so its text never clips. */")
                .contains("#sidebar-container.kfh-sidebar-collapsed .kfh-sidebar-collapse-label")
                .contains("display: none !important")
                .contains("#sidebar-container.kfh-sidebar-collapsed .kfh-sidebar-collapse-toggle")
                .contains("max-width: 46px !important")
                .contains("justify-content: center !important")
                .contains("overflow: hidden !important")
                .contains("/* Collapsed rail color calibration: dark KFH rail, white active pill, green active icon. */")
                .contains("background: #003D2E !important")
                .contains("#sidebar-container.kfh-sidebar-collapsed .kfh-sidebar-nav-item.active")
                .contains("background: #ffffff !important")
                .contains("#sidebar-container.kfh-sidebar-collapsed .kfh-sidebar-nav-item.active .kfh-sidebar-nav-icon svg")
                .contains("stroke: #128754 !important")
                .doesNotContain(".kfh-app-layout.kfh-settings-focus-mode.kfh-settings-main-menu-open");
        assertThat(script)
                .doesNotContain("Open Main Menu")
                .doesNotContain("Hide Main Menu")
                .doesNotContain("Settings.toggleMainMenu(event)")
                .doesNotContain("window.Router?.toggleSettingsMainMenu?.()")
                .doesNotContain("aria-pressed");
        assertThat(settingsStyles)
                .doesNotContain(".settings-page-shell .settings-main-menu-toggle")
                .doesNotContain(".settings-page-shell .settings-main-menu-toggle.active");
    }

    @Test
    void shouldRequireExplicitCloseControlsForSettingsModals() throws IOException {
        var script = Files.readString(SETTINGS_JS);

        assertThat(script)
                .contains("<div id=\"settings-modal-overlay\" class=\"settings-modal-overlay open\" onclick=\"Settings.keepModalOpen(event)\">")
                .contains("<button onclick=\"Settings.closeModal()\" class=\"settings-modal-close\"")
                .contains("<button onclick=\"Settings.closeModal()\" class=\"settings-btn settings-btn-outline\">Cancel</button>")
                .contains("function keepModalOpen(event)")
                .contains("event?.stopPropagation?.();")
                .contains("keepModalOpen,")
                .doesNotContain("onclick=\"if(event.target === this) Settings.closeModal()\"");
        assertThat(script.split(Pattern.quote("onclick=\"Settings.keepModalOpen(event)\""), -1)).hasSize(5);
    }
}
