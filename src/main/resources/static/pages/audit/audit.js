/**
 * KFH AIOps Command Center - Audit Logs Module
 * Complete audit trail of all system activities
 */
const Audit = (function() {
  'use strict';

  // State
  const state = {
    currentTab: 'activity',
    data: {
      users: [],
      apps: [],
      incidents: [],
      alerts: [],
      connectors: [],
      scheduleJobs: [],
      auditEvents: [],
      loginSessions: []
    },
    ui: {
      searchQuery: '',
      dateRange: '15d',
      filters: {
        categories: [],
        actions: [],
        results: [],
        severities: [],
        showOnlyFailures: false
      },
      pageSize: 50,
      drawerOpen: false,
      drawerEvent: null,
      modalOpen: false,
      modalEvent: null
    }
  };

  // Utilities
  const randInt = (min, max) => Math.floor(Math.random() * (max - min + 1)) + min;
  const randChoice = arr => arr[Math.floor(Math.random() * arr.length)];
  const esc = s => s ? s.replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])) : '';

  // SVG Icons
  const icons = {
    search: '<circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/>',
    close: '<path d="M18 6L6 18"/><path d="m6 6 12 12"/>',
    clipboard: '<rect width="8" height="4" x="8" y="2" rx="1" ry="1"/><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/>',
    download: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" x2="12" y1="15" y2="3"/>',
    filter: '<polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3"/>'
  };

  function icon(name, size = 16) {
    return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${icons[name] || ''}</svg>`;
  }

  function formatTime(timestamp) {
    const date = new Date(timestamp);
    const now = new Date();
    const diff = now - date;
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);

    if (minutes < 1) return 'Just now';
    if (minutes < 60) return `${minutes}m ago`;
    if (hours < 24) return `${hours}h ago`;
    if (days < 7) return `${days}d ago`;
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  function formatFullTime(timestamp) {
    const date = new Date(timestamp);
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
  }

  function formatJSON(obj) {
    return JSON.stringify(obj, null, 2);
  }

  // Chip helper
  function chip(text, color) {
    return `<span class="audit-chip audit-chip-${color}">${esc(text)}</span>`;
  }

  // Demo Data Generation
  function generateDemoData() {
    const now = Date.now();
    const day = 86400000;
    const hour = 3600000;

    // Users
    state.data.users = [
      { id: 'usr-1', displayName: 'Ahmed Al-Mansour', team: 'Platform', role: 'Admin' },
      { id: 'usr-2', displayName: 'Fatima Hassan', team: 'Security', role: 'Security Analyst' },
      { id: 'usr-3', displayName: 'Mohammed Ali', team: 'Backend', role: 'Developer' },
      { id: 'usr-4', displayName: 'Sara Ibrahim', team: 'DevOps', role: 'DevOps Engineer' },
      { id: 'usr-5', displayName: 'Khalid Omar', team: 'Frontend', role: 'Developer' },
      { id: 'usr-6', displayName: 'Layla Abdullah', team: 'Platform', role: 'System Admin' },
      { id: 'usr-7', displayName: 'System', team: 'System', role: 'Automated' }
    ];

    // Apps
    state.data.apps = [
      { id: 'app-1', name: 'Customer Portal', env: 'Prod' },
      { id: 'app-2', name: 'Payment Gateway', env: 'Prod' },
      { id: 'app-3', name: 'Mobile API', env: 'Prod' },
      { id: 'app-4', name: 'Analytics Engine', env: 'DR' },
      { id: 'app-5', name: 'Notification Service', env: 'Prod' }
    ];

    // Connectors
    state.data.connectors = [
      { id: 'conn-1', name: 'Azure OpenAI', type: 'AI' },
      { id: 'conn-2', name: 'Neo4j Graph DB', type: 'Database' },
      { id: 'conn-3', name: 'SharePoint', type: 'Storage' },
      { id: 'conn-4', name: 'ServiceNow', type: 'ITSM' }
    ];

    // Schedule Jobs
    state.data.scheduleJobs = [
      { id: 'job-1', name: 'Hourly Alert Analysis', type: 'HourlyAnalysis' },
      { id: 'job-2', name: 'Azure OpenAI Sync', type: 'ConnectorSync' },
      { id: 'job-3', name: 'Daily Executive Report', type: 'ReportPack' },
      { id: 'job-4', name: 'Evidence Archive Export', type: 'EvidenceExport' },
      { id: 'job-5', name: 'Alert Retention Cleanup', type: 'Retention' }
    ];

    // Incidents
    for (let i = 1; i <= 20; i++) {
      state.data.incidents.push({
        id: `INC-${1000 + i}`,
        title: `Incident ${i}`,
        severity: randChoice(['Critical', 'High', 'Medium', 'Low']),
        status: randChoice(['Open', 'Investigating', 'Resolved', 'Closed'])
      });
    }

    // Alerts
    for (let i = 1; i <= 50; i++) {
      state.data.alerts.push({
        id: `alert-${i}`,
        severity: randChoice(['Critical', 'High', 'Medium', 'Low'])
      });
    }

    // Login Sessions
    state.data.loginSessions = [
      { id: 'sess-1', userId: 'usr-1', startedAt: now - 2 * hour, ip: '10.20.30.40' },
      { id: 'sess-2', userId: 'usr-2', startedAt: now - 5 * hour, ip: '10.20.30.41' },
      { id: 'sess-3', userId: 'usr-3', startedAt: now - 1 * day, ip: '10.20.30.42' },
      { id: 'sess-4', userId: 'usr-4', startedAt: now - 3 * hour, ip: '10.20.30.43' },
      { id: 'sess-5', userId: 'usr-5', startedAt: now - 8 * hour, ip: '10.20.30.44' }
    ];

    // Generate Audit Events
    const categories = ['Settings', 'Users', 'Connectors', 'Schedules', 'Incidents', 'Reports', 'Inventory', 'Applications', 'Alerts'];
    const actions = {
      Settings: ['Updated', 'Viewed', 'Exported'],
      Users: ['Login', 'Logout', 'Created', 'Updated', 'Deleted', 'Password Changed', 'Permission Denied', 'Failed Login'],
      Connectors: ['Created', 'Updated', 'Deleted', 'Tested', 'Secret Rotated', 'Sync Started', 'Sync Completed', 'Sync Failed'],
      Schedules: ['Created', 'Updated', 'Deleted', 'Enabled', 'Disabled', 'Run Started', 'Run Completed', 'Run Failed'],
      Incidents: ['Created', 'Updated', 'Closed', 'Reopened', 'Assigned', 'Commented'],
      Reports: ['Generated', 'Downloaded', 'Shared', 'Deleted'],
      Inventory: ['Asset Added', 'Asset Updated', 'Asset Removed', 'Scan Completed'],
      Applications: ['Registered', 'Updated', 'Deleted', 'Health Check'],
      Alerts: ['Created', 'Acknowledged', 'Suppressed', 'Group Created', 'Escalated']
    };

    const ips = ['10.20.30.40', '10.20.30.41', '10.20.30.42', '10.20.30.43', '10.20.30.44', '192.168.1.100', '192.168.1.101'];

    let eventId = 1;
    state.data.auditEvents = [];

    for (let dayOffset = 0; dayOffset < 15; dayOffset++) {
      const eventsPerDay = randInt(6, 10);

      for (let i = 0; i < eventsPerDay; i++) {
        const timestamp = now - (dayOffset * day) - (Math.random() * day);
        const category = randChoice(categories);
        const actionsForCategory = actions[category];
        const action = randChoice(actionsForCategory);
        const actor = randChoice(state.data.users);
        const severity = action.includes('Fail') || action.includes('Denied') ? 'High' :
                        action.includes('Delete') || action.includes('Secret') ? 'Warn' : 'Info';
        const result = action.includes('Fail') || action === 'Permission Denied' || action === 'Failed Login' ? 'Fail' : 'Success';
        const ip = randChoice(ips);
        const session = randChoice(state.data.loginSessions);

        let targetType = category.slice(0, -1);
        let targetId = null;
        let detailShort = '';
        let detailJson = {};

        switch (category) {
          case 'Settings':
            targetType = 'SystemConfig';
            targetId = 'config-general';
            detailShort = `${action} system configuration: ${randChoice(['Alert Thresholds', 'Retention Policy', 'Email Notifications', 'API Rate Limits'])}`;
            detailJson = { configKey: randChoice(['alertThreshold', 'retentionDays', 'emailEnabled', 'apiRateLimit']), before: '30', after: '45' };
            break;

          case 'Users':
            targetType = 'User';
            const targetUser = randChoice(state.data.users);
            targetId = targetUser.id;
            if (action === 'Login' || action === 'Logout') {
              detailShort = `${action} from ${ip}`;
              detailJson = { ip, sessionId: session.id, userAgent: 'Mozilla/5.0' };
            } else if (action === 'Failed Login') {
              detailShort = `Failed login attempt for ${targetUser.displayName}`;
              detailJson = { reason: 'Invalid credentials', ip, attempts: randInt(1, 3) };
            } else if (action === 'Permission Denied') {
              detailShort = `Access denied to ${randChoice(['Schedules', 'Connectors', 'Reports'])}`;
              detailJson = { resource: 'Schedules', action: 'delete', reason: 'Insufficient privileges' };
            } else {
              detailShort = `${action} user: ${targetUser.displayName}`;
              detailJson = { userId: targetUser.id, name: targetUser.displayName, team: targetUser.team };
            }
            break;

          case 'Connectors':
            const connector = randChoice(state.data.connectors);
            targetType = 'Connector';
            targetId = connector.id;
            detailShort = `${action} connector: ${connector.name}`;
            if (action.includes('Sync')) {
              detailJson = { connectorId: connector.id, recordsProcessed: randInt(50, 500), duration: randInt(10, 120), status: result };
            } else {
              detailJson = { connectorId: connector.id, name: connector.name, type: connector.type };
            }
            break;

          case 'Schedules':
            const job = randChoice(state.data.scheduleJobs);
            targetType = 'ScheduleJob';
            targetId = job.id;
            detailShort = `${action} schedule: ${job.name}`;
            if (action.includes('Run')) {
              detailJson = { jobId: job.id, runId: `run-${Date.now()}`, duration: randInt(20, 180), incidentsCreated: action === 'Run Completed' ? randInt(0, 3) : 0, artifactsCreated: action === 'Run Completed' ? randInt(1, 2) : 0 };
            } else {
              detailJson = { jobId: job.id, name: job.name, type: job.type, before: action === 'Updated' ? { enabled: true } : null, after: action === 'Updated' ? { enabled: false } : null };
            }
            break;

          case 'Incidents':
            const incident = randChoice(state.data.incidents);
            targetType = 'Incident';
            targetId = incident.id;
            detailShort = `${action} incident: ${incident.id} - ${incident.title}`;
            detailJson = { incidentId: incident.id, severity: incident.severity, status: incident.status, alertCount: randInt(1, 10) };
            break;

          case 'Alerts':
            const alert = randChoice(state.data.alerts);
            targetType = 'Alert';
            targetId = alert.id;
            detailShort = `${action} alert: ${alert.id}`;
            detailJson = { alertId: alert.id, severity: alert.severity, reason: action === 'Suppressed' ? 'Maintenance window' : null };
            break;

          case 'Reports':
            targetType = 'Report';
            targetId = `report-${randInt(1, 20)}`;
            detailShort = `${action} report: ${randChoice(['Executive Summary', 'Trend Analysis', 'Incident Report', 'Performance Dashboard'])}`;
            detailJson = { reportId: targetId, format: 'PDF', size: `${randInt(100, 500)}KB` };
            break;

          case 'Applications':
            const app = randChoice(state.data.apps);
            targetType = 'Application';
            targetId = app.id;
            detailShort = `${action} application: ${app.name}`;
            detailJson = { appId: app.id, name: app.name, env: app.env, status: action === 'Health Check' ? (Math.random() > 0.2 ? 'Healthy' : 'Degraded') : null };
            break;

          case 'Inventory':
            targetType = 'Asset';
            targetId = `asset-${randInt(1, 100)}`;
            detailShort = `${action}: ${randChoice(['Server', 'Database', 'Load Balancer', 'Storage'])}`;
            detailJson = { assetId: targetId, assetType: 'Server', location: randChoice(['DC1', 'DC2', 'Cloud-US', 'Cloud-EU']) };
            break;
        }

        state.data.auditEvents.push({
          id: `evt-${eventId++}`,
          ts: timestamp,
          actorUserId: actor.id,
          actorTeam: actor.team,
          action,
          category,
          targetType,
          targetId,
          severity,
          result,
          ip,
          sessionId: session.id,
          detailShort,
          detailJson
        });
      }
    }

    state.data.auditEvents.sort((a, b) => b.ts - a.ts);
  }

  // Filtering
  function getDateRangeFilter() {
    const now = Date.now();
    switch (state.ui.dateRange) {
      case 'today':
        const startOfDay = new Date();
        startOfDay.setHours(0, 0, 0, 0);
        return startOfDay.getTime();
      case '24h': return now - 86400000;
      case '7d': return now - 7 * 86400000;
      case '15d': return now - 15 * 86400000;
      default: return now - 15 * 86400000;
    }
  }

  function getFilteredEvents() {
    let events = [...state.data.auditEvents];

    const dateFilter = getDateRangeFilter();
    events = events.filter(e => e.ts >= dateFilter);

    if (state.ui.searchQuery) {
      const query = state.ui.searchQuery.toLowerCase();
      events = events.filter(e => {
        const actor = state.data.users.find(u => u.id === e.actorUserId);
        return e.detailShort.toLowerCase().includes(query) ||
               e.targetId?.toLowerCase().includes(query) ||
               actor?.displayName.toLowerCase().includes(query) ||
               e.action.toLowerCase().includes(query);
      });
    }

    if (state.ui.filters.categories.length > 0) {
      events = events.filter(e => state.ui.filters.categories.includes(e.category));
    }

    if (state.ui.filters.results.length > 0) {
      events = events.filter(e => state.ui.filters.results.includes(e.result));
    }

    if (state.ui.filters.severities.length > 0) {
      events = events.filter(e => state.ui.filters.severities.includes(e.severity));
    }

    if (state.ui.filters.showOnlyFailures) {
      events = events.filter(e => e.result === 'Fail');
    }

    // Tab-specific filtering
    if (state.currentTab === 'admin') {
      events = events.filter(e => ['Settings', 'Users', 'Connectors', 'Schedules'].includes(e.category));
    } else if (state.currentTab === 'security') {
      events = events.filter(e =>
        e.action === 'Login' || e.action === 'Logout' || e.action === 'Failed Login' ||
        e.action === 'Permission Denied' || e.action === 'Secret Rotated'
      );
    } else if (state.currentTab === 'jobs') {
      events = events.filter(e =>
        e.category === 'Schedules' && (e.action.includes('Run') || e.action.includes('Retention'))
      );
    } else if (state.currentTab === 'alerts') {
      events = events.filter(e => ['Incidents', 'Alerts'].includes(e.category));
    }

    return events;
  }

  function computeStats() {
    const events = getFilteredEvents();
    return {
      totalEvents: events.length,
      failures: events.filter(e => e.result === 'Fail').length,
      highSeverity: events.filter(e => e.severity === 'High').length,
      uniqueActors: new Set(events.map(e => e.actorUserId)).size,
      uniqueIps: new Set(events.map(e => e.ip)).size,
      failedLogins: events.filter(e => e.action === 'Failed Login').length,
      deniedActions: events.filter(e => e.action === 'Permission Denied').length,
      newSessions: events.filter(e => e.action === 'Login').length
    };
  }

  function getAllCategories() {
    return [...new Set(state.data.auditEvents.map(e => e.category))].sort();
  }

  // Rendering
  function render() {
    const container = document.getElementById('audit-content') || document.getElementById('page-root') || document.getElementById('content-area');
    if (!container) return;

    const stats = computeStats();
    const events = getFilteredEvents();
    const categories = getAllCategories();

    container.innerHTML = `
      <!-- Stats Grid -->
      <div class="audit-stats-grid animate-fade-in">
        <div class="kfh-card audit-stat-card">
          <div class="audit-stat-value audit-stat-value-default">${stats.totalEvents}</div>
          <div class="audit-stat-label">Total Events</div>
        </div>
        <div class="kfh-card audit-stat-card">
          <div class="audit-stat-value audit-stat-value-danger">${stats.failures}</div>
          <div class="audit-stat-label">Failures</div>
        </div>
        <div class="kfh-card audit-stat-card">
          <div class="audit-stat-value audit-stat-value-warning">${stats.highSeverity}</div>
          <div class="audit-stat-label">High Severity</div>
        </div>
        <div class="kfh-card audit-stat-card">
          <div class="audit-stat-value audit-stat-value-info">${stats.uniqueActors}</div>
          <div class="audit-stat-label">Unique Actors</div>
        </div>
      </div>

      <!-- Search & Filters -->
      <div class="kfh-card audit-filters-card animate-fade-in">
        <div class="audit-search-row">
          <div class="audit-search">
            <span class="audit-search-icon">${icon('search', 16)}</span>
            <input id="audit-search" type="text" placeholder="Search events..." class="audit-search-input" value="${esc(state.ui.searchQuery)}">
          </div>
          <select id="date-range" class="audit-select">
            <option value="today" ${state.ui.dateRange === 'today' ? 'selected' : ''}>Today</option>
            <option value="24h" ${state.ui.dateRange === '24h' ? 'selected' : ''}>Last 24 Hours</option>
            <option value="7d" ${state.ui.dateRange === '7d' ? 'selected' : ''}>Last 7 Days</option>
            <option value="15d" ${state.ui.dateRange === '15d' ? 'selected' : ''}>Last 15 Days</option>
          </select>
        </div>

        <div class="audit-filter-pills">
          <span class="audit-filter-label">Filters:</span>
          <label class="audit-checkbox-label">
            <input type="checkbox" id="failures-only" ${state.ui.filters.showOnlyFailures ? 'checked' : ''}>
            <span>Show only failures</span>
          </label>
        </div>

        <details class="audit-advanced-filters">
          <summary class="audit-advanced-summary">Advanced Filters</summary>
          <div class="audit-advanced-content">
            <div>
              <div class="audit-filter-group-label">Category</div>
              <div class="audit-filter-options">
                ${categories.map(cat => `
                  <label class="audit-filter-option">
                    <input type="checkbox" data-filter="categories" value="${cat}" ${state.ui.filters.categories.includes(cat) ? 'checked' : ''}>
                    <span>${cat}</span>
                  </label>
                `).join('')}
              </div>
            </div>
            <div>
              <div class="audit-filter-group-label">Result</div>
              <div class="audit-filter-options">
                ${['Success', 'Fail'].map(r => `
                  <label class="audit-filter-option">
                    <input type="checkbox" data-filter="results" value="${r}" ${state.ui.filters.results.includes(r) ? 'checked' : ''}>
                    <span>${r}</span>
                  </label>
                `).join('')}
              </div>
            </div>
            <div>
              <div class="audit-filter-group-label">Severity</div>
              <div class="audit-filter-options">
                ${['Info', 'Warn', 'High'].map(s => `
                  <label class="audit-filter-option">
                    <input type="checkbox" data-filter="severities" value="${s}" ${state.ui.filters.severities.includes(s) ? 'checked' : ''}>
                    <span>${s}</span>
                  </label>
                `).join('')}
              </div>
            </div>
          </div>
        </details>
      </div>

      <!-- Tabs -->
      <div class="kfh-card audit-tabs-card animate-fade-in">
        <div class="audit-tabs-header">
          ${['activity', 'admin', 'security', 'jobs', 'alerts'].map(tab => `
            <button onclick="Audit.setTab('${tab}')" class="audit-tab ${state.currentTab === tab ? 'active' : ''}">
              ${tab === 'activity' ? 'Activity Stream' : tab === 'admin' ? 'Admin Changes' : tab === 'security' ? 'Security / Access' : tab === 'jobs' ? 'System Jobs' : 'Alerts / Incidents'}
            </button>
          `).join('')}
        </div>
        <div class="audit-tabs-content">
          ${state.currentTab === 'activity' ? renderActivityStream(events) : ''}
          ${state.currentTab === 'admin' ? renderAdminChanges(events) : ''}
          ${state.currentTab === 'security' ? renderSecurityAccess(events) : ''}
          ${state.currentTab === 'jobs' ? renderSystemJobs(events) : ''}
          ${state.currentTab === 'alerts' ? renderAlertsIncidents(events) : ''}
        </div>

        ${events.length > state.ui.pageSize ? `
        <div class="audit-pagination">
          <span class="audit-pagination-info">Showing ${Math.min(state.ui.pageSize, events.length)} of ${events.length} events</span>
          <div class="audit-pagination-controls">
            <span class="audit-pagination-label">Page size:</span>
            <select id="page-size" class="audit-select" style="min-width: 80px;">
              <option value="25" ${state.ui.pageSize === 25 ? 'selected' : ''}>25</option>
              <option value="50" ${state.ui.pageSize === 50 ? 'selected' : ''}>50</option>
              <option value="100" ${state.ui.pageSize === 100 ? 'selected' : ''}>100</option>
            </select>
          </div>
        </div>
        ` : ''}
      </div>
    `;

    bindEvents();
  }

  function renderActivityStream(events) {
    if (events.length === 0) return renderEmpty();

    // Group by day
    const groupedByDay = {};
    events.forEach(event => {
      const dayKey = new Date(event.ts).toDateString();
      if (!groupedByDay[dayKey]) groupedByDay[dayKey] = [];
      groupedByDay[dayKey].push(event);
    });

    const sortedDays = Object.keys(groupedByDay).sort((a, b) => new Date(b).getTime() - new Date(a).getTime());

    return sortedDays.map(dayKey => {
      const dayEvents = groupedByDay[dayKey].slice(0, state.ui.pageSize);
      return `
        <div class="activity-day-group">
          <div class="activity-day-header">
            <span class="activity-day-title">${dayKey}</span>
          </div>
          <div class="activity-events">
            ${dayEvents.map((event, idx) => {
              const actor = state.data.users.find(u => u.id === event.actorUserId);
              const time = new Date(event.ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
              const dotClass = event.result === 'Fail' ? 'fail' : event.severity === 'High' ? 'warning' : 'success';
              return `
                <div class="activity-event">
                  <div class="activity-dot activity-dot-${dotClass}"></div>
                  ${idx < dayEvents.length - 1 ? '<div class="activity-line"></div>' : ''}
                  <div class="activity-event-card" onclick="Audit.openDrawer('${event.id}')">
                    <div class="activity-event-header">
                      <span class="activity-event-time">${time}</span>
                      ${chip(event.category, 'teal')}
                      ${event.result === 'Success' ? chip('Success', 'green') : chip('Failed', 'red')}
                      ${event.severity === 'High' ? chip('High', 'red') : ''}
                    </div>
                    <div class="activity-event-actor">${esc(actor?.displayName || 'Unknown')} ${event.action.toLowerCase()}</div>
                    <div class="activity-event-desc">${esc(event.detailShort)}</div>
                  </div>
                </div>
              `;
            }).join('')}
          </div>
        </div>
      `;
    }).join('');
  }

  function renderAdminChanges(events) {
    if (events.length === 0) return renderEmpty();

    return `
      <div class="overflow-x-auto">
        <table class="audit-table">
          <thead>
            <tr>
              <th>Timestamp</th>
              <th>Actor</th>
              <th>Action</th>
              <th>Category</th>
              <th>Target</th>
              <th>Change</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            ${events.slice(0, state.ui.pageSize).map(event => {
              const actor = state.data.users.find(u => u.id === event.actorUserId);
              const hasDiff = event.detailJson.before && event.detailJson.after;
              return `
                <tr>
                  <td class="text-[#666666]">${formatTime(event.ts)}</td>
                  <td>
                    <div class="audit-actor-name">${esc(actor?.displayName || 'Unknown')}</div>
                    <div class="audit-actor-team">${esc(actor?.team || '-')}</div>
                  </td>
                  <td>${esc(event.action)}</td>
                  <td>${chip(event.category, 'teal')}</td>
                  <td>
                    <div class="audit-target-type">${esc(event.targetType)}</div>
                    <div class="audit-target-id">${esc(event.targetId || '-')}</div>
                  </td>
                  <td>${hasDiff ? '<span style="color: #D97706;">Modified</span>' : '-'}</td>
                  <td>
                    <div class="flex items-center gap-3">
                      ${hasDiff ? `<button onclick="Audit.openDiff('${event.id}')" class="audit-action-btn audit-action-btn-blue">Diff</button>` : ''}
                      <button onclick="Audit.openDrawer('${event.id}')" class="audit-action-btn">Details</button>
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

  function renderSecurityAccess(events) {
    const stats = computeStats();

    return `
      <div class="security-kpi-grid">
        <div class="security-kpi-card">
          <div class="security-kpi-value" style="color: #DC2626;">${stats.failedLogins}</div>
          <div class="security-kpi-label">Failed Logins</div>
        </div>
        <div class="security-kpi-card">
          <div class="security-kpi-value" style="color: #D97706;">${stats.deniedActions}</div>
          <div class="security-kpi-label">Denied Actions</div>
        </div>
        <div class="security-kpi-card">
          <div class="security-kpi-value" style="color: var(--kfh-primary);">${stats.newSessions}</div>
          <div class="security-kpi-label">New Sessions</div>
        </div>
        <div class="security-kpi-card">
          <div class="security-kpi-value" style="color: #2563EB;">${stats.uniqueIps}</div>
          <div class="security-kpi-label">Unique IPs</div>
        </div>
      </div>

      ${events.length === 0 ? renderEmpty() : `
      <div class="overflow-x-auto">
        <table class="audit-table">
          <thead>
            <tr>
              <th>Timestamp</th>
              <th>User</th>
              <th>Action</th>
              <th>IP Address</th>
              <th>Session</th>
              <th>Result</th>
              <th>Details</th>
            </tr>
          </thead>
          <tbody>
            ${events.slice(0, state.ui.pageSize).map(event => {
              const actor = state.data.users.find(u => u.id === event.actorUserId);
              const actionChip = event.action === 'Failed Login' ? chip('Failed Login', 'red') :
                                 event.action === 'Permission Denied' ? chip('Access Denied', 'red') :
                                 event.action === 'Login' ? chip('Login', 'green') :
                                 event.action === 'Logout' ? chip('Logout', 'slate') :
                                 chip(event.action, 'amber');
              return `
                <tr>
                  <td class="text-[#666666]">${formatTime(event.ts)}</td>
                  <td>
                    <div class="audit-actor-name">${esc(actor?.displayName || 'Unknown')}</div>
                    <div class="audit-actor-team">${esc(actor?.team || '-')}</div>
                  </td>
                  <td>${actionChip}</td>
                  <td class="font-mono text-[#666666]">${esc(event.ip)}</td>
                  <td class="font-mono text-[#666666]">${esc(event.sessionId)}</td>
                  <td>${event.result === 'Success' ? chip('Success', 'green') : chip('Failed', 'red')}</td>
                  <td>
                    <button onclick="Audit.openDrawer('${event.id}')" class="audit-action-btn">View</button>
                  </td>
                </tr>
              `;
            }).join('')}
          </tbody>
        </table>
      </div>
      `}
    `;
  }

  function renderSystemJobs(events) {
    if (events.length === 0) return renderEmpty();

    return `
      <div class="overflow-x-auto">
        <table class="audit-table">
          <thead>
            <tr>
              <th>Timestamp</th>
              <th>Job</th>
              <th>Action</th>
              <th>Result</th>
              <th>Metrics</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            ${events.slice(0, state.ui.pageSize).map(event => {
              const job = state.data.scheduleJobs.find(j => j.id === event.targetId);
              const metrics = event.detailJson;
              let metricsText = metrics.duration ? `${metrics.duration}s` : '-';
              if (metrics.incidentsCreated > 0) metricsText += ` • ${metrics.incidentsCreated} incidents`;
              if (metrics.artifactsCreated > 0) metricsText += ` • ${metrics.artifactsCreated} artifacts`;
              return `
                <tr>
                  <td class="text-[#666666]">${formatTime(event.ts)}</td>
                  <td>
                    <div class="audit-actor-name">${esc(job?.name || event.targetId)}</div>
                    <div class="audit-actor-team">${esc(job?.type || '-')}</div>
                  </td>
                  <td>${chip(event.action, 'blue')}</td>
                  <td>${event.result === 'Success' ? chip('Success', 'green') : chip('Failed', 'red')}</td>
                  <td class="text-[#666666]">${metricsText}</td>
                  <td>
                    <button onclick="Audit.openDrawer('${event.id}')" class="audit-action-btn">Details</button>
                  </td>
                </tr>
              `;
            }).join('')}
          </tbody>
        </table>
      </div>
    `;
  }

  function renderAlertsIncidents(events) {
    if (events.length === 0) return renderEmpty();

    return `
      <div class="overflow-x-auto">
        <table class="audit-table">
          <thead>
            <tr>
              <th>Timestamp</th>
              <th>Category</th>
              <th>Action</th>
              <th>Target ID</th>
              <th>Actor</th>
              <th>Description</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            ${events.slice(0, state.ui.pageSize).map(event => {
              const actor = state.data.users.find(u => u.id === event.actorUserId);
              return `
                <tr>
                  <td class="text-[#666666]">${formatTime(event.ts)}</td>
                  <td>${chip(event.category, event.category === 'Incidents' ? 'red' : 'orange')}</td>
                  <td>${esc(event.action)}</td>
                  <td class="font-mono text-[#666666]">${esc(event.targetId || '-')}</td>
                  <td>
                    <div class="audit-actor-name">${esc(actor?.displayName || 'System')}</div>
                    <div class="audit-actor-team">${esc(actor?.team || '-')}</div>
                  </td>
                  <td class="max-w-xs truncate">${esc(event.detailShort)}</td>
                  <td>
                    <div class="flex items-center gap-3">
                      ${event.category === 'Incidents' ? `<button onclick="Audit.toast('info', 'Incident view not implemented')" class="audit-action-btn audit-action-btn-blue">Open</button>` : ''}
                      <button onclick="Audit.openDrawer('${event.id}')" class="audit-action-btn">Details</button>
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

  function renderEmpty() {
    return `
      <div class="audit-empty">
        <div class="audit-empty-icon">${icon('clipboard', 64)}</div>
        <p class="audit-empty-text">No events match your filters</p>
      </div>
    `;
  }

  // Drawer
  function openDrawer(eventId) {
    const event = state.data.auditEvents.find(e => e.id === eventId);
    if (!event) return;

    state.ui.drawerOpen = true;
    state.ui.drawerEvent = event;

    const overlay = document.getElementById('audit-drawer-overlay');
    const drawer = document.getElementById('audit-drawer');

    overlay.classList.add('open');
    drawer.classList.add('open');

    renderDrawerContent();
  }

  function closeDrawer() {
    state.ui.drawerOpen = false;
    state.ui.drawerEvent = null;

    document.getElementById('audit-drawer-overlay').classList.remove('open');
    document.getElementById('audit-drawer').classList.remove('open');
  }

  function renderDrawerContent() {
    const event = state.ui.drawerEvent;
    if (!event) return;

    const actor = state.data.users.find(u => u.id === event.actorUserId);
    const content = document.getElementById('drawer-content');

    content.innerHTML = `
      <div class="drawer-header">
        <div>
          <div class="drawer-header-title">Audit Event Details</div>
          <div class="drawer-header-badges">
            ${chip(event.category, 'teal')}
            ${event.result === 'Success' ? chip('Success', 'green') : chip('Failed', 'red')}
            ${event.severity === 'High' ? chip('High Severity', 'red') : event.severity === 'Warn' ? chip('Warning', 'amber') : chip('Info', 'blue')}
          </div>
        </div>
        <button onclick="Audit.closeDrawer()" class="drawer-close-btn">${icon('close', 20)}</button>
      </div>

      <div class="drawer-body">
        <div class="drawer-info-grid">
          <div class="drawer-info-card">
            <div class="drawer-info-label">Timestamp</div>
            <div class="drawer-info-value">${formatFullTime(event.ts)}</div>
          </div>
          <div class="drawer-info-card">
            <div class="drawer-info-label">Action</div>
            <div class="drawer-info-value">${esc(event.action)}</div>
          </div>
          <div class="drawer-info-card">
            <div class="drawer-info-label">Actor</div>
            <div class="drawer-info-value">${esc(actor?.displayName || 'Unknown')}</div>
            <div class="drawer-info-sub">${esc(actor?.team || '-')}</div>
          </div>
          <div class="drawer-info-card">
            <div class="drawer-info-label">IP Address</div>
            <div class="drawer-info-value" style="font-family: monospace;">${esc(event.ip)}</div>
          </div>
          <div class="drawer-info-card">
            <div class="drawer-info-label">Session ID</div>
            <div class="drawer-info-value" style="font-family: monospace;">${esc(event.sessionId)}</div>
          </div>
          <div class="drawer-info-card">
            <div class="drawer-info-label">Target</div>
            <div class="drawer-info-value">${esc(event.targetType)}</div>
            <div class="drawer-info-sub" style="font-family: monospace;">${esc(event.targetId || '-')}</div>
          </div>
        </div>

        <div class="drawer-section">
          <div class="drawer-section-title">Description</div>
          <p class="drawer-description">${esc(event.detailShort)}</p>
        </div>

        <div class="drawer-section">
          <div class="drawer-section-title">
            <span>Event Payload (JSON)</span>
            <button onclick="Audit.copyJSON('${event.id}')" class="px-3 py-1.5 bg-[#128754] hover:bg-[#0E6B42] text-white rounded-lg text-xs font-semibold transition-colors">Copy JSON</button>
          </div>
          <div class="json-viewer">
            <pre>${esc(formatJSON(event.detailJson))}</pre>
          </div>
        </div>
      </div>
    `;
  }

  // Diff Modal
  function openDiff(eventId) {
    const event = state.data.auditEvents.find(e => e.id === eventId);
    if (!event || !event.detailJson.before || !event.detailJson.after) return;

    state.ui.modalOpen = true;
    state.ui.modalEvent = event;

    const overlay = document.getElementById('audit-modal-overlay');
    overlay.classList.add('open');

    overlay.innerHTML = `
      <div class="audit-modal">
        <div class="modal-header">
          <h3 class="modal-title">Configuration Diff</h3>
          <button onclick="Audit.closeModal()" class="modal-close">${icon('close', 20)}</button>
        </div>
        <div class="modal-body">
          <div class="diff-grid">
            <div>
              <h4 class="diff-column-title">Before</h4>
              <div class="json-viewer">
                <pre>${esc(formatJSON(event.detailJson.before))}</pre>
              </div>
            </div>
            <div>
              <h4 class="diff-column-title">After</h4>
              <div class="json-viewer" style="background: #064E3B;">
                <pre style="color: #6EE7B7;">${esc(formatJSON(event.detailJson.after))}</pre>
              </div>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button onclick="Audit.closeModal()" class="px-4 py-2 bg-[#4B5563] hover:bg-[#374151] text-white rounded-lg text-sm font-semibold transition-colors">Close</button>
        </div>
      </div>
    `;
  }

  function closeModal() {
    state.ui.modalOpen = false;
    state.ui.modalEvent = null;
    document.getElementById('audit-modal-overlay').classList.remove('open');
  }

  // Actions
  function setTab(tab) {
    state.currentTab = tab;
    render();
  }

  function clearAllFilters() {
    state.ui.searchQuery = '';
    state.ui.dateRange = '15d';
    state.ui.filters = { categories: [], actions: [], results: [], severities: [], showOnlyFailures: false };
    render();
    toast('success', 'Filters cleared');
  }

  function copyJSON(eventId) {
    const event = state.data.auditEvents.find(e => e.id === eventId);
    if (!event) return;

    const text = formatJSON(event.detailJson);
    if (navigator.clipboard) {
      navigator.clipboard.writeText(text).then(() => toast('success', 'Copied to clipboard')).catch(() => toast('error', 'Failed to copy'));
    } else {
      toast('error', 'Clipboard not available');
    }
  }

  function exportCSV() {
    toast('info', 'Exporting audit logs to CSV...');
    setTimeout(() => toast('success', 'CSV export ready for download'), 1000);
  }

  function exportJSON() {
    toast('info', 'Exporting audit logs to JSON...');
    setTimeout(() => toast('success', 'JSON export ready for download'), 1000);
  }

  function toast(type, msg) {
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

  // Events
  function bindEvents() {
    document.getElementById('audit-search')?.addEventListener('input', e => {
      state.ui.searchQuery = e.target.value;
      render();
    });

    document.getElementById('date-range')?.addEventListener('change', e => {
      state.ui.dateRange = e.target.value;
      render();
    });

    document.getElementById('failures-only')?.addEventListener('change', e => {
      state.ui.filters.showOnlyFailures = e.target.checked;
      render();
    });

    document.getElementById('page-size')?.addEventListener('change', e => {
      state.ui.pageSize = parseInt(e.target.value);
      render();
    });

    document.querySelectorAll('[data-filter]').forEach(el => {
      el.addEventListener('change', e => {
        const filterType = e.target.dataset.filter;
        const value = e.target.value;
        const checked = e.target.checked;

        if (checked) {
          if (!state.ui.filters[filterType].includes(value)) {
            state.ui.filters[filterType].push(value);
          }
        } else {
          const idx = state.ui.filters[filterType].indexOf(value);
          if (idx > -1) state.ui.filters[filterType].splice(idx, 1);
        }
        render();
      });
    });

    document.getElementById('audit-drawer-overlay')?.addEventListener('click', closeDrawer);
  }

  // Init
  function init() {
    generateDemoData();
    render();
    console.log('Audit module initialized');
  }

  return {
    init,
    setTab,
    openDrawer,
    closeDrawer,
    openDiff,
    closeModal,
    copyJSON,
    exportCSV,
    exportJSON,
    clearAllFilters,
    toast,
    refresh: () => { generateDemoData(); render(); toast('success', 'Audit logs refreshed'); }
  };
})();

window.Audit = Audit;
