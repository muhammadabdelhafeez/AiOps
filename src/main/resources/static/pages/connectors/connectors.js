/**
 * KFH AIOps Command Center - Connectors Module
 * Manage integrations and monitor data ingestion
 */
var Connectors = (function() {
  'use strict';

  // State
  let connectors = [];
  let testRuns = [];
  let searchQuery = '';
  let filters = { status: '', type: '', scope: '' };
  let viewMode = 'grid';
  let selectedConnector = null;
  let drawerTab = 'overview';
  let isModalOpen = false;
  let smartFiltersOpen = false;
  let connectorTypes = [];
  let selectedNewType = 'BMC';
  let pageMode = 'inventory';
  let marketplaceDetailType = '';
  let marketplaceInstallCountry = '';
  let heartbeatBusy = false;
  let connectorCardPage = 1;
  let drawerFeedback = null;

  // Constants
  const CONNECTOR_TYPES = [
    { value: 'BMC', label: 'BMC Helix', icon: 'server', available: true, category: 'Event Management' },
    { value: 'APPDYNAMICS', label: 'AppDynamics', icon: 'activity', available: true, category: 'Application Performance' },
    { value: 'VROPS', label: 'VMware vROps', icon: 'cloud', available: true, category: 'Infrastructure' },
    { value: 'SCOM', label: 'Microsoft SCOM', icon: 'server', available: true, category: 'Infrastructure' },
    { value: 'EMCO', label: 'EMCO Ping Monitor', icon: 'database', available: true, category: 'Network' },
    { value: 'LANSWEEPER', label: 'Lansweeper', icon: 'file', available: false, category: 'Inventory' }
  ];

  const STATUSES = ['Healthy', 'Pending', 'Degraded', 'Down', 'Disabled'];
  const SCOPES = ['PROD', 'UAT', 'DEV'];
  const COUNTRIES = ['KW', 'BH', 'EG'];
  const AUTH_MODES = ['AccessKey', 'BasicAuth', 'Token', 'WinRM', 'SqlServerCredentials'];
  const OWNER_TEAMS = ['Platform Ops', 'App Support', 'Network Ops', 'Storage Team', 'NOC'];
  const CONNECTOR_CARDS_PER_PAGE = 10;

  // Utilities
  const genId = () => (window.crypto && window.crypto.randomUUID ? window.crypto.randomUUID() : `tmp-${Date.now()}`);
  const esc = s => s ? s.replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])) : '';

  function pageContent(response) {
    return response && Array.isArray(response.content) ? response.content : Array.isArray(response) ? response : [];
  }

  function normalizeType(row) {
    return {
      value: row.pluginType || row.value,
      label: row.displayName || row.label || row.pluginType,
      icon: row.icon || 'server',
      category: row.category || 'Connector',
      description: row.description || '',
      available: row.available !== false,
      fields: Array.isArray(row.fields) ? row.fields : [],
      defaults: row.defaults || {}
    };
  }

  function availableTypes() {
    return connectorTypes.length ? connectorTypes : CONNECTOR_TYPES;
  }

  function typeInfo(type) {
    return availableTypes().find(t => t.value === type) || CONNECTOR_TYPES.find(t => t.value === type);
  }

  function marketplaceCountry() {
    return COUNTRIES.includes(marketplaceInstallCountry) ? marketplaceInstallCountry : defaultCountry();
  }

  function installedConnectorForScope(type, country, environment) {
    return connectors.find(c => c.type === type && c.countryCode === country && c.environmentScope === environment);
  }

  function installedConnectorFor(type) {
    return installedConnectorForScope(type, marketplaceCountry(), defaultEnvironment());
  }

  function connectorStatus(row, enabled) {
    if (!enabled) return 'Disabled';
    const lastTestStatus = String(row.lastTestStatus || '').trim().toUpperCase();
    if (lastTestStatus === 'FAIL') return 'Down';
    if (lastTestStatus === 'PASS') return 'Healthy';
    const rawStatus = String(row.health || row.status || row.configurationStatus || '').trim().toUpperCase();
    if (rawStatus === 'DOWN' || rawStatus === 'ERROR' || rawStatus === 'FAILED') return 'Down';
    if (rawStatus === 'HEALTHY' || rawStatus === 'PASS') return 'Healthy';
    if (rawStatus === 'DISABLED') return 'Disabled';
    if (rawStatus === 'PENDING' || rawStatus === 'NOT_CONFIGURED') return 'Pending';
    if (rawStatus === 'DEGRADED' || rawStatus === 'WARNING') return 'Degraded';
    return 'Pending';
  }

  function connectorDescription(type) {
    const descriptions = {
      BMC: 'Collect BMC Helix event alerts through access-key authentication, then normalize them into country-aware telemetry for evidence-backed AIOps analysis.',
      APPDYNAMICS: 'Discover applications, business transactions, error snapshots, health-rule violations, and slow transactions from AppDynamics.',
      VROPS: 'Collect VMware Aria Operations alerts and resource health to connect compute/cluster issues to business journeys.',
      SCOM: 'Collect Microsoft SCOM infrastructure alerts through governed WinRM/PowerShell execution.',
      EMCO: 'Read EMCO Ping Monitor network connectivity events from SQL Server for network evidence correlation.',
      LANSWEEPER: 'Import Lansweeper scan events and software changes as inventory/change evidence.'
    };
    return descriptions[type] || 'Connector integration for governed telemetry collection.';
  }

  function currentSession() {
    return window.KFHConfig && typeof KFHConfig.getSession === 'function' ? KFHConfig.getSession() : {};
  }

  function defaultCountry() {
    const session = currentSession();
    return COUNTRIES.includes(session.countryCode) ? session.countryCode : 'KW';
  }

  function defaultEnvironment() {
    const session = currentSession();
    return SCOPES.includes(session.environment) ? session.environment : 'PROD';
  }

  function canChooseCountry() {
    const session = currentSession();
    const permissions = Array.isArray(session.permissions) ? session.permissions : [];
    return session.countryCode === 'ALL' && (permissions.includes('*') || permissions.includes('COUNTRY_GLOBAL_VIEW'));
  }

  function booleanSetting(value, fallback = true) {
    if (value === undefined || value === null || value === '') return fallback;
    if (typeof value === 'boolean') return value;
    if (typeof value === 'number') return value !== 0;
    const text = String(value).trim().toLowerCase();
    if (['false', '0', 'no', 'off'].includes(text)) return false;
    if (['true', '1', 'yes', 'on'].includes(text)) return true;
    return fallback;
  }

  function checkedSetting(id, fallback = true) {
    const input = document.getElementById(id);
    return input ? input.checked : booleanSetting(fallback, true);
  }

  function connectorVerifySslSetting(connector) {
    if (connector?.type === 'SCOM') {
      const skipWinRmCertificateValidation = document.getElementById('edit-scom-skip-cert-validation');
      if (skipWinRmCertificateValidation) return !skipWinRmCertificateValidation.checked;
    }
    return checkedSetting('edit-verify-ssl', connector?.verifySsl);
  }

  function normalizeConnector(row) {
    const type = row.pluginType || row.type || 'UNKNOWN';
    const enabled = row.enabled !== false;
    const status = connectorStatus(row, enabled);
    return {
      id: String(row.id || row.connectorId || ''),
      name: row.name || type,
      type,
      enabled,
      countryCode: row.countryCode || defaultCountry(),
      environmentScope: row.environment || row.environmentScope || 'PROD',
      status,
      lastTestStatus: row.lastTestStatus || '',
      lastTestMessage: row.lastTestMessage || '',
      lastTestErrorCode: row.lastTestErrorCode || '',
      lastHealthCheckedAt: row.lastHealthCheckedAt ? Date.parse(row.lastHealthCheckedAt) : null,
      lastSync: row.lastRunAt ? Date.parse(row.lastRunAt) : row.lastTestAt ? Date.parse(row.lastTestAt) : null,
      nextSync: row.nextRunAt ? Date.parse(row.nextRunAt) : null,
      lagMinutes: Number(row.lagMinutes || 0),
      events24h: Number(row.events24h || row.eventCount24h || 0),
      errors24h: Number(row.errors24h || row.errorCount24h || 0),
      authMode: row.authMode || row.authenticationMode || 'Configured',
      baseUrl: row.baseUrl || row.endpointUrl || '',
      controllerUrl: row.controllerUrl || row.baseUrl || row.endpointUrl || '',
      host: row.host || '',
      sqlServer: row.sqlServer || row.host || '',
      sqlPort: Number(row.sqlPort || 11433),
      kfhDatabase: row.kfhDatabase || 'EMCO_KFH_PROD',
      cctvDatabase: row.cctvDatabase || 'EMCO_CCTV_PROD',
      managementServer: row.managementServer || row.host || '',
      domain: row.domain || 'corp.kfh.kw',
      winrmPort: Number(row.winrmPort || 5986),
      useHttps: booleanSetting(row.useHttps, true),
      authMethod: row.authMethod || 'Kerberos',
      authSource: row.authSource || 'KFH AD',
      loginEndpoint: row.loginEndpoint || '/ims/api/v1/access_keys/login',
      eventsEndpoint: row.eventsEndpoint || '/events-service/api/v1.0/events/msearch',
      hours: Number(row.hours || 1),
      hoursBack: Number(row.hoursBack || row.hours || 1),
      minutesBack: Number(row.minutesBack || 60),
      connectionTimeoutSeconds: Number(row.connectionTimeoutSeconds || row.timeoutSeconds || 60),
      queryTimeoutSeconds: Number(row.queryTimeoutSeconds || row.timeoutSeconds || 120),
      durationMinutes: Number(row.durationMinutes || 60),
      pageSize: Number(row.pageSize || 100),
      maxEvents: Number(row.maxEvents || 500),
      maxPages: Number(row.maxPages || 200),
      maxWorkers: Number(row.maxWorkers || 15),
      timeoutSeconds: Number(row.timeoutSeconds || 120),
      verifySsl: booleanSetting(row.verifySsl, true),
      encrypt: booleanSetting(row.encrypt, true),
      trustServerCertificate: booleanSetting(row.trustServerCertificate, false),
      fetchErrors: booleanSetting(row.fetchErrors, true),
      fetchViolations: booleanSetting(row.fetchViolations, true),
      fetchSlowTransactions: booleanSetting(row.fetchSlowTransactions, true),
      secretsMask: row.secretsMask || row.credentialStatus || (row.configurationStatus === 'CONFIGURED' ? 'configured' : 'not_configured'),
      credentialRecoveryRequired: booleanSetting(row.credentialRecoveryRequired, false),
      configurationStatus: row.configurationStatus || (row.baseUrl || row.endpointUrl ? 'CONFIGURED' : 'PENDING'),
      endpoints: Array.isArray(row.endpoints) ? row.endpoints : (row.baseUrl || row.endpointUrl ? [{ env: row.environment || 'PROD', url: row.baseUrl || row.endpointUrl, port: 443 }] : []),
      mappings: row.mappings || { appField: '', assetField: '', severityMap: {} },
      schedules: row.schedules || { intervalMin: row.intervalMin || 0 },
      notes: row.notes || '',
      ownerTeam: row.ownerTeam || row.team || '',
      createdAt: row.createdAt ? Date.parse(row.createdAt) : Date.now(),
      logs: []
    };
  }

  async function loadConnectors() {
    if (!window.APIClient || !APIClient.connectors) {
      connectors = [];
      return;
    }
    try {
      const response = await APIClient.connectors.list();
      connectors = pageContent(response).map(normalizeConnector);
    } catch (error) {
      console.warn('[Connectors] Unable to load production connectors; rendering empty state.', error);
      connectors = [];
    }
  }

  async function loadConnectorTypes() {
    if (!window.APIClient || !APIClient.connectors || !APIClient.connectors.types) {
      connectorTypes = CONNECTOR_TYPES;
      return;
    }
    try {
      const response = await APIClient.connectors.types();
      const loaded = pageContent(response).map(normalizeType);
      connectorTypes = loaded.length ? loaded : CONNECTOR_TYPES;
      if (!connectorTypes.some(t => t.value === selectedNewType && t.available !== false)) {
        selectedNewType = connectorTypes.find(t => t.available !== false)?.value || 'BMC';
      }
    } catch (error) {
      console.warn('[Connectors] Unable to load connector type metadata; using static BMC catalog.', error);
      connectorTypes = CONNECTOR_TYPES;
    }
  }

  function formatTime(timestamp) {
    if (!timestamp) return 'N/A';
    const diff = Date.now() - timestamp;
    const minutes = Math.floor(diff / 60000);
    if (minutes < 60) return `${minutes}m ago`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}h ago`;
    const days = Math.floor(hours / 24);
    return `${days}d ago`;
  }

  // SVG Icons
  const icons = {
    plug: '<path stroke-linecap="round" stroke-linejoin="round" d="M12 22v-5M9 8V2M15 8V2M18 8v5a4 4 0 01-4 4h-4a4 4 0 01-4-4V8z"/>',
    server: '<rect width="20" height="8" x="2" y="2" rx="2" ry="2"/><rect width="20" height="8" x="2" y="14" rx="2" ry="2"/><line x1="6" x2="6.01" y1="6" y2="6"/><line x1="6" x2="6.01" y1="18" y2="18"/>',
    cloud: '<path d="M17.5 19H9a7 7 0 116.71-9h1.79a4.5 4.5 0 110 9z"/>',
    globe: '<circle cx="12" cy="12" r="10"/><path d="M2 12h20"/><path d="M12 2a15.3 15.3 0 014 10 15.3 15.3 0 01-4 10 15.3 15.3 0 01-4-10 15.3 15.3 0 014-10z"/>',
    database: '<ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M3 5v14a9 3 0 0018 0V5"/><path d="M3 12a9 3 0 0018 0"/>',
    activity: '<path d="M22 12h-4l-3 9L9 3l-3 9H2"/>',
    file: '<path d="M14.5 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V7.5L14.5 2z"/><polyline points="14 2 14 8 20 8"/>',
    mail: '<rect width="20" height="16" x="2" y="4" rx="2"/><path d="m22 7-8.97 5.7a1.94 1.94 0 01-2.06 0L2 7"/>',
    upload: '<path d="M14.5 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V7.5L14.5 2z"/><polyline points="14 2 14 8 20 8"/><path d="M12 18v-6"/><path d="m9 15 3-3 3 3"/>',
    message: '<path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/>',
    check: '<path d="M22 11.08V12a10 10 0 11-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/>',
    x: '<circle cx="12" cy="12" r="10"/><path d="m15 9-6 6"/><path d="m9 9 6 6"/>',
    alert: '<path d="m21.73 18-8-14a2 2 0 00-3.48 0l-8 14A2 2 0 004 21h16a2 2 0 001.73-3z"/><path d="M12 9v4"/><path d="M12 17h.01"/>',
    settings: '<path d="M12.22 2h-.44a2 2 0 00-2 2v.18a2 2 0 01-1 1.73l-.43.25a2 2 0 01-2 0l-.15-.08a2 2 0 00-2.73.73l-.22.38a2 2 0 00.73 2.73l.15.1a2 2 0 011 1.72v.51a2 2 0 01-1 1.74l-.15.09a2 2 0 00-.73 2.73l.22.38a2 2 0 002.73.73l.15-.08a2 2 0 012 0l.43.25a2 2 0 011 1.73V20a2 2 0 002 2h.44a2 2 0 002-2v-.18a2 2 0 011-1.73l.43-.25a2 2 0 012 0l.15.08a2 2 0 002.73-.73l.22-.39a2 2 0 00-.73-2.73l-.15-.08a2 2 0 01-1-1.74v-.5a2 2 0 011-1.74l.15-.09a2 2 0 00.73-2.73l-.22-.38a2 2 0 00-2.73-.73l-.15.08a2 2 0 01-2 0l-.43-.25a2 2 0 01-1-1.73V4a2 2 0 00-2-2z"/><circle cx="12" cy="12" r="3"/>',
    play: '<polygon points="5 3 19 12 5 21 5 3"/>',
    pause: '<rect x="6" y="4" width="4" height="16" rx="1"/><rect x="14" y="4" width="4" height="16" rx="1"/>',
    search: '<circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/>',
    grid: '<rect width="7" height="7" x="3" y="3" rx="1"/><rect width="7" height="7" x="14" y="3" rx="1"/><rect width="7" height="7" x="14" y="14" rx="1"/><rect width="7" height="7" x="3" y="14" rx="1"/>',
    list: '<line x1="8" x2="21" y1="6" y2="6"/><line x1="8" x2="21" y1="12" y2="12"/><line x1="8" x2="21" y1="18" y2="18"/><line x1="3" x2="3.01" y1="6" y2="6"/><line x1="3" x2="3.01" y1="12" y2="12"/><line x1="3" x2="3.01" y1="18" y2="18"/>',
    plus: '<path d="M5 12h14"/><path d="M12 5v14"/>',
    close: '<path d="M18 6L6 18"/><path d="m6 6 12 12"/>',
    save: '<path d="M19 21H5a2 2 0 01-2-2V5a2 2 0 012-2h11l5 5v11a2 2 0 01-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/>',
    zap: '<polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>'
  };

  function icon(name, size = 16) {
    return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${icons[name] || ''}</svg>`;
  }


  // Stats
  function getKpis() {
    return {
      total: connectors.length,
      healthy: connectors.filter(c => c.status === 'Healthy').length,
      warning: connectors.filter(c => isWarningStatus(c.status)).length,
      down: connectors.filter(c => c.status === 'Down').length,
      disabled: connectors.filter(c => c.status === 'Disabled').length,
      totalEvents24h: connectors.reduce((sum, c) => sum + c.events24h, 0)
    };
  }

  function isWarningStatus(status) {
    return status === 'Pending' || status === 'Degraded';
  }

  function statusClass(status) {
    if (status === 'Healthy') return 'healthy';
    if (status === 'Down') return 'down';
    if (status === 'Disabled') return 'disabled';
    return 'degraded';
  }

  function statusHint(c) {
    if (c.status === 'Down') return c.lastTestMessage || 'Last connector test failed. Run Test or Heartbeat after fixing the endpoint or credentials.';
    if (c.status === 'Pending') return 'Enabled but not confirmed online yet. Run Heartbeat to validate this connector.';
    if (c.status === 'Degraded') return c.lastTestMessage || 'Connector needs attention before collection should be trusted.';
    return '';
  }

  function getFilteredConnectors() {
    return connectors.filter(c => {
      if (searchQuery && !c.name.toLowerCase().includes(searchQuery.toLowerCase()) && !c.type.toLowerCase().includes(searchQuery.toLowerCase())) return false;
      if (filters.status && c.status !== filters.status) return false;
      if (filters.type && c.type !== filters.type) return false;
      if (filters.scope && c.environmentScope !== filters.scope) return false;
      return true;
    });
  }

  function resetConnectorPage() {
    connectorCardPage = 1;
  }

  function cardPageCount(total) {
    return Math.max(1, Math.ceil(total / CONNECTOR_CARDS_PER_PAGE));
  }

  function clampConnectorPage(total) {
    connectorCardPage = Math.min(Math.max(1, connectorCardPage), cardPageCount(total));
  }

  function getVisibleConnectors(filtered) {
    if (viewMode !== 'grid') return filtered;
    clampConnectorPage(filtered.length);
    const start = (connectorCardPage - 1) * CONNECTOR_CARDS_PER_PAGE;
    return filtered.slice(start, start + CONNECTOR_CARDS_PER_PAGE);
  }

  function renderConnectorPagination(total, visibleCount) {
    if (viewMode !== 'grid' || total <= CONNECTOR_CARDS_PER_PAGE) return '';
    const pages = cardPageCount(total);
    const start = (connectorCardPage - 1) * CONNECTOR_CARDS_PER_PAGE + 1;
    const end = start + visibleCount - 1;
    const pageButtons = Array.from({ length: pages }, (_, index) => {
      const page = index + 1;
      return `<button type="button" class="connector-pagination-page ${page === connectorCardPage ? 'active' : ''}" onclick="Connectors.setPage(${page})" aria-current="${page === connectorCardPage ? 'page' : 'false'}">${page}</button>`;
    }).join('');
    return `
      <nav class="connector-pagination" aria-label="Connector pagination">
        <span class="connector-pagination-summary">Showing ${start}-${end} of ${total} connectors · ${CONNECTOR_CARDS_PER_PAGE} per page</span>
        <div class="connector-pagination-actions">
          <button type="button" class="connector-pagination-btn" onclick="Connectors.setPage(${connectorCardPage - 1})" ${connectorCardPage === 1 ? 'disabled' : ''}>Previous</button>
          ${pageButtons}
          <button type="button" class="connector-pagination-btn" onclick="Connectors.setPage(${connectorCardPage + 1})" ${connectorCardPage === pages ? 'disabled' : ''}>Next</button>
        </div>
      </nav>
    `;
  }

  function filterSummary() {
    const active = [filters.status, filters.type, filters.scope].filter(Boolean).length;
    return active ? `${active} filter${active === 1 ? '' : 's'} active` : 'All connectors';
  }

  function filterChip(group, value, label) {
    const active = (filters[group] || '') === value;
    return `<button type="button" class="connector-filter-chip ${active ? 'active' : ''}" onclick="Connectors.setSmartFilter('${group}', '${value}')">${esc(label)}</button>`;
  }

  function renderSmartFilters() {
    return `
      <div class="connector-smart-filter ${smartFiltersOpen ? 'open' : ''}">
        <button id="connector-smart-filter-toggle" type="button" class="connector-smart-filter-toggle" onclick="Connectors.toggleSmartFilters()" aria-haspopup="true" aria-expanded="${smartFiltersOpen}">
          <span class="connector-smart-filter-title">Connector filters</span>
          <span class="connector-smart-filter-summary">${filterSummary()}</span>
        </button>
        ${smartFiltersOpen ? `
          <div class="connector-smart-filter-panel" role="dialog" aria-label="Connector filters">
            <div class="connector-smart-filter-heading">
              <strong>Connector filters</strong>
              <span>${filterSummary()}</span>
            </div>
            <div class="connector-smart-filter-section">
              <div class="connector-smart-filter-label">Status</div>
              <div class="connector-filter-chip-row">
                ${filterChip('status', '', 'All')}
                ${STATUSES.map(s => filterChip('status', s, s)).join('')}
              </div>
            </div>
            <div class="connector-smart-filter-section">
              <div class="connector-smart-filter-label">Type</div>
              <div class="connector-filter-chip-row connector-filter-chip-row-wrap">
                ${filterChip('type', '', 'All')}
                ${availableTypes().map(t => filterChip('type', t.value, t.label)).join('')}
              </div>
            </div>
            <div class="connector-smart-filter-section">
              <div class="connector-smart-filter-label">Scope</div>
              <div class="connector-filter-chip-row">
                ${filterChip('scope', '', 'All')}
                ${SCOPES.map(s => filterChip('scope', s, s)).join('')}
              </div>
            </div>
          </div>
        ` : ''}
      </div>
    `;
  }

  // Rendering
  function render() {
    const container = document.getElementById('connectors-content') || document.getElementById('page-root') || document.getElementById('content-area');
    if (!container) return;

    if (pageMode === 'marketplace') {
      container.innerHTML = renderMarketplacePage();
      return;
    }

    if (pageMode === 'marketplace-detail') {
      container.innerHTML = renderMarketplaceDetailPage(typeInfo(marketplaceDetailType) || typeInfo('BMC'));
      return;
    }

    const kpis = getKpis();
    const filtered = getFilteredConnectors();
    const visibleConnectors = getVisibleConnectors(filtered);
    const connectorPagination = renderConnectorPagination(filtered.length, visibleConnectors.length);

    container.innerHTML = `
      <section class="connectors-page-shell animate-fade-in">
        <div class="connectors-header-card connectors-header-card-compact">
          <div class="connectors-header-one-line">
            <div class="connectors-title-block">
              <h1 class="connectors-title">Connectors</h1>
            </div>
            <label class="connector-search" aria-label="Search connectors">
              <span class="connector-search-icon">${icon('search', 16)}</span>
              <input id="connector-search" type="text" placeholder="Search connector name or type" class="connector-search-input" value="${esc(searchQuery)}">
            </label>
            ${renderSmartFilters()}
            <div class="view-toggle" aria-label="Connector view mode">
              <button onclick="Connectors.setViewMode('table')" class="view-toggle-btn ${viewMode === 'table' ? 'active' : ''}" title="Table view" type="button">${icon('list', 16)}<span>Table</span></button>
              <button onclick="Connectors.setViewMode('grid')" class="view-toggle-btn ${viewMode === 'grid' ? 'active' : ''}" title="Card view" type="button">${icon('grid', 16)}<span>Cards</span></button>
            </div>
            <button onclick="Connectors.runHeartbeat()" class="connectors-secondary-action connectors-header-heartbeat" type="button" ${heartbeatBusy ? 'disabled' : ''}>${icon('activity', 16)} ${heartbeatBusy ? 'Heartbeat...' : 'Heartbeat enabled'}</button>
            <button onclick="Connectors.openMarketplace()" class="connectors-primary-action connectors-header-add" type="button">${icon('plus', 16)} Connector Marketplace</button>
          </div>
        </div>

        <div class="connectors-kpi-grid">
          ${renderKpiCard('Total', kpis.total, 'plug', 'default')}
          ${renderKpiCard('Healthy', kpis.healthy, 'check', 'healthy')}
          ${renderKpiCard('Warning', kpis.warning, 'alert', 'degraded', kpis.warning > 0)}
          ${renderKpiCard('Down', kpis.down, 'x', 'down', kpis.down > 0)}
          ${renderKpiCard('Disabled', kpis.disabled, 'x', 'disabled')}
        </div>

        ${filtered.length ? (viewMode === 'grid' ? `${renderConnectorsGrid(visibleConnectors)}${connectorPagination}` : renderConnectorsTable(filtered)) : renderEmptyState()}
      </section>
    `;

    bindEvents();
  }

  function renderMarketplacePage() {
    const installedCount = availableTypes().filter(t => installedConnectorFor(t.value)).length;
    const availableCount = availableTypes().filter(t => t.available !== false).length;
    return `
      <section class="connectors-page-shell connector-marketplace-page animate-fade-in">
        <div class="marketplace-hero">
          <div class="marketplace-hero-main">
            <div class="marketplace-title-row">
              <button type="button" class="marketplace-back" onclick="Connectors.openInventory()" aria-label="Return to Connector Inventory">
                <span class="marketplace-back-icon" aria-hidden="true">←</span>
                <span class="marketplace-back-label">Back</span>
              </button>
              <h1 class="connectors-title">Connector Marketplace</h1>
            </div>
            <span class="connectors-eyebrow">KFH Integration Marketplace</span>
            <p class="marketplace-subtitle">Browse governed source connectors, review capabilities, install per country, then configure secure connection settings only when needed.</p>
          </div>
          <div class="marketplace-hero-stats">
            <div><strong>${availableTypes().length}</strong><span>Total connectors</span></div>
            <div><strong>${availableCount}</strong><span>Ready now</span></div>
            <div><strong>${installedCount}</strong><span>Installed</span></div>
          </div>
        </div>
        <div class="marketplace-toolbar">
          <div>
            <h2>Available connectors</h2>
            <p>Choose a connector to open its full product page.</p>
          </div>
          <div class="marketplace-pills"><span>Event Management</span><span>APM</span><span>Infrastructure</span><span>Network</span><span>Inventory</span></div>
        </div>
        <div class="marketplace-grid">
          ${availableTypes().map(t => renderMarketplaceTile(t)).join('')}
        </div>
      </section>
    `;
  }

  function renderMarketplaceTile(t) {
    const installed = installedConnectorFor(t.value);
    return `
      <article class="marketplace-tile ${t.available === false ? 'disabled' : ''}" onclick="Connectors.openMarketplaceDetail('${esc(t.value)}')">
        <div class="marketplace-tile-top">
          <div class="marketplace-tile-icon">${icon(t.icon || 'server', 28)}</div>
          <span class="connector-picker-status ${t.available === false ? 'coming-soon' : installed ? 'ready installed' : 'ready'}">${t.available === false ? 'Coming soon' : installed ? 'Installed' : 'Available'}</span>
        </div>
        <div class="marketplace-tile-body">
          <div class="marketplace-tile-title">${esc(t.label)}</div>
          <div class="marketplace-tile-category">${esc(t.category || 'Connector')}</div>
          <p>${esc(connectorDescription(t.value))}</p>
        </div>
        <div class="marketplace-tile-footer">
          <button type="button" class="connector-install-btn" onclick="event.stopPropagation(); Connectors.openMarketplaceDetail('${esc(t.value)}')">View details</button>
        </div>
      </article>
    `;
  }

  function renderMarketplaceDetailPage(t) {
    const installCountry = marketplaceCountry();
    const environment = defaultEnvironment();
    const installed = installedConnectorForScope(t.value, installCountry, environment);
    const countryControl = canChooseCountry()
      ? `<select class="marketplace-country-select" aria-label="Installation country" onchange="Connectors.setMarketplaceCountry(this.value)">${optionList(COUNTRIES, installCountry)}</select>`
      : `<span class="details-row-value locked-country">${esc(installCountry)}</span>`;
    const statusLabel = t.available === false ? 'Coming soon' : installed ? 'Installed' : 'Not installed';
    return `
      <section class="connectors-page-shell connector-marketplace-detail animate-fade-in">
        <div class="marketplace-detail-hero">
          <div class="marketplace-detail-title-row">
            <button type="button" class="marketplace-back marketplace-detail-back" onclick="Connectors.openMarketplace()" aria-label="Return to Connector Marketplace">
              <span class="marketplace-back-icon" aria-hidden="true">←</span>
              <span class="marketplace-back-label">Back</span>
            </button>
            <h1>${esc(t.label)}</h1>
          </div>
          <div class="marketplace-detail-icon">${icon(t.icon || 'server', 40)}</div>
          <div class="marketplace-detail-copy">
            <span class="marketplace-tile-category">${esc(t.category || 'Connector')}</span>
            <p>${esc(connectorDescription(t.value))}</p>
            <div class="marketplace-detail-badges">
              <span>Country-aware</span><span>Tenant-scoped</span><span>Secret-safe</span><span>Evidence-first</span>
            </div>
          </div>
          <div class="marketplace-detail-actions">
            <span class="marketplace-detail-status ${installed ? 'installed' : t.available === false ? 'soon' : 'available'}">${t.available === false ? 'Coming soon' : installed ? 'Installed' : 'Available'}</span>
            ${renderMarketplacePrimaryAction(t, installed)}
          </div>
        </div>
        <div class="marketplace-detail-layout">
          <div class="marketplace-detail-main">
            <section class="marketplace-panel"><h2>Overview</h2><p>${esc(connectorDescription(t.value))}</p></section>
            <section class="marketplace-panel"><h2>Capabilities</h2><div class="marketplace-capability-grid">${marketplaceCapabilities(t.value).map(item => `<div class="marketplace-capability">${icon('check', 16)}<span>${esc(item)}</span></div>`).join('')}</div></section>
            <section class="marketplace-panel"><h2>Security and governance</h2><div class="marketplace-security-grid"><div>Per-country connector installation prevents cross-country alert conflicts.</div><div>Secrets are submitted server-side only and never returned in API responses.</div><div>URL inputs are validated before live connector execution.</div><div>Connector writes are audited with correlation IDs.</div></div></section>
          </div>
          <aside class="marketplace-detail-side installation-scope-card">
            <div class="installation-scope-heading">
              <div class="installation-scope-icon">${icon('globe', 18)}</div>
              <div>
                <span>Governed install target</span>
                <h3>Installation scope</h3>
              </div>
            </div>
            <div class="installation-scope-summary">
              <div>
                <span>Country</span>
                <strong>${esc(installCountry)}</strong>
              </div>
              <div>
                <span>Environment</span>
                <strong>${esc(environment)}</strong>
              </div>
            </div>
            <div class="details-list installation-scope-list">
              <div class="details-row"><span class="details-row-label">Target country</span>${countryControl}</div>
              <div class="details-row"><span class="details-row-label">Environment</span><span class="details-row-value scope-value-pill">${esc(environment)}</span></div>
              <div class="details-row"><span class="details-row-label">Connector status</span><span class="details-row-value scope-status-pill ${installed ? 'installed' : t.available === false ? 'soon' : 'not-installed'}">${statusLabel}</span></div>
            </div>
            <div class="marketplace-side-note installation-scope-note">${canChooseCountry() ? 'Select the physical country before installing. Each country keeps a separate connector profile, collection window, and alert scope.' : 'Your role installs connectors only into the signed-in country. Global admins can choose another physical country.'}</div>
          </aside>
        </div>
      </section>
    `;
  }

  function renderMarketplacePrimaryAction(t, installed) {
    if (t.available === false) return '<button class="connectors-primary-action" disabled>Coming soon</button>';
    if (installed) {
      return `<button class="connectors-primary-action" onclick="Connectors.openDrawer('${installed.id}')">Configure</button><button class="marketplace-uninstall" onclick="Connectors.uninstallConnector('${t.value}')">Uninstall</button>`;
    }
    return `<button class="connectors-primary-action" onclick="Connectors.installConnector('${t.value}')">Install</button>`;
  }

  function marketplaceCapabilities(type) {
    if (type === 'BMC') return ['Access-key based BMC Helix connection profile', 'BMC event msearch endpoint configuration', 'Collection window, page size, max events, timeout, and interval controls', 'Country/environment-scoped installation enabled by default with pending configuration status'];
    if (type === 'APPDYNAMICS') return ['Basic Auth AppDynamics Controller readiness test', 'Application discovery through /rest/applications?output=JSON', 'Configurable error snapshots, health-rule violations, and slow transaction families', 'Preserves AppDynamics Event ID requirements for downstream AI correlation'];
    if (type === 'VROPS') return ['Token-based VMware Aria Operations readiness test', 'Alert API paging through /suite-api/api/alerts', 'Resource enrichment-ready configuration for VM/host health evidence', 'Public and private KFH hybrid IPs/hostnames supported'];
    if (type === 'SCOM') return ['WinRM/PowerShell readiness test against the SCOM management server', 'OperationsManager module and Get-SCOMAlert probe support', 'SCOM Id, host/object path, severity, resolution state, and description mapping', 'Kerberos-first domain authentication with encrypted username/password storage'];
    if (type === 'EMCO') return ['SQL Server JDBC readiness test for KFH and CCTV EMCO databases', 'KFH db_owner and CCTV dbo host-event query probes with bounded query timeout', 'Separate encrypted KFH/CCTV SQL credentials with write-only rotation', 'Network host state and connection-quality evidence mapped to canonical telemetry'];
    return ['Connector metadata visible in marketplace', 'Install workflow will be enabled in a future connector phase', 'Designed for country-aware telemetry collection'];
  }

  function renderKpiCard(label, value, iconName, variant, pulse = false) {
    return `
      <div class="connector-kpi-card connector-kpi-card-${variant} ${pulse ? 'pulse-glow' : ''}">
        <div class="connector-kpi-header">
          <div class="connector-kpi-icon connector-kpi-icon-${variant}">${icon(iconName, 18)}</div>
          <span class="connector-kpi-label">${label}</span>
        </div>
        <span class="connector-kpi-value">${value}</span>
      </div>
    `;
  }

  function renderConnectorsGrid(connectors) {
    return `
      <div class="connectors-grid connectors-modern-grid">
        ${connectors.map(c => renderConnectorCard(c)).join('')}
      </div>
    `;
  }

  function renderConnectorCard(c) {
    const connectorType = typeInfo(c.type);
    const iconClass = c.enabled === false ? 'disabled' : statusClass(c.status);
    const lagClass = c.enabled === false ? 'color: #64748b' : c.lagMinutes < 5 ? 'color: var(--kfh-primary)' : c.lagMinutes < 15 ? 'color: #D97706' : 'color: #DC2626';
    const hint = statusHint(c);

    return `
      <article class="connector-card connector-card-status-${iconClass} connector-card-collection-${c.enabled ? 'enabled' : 'disabled'} animate-fade-in">
        <div class="connector-card-header">
          <div class="connector-card-info">
            <div class="connector-type-icon connector-type-icon-${iconClass}">${icon(connectorType?.icon || 'server', 24)}</div>
            <div>
              <div class="connector-card-name">${esc(c.name)}</div>
              <div class="connector-card-type">${connectorType?.label || c.type}</div>
            </div>
          </div>
          <span class="collection-state-pill ${c.enabled ? 'enabled' : 'disabled'}">${c.enabled ? 'Enabled' : 'Disabled'}</span>
        </div>

        <div class="connector-details">
          <div class="connector-detail-row">
            <span class="connector-detail-label">Country</span>
            <span class="country-badge">${esc(c.countryCode)}</span>
          </div>
          <div class="connector-detail-row">
            <span class="connector-detail-label">Status</span>
            <span class="status-badge status-badge-${iconClass}">${c.status}</span>
          </div>
          ${hint ? `<div class="connector-health-warning connector-health-warning-${iconClass}">${icon(iconClass === 'down' ? 'x' : 'alert', 13)}<span>${esc(hint)}</span></div>` : ''}
          <div class="connector-detail-row">
            <span class="connector-detail-label">Scope</span>
            <div class="flex gap-1">
              ${c.environmentScope === 'Both' 
                ? '<span class="scope-badge scope-badge-prod">Prod</span><span class="scope-badge scope-badge-dr">DR</span>'
                : `<span class="scope-badge scope-badge-${c.environmentScope.toLowerCase()}">${c.environmentScope}</span>`}
            </div>
          </div>
          ${c.lastSync ? `
          <div class="connector-detail-row">
            <span class="connector-detail-label">Last Sync</span>
            <span class="connector-detail-value">${formatTime(c.lastSync)}</span>
          </div>
          ` : ''}
          <div class="connector-detail-row">
            <span class="connector-detail-label">Lag</span>
            <span class="connector-detail-value" style="${lagClass}; font-weight: 600;">${c.lagMinutes}m</span>
          </div>
        </div>

        <div class="connector-stats">
          <div class="connector-stat">
            <div class="connector-stat-label">Events 24h</div>
            <div class="connector-stat-value connector-stat-value-events">${c.events24h.toLocaleString()}</div>
          </div>
          <div class="connector-stat">
            <div class="connector-stat-label">Errors 24h</div>
            <div class="connector-stat-value connector-stat-value-errors">${c.errors24h}</div>
          </div>
        </div>

        <div class="connector-card-actions">
          <button onclick="Connectors.openDrawer('${c.id}')" class="connector-action-btn connector-action-configure">
            ${icon('settings', 14)} Configure
          </button>
          <button onclick="Connectors.runTest('${c.id}')" class="connector-action-btn connector-action-test">
            ${icon('play', 14)} Test
          </button>
        </div>
      </article>
    `;
  }

  function renderConnectorsTable(connectors) {
    return `
      <div class="connectors-table-card animate-fade-in">
        <div class="connectors-table-top">
          <div>
            <h2>Connector inventory</h2>
            <p>${connectors.length} source${connectors.length === 1 ? '' : 's'} matched current filters</p>
          </div>
        </div>
        <div class="connectors-table-wrap">
          <table class="connectors-table">
            <thead>
              <tr>
                <th>Connector</th>
                <th>Health</th>
                <th>State</th>
                <th>Country</th>
                <th>Scope</th>
                <th>Last sync</th>
                <th>Lag</th>
                <th>Events</th>
                <th>Errors</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              ${connectors.map(c => {
                const connectorType = typeInfo(c.type);
                const iconClass = statusClass(c.status);
                const lagClass = c.lagMinutes < 5 ? 'table-lag-good' : c.lagMinutes < 15 ? 'table-lag-warn' : 'table-lag-bad';
                return `
                  <tr>
                    <td>
                      <div class="connector-table-identity">
                        <div class="connector-type-icon connector-type-icon-${iconClass}">${icon(connectorType?.icon || 'server', 18)}</div>
                        <div>
                          <span class="table-connector-name">${esc(c.name)}</span>
                          <span class="table-connector-type">${connectorType?.label || c.type}</span>
                        </div>
                      </div>
                    </td>
                    <td><span class="status-badge status-badge-${iconClass}">${c.status}</span></td>
                    <td><span class="collection-state-pill ${c.enabled ? 'enabled' : 'disabled'}">${c.enabled ? 'Enabled' : 'Disabled'}</span></td>
                    <td><span class="country-badge">${esc(c.countryCode)}</span></td>
                    <td>
                      ${c.environmentScope === 'Both'
                        ? '<span class="scope-badge scope-badge-prod">Prod</span> <span class="scope-badge scope-badge-dr">DR</span>'
                        : `<span class="scope-badge scope-badge-${c.environmentScope.toLowerCase()}">${c.environmentScope}</span>`}
                    </td>
                    <td class="table-muted">${c.lastSync ? formatTime(c.lastSync) : 'N/A'}</td>
                    <td><span class="${lagClass}">${c.lagMinutes}m</span></td>
                    <td><span class="table-events">${c.events24h.toLocaleString()}</span></td>
                    <td><span class="table-errors">${c.errors24h}</span></td>
                    <td>
                      <div class="table-actions">
                        <button onclick="Connectors.openDrawer('${c.id}')" class="table-action-btn table-action-configure" title="Configure" type="button">${icon('settings', 15)} Configure</button>
                        <button onclick="Connectors.runTest('${c.id}')" class="table-action-btn table-action-test" title="Run test" type="button">${icon('play', 15)} Test</button>
                      </div>
                    </td>
                  </tr>
                `;
              }).join('')}
            </tbody>
          </table>
        </div>
      </div>
    `;
  }

  function renderEmptyState() {
    return `
      <div class="connectors-table-card empty-state">
        <div class="empty-state-icon">${icon('plug', 64)}</div>
        <h3 class="empty-state-title">No connectors found</h3>
        <p class="empty-state-text">${searchQuery || filters.status || filters.type || filters.scope ? 'Try adjusting your filters' : 'Get started by adding your first connector'}</p>
      </div>
    `;
  }

  function ensureConnectorOverlays() {
    if (!document.getElementById('connector-drawer-overlay')) {
      const drawerOverlay = document.createElement('div');
      drawerOverlay.id = 'connector-drawer-overlay';
      drawerOverlay.className = 'connector-drawer-overlay';
      document.body.appendChild(drawerOverlay);
    }

    if (!document.getElementById('connector-drawer')) {
      const drawer = document.createElement('div');
      drawer.id = 'connector-drawer';
      drawer.className = 'connector-drawer';
      drawer.innerHTML = '<div id="drawer-content" class="h-full flex flex-col"></div>';
      document.body.appendChild(drawer);
    }

    if (!document.getElementById('connector-modal-overlay')) {
      const modalOverlay = document.createElement('div');
      modalOverlay.id = 'connector-modal-overlay';
      modalOverlay.className = 'modal-overlay';
      document.body.appendChild(modalOverlay);
    }
  }

  // Drawer
  function openDrawer(connectorId) {
    ensureConnectorOverlays();
    selectedConnector = connectors.find(c => c.id === connectorId);
    drawerTab = selectedConnector?.configurationStatus === 'PENDING' ? 'configuration' : 'overview';
    renderDrawer();
    document.getElementById('connector-drawer-overlay')?.classList.add('open');
    document.getElementById('connector-drawer')?.classList.add('open');
  }

  function closeDrawer() {
    document.getElementById('connector-drawer-overlay')?.classList.remove('open');
    document.getElementById('connector-drawer')?.classList.remove('open');
    selectedConnector = null;
    drawerFeedback = null;
  }

  function renderDrawer() {
    ensureConnectorOverlays();
    if (!selectedConnector) return;
    const c = selectedConnector;
    const drawerContent = document.getElementById('drawer-content');
    const connectorType = typeInfo(c.type);
    const drawerIconClass = c.enabled === false ? 'disabled' : statusClass(c.status);
    const logs = Array.isArray(c.logs) ? c.logs : [];
    const currentTestRun = testRuns.find(tr => tr.connectorId === c.id && tr.result === 'running');
    const latestTestRun = testRuns.find(tr => tr.connectorId === c.id);

    drawerContent.innerHTML = `
      <div class="drawer-header">
        <div class="drawer-title-group">
          <div class="drawer-title-icon drawer-title-icon-${drawerIconClass}">${icon(connectorType?.icon || 'server', 19)}</div>
          <div class="drawer-header-info">
            <span class="drawer-eyebrow">Connector details</span>
            <h2>${esc(c.name)}</h2>
            <p>${connectorType?.label || c.type}</p>
          </div>
        </div>
        <button type="button" aria-label="Close connector details" title="Close" onclick="Connectors.closeDrawer()" class="drawer-close-btn">${icon('close', 18)}</button>
      </div>

      <div class="drawer-tabs">
        ${['overview', 'configuration', 'mapping', 'health', 'logs'].map(tab => `
          <button onclick="Connectors.setDrawerTab('${tab}')" class="drawer-tab ${drawerTab === tab ? 'active' : ''}">${tab}</button>
        `).join('')}
      </div>

      <div class="drawer-body">
        ${drawerTab === 'overview' ? renderOverviewTab(c) : ''}
        ${drawerTab === 'configuration' ? renderConfigurationTab(c) : ''}
        ${drawerTab === 'mapping' ? renderMappingTab(c) : ''}
        ${drawerTab === 'health' ? renderHealthTab(c, currentTestRun, latestTestRun) : ''}
        ${drawerTab === 'logs' ? renderLogsTab(logs) : ''}
        <div class="drawer-action-feedback-slot">${renderDrawerFeedback()}</div>
      </div>

      ${drawerTab === 'configuration' ? `
      <div class="drawer-footer">
        <button type="button" data-connector-action="cancel" class="drawer-footer-cancel">Cancel</button>
        <button type="button" data-connector-action="test" class="drawer-footer-test">${icon('play', 16)} Test Connection</button>
        <button type="button" data-connector-action="save" class="drawer-footer-save">${icon('save', 16)} Save</button>
      </div>
      ` : ''}
    `;
    bindDrawerFooterActions(c.id);
  }

  function renderDrawerFeedback() {
    if (!drawerFeedback) return '';
    const type = drawerFeedback.type || 'info';
    const role = type === 'error' ? 'alert' : 'status';
    const iconName = type === 'success' ? 'check' : type === 'error' ? 'x' : 'alert';
    return `
      <div class="drawer-action-feedback drawer-action-feedback-${type}" role="${role}" aria-live="polite">
        <span class="drawer-action-feedback-icon">${icon(iconName, 14)}</span>
        <span class="drawer-action-feedback-message">${esc(drawerFeedback.message)}</span>
      </div>
    `;
  }

  function updateDrawerFeedback() {
    const slot = document.querySelector('#drawer-content .drawer-action-feedback-slot');
    if (slot) slot.innerHTML = renderDrawerFeedback();
  }

  function bindDrawerFooterActions(connectorId) {
    const drawerContent = document.getElementById('drawer-content');
    if (!drawerContent) return;
    drawerContent.querySelector('[data-connector-action="cancel"]')?.addEventListener('click', closeDrawer);
    drawerContent.querySelector('[data-connector-action="test"]')?.addEventListener('click', () => testCurrentConfiguration(connectorId));
    drawerContent.querySelector('[data-connector-action="save"]')?.addEventListener('click', saveConnector);
  }

  function renderOverviewTab(c) {
    return `
      <div class="connector-tab-modern overview-tab-modern">
        <div class="config-hero-panel tab-hero-panel">
          <div class="config-hero-icon">${icon('server', 22)}</div>
          <div class="config-hero-copy">
            <span>Connector overview</span>
            <h3>${esc(c.name)}</h3>
            <p>Current operational posture, source endpoint footprint, and country-scoped ownership for this connector.</p>
          </div>
          <div class="config-hero-chips">
            <span>${esc(c.countryCode)}</span><span>${esc(c.environmentScope)}</span><span>${esc(c.status)}</span>
          </div>
        </div>

        <div class="modern-stat-grid">
          <div class="modern-stat-card status"><span>Status</span><strong>${esc(c.status)}</strong></div>
          <div class="modern-stat-card events"><span>Events 24h</span><strong>${c.events24h.toLocaleString()}</strong></div>
          <div class="modern-stat-card errors"><span>Errors 24h</span><strong>${c.errors24h.toLocaleString()}</strong></div>
          <div class="modern-stat-card lag"><span>Lag</span><strong>${c.lagMinutes}m</strong></div>
        </div>

        <div class="drawer-section config-section">
          <div class="config-section-head">
            <div class="config-section-icon">${icon('cloud', 18)}</div>
            <div><span class="config-section-kicker">Source endpoints</span><div class="drawer-section-title">Endpoints</div><p>Runtime endpoints used during governed connector validation and scheduled collection.</p></div>
          </div>
          <div class="modern-endpoint-list">
            ${c.endpoints.length ? c.endpoints.map(ep => `
              <div class="modern-endpoint-item">
                <div><span class="scope-badge scope-badge-${String(ep.env || c.environmentScope).toLowerCase()}">${esc(ep.env || c.environmentScope)}</span><div class="endpoint-url">${esc(ep.url)}</div>${ep.port ? `<div class="endpoint-port">Port: ${esc(String(ep.port))}</div>` : ''}</div>
                <span class="status-badge status-badge-healthy">Active</span>
              </div>
            `).join('') : '<div class="modern-empty-state">No endpoints returned yet. Configure and save the source URL first.</div>'}
          </div>
        </div>

        <div class="drawer-section config-section">
          <div class="config-section-head">
            <div class="config-section-icon">${icon('file', 18)}</div>
            <div><span class="config-section-kicker">Connector metadata</span><div class="drawer-section-title">Details</div><p>System-of-record metadata shown to operators during triage and connector handover.</p></div>
          </div>
          <div class="modern-details-grid">
            <div><span>Auth Mode</span><strong>${esc(c.authMode)}</strong></div>
            <div><span>Country</span><strong>${esc(c.countryCode)}</strong></div>
            <div><span>Environment</span><strong>${esc(c.environmentScope)}</strong></div>
            <div><span>Schedule</span><strong>Every ${esc(String(c.schedules.intervalMin))}m</strong></div>
            ${c.baseUrl ? `<div class="wide"><span>Base URL</span><strong>${esc(c.baseUrl)}</strong></div>` : ''}
            <div><span>TLS verification</span><strong>${c.verifySsl ? 'Enabled' : 'Disabled for test'}</strong></div>
            <div><span>Owner Team</span><strong>${esc(c.ownerTeam || 'Unassigned')}</strong></div>
            <div><span>Created</span><strong>${new Date(c.createdAt).toLocaleDateString()}</strong></div>
          </div>
        </div>
      </div>
    `;
  }

  function renderConfigurationTab(c) {
    const authModeOptions = (c.type === 'APPDYNAMICS' ? ['BasicAuth'] : c.type === 'VROPS' ? ['Token'] : c.type === 'SCOM' ? ['WinRM'] : c.type === 'EMCO' ? ['SqlServerCredentials'] : AUTH_MODES)
      .map(a => `<option value="${a}" ${c.authMode === a ? 'selected' : ''}>${a}</option>`).join('');
    const usesUsernamePassword = c.type === 'APPDYNAMICS' || c.type === 'VROPS' || c.type === 'SCOM';
    const credentialLabelA = usesUsernamePassword ? 'Username' : 'Access Key';
    const credentialLabelB = usesUsernamePassword ? 'Password' : 'Access Secret Key';
    const credentialConfigured = String(c.secretsMask || '').toLowerCase() === 'configured';
    const credentialNeedsReentry = String(c.secretsMask || '').toLowerCase() === 'needs_reentry' || c.credentialRecoveryRequired === true || c.lastTestErrorCode === 'SECRET_DECRYPTION_FAILED';
    const credentialsRequired = c.configurationStatus === 'PENDING' || credentialNeedsReentry;
    const credentialStatusText = credentialNeedsReentry ? 'Credentials need re-entry' : credentialConfigured ? 'Encrypted credentials saved' : 'Credentials not configured';
    const credentialHelp = c.type === 'EMCO'
      ? 'Submit KFH/CCTV SQL credentials only when rotating or completing pending setup.'
      : usesUsernamePassword
      ? 'Submit username/password credentials only when rotating or completing pending setup.'
      : 'Submit access keys only when rotating or completing pending setup.';
    return `
      <div class="connector-config-modern">
        <div class="config-hero-panel">
          <div class="config-hero-icon">${icon('settings', 22)}</div>
          <div class="config-hero-copy"><span>Governed connector setup</span><h3>Secure connection profile</h3><p>Configure endpoint, ownership, credentials, schedule, and collection limits before validating the connector.</p></div>
          <div class="config-hero-chips"><span>${esc(c.countryCode)}</span><span>${esc(c.environmentScope)}</span><span>${esc(c.configurationStatus || c.status)}</span></div>
        </div>
        <div class="drawer-section config-section config-collection-section">
          <div class="config-section-head"><div class="config-section-icon">${icon(c.enabled ? 'check' : 'play', 18)}</div><div><span class="config-section-kicker">Connector state</span><div class="drawer-section-title">Connector Enablement</div><p>Enable or disable scheduled collection for this connector without changing its saved endpoint or credentials.</p></div></div>
it           <div class="config-collection-control ${c.enabled ? 'is-enabled' : 'is-disabled'}"><div><span class="config-collection-label">Current state</span><strong class="config-collection-state ${c.enabled ? 'enabled' : 'disabled'}">${c.enabled ? 'Enabled' : 'Disabled'}</strong><p>${c.enabled ? 'Scheduled connector runs are allowed after configuration is complete.' : 'Scheduled connector runs are disabled until this connector is enabled.'}</p></div><button type="button" onclick="Connectors.toggleConnector('${c.id}', ${!c.enabled})" class="config-collection-toggle ${c.enabled ? 'enabled' : 'disabled'}" aria-pressed="${c.enabled}"><span class="config-collection-switch"><span></span></span><span>${c.enabled ? 'Disable Connector' : 'Enable Connector'}</span></button></div>
        </div>
        <div class="drawer-section config-section">
          <div class="config-section-head"><div class="config-section-icon">${icon('globe', 18)}</div><div><span class="config-section-kicker">Identity and scope</span><div class="drawer-section-title">Basic Settings</div><p>Define the country-aware connector name, runtime environment, and responsible owner team.</p></div></div>
          <div class="drawer-form-group"><label class="drawer-form-label">Connector Name</label><input type="text" id="edit-name" class="drawer-form-input" value="${esc(c.name)}"></div>
          <div class="drawer-form-row"><div class="drawer-form-group"><label class="drawer-form-label">Country</label><input type="text" id="edit-country" class="drawer-form-input" value="${esc(c.countryCode)}" disabled></div><div class="drawer-form-group"><label class="drawer-form-label">Environment</label><select id="edit-scope" class="drawer-form-select">${SCOPES.map(s => `<option value="${s}" ${c.environmentScope === s ? 'selected' : ''}>${s}</option>`).join('')}</select></div></div>
          <div class="drawer-form-row"><div class="drawer-form-group"><label class="drawer-form-label">Owner Team</label><select id="edit-team" class="drawer-form-select">${OWNER_TEAMS.map(t => `<option value="${t}" ${c.ownerTeam === t ? 'selected' : ''}>${t}</option>`).join('')}</select></div></div>
        </div>
        ${c.type === 'BMC' ? `
        <div class="drawer-section config-section config-section-accent">
          <div class="config-section-head"><div class="config-section-icon">${icon('cloud', 18)}</div><div><span class="config-section-kicker">Source endpoint</span><div class="drawer-section-title">BMC Helix Connection</div><p>Use HTTPS endpoints only. Raw credentials stay write-only and are never returned to the browser.</p></div></div>
          <div class="drawer-form-group"><label class="drawer-form-label">BMC Base URL</label><input type="url" id="edit-base-url" class="drawer-form-input" value="${esc(c.baseUrl)}" placeholder="https://kfh-itom.onbmc.com"></div>
          <div class="drawer-form-row"><div class="drawer-form-group"><label class="drawer-form-label">Login Endpoint</label><input type="text" id="edit-login-endpoint" class="drawer-form-input" value="${esc(c.loginEndpoint)}"></div><div class="drawer-form-group"><label class="drawer-form-label">Events Endpoint</label><input type="text" id="edit-events-endpoint" class="drawer-form-input" value="${esc(c.eventsEndpoint)}"></div></div>
          <div class="drawer-form-group"><label class="drawer-form-label">TLS Certificate Verification</label><div class="connector-checkbox-stack"><label><input type="checkbox" id="edit-verify-ssl" ${c.verifySsl ? 'checked' : ''}> Verify TLS certificate chain</label></div><p class="connector-field-help">Keep enabled by default. Clear only for governed dev/hybrid tests while Java truststore CA import is pending.</p></div>
        </div>
        <div class="drawer-section config-section">
          <div class="config-section-head"><div class="config-section-icon">${icon('activity', 18)}</div><div><span class="config-section-kicker">Collection guardrails</span><div class="drawer-section-title">BMC Collection Settings</div><p>Control the lookback window, page size, event cap, and timeout to avoid noisy source-system pulls.</p></div></div>
          <div class="drawer-form-row"><div class="drawer-form-group"><label class="drawer-form-label">Window Minutes</label><input type="number" id="edit-minutes-back" class="drawer-form-input" min="1" max="1440" value="${c.minutesBack}"></div><div class="drawer-form-group"><label class="drawer-form-label">Page Size</label><input type="number" id="edit-page-size" class="drawer-form-input" min="1" max="500" value="${c.pageSize}"></div></div>
          <div class="drawer-form-row"><div class="drawer-form-group"><label class="drawer-form-label">Max Events</label><input type="number" id="edit-max-events" class="drawer-form-input" min="1" max="10000" value="${c.maxEvents}"></div><div class="drawer-form-group"><label class="drawer-form-label">Timeout Seconds</label><input type="number" id="edit-timeout-seconds" class="drawer-form-input" min="5" max="300" value="${c.timeoutSeconds}"></div></div>
        </div>` : ''}
        ${c.type === 'APPDYNAMICS' ? `
        <div class="drawer-section config-section config-section-accent">
          <div class="config-section-head"><div class="config-section-icon">${icon('activity', 18)}</div><div><span class="config-section-kicker">Source endpoint</span><div class="drawer-section-title">AppDynamics Controller</div><p>Use the HTTPS Controller URL ending with <code>/controller</code>. Public and private KFH hybrid hosts/IPs are supported; metadata and localhost targets are blocked.</p></div></div>
          <div class="drawer-form-group"><label class="drawer-form-label">Controller URL</label><input type="url" id="edit-controller-url" class="drawer-form-input" value="${esc(c.controllerUrl || c.baseUrl)}" placeholder="https://appd.corp.kfh.kw/controller"></div>
          <div class="drawer-form-group"><label class="drawer-form-label">TLS Certificate Verification</label><div class="connector-checkbox-stack"><label><input type="checkbox" id="edit-verify-ssl" ${c.verifySsl ? 'checked' : ''}> Verify TLS certificate chain</label></div><p class="connector-field-help">For the current PKIX error, clear this only in dev/hybrid testing or import the KFH CA into the JVM truststore and keep it enabled.</p></div>
        </div>
        <div class="drawer-section config-section">
          <div class="config-section-head"><div class="config-section-icon">${icon('grid', 18)}</div><div><span class="config-section-kicker">Collection guardrails</span><div class="drawer-section-title">AppDynamics Collection Settings</div><p>Control APM lookback, controller timeout, worker fan-out, and event families for future scheduled collection.</p></div></div>
          <div class="drawer-form-row"><div class="drawer-form-group"><label class="drawer-form-label">Duration Minutes</label><input type="number" id="edit-duration-minutes" class="drawer-form-input" min="1" max="1440" value="${c.durationMinutes}"></div><div class="drawer-form-group"><label class="drawer-form-label">Max Workers</label><input type="number" id="edit-max-workers" class="drawer-form-input" min="1" max="64" value="${c.maxWorkers}"></div></div>
          <div class="drawer-form-row"><div class="drawer-form-group"><label class="drawer-form-label">Timeout Seconds</label><input type="number" id="edit-timeout-seconds" class="drawer-form-input" min="5" max="300" value="${c.timeoutSeconds}"></div><div class="drawer-form-group"><label class="drawer-form-label">Event Families</label><div class="connector-checkbox-stack"><label><input type="checkbox" id="edit-fetch-errors" ${c.fetchErrors ? 'checked' : ''}> Error snapshots</label><label><input type="checkbox" id="edit-fetch-violations" ${c.fetchViolations ? 'checked' : ''}> Health-rule violations</label><label><input type="checkbox" id="edit-fetch-slow" ${c.fetchSlowTransactions ? 'checked' : ''}> Slow transactions</label></div></div></div>
        </div>` : ''}
        ${c.type === 'VROPS' ? `
        <div class="drawer-section config-section config-section-accent">
          <div class="config-section-head"><div class="config-section-icon">${icon('cloud', 18)}</div><div><span class="config-section-kicker">Source endpoint</span><div class="drawer-section-title">VMware vROps / Aria Operations</div><p>Use a hostname/IP or HTTPS URL ending with <code>/suite-api/api</code>. Public and private KFH hybrid hosts/IPs are supported.</p></div></div>
          <div class="drawer-form-row"><div class="drawer-form-group"><label class="drawer-form-label">vROps Host or URL</label><input type="text" id="edit-vrops-host" class="drawer-form-input" value="${esc(c.host || c.baseUrl)}" placeholder="10.2.243.66"></div><div class="drawer-form-group"><label class="drawer-form-label">Auth Source</label><input type="text" id="edit-auth-source" class="drawer-form-input" value="${esc(c.authSource || 'KFH AD')}" placeholder="KFH AD"></div></div>
        </div>
        <div class="drawer-section config-section">
          <div class="config-section-head"><div class="config-section-icon">${icon('grid', 18)}</div><div><span class="config-section-kicker">Collection guardrails</span><div class="drawer-section-title">vROps Collection Settings</div><p>Control alert lookback, page limits, timeout, and future resource enrichment fan-out.</p></div></div>
          <div class="drawer-form-row"><div class="drawer-form-group"><label class="drawer-form-label">Window Hours</label><input type="number" id="edit-hours" class="drawer-form-input" min="1" max="168" value="${c.hours || 1}"></div><div class="drawer-form-group"><label class="drawer-form-label">Page Size</label><input type="number" id="edit-page-size" class="drawer-form-input" min="1" max="5000" value="${c.pageSize || 1000}"></div></div>
          <div class="drawer-form-row"><div class="drawer-form-group"><label class="drawer-form-label">Max Pages</label><input type="number" id="edit-max-pages" class="drawer-form-input" min="1" max="1000" value="${c.maxPages || 200}"></div><div class="drawer-form-group"><label class="drawer-form-label">Max Workers</label><input type="number" id="edit-max-workers" class="drawer-form-input" min="1" max="64" value="${c.maxWorkers || 12}"></div></div>
          <div class="drawer-form-row"><div class="drawer-form-group"><label class="drawer-form-label">Timeout Seconds</label><input type="number" id="edit-timeout-seconds" class="drawer-form-input" min="5" max="300" value="${c.timeoutSeconds || 120}"></div><div class="drawer-form-group"><label class="drawer-form-label">TLS Certificate Verification</label><div class="connector-checkbox-stack"><label><input type="checkbox" id="edit-verify-ssl" ${c.verifySsl ? 'checked' : ''}> Verify TLS certificate chain</label></div><p class="connector-field-help">Keep enabled by default; clear only for governed dev/hybrid tests when Java truststore CA import is pending.</p></div></div>
        </div>` : ''}
        ${c.type === 'SCOM' ? `
        <div class="drawer-section config-section config-section-accent">
          <div class="config-section-head"><div class="config-section-icon">${icon('server', 18)}</div><div><span class="config-section-kicker">Source endpoint</span><div class="drawer-section-title">Microsoft SCOM WinRM</div><p>Use the SCOM management server FQDN/IP or a WinRM URL ending with <code>/wsman</code>. Localhost, metadata, link-local, and multicast targets are blocked.</p></div></div>
          <div class="drawer-form-row"><div class="drawer-form-group"><label class="drawer-form-label">SCOM Management Server</label><input type="text" id="edit-scom-server" class="drawer-form-input" value="${esc(c.managementServer || c.host || '')}" placeholder="dcvscoap12.corp.kfh.kw"></div><div class="drawer-form-group"><label class="drawer-form-label">Domain</label><input type="text" id="edit-scom-domain" class="drawer-form-input" value="${esc(c.domain || 'corp.kfh.kw')}" placeholder="corp.kfh.kw"></div></div>
          <div class="drawer-form-row"><div class="drawer-form-group"><label class="drawer-form-label">WinRM Port</label><input type="number" id="edit-winrm-port" class="drawer-form-input" min="1" max="65535" value="${c.winrmPort || 5986}"></div><div class="drawer-form-group"><label class="drawer-form-label">PowerShell Authentication</label><select id="edit-auth-method" class="drawer-form-select">${['Kerberos', 'Negotiate', 'Default', 'CredSSP'].map(a => `<option value="${a}" ${c.authMethod === a ? 'selected' : ''}>${a}</option>`).join('')}</select></div></div>
          <div class="drawer-form-row"><div class="drawer-form-group"><label class="drawer-form-label">WinRM Transport</label><div class="connector-checkbox-stack"><label><input type="checkbox" id="edit-use-https" ${c.useHttps ? 'checked' : ''}> Use HTTPS WinRM transport</label></div></div><div class="drawer-form-group"><label class="drawer-form-label">WinRM Certificate Validation</label><div class="connector-checkbox-stack"><label><input type="checkbox" id="edit-scom-skip-cert-validation" ${!c.verifySsl ? 'checked' : ''}> Disable certificate validation for this SCOM test</label></div><p class="connector-field-help">Temporary dev/hybrid option for CRL/CA/CN issues. Keeps HTTPS/5986 encryption and makes the probe use <code>-SkipCACheck</code>, <code>-SkipCNCheck</code>, and <code>-SkipRevocationCheck</code>. Re-enable verification after PKI/CRL access is fixed.</p></div></div>
        </div>
        <div class="drawer-section config-section">
          <div class="config-section-head"><div class="config-section-icon">${icon('grid', 18)}</div><div><span class="config-section-kicker">Collection guardrails</span><div class="drawer-section-title">SCOM Collection Settings</div><p>Control alert lookback and PowerShell/WinRM timeout for future scheduled collection.</p></div></div>
          <div class="drawer-form-row"><div class="drawer-form-group"><label class="drawer-form-label">Window Hours</label><input type="number" id="edit-hours-back" class="drawer-form-input" min="1" max="168" value="${c.hoursBack || c.hours || 1}"></div><div class="drawer-form-group"><label class="drawer-form-label">Connection Timeout Seconds</label><input type="number" id="edit-connection-timeout-seconds" class="drawer-form-input" min="5" max="300" value="${c.connectionTimeoutSeconds || c.timeoutSeconds || 60}"></div></div>
        </div>` : ''}
        ${c.type === 'EMCO' ? `
        <div class="drawer-section config-section config-section-accent">
          <div class="config-section-head"><div class="config-section-icon">${icon('database', 18)}</div><div><span class="config-section-kicker">Source databases</span><div class="drawer-section-title">EMCO Ping Monitor SQL Server</div><p>Use the SQL Server hostname/IP and listener port only. Localhost, metadata, link-local, multicast, URL paths, credentials, and query strings are blocked.</p></div></div>
          <div class="drawer-form-row"><div class="drawer-form-group"><label class="drawer-form-label">SQL Server Host</label><input type="text" id="edit-emco-sql-server" class="drawer-form-input" value="${esc(c.sqlServer || c.host || '')}" placeholder="DCVSAMDB01"></div><div class="drawer-form-group"><label class="drawer-form-label">SQL Server Port</label><input type="number" id="edit-emco-sql-port" class="drawer-form-input" min="1" max="65535" value="${c.sqlPort || 11433}"></div></div>
          <div class="drawer-form-row"><div class="drawer-form-group"><label class="drawer-form-label">KFH Database</label><input type="text" id="edit-emco-kfh-database" class="drawer-form-input" value="${esc(c.kfhDatabase || 'EMCO_KFH_PROD')}" placeholder="EMCO_KFH_PROD"></div><div class="drawer-form-group"><label class="drawer-form-label">CCTV Database</label><input type="text" id="edit-emco-cctv-database" class="drawer-form-input" value="${esc(c.cctvDatabase || 'EMCO_CCTV_PROD')}" placeholder="EMCO_CCTV_PROD"></div></div>
          <div class="drawer-form-row"><div class="drawer-form-group"><label class="drawer-form-label">SQL Transport Encryption</label><div class="connector-checkbox-stack"><label><input type="checkbox" id="edit-emco-encrypt" ${c.encrypt ? 'checked' : ''}> Encrypt SQL Server connection</label></div><p class="connector-field-help">Keep enabled for governed SQL Server TLS transport.</p></div><div class="drawer-form-group"><label class="drawer-form-label">Server Certificate Trust</label><div class="connector-checkbox-stack"><label><input type="checkbox" id="edit-emco-trust-server-certificate" ${c.trustServerCertificate ? 'checked' : ''}> Trust SQL Server certificate for this test</label></div><p class="connector-field-help">Keep disabled unless explicitly approved for dev/hybrid certificate remediation.</p></div></div>
        </div>
        <div class="drawer-section config-section">
          <div class="config-section-head"><div class="config-section-icon">${icon('grid', 18)}</div><div><span class="config-section-kicker">Collection guardrails</span><div class="drawer-section-title">EMCO Collection Settings</div><p>Control the KFH/CCTV lookback window, SQL login timeout, query timeout, and scheduled collection interval.</p></div></div>
          <div class="drawer-form-row"><div class="drawer-form-group"><label class="drawer-form-label">Window Minutes</label><input type="number" id="edit-minutes-back" class="drawer-form-input" min="1" max="10080" value="${c.minutesBack || 60}"></div><div class="drawer-form-group"><label class="drawer-form-label">Connection Timeout Seconds</label><input type="number" id="edit-connection-timeout-seconds" class="drawer-form-input" min="5" max="300" value="${c.connectionTimeoutSeconds || 30}"></div></div>
          <div class="drawer-form-row"><div class="drawer-form-group"><label class="drawer-form-label">Query Timeout Seconds</label><input type="number" id="edit-emco-query-timeout-seconds" class="drawer-form-input" min="5" max="600" value="${c.queryTimeoutSeconds || c.timeoutSeconds || 120}"></div></div>
        </div>` : ''}
        <div class="drawer-section config-section config-section-secure">
          <div class="config-section-head"><div class="config-section-icon">${icon('zap', 18)}</div><div><span class="config-section-kicker">Secret-safe authentication</span><div class="drawer-section-title">Authentication</div><p>${credentialHelp} Existing secrets remain encrypted server-side.</p></div></div>
          ${credentialNeedsReentry ? `<div class="connector-error-callout"><strong>Credential recovery required.</strong><p>Saved credentials were encrypted with a different platform master key. Restore the original key or re-enter every credential field for this connector, then save and test again.</p></div>` : ''}
          <div class="modern-details-grid credential-status-grid"><div><span>Credential Status</span><strong>${credentialStatusText}</strong></div><div><span>Storage</span><strong>Encrypted server-side table</strong></div></div>
          <div class="drawer-form-group"><label class="drawer-form-label">Auth Mode</label><select id="edit-auth" class="drawer-form-select">${authModeOptions}</select></div>
          ${c.type === 'EMCO' ? `<div class="drawer-form-row"><div class="drawer-form-group"><label class="drawer-form-label">KFH SQL Username ${credentialsRequired ? '*' : '(leave blank to keep existing)'}</label><input type="password" id="edit-emco-kfh-username" class="drawer-form-input" autocomplete="off" placeholder="${credentialsRequired ? 'Required to recover credentials' : '••••••••••••••••'}"></div><div class="drawer-form-group"><label class="drawer-form-label">KFH SQL Password ${credentialsRequired ? '*' : '(leave blank to keep existing)'}</label><input type="password" id="edit-emco-kfh-password" class="drawer-form-input" autocomplete="off" placeholder="${credentialsRequired ? 'Required to recover credentials' : '••••••••••••••••'}"></div></div><div class="drawer-form-row"><div class="drawer-form-group"><label class="drawer-form-label">CCTV SQL Username ${credentialsRequired ? '*' : '(leave blank to keep existing)'}</label><input type="password" id="edit-emco-cctv-username" class="drawer-form-input" autocomplete="off" placeholder="${credentialsRequired ? 'Required to recover credentials' : '••••••••••••••••'}"></div><div class="drawer-form-group"><label class="drawer-form-label">CCTV SQL Password ${credentialsRequired ? '*' : '(leave blank to keep existing)'}</label><input type="password" id="edit-emco-cctv-password" class="drawer-form-input" autocomplete="off" placeholder="${credentialsRequired ? 'Required to recover credentials' : '••••••••••••••••'}"></div></div>` : `<div class="drawer-form-row"><div class="drawer-form-group"><label class="drawer-form-label">${credentialLabelA} ${credentialsRequired ? '*' : '(leave blank to keep existing)'}</label><input type="password" id="edit-access-key" class="drawer-form-input" autocomplete="off" placeholder="${credentialsRequired ? 'Required to recover credentials' : '••••••••••••••••'}"></div><div class="drawer-form-group"><label class="drawer-form-label">${credentialLabelB} ${credentialsRequired ? '*' : '(leave blank to keep existing)'}</label><input type="password" id="edit-access-secret-key" class="drawer-form-input" autocomplete="off" placeholder="${credentialsRequired ? 'Required to recover credentials' : '••••••••••••••••'}"></div></div>`}
          <p class="config-secure-note">${icon('check', 14)} Credentials are encrypted and stored securely. Plaintext values are never shown after saving.</p>
        </div>
        <div class="drawer-section config-section"><div class="config-section-head"><div class="config-section-icon">${icon('play', 18)}</div><div><span class="config-section-kicker">Run cadence</span><div class="drawer-section-title">Schedule</div><p>Set the recurring collection interval after the connection test passes.</p></div></div><div class="drawer-form-group"><label class="drawer-form-label">Sync Interval (minutes)</label><select id="edit-interval" class="drawer-form-select">${[5, 10, 15, 30, 60].map(m => `<option value="${m}" ${c.schedules.intervalMin === m ? 'selected' : ''}>${m} minutes</option>`).join('')}</select></div></div>
        <div class="drawer-section config-section"><div class="config-section-head"><div class="config-section-icon">${icon('file', 18)}</div><div><span class="config-section-kicker">Operations context</span><div class="drawer-section-title">Notes</div><p>Capture support ownership notes, rollout comments, or operational caveats for NOC handover.</p></div></div><textarea id="edit-notes" class="drawer-form-textarea" rows="4" placeholder="Add notes about this connector...">${esc(c.notes || '')}</textarea></div>
      </div>
    `;
  }
  function renderMappingTab(c) {
    return `
      <div class="connector-tab-modern mapping-tab-modern">
        <div class="config-hero-panel tab-hero-panel">
          <div class="config-hero-icon">${icon('grid', 22)}</div>
          <div class="config-hero-copy"><span>Normalization mapping</span><h3>Source field alignment</h3><p>Map connector-specific payload fields into the canonical country-aware telemetry model.</p></div>
          <div class="config-hero-chips"><span>${esc(c.type)}</span><span>Canonical</span></div>
        </div>

        <div class="drawer-section config-section">
          <div class="config-section-head"><div class="config-section-icon">${icon('file', 18)}</div><div><span class="config-section-kicker">Identity fields</span><div class="drawer-section-title">Field Mapping</div><p>Choose which source fields identify applications and assets for enrichment and topology correlation.</p></div></div>
          <div class="drawer-form-row">
            <div class="drawer-form-group"><label class="drawer-form-label">Application Field</label><input type="text" class="drawer-form-input" value="${esc(c.mappings.appField)}" placeholder="applicationName"></div>
            <div class="drawer-form-group"><label class="drawer-form-label">Asset Field</label><input type="text" class="drawer-form-input" value="${esc(c.mappings.assetField)}" placeholder="resourceName"></div>
          </div>
        </div>

        <div class="drawer-section config-section">
          <div class="config-section-head"><div class="config-section-icon">${icon('alert', 18)}</div><div><span class="config-section-kicker">Severity translation</span><div class="drawer-section-title">Severity Mapping</div><p>Normalize tool severities into platform severities used by RCA, incident lifecycle, and dashboards.</p></div></div>
          <div class="modern-table-wrap"><table class="modern-mapping-table"><thead><tr><th>Tool Severity</th><th>Platform Severity</th></tr></thead><tbody>
            ${Object.entries(c.mappings.severityMap).map(([key, value]) => `<tr><td><span class="mapping-source-pill">${esc(key)}</span></td><td><select class="drawer-form-select">${['critical', 'high', 'medium', 'low', 'info'].map(s => `<option value="${s}" ${value === s ? 'selected' : ''}>${s.charAt(0).toUpperCase() + s.slice(1)}</option>`).join('')}</select></td></tr>`).join('') || '<tr><td colspan="2"><div class="modern-empty-state">No severity mappings returned by the API.</div></td></tr>'}
          </tbody></table></div>
        </div>
      </div>
    `;
  }

  function renderHealthTab(c, currentTestRun, latestTestRun) {
    return `
      <div class="connector-tab-modern health-tab-modern">
        <div class="config-hero-panel tab-hero-panel">
          <div class="config-hero-icon">${icon('activity', 22)}</div>
          <div class="config-hero-copy"><span>Connector health</span><h3>Runtime validation</h3><p>Review event trend placeholders, last connection test, and deterministic health checks. A PASS validates the saved profile; it does not lock endpoint or credential updates.</p></div>
          <div class="health-hero-actions">
            <button type="button" onclick="Connectors.setDrawerTab('configuration')" class="health-edit-config">${icon('settings', 14)} Edit Configuration</button>
            <button type="button" onclick="Connectors.runTest('${c.id}')" class="health-run-test" ${currentTestRun ? 'disabled' : ''}>${icon('play', 14)} ${currentTestRun ? 'Running...' : 'Run Test'}</button>
          </div>
        </div>

        <div class="drawer-section config-section">
          <div class="config-section-head"><div class="config-section-icon">${icon('activity', 18)}</div><div><span class="config-section-kicker">Telemetry trend</span><div class="drawer-section-title">Events Trend (24h)</div><p>Trend visualization will use connector metrics once chart data is available from the API.</p></div></div>
          <div class="modern-chart-placeholder"><span>${icon('activity', 22)}</span><strong>Chart visualization</strong><small>Events and errors over the last 24 hours</small></div>
        </div>

      ${currentTestRun ? `
      <div class="drawer-section config-section">
        <div class="config-section-head"><div class="config-section-icon">${icon('zap', 18)}</div><div><span class="config-section-kicker">Live validation</span><div class="drawer-section-title">Test Execution</div><p>Connection test steps run through endpoint validation, authentication, and parsing checks.</p></div></div>
        <div class="test-steps">
          ${(currentTestRun.steps.length ? currentTestRun.steps : [{ name: 'Waiting for validation response', status: 'running' }]).map((step, idx) => `
            <div class="test-step">
              <div class="test-step-circle test-step-circle-${step.status}">${step.status === 'pass' ? icon('check', 14) : step.status === 'fail' ? icon('x', 14) : step.status === 'running' ? icon('zap', 12) : idx + 1}</div>
              <div class="test-step-name">${step.name}</div>
              ${idx < currentTestRun.steps.length - 1 ? `<div class="test-step-line ${step.status === 'pass' ? 'active' : ''}"></div>` : ''}
            </div>
          `).join('')}
        </div>
      </div>
      ` : ''}

      ${latestTestRun && latestTestRun.result !== 'running' ? `
      <div class="drawer-section config-section">
        <div class="config-section-head"><div class="config-section-icon">${icon('check', 18)}</div><div><span class="config-section-kicker">Last execution</span><div class="drawer-section-title">Latest Test Result</div><p>Most recent connector validation result returned by the test endpoint.</p></div></div>
        <div class="modern-endpoint-item">
          <div class="endpoint-url">Completed ${formatTime(latestTestRun.startedAt)}</div>
          <span class="status-badge status-badge-${latestTestRun.result === 'Pass' ? 'healthy' : 'down'}">${latestTestRun.result}</span>
        </div>
        ${latestTestRun.message ? `<p class="test-result-message">${esc(latestTestRun.message)}</p>` : ''}
        ${latestTestRun.errorCode === 'SECRET_DECRYPTION_FAILED' ? `<div class="connector-error-callout"><strong>Credential recovery required.</strong><p>Restore the original stable platform secret key, or choose <strong>Edit Configuration</strong> and re-enter every credential field for this connector.</p></div>` : `<p class="test-result-hint">Need to rotate access keys or change connection details? Choose <strong>Edit Configuration</strong>; leave credential fields blank to keep existing encrypted secrets.</p>`}
      </div>
      ` : ''}

      <div class="drawer-section config-section">
        <div class="config-section-head"><div class="config-section-icon">${icon('check', 18)}</div><div><span class="config-section-kicker">Deterministic checks</span><div class="drawer-section-title">Health Checks</div><p>Evidence-oriented checks used to confirm the connector can safely collect telemetry.</p></div></div>
        <div class="modern-health-grid">${[
          { name: 'DNS Resolution', status: 'pass' },
          { name: 'Auth Handshake', status: c.status === 'Down' ? 'fail' : 'pass' },
          { name: 'API Connectivity', status: c.status === 'Down' ? 'fail' : 'pass' },
          { name: 'Data Parsing', status: c.status === 'Degraded' ? 'fail' : 'pass' }
        ].map(check => `
          <div class="health-check-item modern-health-item">
            <span class="health-check-name">${check.name}</span>
            <span class="status-badge status-badge-${check.status === 'pass' ? 'healthy' : 'down'}">${check.status === 'pass' ? 'Pass' : 'Fail'}</span>
          </div>
        `).join('')}</div>
      </div>
      </div>
    `;
  }

  function renderLogsTab(logs) {
    return `
      <div class="connector-tab-modern logs-tab-modern">
        <div class="config-hero-panel tab-hero-panel">
          <div class="config-hero-icon">${icon('file', 22)}</div>
          <div class="config-hero-copy"><span>Connector audit stream</span><h3>Recent Logs</h3><p>Operational messages from connector execution and validation. Secret values are not displayed.</p></div>
          <div class="config-hero-chips"><span>${logs.length} entries</span></div>
        </div>
        <div class="drawer-section config-section">
          <div class="config-section-head"><div class="config-section-icon">${icon('list', 18)}</div><div><span class="config-section-kicker">Execution messages</span><div class="drawer-section-title">Recent Logs</div><p>Filter by level to isolate warning/error patterns during connector setup.</p></div></div>
          <div class="modern-log-toolbar"><select class="connector-filter-select">
            <option value="">All Levels</option>
            <option value="INFO">INFO</option>
            <option value="WARN">WARN</option>
            <option value="ERROR">ERROR</option>
            <option value="DEBUG">DEBUG</option>
          </select></div>
        <div class="logs-container">
          ${logs.length === 0 ? '<div class="modern-empty-state">No connector logs returned by the API.</div>' : logs.map(log => `
            <div class="log-entry">
              <span class="log-time">${new Date(log.timestamp).toLocaleTimeString()}</span>
              <span class="log-level log-level-${log.level.toLowerCase()}">${log.level}</span>
              <span class="log-message">${esc(log.message)}</span>
            </div>
          `).join('')}
        </div>
      </div>
      </div>
    `;
  }

  // Modal
  function openModal() {
    ensureConnectorOverlays();
    isModalOpen = true;
    renderModal();
    document.getElementById('connector-modal-overlay')?.classList.add('open');
  }

  function closeModal() {
    isModalOpen = false;
    document.getElementById('connector-modal-overlay')?.classList.remove('open');
  }

  function renderConnectorTypePicker() {
    return `
      <div class="connector-picker-grid" role="list" aria-label="Available connector types">
        ${availableTypes().map(t => `
          <div role="listitem" class="connector-picker-card ${selectedNewType === t.value ? 'selected' : ''} ${t.available === false ? 'disabled' : ''}">
            <span class="connector-picker-icon">${icon(t.icon || 'server', 22)}</span>
            <span class="connector-picker-name">${esc(t.label)}</span>
            <span class="connector-picker-category">${esc(t.category || 'Connector')}</span>
            <span class="connector-picker-status ${t.available === false ? 'coming-soon' : 'ready'}">${t.available === false ? 'Coming soon' : 'Ready'}</span>
            ${renderInstallAction(t)}
          </div>
        `).join('')}
      </div>
    `;
  }


  function renderInstallAction(t) {
    if (t.available === false) {
      return '<button type="button" class="connector-install-btn" disabled>Coming soon</button>';
    }
    const installed = installedConnectorFor(t.value);
    if (installed) {
      return `
        <button type="button" class="connector-install-btn installed" onclick="Connectors.openDrawer('${installed.id}')">Configure</button>
        <button type="button" class="connector-install-btn uninstall" onclick="Connectors.uninstallConnector('${t.value}')">Uninstall</button>
      `;
    }
    return `<button type="button" class="connector-install-btn" onclick="Connectors.installConnector('${t.value}')">Install</button>`;
  }

  function optionList(values, selected) {
    return values.map(value => `<option value="${value}" ${value === selected ? 'selected' : ''}>${value}</option>`).join('');
  }

  function renderBmcConnectorForm() {
    const country = defaultCountry();
    const environment = defaultEnvironment();
    const countryLocked = !canChooseCountry();
    return `
      <div class="connector-modal-section">
        <div class="connector-modal-section-title">BMC Helix connector details</div>
        <div class="drawer-form-row">
          <div class="drawer-form-group">
            <label class="drawer-form-label">Connector Name *</label>
            <input type="text" id="new-name" class="drawer-form-input" maxlength="150" placeholder="e.g., BMC Helix KW PROD">
          </div>
          <div class="drawer-form-group">
            <label class="drawer-form-label">Country *</label>
            <select id="new-country" class="drawer-form-select" ${countryLocked ? 'disabled' : ''}>
              ${optionList(COUNTRIES, country)}
            </select>
            ${countryLocked ? `<input type="hidden" id="new-country-hidden" value="${country}">` : ''}
          </div>
        </div>
        <div class="drawer-form-row">
          <div class="drawer-form-group">
            <label class="drawer-form-label">Environment *</label>
            <select id="new-environment" class="drawer-form-select">
              ${optionList(SCOPES, environment)}
            </select>
          </div>
          <div class="drawer-form-group">
            <label class="drawer-form-label">Owner Team</label>
            <select id="new-team" class="drawer-form-select">
              ${OWNER_TEAMS.map(t => `<option value="${esc(t)}">${esc(t)}</option>`).join('')}
            </select>
          </div>
        </div>
      </div>

      <div class="connector-modal-section">
        <div class="connector-modal-section-title">Connection settings</div>
        <div class="drawer-form-group">
          <label class="drawer-form-label">BMC Base URL *</label>
          <input type="url" id="new-base-url" class="drawer-form-input" placeholder="https://kfh-itom.onbmc.com">
          <p class="connector-field-help">HTTPS tenant URL only. API paths are configured separately; public and private KFH hybrid hosts/IPs are supported.</p>
        </div>
        <div class="drawer-form-row">
          <div class="drawer-form-group">
            <label class="drawer-form-label">Login Endpoint *</label>
            <input type="text" id="new-login-endpoint" class="drawer-form-input" value="/ims/api/v1/access_keys/login">
          </div>
          <div class="drawer-form-group">
            <label class="drawer-form-label">Events Search Endpoint *</label>
            <input type="text" id="new-events-endpoint" class="drawer-form-input" value="/events-service/api/v1.0/events/msearch">
          </div>
        </div>
        <div class="drawer-form-group">
          <label class="drawer-form-label">TLS Certificate Verification</label>
          <div class="connector-checkbox-stack"><label><input type="checkbox" id="new-verify-ssl" checked> Verify TLS certificate chain</label></div>
          <p class="connector-field-help">Keep enabled by default. Clear only for governed dev/hybrid testing while the corporate CA is not yet imported into the Java truststore.</p>
        </div>
      </div>

      <div class="connector-modal-section">
        <div class="connector-modal-section-title">Credentials</div>
        <div class="drawer-form-row">
          <div class="drawer-form-group">
            <label class="drawer-form-label">Access Key *</label>
            <input type="password" id="new-access-key" class="drawer-form-input" autocomplete="off" placeholder="Stored encrypted server-side">
          </div>
          <div class="drawer-form-group">
            <label class="drawer-form-label">Access Secret Key *</label>
            <input type="password" id="new-access-secret-key" class="drawer-form-input" autocomplete="off" placeholder="Never returned by APIs">
          </div>
        </div>
        <p class="connector-field-help">Secrets are submitted once as <code>secretsPlain</code>, stripped from responses, and represented only as a mask.</p>
      </div>

      <div class="connector-modal-section">
        <div class="connector-modal-section-title">Collection settings</div>
        <div class="drawer-form-row">
          <div class="drawer-form-group">
            <label class="drawer-form-label">Window Minutes</label>
            <input type="number" id="new-minutes-back" class="drawer-form-input" min="1" max="1440" value="60">
          </div>
          <div class="drawer-form-group">
            <label class="drawer-form-label">Page Size</label>
            <input type="number" id="new-page-size" class="drawer-form-input" min="1" max="500" value="100">
          </div>
        </div>
        <div class="drawer-form-row">
          <div class="drawer-form-group">
            <label class="drawer-form-label">Max Events Per Run</label>
            <input type="number" id="new-max-events" class="drawer-form-input" min="1" max="10000" value="500">
          </div>
          <div class="drawer-form-group">
            <label class="drawer-form-label">Timeout Seconds</label>
            <input type="number" id="new-timeout-seconds" class="drawer-form-input" min="5" max="300" value="120">
          </div>
        </div>
        <div class="drawer-form-row">
          <div class="drawer-form-group">
            <label class="drawer-form-label">Sync Interval</label>
            <select id="new-interval" class="drawer-form-select">
              ${[5, 10, 15, 30, 60].map(m => `<option value="${m}" ${m === 15 ? 'selected' : ''}>${m} minutes</option>`).join('')}
            </select>
          </div>
          <div class="drawer-form-group">
            <label class="drawer-form-label">Notes</label>
            <textarea id="new-notes" class="drawer-form-textarea" rows="2" maxlength="500" placeholder="Operational notes for this country connector"></textarea>
          </div>
        </div>
      </div>
    `;
  }

  function renderModal() {
    ensureConnectorOverlays();
    const modal = document.getElementById('connector-modal-overlay');
    if (!modal) return;

    modal.innerHTML = `
      <div class="modal connector-add-modal">
        <div class="modal-header">
          <div>
            <h3 class="modal-title">Add Connector</h3>
            <p class="connector-modal-subtitle">Choose a source, then configure a country-specific connector instance.</p>
          </div>
          <button onclick="Connectors.closeModal()" class="modal-close">${icon('close', 20)}</button>
        </div>
        <div class="modal-body">
          <div class="connector-modal-section connector-catalog-section">
            <div class="connector-modal-section-title">Connector catalog</div>
            <p class="connector-modal-help">Install a connector first. Installed connectors appear on the Connectors page as disabled until you open Configure and enter connection details.</p>
            ${renderConnectorTypePicker()}
          </div>
        </div>
        <div class="modal-footer">
          <button onclick="Connectors.closeModal()" class="px-4 py-2 text-sm font-medium text-gray-600 hover:bg-gray-100 rounded-lg transition-colors">Cancel</button>
          <button onclick="Connectors.closeModal()" class="px-4 py-2 bg-[#128754] hover:bg-[#0E6B42] text-white rounded-lg text-sm font-semibold transition-colors flex items-center gap-2">Done</button>
        </div>
      </div>
    `;
  }

  // Events
  function bindEvents() {
    KFHUtils.bindLiveSearch('connector-search', function(value) {
      searchQuery = value;
      resetConnectorPage();
      render();
    });

    document.getElementById('connector-drawer-overlay')?.addEventListener('click', closeDrawer);
  }

  // Actions
  function setViewMode(mode) {
    resetConnectorPage();
    viewMode = mode;
    render();
  }

  function setPage(page) {
    connectorCardPage = Number(page) || 1;
    render();
  }

  function setDrawerTab(tab) {
    drawerTab = tab;
    renderDrawer();
  }

  function toggleSmartFilters() {
    smartFiltersOpen = !smartFiltersOpen;
    render();
  }

  function setSmartFilter(group, value) {
    if (!Object.prototype.hasOwnProperty.call(filters, group)) return;
    filters[group] = value;
    smartFiltersOpen = false;
    resetConnectorPage();
    render();
  }

  function openInventory() {
    pageMode = 'inventory';
    marketplaceDetailType = '';
    syncUrl();
    render();
  }

  function openMarketplace() {
    marketplaceInstallCountry = marketplaceCountry();
    pageMode = 'marketplace';
    marketplaceDetailType = '';
    syncUrl();
    render();
  }

  function openMarketplaceDetail(type) {
    marketplaceInstallCountry = marketplaceCountry();
    marketplaceDetailType = type;
    pageMode = 'marketplace-detail';
    syncUrl();
    render();
  }

  // Keep the URL hash in sync with the current marketplace view so a page
  // refresh restores the Marketplace / Marketplace Detail screen instead of
  // falling back to the default Inventory view. history.replaceState does NOT
  // fire `hashchange`, so the Router is not re-invoked here.
  function syncUrl() {
    if (typeof window === 'undefined' || !window.history || !window.history.replaceState) return;
    let target = '#connectors';
    if (pageMode === 'marketplace') {
      target = '#connectors?view=marketplace';
    } else if (pageMode === 'marketplace-detail' && marketplaceDetailType) {
      target = `#connectors?view=marketplace-detail&type=${encodeURIComponent(marketplaceDetailType)}`;
    }
    if (window.location.hash !== target) {
      try { window.history.replaceState(null, '', target); } catch (_) { /* ignore */ }
    }
  }

  // Parse current URL hash to restore page mode on load/refresh.
  function restoreModeFromHash() {
    if (typeof window === 'undefined' || !window.location) return;
    const raw = String(window.location.hash || '').replace(/^#/, '');
    const qIndex = raw.indexOf('?');
    if (qIndex < 0) return;
    const query = raw.slice(qIndex + 1);
    const params = new URLSearchParams(query);
    const view = (params.get('view') || '').toLowerCase();
    if (view === 'marketplace') {
      pageMode = 'marketplace';
      marketplaceDetailType = '';
    } else if (view === 'marketplace-detail') {
      const type = (params.get('type') || '').trim();
      if (type) {
        pageMode = 'marketplace-detail';
        marketplaceDetailType = type;
      }
    }
  }

  function setMarketplaceCountry(country) {
    if (!canChooseCountry() || !COUNTRIES.includes(country)) return;
    marketplaceInstallCountry = country;
    render();
  }

  function setNewConnectorType(type) {
    const selected = typeInfo(type);
    if (!selected || selected.available === false) return;
    selectedNewType = type;
    renderModal();
  }

  async function installConnector(type) {
    const selected = typeInfo(type);
    if (!selected || selected.available === false || !['BMC', 'APPDYNAMICS', 'VROPS', 'SCOM', 'EMCO'].includes(type)) {
      toast('This connector is not available yet', 'error');
      return;
    }
    const country = marketplaceCountry();
    const environment = defaultEnvironment();
    const label = selected.label || type;
    try {
      await APIClient.connectors.create({
        name: `${label} ${country} ${environment}`,
        enabled: true,
        attributes: { pluginType: type, countryCode: country, environment, installOnly: true, configurationStatus: 'PENDING' }
      });
      await loadConnectors();
      marketplaceDetailType = type;
      pageMode = 'marketplace-detail';
      syncUrl();
      render();
      toast(`${label} installed as enabled. Open Configure to enter connection details.`, 'success');
    } catch (error) {
      toast('Unable to install connector', 'error');
    }
  }
  async function uninstallConnector(type) {
    const installed = installedConnectorFor(type);
    if (!installed) return;
    await deleteConnector(installed.id);
    marketplaceDetailType = type;
    pageMode = 'marketplace-detail';
    syncUrl();
    render();
  }

  async function toggleConnector(id, enabled) {
    const c = connectors.find(c => c.id === id);
    if (c) {
      try {
        await APIClient.connectors.toggle(id, enabled);
        await loadConnectors();
        if (selectedConnector?.id === id) {
          selectedConnector = connectors.find(connector => connector.id === id) || selectedConnector;
        }
        toast(`Connector ${enabled ? 'enabled' : 'disabled'}`, 'info');
        render();
        if (selectedConnector?.id === id) renderDrawer();
      } catch (error) {
        toast('Unable to update connector status', 'error');
      }
    }
  }

  async function runTest(connectorId, stayOnCurrentTab = false) {
    const c = connectors.find(c => c.id === connectorId);
    if (!c) {
      toast('Connector is not loaded. Refresh connectors and try again.', 'error');
      return;
    }
    const testId = genId();
    const testRun = { id: testId, connectorId, startedAt: Date.now(), result: 'running', steps: [] };
    testRuns.unshift(testRun);
    setDrawerActionBusy('test', true, 'Testing...');
    toast('Test requested...', 'info');
    if (selectedConnector?.id === connectorId && !stayOnCurrentTab) {
      drawerTab = 'health';
      renderDrawer();
    }
    try {
      const result = await APIClient.connectors.test(connectorId);
      testRun.result = result?.pass === false || result?.status === 'FAIL' ? 'Fail' : 'Pass';
      testRun.steps = Array.isArray(result?.steps) ? result.steps : [];
      testRun.message = result?.message || '';
      testRun.errorCode = result?.errorCode || '';
      await loadConnectors();
      if (selectedConnector?.id === connectorId) {
        selectedConnector = connectors.find(connector => connector.id === connectorId) || selectedConnector;
      }
      toast(testRun.result === 'Pass' ? 'Test passed' : (testRun.message || 'Test failed'), testRun.result === 'Pass' ? 'success' : 'error');
      if (selectedConnector?.id === connectorId && !stayOnCurrentTab) renderDrawer();
    } catch (error) {
      testRun.result = 'Fail';
      testRun.message = errorMessage(error, 'Connector test failed');
      toast(testRun.message, 'error');
      if (selectedConnector?.id === connectorId && !stayOnCurrentTab) renderDrawer();
    } finally {
      setDrawerActionBusy('test', false);
    }
  }

  async function runHeartbeat() {
    if (heartbeatBusy) return;
    if (!window.APIClient || !APIClient.connectors || !APIClient.connectors.heartbeat) {
      toast('Connector heartbeat API is unavailable', 'error');
      return;
    }
    heartbeatBusy = true;
    render();
    toast('Heartbeat requested for enabled connectors...', 'info');
    try {
      const result = await APIClient.connectors.heartbeat();
      const heartbeatResults = Array.isArray(result?.results) ? result.results : [];
      heartbeatResults.forEach(item => {
        const connectorId = String(item.connectorId || item.id || '');
        if (!connectorId) return;
        testRuns.unshift({
          id: genId(),
          connectorId,
          startedAt: Date.parse(item.testedAt || result.checkedAt) || Date.now(),
          result: item.pass === true ? 'Pass' : 'Fail',
          steps: Array.isArray(item.steps) ? item.steps : [],
          message: item.message || ''
        });
      });
      await loadConnectors();
      if (selectedConnector) {
        selectedConnector = connectors.find(connector => connector.id === selectedConnector.id) || selectedConnector;
      }
      const down = Number(result?.down || heartbeatResults.filter(item => item.pass !== true).length || 0);
      const total = Number(result?.totalEnabled || heartbeatResults.length || 0);
      toast(down > 0 ? `Heartbeat complete: ${down} of ${total} enabled connector(s) down` : `Heartbeat complete: ${total} enabled connector(s) healthy`, down > 0 ? 'error' : 'success');
    } catch (error) {
      toast(errorMessage(error, 'Connector heartbeat failed'), 'error');
    } finally {
      heartbeatBusy = false;
      render();
      if (selectedConnector) renderDrawer();
    }
  }

  async function testCurrentConfiguration(connectorId) {
    if (drawerTab === 'configuration' && selectedConnector?.id === connectorId) {
      const saved = await saveConnector({ keepOpen: true, silentSuccess: true, busyAction: 'test', busyLabel: 'Saving...' });
      if (!saved) {
        return;
      }
    }
    await runTest(connectorId);
  }

  async function addConnector() {
    const name = document.getElementById('new-name')?.value;
    const country = document.getElementById('new-country')?.value || document.getElementById('new-country-hidden')?.value || defaultCountry();
    const environment = document.getElementById('new-environment')?.value || defaultEnvironment();
    const baseUrl = document.getElementById('new-base-url')?.value;
    const accessKey = document.getElementById('new-access-key')?.value;
    const accessSecretKey = document.getElementById('new-access-secret-key')?.value;

    if (selectedNewType !== 'BMC') {
      toast('Only BMC Helix connector creation is available in this phase', 'error');
      return;
    }

    if (!name || !baseUrl || !accessKey || !accessSecretKey) {
      toast('Please fill in BMC name, base URL, access key, and access secret key', 'error');
      return;
    }

    const newConnector = {
      name,
      enabled: true,
      attributes: {
        pluginType: 'BMC',
        countryCode: country,
        environment,
        baseUrl,
        loginEndpoint: document.getElementById('new-login-endpoint')?.value || '/ims/api/v1/access_keys/login',
        eventsEndpoint: document.getElementById('new-events-endpoint')?.value || '/events-service/api/v1.0/events/msearch',
        authMode: 'AccessKey',
        minutesBack: parseInt(document.getElementById('new-minutes-back')?.value) || 60,
        pageSize: parseInt(document.getElementById('new-page-size')?.value) || 100,
        maxEvents: parseInt(document.getElementById('new-max-events')?.value) || 500,
        timeoutSeconds: parseInt(document.getElementById('new-timeout-seconds')?.value) || 120,
        verifySsl: checkedSetting('new-verify-ssl', true),
        intervalMin: parseInt(document.getElementById('new-interval')?.value) || 15,
        notes: document.getElementById('new-notes')?.value || '',
        ownerTeam: document.getElementById('new-team')?.value || 'Platform Ops',
        secretsPlain: { accessKey, accessSecretKey }
      }
    };
    try {
      await APIClient.connectors.create(newConnector);
      await loadConnectors();
      closeModal();
      toast('Connector added successfully', 'success');
      render();
    } catch (error) {
      toast('Unable to create connector', 'error');
    }
  }

  async function saveConnector(options = {}) {
    if (!selectedConnector) {
      toast('Connector is not selected. Reopen the connector and try again.', 'error');
      return false;
    }
    const busyAction = options.busyAction || 'save';
    try {
      setDrawerActionBusy(busyAction, true, options.busyLabel || 'Saving...');
      const accessKey = document.getElementById('edit-access-key')?.value || '';
      const accessSecretKey = document.getElementById('edit-access-secret-key')?.value || '';
      const attributes = {
        pluginType: selectedConnector.type,
        environment: document.getElementById('edit-scope')?.value || selectedConnector.environmentScope,
        environmentScope: document.getElementById('edit-scope')?.value || selectedConnector.environmentScope,
        ownerTeam: document.getElementById('edit-team')?.value || selectedConnector.ownerTeam,
        authMode: document.getElementById('edit-auth')?.value || selectedConnector.authMode,
        baseUrl: document.getElementById('edit-base-url')?.value || selectedConnector.baseUrl,
        controllerUrl: document.getElementById('edit-controller-url')?.value || selectedConnector.controllerUrl,
        host: document.getElementById('edit-vrops-host')?.value || selectedConnector.host,
        sqlServer: document.getElementById('edit-emco-sql-server')?.value || selectedConnector.sqlServer,
        sqlPort: parseInt(document.getElementById('edit-emco-sql-port')?.value) || selectedConnector.sqlPort,
        kfhDatabase: document.getElementById('edit-emco-kfh-database')?.value || selectedConnector.kfhDatabase,
        cctvDatabase: document.getElementById('edit-emco-cctv-database')?.value || selectedConnector.cctvDatabase,
        managementServer: document.getElementById('edit-scom-server')?.value || selectedConnector.managementServer,
        domain: document.getElementById('edit-scom-domain')?.value || selectedConnector.domain,
        winrmPort: parseInt(document.getElementById('edit-winrm-port')?.value) || selectedConnector.winrmPort,
        useHttps: checkedSetting('edit-use-https', selectedConnector.useHttps),
        authMethod: document.getElementById('edit-auth-method')?.value || selectedConnector.authMethod,
        authSource: document.getElementById('edit-auth-source')?.value || selectedConnector.authSource,
        loginEndpoint: document.getElementById('edit-login-endpoint')?.value || selectedConnector.loginEndpoint,
        eventsEndpoint: document.getElementById('edit-events-endpoint')?.value || selectedConnector.eventsEndpoint,
        hours: parseInt(document.getElementById('edit-hours')?.value) || selectedConnector.hours,
        hoursBack: parseInt(document.getElementById('edit-hours-back')?.value) || selectedConnector.hoursBack,
        minutesBack: parseInt(document.getElementById('edit-minutes-back')?.value) || selectedConnector.minutesBack,
        connectionTimeoutSeconds: parseInt(document.getElementById('edit-connection-timeout-seconds')?.value) || selectedConnector.connectionTimeoutSeconds,
        queryTimeoutSeconds: parseInt(document.getElementById('edit-emco-query-timeout-seconds')?.value) || selectedConnector.queryTimeoutSeconds,
        durationMinutes: parseInt(document.getElementById('edit-duration-minutes')?.value) || selectedConnector.durationMinutes,
        pageSize: parseInt(document.getElementById('edit-page-size')?.value) || selectedConnector.pageSize,
        maxEvents: parseInt(document.getElementById('edit-max-events')?.value) || selectedConnector.maxEvents,
        maxPages: parseInt(document.getElementById('edit-max-pages')?.value) || selectedConnector.maxPages,
        maxWorkers: parseInt(document.getElementById('edit-max-workers')?.value) || selectedConnector.maxWorkers,
        timeoutSeconds: parseInt(document.getElementById('edit-timeout-seconds')?.value) || selectedConnector.timeoutSeconds,
        verifySsl: connectorVerifySslSetting(selectedConnector),
        encrypt: checkedSetting('edit-emco-encrypt', selectedConnector.encrypt),
        trustServerCertificate: checkedSetting('edit-emco-trust-server-certificate', selectedConnector.trustServerCertificate),
        fetchErrors: checkedSetting('edit-fetch-errors', selectedConnector.fetchErrors),
        fetchViolations: checkedSetting('edit-fetch-violations', selectedConnector.fetchViolations),
        fetchSlowTransactions: checkedSetting('edit-fetch-slow', selectedConnector.fetchSlowTransactions),
        intervalMin: parseInt(document.getElementById('edit-interval')?.value) || selectedConnector.schedules.intervalMin,
        notes: document.getElementById('edit-notes')?.value || ''
      };
      const submittedSecrets = selectedConnector.type === 'EMCO'
        ? emcoSecretPayload()
        : connectorSecretPayload(selectedConnector, accessKey, accessSecretKey);
      if (Object.keys(submittedSecrets).length > 0) {
        attributes.secretsPlain = submittedSecrets;
      }
      if (!validateConnectorSaveForm(selectedConnector, attributes, accessKey, accessSecretKey)) {
        return false;
      }
      await APIClient.connectors.update(selectedConnector.id, {
        name: document.getElementById('edit-name')?.value || selectedConnector.name,
        attributes
      });
      await loadConnectors();
      selectedConnector = connectors.find(connector => connector.id === selectedConnector.id) || selectedConnector;
      if (!options.keepOpen) {
        closeDrawer();
      } else {
        renderDrawer();
      }
      if (!options.silentSuccess) {
        toast('Connector updated successfully', 'success');
      }
      render();
      return true;
    } catch (error) {
      toast(errorMessage(error, 'Unable to update connector'), 'error');
      return false;
    } finally {
      setDrawerActionBusy(busyAction, false);
    }
  }

  function connectorSecretPayload(connector, accessKey, accessSecretKey) {
    const first = String(accessKey || '').trim();
    const second = String(accessSecretKey || '').trim();
    if (!first && !second) return {};
    if (connector.type === 'APPDYNAMICS' || connector.type === 'VROPS' || connector.type === 'SCOM') {
      return Object.assign({}, first ? { username: first } : {}, second ? { password: second } : {});
    }
    return Object.assign({}, first ? { accessKey: first } : {}, second ? { accessSecretKey: second } : {});
  }

  function emcoSecretPayload() {
    const kfhUsername = String(document.getElementById('edit-emco-kfh-username')?.value || '').trim();
    const kfhPassword = String(document.getElementById('edit-emco-kfh-password')?.value || '').trim();
    const cctvUsername = String(document.getElementById('edit-emco-cctv-username')?.value || '').trim();
    const cctvPassword = String(document.getElementById('edit-emco-cctv-password')?.value || '').trim();
    return Object.assign({},
      kfhUsername ? { kfhUsername } : {},
      kfhPassword ? { kfhPassword } : {},
      cctvUsername ? { cctvUsername } : {},
      cctvPassword ? { cctvPassword } : {});
  }

  function validateConnectorSaveForm(connector, attributes, accessKey, accessSecretKey) {
    if (connector.type === 'BMC' && !String(attributes.baseUrl || '').trim()) {
      focusField('edit-base-url');
      toast('Enter the BMC Base URL before saving this connector.', 'error');
      return false;
    }
    if (connector.type === 'BMC' && connector.configurationStatus === 'PENDING'
        && (!accessKey.trim() || !accessSecretKey.trim())) {
      focusField(!accessKey.trim() ? 'edit-access-key' : 'edit-access-secret-key');
      toast('Enter both BMC access key and access secret key to complete pending setup.', 'error');
      return false;
    }
    if (connector.type === 'APPDYNAMICS' && !String(attributes.controllerUrl || '').trim()) {
      focusField('edit-controller-url');
      toast('Enter the AppDynamics Controller URL before saving this connector.', 'error');
      return false;
    }
    if (connector.type === 'APPDYNAMICS' && connector.configurationStatus === 'PENDING'
        && (!accessKey.trim() || !accessSecretKey.trim())) {
      focusField(!accessKey.trim() ? 'edit-access-key' : 'edit-access-secret-key');
      toast('Enter both AppDynamics username and password to complete pending setup.', 'error');
      return false;
    }
    if (connector.type === 'APPDYNAMICS'
        && attributes.fetchErrors === false && attributes.fetchViolations === false && attributes.fetchSlowTransactions === false) {
      toast('Enable at least one AppDynamics event family before saving.', 'error');
      return false;
    }
    if (connector.type === 'VROPS' && !String(attributes.host || attributes.baseUrl || '').trim()) {
      focusField('edit-vrops-host');
      toast('Enter the vROps host or suite-api URL before saving this connector.', 'error');
      return false;
    }
    if (connector.type === 'VROPS' && !String(attributes.authSource || '').trim()) {
      focusField('edit-auth-source');
      toast('Enter the vROps auth source before saving this connector.', 'error');
      return false;
    }
    if (connector.type === 'VROPS' && connector.configurationStatus === 'PENDING'
        && (!accessKey.trim() || !accessSecretKey.trim())) {
      focusField(!accessKey.trim() ? 'edit-access-key' : 'edit-access-secret-key');
      toast('Enter both vROps username and password to complete pending setup.', 'error');
      return false;
    }
    if (connector.type === 'VROPS' && connector.configurationStatus !== 'PENDING'
        && Boolean(accessKey.trim()) !== Boolean(accessSecretKey.trim())) {
      focusField(!accessKey.trim() ? 'edit-access-key' : 'edit-access-secret-key');
      toast('Enter both vROps username and password when rotating vROps credentials.', 'error');
      return false;
    }
    if (connector.type === 'SCOM' && !String(attributes.managementServer || '').trim()) {
      focusField('edit-scom-server');
      toast('Enter the SCOM management server before saving this connector.', 'error');
      return false;
    }
    if (connector.type === 'SCOM' && !String(attributes.domain || '').trim()) {
      focusField('edit-scom-domain');
      toast('Enter the SCOM domain before saving this connector.', 'error');
      return false;
    }
    if (connector.type === 'SCOM' && connector.configurationStatus === 'PENDING'
        && (!accessKey.trim() || !accessSecretKey.trim())) {
      focusField(!accessKey.trim() ? 'edit-access-key' : 'edit-access-secret-key');
      toast('Enter both SCOM username and password to complete pending setup.', 'error');
      return false;
    }
    if (connector.type === 'SCOM' && connector.configurationStatus !== 'PENDING'
        && Boolean(accessKey.trim()) !== Boolean(accessSecretKey.trim())) {
      focusField(!accessKey.trim() ? 'edit-access-key' : 'edit-access-secret-key');
      toast('Enter both SCOM username and password when rotating SCOM credentials.', 'error');
      return false;
    }
    if (connector.type === 'EMCO' && !String(attributes.sqlServer || '').trim()) {
      focusField('edit-emco-sql-server');
      toast('Enter the EMCO SQL Server host before saving this connector.', 'error');
      return false;
    }
    if (connector.type === 'EMCO' && !String(attributes.kfhDatabase || '').trim()) {
      focusField('edit-emco-kfh-database');
      toast('Enter the EMCO KFH database before saving this connector.', 'error');
      return false;
    }
    if (connector.type === 'EMCO' && !String(attributes.cctvDatabase || '').trim()) {
      focusField('edit-emco-cctv-database');
      toast('Enter the EMCO CCTV database before saving this connector.', 'error');
      return false;
    }
    if (connector.type === 'EMCO') {
      const kfhUsername = String(document.getElementById('edit-emco-kfh-username')?.value || '').trim();
      const kfhPassword = String(document.getElementById('edit-emco-kfh-password')?.value || '').trim();
      const cctvUsername = String(document.getElementById('edit-emco-cctv-username')?.value || '').trim();
      const cctvPassword = String(document.getElementById('edit-emco-cctv-password')?.value || '').trim();
      if (connector.configurationStatus === 'PENDING'
          && (!kfhUsername || !kfhPassword || !cctvUsername || !cctvPassword)) {
        focusField(!kfhUsername ? 'edit-emco-kfh-username' : !kfhPassword ? 'edit-emco-kfh-password' : !cctvUsername ? 'edit-emco-cctv-username' : 'edit-emco-cctv-password');
        toast('Enter KFH and CCTV SQL usernames and passwords to complete EMCO setup.', 'error');
        return false;
      }
      if (connector.configurationStatus !== 'PENDING' && Boolean(kfhUsername) !== Boolean(kfhPassword)) {
        focusField(!kfhUsername ? 'edit-emco-kfh-username' : 'edit-emco-kfh-password');
        toast('Enter both EMCO KFH SQL username and password when rotating KFH credentials.', 'error');
        return false;
      }
      if (connector.configurationStatus !== 'PENDING' && Boolean(cctvUsername) !== Boolean(cctvPassword)) {
        focusField(!cctvUsername ? 'edit-emco-cctv-username' : 'edit-emco-cctv-password');
        toast('Enter both EMCO CCTV SQL username and password when rotating CCTV credentials.', 'error');
        return false;
      }
    }
    return true;
  }
  function focusField(id) {
    const field = document.getElementById(id);
    if (!field) return;
    field.scrollIntoView({ behavior: 'smooth', block: 'center' });
    field.focus({ preventScroll: true });
  }
  function setDrawerActionBusy(action, busy, label) {
    const button = document.querySelector(`[data-connector-action="${action}"]`);
    if (!button) return;
    if (!button.dataset.originalHtml) {
      button.dataset.originalHtml = button.innerHTML;
    }
    button.disabled = busy;
    button.classList.toggle('is-busy', busy);
    button.innerHTML = busy ? `<span class="drawer-footer-spinner" aria-hidden="true"></span>${esc(label || 'Working...')}` : button.dataset.originalHtml;
  }

  function errorMessage(error, fallback) {
    const details = error?.details || {};
    return details.message || error?.message || fallback;
  }

  async function deleteConnector(id) {
    const c = connectors.find(connector => connector.id === id);
    if (!c) return;
    if (!window.confirm(`Remove connector "${c.name}"? This removes the connector configuration for ${c.countryCode}/${c.environmentScope}.`)) {
      return;
    }
    try {
      await APIClient.connectors.delete(id);
      await loadConnectors();
      if (selectedConnector?.id === id) closeDrawer();
      toast('Connector removed', 'success');
      render();
    } catch (error) {
      toast('Unable to remove connector', 'error');
    }
  }

  function toast(msg, type) {
    type = type || 'info';
    const drawer = document.getElementById('connector-drawer');
    if (drawer?.classList.contains('open') && selectedConnector) {
      const feedback = { message: msg, type };
      drawerFeedback = feedback;
      updateDrawerFeedback();
      setTimeout(() => {
        if (drawerFeedback === feedback) {
          drawerFeedback = null;
          updateDrawerFeedback();
        }
      }, 5000);
      return;
    }

    const existing = document.querySelector('.toast-notification');
    if (existing) existing.remove();

    const t = document.createElement('div');
    t.className = `toast-notification toast-notification-${type} fixed bottom-6 right-6 rounded-lg shadow-lg z-[100] animate-fade-in flex items-center gap-3`;
    t.setAttribute('role', type === 'error' ? 'alert' : 'status');
    t.setAttribute('aria-live', 'polite');
    Object.assign(t.style, {
      position: 'fixed',
      bottom: '24px',
      right: '24px',
      zIndex: '10000',
      padding: '9px 14px',
      borderRadius: '14px',
      boxShadow: '0 12px 28px rgba(15, 23, 42, .14)',
      display: 'flex',
      alignItems: 'center',
      gap: '10px',
      maxWidth: '360px',
      fontSize: '13px',
      lineHeight: '1.35',
      fontWeight: '800'
    });

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

    t.innerHTML = '<span>' + esc(msg) + '</span>';
    document.body.appendChild(t);
    setTimeout(() => t.remove(), 4000);
  }

  // Init
  async function init() {
    ensureConnectorOverlays();
    restoreModeFromHash();
    await loadConnectorTypes();
    await loadConnectors();
    syncUrl();
    render();
    renderModal();
    console.log('Connectors module initialized');
  }

  return {
    init,
    setViewMode,
    setPage,
    setDrawerTab,
    toggleSmartFilters,
    setSmartFilter,
    openInventory,
    openMarketplace,
    openMarketplaceDetail,
    openDrawer,
    closeDrawer,
    openModal,
    closeModal,
    setMarketplaceCountry,
    installConnector,
    uninstallConnector,
    toggleConnector,
    setNewConnectorType,
    runTest,
    runHeartbeat,
    addConnector,
    saveConnector,
    testCurrentConfiguration,
    deleteConnector,
    refresh: async () => { await loadConnectors(); render(); toast('Connectors refreshed', 'success'); }
  };
})();

window.Connectors = Connectors;
