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

  // Constants
  const CONNECTOR_TYPES = [
    { value: 'SCOM', label: 'SCOM', icon: 'server' },
    { value: 'vROps', label: 'vROps', icon: 'cloud' },
    { value: 'BMC', label: 'BMC Helix', icon: 'server' },
    { value: 'SolarWinds', label: 'SolarWinds', icon: 'activity' },
    { value: 'Elastic', label: 'Elastic', icon: 'database' },
    { value: 'Azure', label: 'Azure Monitor', icon: 'cloud' },
    { value: 'Syslog', label: 'Syslog', icon: 'file' },
    { value: 'SMTP', label: 'SMTP/Email', icon: 'mail' },
    { value: 'SharePoint', label: 'SharePoint', icon: 'upload' },
    { value: 'Teams', label: 'Teams', icon: 'message' }
  ];

  const STATUSES = ['Healthy', 'Degraded', 'Down', 'Disabled'];
  const SCOPES = ['Prod', 'DR', 'Both'];
  const AUTH_MODES = ['ApiKey', 'Basic', 'OAuth', 'Certificate'];
  const OWNER_TEAMS = ['Platform Ops', 'App Support', 'Network Ops', 'DevOps'];

  // Utilities
  const genId = () => (window.crypto && window.crypto.randomUUID ? window.crypto.randomUUID() : `tmp-${Date.now()}`);
  const esc = s => s ? s.replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])) : '';

  function pageContent(response) {
    return response && Array.isArray(response.content) ? response.content : Array.isArray(response) ? response : [];
  }

  function normalizeConnector(row) {
    const type = row.pluginType || row.type || 'UNKNOWN';
    const enabled = row.enabled !== false;
    const status = row.health || row.status || (enabled ? 'Healthy' : 'Disabled');
    return {
      id: String(row.id || row.connectorId || ''),
      name: row.name || type,
      type,
      enabled,
      environmentScope: row.environment || row.environmentScope || 'Prod',
      status: String(status).charAt(0).toUpperCase() + String(status).slice(1).toLowerCase(),
      lastSync: row.lastRunAt ? Date.parse(row.lastRunAt) : row.lastTestAt ? Date.parse(row.lastTestAt) : null,
      nextSync: row.nextRunAt ? Date.parse(row.nextRunAt) : null,
      lagMinutes: Number(row.lagMinutes || 0),
      events24h: Number(row.events24h || row.eventCount24h || 0),
      errors24h: Number(row.errors24h || row.errorCount24h || 0),
      authMode: row.authMode || row.authenticationMode || 'Configured',
      endpoints: Array.isArray(row.endpoints) ? row.endpoints : [],
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
      degraded: connectors.filter(c => c.status === 'Degraded').length,
      down: connectors.filter(c => c.status === 'Down').length,
      disabled: connectors.filter(c => c.status === 'Disabled').length,
      totalEvents24h: connectors.reduce((sum, c) => sum + c.events24h, 0)
    };
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

  // Rendering
  function render() {
    const container = document.getElementById('connectors-content') || document.getElementById('page-root') || document.getElementById('content-area');
    if (!container) return;

    const kpis = getKpis();
    const filtered = getFilteredConnectors();

    container.innerHTML = `
      <!-- KPI Strip -->
      <div class="connectors-kpi-grid animate-fade-in">
        ${renderKpiCard('Total Connectors', kpis.total, 'plug', 'default')}
        ${renderKpiCard('Healthy', kpis.healthy, 'check', 'healthy')}
        ${renderKpiCard('Degraded', kpis.degraded, 'alert', 'degraded')}
        ${renderKpiCard('Down', kpis.down, 'x', 'down', kpis.down > 0)}
        ${renderKpiCard('Disabled', kpis.disabled, 'x', 'disabled')}
        ${renderKpiCard('Events 24h', kpis.totalEvents24h.toLocaleString(), 'activity', 'events')}
      </div>

      <!-- Filters -->
      <div class="kfh-card connectors-filters animate-fade-in">
        <div class="connectors-filters-inner">
          <div class="connector-search">
            <span class="connector-search-icon">${icon('search', 16)}</span>
            <input id="connector-search" type="text" placeholder="Search connectors..." class="connector-search-input" value="${esc(searchQuery)}">
          </div>
          <div class="flex gap-2 flex-wrap">
            <select id="filter-status" class="connector-filter-select" style="width: 140px;">
              <option value="">All Statuses</option>
              ${STATUSES.map(s => `<option value="${s}" ${filters.status === s ? 'selected' : ''}>${s}</option>`).join('')}
            </select>
            <select id="filter-type" class="connector-filter-select" style="width: 140px;">
              <option value="">All Types</option>
              ${CONNECTOR_TYPES.map(t => `<option value="${t.value}" ${filters.type === t.value ? 'selected' : ''}>${t.label}</option>`).join('')}
            </select>
            <select id="filter-scope" class="connector-filter-select" style="width: 120px;">
              <option value="">All Scopes</option>
              ${SCOPES.map(s => `<option value="${s}" ${filters.scope === s ? 'selected' : ''}>${s}</option>`).join('')}
            </select>
            <div class="view-toggle">
              <button onclick="Connectors.setViewMode('grid')" class="view-toggle-btn ${viewMode === 'grid' ? 'active' : ''}">${icon('grid', 16)}</button>
              <button onclick="Connectors.setViewMode('table')" class="view-toggle-btn ${viewMode === 'table' ? 'active' : ''}">${icon('list', 16)}</button>
            </div>
          </div>
        </div>
      </div>

      <!-- Connectors List -->
      ${viewMode === 'grid' ? renderConnectorsGrid(filtered) : renderConnectorsTable(filtered)}

      ${filtered.length === 0 ? renderEmptyState() : ''}
    `;

    bindEvents();
  }

  function renderKpiCard(label, value, iconName, variant, pulse = false) {
    return `
      <div class="kfh-card connector-kpi-card ${pulse ? 'pulse-glow' : ''}">
        <div class="connector-kpi-header">
          <span class="connector-kpi-label">${label}</span>
          <div class="connector-kpi-icon connector-kpi-icon-${variant}">${icon(iconName, 18)}</div>
        </div>
        <span class="connector-kpi-value">${value}</span>
      </div>
    `;
  }

  function renderConnectorsGrid(connectors) {
    return `
      <div class="connectors-grid">
        ${connectors.map(c => renderConnectorCard(c)).join('')}
      </div>
    `;
  }

  function renderConnectorCard(c) {
    const typeInfo = CONNECTOR_TYPES.find(t => t.value === c.type);
    const iconClass = c.status === 'Healthy' ? 'healthy' : c.status === 'Degraded' ? 'degraded' : c.status === 'Down' ? 'down' : 'disabled';
    const lagClass = c.lagMinutes < 5 ? 'color: var(--kfh-primary)' : c.lagMinutes < 15 ? 'color: #D97706' : 'color: #DC2626';

    return `
      <div class="kfh-card connector-card animate-fade-in">
        <div class="connector-card-header">
          <div class="connector-card-info">
            <div class="connector-type-icon connector-type-icon-${iconClass}">${icon(typeInfo?.icon || 'server', 24)}</div>
            <div>
              <div class="connector-card-name">${esc(c.name)}</div>
              <div class="connector-card-type">${typeInfo?.label || c.type}</div>
            </div>
          </div>
          <button onclick="Connectors.toggleConnector('${c.id}', ${!c.enabled})" class="connector-toggle ${c.enabled ? 'enabled' : ''}" title="${c.enabled ? 'Disable' : 'Enable'} connector">
            <span class="connector-toggle-knob"></span>
          </button>
        </div>

        <div class="connector-details">
          <div class="connector-detail-row">
            <span class="connector-detail-label">Status</span>
            <span class="status-badge status-badge-${iconClass}">${c.status}</span>
          </div>
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
          <button onclick="Connectors.runTest('${c.id}')" class="connector-action-btn connector-action-test" ${!c.enabled ? 'disabled' : ''}>
            ${icon('play', 14)} Test
          </button>
        </div>
      </div>
    `;
  }

  function renderConnectorsTable(connectors) {
    return `
      <div class="kfh-card connectors-table-card animate-fade-in">
        <table class="connectors-table">
          <thead>
            <tr>
              <th>Name</th>
              <th class="text-center">Type</th>
              <th class="text-center">Status</th>
              <th class="text-center">Enabled</th>
              <th class="text-center">Scope</th>
              <th class="text-center">Last Sync</th>
              <th class="text-center">Lag</th>
              <th class="text-center">Events 24h</th>
              <th class="text-center">Errors 24h</th>
              <th class="text-center">Actions</th>
            </tr>
          </thead>
          <tbody>
            ${connectors.map(c => {
              const iconClass = c.status === 'Healthy' ? 'healthy' : c.status === 'Degraded' ? 'degraded' : c.status === 'Down' ? 'down' : 'disabled';
              const lagClass = c.lagMinutes < 5 ? 'table-lag-good' : c.lagMinutes < 15 ? 'table-lag-warn' : 'table-lag-bad';
              return `
                <tr>
                  <td><span class="table-connector-name">${esc(c.name)}</span></td>
                  <td class="text-center"><span class="status-badge status-badge-disabled">${c.type}</span></td>
                  <td class="text-center"><span class="status-badge status-badge-${iconClass}">${c.status}</span></td>
                  <td class="text-center">
                    <button onclick="Connectors.toggleConnector('${c.id}', ${!c.enabled})" class="connector-toggle ${c.enabled ? 'enabled' : ''}" style="margin: 0 auto;">
                      <span class="connector-toggle-knob"></span>
                    </button>
                  </td>
                  <td class="text-center">
                    ${c.environmentScope === 'Both' 
                      ? '<span class="scope-badge scope-badge-prod">Prod</span> <span class="scope-badge scope-badge-dr">DR</span>'
                      : `<span class="scope-badge scope-badge-${c.environmentScope.toLowerCase()}">${c.environmentScope}</span>`}
                  </td>
                  <td class="text-center text-[#666666]">${c.lastSync ? formatTime(c.lastSync) : 'N/A'}</td>
                  <td class="text-center"><span class="${lagClass}">${c.lagMinutes}m</span></td>
                  <td class="text-center"><span class="table-events">${c.events24h.toLocaleString()}</span></td>
                  <td class="text-center"><span class="table-errors">${c.errors24h}</span></td>
                  <td>
                    <div class="table-actions">
                      <button onclick="Connectors.openDrawer('${c.id}')" class="table-action-btn" title="Configure">${icon('settings', 16)}</button>
                      <button onclick="Connectors.runTest('${c.id}')" class="table-action-btn" title="Test" ${!c.enabled ? 'disabled' : ''}>${icon('play', 16)}</button>
                    </div>
                  </td>
                </tr>
              `;
            }).join('')}
          </tbody>
        </table>
      </div>
    `;
  }

  function renderEmptyState() {
    return `
      <div class="kfh-card empty-state">
        <div class="empty-state-icon">${icon('plug', 64)}</div>
        <h3 class="empty-state-title">No connectors found</h3>
        <p class="empty-state-text">${searchQuery || filters.status || filters.type || filters.scope ? 'Try adjusting your filters' : 'Get started by adding your first connector'}</p>
        ${!searchQuery && !filters.status && !filters.type && !filters.scope ? `
        <button onclick="Connectors.openModal()" class="px-4 py-2 bg-[#128754] hover:bg-[#0E6B42] text-white rounded-lg text-sm font-semibold transition-colors">
          ${icon('plus', 16)} Add Connector
        </button>
        ` : ''}
      </div>
    `;
  }

  // Drawer
  function openDrawer(connectorId) {
    selectedConnector = connectors.find(c => c.id === connectorId);
    drawerTab = 'overview';
    renderDrawer();
    document.getElementById('connector-drawer-overlay').classList.add('open');
    document.getElementById('connector-drawer').classList.add('open');
  }

  function closeDrawer() {
    document.getElementById('connector-drawer-overlay').classList.remove('open');
    document.getElementById('connector-drawer').classList.remove('open');
    selectedConnector = null;
  }

  function renderDrawer() {
    if (!selectedConnector) return;
    const c = selectedConnector;
    const drawerContent = document.getElementById('drawer-content');
    const typeInfo = CONNECTOR_TYPES.find(t => t.value === c.type);
    const logs = Array.isArray(c.logs) ? c.logs : [];
    const currentTestRun = testRuns.find(tr => tr.connectorId === c.id && tr.result === 'running');
    const latestTestRun = testRuns.find(tr => tr.connectorId === c.id);

    drawerContent.innerHTML = `
      <div class="drawer-header">
        <div class="drawer-header-info">
          <h2>${esc(c.name)}</h2>
          <p>${typeInfo?.label || c.type}</p>
        </div>
        <button onclick="Connectors.closeDrawer()" class="drawer-close-btn">${icon('close', 20)}</button>
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
      </div>

      ${drawerTab === 'configuration' ? `
      <div class="drawer-footer">
        <button onclick="Connectors.closeDrawer()" class="px-4 py-2 text-sm font-medium text-gray-600 hover:bg-gray-100 rounded-lg transition-colors">Cancel</button>
        <button onclick="Connectors.saveConnector()" class="px-4 py-2 bg-[#128754] hover:bg-[#0E6B42] text-white rounded-lg text-sm font-semibold transition-colors flex items-center gap-2">${icon('save', 16)} Save Changes</button>
      </div>
      ` : ''}
    `;
  }

  function renderOverviewTab(c) {
    return `
      <div class="drawer-kpi-grid">
        <div class="kfh-card drawer-kpi-card"><div class="drawer-kpi-label">Status</div><div class="drawer-kpi-value" style="color: ${c.status === 'Healthy' ? 'var(--kfh-primary)' : c.status === 'Degraded' ? '#D97706' : c.status === 'Down' ? '#DC2626' : '#9CA3AF'}">${c.status}</div></div>
        <div class="kfh-card drawer-kpi-card"><div class="drawer-kpi-label">Events 24h</div><div class="drawer-kpi-value" style="color: #2563EB">${c.events24h.toLocaleString()}</div></div>
        <div class="kfh-card drawer-kpi-card"><div class="drawer-kpi-label">Errors 24h</div><div class="drawer-kpi-value" style="color: ${c.errors24h > 50 ? '#DC2626' : 'var(--text-primary)'}">${c.errors24h}</div></div>
        <div class="kfh-card drawer-kpi-card"><div class="drawer-kpi-label">Lag</div><div class="drawer-kpi-value" style="color: ${c.lagMinutes < 5 ? 'var(--kfh-primary)' : c.lagMinutes < 15 ? '#D97706' : '#DC2626'}">${c.lagMinutes}m</div></div>
      </div>

      <div class="drawer-section">
        <div class="drawer-section-title">Endpoints</div>
        ${c.endpoints.map(ep => `
          <div class="endpoint-item">
            <div>
              <span class="scope-badge scope-badge-${ep.env.toLowerCase()}" style="margin-bottom: 8px">${ep.env}</span>
              <div class="endpoint-url">${ep.url}</div>
              ${ep.port ? `<div class="endpoint-port">Port: ${ep.port}</div>` : ''}
            </div>
            <span class="status-badge status-badge-healthy">Active</span>
          </div>
        `).join('')}
      </div>

      <div class="drawer-section">
        <div class="drawer-section-title">Details</div>
        <div class="details-list">
          <div class="details-row"><span class="details-row-label">Auth Mode</span><span class="details-row-value">${c.authMode}</span></div>
          <div class="details-row"><span class="details-row-label">Schedule</span><span class="details-row-value">Every ${c.schedules.intervalMin}m</span></div>
          <div class="details-row"><span class="details-row-label">Owner Team</span><span class="details-row-value">${c.ownerTeam}</span></div>
          <div class="details-row"><span class="details-row-label">Created</span><span class="details-row-value">${new Date(c.createdAt).toLocaleDateString()}</span></div>
        </div>
      </div>
    `;
  }

  function renderConfigurationTab(c) {
    return `
      <div class="drawer-section">
        <div class="drawer-section-title">Basic Settings</div>
        <div class="drawer-form-group">
          <label class="drawer-form-label">Connector Name</label>
          <input type="text" id="edit-name" class="drawer-form-input" value="${esc(c.name)}">
        </div>
        <div class="drawer-form-row">
          <div class="drawer-form-group">
            <label class="drawer-form-label">Environment Scope</label>
            <select id="edit-scope" class="drawer-form-select">
              ${SCOPES.map(s => `<option value="${s}" ${c.environmentScope === s ? 'selected' : ''}>${s}</option>`).join('')}
            </select>
          </div>
          <div class="drawer-form-group">
            <label class="drawer-form-label">Owner Team</label>
            <select id="edit-team" class="drawer-form-select">
              ${OWNER_TEAMS.map(t => `<option value="${t}" ${c.ownerTeam === t ? 'selected' : ''}>${t}</option>`).join('')}
            </select>
          </div>
        </div>
      </div>

      <div class="drawer-section">
        <div class="drawer-section-title">Authentication</div>
        <div class="drawer-form-group">
          <label class="drawer-form-label">Auth Mode</label>
          <select id="edit-auth" class="drawer-form-select">
            ${AUTH_MODES.map(a => `<option value="${a}" ${c.authMode === a ? 'selected' : ''}>${a}</option>`).join('')}
          </select>
        </div>
        <div class="drawer-form-row">
          <div class="drawer-form-group">
            <label class="drawer-form-label">API Key / Username</label>
            <input type="text" class="drawer-form-input" value="••••••••••••••••" disabled>
          </div>
          <div class="drawer-form-group">
            <label class="drawer-form-label">Secret / Password</label>
            <input type="password" class="drawer-form-input" value="••••••••••••••••" disabled>
          </div>
        </div>
        <p class="text-xs text-[#666666] mt-2">Credentials are encrypted and stored securely</p>
      </div>

      <div class="drawer-section">
        <div class="drawer-section-title">Schedule</div>
        <div class="drawer-form-group">
          <label class="drawer-form-label">Sync Interval (minutes)</label>
          <select id="edit-interval" class="drawer-form-select">
            ${[5, 10, 15, 30, 60].map(m => `<option value="${m}" ${c.schedules.intervalMin === m ? 'selected' : ''}>${m} minutes</option>`).join('')}
          </select>
        </div>
      </div>

      <div class="drawer-section">
        <div class="drawer-section-title">Notes</div>
        <textarea id="edit-notes" class="drawer-form-textarea" rows="4" placeholder="Add notes about this connector...">${esc(c.notes || '')}</textarea>
      </div>
    `;
  }

  function renderMappingTab(c) {
    return `
      <div class="drawer-section">
        <div class="drawer-section-title">Field Mapping</div>
        <div class="drawer-form-row">
          <div class="drawer-form-group">
            <label class="drawer-form-label">Application Field</label>
            <input type="text" class="drawer-form-input" value="${esc(c.mappings.appField)}">
          </div>
          <div class="drawer-form-group">
            <label class="drawer-form-label">Asset Field</label>
            <input type="text" class="drawer-form-input" value="${esc(c.mappings.assetField)}">
          </div>
        </div>
      </div>

      <div class="drawer-section">
        <div class="drawer-section-title">Severity Mapping</div>
        <table class="w-full">
          <thead>
            <tr class="border-b border-gray-200">
              <th class="text-left py-2 text-xs font-semibold text-[#666666]">Tool Severity</th>
              <th class="text-left py-2 text-xs font-semibold text-[#666666]">Platform Severity</th>
            </tr>
          </thead>
          <tbody>
            ${Object.entries(c.mappings.severityMap).map(([key, value]) => `
              <tr class="border-b border-gray-100">
                <td class="py-3 text-sm text-[#1D1D1D]">${key}</td>
                <td class="py-3">
                  <select class="drawer-form-select" style="width: 100%">
                    ${['critical', 'high', 'medium', 'low', 'info'].map(s => `<option value="${s}" ${value === s ? 'selected' : ''}>${s.charAt(0).toUpperCase() + s.slice(1)}</option>`).join('')}
                  </select>
                </td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>
    `;
  }

  function renderHealthTab(c, currentTestRun, latestTestRun) {
    return `
      <div class="drawer-section">
        <div class="drawer-section-title">
          <span>Events Trend (24h)</span>
          <button onclick="Connectors.runTest('${c.id}')" class="px-3 py-1.5 bg-[#128754] hover:bg-[#0E6B42] text-white rounded-lg text-xs font-semibold transition-colors flex items-center gap-1" ${!c.enabled || currentTestRun ? 'disabled' : ''}>
            ${icon('play', 12)} ${currentTestRun ? 'Running...' : 'Run Test'}
          </button>
        </div>
        <div class="chart-container" style="background: #F9FAFB; border-radius: 8px; padding: 16px; display: flex; align-items: center; justify-content: center; color: #9CA3AF;">
          Chart visualization (Recharts)
        </div>
      </div>

      ${currentTestRun ? `
      <div class="drawer-section">
        <div class="drawer-section-title">Test Execution</div>
        <div class="test-steps">
          ${currentTestRun.steps.map((step, idx) => `
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
      <div class="drawer-section">
        <div class="drawer-section-title">Latest Test Result</div>
        <div class="endpoint-item">
          <div class="text-sm text-[#666666]">Completed ${formatTime(latestTestRun.startedAt)}</div>
          <span class="status-badge status-badge-${latestTestRun.result === 'Pass' ? 'healthy' : 'down'}">${latestTestRun.result}</span>
        </div>
      </div>
      ` : ''}

      <div class="drawer-section">
        <div class="drawer-section-title">Health Checks</div>
        ${[
          { name: 'DNS Resolution', status: 'pass' },
          { name: 'Auth Handshake', status: c.status === 'Down' ? 'fail' : 'pass' },
          { name: 'API Connectivity', status: c.status === 'Down' ? 'fail' : 'pass' },
          { name: 'Data Parsing', status: c.status === 'Degraded' ? 'fail' : 'pass' }
        ].map(check => `
          <div class="health-check-item">
            <span class="health-check-name">${check.name}</span>
            <span class="status-badge status-badge-${check.status === 'pass' ? 'healthy' : 'down'}">${check.status === 'pass' ? 'Pass' : 'Fail'}</span>
          </div>
        `).join('')}
      </div>
    `;
  }

  function renderLogsTab(logs) {
    return `
      <div class="drawer-section">
        <div class="drawer-section-title">
          <span>Recent Logs</span>
          <select class="connector-filter-select" style="width: 100px; font-size: 12px;">
            <option value="">All Levels</option>
            <option value="INFO">INFO</option>
            <option value="WARN">WARN</option>
            <option value="ERROR">ERROR</option>
            <option value="DEBUG">DEBUG</option>
          </select>
        </div>
        <div class="logs-container">
          ${logs.length === 0 ? '<div class="p-4 text-sm text-[#666666]">No connector logs returned by the API.</div>' : logs.map(log => `
            <div class="log-entry">
              <span class="log-time">${new Date(log.timestamp).toLocaleTimeString()}</span>
              <span class="log-level log-level-${log.level.toLowerCase()}">${log.level}</span>
              <span class="log-message">${esc(log.message)}</span>
            </div>
          `).join('')}
        </div>
      </div>
    `;
  }

  // Modal
  function openModal() {
    isModalOpen = true;
    renderModal();
    document.getElementById('connector-modal-overlay').classList.add('open');
  }

  function closeModal() {
    isModalOpen = false;
    document.getElementById('connector-modal-overlay').classList.remove('open');
  }

  function renderModal() {
    const modal = document.getElementById('connector-modal-overlay');
    if (!modal) return;

    modal.innerHTML = `
      <div class="modal">
        <div class="modal-header">
          <h3 class="modal-title">Add New Connector</h3>
          <button onclick="Connectors.closeModal()" class="modal-close">${icon('close', 20)}</button>
        </div>
        <div class="modal-body">
          <div class="drawer-form-row" style="margin-bottom: 16px;">
            <div class="drawer-form-group">
              <label class="drawer-form-label">Connector Name *</label>
              <input type="text" id="new-name" class="drawer-form-input" placeholder="e.g., SCOM Prod">
            </div>
            <div class="drawer-form-group">
              <label class="drawer-form-label">Type *</label>
              <select id="new-type" class="drawer-form-select">
                ${CONNECTOR_TYPES.map(t => `<option value="${t.value}">${t.label}</option>`).join('')}
              </select>
            </div>
          </div>
          <div class="drawer-form-row" style="margin-bottom: 16px;">
            <div class="drawer-form-group">
              <label class="drawer-form-label">Environment Scope *</label>
              <select id="new-scope" class="drawer-form-select">
                ${SCOPES.map(s => `<option value="${s}">${s}</option>`).join('')}
              </select>
            </div>
            <div class="drawer-form-group">
              <label class="drawer-form-label">Owner Team</label>
              <select id="new-team" class="drawer-form-select">
                ${OWNER_TEAMS.map(t => `<option value="${t}">${t}</option>`).join('')}
              </select>
            </div>
          </div>
          <div class="drawer-section" style="padding: 16px; margin-bottom: 16px;">
            <div class="drawer-section-title" style="margin-bottom: 12px;">Endpoint Configuration</div>
            <div class="drawer-form-group">
              <label class="drawer-form-label">URL *</label>
              <input type="text" id="new-url" class="drawer-form-input" placeholder="https://scom.kfh.com">
            </div>
            <div class="drawer-form-group">
              <label class="drawer-form-label">Port</label>
              <input type="number" id="new-port" class="drawer-form-input" value="443">
            </div>
          </div>
          <div class="drawer-form-row" style="margin-bottom: 16px;">
            <div class="drawer-form-group">
              <label class="drawer-form-label">Auth Mode</label>
              <select id="new-auth" class="drawer-form-select">
                ${AUTH_MODES.map(a => `<option value="${a}">${a}</option>`).join('')}
              </select>
            </div>
            <div class="drawer-form-group">
              <label class="drawer-form-label">Sync Interval</label>
              <select id="new-interval" class="drawer-form-select">
                ${[5, 10, 15, 30, 60].map(m => `<option value="${m}" ${m === 15 ? 'selected' : ''}>${m} minutes</option>`).join('')}
              </select>
            </div>
          </div>
          <div class="drawer-form-group">
            <label class="drawer-form-label">Notes</label>
            <textarea id="new-notes" class="drawer-form-textarea" rows="3" placeholder="Add any notes about this connector..."></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button onclick="Connectors.closeModal()" class="px-4 py-2 text-sm font-medium text-gray-600 hover:bg-gray-100 rounded-lg transition-colors">Cancel</button>
          <button onclick="Connectors.addConnector()" class="px-4 py-2 bg-[#128754] hover:bg-[#0E6B42] text-white rounded-lg text-sm font-semibold transition-colors flex items-center gap-2">${icon('plus', 16)} Add Connector</button>
        </div>
      </div>
    `;
  }

  // Events
  function bindEvents() {
    document.getElementById('connector-search')?.addEventListener('input', e => {
      searchQuery = e.target.value;
      render();
    });

    document.getElementById('filter-status')?.addEventListener('change', e => {
      filters.status = e.target.value;
      render();
    });

    document.getElementById('filter-type')?.addEventListener('change', e => {
      filters.type = e.target.value;
      render();
    });

    document.getElementById('filter-scope')?.addEventListener('change', e => {
      filters.scope = e.target.value;
      render();
    });

    document.getElementById('connector-drawer-overlay')?.addEventListener('click', closeDrawer);
  }

  // Actions
  function setViewMode(mode) {
    viewMode = mode;
    render();
  }

  function setDrawerTab(tab) {
    drawerTab = tab;
    renderDrawer();
  }

  async function toggleConnector(id, enabled) {
    const c = connectors.find(c => c.id === id);
    if (c) {
      try {
        await APIClient.connectors.toggle(id, enabled);
        await loadConnectors();
        toast(`Connector ${enabled ? 'enabled' : 'disabled'}`, 'info');
        render();
      } catch (error) {
        toast('Unable to update connector status', 'error');
      }
    }
  }

  async function runTest(connectorId) {
    const c = connectors.find(c => c.id === connectorId);
    if (!c || !c.enabled) return;
    const testId = genId();
    const testRun = { id: testId, connectorId, startedAt: Date.now(), result: 'running', steps: [] };
    testRuns.unshift(testRun);
    toast('Test requested...', 'info');
    if (selectedConnector?.id === connectorId) {
      drawerTab = 'health';
      renderDrawer();
    }
    try {
      const result = await APIClient.connectors.test(connectorId);
      testRun.result = result?.pass === false || result?.status === 'FAIL' ? 'Fail' : 'Pass';
      testRun.steps = Array.isArray(result?.steps) ? result.steps : [];
      toast(`Test ${testRun.result.toLowerCase()}ed`, testRun.result === 'Pass' ? 'success' : 'error');
      if (selectedConnector?.id === connectorId) renderDrawer();
    } catch (error) {
      testRun.result = 'Fail';
      toast('Connector test failed', 'error');
      if (selectedConnector?.id === connectorId) renderDrawer();
    }
  }

  async function addConnector() {
    const name = document.getElementById('new-name')?.value;
    const type = document.getElementById('new-type')?.value;
    const scope = document.getElementById('new-scope')?.value;
    const url = document.getElementById('new-url')?.value;

    if (!name || !url) {
      toast('Please fill in all required fields', 'error');
      return;
    }

    const newConnector = {
      name,
      enabled: true,
      attributes: {
        pluginType: type,
        environmentScope: scope,
        endpointUrl: url,
        port: parseInt(document.getElementById('new-port')?.value) || 443,
        authMode: document.getElementById('new-auth')?.value || 'ApiKey',
        intervalMin: parseInt(document.getElementById('new-interval')?.value) || 15,
        notes: document.getElementById('new-notes')?.value || '',
        ownerTeam: document.getElementById('new-team')?.value || 'Platform Ops'
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

  async function saveConnector() {
    if (!selectedConnector) return;
    try {
      await APIClient.connectors.update(selectedConnector.id, {
        name: document.getElementById('edit-name')?.value || selectedConnector.name,
        attributes: {
          environmentScope: document.getElementById('edit-scope')?.value || selectedConnector.environmentScope,
          ownerTeam: document.getElementById('edit-team')?.value || selectedConnector.ownerTeam,
          authMode: document.getElementById('edit-auth')?.value || selectedConnector.authMode,
          intervalMin: parseInt(document.getElementById('edit-interval')?.value) || selectedConnector.schedules.intervalMin,
          notes: document.getElementById('edit-notes')?.value || ''
        }
      });
      await loadConnectors();
      closeDrawer();
      toast('Connector updated successfully', 'success');
      render();
    } catch (error) {
      toast('Unable to update connector', 'error');
    }
  }

  function toast(msg, type) {
    type = type || 'info';
    const existing = document.querySelector('.toast-notification');
    if (existing) existing.remove();

    const t = document.createElement('div');
    t.className = 'toast-notification fixed top-6 right-6 px-5 py-3 rounded-lg shadow-lg z-[100] animate-fade-in flex items-center gap-3';

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

  // Init
  async function init() {
    await loadConnectors();
    render();
    renderModal();
    console.log('Connectors module initialized');
  }

  return {
    init,
    setViewMode,
    setDrawerTab,
    openDrawer,
    closeDrawer,
    openModal,
    closeModal,
    toggleConnector,
    runTest,
    addConnector,
    saveConnector,
    refresh: async () => { await loadConnectors(); render(); toast('Connectors refreshed', 'success'); }
  };
})();

window.Connectors = Connectors;
