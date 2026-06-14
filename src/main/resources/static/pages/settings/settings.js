/**
 * KFH AIOps Command Center - Settings Module
 * Configure integrations and system preferences
 */
var Settings = (function() {
  'use strict';

  function emptySettings() {
    return {
      azureOpenAI: {
        embeddings: { roundRobin: false, endpoint: '', apiKey: '', deploymentA: '', deploymentB: '', circuitBreakerA: 'unknown', circuitBreakerB: 'unknown', lastTest: null },
        gpt: { roundRobin: false, endpoint: '', apiKey: '', deploymentA: '', deploymentB: '', circuitBreakerA: 'unknown', circuitBreakerB: 'unknown', lastTest: null }
      },
      neo4j: { boltUrl: '', user: '', password: '', lastTest: null },
      postgresql: { jdbcUrl: '', user: '', password: '', lastTest: null },
      sharepoint: { tenant: '', site: '', clientId: '', clientSecret: '', lastTest: null },
      teams: { mappings: [] }
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
    settings: emptySettings()
  };

  // Utilities
  const esc = s => s ? s.replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])) : '';

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
      current = current[keys[i]];
    }
    current[keys[keys.length - 1]] = value;
    render();
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
      const result = await APIClient.settings.test(path, state.settings);
      const status = result?.status || (result?.pass === false ? 'Fail' : 'Pass');
      handleSettingChange(`${path}.lastTest`, { status, latency: result?.latencyMs || result?.latency || null });
      toast(status === 'Pass' ? 'Test Passed' : 'Test Failed', status === 'Pass' ? 'success' : 'error');
    } catch (error) {
      handleSettingChange(`${path}.lastTest`, { status: 'Fail', latency: null });
      toast('Test Failed', 'error');
    }
  }

  // Toggle circuit breaker
  function toggleCircuit(path, currentStatus) {
    handleSettingChange(path, currentStatus === 'healthy' ? 'tripped' : 'healthy');
  }

  // Save settings
  async function saveSettings() {
    try {
      const saved = await APIClient.settings.update(state.settings);
      state.settings = Object.assign(emptySettings(), saved || state.settings);
      state.hasUnsavedChanges = false;
      render();
      toast('Settings saved successfully', 'success');
    } catch (error) {
      toast('Unable to save settings', 'error');
    }
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
      state.settings = Object.assign(emptySettings(), loaded || {});
    } catch (error) {
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
    if (state.searchQuery) {
      return keywords.toLowerCase().includes(state.searchQuery.toLowerCase());
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
        <div class="flex items-center justify-between">
          <div>
            <h1 class="settings-title">Settings</h1>
            <p class="settings-subtitle">Configure integrations and system preferences</p>
          </div>
          <div class="settings-actions">
            <div class="settings-search">
              <span class="settings-search-icon">${icon('search', 16)}</span>
              <input id="settings-search" type="text" placeholder="Search settings..." class="settings-search-input" value="${esc(state.searchQuery)}">
            </div>
            <button onclick="Settings.resetSettings()" class="settings-btn settings-btn-outline">Reset</button>
            <button onclick="Settings.saveSettings()" class="settings-btn settings-btn-primary">Save Changes</button>
          </div>
        </div>
        
        ${state.hasUnsavedChanges ? `
          <div class="unsaved-bar">
            ${icon('alert', 16)}
            You have unsaved changes
          </div>
        ` : ''}
        
        ${!state.searchQuery ? `
          <div class="settings-tabs">
            <button onclick="Settings.setTab('azure')" class="settings-tab ${state.activeTab === 'azure' ? 'active' : ''}">
              ${icon('cloud', 16)} Azure OpenAI
            </button>
            <button onclick="Settings.setTab('databases')" class="settings-tab ${state.activeTab === 'databases' ? 'active' : ''}">
              ${icon('database', 16)} Databases
            </button>
            <button onclick="Settings.setTab('sharepoint')" class="settings-tab ${state.activeTab === 'sharepoint' ? 'active' : ''}">
              ${icon('file', 16)} SharePoint
            </button>
            <button onclick="Settings.setTab('teams')" class="settings-tab ${state.activeTab === 'teams' ? 'active' : ''}">
              ${icon('chat', 16)} Microsoft Teams
            </button>
          </div>
        ` : ''}
      </div>
    `;
  }

  // Render Azure OpenAI
  function renderAzureOpenAI() {
    if (!isVisible('azure', 'azure openai gpt embeddings ai')) return '';

    const s = state.settings.azureOpenAI;

    return `
      <div class="animate-fade-in">
        <h3 class="settings-section-title">Artificial Intelligence</h3>
        <div class="settings-card">
          <div class="settings-card-header">
            <h3 class="settings-card-title">Azure OpenAI Configuration</h3>
          </div>
          <div class="settings-card-body">
            <!-- Embeddings -->
            <div class="settings-subsection">
              <div class="settings-subsection-header">
                <h4 class="settings-subsection-title">${icon('bolt', 16)} Embeddings</h4>
                <label class="settings-toggle-label">
                  Round-Robin
                  <div class="settings-toggle ${s.embeddings.roundRobin ? 'active' : ''}" onclick="Settings.handleChange('azureOpenAI.embeddings.roundRobin', ${!s.embeddings.roundRobin})"></div>
                </label>
              </div>
              
              <div class="grid grid-cols-2 gap-5 mb-5">
                <div class="settings-form-group">
                  <label class="settings-label">Endpoint URL</label>
                  <input type="text" class="settings-input" value="${esc(s.embeddings.endpoint)}" onchange="Settings.handleChange('azureOpenAI.embeddings.endpoint', this.value)">
                </div>
                <div class="settings-form-group">
                  <label class="settings-label">API Key</label>
                  <div class="settings-input-group">
                    <input type="${state.revealedSecrets.has('emb.key') ? 'text' : 'password'}" class="settings-input" value="${esc(s.embeddings.apiKey)}" onchange="Settings.handleChange('azureOpenAI.embeddings.apiKey', this.value)">
                    <button onclick="Settings.toggleReveal('emb.key')" class="settings-input-reveal">${state.revealedSecrets.has('emb.key') ? icon('eyeOff', 16) : icon('eye', 16)}</button>
                  </div>
                </div>
              </div>

              <div class="grid grid-cols-2 gap-5">
                <div class="deployment-box">
                  <div class="deployment-header">
                    <span class="deployment-label">Deployment A</span>
                    ${chip(s.embeddings.circuitBreakerA, s.embeddings.circuitBreakerA)}
                  </div>
                  <div class="deployment-controls">
                    <input type="text" class="deployment-input" value="${esc(s.embeddings.deploymentA)}" onchange="Settings.handleChange('azureOpenAI.embeddings.deploymentA', this.value)">
                    <button onclick="Settings.toggleCircuit('azureOpenAI.embeddings.circuitBreakerA', '${s.embeddings.circuitBreakerA}')" class="settings-btn-toggle">Toggle</button>
                  </div>
                </div>
                <div class="deployment-box">
                  <div class="deployment-header">
                    <span class="deployment-label">Deployment B</span>
                    ${chip(s.embeddings.circuitBreakerB, s.embeddings.circuitBreakerB)}
                  </div>
                  <div class="deployment-controls">
                    <input type="text" class="deployment-input" value="${esc(s.embeddings.deploymentB)}" onchange="Settings.handleChange('azureOpenAI.embeddings.deploymentB', this.value)">
                    <button onclick="Settings.toggleCircuit('azureOpenAI.embeddings.circuitBreakerB', '${s.embeddings.circuitBreakerB}')" class="settings-btn-toggle">Toggle</button>
                  </div>
                </div>
              </div>

              <div class="mt-4 pt-4 border-t border-gray-200/50 flex justify-between items-center">
                <span class="settings-status ${s.embeddings.lastTest?.status === 'Pass' ? 'settings-status-pass' : s.embeddings.lastTest?.status === 'Fail' ? 'settings-status-fail' : ''}">
                  ${s.embeddings.lastTest ? `Last Test: ${s.embeddings.lastTest.status} (${s.embeddings.lastTest.latency}ms)` : 'Status: Unknown'}
                </span>
                <button onclick="Settings.runTest('azureOpenAI.embeddings')" class="settings-btn settings-btn-ghost">Test Connection</button>
              </div>
            </div>

            <!-- GPT -->
            <div class="settings-subsection">
              <div class="settings-subsection-header">
                <h4 class="settings-subsection-title">${icon('chat', 16)} GPT-5.2-Pro</h4>
                <label class="settings-toggle-label">
                  Round-Robin
                  <div class="settings-toggle ${s.gpt.roundRobin ? 'active' : ''}" onclick="Settings.handleChange('azureOpenAI.gpt.roundRobin', ${!s.gpt.roundRobin})"></div>
                </label>
              </div>
              
              <div class="grid grid-cols-2 gap-5 mb-5">
                <div class="settings-form-group">
                  <label class="settings-label">Endpoint URL</label>
                  <input type="text" class="settings-input" value="${esc(s.gpt.endpoint)}" onchange="Settings.handleChange('azureOpenAI.gpt.endpoint', this.value)">
                </div>
                <div class="settings-form-group">
                  <label class="settings-label">API Key</label>
                  <div class="settings-input-group">
                    <input type="${state.revealedSecrets.has('gpt.key') ? 'text' : 'password'}" class="settings-input" value="${esc(s.gpt.apiKey)}" onchange="Settings.handleChange('azureOpenAI.gpt.apiKey', this.value)">
                    <button onclick="Settings.toggleReveal('gpt.key')" class="settings-input-reveal">${state.revealedSecrets.has('gpt.key') ? icon('eyeOff', 16) : icon('eye', 16)}</button>
                  </div>
                </div>
              </div>

              <div class="grid grid-cols-2 gap-5">
                <div class="deployment-box">
                  <div class="deployment-header">
                    <span class="deployment-label">Deployment A</span>
                    ${chip(s.gpt.circuitBreakerA, s.gpt.circuitBreakerA)}
                  </div>
                  <div class="deployment-controls">
                    <input type="text" class="deployment-input" value="${esc(s.gpt.deploymentA)}" onchange="Settings.handleChange('azureOpenAI.gpt.deploymentA', this.value)">
                    <button onclick="Settings.toggleCircuit('azureOpenAI.gpt.circuitBreakerA', '${s.gpt.circuitBreakerA}')" class="settings-btn-toggle">Toggle</button>
                  </div>
                </div>
                <div class="deployment-box">
                  <div class="deployment-header">
                    <span class="deployment-label">Deployment B</span>
                    ${chip(s.gpt.circuitBreakerB, s.gpt.circuitBreakerB)}
                  </div>
                  <div class="deployment-controls">
                    <input type="text" class="deployment-input" value="${esc(s.gpt.deploymentB)}" onchange="Settings.handleChange('azureOpenAI.gpt.deploymentB', this.value)">
                    <button onclick="Settings.toggleCircuit('azureOpenAI.gpt.circuitBreakerB', '${s.gpt.circuitBreakerB}')" class="settings-btn-toggle">Toggle</button>
                  </div>
                </div>
              </div>

              <div class="mt-4 pt-4 border-t border-gray-200/50 flex justify-between items-center">
                <span class="settings-status ${s.gpt.lastTest?.status === 'Pass' ? 'settings-status-pass' : s.gpt.lastTest?.status === 'Fail' ? 'settings-status-fail' : ''}">
                  ${s.gpt.lastTest ? `Last Test: ${s.gpt.lastTest.status} (${s.gpt.lastTest.latency}ms)` : 'Status: Unknown'}
                </span>
                <button onclick="Settings.runTest('azureOpenAI.gpt')" class="settings-btn settings-btn-ghost">Test Connection</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    `;
  }

  // Render Databases
  function renderDatabases() {
    if (!isVisible('databases', 'neo4j postgres sql database')) return '';

    const neo = state.settings.neo4j;
    const pg = state.settings.postgresql;

    return `
      <div class="animate-fade-in">
        <h3 class="settings-section-title">Data Persistence</h3>
        <div class="settings-grid">
          <!-- Neo4j -->
          <div class="settings-card">
            <div class="settings-card-header">
              <h3 class="settings-card-title">Neo4j Graph</h3>
              ${chip('Active', 'active')}
            </div>
            <div class="settings-card-body">
              <div class="settings-form-group">
                <label class="settings-label">Bolt URL</label>
                <input type="text" class="settings-input" value="${esc(neo.boltUrl)}" onchange="Settings.handleChange('neo4j.boltUrl', this.value)">
              </div>
              <div class="settings-form-group">
                <label class="settings-label">Username</label>
                <input type="text" class="settings-input" value="${esc(neo.user)}" onchange="Settings.handleChange('neo4j.user', this.value)">
              </div>
              <div class="settings-form-group">
                <label class="settings-label">Password</label>
                <div class="settings-input-group">
                  <input type="${state.revealedSecrets.has('neo.pass') ? 'text' : 'password'}" class="settings-input" value="${esc(neo.password)}" onchange="Settings.handleChange('neo4j.password', this.value)">
                  <button onclick="Settings.toggleReveal('neo.pass')" class="settings-input-reveal">${state.revealedSecrets.has('neo.pass') ? icon('eyeOff', 16) : icon('eye', 16)}</button>
                </div>
              </div>
              <button onclick="Settings.runTest('neo4j')" class="settings-btn settings-btn-outline w-full mt-2">Test Bolt Connection</button>
            </div>
            ${neo.lastTest ? `
              <div class="settings-card-footer">
                <span class="settings-status ${neo.lastTest.status === 'Pass' ? 'settings-status-pass' : 'settings-status-fail'}">
                  Last Test: ${neo.lastTest.status} (${neo.lastTest.latency}ms)
                </span>
              </div>
            ` : ''}
          </div>

          <!-- PostgreSQL -->
          <div class="settings-card">
            <div class="settings-card-header">
              <h3 class="settings-card-title">PostgreSQL</h3>
              ${chip('Active', 'active')}
            </div>
            <div class="settings-card-body">
              <div class="settings-form-group">
                <label class="settings-label">JDBC URL</label>
                <input type="text" class="settings-input" value="${esc(pg.jdbcUrl)}" onchange="Settings.handleChange('postgresql.jdbcUrl', this.value)">
              </div>
              <div class="settings-form-group">
                <label class="settings-label">Username</label>
                <input type="text" class="settings-input" value="${esc(pg.user)}" onchange="Settings.handleChange('postgresql.user', this.value)">
              </div>
              <div class="settings-form-group">
                <label class="settings-label">Password</label>
                <div class="settings-input-group">
                  <input type="${state.revealedSecrets.has('pg.pass') ? 'text' : 'password'}" class="settings-input" value="${esc(pg.password)}" onchange="Settings.handleChange('postgresql.password', this.value)">
                  <button onclick="Settings.toggleReveal('pg.pass')" class="settings-input-reveal">${state.revealedSecrets.has('pg.pass') ? icon('eyeOff', 16) : icon('eye', 16)}</button>
                </div>
              </div>
              <button onclick="Settings.runTest('postgresql')" class="settings-btn settings-btn-outline w-full mt-2">Test SQL Connection</button>
            </div>
            ${pg.lastTest ? `
              <div class="settings-card-footer">
                <span class="settings-status ${pg.lastTest.status === 'Pass' ? 'settings-status-pass' : 'settings-status-fail'}">
                  Last Test: ${pg.lastTest.status} (${pg.lastTest.latency}ms)
                </span>
              </div>
            ` : ''}
          </div>
        </div>
      </div>
    `;
  }

  // Render SharePoint
  function renderSharePoint() {
    if (!isVisible('sharepoint', 'sharepoint microsoft document')) return '';

    const sp = state.settings.sharepoint;

    return `
      <div class="animate-fade-in">
        <h3 class="settings-section-title">Document Management</h3>
        <div class="settings-card">
          <div class="settings-card-header">
            <h3 class="settings-card-title">SharePoint Configuration</h3>
          </div>
          <div class="settings-card-body">
            <div class="grid grid-cols-2 gap-6">
              <div class="settings-form-group">
                <label class="settings-label">Tenant</label>
                <input type="text" class="settings-input" value="${esc(sp.tenant)}" onchange="Settings.handleChange('sharepoint.tenant', this.value)">
              </div>
              <div class="settings-form-group">
                <label class="settings-label">Site</label>
                <input type="text" class="settings-input" value="${esc(sp.site)}" onchange="Settings.handleChange('sharepoint.site', this.value)">
              </div>
              <div class="settings-form-group">
                <label class="settings-label">Client ID</label>
                <input type="text" class="settings-input" value="${esc(sp.clientId)}" onchange="Settings.handleChange('sharepoint.clientId', this.value)">
              </div>
              <div class="settings-form-group">
                <label class="settings-label">Client Secret</label>
                <div class="settings-input-group">
                  <input type="${state.revealedSecrets.has('sp.secret') ? 'text' : 'password'}" class="settings-input" value="${esc(sp.clientSecret)}" onchange="Settings.handleChange('sharepoint.clientSecret', this.value)">
                  <button onclick="Settings.toggleReveal('sp.secret')" class="settings-input-reveal">${state.revealedSecrets.has('sp.secret') ? icon('eyeOff', 16) : icon('eye', 16)}</button>
                </div>
              </div>
            </div>
            <div class="mt-6 flex justify-end">
              <button onclick="Settings.runTest('sharepoint')" class="settings-btn settings-btn-primary">Verify Access</button>
            </div>
          </div>
          ${sp.lastTest ? `
            <div class="settings-card-footer">
              <span class="settings-status ${sp.lastTest.status === 'Pass' ? 'settings-status-pass' : 'settings-status-fail'}">
                Last Test: ${sp.lastTest.status} (${sp.lastTest.latency}ms)
              </span>
            </div>
          ` : ''}
        </div>
      </div>
    `;
  }

  // Render Teams
  function renderTeams() {
    if (!isVisible('teams', 'microsoft teams chat webhook')) return '';

    const teams = state.settings.teams;

    return `
      <div class="animate-fade-in">
        <h3 class="settings-section-title">Collaboration</h3>
        <div class="settings-card">
          <div class="settings-card-header">
            <h3 class="settings-card-title">Teams Webhooks</h3>
            <button onclick="Settings.openModal()" class="settings-btn settings-btn-ghost settings-btn-sm">
              ${icon('plus', 14)} Add Mapping
            </button>
          </div>
          <div class="settings-card-body">
            <div class="teams-table-wrapper">
              <table class="teams-table">
                <thead>
                  <tr>
                    <th>Domain / Team</th>
                    <th>Channel</th>
                    <th>Webhook URL</th>
                    <th>Active</th>
                    <th class="text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  ${teams.mappings.map(m => `
                    <tr class="${state.selectedTeamMapping === m.id ? 'teams-row-selected' : ''}">
                      <td>
                        <div class="teams-domain">${esc(m.domain)}</div>
                        <div class="teams-team">${esc(m.team)}</div>
                      </td>
                      <td class="teams-channel">#${esc(m.channelName)}</td>
                      <td>
                        <div class="flex items-center gap-2">
                          <code class="teams-webhook-code">${state.revealedSecrets.has('team.' + m.id) ? esc(m.webhookUrl) : '••••••••••••••••••••'}</code>
                          <button onclick="Settings.toggleReveal('team.${m.id}')" class="text-gray-400 hover:text-[#128754]">
                            ${state.revealedSecrets.has('team.' + m.id) ? icon('eyeOff', 12) : icon('eye', 12)}
                          </button>
                        </div>
                      </td>
                      <td>
                        <input type="checkbox" class="settings-checkbox" ${m.enabled ? 'checked' : ''} onchange="Settings.toggleTeamMapping(${m.id}, this.checked)">
                      </td>
                      <td>
                        <div class="teams-actions">
                          <button onclick="Settings.selectTeamMapping(${m.id})" class="teams-action-btn teams-action-btn-test">
                            ${state.selectedTeamMapping === m.id ? 'Selected' : 'Test'}
                          </button>
                          <button onclick="Settings.deleteTeamMapping(${m.id})" class="teams-action-btn teams-action-btn-delete">Delete</button>
                        </div>
                      </td>
                    </tr>
                  `).join('')}
                </tbody>
              </table>
            </div>
            
            ${state.selectedTeamMapping ? `
              <div class="teams-test-bar">
                <span class="teams-test-bar-text">Ready to test webhook for selected row.</span>
                <button onclick="Settings.runTest('teams')" class="settings-btn settings-btn-primary settings-btn-sm">Send Test Payload</button>
              </div>
            ` : ''}
          </div>
        </div>
      </div>
    `;
  }

  // Render modal
  function renderModal() {
    if (!state.modalOpen) return '';

    return `
      <div id="settings-modal-overlay" class="settings-modal-overlay open" onclick="if(event.target === this) Settings.closeModal()">
        <div class="settings-modal">
          <div class="settings-modal-header">
            <h3 class="settings-modal-title">Add Team Mapping</h3>
            <button onclick="Settings.closeModal()" class="settings-modal-close">${icon('close', 20)}</button>
          </div>
          <div class="settings-modal-body">
            <div class="settings-form-group">
              <label class="settings-label">Domain</label>
              <input type="text" id="new-domain" class="settings-input" placeholder="e.g. Payments">
            </div>
            <div class="settings-form-group">
              <label class="settings-label">Team</label>
              <input type="text" id="new-team" class="settings-input" placeholder="e.g. SRE-Team-A">
            </div>
            <div class="settings-form-group">
              <label class="settings-label">Channel Name</label>
              <input type="text" id="new-channel" class="settings-input" placeholder="alerts-prod">
            </div>
            <div class="settings-form-group">
              <label class="settings-label">Webhook URL</label>
              <input type="text" id="new-url" class="settings-input" placeholder="https://...">
            </div>
          </div>
          <div class="settings-modal-footer">
            <button onclick="Settings.closeModal()" class="settings-btn settings-btn-outline">Cancel</button>
            <button onclick="Settings.addTeamMapping()" class="settings-btn settings-btn-primary">Add Mapping</button>
          </div>
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
    render();
  }

  function closeModal() {
    state.modalOpen = false;
    render();
  }

  function addTeamMapping() {
    const domain = document.getElementById('new-domain')?.value;
    const team = document.getElementById('new-team')?.value;
    const channel = document.getElementById('new-channel')?.value;
    const url = document.getElementById('new-url')?.value;

    if (!domain || !team || !channel || !url) {
      toast('Please fill in all fields', 'error');
      return;
    }

    const newMapping = {
      id: Date.now(),
      domain,
      team,
      channelName: channel,
      webhookUrl: url,
      enabled: true
    };

    state.settings.teams.mappings.push(newMapping);
    state.hasUnsavedChanges = true;
    state.modalOpen = false;
    render();
    toast('New mapping added', 'success');
  }

  // Main render
  function render() {
    const container = document.getElementById('settings-content') || document.getElementById('page-root') || document.getElementById('content-area');
    if (!container) return;

    container.innerHTML = `
      ${renderHeader()}
      <div class="settings-content">
        <div class="settings-section">
          ${renderAzureOpenAI()}
          ${renderDatabases()}
          ${renderSharePoint()}
          ${renderTeams()}
        </div>
      </div>
      ${renderModal()}
    `;

    bindEvents();
  }

  function bindEvents() {
    document.getElementById('settings-search')?.addEventListener('input', e => {
      state.searchQuery = e.target.value;
      render();
    });
  }

  function setTab(tab) {
    state.activeTab = tab;
    render();
  }

  async function init() {
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
    toggleCircuit,
    saveSettings,
    resetSettings,
    toggleTeamMapping,
    selectTeamMapping,
    deleteTeamMapping,
    openModal,
    closeModal,
    addTeamMapping,
    toast
  };
})();

window.Settings = Settings;
