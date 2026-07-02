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
  const SETTINGS_ACTIVE_TAB_KEY = 'kfh.aiops.settings.activeSection';
  // Section IDs used by the KFH enterprise-grade Settings shell. Old IDs are
  // migrated in restoreActiveTab() below.
  const SETTINGS_TAB_IDS = ['ai', 'databases', 'notifications', 'infrastructure', 'connections', 'system'];
  // A section may render legacy sub-renderers keyed under different IDs; the
  // alias table lets isVisible() include those when the composite section is
  // active. Example: "notifications" surfaces both SharePoint and Teams.
  const TAB_ALIASES = {
    ai: ['azure'],
    notifications: ['sharepoint', 'teams']
  };
  // Legacy tab IDs from earlier versions map to the new KFH section IDs.
  const LEGACY_TAB_MIGRATIONS = {
    azure: 'ai',
    sharepoint: 'notifications',
    teams: 'notifications',
    connectors: 'connections'
  };

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
    activeTab: 'ai',
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
    settings: emptySettings(),
    // enterprise-grade Connections catalog (Settings → Connections)
    connectors: [],
    connectorTypes: [],
    connectorsLoaded: false,
    connectorsLoading: false,
    connectorsError: null,
    // Add / View connection popup state.
    // Shape: { pluginType, mode: 'catalog'|'form', activeTab: 'setup'|'share',
    //          connectorId: string|null, draft: {...}, testing: bool, saving: bool,
    //          readOnly: bool, message: string|null }
    connectionPopup: null,
    // enterprise-grade connector detail page (drill-down inside Connections).
    // When set, Settings → Connections shows the per-connector detail page
    // (breadcrumb + header + toolbar + connections table) instead of the catalog.
    connectionDetailType: null,
    connectionDetailSearch: '',
    connectionDetailOwner: ''
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
      let saved = localStorage.getItem(SETTINGS_ACTIVE_TAB_KEY);
      if (!saved) {
        // Backward-compat: honor the pre-redesign key one time.
        saved = localStorage.getItem('kfh.aiops.settings.activeTab');
      }
      if (saved && LEGACY_TAB_MIGRATIONS[saved]) {
        saved = LEGACY_TAB_MIGRATIONS[saved];
      }
      if (SETTINGS_TAB_IDS.includes(saved)) {
        state.activeTab = saved;
      }
    } catch (error) {
      state.activeTab = state.activeTab || 'ai';
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
    plus: '<path d="M5 12h14"/><path d="M12 5v14"/>',
    plug: '<path d="M9 2v6"/><path d="M15 2v6"/><path d="M6 8h12v3a6 6 0 0 1-12 0V8Z"/><path d="M12 17v5"/>',
    gear: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33h.01a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51h.01a1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82v.01a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1Z"/>',
    globe: '<circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>',
    server: '<rect x="3" y="4" width="18" height="7" rx="1.5"/><rect x="3" y="13" width="18" height="7" rx="1.5"/><circle cx="7" cy="7.5" r="0.6"/><circle cx="7" cy="16.5" r="0.6"/>',
    activity: '<path d="M22 12h-4l-3 8-4-16-3 8H2"/>',
    trash: '<path d="M3 6h18"/><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>',
    check: '<polyline points="20 6 9 17 4 12"/>',
    users: '<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
    info: '<circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/>'
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

  // Check tab visibility. Supports composite sections via TAB_ALIASES so a
  // single active section (e.g. "notifications") can surface multiple legacy
  // renderers ("sharepoint" + "teams").
  function isVisible(tabId, keywords) {
    const query = state.searchQuery.trim().toLowerCase();
    if (query) {
      return keywords.toLowerCase().includes(query);
    }
    if (state.activeTab === tabId) return true;
    const aliases = TAB_ALIASES[state.activeTab] || [];
    return aliases.includes(tabId);
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

  // Render header — unified slim page header (.kfh-phdr): title + search + reset.
  function renderHeader() {
    return `
      <div class="kfh-phdr">
        <div class="kfh-phdr-titlewrap"><h1 class="kfh-phdr-title">Settings</h1><span class="kfh-phdr-sub">Providers · databases · connections · system</span></div>
        <div class="kfh-phdr-search">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
          <input id="settings-search" type="text" placeholder="Search settings…" value="${esc(state.searchQuery || '')}">
        </div>
        <div class="kfh-phdr-ctrls">
          <button onclick="Settings.resetSettings()" class="kfh-phdr-chip">Reset</button>
        </div>
      </div>
    `;
  }

  function settingsMenuItems() {
    // KFH-focused enterprise-grade sub-nav (image 1). Each item is a section
    // rendered in the main pane. Icons come from the local `icons` map.
    return [
      { id: 'ai',             icon: 'cloud',    label: 'AI Providers',     hint: 'Azure OpenAI endpoints, deployments, keys' },
      { id: 'databases',      icon: 'database', label: 'Databases',        hint: 'Neo4j & database connectors' },
      { id: 'notifications',  icon: 'chat',     label: 'Notifications',    hint: 'SharePoint evidence + Microsoft Teams webhooks' },
      { id: 'infrastructure', icon: 'bolt',     label: 'Infrastructure',   hint: 'Redis, Kafka, index storage' },
      { id: 'connections',    icon: 'plug',     label: 'Connections',      hint: 'Data source connector catalog' },
      { id: 'system',         icon: 'gear',     label: 'System Variables', hint: 'Runtime, SSL, AI mode' }
    ];
  }

  function renderSettingsMenu() {
    const searching = state.searchQuery.trim().length > 0;
    return `
      <aside class="settings-local-sidebar kfh-settings-sidebar" aria-label="Settings sections">
        <div class="kfh-settings-search">
          <span class="kfh-settings-search-icon">${icon('search', 14)}</span>
          <input id="settings-search" type="text" placeholder="Search settings" class="kfh-settings-search-input" value="${esc(state.searchQuery)}" autocomplete="off">
        </div>
        ${searching ? '<div class="settings-menu-searching kfh-settings-menu-searching">Filtering variable matches</div>' : ''}
        <nav class="settings-tabs kfh-settings-tabs" aria-label="Settings section navigation">
          ${settingsMenuItems().map(item => {
            const isActive = state.activeTab === item.id;
            return `
            <button onclick="Settings.setTab('${item.id}')" class="settings-tab kfh-settings-tab ${isActive ? 'active' : ''}" aria-current="${isActive ? 'page' : 'false'}">
              <span class="settings-tab-icon">${icon(item.icon, 16)}</span>
              <span class="settings-tab-copy">
                <span class="settings-tab-label">${esc(item.label)}</span>
                <span class="settings-tab-hint">${esc(item.hint)}</span>
              </span>
            </button>`;
          }).join('')}
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
    if (state.connectionPopup) return renderConnectionModal();
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

  // ============================================================
  // Connections (enterprise-grade catalog + Add/View popup)
  // Consolidates the removed #connectors marketplace into a single
  // Settings section. All connector CRUD still goes through the
  // existing APIClient.connectors.* endpoints (no new backend).
  // ============================================================

  // Static fallback catalog mirrors the KFH plugin set. Used when
  // APIClient.connectors.types is unavailable, and to enrich descriptions
  // the backend may omit.
  const CONNECTION_CATALOG_FALLBACK = [
    { pluginType: 'BMC',         displayName: 'BMC Helix',         icon: 'server',   category: 'Event Management',        description: 'Ingest events from BMC Helix / TrueSight event management into the causal funnel.',        available: true },
    { pluginType: 'APPDYNAMICS', displayName: 'AppDynamics',       icon: 'activity', category: 'Application Performance', description: 'Ingest application errors, slow transactions and violations from AppDynamics.',           available: true },
    { pluginType: 'VROPS',       displayName: 'VMware vROps',      icon: 'cloud',    category: 'Infrastructure',          description: 'Pull virtualization metrics and alerts from vRealize Operations / Aria Operations.',      available: true },
    { pluginType: 'SCOM',        displayName: 'Microsoft SCOM',    icon: 'server',   category: 'Infrastructure',          description: 'Collect infrastructure alerts from System Center Operations Manager over WinRM / PowerShell.', available: true },
    { pluginType: 'EMCO',        displayName: 'EMCO Ping Monitor', icon: 'database', category: 'Network',                 description: 'Network availability and latency from EMCO Ping Monitor (SQL Server source).',           available: true }
  ];

  const CONNECTION_OWNER_TEAMS = ['Platform Ops', 'App Support', 'Network Ops', 'Storage Team', 'NOC'];
  const CONNECTION_ENVIRONMENTS = ['PROD', 'UAT', 'DEV'];
  const CONNECTION_COUNTRIES = ['KW', 'BH', 'EG'];

  function connectionCatalog() {
    if (Array.isArray(state.connectorTypes) && state.connectorTypes.length) {
      return state.connectorTypes.map(function (t) {
        const fallback = CONNECTION_CATALOG_FALLBACK.find(function (f) { return f.pluginType === (t.pluginType || t.value); }) || {};
        return {
          pluginType: t.pluginType || t.value,
          displayName: t.displayName || t.label || fallback.displayName || (t.pluginType || t.value),
          icon: t.icon || fallback.icon || 'server',
          category: t.category || fallback.category || 'Connector',
          description: t.description || fallback.description || 'Data source connector for governed telemetry collection.',
          available: t.available !== false,
          fields: Array.isArray(t.fields) ? t.fields : [],
          defaults: t.defaults || {}
        };
      });
    }
    return CONNECTION_CATALOG_FALLBACK.slice();
  }

  function connectionCatalogEntry(pluginType) {
    const catalog = connectionCatalog();
    return catalog.find(function (c) { return c.pluginType === pluginType; })
        || CONNECTION_CATALOG_FALLBACK.find(function (c) { return c.pluginType === pluginType; })
        || { pluginType: pluginType, displayName: pluginType, icon: 'server', category: 'Connector', description: '', available: true };
  }

  function normalizeConnectorRow(row) {
    if (!row || typeof row !== 'object') return null;
    // The DB store returns connector config at the row's top level (no nested `attributes`), so fall
    // back to the row itself — otherwise editing shows empty Base URL / SCOM fields (config not reloaded).
    const attrs = (row.attributes && typeof row.attributes === 'object') ? row.attributes : row;
    const pluginType = row.type || row.pluginType || attrs.pluginType || attrs.type || '';
    return {
      raw: row,
      id: row.id || row.connectorId || attrs.id || null,
      name: row.name || attrs.name || pluginType,
      enabled: row.enabled !== false,
      pluginType: pluginType,
      countryCode: row.countryCode || attrs.countryCode || '',
      environment: row.environmentScope || row.environment || attrs.environment || attrs.environmentScope || '',
      ownerTeam: row.ownerTeam || attrs.ownerTeam || '',
      baseUrl: row.baseUrl || attrs.baseUrl || attrs.endpoint || attrs.controllerUrl || attrs.host || attrs.sqlServer || '',
      authMode: row.authMode || attrs.authMode || defaultAuthModeFor(pluginType),
      lastTestStatus: row.lastTestStatus || attrs.lastTestStatus || '',
      lastSyncAt: row.lastSyncAt || row.lastRunAt || attrs.lastSyncAt || null,
      attributes: attrs
    };
  }

  function defaultAuthModeFor(pluginType) {
    switch (pluginType) {
      case 'BMC': return 'AccessKey';
      case 'APPDYNAMICS': return 'BasicAuth';
      case 'VROPS': return 'Token';
      case 'SCOM': return 'WinRM';
      case 'EMCO': return 'SqlServerCredentials';
      default: return 'BasicAuth';
    }
  }

  async function loadConnectors() {
    if (!window.APIClient || !APIClient.connectors || !APIClient.connectors.list) return;
    state.connectorsLoading = true;
    state.connectorsError = null;
    try {
      const response = await APIClient.connectors.list();
      const rows = response && Array.isArray(response.content) ? response.content : Array.isArray(response) ? response : [];
      state.connectors = rows.map(normalizeConnectorRow).filter(Boolean);
      state.connectorsLoaded = true;
    } catch (error) {
      state.connectorsError = errorMessage(error, 'Unable to load connectors');
      state.connectorsLoaded = true;
    } finally {
      state.connectorsLoading = false;
      // Only re-render when the Connections section is visible so we don't
      // trash other sections mid-interaction.
      if (state.activeTab === 'connections' || state.searchQuery) render();
    }
  }

  async function loadConnectorTypes() {
    if (!window.APIClient || !APIClient.connectors || !APIClient.connectors.types) return;
    try {
      const response = await APIClient.connectors.types();
      const rows = response && Array.isArray(response.content) ? response.content : Array.isArray(response) ? response : [];
      if (rows.length) state.connectorTypes = rows;
    } catch (error) {
      // Silent fallback to the static catalog.
      console.warn('[Settings] Connector types API unavailable — using static catalog', error);
    } finally {
      if (state.activeTab === 'connections' || state.searchQuery) render();
    }
  }

  function connectorsForType(pluginType) {
    return (state.connectors || []).filter(function (c) { return c.pluginType === pluginType; });
  }

  // Human-friendly "last sync" for the connections table. null/never → "Never".
  function formatConnLastSync(ts) {
    if (!ts) return 'Never';
    try {
      var d = new Date(ts);
      if (isNaN(d.getTime())) return String(ts);
      var diff = Date.now() - d.getTime();
      if (diff < 0) return d.toLocaleString();
      if (diff < 60000) return 'just now';
      var m = Math.floor(diff / 60000);
      if (m < 60) return m + 'm ago';
      var h = Math.floor(m / 60);
      if (h < 24) return h + 'h ago';
      var days = Math.floor(h / 24);
      if (days < 7) return days + 'd ago';
      return d.toLocaleDateString();
    } catch (e) {
      return String(ts);
    }
  }

  // Enable/disable a single connection. Optimistic UI, then persist via connectors.toggle;
  // once enabled the scheduled poller (kfh.ingestion.*.enabled) collects on its interval.
  function toggleConnectionEnabled(id, enable) {
    if (!id) return;
    var row = (state.connectors || []).find(function (c) { return String(c.id) === String(id); });
    var previous = row ? row.enabled : undefined;
    if (row) row.enabled = enable;         // optimistic
    render();
    if (!(window.APIClient && APIClient.connectors && APIClient.connectors.toggle)) return;
    APIClient.connectors.toggle(id, enable)
      .then(function () { return loadConnectors(); })
      .then(function () {
        render();
        if (window.KFHUtils && KFHUtils.showToast) {
          KFHUtils.showToast('Connection ' + (enable ? 'enabled — collection will run on schedule' : 'disabled'), 'success');
        }
      })
      .catch(function (err) {
        if (row) row.enabled = previous;   // roll back
        render();
        if (window.KFHUtils && KFHUtils.showToast) {
          KFHUtils.showToast('Could not ' + (enable ? 'enable' : 'disable') + ' connection: ' + ((err && err.message) || 'request failed'), 'error');
        }
      });
  }

  function renderConnections() {
    if (!isVisible('connections', 'connections connector plugin bmc scom vrops appdynamics emco integrations catalog data source')) return '';
    if (state.connectionDetailType) {
      return renderConnectionDetailPage(state.connectionDetailType);
    }
    const catalog = connectionCatalog();
    const rows = catalog.map(function (entry) {
      const count = connectorsForType(entry.pluginType).length;
      const disabled = entry.available === false;
      return `
        <button type="button" class="kfh-conn-row" ${disabled ? 'disabled aria-disabled="true"' : `onclick="Settings.openConnectionDetail('${esc(entry.pluginType)}')"`}>
          <span class="kfh-conn-row-ic kfh-conn-ic-${esc(String(entry.pluginType).toLowerCase())}">${icon(entry.icon || 'server', 20)}</span>
          <span class="kfh-conn-row-body">
            <span class="kfh-conn-row-title">${esc(entry.displayName)}${disabled ? ' <span class="kfh-conn-row-soon">Coming soon</span>' : ''}</span>
            <span class="kfh-conn-row-desc">${esc(entry.description || '')}</span>
          </span>
          <span class="kfh-conn-row-meta">
            <span class="kfh-conn-row-cat">${esc(entry.category || '')}</span>
            <span class="kfh-conn-row-count" title="Existing connections in this tenant">${count} conn.</span>
            <span class="kfh-conn-row-chev" aria-hidden="true">›</span>
          </span>
        </button>`;
    }).join('');

    const loadingBanner = state.connectorsLoading && !state.connectorsLoaded
      ? '<div class="kfh-conn-hint">Loading tenant connectors…</div>' : '';
    const errorBanner = state.connectorsError
      ? `<div class="kfh-conn-hint kfh-conn-hint-error">${esc(state.connectorsError)}</div>` : '';

    return `
      <div class="animate-fade-in">
        <div class="kfh-conn-shell">
          ${loadingBanner}
          ${errorBanner}
          <div class="kfh-conn-list" role="list">${rows}</div>
        </div>
      </div>
    `;
  }

  // ---- Connector detail page (enterprise observability "Connections › <Name>") --------

  function renderConnectionDetailPage(pluginType) {
    const entry = connectionCatalogEntry(pluginType);
    const all = connectorsForType(pluginType);
    const q = String(state.connectionDetailSearch || '').trim().toLowerCase();
    const ownerFilter = String(state.connectionDetailOwner || '').trim();
    const filtered = all.filter(function (row) {
      if (q && !(String(row.name || '').toLowerCase().includes(q))) return false;
      if (ownerFilter && (row.ownerTeam || '') !== ownerFilter) return false;
      return true;
    });

    const owners = Array.from(new Set(all.map(function (r) { return r.ownerTeam || ''; }).filter(Boolean))).sort();
    const ownerOptions = ['<option value="">All connections</option>']
      .concat(owners.map(function (o) { return `<option value="${esc(o)}" ${ownerFilter === o ? 'selected' : ''}>${esc(o)}</option>`; }))
      .join('');

    const tableRows = filtered.length ? filtered.map(function (row) {
      const shareCount = Array.isArray(row.attributes && row.attributes.sharedWithTeams) ? row.attributes.sharedWithTeams.length : 0;
      const shareIcon = shareCount > 0
        ? `<span class="kfh-conn-detail-shared" title="Shared with ${shareCount} team(s)">${icon('users', 14)}</span>`
        : '';
      const enabled = row.enabled !== false;
      const country = row.countryCode ? esc(row.countryCode) : '—';
      const attrs = row.attributes || {};
      const interval = attrs.intervalMin || attrs.pollIntervalMin;
      const schedule = interval ? `${esc(interval)} min` : '<span class="kfh-conn-detail-inherit">System default</span>';
      const lastSync = esc(formatConnLastSync(row.lastSyncAt));
      const status = enabled
        ? '<span class="kfh-conn-status kfh-conn-status-on">● Enabled</span>'
        : '<span class="kfh-conn-status kfh-conn-status-off">○ Disabled</span>';
      const toggle = `<button type="button" class="kfh-conn-toggle ${enabled ? 'is-on' : ''}" title="${enabled ? 'Disable' : 'Enable'} connection"
              onclick="event.stopPropagation(); Settings.toggleConnectionEnabled('${esc(row.id)}', ${!enabled})">${enabled ? 'Disable' : 'Enable'}</button>`;
      return `
        <tr class="kfh-conn-detail-row" onclick="Settings.openConnectionEditor('${esc(row.id)}')">
          <td class="kfh-conn-detail-col-name">
            <span class="kfh-conn-detail-name">${esc(row.name || '(unnamed)')}</span>
            ${shareIcon}
          </td>
          <td class="kfh-conn-detail-col-country">${country}</td>
          <td class="kfh-conn-detail-col-sched">${schedule}</td>
          <td class="kfh-conn-detail-col-sync">${lastSync}</td>
          <td class="kfh-conn-detail-col-status">${status}</td>
          <td class="kfh-conn-detail-col-actions" onclick="event.stopPropagation()">
            ${toggle}
            <button type="button" class="kfh-conn-detail-menu" title="Configure" onclick="Settings.openConnectionEditor('${esc(row.id)}')" aria-label="Open connection">⋯</button>
          </td>
        </tr>`;
    }).join('') : `
      <tr><td colspan="6" class="kfh-conn-detail-empty">
        ${all.length === 0
          ? `No connections yet. Use <strong>+ Connection</strong> to add your first ${esc(entry.displayName)} connection.`
          : 'No connections match your filters.'}
      </td></tr>`;

    const total = filtered.length;
    const pageOf = total === 0 ? 0 : 1;
    const learnMoreLink = entry.docsUrl
      ? ` <a class="kfh-conn-detail-learn" href="${esc(entry.docsUrl)}" target="_blank" rel="noopener">Learn more ↗</a>`
      : '';

    return `
      <div class="animate-fade-in">
        <div class="kfh-conn-shell kfh-conn-detail-shell">
          <div class="kfh-conn-detail-crumb">
            <a href="#" class="kfh-conn-detail-crumb-link" onclick="event.preventDefault(); Settings.closeConnectionDetail()">Connections</a>
            <span class="kfh-conn-detail-crumb-sep">›</span>
            <span class="kfh-conn-detail-crumb-current">${esc(entry.displayName)}</span>
          </div>
          <div class="kfh-conn-detail-header">
            <span class="kfh-conn-detail-ic kfh-conn-ic-${esc(String(entry.pluginType).toLowerCase())}">${icon(entry.icon || 'server', 26)}</span>
            <div class="kfh-conn-detail-header-body">
              <h1 class="kfh-conn-detail-title">${esc(entry.displayName)}</h1>
              <p class="kfh-conn-detail-desc">${esc(entry.description || '')}${learnMoreLink}</p>
            </div>
          </div>

          <div class="kfh-conn-detail-toolbar">
            <div class="kfh-conn-detail-filter">
              <label class="kfh-conn-detail-flabel">Name</label>
              <input type="text" class="kfh-conn-detail-fsearch" value="${esc(state.connectionDetailSearch)}"
                     placeholder="Search by name" oninput="Settings.setConnectionDetailSearch(this.value)">
            </div>
            <div class="kfh-conn-detail-filter">
              <label class="kfh-conn-detail-flabel">Owner</label>
              <select class="kfh-conn-detail-fselect" onchange="Settings.setConnectionDetailOwner(this.value)">${ownerOptions}</select>
            </div>
            <div class="kfh-conn-detail-toolbar-spacer"></div>
            <button type="button" class="settings-btn settings-btn-primary kfh-conn-detail-add"
                    onclick="Settings.openConnectionPopup('${esc(entry.pluginType)}', null, true)">${icon('plus', 14)} Connection</button>
          </div>

          <div class="kfh-conn-detail-table-wrap">
            <table class="kfh-conn-detail-table">
              <thead>
                <tr>
                  <th class="kfh-conn-detail-col-name">Connection</th>
                  <th class="kfh-conn-detail-col-country">Country</th>
                  <th class="kfh-conn-detail-col-sched">Schedule</th>
                  <th class="kfh-conn-detail-col-sync">Last sync</th>
                  <th class="kfh-conn-detail-col-status">Status</th>
                  <th class="kfh-conn-detail-col-actions" aria-label="Actions"></th>
                </tr>
              </thead>
              <tbody>${tableRows}</tbody>
            </table>
          </div>

          <div class="kfh-conn-detail-footer">
            <div class="kfh-conn-detail-rows-per-page">
              <select class="kfh-conn-detail-fselect" disabled aria-label="Rows per page"><option>100</option></select>
              <span>rows per page</span>
            </div>
            <div class="kfh-conn-detail-pager">
              <span>Page <strong>${pageOf}</strong> of <strong>${pageOf}</strong></span>
              <button type="button" class="kfh-conn-detail-pager-btn" disabled aria-label="Previous page">‹</button>
              <button type="button" class="kfh-conn-detail-pager-btn" disabled aria-label="Next page">›</button>
            </div>
          </div>
        </div>
      </div>
    `;
  }

  function openConnectionDetail(pluginType) {
    state.connectionDetailType = pluginType;
    state.connectionDetailSearch = '';
    state.connectionDetailOwner = '';
    render();
  }

  function closeConnectionDetail() {
    state.connectionDetailType = null;
    state.connectionDetailSearch = '';
    state.connectionDetailOwner = '';
    render();
  }

  function setConnectionDetailSearch(value) {
    state.connectionDetailSearch = String(value || '');
    render();
    const el = document.querySelector('.kfh-conn-detail-fsearch');
    if (el) { el.focus(); el.setSelectionRange(el.value.length, el.value.length); }
  }

  function setConnectionDetailOwner(value) {
    state.connectionDetailOwner = String(value || '');
    render();
  }

  // ---- Add / View connection popup ------------------------------------

  function newConnectionDraft(pluginType) {
    const entry = connectionCatalogEntry(pluginType);
    const defaults = entry.defaults || {};
    const s = session();
    const country = CONNECTION_COUNTRIES.includes(s.countryCode) ? s.countryCode : 'KW';
    // Environment concept removed from the UI: always bind to the active session environment so a
    // saved connector is never hidden by a scope mismatch on reload (the list query uses the same env).
    const environment = s.environment || 'PROD';
    return {
      pluginType: pluginType,
      name: '',
      enabled: true,
      countryCode: country,
      environment: environment,
      ownerTeam: '',
      intervalMin: '',
      authMode: defaults.authMode || defaultAuthModeFor(pluginType),
      baseUrl: defaults.baseUrl || '',
      // Credential fields (per auth mode). Kept in-memory only, cleared on close.
      accessKey: '',
      accessSecretKey: '',
      username: '',
      password: '',
      token: '',
      // Plugin-specific fields
      managementServer: '',
      domain: '',
      winrmPort: 5986,
      useHttps: true,
      sqlServer: '',
      sqlPort: 1433,
      kfhDatabase: '',
      cctvDatabase: '',
      kfhUsername: '',
      kfhPassword: '',
      cctvUsername: '',
      cctvPassword: '',
      sharedWithTeams: []
    };
  }

  function draftFromExisting(row) {
    if (!row) return newConnectionDraft('BMC');
    // Reload config from BOTH the raw backend row (the DB store returns config at the top level) and
    // any nested attributes object, so editing always shows the saved values — you never re-enter the
    // URL/host/etc. Only credentials stay blank ("leave blank to keep existing").
    const src = Object.assign({}, row.raw || {}, row.attributes || {});
    return {
      pluginType: row.pluginType || src.pluginType,
      id: row.id,
      name: row.name || src.name,
      enabled: row.enabled !== false,
      countryCode: row.countryCode || src.countryCode || 'KW',
      environment: row.environment || src.environment || 'PROD',
      ownerTeam: row.ownerTeam || src.ownerTeam || '',
      intervalMin: src.intervalMin || src.pollIntervalMin || '',
      authMode: row.authMode || src.authMode || defaultAuthModeFor(row.pluginType),
      baseUrl: row.baseUrl || src.baseUrl || src.endpointUrl || src.controllerUrl || src.host || src.sqlServer || '',
      // Credentials never come back from the API in plaintext — leave blank to keep existing.
      accessKey: '', accessSecretKey: '', username: '', password: '', token: '',
      managementServer: src.managementServer || '',
      domain: src.domain || '',
      winrmPort: src.winrmPort || 5986,
      useHttps: src.useHttps !== false,
      sqlServer: src.sqlServer || '',
      sqlPort: src.sqlPort || 1433,
      kfhDatabase: src.kfhDatabase || '',
      cctvDatabase: src.cctvDatabase || '',
      kfhUsername: '', kfhPassword: '', cctvUsername: '', cctvPassword: '',
      sharedWithTeams: Array.isArray(src.sharedWithTeams) ? src.sharedWithTeams.slice() : []
    };
  }

  function openConnectionPopup(pluginType, connectorId, forceForm) {
    const existing = connectorId ? (state.connectors.find(function (c) { return c.id === connectorId; }) || null) : null;
    let mode;
    if (existing) {
      mode = 'form';
    } else if (forceForm === true) {
      mode = 'form';
    } else {
      // Legacy behaviour (deep-linking from other pages without the detail
      // page): if there are existing connections of this type show the
      // catalog list, otherwise open the empty form.
      mode = connectorsForType(pluginType).length > 0 ? 'catalog' : 'form';
    }
    state.connectionPopup = {
      pluginType: pluginType,
      mode: mode,
      activeTab: 'setup',
      connectorId: existing ? existing.id : null,
      draft: existing ? draftFromExisting(existing) : newConnectionDraft(pluginType),
      testing: false,
      saving: false,
      deleting: false,
      readOnly: !canWriteConnectors(),
      message: null,
      messageType: 'info'
    };
    render();
  }

  function canWriteConnectors() {
    const perms = (session().permissions || []);
    if (perms.includes('*')) return true;
    // No explicit permission flag today → treat operator as writer, but
    // never fabricate credentials or bypass server-side auth.
    return true;
  }

  function closeConnectionPopup() {
    // Wipe sensitive fields from memory before releasing the draft.
    if (state.connectionPopup && state.connectionPopup.draft) {
      const d = state.connectionPopup.draft;
      d.accessKey = ''; d.accessSecretKey = ''; d.password = ''; d.token = '';
      d.kfhPassword = ''; d.cctvPassword = '';
    }
    state.connectionPopup = null;
    render();
  }

  function switchConnectionTab(tab) {
    if (!state.connectionPopup) return;
    if (tab !== 'setup' && tab !== 'share') return;
    state.connectionPopup.activeTab = tab;
    render();
  }

  function setConnectionPopupMode(mode) {
    if (!state.connectionPopup) return;
    state.connectionPopup.mode = mode;
    if (mode === 'form' && !state.connectionPopup.connectorId) {
      state.connectionPopup.draft = newConnectionDraft(state.connectionPopup.pluginType);
    }
    render();
  }

  function updateConnectionField(field, value) {
    if (!state.connectionPopup || !state.connectionPopup.draft) return;
    state.connectionPopup.draft[field] = value;
    // Only fields that affect visible controls need a re-render.
    if (field === 'authMode' || field === 'countryCode' || field === 'environment' || field === 'enabled') {
      render();
    }
  }

  function toggleConnectionShareTeam(team) {
    if (!state.connectionPopup || !state.connectionPopup.draft) return;
    const list = state.connectionPopup.draft.sharedWithTeams || [];
    const idx = list.indexOf(team);
    if (idx >= 0) list.splice(idx, 1); else list.push(team);
    state.connectionPopup.draft.sharedWithTeams = list;
    render();
  }

  function connectionEditPopup(connectorId) {
    const row = state.connectors.find(function (c) { return c.id === connectorId; });
    if (!row) return;
    openConnectionPopup(row.pluginType, row.id);
    // Ensure form mode
    if (state.connectionPopup) {
      state.connectionPopup.mode = 'form';
      state.connectionPopup.connectorId = row.id;
      render();
    }
  }

  function buildConnectionPayload(draft) {
    const secretsPlain = {};
    if (draft.pluginType === 'BMC') {
      if (draft.accessKey) secretsPlain.accessKey = draft.accessKey;
      if (draft.accessSecretKey) secretsPlain.accessSecretKey = draft.accessSecretKey;
    } else if (draft.pluginType === 'APPDYNAMICS') {
      if (draft.username) secretsPlain.username = draft.username;
      if (draft.password) secretsPlain.password = draft.password;
    } else if (draft.pluginType === 'VROPS') {
      if (draft.token) secretsPlain.token = draft.token;
    } else if (draft.pluginType === 'SCOM') {
      if (draft.username) secretsPlain.username = draft.username;
      if (draft.password) secretsPlain.password = draft.password;
    } else if (draft.pluginType === 'EMCO') {
      if (draft.kfhUsername) secretsPlain.kfhUsername = draft.kfhUsername;
      if (draft.kfhPassword) secretsPlain.kfhPassword = draft.kfhPassword;
      if (draft.cctvUsername) secretsPlain.cctvUsername = draft.cctvUsername;
      if (draft.cctvPassword) secretsPlain.cctvPassword = draft.cctvPassword;
    }

    const attributes = {
      pluginType: draft.pluginType,
      countryCode: draft.countryCode,
      environment: draft.environment,
      environmentScope: draft.environment,
      ownerTeam: draft.ownerTeam || null,
      intervalMin: draft.intervalMin ? Number(draft.intervalMin) : null,
      authMode: draft.authMode,
      baseUrl: draft.baseUrl || null,
      sharedWithTeams: Array.isArray(draft.sharedWithTeams) ? draft.sharedWithTeams.slice() : []
    };
    if (draft.pluginType === 'SCOM') {
      attributes.managementServer = draft.managementServer || null;
      attributes.domain = draft.domain || null;
      attributes.winrmPort = Number(draft.winrmPort) || 5986;
      attributes.useHttps = draft.useHttps !== false;
    }
    if (draft.pluginType === 'EMCO') {
      attributes.sqlServer = draft.sqlServer || null;
      attributes.sqlPort = Number(draft.sqlPort) || 1433;
      attributes.kfhDatabase = draft.kfhDatabase || null;
      attributes.cctvDatabase = draft.cctvDatabase || null;
    }
    if (Object.keys(secretsPlain).length) {
      attributes.secretsPlain = secretsPlain;
    }
    return { name: draft.name, enabled: draft.enabled !== false, attributes: attributes };
  }

  function validateConnectionDraft(draft) {
    if (!draft.name || !draft.name.trim()) return 'Connection name is required.';
    if (!draft.countryCode) return 'Country is required.';
    if (!draft.environment) return 'Environment is required.';
    if (['BMC', 'APPDYNAMICS', 'VROPS'].includes(draft.pluginType) && !draft.baseUrl.trim()) {
      return 'Endpoint / Base URL is required.';
    }
    // On create, require the credentials required by the auth mode.
    if (!state.connectionPopup.connectorId) {
      if (draft.pluginType === 'BMC' && (!draft.accessKey || !draft.accessSecretKey)) {
        return 'Access key and access secret key are required.';
      }
      if ((draft.pluginType === 'APPDYNAMICS' || draft.pluginType === 'SCOM') && (!draft.username || !draft.password)) {
        return 'Username and password are required.';
      }
      if (draft.pluginType === 'VROPS' && !draft.token) return 'API token is required.';
      if (draft.pluginType === 'EMCO' && (!draft.kfhUsername || !draft.kfhPassword)) {
        return 'KFH SQL Server credentials are required.';
      }
    }
    return null;
  }

  async function saveConnection() {
    if (!state.connectionPopup) return;
    const popup = state.connectionPopup;
    const draft = popup.draft;
    const err = validateConnectionDraft(draft);
    if (err) {
      popup.message = err; popup.messageType = 'error';
      render();
      return;
    }
    if (!window.APIClient || !APIClient.connectors) {
      popup.message = 'Connector API is not available.'; popup.messageType = 'error';
      render();
      return;
    }
    const payload = buildConnectionPayload(draft);
    popup.saving = true; popup.message = null;
    render();
    try {
      if (popup.connectorId) {
        await APIClient.connectors.update(popup.connectorId, payload);
        toast('Connection updated', 'success');
      } else {
        await APIClient.connectors.create(payload);
        toast('Connection saved', 'success');
      }
      // Wipe secrets, refresh the list, close the popup.
      draft.accessKey = draft.accessSecretKey = draft.password = draft.token = '';
      draft.kfhPassword = draft.cctvPassword = '';
      state.connectionPopup = null;
      await loadConnectors();
      render();
    } catch (error) {
      popup.saving = false;
      popup.message = errorMessage(error, 'Unable to save connection');
      popup.messageType = 'error';
      render();
    }
  }

  // Enable/disable an existing connector directly from its editor popup (persists via the API).
  async function togglePopupConnector() {
    const popup = state.connectionPopup;
    if (!popup || !popup.connectorId) return;
    const enable = popup.draft.enabled === false; // currently disabled → enable, else disable
    if (!(window.APIClient && APIClient.connectors && APIClient.connectors.toggle)) {
      popup.message = 'Connector API is not available.'; popup.messageType = 'error'; render(); return;
    }
    popup.saving = true; popup.message = null; render();
    try {
      await APIClient.connectors.toggle(popup.connectorId, enable);
      popup.draft.enabled = enable;
      popup.message = enable ? 'Connection enabled — it will collect on its schedule.' : 'Connection disabled.';
      popup.messageType = 'success';
      await loadConnectors();
    } catch (error) {
      popup.message = errorMessage(error, 'Unable to change connection status');
      popup.messageType = 'error';
    }
    popup.saving = false; render();
  }

  async function testConnection() {
    if (!state.connectionPopup) return;
    const popup = state.connectionPopup;
    if (!popup.connectorId) {
      popup.message = 'Save first, then test — a connector must exist before it can be probed.';
      popup.messageType = 'info';
      render();
      return;
    }
    if (!window.APIClient || !APIClient.connectors || !APIClient.connectors.test) return;
    popup.testing = true; popup.message = null;
    render();
    try {
      const result = await APIClient.connectors.test(popup.connectorId);
      const status = normalizeStatus(result && (result.status || (result.pass === false ? 'Fail' : 'Pass')));
      popup.message = status === 'Pass'
        ? ((result && result.message) || 'Connection test passed.')
        : ((result && result.message) || 'Connection test failed.');
      popup.messageType = status === 'Pass' ? 'success' : 'error';
    } catch (error) {
      popup.message = errorMessage(error, 'Connection test failed');
      popup.messageType = 'error';
    } finally {
      popup.testing = false;
      render();
    }
  }

  async function deleteConnection() {
    if (!state.connectionPopup || !state.connectionPopup.connectorId) return;
    if (!confirm('Delete this connection? This action cannot be undone.')) return;
    const popup = state.connectionPopup;
    popup.deleting = true;
    render();
    try {
      await APIClient.connectors.delete(popup.connectorId);
      toast('Connection deleted', 'info');
      state.connectionPopup = null;
      await loadConnectors();
      render();
    } catch (error) {
      popup.deleting = false;
      popup.message = errorMessage(error, 'Unable to delete connection');
      popup.messageType = 'error';
      render();
    }
  }

  function renderConnectionModal() {
    const popup = state.connectionPopup;
    if (!popup) return '';
    const entry = connectionCatalogEntry(popup.pluginType);
    const existing = connectorsForType(popup.pluginType);
    const isEditing = Boolean(popup.connectorId);
    const isCatalog = popup.mode === 'catalog' && existing.length > 0 && !isEditing;
    const heading = isEditing ? 'View connection' : (isCatalog ? `${entry.displayName} — Connections` : 'Add connection');

    let body;
    if (isCatalog) {
      body = renderConnectionCatalogList(entry, existing);
    } else {
      body = renderConnectionForm(entry, popup);
    }

    return `
      <div id="settings-modal-overlay" class="settings-modal-overlay open kfh-conn-modal-overlay" onclick="Settings.keepConnectionPopup(event)">
        <div class="settings-modal kfh-conn-modal">
          <div class="settings-modal-header kfh-conn-modal-header">
            <div class="kfh-conn-modal-title-wrap">
              <span class="kfh-conn-modal-ic kfh-conn-ic-${esc(String(entry.pluginType).toLowerCase())}">${icon(entry.icon || 'server', 18)}</span>
              <div>
                <h3 class="settings-modal-title kfh-conn-modal-title">${esc(heading)}</h3>
                <p class="settings-modal-subtitle kfh-conn-modal-sub">${esc(entry.displayName)} · ${esc(entry.category || '')}</p>
              </div>
            </div>
            <button onclick="Settings.closeConnectionPopup()" class="settings-modal-close" aria-label="Close">${icon('close', 20)}</button>
          </div>
          ${body}
        </div>
      </div>
    `;
  }

  function renderConnectionCatalogList(entry, existing) {
    const rows = existing.map(function (row) {
      const status = row.enabled ? 'Enabled' : 'Disabled';
      const scope = `${esc(row.countryCode || '—')} · ${esc(row.environment || '—')}`;
      return `
        <div class="kfh-conn-inst-row">
          <div class="kfh-conn-inst-body">
            <div class="kfh-conn-inst-name">${esc(row.name)}</div>
            <div class="kfh-conn-inst-meta">${scope} · ${esc(row.authMode || '')}</div>
          </div>
          <div class="kfh-conn-inst-status kfh-conn-inst-status-${row.enabled ? 'ok' : 'off'}">${esc(status)}</div>
          <div class="kfh-conn-inst-actions">
            <button type="button" class="settings-btn settings-btn-outline" onclick="Settings.openConnectionEditor('${esc(row.id)}')">View / Edit</button>
          </div>
        </div>`;
    }).join('');

    return `
      <div class="settings-modal-body kfh-conn-modal-body">
        <div class="kfh-conn-banner kfh-conn-banner-info">
          <span class="kfh-conn-banner-ic">${icon('info', 16)}</span>
          <div>${esc(entry.description || '')}</div>
        </div>
        <div class="kfh-conn-inst-head">
          <div class="kfh-conn-inst-head-title">Existing connections (${existing.length})</div>
          <button type="button" class="settings-btn settings-btn-primary" onclick="Settings.setConnectionPopupMode('form')">${icon('plus', 14)} New connection</button>
        </div>
        <div class="kfh-conn-inst-list">${rows}</div>
      </div>
      <div class="settings-modal-footer kfh-conn-modal-footer">
        <button type="button" onclick="Settings.closeConnectionPopup()" class="settings-btn settings-btn-primary">Close</button>
      </div>
    `;
  }

  function renderConnectionForm(entry, popup) {
    const draft = popup.draft;
    const testing = popup.testing;
    const saving = popup.saving;
    const readOnly = popup.readOnly === true;
    const isEditing = Boolean(popup.connectorId);
    const activeTab = popup.activeTab || 'setup';

    const infoBanner = readOnly
      ? `<div class="kfh-conn-banner kfh-conn-banner-info"><span class="kfh-conn-banner-ic">${icon('info', 16)}</span><div>You do not have the permission to change any values in this dialog.</div></div>`
      : `<div class="kfh-conn-banner kfh-conn-banner-info"><span class="kfh-conn-banner-ic">${icon('info', 16)}</span><div>You are ${isEditing ? 'viewing' : 'creating a new connection to'} <strong>${esc(entry.displayName)}</strong>.</div></div>`;

    const msg = popup.message
      ? `<div class="kfh-conn-msg kfh-conn-msg-${esc(popup.messageType || 'info')}">${esc(popup.message)}</div>`
      : '';

    const setupBody = renderConnectionSetupFields(entry, draft, readOnly, isEditing);

    return `
      <div class="settings-modal-body kfh-conn-modal-body">
        ${infoBanner}
        ${msg}
        <div class="kfh-conn-tab-panel">
          ${setupBody}
        </div>
      </div>
      <div class="settings-modal-footer kfh-conn-modal-footer">
        ${isEditing && !readOnly ? `<button type="button" class="settings-btn settings-btn-danger-text" onclick="Settings.deleteConnection()" ${popup.deleting ? 'disabled' : ''}>${popup.deleting ? 'Deleting…' : 'Delete this connection'}</button>` : '<span></span>'}
        <div class="kfh-conn-modal-footer-actions">
          ${isEditing ? `<button type="button" class="settings-btn ${draft.enabled !== false ? 'settings-btn-outline' : 'settings-btn-primary'}" onclick="Settings.togglePopupConnector()" ${saving || readOnly ? 'disabled' : ''} title="${draft.enabled !== false ? 'Stop this connection collecting' : 'Enable this connection to start collecting'}">${draft.enabled !== false ? 'Disable' : 'Enable'}</button>` : ''}
          <button type="button" class="settings-btn settings-btn-outline" onclick="Settings.testConnection()" ${testing || readOnly ? 'disabled' : ''}>${testing ? 'Testing…' : 'Test connection'}</button>
          <button type="button" class="settings-btn settings-btn-primary" onclick="Settings.saveConnection()" ${saving || readOnly ? 'disabled' : ''}>${saving ? 'Saving…' : (isEditing ? 'Save' : 'Save')}</button>
          <button type="button" class="settings-btn settings-btn-outline" onclick="Settings.closeConnectionPopup()">Close</button>
        </div>
      </div>
    `;
  }

  function renderConnectionSetupFields(entry, draft, readOnly, isEditing) {
    const ro = readOnly ? 'readonly disabled' : '';
    const authOptions = ['AccessKey', 'BasicAuth', 'Token', 'WinRM', 'SqlServerCredentials']
      .map(function (m) { return `<option value="${m}" ${draft.authMode === m ? 'selected' : ''}>${m}</option>`; }).join('');
    const countryOptions = CONNECTION_COUNTRIES.map(function (c) {
      return `<option value="${c}" ${draft.countryCode === c ? 'selected' : ''}>${c}</option>`;
    }).join('');
    const envOptions = CONNECTION_ENVIRONMENTS.map(function (e) {
      return `<option value="${e}" ${draft.environment === e ? 'selected' : ''}>${e}</option>`;
    }).join('');
    const teamOptions = ['<option value="">— None —</option>'].concat(CONNECTION_OWNER_TEAMS.map(function (t) {
      return `<option value="${t}" ${draft.ownerTeam === t ? 'selected' : ''}>${t}</option>`;
    })).join('');
    const currentInterval = draft.intervalMin ? Number(draft.intervalMin) : '';
    const scheduleOptions = [
      { value: '', label: 'System default (15 min)' },
      { value: 5, label: 'Every 5 minutes' },
      { value: 10, label: 'Every 10 minutes' },
      { value: 15, label: 'Every 15 minutes' },
      { value: 30, label: 'Every 30 minutes' },
      { value: 60, label: 'Every hour' },
      { value: 120, label: 'Every 2 hours' },
      { value: 360, label: 'Every 6 hours' }
    ].map(function (o) {
      return `<option value="${o.value}" ${currentInterval === o.value ? 'selected' : ''}>${o.label}</option>`;
    }).join('');

    return `
      <div class="kfh-conn-form">
        <div class="kfh-conn-field kfh-conn-field-wide">
          <label class="kfh-conn-label">Connection name<span class="kfh-conn-req">*</span></label>
          <input type="text" class="kfh-conn-input" value="${esc(draft.name)}" oninput="Settings.updateConnectionField('name', this.value)" placeholder="e.g. kfh-${esc(String(entry.pluginType).toLowerCase())}-kw-prod" ${ro}>
          <small class="kfh-conn-help">Unique and clearly identifiable connection name.</small>
        </div>
        <div class="kfh-conn-field">
          <label class="kfh-conn-label">Type</label>
          <select class="kfh-conn-input" disabled aria-disabled="true"><option>${esc(entry.displayName)}</option></select>
          <small class="kfh-conn-help">${esc(entry.category || '')}${isEditing ? ' · read-only when editing' : ''}</small>
        </div>
        <div class="kfh-conn-field kfh-conn-field-wide">
          <label class="kfh-conn-label">Country</label>
          <select class="kfh-conn-input" onchange="Settings.updateConnectionField('countryCode', this.value)" ${ro}>${countryOptions}</select>
        </div>
        ${['BMC','APPDYNAMICS','VROPS'].includes(draft.pluginType) ? `
        <div class="kfh-conn-field kfh-conn-field-wide">
          <label class="kfh-conn-label">Endpoint / Base URL<span class="kfh-conn-req">*</span></label>
          <input type="url" class="kfh-conn-input" value="${esc(draft.baseUrl)}" oninput="Settings.updateConnectionField('baseUrl', this.value)" placeholder="https://…" ${ro}>
          <small class="kfh-conn-help">Full URL of the source system. Must resolve from the KFH AIOps collector network.</small>
        </div>` : ''}
        <div class="kfh-conn-field">
          <label class="kfh-conn-label">Authentication mode</label>
          <select class="kfh-conn-input" onchange="Settings.updateConnectionField('authMode', this.value)" ${ro}>${authOptions}</select>
        </div>
        <div class="kfh-conn-field">
          <label class="kfh-conn-label">Owner team</label>
          <select class="kfh-conn-input" onchange="Settings.updateConnectionField('ownerTeam', this.value)" ${ro}>${teamOptions}</select>
        </div>
        <div class="kfh-conn-field">
          <label class="kfh-conn-label">Ingestion schedule</label>
          <select class="kfh-conn-input" onchange="Settings.updateConnectionField('intervalMin', this.value ? Number(this.value) : '')" ${ro}>
            ${scheduleOptions}
          </select>
          <small class="kfh-conn-help">How often this connection collects alerts once enabled (5 min–24 h). <strong>System default</strong> applies the platform default of 15 minutes.</small>
        </div>
        ${renderConnectionCredentialFields(draft, ro, isEditing)}
        ${renderConnectionAdvancedFields(draft, ro)}
      </div>
    `;
  }

  function renderConnectionCredentialFields(draft, ro, isEditing) {
    const placeholder = isEditing ? '•••••• (leave blank to keep existing)' : '';
    const pw = 'password';
    if (draft.pluginType === 'BMC') {
      return `
        <div class="kfh-conn-field">
          <label class="kfh-conn-label">Access key${isEditing ? '' : '<span class="kfh-conn-req">*</span>'}</label>
          <input type="${pw}" class="kfh-conn-input" value="${esc(draft.accessKey)}" oninput="Settings.updateConnectionField('accessKey', this.value)" autocomplete="new-password" placeholder="${placeholder}" ${ro}>
        </div>
        <div class="kfh-conn-field">
          <label class="kfh-conn-label">Access secret key${isEditing ? '' : '<span class="kfh-conn-req">*</span>'}</label>
          <input type="${pw}" class="kfh-conn-input" value="${esc(draft.accessSecretKey)}" oninput="Settings.updateConnectionField('accessSecretKey', this.value)" autocomplete="new-password" placeholder="${placeholder}" ${ro}>
        </div>`;
    }
    if (draft.pluginType === 'APPDYNAMICS' || draft.pluginType === 'SCOM') {
      return `
        <div class="kfh-conn-field">
          <label class="kfh-conn-label">Username${isEditing ? '' : '<span class="kfh-conn-req">*</span>'}</label>
          <input type="text" class="kfh-conn-input" value="${esc(draft.username)}" oninput="Settings.updateConnectionField('username', this.value)" ${ro}>
        </div>
        <div class="kfh-conn-field">
          <label class="kfh-conn-label">Password${isEditing ? '' : '<span class="kfh-conn-req">*</span>'}</label>
          <input type="${pw}" class="kfh-conn-input" value="${esc(draft.password)}" oninput="Settings.updateConnectionField('password', this.value)" autocomplete="new-password" placeholder="${placeholder}" ${ro}>
        </div>`;
    }
    if (draft.pluginType === 'VROPS') {
      return `
        <div class="kfh-conn-field kfh-conn-field-wide">
          <label class="kfh-conn-label">API token${isEditing ? '' : '<span class="kfh-conn-req">*</span>'}</label>
          <input type="${pw}" class="kfh-conn-input" value="${esc(draft.token)}" oninput="Settings.updateConnectionField('token', this.value)" autocomplete="new-password" placeholder="${placeholder}" ${ro}>
        </div>`;
    }
    if (draft.pluginType === 'EMCO') {
      return `
        <div class="kfh-conn-field-row">
          <div class="kfh-conn-field">
            <label class="kfh-conn-label">KFH SQL user${isEditing ? '' : '<span class="kfh-conn-req">*</span>'}</label>
            <input type="text" class="kfh-conn-input" value="${esc(draft.kfhUsername)}" oninput="Settings.updateConnectionField('kfhUsername', this.value)" ${ro}>
          </div>
          <div class="kfh-conn-field">
            <label class="kfh-conn-label">KFH SQL password${isEditing ? '' : '<span class="kfh-conn-req">*</span>'}</label>
            <input type="${pw}" class="kfh-conn-input" value="${esc(draft.kfhPassword)}" oninput="Settings.updateConnectionField('kfhPassword', this.value)" autocomplete="new-password" placeholder="${placeholder}" ${ro}>
          </div>
        </div>
        <div class="kfh-conn-field-row">
          <div class="kfh-conn-field">
            <label class="kfh-conn-label">CCTV SQL user</label>
            <input type="text" class="kfh-conn-input" value="${esc(draft.cctvUsername)}" oninput="Settings.updateConnectionField('cctvUsername', this.value)" ${ro}>
          </div>
          <div class="kfh-conn-field">
            <label class="kfh-conn-label">CCTV SQL password</label>
            <input type="${pw}" class="kfh-conn-input" value="${esc(draft.cctvPassword)}" oninput="Settings.updateConnectionField('cctvPassword', this.value)" autocomplete="new-password" placeholder="${placeholder}" ${ro}>
          </div>
        </div>`;
    }
    return '';
  }

  function renderConnectionAdvancedFields(draft, ro) {
    if (draft.pluginType === 'SCOM') {
      return `
        <details class="kfh-conn-advanced"><summary>Advanced (SCOM)</summary>
          <div class="kfh-conn-field-row">
            <div class="kfh-conn-field">
              <label class="kfh-conn-label">Management server</label>
              <input type="text" class="kfh-conn-input" value="${esc(draft.managementServer)}" oninput="Settings.updateConnectionField('managementServer', this.value)" ${ro}>
            </div>
            <div class="kfh-conn-field">
              <label class="kfh-conn-label">Domain</label>
              <input type="text" class="kfh-conn-input" value="${esc(draft.domain)}" oninput="Settings.updateConnectionField('domain', this.value)" ${ro}>
            </div>
          </div>
          <div class="kfh-conn-field-row">
            <div class="kfh-conn-field">
              <label class="kfh-conn-label">WinRM port</label>
              <input type="number" class="kfh-conn-input" value="${esc(draft.winrmPort)}" oninput="Settings.updateConnectionField('winrmPort', Number(this.value)||5986)" ${ro}>
            </div>
            <div class="kfh-conn-field kfh-conn-field-inline">
              <label class="kfh-conn-check"><input type="checkbox" ${draft.useHttps !== false ? 'checked' : ''} onchange="Settings.updateConnectionField('useHttps', this.checked)" ${ro}> Use HTTPS</label>
            </div>
          </div>
        </details>`;
    }
    if (draft.pluginType === 'EMCO') {
      return `
        <details class="kfh-conn-advanced"><summary>Advanced (EMCO SQL Server)</summary>
          <div class="kfh-conn-field-row">
            <div class="kfh-conn-field">
              <label class="kfh-conn-label">SQL Server</label>
              <input type="text" class="kfh-conn-input" value="${esc(draft.sqlServer)}" oninput="Settings.updateConnectionField('sqlServer', this.value)" placeholder="host or instance" ${ro}>
            </div>
            <div class="kfh-conn-field">
              <label class="kfh-conn-label">SQL port</label>
              <input type="number" class="kfh-conn-input" value="${esc(draft.sqlPort)}" oninput="Settings.updateConnectionField('sqlPort', Number(this.value)||1433)" ${ro}>
            </div>
          </div>
          <div class="kfh-conn-field-row">
            <div class="kfh-conn-field">
              <label class="kfh-conn-label">KFH database</label>
              <input type="text" class="kfh-conn-input" value="${esc(draft.kfhDatabase)}" oninput="Settings.updateConnectionField('kfhDatabase', this.value)" ${ro}>
            </div>
            <div class="kfh-conn-field">
              <label class="kfh-conn-label">CCTV database</label>
              <input type="text" class="kfh-conn-input" value="${esc(draft.cctvDatabase)}" oninput="Settings.updateConnectionField('cctvDatabase', this.value)" ${ro}>
            </div>
          </div>
        </details>`;
    }
    return '';
  }

  function renderConnectionShareFields(draft, readOnly) {
    const ro = readOnly ? 'disabled' : '';
    const shared = Array.isArray(draft.sharedWithTeams) ? draft.sharedWithTeams : [];
    const rows = CONNECTION_OWNER_TEAMS.map(function (team) {
      const checked = shared.includes(team) ? 'checked' : '';
      return `
        <label class="kfh-conn-share-row">
          <input type="checkbox" ${checked} ${ro} onchange="Settings.toggleConnectionShareTeam('${esc(team)}')">
          <span>${esc(team)}</span>
        </label>`;
    }).join('');
    return `
      <div class="kfh-conn-form">
        <p class="kfh-conn-help">Share this connection with other KFH teams. Sharing grants read access; write access remains restricted to the owner team.</p>
        <div class="kfh-conn-share-list">${rows}</div>
      </div>
    `;
  }

  function keepConnectionPopup(event) {
    if (event && event.target && event.target.id === 'settings-modal-overlay') {
      closeConnectionPopup();
    }
  }

  // Main render
  function render() {
    const container = document.getElementById('settings-content') || document.getElementById('page-root') || document.getElementById('content-area');
    if (!container) return;

    container.innerHTML = `
      <div class="settings-page-shell kfh-settings-shell">
        ${renderHeader()}
        <div class="settings-content kfh-settings-content">
          ${renderSettingsMenu()}
          <div class="settings-section kfh-settings-section">
            ${renderAzureOpenAI()}
            ${renderDatabases()}
            ${renderSharePoint()}
            ${renderTeams()}
            ${renderInfrastructure()}
            ${renderConnections()}
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
    if (tab !== 'connections') {
      state.connectionDetailType = null;
      state.connectionDetailSearch = '';
      state.connectionDetailOwner = '';
    }
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

  async function init(preferredSection) {
    restoreActiveTab();
    if (preferredSection && SETTINGS_TAB_IDS.includes(preferredSection)) {
      state.activeTab = preferredSection;
      persistActiveTab(preferredSection);
    }
    await loadSettings();
    // Fire and forget: catalog + list load in background; UI re-renders
    // when data arrives. Failures fall back to the static catalog.
    loadConnectorTypes();
    loadConnectors();
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
    // enterprise-grade Connections popup public API
    openConnectionPopup,
    openConnectionEditor: connectionEditPopup,
    openConnectionDetail,
    closeConnectionDetail,
    setConnectionDetailSearch,
    setConnectionDetailOwner,
    toggleConnectionEnabled,
    closeConnectionPopup,
    switchConnectionTab,
    setConnectionPopupMode,
    updateConnectionField,
    toggleConnectionShareTeam,
    saveConnection,
    togglePopupConnector,
    testConnection,
    deleteConnection,
    keepConnectionPopup,
    reloadConnectors: loadConnectors,
    toast
  };
})();

window.Settings = Settings;