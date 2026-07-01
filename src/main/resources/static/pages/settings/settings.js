/**
 * KFH AIOps Command Center - Settings Module
 * Configure integrations and system preferences
 */
var Settings = (function() {
  'use strict';

  const AZURE_GPT54_ENDPOINT = 'https://92338-mkp4qz2u-centralus.services.ai.azure.com/openai/v1';
  const AZURE_READY_MODELS = [
    {
      value: 'gpt-5.4',
      label: 'GPT 5.4',
      purpose: 'GPT',
      deployment: 'gpt-5.4',
      endpoint: AZURE_GPT54_ENDPOINT,
      authMode: 'API_KEY',
      apiStyle: 'RESPONSES',
      description: 'Azure AI Foundry Responses API using Azure OpenAI API key authentication'
    }
  ];
  const SETTINGS_ACTIVE_TAB_KEY = 'kfh.aiops.settings.activeTab';
  const SETTINGS_TAB_IDS = ['azure', 'databases', 'sharepoint', 'teams', 'infrastructure', 'system'];

  function emptyAzureIntegration(index = 1) {
    return {
      id: `azure-openai-ui-${Date.now()}-${index}`,
      name: `Azure OpenAI ${index}`,
      provider: 'AZURE_OPENAI',
      purpose: 'GPT',
      modelName: 'gpt-5.4',
      countryCodes: defaultAiProviderCountries(),
      endpoint: AZURE_GPT54_ENDPOINT,
      apiKey: '',
      deployment: 'gpt-5.4',
      apiVersion: '2024-02-15-preview',
      authMode: 'API_KEY',
      apiStyle: 'RESPONSES',
      maxOutputTokens: 4096,
      monthlyTokenLimit: 1000000,
      timeoutSeconds: 5,
      enabled: true,
      lastTest: null
    };
  }

  function emptySettings() {
    return {
      azureOpenAI: {
        integrations: [],
        embeddings: { roundRobin: false, endpoint: '', apiKey: '', deploymentA: '', deploymentB: '', circuitBreakerA: 'unknown', circuitBreakerB: 'unknown', lastTest: null },
        gpt: { roundRobin: false, endpoint: '', apiKey: '', deploymentA: '', deploymentB: '', circuitBreakerA: 'unknown', circuitBreakerB: 'unknown', lastTest: null }
      },
      databases: { connections: [] },
      neo4j: { boltUrl: '', user: '', password: '', database: 'neo4j', healthIndicatorEnabled: false, countryCode: 'ALL', countryCodes: ['ALL'], lastTest: null },
      postgresql: { jdbcUrl: '', user: '', password: '', lastTest: null },
      sharepoint: { tenant: '', site: '', clientId: '', clientSecret: '', connections: [], lastTest: null },
      teams: { mappings: [] },
      infrastructure: { connections: [] },
      configurationOwnership: {
        startupRequiredApplicationProperties: [],
        databaseBackedMetadata: [],
        startupPolicy: '',
        metadataStore: 'config.integration_settings'
      },
      system: {
        dashboardRefreshSeconds: 30,
        quietPeriodMinutes: 15,
        aiMode: 'EVIDENCE_PACK_ONLY',
        countriesEnabled: '',
        redisHost: '',
        redisPort: 6379,
        redisPassword: '',
        redisHealthIndicatorEnabled: false,
        kafkaBootstrapServers: '',
        kafkaSecurityProtocol: 'SSL',
        kafkaUsername: '',
        kafkaPassword: '',
        indexStorageProvider: 'FILESYSTEM',
        indexStoragePath: '/data/aiops-index',
        indexStorageBucket: '',
        serverPort: 8443,
        sslEnabled: true
      }
    };
  }

  // State
  const state = {
    activeTab: 'azure',
    searchQuery: '',
    hasUnsavedChanges: false,
    revealedSecrets: new Set(),
    selectedTeamMapping: null,
    modalOpen: false,
    modalType: null,
    azureDraft: null,
    azureEditIndex: null,
    azureTesting: false,
    connectorTesting: false,
    neo4jTesting: false,
    connectorDraft: null,
    connectorKind: null,
    connectorEditIndex: null,
    neo4jDraft: null,
    teamDraft: null,
    teamEditIndex: null,
    settings: emptySettings()
  };

  let autoSaveTimer = null;

  // Utilities
  const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));

  function session() {
    return window.KFHConfig?.getSession ? KFHConfig.getSession() : { countryCode: 'KW', permissions: [] };
  }

  function canUseGlobalCountryScope() {
    return window.KFHConfig?.canUseGlobalCountryScope ? KFHConfig.canUseGlobalCountryScope(session()) : false;
  }

  function defaultAiProviderCountry() {
    return canUseGlobalCountryScope() ? 'ALL' : normalizeCountryScope(session().countryCode || 'KW');
  }

  function defaultAiProviderCountries() {
    return [defaultAiProviderCountry()];
  }

  function normalizeCountryScope(value) {
    const code = String(value || '').trim().toUpperCase();
    if (!code || code === 'GLOBAL' || code === 'DEFAULT') return 'ALL';
    return code;
  }

  function countryScopesForSettings() {
    const configScopes = Array.isArray(window.KFHConfig?.COUNTRY_SCOPES) ? KFHConfig.COUNTRY_SCOPES : [];
    const configured = configScopes.length ? configScopes : [
      { code: 'ALL', name: 'All countries' },
      { code: 'KW', name: 'KFH Kuwait' },
      { code: 'BH', name: 'KFH Bahrain' },
      { code: 'EG', name: 'KFH Egypt' }
    ];
    if (canUseGlobalCountryScope()) return configured;
    const ownCountry = normalizeCountryScope(session().countryCode || 'KW');
    return configured.filter(country => normalizeCountryScope(country.code) === ownCountry);
  }

  function countryLabel(countryCode) {
    const normalized = normalizeCountryScope(countryCode);
    const country = (window.KFHConfig?.COUNTRY_SCOPES || []).find(item => normalizeCountryScope(item.code) === normalized);
    if (country) return country.name || country.code;
    return normalized === 'ALL' ? 'All countries' : normalized;
  }

  function normalizeCountryScopes(value) {
    const raw = Array.isArray(value) ? value : [value];
    const scopes = raw.map(normalizeCountryScope).filter(Boolean);
    if (!scopes.length) return defaultAiProviderCountries();
    if (scopes.includes('ALL')) return ['ALL'];
    return [...new Set(scopes)];
  }

  function primaryCountryScope(countryCodes) {
    const scopes = normalizeCountryScopes(countryCodes);
    return scopes.includes('ALL') ? 'ALL' : scopes[0];
  }

  function selectedCountriesLabel(countryCodes) {
    const scopes = normalizeCountryScopes(countryCodes);
    if (scopes.includes('ALL')) return 'All countries (KW, BH, EG)';
    return scopes.map(countryLabel).join(', ');
  }

  function restoreActiveTab() {
    try {
      const saved = localStorage.getItem(SETTINGS_ACTIVE_TAB_KEY);
      if (SETTINGS_TAB_IDS.includes(saved)) {
        state.activeTab = saved;
      }
    } catch (error) {
      state.activeTab = state.activeTab || 'azure';
    }
  }

  function persistActiveTab(tab) {
    try {
      localStorage.setItem(SETTINGS_ACTIVE_TAB_KEY, tab);
    } catch (error) {
      // Ignore browser storage restrictions; in-memory tab state still works.
    }
  }

  function deepMerge(target, source) {
    if (!source || typeof source !== 'object') return target;
    Object.keys(source).forEach(key => {
      const value = source[key];
      if (Array.isArray(value)) {
        target[key] = value;
      } else if (value && typeof value === 'object') {
        target[key] = deepMerge(target[key] && typeof target[key] === 'object' ? target[key] : {}, value);
      } else {
        target[key] = value;
      }
    });
    return target;
  }

  function normalizeSettings(loaded) {
    const hasLoadedIntegrations = Array.isArray(loaded?.azureOpenAI?.integrations);
    const hasDatabaseConnections = Array.isArray(loaded?.databases?.connections);
    const hasSharePointConnections = Array.isArray(loaded?.sharepoint?.connections);
    const hasInfrastructureConnections = Array.isArray(loaded?.infrastructure?.connections);
    const normalized = deepMerge(emptySettings(), loaded || {});
    if (!hasLoadedIntegrations) {
      normalized.azureOpenAI.integrations = legacyAzureIntegrations(normalized.azureOpenAI);
    }
    if (!hasDatabaseConnections) {
      normalized.databases.connections = [];
    }
    if (!hasSharePointConnections) {
      normalized.sharepoint.connections = [];
    }
    if (!hasInfrastructureConnections) {
      normalized.infrastructure.connections = [];
    }
    normalized.azureOpenAI.integrations = normalized.azureOpenAI.integrations.map((item, index) => ({
      ...emptyAzureIntegration(index + 1),
      ...(item || {}),
      id: item?.id || `azure-openai-ui-${Date.now()}-${index + 1}`,
      name: item?.name || `Azure OpenAI ${index + 1}`,
      modelName: item?.modelName || item?.deployment || 'gpt-5.4',
      authMode: 'API_KEY',
      apiStyle: item?.apiStyle || 'RESPONSES',
      countryCodes: normalizeCountryScopes(item?.countryCodes || item?.countryCode || defaultAiProviderCountries()),
      countryCode: primaryCountryScope(item?.countryCodes || item?.countryCode || defaultAiProviderCountries()),
      enabled: item?.enabled !== false
    }));
    normalized.databases.connections = normalizeConnectorConnections(normalized.databases.connections, 'database');
    normalized.sharepoint.connections = normalizeConnectorConnections(normalized.sharepoint.connections, 'sharepoint');
    normalized.infrastructure.connections = normalizeConnectorConnections(normalized.infrastructure.connections, 'infrastructure');
    if (normalized.neo4j) {
      const neo4jCountryCodes = normalizeCountryScopes(normalized.neo4j.countryCodes || normalized.neo4j.countryCode || defaultAiProviderCountries());
      normalized.neo4j.countryCodes = neo4jCountryCodes;
      normalized.neo4j.countryCode = primaryCountryScope(neo4jCountryCodes);
    }
    return normalized;
  }

  function normalizeConnectorConnections(connections, kind) {
    return (connections || []).map((item, index) => {
      const countryCodes = normalizeCountryScopes(item?.countryCodes || item?.countryCode || defaultAiProviderCountries());
      return {
        ...emptyConnectorDefaults(kind, index + 1),
        ...(item || {}),
        id: item?.id || `${kind}-ui-${Date.now()}-${index + 1}`,
        countryCodes,
        countryCode: primaryCountryScope(countryCodes),
        enabled: item?.enabled !== false
      };
    });
  }

  function hasAnyValue(...values) {
    return values.some(value => String(value ?? '').trim().length > 0);
  }

  function legacyAzureIntegrations(azure) {
    const integrations = [];
    if (azure?.embeddings && hasAnyValue(azure.embeddings.endpoint, azure.embeddings.apiKey, azure.embeddings.deploymentA, azure.embeddings.deploymentB)) {
      integrations.push({
        ...emptyAzureIntegration(1),
        id: 'azure-openai-embeddings',
        name: 'Azure OpenAI Embeddings',
        purpose: 'EMBEDDINGS',
        endpoint: azure.embeddings.endpoint || '',
        apiKey: azure.embeddings.apiKey || '',
        deployment: azure.embeddings.deploymentA || azure.embeddings.deploymentB || ''
      });
    }
    if (azure?.gpt && hasAnyValue(azure.gpt.endpoint, azure.gpt.apiKey, azure.gpt.deploymentA, azure.gpt.deploymentB)) {
      integrations.push({
        ...emptyAzureIntegration(2),
        id: 'azure-openai-gpt',
        name: 'Azure OpenAI GPT',
        purpose: 'GPT',
        endpoint: azure.gpt.endpoint || '',
        apiKey: azure.gpt.apiKey || '',
        deployment: azure.gpt.deploymentA || azure.gpt.deploymentB || ''
      });
    }
    return integrations;
  }

  function getByPath(path) {
    return String(path || '').split('.').reduce((current, key) => current == null ? undefined : current[key], state.settings);
  }

  // Icons
  const icons = {
    cloud: '<path d="M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z"/>',
    database: '<ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M3 5V19A9 3 0 0 0 21 19V5"/><path d="M3 12A9 3 0 0 0 21 12"/>',
    file: '<path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/><polyline points="14 2 14 8 20 8"/>',
    chat: '<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>',
    bolt: '<polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>',
    eye: '<path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/>',
    eyeOff: '<path d="M9.88 9.88a3 3 0 1 0 4.24 4.24"/><path d="M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68"/><path d="M6.61 6.61A13.526 13.526 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61"/><line x1="2" x2="22" y1="2" y2="22"/>',
    search: '<circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/>',
    alert: '<path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/><path d="M12 9v4"/><path d="M12 17h.01"/>',
    close: '<path d="M18 6L6 18"/><path d="m6 6 12 12"/>',
    plus: '<path d="M5 12h14"/><path d="M12 5v14"/>'
  };

  function icon(name, size = 16) {
    return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${icons[name] || ''}</svg>`;
  }

  // Settings change handler
  function handleSettingChange(path, value) {
    state.hasUnsavedChanges = true;
    const keys = path.split('.');
    let current = state.settings;
    for (let i = 0; i < keys.length - 1; i++) {
      if (current[keys[i]] == null) {
        current[keys[i]] = /^\d+$/.test(keys[i + 1]) ? [] : {};
      }
      current = current[keys[i]];
    }
    current[keys[keys.length - 1]] = value;
    render();
    scheduleAutoSave();
  }

  // Toggle reveal
  function toggleReveal(key) {
    if (state.revealedSecrets.has(key)) {
      state.revealedSecrets.delete(key);
    } else {
      state.revealedSecrets.add(key);
    }
    render();
  }

  // Run test
  async function runTest(path) {
    try {
      const request = testPayload(path, getByPath(path) || state.settings);
      const result = await APIClient.settings.test(path, request);
      const status = normalizeStatus(result?.status || (result?.pass === false ? 'Fail' : 'Pass'));
      const message = result?.message || '';
      handleSettingChange(`${path}.lastTest`, {
        status,
        latency: result?.latencyMs || result?.latency || null,
        message,
        testedAt: result?.testedAt || null,
        checkedEndpoint: result?.checkedEndpoint || ''
      });
      toast(status === 'Pass' ? 'Test Passed' : (message || 'Test Failed'), status === 'Pass' ? 'success' : 'error');
    } catch (error) {
      const message = errorMessage(error, 'Test Failed');
      handleSettingChange(`${path}.lastTest`, { status: 'Fail', latency: null, message });
      toast(message, 'error');
    }
  }

  function testPayload(path, value) {
    if (String(path || '').startsWith('azureOpenAI')) {
      const provider = { ...(value || {}) };
      const countryCodes = normalizeCountryScopes(provider.countryCodes || provider.countryCode || defaultAiProviderCountries());
      return {
        ...provider,
        countryCodes,
        countryCode: primaryCountryScope(countryCodes),
        environment: session().environment || 'PROD'
      };
    }
    if (String(path || '').toLowerCase() === 'neo4j') {
      const neo4j = { ...(value || {}) };
      const countryCodes = normalizeCountryScopes(neo4j.countryCodes || neo4j.countryCode || defaultAiProviderCountries());
      return {
        ...neo4j,
        countryCodes,
        countryCode: primaryCountryScope(countryCodes)
      };
    }
    return value;
  }

  function normalizeStatus(status) {
    return String(status || '').toUpperCase() === 'PASS' ? 'Pass' : 'Fail';
  }

  function errorMessage(error, fallback) {
    return error?.details?.message || error?.message || fallback;
  }

  function isSessionContextError(error) {
    const code = String(error?.details?.code || '').trim().toUpperCase();
    return code === 'CLIENT_SESSION_CONTEXT_MISSING' || error?.status === 401;
  }

  function sessionContextMessage() {
    return 'Your session is missing valid tenant or user context. Sign in again, then retry Settings.';
  }

  // Save settings
  async function saveSettings(options = {}) {
    const showToast = options.showToast !== false;
    if (autoSaveTimer) {
      clearTimeout(autoSaveTimer);
      autoSaveTimer = null;
    }
    try {
      const saved = await APIClient.settings.update(state.settings);
      state.settings = normalizeSettings(saved || state.settings);
      state.hasUnsavedChanges = false;
      render();
      if (showToast) toast('Settings saved successfully', 'success');
      return { ok: true, saved: state.settings };
    } catch (error) {
      const message = isSessionContextError(error) ? sessionContextMessage() : errorMessage(error, 'Unable to save settings');
      toast(message, 'error');
      return { ok: false, error, message };
    }
  }

  function scheduleAutoSave() {
    if (!window.APIClient || !APIClient.settings) return;
    if (autoSaveTimer) clearTimeout(autoSaveTimer);
    autoSaveTimer = setTimeout(() => saveSettings({ showToast: false }), 650);
  }

  // Reset settings
  async function resetSettings() {
    state.hasUnsavedChanges = false;
    await loadSettings();
    render();
    toast('Settings reloaded', 'info');
  }

  async function loadSettings() {
    state.settings = emptySettings();
    if (!window.APIClient || !APIClient.settings) return;
    try {
      const loaded = await APIClient.settings.get();
      state.settings = normalizeSettings(loaded || {});
    } catch (error) {
      if (isSessionContextError(error)) {
        toast(sessionContextMessage(), 'error');
        console.warn('[Settings] Missing valid browser session context; refusing to load settings until the user signs in again.', error);
        return;
      }
      console.warn('[Settings] Unable to load production settings; rendering blank configuration.', error);
    }
  }

  // Toast
  function toast(msg, type = 'info') {
    const existing = document.querySelector('.settings-toast');
    if (existing) existing.remove();

    const t = document.createElement('div');
    t.className = 'settings-toast fixed top-6 right-6 px-5 py-3 rounded-lg shadow-lg z-[100] flex items-center gap-3 animate-fade-in';

    if (type === 'success') {
      t.style.background = '#E8F5EF';
      t.style.border = '1px solid #128754';
      t.style.color = '#128754';
    } else if (type === 'error') {
      t.style.background = '#FEE2E2';
      t.style.border = '1px solid #DC2626';
      t.style.color = '#DC2626';
    } else {
      t.style.background = '#E0F7FA';
      t.style.border = '1px solid #006064';
      t.style.color = '#006064';
    }

    t.innerHTML = '<span class="text-sm font-medium">' + esc(msg) + '</span>';
    document.body.appendChild(t);
    setTimeout(() => t.remove(), 4000);
  }

  // Check tab visibility
  function isVisible(tabId, keywords) {
    const query = state.searchQuery.trim().toLowerCase();
    if (query) {
      return keywords.toLowerCase().includes(query);
    }
    return state.activeTab === tabId;
  }

  // Chip
  function chip(label, variant) {
    const variants = {
      healthy: 'settings-chip-healthy',
      tripped: 'settings-chip-tripped',
      active: 'settings-chip-active',
      default: 'settings-chip-default'
    };
    return `<span class="settings-chip ${variants[variant] || variants.default}">${esc(label)}</span>`;
  }

  // Render header
  function renderHeader() {
    return `
      <div class="settings-header">
        <div class="settings-hero">
          <div class="settings-hero-copy">
            <h1 class="settings-title">Settings</h1>
          </div>
          <div class="settings-actions">
            <div class="settings-search">
              <span class="settings-search-icon">${icon('search', 16)}</span>
              <input id="settings-search" type="text" placeholder="Search variables: redisHost, serverPort, aiMode..." class="settings-search-input" value="${esc(state.searchQuery)}">
            </div>
            <button onclick="Settings.resetSettings()" class="settings-btn settings-btn-outline">Reset</button>
          </div>
        </div>
        
      </div>
    `;
  }

  function settingsMenuItems() {
    return [
      { id: 'azure', icon: 'cloud', label: 'Azure OpenAI', hint: 'AI endpoints, deployments, keys' },
      { id: 'databases', icon: 'database', label: 'Databases', hint: 'Additional DB and Neo4j metadata' },
      { id: 'sharepoint', icon: 'file', label: 'SharePoint', hint: 'Evidence artifact storage' },
      { id: 'teams', icon: 'chat', label: 'Microsoft Teams', hint: 'Notification webhooks' },
      { id: 'infrastructure', icon: 'bolt', label: 'Servers & Index', hint: 'Redis, Kafka, index storage' },
      { id: 'system', icon: 'bolt', label: 'System Variables', hint: 'Runtime, SSL, AI mode' }
    ];
  }

  function renderSettingsMenu() {
    const searching = state.searchQuery.trim().length > 0;
    return `
      <aside class="settings-local-sidebar" aria-label="Settings sections">
        <div class="settings-menu-head">
          <div class="settings-menu-kicker">Configuration menu</div>
          <div class="settings-menu-title">Sections</div>
          ${searching ? `<div class="settings-menu-searching">Filtering variable matches</div>` : ''}
        </div>
        <nav class="settings-tabs" aria-label="Settings section navigation">
          ${settingsMenuItems().map(item => `
            <button onclick="Settings.setTab('${item.id}')" class="settings-tab ${state.activeTab === item.id ? 'active' : ''}" aria-current="${state.activeTab === item.id ? 'page' : 'false'}">
              <span class="settings-tab-icon">${icon(item.icon, 16)}</span>
              <span class="settings-tab-copy">
                <span class="settings-tab-label">${esc(item.label)}</span>
                <span class="settings-tab-hint">${esc(item.hint)}</span>
              </span>
            </button>
          `).join('')}
        </nav>
      </aside>
    `;
  }

  function sectionHeader(title, hint) {
    return '';
  }

  // Render Azure OpenAI
  function renderAzureOpenAI() {
    if (!isVisible('azure', 'azure openai gpt 5.4 gpt-5.4 embeddings ai provider integration country countries name endpoint apiKey deployment modelName maxOutputTokens monthlyTokenLimit timeoutSeconds enabled azureOpenAI.integrations azureOpenAI.integrations.countryCodes azureOpenAI.integrations.name azureOpenAI.integrations.provider azureOpenAI.integrations.endpoint azureOpenAI.integrations.apiKey azureOpenAI.integrations.deployment azureOpenAI.integrations.apiVersion azureOpenAI.embeddings.endpoint azureOpenAI.gpt.endpoint')) return '';

    const integrations = state.settings.azureOpenAI.integrations || [];

    return `
      <div class="animate-fade-in">
        ${sectionHeader('Artificial Intelligence', 'Add multiple country-aware AI providers, configure each deployment, and test credentials without exposing secrets.')}
        <div class="settings-card">
          <div class="settings-card-header">
            <div>
              <h3 class="settings-card-title">AI Provider Integrations</h3>
              <p class="settings-card-subtitle">Each provider is scoped to all countries or selected countries. Test & Save validates credentials before persisting encrypted keys.</p>
            </div>
            <button onclick="Settings.openAzureModal()" class="settings-btn settings-btn-primary">${icon('plus', 15)} Add AI Provider</button>
          </div>
          <div class="settings-card-body">
            <div class="azure-connector-list">
              <div class="azure-connector-list-head">
                <span>Connector</span>
                <span>Model / Provider</span>
                <span>Country</span>
                <span>Endpoint</span>
                <span>Status</span>
                <span>Actions</span>
              </div>
              ${integrations.length ? integrations.map((integration, index) => renderAzureConnectorRow(integration, index)).join('') : '<div class="settings-empty-connectors">No AI provider has been added yet. Add an all-country provider or select one or more countries in the popup.</div>'}
            </div>
          </div>
        </div>
      </div>
    `;
  }

  function renderAzureConnectorRow(integration, index) {
    const base = `azureOpenAI.integrations.${index}`;
    const lastTest = integration.lastTest;
    const statusClass = lastTest?.status === 'Pass' ? 'settings-status-pass' : lastTest?.status === 'Fail' ? 'settings-status-fail' : '';
    return `
      <div class="azure-connector-row ${integration.enabled === false ? 'disabled' : ''}">
        <div class="azure-connector-main">
          <span class="azure-integration-icon">${icon(integration.purpose === 'EMBEDDINGS' ? 'bolt' : 'chat', 16)}</span>
          <div class="azure-connector-copy">
            <strong>${esc(integration.name || `Azure OpenAI ${index + 1}`)}</strong>
              <span>${integration.enabled === false ? 'Disabled' : 'Enabled'} • ${esc(integration.modelName || integration.deployment || 'No model')}</span>
          </div>
        </div>
        <div class="azure-connector-model">
          <strong>${esc(integration.purpose || 'GPT')}</strong>
          <span>${esc(providerLabel(integration.provider))} • ${esc(authModeLabel())}</span>
        </div>
        <div class="azure-connector-country">
          <strong>${esc(selectedCountriesLabel(integration.countryCodes || integration.countryCode || 'ALL'))}</strong>
          <span>${normalizeCountryScopes(integration.countryCodes || integration.countryCode).includes('ALL') ? 'Available in KW, BH, EG' : `${normalizeCountryScopes(integration.countryCodes || integration.countryCode).length} selected country scope`}</span>
        </div>
        <div class="azure-connector-endpoint" title="${esc(integration.endpoint || '')}">
          ${esc(integration.endpoint || 'Endpoint not configured')}
        </div>
        <div class="azure-connector-status">
          <span class="settings-status ${statusClass}">
            ${lastTest ? `${esc(lastTest.status)} (${esc(lastTest.latency ?? '-')}ms)` : 'Not tested'}
          </span>
          ${lastTest?.message ? `<small>${esc(lastTest.message)}</small>` : ''}
        </div>
        <div class="azure-connector-actions">
          <button onclick="Settings.runTest('${base}')" class="settings-btn settings-btn-ghost">Test Connection</button>
          <button onclick="Settings.openAzureModal(${index})" class="settings-btn settings-btn-outline">Edit</button>
          <button onclick="Settings.removeAzureIntegration(${index})" class="settings-btn settings-btn-outline">Remove</button>
        </div>
      </div>
    `;
  }

  function providerOptions(selected) {
    const providers = [
      ['AZURE_OPENAI', 'Azure OpenAI']
    ];
    return providers.map(([value, label]) => `<option value="${value}" ${selected === value ? 'selected' : ''}>${label}</option>`).join('');
  }

  function azureModelOptions(selected) {
    return AZURE_READY_MODELS
      .map(model => `<option value="${model.value}" ${selected === model.value ? 'selected' : ''}>${model.label}</option>`)
      .join('');
  }

  function authModeLabel() {
    return 'API key';
  }

  function selectedAzureModel(modelName) {
    const selected = AZURE_READY_MODELS.find(model => model.value === modelName);
    return selected || AZURE_READY_MODELS[0];
  }

  function countryScopeOptions(selected) {
    const selectedScopes = normalizeCountryScopes(selected || defaultAiProviderCountries());
    return countryScopesForSettings()
      .map(country => {
        const code = normalizeCountryScope(country.code);
        return `<option value="${code}" ${selectedScopes.includes(code) ? 'selected' : ''}>${esc(countryLabel(code))}</option>`;
      })
      .join('');
  }

  function countryScopeSelectorHtml(selected, onchangeHandler, helpText) {
    const selectedScopes = normalizeCountryScopes(selected || defaultAiProviderCountries());
    return `
      <div class="settings-form-group">
        <label class="settings-label">Countries</label>
        <select class="settings-input ai-country-multiselect" multiple size="4" onchange="${onchangeHandler}">
          ${countryScopeOptions(selectedScopes)}
        </select>
        <small class="azure-modal-help">${esc(helpText || 'Selecting All Countries makes this provider available for KW, BH, and EG.')}</small>
      </div>
    `;
  }

  function providerLabel(provider) {
    return providerOptions(provider).match(/selected>(.*?)<\/option>/)?.[1]?.replace('Azure OpenAI — ', '') || 'Azure OpenAI';
  }

  function connectorPath(kind, index) {
    const paths = {
      database: `databases.connections.${index}`,
      sharepoint: `sharepoint.connections.${index}`,
      infrastructure: `infrastructure.connections.${index}`,
      team: `teams.mappings.${index}`
    };
    return paths[kind] || '';
  }

  function connectorIcon(type) {
    const normalized = String(type || '').toUpperCase();
    if (normalized.includes('TEAM')) return 'chat';
    if (normalized.includes('SHAREPOINT')) return 'file';
    if (normalized.includes('REDIS') || normalized.includes('KAFKA') || normalized.includes('INDEX')) return 'bolt';
    return 'database';
  }

  function connectorEndpoint(connector) {
    if (connector.webhookUrl) return connector.webhookUrl;
    if (connector.endpoint && connector.port) return `${connector.endpoint}:${connector.port}`;
    return connector.endpoint || connector.channelName || connector.site || 'Endpoint not configured';
  }

  function connectorStatus(connector) {
    const lastTest = connector.lastTest;
    if (!lastTest) return 'Not tested';
    return `${lastTest.status || 'Unknown'} (${lastTest.latency ?? '-'}ms)`;
  }

  function renderConnectorRows(kind, connectors, emptyText) {
    if (!connectors.length) {
      return `<div class="settings-empty-connectors">${esc(emptyText)}</div>`;
    }
    return `
      <div class="azure-connector-list connector-list-5col">
        <div class="azure-connector-list-head">
          <span>Connector</span>
          <span>Type / Scope</span>
          <span>Endpoint</span>
          <span>Status</span>
          <span>Actions</span>
        </div>
        ${connectors.map((connector, index) => renderGenericConnectorRow(kind, connector, index)).join('')}
      </div>
    `;
  }

  function renderDatabaseRows(neo4j, connections) {
    const optionalRows = connections.map((connector, index) => renderGenericConnectorRow('database', connector, index)).join('');
    return `
      <div class="azure-connector-list database-connector-list">
        <div class="azure-connector-list-head database-connector-list-head">
          <span>Connector</span>
          <span>Type / Scope</span>
          <span>Endpoint</span>
          <span>Status</span>
          <span>Actions</span>
        </div>
        ${renderBuiltInNeo4jRow(neo4j)}
        ${optionalRows || `<div class="settings-empty-connectors">No optional database metadata connector has been added yet. The primary PostgreSQL datasource is configured in application properties/environment.</div>`}
      </div>
    `;
  }

  function renderBuiltInNeo4jRow(neo4j) {
    const endpoint = neo4j?.boltUrl || 'bolt://host:7687';
    return `
      <div class="azure-connector-row database-connector-row built-in-neo4j">
        <div class="azure-connector-main">
          <span class="azure-integration-icon">${icon('database', 16)}</span>
          <div class="azure-connector-copy">
            <strong>Neo4j Topology Graph</strong>
            <span>Built-in • Banking flow and dependency graph</span>
          </div>
        </div>
        <div class="azure-connector-model">
          <strong>NEO4J</strong>
          <span>${esc(neo4j?.database || 'neo4j')}</span>
        </div>
        <div class="azure-connector-endpoint" title="${esc(endpoint)}">${esc(endpoint)}</div>
        <div class="azure-connector-status">
          <span class="settings-status ${neo4j?.lastTest?.status === 'Pass' ? 'settings-status-pass' : neo4j?.lastTest?.status === 'Fail' ? 'settings-status-fail' : ''}">${esc(connectorStatus(neo4j || {}))}</span>
          <small>${neo4j?.lastTest?.message ? esc(neo4j.lastTest.message) : '&nbsp;'}</small>
        </div>
        <div class="azure-connector-actions">
          <button onclick="Settings.runTest('neo4j')" class="settings-btn settings-btn-ghost">Test Connection</button>
          <button onclick="Settings.openNeo4jModal()" class="settings-btn settings-btn-outline">Edit</button>
          <button onclick="Settings.removeNeo4jConfig()" class="settings-btn settings-btn-outline">Remove</button>
        </div>
      </div>
    `;
  }

  function renderGenericConnectorRow(kind, connector, index) {
    const path = connectorPath(kind, index);
    const type = connector.type || connector.provider || 'CONNECTOR';
    const testPath = kind === 'team' ? 'teams' : path;
    const editAction = kind === 'team' ? `Settings.openTeamModal(${index})` : `Settings.openConnectorModal('${kind}', ${index})`;
    const removeAction = kind === 'team' ? `Settings.deleteTeamMapping(${connector.id})` : `Settings.removeConnector('${kind}', ${index})`;
    return `
      <div class="azure-connector-row ${kind === 'database' ? 'database-connector-row' : ''} ${connector.enabled === false ? 'disabled' : ''}">
        <div class="azure-connector-main">
          <span class="azure-integration-icon">${icon(connectorIcon(type), 16)}</span>
          <div class="azure-connector-copy">
            <strong>${esc(connector.name || connector.domain || `Connector ${index + 1}`)}</strong>
            <span>${connector.enabled === false ? 'Disabled' : 'Enabled'}${connector.team ? ` • ${esc(connector.team)}` : ''}</span>
          </div>
        </div>
        <div class="azure-connector-model">
          <strong>${esc(type)}</strong>
          <span>${esc(connectorScopeLabel(connector))}</span>
        </div>
        <div class="azure-connector-endpoint" title="${esc(connectorEndpoint(connector))}">${esc(connectorEndpoint(connector))}</div>
        <div class="azure-connector-status">
          <span class="settings-status ${connector.lastTest?.status === 'Pass' ? 'settings-status-pass' : connector.lastTest?.status === 'Fail' ? 'settings-status-fail' : ''}">${esc(connectorStatus(connector))}</span>
          <small>${connector.lastTest?.message ? esc(connector.lastTest.message) : '&nbsp;'}</small>
        </div>
        <div class="azure-connector-actions">
          <button onclick="Settings.runTest('${testPath}')" class="settings-btn settings-btn-ghost">Test Connection</button>
          <button onclick="${editAction}" class="settings-btn settings-btn-outline">Edit</button>
          <button onclick="${removeAction}" class="settings-btn settings-btn-outline">Remove</button>
        </div>
      </div>
    `;
  }

  function connectorScopeLabel(connector) {
    const technicalScope = connector.protocol || connector.provider || connector.domain || connector.tenant || 'Metadata';
    return `${technicalScope} • ${selectedCountriesLabel(connector.countryCodes || connector.countryCode || defaultAiProviderCountries())}`;
  }

  // Render Databases
  function renderDatabases() {
    if (!isVisible('databases', 'neo4j postgres postgresql sql database connector databases.connections endpoint username password jdbc bolt')) return '';

    const connections = state.settings.databases.connections || [];
    const neo4j = state.settings.neo4j || {};

    return `
      <div class="animate-fade-in">
        ${sectionHeader('Data Persistence', 'Primary PostgreSQL is loaded from application properties at startup. Add only optional monitored databases or Neo4j metadata here.')}
        <div class="settings-card">
          <div class="settings-card-header">
            <div>
              <h3 class="settings-card-title">Database Connectors</h3>
              <p class="settings-card-subtitle">The boot PostgreSQL datasource is not stored here because this metadata is loaded from PostgreSQL after startup.</p>
            </div>
            <button onclick="Settings.openConnectorModal('database')" class="settings-btn settings-btn-primary">${icon('plus', 15)} Add Database</button>
          </div>
          <div class="settings-card-body">
            ${renderDatabaseRows(neo4j, connections)}
          </div>
        </div>
      </div>
    `;
  }

  // Render SharePoint
  function renderSharePoint() {
    if (!isVisible('sharepoint', 'sharepoint microsoft document sharepoint.tenant sharepoint.site sharepoint.clientId sharepoint.clientSecret')) return '';

    const connections = state.settings.sharepoint.connections || [];

    return `
      <div class="animate-fade-in">
        ${sectionHeader('Document Management', 'Add SharePoint evidence/artifact storage connectors from a popup, then manage them as rows.')}
        <div class="settings-card">
          <div class="settings-card-header">
            <div>
              <h3 class="settings-card-title">SharePoint Connectors</h3>
              <p class="settings-card-subtitle">Used for raw archives, evidence snapshots, and report output artifacts.</p>
            </div>
            <button onclick="Settings.openConnectorModal('sharepoint')" class="settings-btn settings-btn-primary">${icon('plus', 15)} Add SharePoint</button>
          </div>
          <div class="settings-card-body">
            ${renderConnectorRows('sharepoint', connections, 'No SharePoint connector has been added yet. Click Add SharePoint to create one.')}
          </div>
        </div>
      </div>
    `;
  }

  // Render Teams
  function renderTeams() {
    if (!isVisible('teams', 'microsoft teams chat webhook teams.mappings domain team channelName webhookUrl enabled')) return '';

    const teams = state.settings.teams;

    return `
      <div class="animate-fade-in">
        ${sectionHeader('Collaboration', 'Microsoft Teams notification mappings and masked webhook variables.')}
        <div class="settings-card">
          <div class="settings-card-header">
            <h3 class="settings-card-title">Teams Webhooks</h3>
            <button onclick="Settings.openTeamModal()" class="settings-btn settings-btn-primary">
              ${icon('plus', 14)} Add Mapping
            </button>
          </div>
          <div class="settings-card-body">
            ${renderConnectorRows('team', teams.mappings.map(m => ({ ...m, name: m.domain, type: 'TEAMS', endpoint: m.webhookUrl })), 'No Teams webhook mapping has been added yet. Click Add Mapping to create one.')}
          </div>
        </div>
      </div>
    `;
  }

  function renderSystem() {
    if (!isVisible('system', 'system variables application properties ssl countries incident ai system.dashboardRefreshSeconds system.quietPeriodMinutes system.aiMode system.countriesEnabled system.serverPort system.sslEnabled')) return '';

    const sys = state.settings.system;

    return `
      <div class="animate-fade-in">
        <div class="settings-card">
          <div class="settings-card-header">
            <div>
              <h3 class="settings-card-title">System Variables</h3>
              <p class="settings-card-subtitle">Runtime defaults loaded from application properties/environment.</p>
            </div>
            ${chip('application.properties', 'active')}
          </div>
          <div class="settings-card-body">
            <div class="settings-system-grid">
              <div class="settings-form-group">
                <label class="settings-label">Dashboard Refresh Seconds</label>
                <input type="number" class="settings-input" value="${esc(sys.dashboardRefreshSeconds)}" onchange="Settings.handleChange('system.dashboardRefreshSeconds', Number(this.value))">
              </div>
              <div class="settings-form-group">
                <label class="settings-label">Incident Quiet Period Minutes</label>
                <input type="number" class="settings-input" value="${esc(sys.quietPeriodMinutes)}" onchange="Settings.handleChange('system.quietPeriodMinutes', Number(this.value))">
              </div>
              <div class="settings-form-group">
                <label class="settings-label">AI Mode</label>
                <input type="text" class="settings-input" value="${esc(sys.aiMode)}" onchange="Settings.handleChange('system.aiMode', this.value)">
              </div>
              <div class="settings-form-group">
                <label class="settings-label">Enabled Countries</label>
                <input type="text" class="settings-input" value="${esc(sys.countriesEnabled)}" onchange="Settings.handleChange('system.countriesEnabled', this.value)">
              </div>
              <div class="settings-form-group">
                <label class="settings-label">Server Port</label>
                <input type="number" class="settings-input" value="${esc(sys.serverPort)}" onchange="Settings.handleChange('system.serverPort', Number(this.value))">
              </div>
              <label class="settings-toggle-label settings-system-toggle">
                SSL Enabled
                <div class="settings-toggle ${sys.sslEnabled ? 'active' : ''}" onclick="Settings.handleChange('system.sslEnabled', ${!sys.sslEnabled})"></div>
              </label>
            </div>
          </div>
        </div>
      </div>
    `;
  }

  function renderInfrastructure() {
    if (!isVisible('infrastructure', 'redis kafka storage index server infrastructure connections bootstrap broker aiops-index custom index storage redisHost kafkaBootstrapServers indexStoragePath')) return '';

    const connections = state.settings.infrastructure.connections || [];

    return `
      <div class="animate-fade-in">
        ${sectionHeader('Servers & Index Storage', 'Add Redis, Kafka, and custom index storage servers from popup forms. No default/dummy rows are created from startup properties.')}
        <div class="settings-card">
          <div class="settings-card-header">
            <div>
              <h3 class="settings-card-title">Infrastructure Connectors</h3>
              <p class="settings-card-subtitle">Redis hot state, Kafka durable stream, and custom index storage are configured after startup as metadata rows.</p>
            </div>
            <button onclick="Settings.openConnectorModal('infrastructure')" class="settings-btn settings-btn-primary">${icon('plus', 15)} Add Server</button>
          </div>
          <div class="settings-card-body">
            ${renderConnectorRows('infrastructure', connections, 'No Redis, Kafka, or index storage server has been added yet. Click Add Server to create one.')}
          </div>
        </div>
      </div>
    `;
  }

  // Render modal
  function renderModal() {
    if (!state.modalOpen) return '';
    if (state.modalType === 'azure') {
      return renderAzureModal();
    }
    if (state.modalType === 'connector') {
      return renderConnectorModal();
    }
    if (state.modalType === 'neo4j') {
      return renderNeo4jModal();
    }
    if (state.modalType === 'team') {
      return renderTeamModal();
    }

    return '';
  }

  function renderNeo4jModal() {
    const draft = state.neo4jDraft || { ...(state.settings.neo4j || {}) };
    const secretKey = 'neo4j.modal.password';
    const neo4jTesting = state.neo4jTesting;
    const countryCodes = normalizeCountryScopes(draft.countryCodes || draft.countryCode || defaultAiProviderCountries());
    return `
      <div id="settings-modal-overlay" class="settings-modal-overlay open" onclick="Settings.keepModalOpen(event)">
        <div class="settings-modal settings-modal-wide azure-modal">
          <div class="settings-modal-header">
            <div>
              <h3 class="settings-modal-title">Edit Neo4j Topology Graph</h3>
              <p class="settings-modal-subtitle">Configure the Neo4j relationship graph used for topology, banking flow, blast radius, and causal path analysis.</p>
            </div>
            <button onclick="Settings.closeModal()" class="settings-modal-close">${icon('close', 20)}</button>
          </div>
          <div class="settings-modal-body azure-modal-body">
            <div class="azure-modal-grid">
              <div class="settings-form-group azure-modal-wide-field">
                <label class="settings-label">Bolt URL</label>
                <input type="text" class="settings-input" value="${esc(draft.boltUrl || '')}" oninput="Settings.updateNeo4jDraft('boltUrl', this.value)" placeholder="bolt://172.17.133.47:7687">
              </div>
              <div class="settings-form-group azure-modal-wide-field">
                <label class="settings-label">Countries</label>
                <select class="settings-input ai-country-multiselect" multiple size="4" onchange="Settings.updateNeo4jCountries(Array.from(this.selectedOptions).map(option => option.value))">
                  ${countryScopeOptions(countryCodes)}
                </select>
                <small class="azure-modal-help">Hold Ctrl to select 1 or 2 countries. Selecting All Countries applies to KW, BH, and EG.</small>
              </div>
              <div class="settings-form-group">
                <label class="settings-label">Username</label>
                <input type="text" class="settings-input" value="${esc(draft.user || '')}" oninput="Settings.updateNeo4jDraft('user', this.value)" placeholder="neo4j">
              </div>
              <div class="settings-form-group">
                <label class="settings-label">Password</label>
                <div class="settings-input-group">
                  <input type="${state.revealedSecrets.has(secretKey) ? 'text' : 'password'}" class="settings-input" value="${esc(draft.password || '')}" oninput="Settings.updateNeo4jDraft('password', this.value)">
                  <button onclick="Settings.toggleReveal('${secretKey}')" class="settings-input-reveal">${state.revealedSecrets.has(secretKey) ? icon('eyeOff', 16) : icon('eye', 16)}</button>
                </div>
              </div>
              <div class="settings-form-group">
                <label class="settings-label">Database</label>
                <input type="text" class="settings-input" value="${esc(draft.database || 'neo4j')}" oninput="Settings.updateNeo4jDraft('database', this.value)" placeholder="neo4j">
              </div>
              <label class="settings-toggle-label azure-modal-enabled">
                Neo4j Health Indicator Enabled
                <div class="settings-toggle ${draft.healthIndicatorEnabled ? 'active' : ''}" onclick="Settings.updateNeo4jDraft('healthIndicatorEnabled', ${!draft.healthIndicatorEnabled})"></div>
              </label>
              <div class="azure-modal-note">${icon('alert', 15)} Neo4j stores topology relationships only. Do not store raw logs or raw telemetry in Neo4j. Passwords are encrypted server-side and returned only as a mask.</div>
              ${neo4jDraftTestStatus(draft)}
            </div>
          </div>
          <div class="settings-modal-footer">
            <button onclick="Settings.closeModal()" class="settings-btn settings-btn-outline">Cancel</button>
            <button type="button" onclick="Settings.testNeo4jDraft(event)" class="settings-btn settings-btn-outline" ${neo4jTesting ? 'disabled aria-disabled="true"' : ''}>${neo4jTesting ? 'Testing…' : 'Test Only'}</button>
            <button type="button" onclick="Settings.testAndSaveNeo4jDraft(event)" class="settings-btn settings-btn-primary" ${neo4jTesting ? 'disabled aria-disabled="true"' : ''}>${neo4jTesting ? 'Testing…' : 'Test & Update'}</button>
          </div>
        </div>
      </div>
    `;
  }

  function renderTeamModal() {
    const draft = state.teamDraft || { domain: '', team: '', channelName: '', webhookUrl: '', enabled: true };
    const editing = Number.isInteger(state.teamEditIndex);
    return `
      <div id="settings-modal-overlay" class="settings-modal-overlay open" onclick="Settings.keepModalOpen(event)">
        <div class="settings-modal">
          <div class="settings-modal-header">
            <h3 class="settings-modal-title">${editing ? 'Edit Teams Mapping' : 'Add Teams Mapping'}</h3>
            <button onclick="Settings.closeModal()" class="settings-modal-close">${icon('close', 20)}</button>
          </div>
          <div class="settings-modal-body">
            <div class="settings-form-group">
              <label class="settings-label">Domain</label>
              <input type="text" class="settings-input" value="${esc(draft.domain)}" oninput="Settings.updateTeamDraft('domain', this.value)" placeholder="e.g. Payments">
            </div>
            <div class="settings-form-group">
              <label class="settings-label">Team</label>
              <input type="text" class="settings-input" value="${esc(draft.team)}" oninput="Settings.updateTeamDraft('team', this.value)" placeholder="e.g. SRE-Team-A">
            </div>
            <div class="settings-form-group">
              <label class="settings-label">Channel Name</label>
              <input type="text" class="settings-input" value="${esc(draft.channelName)}" oninput="Settings.updateTeamDraft('channelName', this.value)" placeholder="alerts-prod">
            </div>
            <div class="settings-form-group">
              <label class="settings-label">Webhook URL</label>
              <input type="text" class="settings-input" value="${esc(draft.webhookUrl)}" oninput="Settings.updateTeamDraft('webhookUrl', this.value)" placeholder="https://...">
            </div>
            <label class="settings-toggle-label azure-modal-enabled">
              Enabled
              <div class="settings-toggle ${draft.enabled !== false ? 'active' : ''}" onclick="Settings.updateTeamDraft('enabled', ${draft.enabled === false})"></div>
            </label>
          </div>
          <div class="settings-modal-footer">
            <button onclick="Settings.closeModal()" class="settings-btn settings-btn-outline">Cancel</button>
            <button onclick="Settings.saveTeamDraft()" class="settings-btn settings-btn-primary">${editing ? 'Update Mapping' : 'Add Mapping'}</button>
          </div>
        </div>
      </div>
    `;
  }

  function renderConnectorModal() {
    const kind = state.connectorKind || 'database';
    const draft = state.connectorDraft || emptyConnectorDraft(kind);
    const editing = Number.isInteger(state.connectorEditIndex);
    const secretKey = 'connector.modal.secret';
    const testing = state.connectorTesting;
    const countryCodes = normalizeCountryScopes(draft.countryCodes || draft.countryCode || defaultAiProviderCountries());
    return `
      <div id="settings-modal-overlay" class="settings-modal-overlay open" onclick="Settings.keepModalOpen(event)">
        <div class="settings-modal settings-modal-wide azure-modal">
          <div class="settings-modal-header">
            <div>
              <h3 class="settings-modal-title">${editing ? 'Edit' : 'Add'} ${esc(connectorKindTitle(kind))}</h3>
              <p class="settings-modal-subtitle">Choose connector type and parameters first. The connector appears as a row after you add it.</p>
            </div>
            <button onclick="Settings.closeModal()" class="settings-modal-close">${icon('close', 20)}</button>
          </div>
          <div class="settings-modal-body azure-modal-body">
            <div class="azure-modal-grid">
              <div class="settings-form-group">
                <label class="settings-label">Connector Name</label>
                <input type="text" class="settings-input" value="${esc(draft.name)}" oninput="Settings.updateConnectorDraft('name', this.value)">
              </div>
              <div class="settings-form-group">
                <label class="settings-label">Type</label>
                <select class="settings-input" onchange="Settings.updateConnectorDraft('type', this.value)">
                  ${connectorTypeOptions(kind, draft.type)}
                </select>
              </div>
              ${countryScopeSelectorHtml(countryCodes, 'Settings.updateConnectorCountries(Array.from(this.selectedOptions).map(option => option.value))', 'Choose Kuwait, Bahrain, Egypt, or All Countries for this provider metadata.')}
              ${renderConnectorConnectionFields(kind, draft, secretKey)}
              <label class="settings-toggle-label azure-modal-enabled">
                Enabled
                <div class="settings-toggle ${draft.enabled !== false ? 'active' : ''}" onclick="Settings.updateConnectorDraft('enabled', ${draft.enabled === false})"></div>
              </label>
            </div>
            <div class="azure-modal-note">${icon('alert', 15)} Secrets are masked in responses and omitted from audit/test details.</div>
            ${connectorDraftTestStatus(draft)}
          </div>
          <div class="settings-modal-footer">
            <button onclick="Settings.closeModal()" class="settings-btn settings-btn-outline">Cancel</button>
            <button type="button" onclick="Settings.testConnectorDraft(event)" class="settings-btn settings-btn-outline" ${testing ? 'disabled aria-disabled="true"' : ''}>${testing ? 'Testing…' : 'Test Only'}</button>
            <button type="button" onclick="Settings.testAndSaveConnectorDraft(event)" class="settings-btn settings-btn-primary" ${testing ? 'disabled aria-disabled="true"' : ''}>${testing ? 'Testing…' : (editing ? 'Test & Update' : 'Test & Save')}</button>
          </div>
        </div>
      </div>
    `;
  }

  function renderConnectorConnectionFields(kind, draft, secretKey) {
    if (kind === 'infrastructure') {
      return renderInfrastructureConnectionFields(draft, secretKey);
    }
    return renderGenericConnectorFields(kind, draft, secretKey);
  }

  function connectorDraftTestStatus(draft) {
    if (!draft?.lastTest) return '';
    const status = state.connectorTesting ? 'Testing' : normalizeStatus(draft.lastTest.status);
    const statusClass = status === 'Pass' ? 'settings-status-pass' : status === 'Fail' ? 'settings-status-fail' : '';
    return `
      <div class="azure-modal-note azure-modal-test-result" role="status" aria-live="polite">
        ${icon(status === 'Fail' ? 'alert' : 'bolt', 15)}
        <div>
          <strong>Test ${esc(status)}</strong>
          <span>${esc(draft.lastTest.message || (status === 'Pass' ? 'Connection test passed.' : 'Connection test failed.'))}</span>
          <small class="settings-status ${statusClass}">${esc(draft.lastTest.latency ?? '-')}ms${draft.lastTest.checkedEndpoint ? ` • ${esc(draft.lastTest.checkedEndpoint)}` : ''}</small>
        </div>
      </div>
    `;
  }

  function neo4jDraftTestStatus(draft) {
    if (!draft?.lastTest) return '';
    const status = state.neo4jTesting ? 'Testing' : normalizeStatus(draft.lastTest.status);
    const statusClass = status === 'Pass' ? 'settings-status-pass' : status === 'Fail' ? 'settings-status-fail' : '';
    return `
      <div class="azure-modal-note azure-modal-test-result" role="status" aria-live="polite">
        ${icon(status === 'Fail' ? 'alert' : 'bolt', 15)}
        <div>
          <strong>Test ${esc(status)}</strong>
          <span>${esc(draft.lastTest.message || (status === 'Pass' ? 'Neo4j connection test passed.' : 'Neo4j connection test failed.'))}</span>
          <small class="settings-status ${statusClass}">${esc(draft.lastTest.latency ?? '-')}ms${draft.lastTest.checkedEndpoint ? ` • ${esc(draft.lastTest.checkedEndpoint)}` : ''}</small>
        </div>
      </div>
    `;
  }

  function renderGenericConnectorFields(kind, draft, secretKey) {
    return `
      <div class="settings-form-group azure-modal-wide-field">
        <label class="settings-label">Endpoint / URL / Path</label>
        <input type="text" class="settings-input" value="${esc(draft.endpoint || '')}" oninput="Settings.updateConnectorDraft('endpoint', this.value)" placeholder="${esc(connectorEndpointPlaceholder(kind, draft.type))}">
      </div>
      <div class="settings-form-group">
        <label class="settings-label">Username / Client ID</label>
        <input type="text" class="settings-input" value="${esc(draft.username || '')}" oninput="Settings.updateConnectorDraft('username', this.value)">
      </div>
      <div class="settings-form-group">
        <label class="settings-label">Password / Secret</label>
        ${secretInput('secret', draft.secret || '', secretKey)}
      </div>
      <div class="settings-form-group">
        <label class="settings-label">Protocol / Provider</label>
        <input type="text" class="settings-input" value="${esc(draft.protocol || draft.provider || '')}" oninput="Settings.updateConnectorDraft('protocol', this.value)">
      </div>
      <div class="settings-form-group">
        <label class="settings-label">Port</label>
        <input type="number" class="settings-input" value="${esc(draft.port || '')}" oninput="Settings.updateConnectorDraft('port', Number(this.value || 0))">
      </div>
    `;
  }

  function renderInfrastructureConnectionFields(draft, secretKey) {
    if (draft.type === 'KAFKA') return renderKafkaFields(draft, secretKey);
    if (draft.type === 'INDEX_STORAGE') return renderIndexStorageFields(draft, secretKey);
    return renderRedisFields(draft, secretKey);
  }

  function renderRedisFields(draft, secretKey) {
    return `
      <div class="settings-infra-modal-section azure-modal-wide-field">Redis connection details</div>
      <div class="settings-form-group azure-modal-wide-field">
        <label class="settings-label">Redis Host / IP</label>
        <input type="text" class="settings-input" value="${esc(draft.endpoint || '')}" oninput="Settings.updateConnectorDraft('endpoint', this.value)" placeholder="172.17.133.47">
        <small class="azure-modal-help">Use the reachable server IP/hostname. Your Linux check showed Redis responding locally; expose the service only on an approved private interface.</small>
      </div>
      <div class="settings-form-group">
        <label class="settings-label">Redis Port</label>
        <input type="number" min="1" max="65535" class="settings-input" value="${esc(draft.port || '')}" oninput="Settings.updateConnectorDraft('port', Number(this.value || 0))" placeholder="6379">
      </div>
      <div class="settings-form-group">
        <label class="settings-label">Redis Username / ACL User</label>
        <input type="text" class="settings-input" value="${esc(draft.username || '')}" oninput="Settings.updateConnectorDraft('username', this.value)" placeholder="default or ACL user">
      </div>
      <div class="settings-form-group">
        <label class="settings-label">Redis Password</label>
        ${secretInput('secret', draft.secret || '', secretKey)}
      </div>
      <div class="settings-form-group">
        <label class="settings-label">Redis Database</label>
        <input type="number" min="0" max="15" class="settings-input" value="${esc(draft.database ?? '')}" oninput="Settings.updateConnectorDraft('database', Number(this.value || 0))" placeholder="0">
      </div>
      <label class="settings-toggle-label azure-modal-enabled">
        TLS Enabled
        <div class="settings-toggle ${draft.tlsEnabled ? 'active' : ''}" onclick="Settings.updateConnectorDraft('tlsEnabled', ${!draft.tlsEnabled})"></div>
      </label>
    `;
  }

  function renderKafkaFields(draft, secretKey) {
    return `
      <div class="settings-infra-modal-section azure-modal-wide-field">Kafka connection details</div>
      <div class="settings-form-group azure-modal-wide-field">
        <label class="settings-label">Kafka Bootstrap Servers</label>
        <input type="text" class="settings-input" value="${esc(draft.endpoint || '')}" oninput="Settings.updateConnectorDraft('endpoint', this.value)" placeholder="172.17.133.47:9092">
        <small class="azure-modal-help">Use the comma-separated broker list from Kafka advertised listeners.</small>
      </div>
      <div class="settings-form-group">
        <label class="settings-label">Security Protocol</label>
        <select class="settings-input" onchange="Settings.updateConnectorDraft('protocol', this.value)">
          ${optionList(['PLAINTEXT', 'SSL', 'SASL_PLAINTEXT', 'SASL_SSL'], draft.protocol || 'PLAINTEXT')}
        </select>
      </div>
      <div class="settings-form-group">
        <label class="settings-label">SASL Mechanism</label>
        <select class="settings-input" onchange="Settings.updateConnectorDraft('saslMechanism', this.value)">
          ${optionList(['', 'PLAIN', 'SCRAM-SHA-256', 'SCRAM-SHA-512', 'GSSAPI'], draft.saslMechanism || '')}
        </select>
      </div>
      <div class="settings-form-group">
        <label class="settings-label">Kafka Username / Principal</label>
        <input type="text" class="settings-input" value="${esc(draft.username || '')}" oninput="Settings.updateConnectorDraft('username', this.value)" placeholder="Leave blank for PLAINTEXT/no SASL">
      </div>
      <div class="settings-form-group">
        <label class="settings-label">Kafka Password / Secret</label>
        ${secretInput('secret', draft.secret || '', secretKey)}
      </div>
      <div class="settings-form-group">
        <label class="settings-label">Client ID</label>
        <input type="text" class="settings-input" value="${esc(draft.clientId || '')}" oninput="Settings.updateConnectorDraft('clientId', this.value)" placeholder="kfh-aiops-settings">
      </div>
      <div class="settings-form-group azure-modal-wide-field">
        <label class="settings-label">Truststore / CA Path</label>
        <input type="text" class="settings-input" value="${esc(draft.truststorePath || '')}" oninput="Settings.updateConnectorDraft('truststorePath', this.value)" placeholder="Optional for SSL/SASL_SSL">
      </div>
    `;
  }

  function renderIndexStorageFields(draft, secretKey) {
    const provider = draft.provider || 'LOCAL';
    const isObject = provider === 'S3' || provider === 'AZURE_BLOB';
    const isAzure = provider === 'AZURE_BLOB';
    const pathLabel = isObject ? 'Object Storage URI' : (provider === 'SMB' ? 'UNC Path' : 'Index Storage Path');
    const pathPlaceholder = provider === 'S3' ? 's3://bucket/prefix'
      : isAzure ? 'https://<account>.blob.core.windows.net/<container>'
      : provider === 'SMB' ? '\\\\172.17.133.47\\aiops-index'
      : provider === 'PVC' ? '/var/aiops-index (PVC mount path)'
      : '/data/aiops-index';
    const providerHelp = isObject
      ? 'Object storage is not wired for live writes yet — Test validates the URI/metadata only, and the engine falls back to the filesystem default until the SDK is added.'
      : 'LOCAL = app-server disk · NFS = mount · SMB = Windows/UNC share · PVC = OpenShift volume. Test checks the path exists and is readable/writable by the app process.';
    const objectFields = !isObject ? '' : `
      <div class="settings-form-group">
        <label class="settings-label">${isAzure ? 'Container' : 'Bucket'}</label>
        <input type="text" class="settings-input" value="${esc(draft.bucket || '')}" oninput="Settings.updateConnectorDraft('bucket', this.value)" placeholder="${isAzure ? 'Container name' : 'Bucket name'}">
      </div>
      <div class="settings-form-group">
        <label class="settings-label">${isAzure ? 'Storage Account' : 'Region'}</label>
        <input type="text" class="settings-input" value="${esc(draft.region || '')}" oninput="Settings.updateConnectorDraft('region', this.value)" placeholder="${isAzure ? 'Storage account name' : 'Cloud region (e.g. me-central-1)'}">
      </div>
      <div class="settings-form-group">
        <label class="settings-label">${isAzure ? 'SAS / Client ID' : 'Access Key ID'}</label>
        <input type="text" class="settings-input" value="${esc(draft.username || '')}" oninput="Settings.updateConnectorDraft('username', this.value)" placeholder="${isAzure ? 'SAS token or client ID' : 'Access key ID'}">
      </div>
      <div class="settings-form-group">
        <label class="settings-label">${isAzure ? 'Account Key / Secret' : 'Secret Access Key'}</label>
        ${secretInput('secret', draft.secret || '', secretKey)}
      </div>`;
    return `
      <div class="settings-infra-modal-section azure-modal-wide-field">Custom index storage details</div>
      <div class="settings-form-group">
        <label class="settings-label">Storage Provider</label>
        <select class="settings-input" onchange="Settings.updateConnectorDraft('provider', this.value)">
          ${optionList(['LOCAL', 'NFS', 'SMB', 'PVC', 'S3', 'AZURE_BLOB'], provider)}
        </select>
        <small class="azure-modal-help">${providerHelp}</small>
      </div>
      <div class="settings-form-group azure-modal-wide-field">
        <label class="settings-label">${pathLabel}</label>
        <input type="text" class="settings-input" value="${esc(draft.endpoint || '')}" oninput="Settings.updateConnectorDraft('endpoint', this.value)" placeholder="${pathPlaceholder}">
        <small class="azure-modal-help">As seen by the app process. Stores custom index shards only — never point at PostgreSQL, Neo4j, Redis, or raw telemetry.</small>
      </div>
      ${objectFields}
    `;
  }

  function secretInput(field, value, secretKey) {
    return `
      <div class="settings-input-group">
        <input type="${state.revealedSecrets.has(secretKey) ? 'text' : 'password'}" class="settings-input" value="${esc(value || '')}" oninput="Settings.updateConnectorDraft('${field}', this.value)">
        <button onclick="Settings.toggleReveal('${secretKey}')" class="settings-input-reveal">${state.revealedSecrets.has(secretKey) ? icon('eyeOff', 16) : icon('eye', 16)}</button>
      </div>
    `;
  }

  function optionList(values, selected) {
    return values.map(value => `<option value="${esc(value)}" ${value === selected ? 'selected' : ''}>${esc(value || 'Not configured')}</option>`).join('');
  }

  function renderAzureModal() {
    const draft = state.azureDraft || emptyAzureIntegration((state.settings.azureOpenAI.integrations || []).length + 1);
    const editing = Number.isInteger(state.azureEditIndex);
    const secretKey = 'azure.modal.key';
    const countryCodes = normalizeCountryScopes(draft.countryCodes || draft.countryCode || defaultAiProviderCountries());
    const testStatus = azureDraftTestStatus(draft.lastTest);
    const testing = state.azureTesting;
    return `
      <div id="settings-modal-overlay" class="settings-modal-overlay open" onclick="Settings.keepModalOpen(event)">
        <div class="settings-modal settings-modal-wide azure-modal">
          <div class="settings-modal-header">
            <div>
              <h3 class="settings-modal-title">${editing ? 'Edit AI Provider' : 'Add AI Provider'}</h3>
              <p class="settings-modal-subtitle">Choose one or more countries, provider, and model details. Selecting All Countries makes the provider available for KW, BH, and EG.</p>
            </div>
            <button onclick="Settings.closeModal()" class="settings-modal-close">${icon('close', 20)}</button>
          </div>
          <div class="settings-modal-body azure-modal-body">
            <div class="azure-modal-grid">
              <div class="settings-form-group">
                <label class="settings-label">Connector Name</label>
                <input type="text" class="settings-input" value="${esc(draft.name)}" oninput="Settings.updateAzureDraft('name', this.value)" placeholder="e.g. Critical GPT EastUS">
              </div>
              <div class="settings-form-group">
                <label class="settings-label">Provider</label>
                <select class="settings-input" onchange="Settings.updateAzureDraft('provider', this.value)">
                  ${providerOptions(draft.provider)}
                </select>
              </div>
              <div class="settings-form-group">
                <label class="settings-label">Azure OpenAI Model</label>
                <select class="settings-input" onchange="Settings.updateAzureDraft('modelName', this.value)">
                  ${azureModelOptions(draft.modelName || draft.deployment || 'gpt-5.4')}
                </select>
                <small class="azure-modal-help">GPT 5.4 is preconfigured for the Azure AI Foundry Responses API.</small>
              </div>
              <div class="settings-form-group">
                <label class="settings-label">Countries</label>
                <select class="settings-input ai-country-multiselect" multiple size="4" onchange="Settings.updateAzureCountries(Array.from(this.selectedOptions).map(option => option.value))">
                  ${countryScopeOptions(countryCodes)}
                </select>
                <small class="azure-modal-help">Hold Ctrl to select 1 or 2 countries. Selecting All Countries applies to KW, BH, and EG.</small>
              </div>
              <div class="settings-form-group azure-modal-wide-field">
                <label class="settings-label">Endpoint URL</label>
                <input type="text" class="settings-input" value="${esc(draft.endpoint)}" oninput="Settings.updateAzureDraft('endpoint', this.value)" placeholder="https://92338-mkp4qz2u-centralus.services.ai.azure.com/openai/v1">
              </div>
              <div class="settings-form-group">
                <label class="settings-label">Deployment / Model Name</label>
                <input type="text" class="settings-input" value="${esc(draft.deployment)}" oninput="Settings.updateAzureDraft('deployment', this.value)" placeholder="gpt-5.4">
              </div>
              <div class="settings-form-group">
                <label class="settings-label">Max Output Tokens</label>
                <input type="number" min="1" max="128000" class="settings-input" value="${esc(draft.maxOutputTokens || 4096)}" oninput="Settings.updateAzureDraft('maxOutputTokens', Number(this.value || 4096))">
              </div>
              <div class="settings-form-group">
                <label class="settings-label">Monthly Token Limit</label>
                <input type="number" min="0" max="1000000000" class="settings-input" value="${esc(draft.monthlyTokenLimit || 1000000)}" oninput="Settings.updateAzureDraft('monthlyTokenLimit', Number(this.value || 0))">
                <small class="azure-modal-help">Use 0 for no configured monthly cap.</small>
              </div>
              <div class="settings-form-group azure-modal-wide-field">
                <label class="settings-label">API Key</label>
                <div class="settings-input-group">
                  <input type="${state.revealedSecrets.has(secretKey) ? 'text' : 'password'}" class="settings-input" value="${esc(draft.apiKey)}" oninput="Settings.updateAzureDraft('apiKey', this.value)" placeholder="Paste key for testing or leave masked when already configured">
                  <button onclick="Settings.toggleReveal('${secretKey}')" class="settings-input-reveal">${state.revealedSecrets.has(secretKey) ? icon('eyeOff', 16) : icon('eye', 16)}</button>
                </div>
              </div>
              <div class="settings-form-group">
                <label class="settings-label">Timeout Seconds</label>
                <input type="number" min="3" max="30" class="settings-input" value="${esc(draft.timeoutSeconds || 5)}" oninput="Settings.updateAzureDraft('timeoutSeconds', Number(this.value || 5))">
              </div>
              <label class="settings-toggle-label azure-modal-enabled">
                Enabled
                <div class="settings-toggle ${draft.enabled !== false ? 'active' : ''}" onclick="Settings.updateAzureDraft('enabled', ${draft.enabled === false})"></div>
              </label>
            </div>
            <div class="azure-modal-note">
              ${icon('alert', 15)} Secrets stay masked in Settings responses and are omitted from audit/test result details.
            </div>
            ${testStatus}
          </div>
          <div class="settings-modal-footer">
            <button onclick="Settings.closeModal()" class="settings-btn settings-btn-outline">Cancel</button>
            <button type="button" onclick="Settings.testAzureDraft(event)" class="settings-btn settings-btn-outline" ${testing ? 'disabled aria-disabled="true"' : ''}>${testing ? 'Testing…' : 'Test Only'}</button>
            <button type="button" onclick="Settings.testAndSaveAzureDraft(event)" class="settings-btn settings-btn-primary" ${testing ? 'disabled aria-disabled="true"' : ''}>${testing ? 'Testing…' : (editing ? 'Test & Update' : 'Test & Save')}</button>
          </div>
        </div>
      </div>
    `;
  }

  function azureDraftTestStatus(lastTest) {
    if (!lastTest) {
      return `
        <div class="azure-modal-note azure-modal-test-result azure-modal-test-result-placeholder" aria-hidden="true">
          ${icon('bolt', 15)}
          <div>
            <strong>Test Pending</strong>
            <span>Run Test Only or Test & Save to validate this provider.</span>
            <small class="settings-status">-</small>
          </div>
        </div>
      `;
    }
    const status = state.azureTesting ? 'Testing' : normalizeStatus(lastTest.status);
    const statusClass = status === 'Pass' ? 'settings-status-pass' : status === 'Fail' ? 'settings-status-fail' : '';
    return `
      <div class="azure-modal-note azure-modal-test-result" role="status" aria-live="polite">
        ${icon(status === 'Fail' ? 'alert' : 'bolt', 15)}
        <div>
          <strong>Test ${esc(status)}</strong>
          <span>${esc(lastTest.message || (status === 'Pass' ? 'Provider test passed.' : 'Provider test failed.'))}</span>
          <small class="settings-status ${statusClass}">${esc(lastTest.latency ?? '-')}ms${lastTest.checkedEndpoint ? ` • ${esc(lastTest.checkedEndpoint)}` : ''}</small>
        </div>
      </div>
    `;
  }

  // Team mapping actions
  function toggleTeamMapping(id, checked) {
    const mappings = state.settings.teams.mappings.map(m =>
      m.id === id ? { ...m, enabled: checked } : m
    );
    handleSettingChange('teams.mappings', mappings);
  }

  function selectTeamMapping(id) {
    state.selectedTeamMapping = state.selectedTeamMapping === id ? null : id;
    render();
  }

  function deleteTeamMapping(id) {
    const mappings = state.settings.teams.mappings.filter(m => m.id !== id);
    handleSettingChange('teams.mappings', mappings);
    toast('Mapping removed', 'info');
  }

  function openModal() {
    state.modalOpen = true;
    state.modalType = 'team';
    render();
  }

  function closeModal() {
    state.modalOpen = false;
    state.modalType = null;
    state.azureDraft = null;
    state.azureEditIndex = null;
    state.azureTesting = false;
    state.connectorTesting = false;
    state.connectorDraft = null;
    state.connectorKind = null;
    state.connectorEditIndex = null;
    state.neo4jDraft = null;
    state.teamDraft = null;
    state.teamEditIndex = null;
    render();
  }

  function keepModalOpen(event) {
    event?.stopPropagation?.();
  }

  function connectorKindTitle(kind) {
    return ({ database: 'Database Connector', sharepoint: 'SharePoint Connector', infrastructure: 'Infrastructure Server' })[kind] || 'Connector';
  }

  function connectorTypeOptions(kind, selected) {
    const options = {
      database: [['POSTGRESQL', 'PostgreSQL'], ['NEO4J', 'Neo4j'], ['ORACLE', 'Oracle'], ['SQLSERVER', 'SQL Server']],
      sharepoint: [['SHAREPOINT', 'SharePoint']],
      infrastructure: [['REDIS', 'Redis Server'], ['KAFKA', 'Kafka Server'], ['INDEX_STORAGE', 'Index Storage Server']]
    }[kind] || [['CONNECTOR', 'Connector']];
    return options.map(([value, label]) => `<option value="${value}" ${selected === value ? 'selected' : ''}>${label}</option>`).join('');
  }

  function connectorEndpointPlaceholder(kind, type) {
    if (kind === 'database' && type === 'POSTGRESQL') return 'jdbc:postgresql://host:5432/db';
    if (kind === 'database' && type === 'NEO4J') return 'bolt://host:7687';
    if (kind === 'infrastructure' && type === 'KAFKA') return 'broker1:9093,broker2:9093';
    if (kind === 'infrastructure' && type === 'INDEX_STORAGE') return '/data/aiops-index or s3://bucket/prefix';
    if (kind === 'infrastructure') return 'redis-host';
    return 'https://...';
  }

  function emptyConnectorDefaults(kind, index) {
    const type = kind === 'sharepoint' ? 'SHAREPOINT' : kind === 'infrastructure' ? 'REDIS' : 'POSTGRESQL';
    const countryCodes = defaultAiProviderCountries();
    return { id: `${kind}-${Date.now()}-${index}`, name: `${connectorKindTitle(kind)} ${index}`, countryCodes, countryCode: primaryCountryScope(countryCodes), ...connectorTypeDefaults(kind, type), enabled: true, lastTest: null };
  }

  function emptyConnectorDraft(kind) {
    const index = connectorCollection(kind).length + 1;
    return emptyConnectorDefaults(kind, index);
  }

  function connectorTypeDefaults(kind, type) {
    if (kind === 'infrastructure' && type === 'REDIS') {
      return { type, endpoint: '', username: '', secret: '', protocol: 'TCP', port: 6379, database: 0, tlsEnabled: false };
    }
    if (kind === 'infrastructure' && type === 'KAFKA') {
      return { type, endpoint: '', username: '', secret: '', protocol: 'PLAINTEXT', port: '', saslMechanism: '', clientId: 'kfh-aiops-settings', truststorePath: '' };
    }
    if (kind === 'infrastructure' && type === 'INDEX_STORAGE') {
      return { type, endpoint: '', username: '', secret: '', protocol: '', port: '', provider: 'LOCAL', bucket: '', region: '' };
    }
    return { type, endpoint: '', username: '', secret: '', protocol: '', port: '' };
  }

  function connectorCollection(kind) {
    if (kind === 'database') return state.settings.databases.connections || [];
    if (kind === 'sharepoint') return state.settings.sharepoint.connections || [];
    if (kind === 'infrastructure') return state.settings.infrastructure.connections || [];
    return [];
  }

  function setConnectorCollection(kind, connectors) {
    if (kind === 'database') state.settings.databases.connections = connectors;
    if (kind === 'sharepoint') state.settings.sharepoint.connections = connectors;
    if (kind === 'infrastructure') state.settings.infrastructure.connections = connectors;
  }

  function openConnectorModal(kind, index = null) {
    const connectors = connectorCollection(kind);
    const editing = Number.isInteger(index);
    state.connectorKind = kind;
    state.connectorDraft = editing ? { ...emptyConnectorDraft(kind), ...(connectors[index] || {}) } : emptyConnectorDraft(kind);
    state.connectorEditIndex = editing ? index : null;
    state.modalType = 'connector';
    state.modalOpen = true;
    render();
  }

  function updateConnectorDraft(field, value) {
    const kind = state.connectorKind || 'database';
    const current = state.connectorDraft || emptyConnectorDraft(kind);
    state.connectorDraft = field === 'type'
      ? { ...current, ...connectorTypeDefaults(kind, value), type: value }
      : { ...current, [field]: value };
    if (field === 'enabled' || field === 'type' || field === 'provider') render();
  }

  function updateConnectorCountries(values) {
    const kind = state.connectorKind || 'database';
    const countryCodes = normalizeCountryScopes(values);
    state.connectorDraft = {
      ...(state.connectorDraft || emptyConnectorDraft(kind)),
      countryCodes,
      countryCode: primaryCountryScope(countryCodes)
    };
    render();
  }

  async function testConnectorDraft(event) {
    stopModalEvent(event);
    if (state.connectorTesting) return null;
    const kind = state.connectorKind || 'database';
    const draft = normalizedConnectorDraft(kind, state.connectorDraft || emptyConnectorDraft(kind));
    const path = Number.isInteger(state.connectorEditIndex)
      ? connectorPath(kind, state.connectorEditIndex)
      : `${kind}.connections.preview`;
    if (!draft.name?.trim() || !draft.endpoint?.trim()) {
      toast('Connector name and endpoint are required before Test Connection', 'error');
      return { status: 'Fail', draft };
    }
    if (!window.APIClient?.settings?.test) {
      toast('Settings test API is not available. Refresh the page and try again.', 'error');
      return { status: 'Fail', draft };
    }
    state.connectorTesting = true;
    state.connectorDraft = { ...draft, lastTest: { status: 'Fail', latency: null, message: 'Testing connection...' } };
    render();
    try {
      const result = await APIClient.settings.test(path, testPayload(path, draft));
      const status = normalizeStatus(result?.status || (result?.pass === false ? 'Fail' : 'Pass'));
      const lastTest = {
        status,
        latency: result?.latencyMs || result?.latency || null,
        message: result?.message || '',
        testedAt: result?.testedAt || null,
        checkedEndpoint: result?.checkedEndpoint || ''
      };
      state.connectorDraft = { ...draft, lastTest };
      toast(status === 'Pass' ? 'Connection test passed' : (lastTest.message || 'Connection test failed'), status === 'Pass' ? 'success' : 'error');
      return { status, draft: state.connectorDraft };
    } catch (error) {
      const message = errorMessage(error, 'Connection test failed');
      state.connectorDraft = { ...draft, lastTest: { status: 'Fail', latency: null, message } };
      toast(message, 'error');
      return { status: 'Fail', draft: state.connectorDraft };
    } finally {
      state.connectorTesting = false;
      render();
    }
  }

  async function testAndSaveConnectorDraft(event) {
    stopModalEvent(event);
    const outcome = await testConnectorDraft();
    if (!outcome || outcome.status !== 'Pass') return;
    saveConnectorDraft({ draft: outcome.draft, tested: true });
  }

  function saveConnectorDraft(options = {}) {
    const kind = state.connectorKind || 'database';
    const draft = options.draft || normalizedConnectorDraft(kind, state.connectorDraft || emptyConnectorDraft(kind));
    if (!draft.name?.trim() || !draft.endpoint?.trim()) {
      toast('Connector name and endpoint are required', 'error');
      return;
    }
    draft.name = draft.name.trim();
    draft.endpoint = draft.endpoint.trim();
    const connectors = connectorCollection(kind);
    if (Number.isInteger(state.connectorEditIndex)) {
      connectors[state.connectorEditIndex] = draft;
      toast(options.tested ? 'Connector tested and updated' : 'Connector updated', 'success');
    } else {
      connectors.push(draft);
      toast(options.tested ? 'Connector tested and added' : 'Connector added', 'success');
    }
    setConnectorCollection(kind, connectors);
    state.hasUnsavedChanges = true;
    scheduleAutoSave();
    closeModal();
  }

  function normalizedConnectorDraft(kind, source) {
    const draft = { ...emptyConnectorDraft(kind), ...(source || {}) };
    const countryCodes = normalizeCountryScopes(draft.countryCodes || draft.countryCode || defaultAiProviderCountries());
    draft.countryCodes = countryCodes;
    draft.countryCode = primaryCountryScope(draft.countryCodes);
    return draft;
  }

  function removeConnector(kind, index) {
    const connectors = connectorCollection(kind);
    connectors.splice(index, 1);
    setConnectorCollection(kind, connectors);
    state.hasUnsavedChanges = true;
    render();
    scheduleAutoSave();
    toast('Connector removed', 'info');
  }

  function openNeo4jModal() {
    const existing = state.settings.neo4j || {};
    const countryCodes = normalizeCountryScopes(existing.countryCodes || existing.countryCode || defaultAiProviderCountries());
    state.neo4jDraft = {
      boltUrl: '', user: '', password: '', database: 'neo4j', healthIndicatorEnabled: false,
      ...existing,
      countryCodes,
      countryCode: primaryCountryScope(countryCodes)
    };
    state.modalType = 'neo4j';
    state.modalOpen = true;
    render();
  }

  function updateNeo4jDraft(field, value) {
    state.neo4jDraft = { boltUrl: '', user: '', password: '', database: 'neo4j', healthIndicatorEnabled: false, countryCode: 'ALL', countryCodes: ['ALL'], ...(state.neo4jDraft || {}), [field]: value };
    if (field === 'healthIndicatorEnabled') render();
  }

  function updateNeo4jCountries(values) {
    const countryCodes = normalizeCountryScopes(values);
    state.neo4jDraft = {
      ...(state.neo4jDraft || { boltUrl: '', user: '', password: '', database: 'neo4j', healthIndicatorEnabled: false }),
      countryCodes,
      countryCode: primaryCountryScope(countryCodes)
    };
    render();
  }

  function normalizedNeo4jDraft() {
    const draft = { boltUrl: '', user: '', password: '', database: 'neo4j', healthIndicatorEnabled: false, countryCode: 'ALL', countryCodes: ['ALL'], ...(state.neo4jDraft || {}) };
    if (!String(draft.boltUrl || '').trim() || !String(draft.user || '').trim() || !String(draft.database || '').trim()) {
      toast('Neo4j Bolt URL, username, and database are required', 'error');
      return null;
    }
    draft.boltUrl = String(draft.boltUrl || '').trim();
    draft.user = String(draft.user || '').trim();
    draft.password = String(draft.password || '');
    draft.database = String(draft.database || 'neo4j').trim();
    draft.healthIndicatorEnabled = Boolean(draft.healthIndicatorEnabled);
    draft.countryCodes = normalizeCountryScopes(draft.countryCodes || draft.countryCode || defaultAiProviderCountries());
    draft.countryCode = primaryCountryScope(draft.countryCodes);
    return draft;
  }

  async function testNeo4jDraft(event) {
    stopModalEvent(event);
    if (state.neo4jTesting) return null;
    const draft = normalizedNeo4jDraft();
    if (!draft) return null;
    if (!window.APIClient?.settings?.test) {
      const message = 'Settings test API is not available. Refresh the page and try again.';
      state.neo4jDraft = { ...draft, lastTest: { status: 'Fail', latency: null, message } };
      render();
      toast(message, 'error');
      return { status: 'Fail', draft: state.neo4jDraft };
    }
    state.neo4jTesting = true;
    state.neo4jDraft = { ...draft, lastTest: { status: 'Fail', latency: null, message: 'Testing Neo4j connection...' } };
    render();
    try {
      const result = await APIClient.settings.test('neo4j', testPayload('neo4j', draft));
      const status = normalizeStatus(result?.status || (result?.pass === false ? 'Fail' : 'Pass'));
      const lastTest = {
        status,
        latency: result?.latencyMs || result?.latency || null,
        message: result?.message || '',
        testedAt: result?.testedAt || null,
        checkedEndpoint: result?.checkedEndpoint || ''
      };
      state.neo4jDraft = { ...draft, lastTest };
      toast(status === 'Pass' ? 'Neo4j connection test passed' : (lastTest.message || 'Neo4j connection test failed'), status === 'Pass' ? 'success' : 'error');
      return { status, draft: state.neo4jDraft };
    } catch (error) {
      const message = errorMessage(error, 'Neo4j connection test failed');
      state.neo4jDraft = { ...draft, lastTest: { status: 'Fail', latency: null, message } };
      toast(message, 'error');
      return { status: 'Fail', draft: state.neo4jDraft };
    } finally {
      state.neo4jTesting = false;
      render();
    }
  }

  async function testAndSaveNeo4jDraft(event) {
    stopModalEvent(event);
    const outcome = await testNeo4jDraft();
    if (!outcome || outcome.status !== 'Pass') return;
    saveNeo4jDraft({ draft: outcome.draft, tested: true });
  }

  function saveNeo4jDraft(options = {}) {
    const draft = options.draft || normalizedNeo4jDraft();
    if (!draft) return;
    const countryCodes = normalizeCountryScopes(draft.countryCodes || draft.countryCode || defaultAiProviderCountries());
    state.settings.neo4j = {
      boltUrl: String(draft.boltUrl || '').trim(),
      user: String(draft.user || '').trim(),
      password: String(draft.password || '').trim(),
      database: String(draft.database || 'neo4j').trim(),
      healthIndicatorEnabled: Boolean(draft.healthIndicatorEnabled),
      countryCode: primaryCountryScope(countryCodes),
      countryCodes,
      lastTest: draft.lastTest || null
    };
    state.hasUnsavedChanges = true;
    scheduleAutoSave();
    closeModal();
    toast(options.tested ? 'Neo4j tested and updated' : 'Neo4j configuration updated', 'success');
  }

  function removeNeo4jConfig() {
    if (!window.confirm('Remove the Neo4j topology graph metadata from Settings? Startup environment values can still repopulate this row after restart.')) {
      return;
    }
    state.settings.neo4j = { boltUrl: '', user: '', password: '', database: 'neo4j', healthIndicatorEnabled: false, countryCode: 'ALL', countryCodes: ['ALL'], lastTest: null };
    state.hasUnsavedChanges = true;
    render();
    scheduleAutoSave();
    toast('Neo4j configuration removed', 'info');
  }

  function openAzureModal(index = null) {
    const integrations = state.settings.azureOpenAI.integrations || [];
    const editing = Number.isInteger(index);
    state.azureDraft = applyAzureReadyConnectorDefaults(
      editing ? { ...emptyAzureIntegration(index + 1), ...(integrations[index] || {}) } : emptyAzureIntegration(integrations.length + 1)
    );
    state.azureEditIndex = editing ? index : null;
    state.modalType = 'azure';
    state.modalOpen = true;
    render();
  }

  function updateAzureDraft(field, value) {
    const current = { ...(state.azureDraft || emptyAzureIntegration()), [field]: value };
    state.azureDraft = applyAzureReadyConnectorDefaults(current);
    if (field === 'enabled' || field === 'countryCode' || field === 'countryCodes' || field === 'provider' || field === 'modelName') {
      render();
    }
  }

  function applyAzureReadyConnectorDefaults(draft) {
    const next = { ...(draft || emptyAzureIntegration()) };
    const model = selectedAzureModel('gpt-5.4');
    next.provider = 'AZURE_OPENAI';
    next.modelName = model.value;
    next.purpose = model.purpose || 'GPT';
    next.deployment = next.deployment || model.deployment || model.value;
    next.endpoint = next.endpoint || model.endpoint || '';
    next.authMode = model.authMode || 'API_KEY';
    next.apiStyle = model.apiStyle || 'RESPONSES';
    next.maxOutputTokens = Number(next.maxOutputTokens || 4096);
    next.monthlyTokenLimit = Number(next.monthlyTokenLimit ?? 1000000);
    return next;
  }

  function updateAzureCountries(values) {
    const countryCodes = normalizeCountryScopes(values);
    state.azureDraft = {
      ...(state.azureDraft || emptyAzureIntegration()),
      countryCodes,
      countryCode: primaryCountryScope(countryCodes)
    };
    render();
  }

  function normalizedAzureDraft() {
    const draft = { ...(state.azureDraft || emptyAzureIntegration()) };
    draft.modelName = (draft.modelName || draft.deployment || 'gpt-5.4').trim();
    draft.deployment = (draft.deployment || draft.modelName).trim();
    if (!draft.name?.trim() || !draft.endpoint?.trim() || !draft.deployment?.trim()) {
      toast('Connector name, endpoint, and deployment are required', 'error');
      return null;
    }
    if (!draft.apiKey?.trim()) {
      toast('API key is required before running Test Only', 'error');
      return null;
    }
    draft.name = draft.name.trim();
    draft.endpoint = draft.endpoint.trim();
    draft.apiVersion = (draft.apiVersion || '2024-02-15-preview').trim();
    draft.authMode = 'API_KEY';
    draft.apiStyle = String(draft.apiStyle || 'RESPONSES').toUpperCase();
    draft.countryCodes = normalizeCountryScopes(draft.countryCodes || draft.countryCode || defaultAiProviderCountries());
    draft.countryCode = primaryCountryScope(draft.countryCodes);
    draft.maxOutputTokens = Math.max(1, Math.min(128000, Number(draft.maxOutputTokens || 4096)));
    draft.monthlyTokenLimit = Math.max(0, Math.min(1000000000, Number(draft.monthlyTokenLimit ?? 1000000)));
    draft.timeoutSeconds = Math.max(3, Math.min(30, Number(draft.timeoutSeconds || 5)));
    return draft;
  }

  function stopModalEvent(event) {
    if (!event) return;
    event.preventDefault?.();
    event.stopPropagation?.();
  }

  async function testAzureDraft(event) {
    stopModalEvent(event);
    if (state.azureTesting) return null;
    const draft = normalizedAzureDraft();
    if (!draft) return null;
    if (!window.APIClient?.settings?.test) {
      const message = 'Settings test API is not available. Refresh the page and try again.';
      state.azureDraft = { ...draft, lastTest: { status: 'Fail', latency: null, message, countryCode: draft.countryCode, countryCodes: draft.countryCodes } };
      render();
      toast(message, 'error');
      return { status: 'Fail', draft: state.azureDraft };
    }
    state.azureTesting = true;
    state.azureDraft = { ...draft, lastTest: { status: 'Fail', latency: null, message: 'Testing Azure OpenAI provider...', countryCode: draft.countryCode, countryCodes: draft.countryCodes } };
    render();
    try {
      const result = await APIClient.settings.test('azureOpenAI.integrations', testPayload('azureOpenAI.integrations', draft));
      const status = normalizeStatus(result?.status || (result?.pass === false ? 'Fail' : 'Pass'));
      const lastTest = {
        status,
        latency: result?.latencyMs || result?.latency || null,
        message: result?.message || '',
        testedAt: result?.testedAt || null,
        checkedEndpoint: result?.checkedEndpoint || '',
        countryCode: result?.countryCode || draft.countryCode,
        countryCodes: result?.countryCodes || draft.countryCodes
      };
      state.azureDraft = { ...draft, lastTest };
      render();
      toast(status === 'Pass' ? 'Provider test passed' : (lastTest.message || 'Provider test failed'), status === 'Pass' ? 'success' : 'error');
      return { status, draft: state.azureDraft };
    } catch (error) {
      const message = errorMessage(error, 'Provider test failed');
      state.azureDraft = { ...draft, lastTest: { status: 'Fail', latency: null, message, countryCode: draft.countryCode, countryCodes: draft.countryCodes } };
      render();
      toast(message, 'error');
      return { status: 'Fail', draft: state.azureDraft };
    } finally {
      state.azureTesting = false;
      render();
    }
  }

  async function testAndSaveAzureDraft(event) {
    stopModalEvent(event);
    const outcome = await testAzureDraft();
    if (!outcome || outcome.status !== 'Pass') return;
    await saveAzureDraft({ draft: outcome.draft, tested: true });
  }

  async function saveAzureDraft(options = {}) {
    const draft = options.draft || normalizedAzureDraft();
    if (!draft) return;

    const integrations = state.settings.azureOpenAI.integrations || [];
    if (Number.isInteger(state.azureEditIndex)) {
      integrations[state.azureEditIndex] = draft;
      if (!options.tested) toast('AI provider updated', 'success');
    } else {
      integrations.push(draft);
      if (!options.tested) toast('AI provider added', 'success');
    }
    state.settings.azureOpenAI.integrations = integrations;
    state.hasUnsavedChanges = true;
    if (options.tested) {
      const result = await saveSettings({ showToast: false });
      if (!result?.ok) return result;
      toast('AI provider tested and saved', 'success');
    } else {
      scheduleAutoSave();
    }
    state.modalOpen = false;
    state.modalType = null;
    state.azureDraft = null;
    state.azureEditIndex = null;
    render();
    return { ok: true };
  }

  function openTeamModal(index = null) {
    const mappings = state.settings.teams.mappings || [];
    const editing = Number.isInteger(index);
    state.teamDraft = editing ? { ...(mappings[index] || {}) } : { id: Date.now(), domain: '', team: '', channelName: '', webhookUrl: '', enabled: true };
    state.teamEditIndex = editing ? index : null;
    state.modalType = 'team';
    state.modalOpen = true;
    render();
  }

  function updateTeamDraft(field, value) {
    state.teamDraft = { ...(state.teamDraft || { id: Date.now(), enabled: true }), [field]: value };
    if (field === 'enabled') render();
  }

  function saveTeamDraft() {
    const draft = { ...(state.teamDraft || {}) };
    if (!draft.domain?.trim() || !draft.team?.trim() || !draft.channelName?.trim() || !draft.webhookUrl?.trim()) {
      toast('Domain, team, channel, and webhook URL are required', 'error');
      return;
    }
    draft.domain = draft.domain.trim();
    draft.team = draft.team.trim();
    draft.channelName = draft.channelName.trim();
    draft.webhookUrl = draft.webhookUrl.trim();
    const mappings = state.settings.teams.mappings || [];
    if (Number.isInteger(state.teamEditIndex)) {
      mappings[state.teamEditIndex] = draft;
      toast('Teams mapping updated', 'success');
    } else {
      mappings.push(draft);
      toast('Teams mapping added', 'success');
    }
    state.settings.teams.mappings = mappings;
    state.hasUnsavedChanges = true;
    scheduleAutoSave();
    closeModal();
  }

  // Main render
  function render() {
    const container = document.getElementById('settings-content') || document.getElementById('page-root') || document.getElementById('content-area');
    if (!container) return;

    container.innerHTML = `
      <div class="settings-page-shell">
        ${renderHeader()}
        <div class="settings-content">
          ${renderSettingsMenu()}
          <div class="settings-section">
            ${renderAzureOpenAI()}
            ${renderDatabases()}
            ${renderSharePoint()}
            ${renderTeams()}
            ${renderInfrastructure()}
            ${renderSystem()}
          </div>
        </div>
        ${renderModal()}
      </div>
    `;

    bindEvents();
  }

  function bindEvents() {
    KFHUtils.bindLiveSearch('settings-search', function(value) {
      state.searchQuery = value;
      render();
    });
  }

  function setTab(tab) {
    if (!SETTINGS_TAB_IDS.includes(tab)) return;
    state.activeTab = tab;
    persistActiveTab(tab);
    render();
  }

  function removeAzureIntegration(index) {
    const integrations = state.settings.azureOpenAI.integrations || [];
    integrations.splice(index, 1);
    state.hasUnsavedChanges = true;
    render();
    scheduleAutoSave();
    toast('Azure OpenAI integration removed', 'info');
  }

  async function init() {
    restoreActiveTab();
    await loadSettings();
    render();
    console.log('Settings module initialized');
  }

  return {
    init,
    setTab,
    handleChange: handleSettingChange,
    toggleReveal,
    runTest,
    saveSettings,
    resetSettings,
    removeAzureIntegration,
    openAzureModal,
    updateAzureDraft,
    updateAzureCountries,
    testAzureDraft,
    testAndSaveAzureDraft,
    saveAzureDraft,
    openConnectorModal,
    updateConnectorDraft,
    updateConnectorCountries,
    testConnectorDraft,
    testAndSaveConnectorDraft,
    saveConnectorDraft,
    removeConnector,
    openNeo4jModal,
    updateNeo4jDraft,
    updateNeo4jCountries,
    testNeo4jDraft,
    testAndSaveNeo4jDraft,
    saveNeo4jDraft,
    removeNeo4jConfig,
    toggleTeamMapping,
    selectTeamMapping,
    deleteTeamMapping,
    openTeamModal,
    updateTeamDraft,
    saveTeamDraft,
    openModal,
    closeModal,
    keepModalOpen,
    toast
  };
})();

window.Settings = Settings;