/**
 * KFH AIOps Command Center - Audit Activity Module
 * API-backed application activity view. No generated or dummy audit events.
 */
var Audit = (function() {
  'use strict';

  const state = {
    events: [],
    loading: false,
    error: '',
    meta: { totalElements: 0, page: 0, size: 100 },
    ui: {
      searchQuery: '',
      dateRange: '15d',
      category: 'ALL',
      result: 'ALL',
      pageSize: 100,
      drawerEvent: null
    }
  };

  const icons = {
    search: '<circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/>',
    close: '<path d="M18 6L6 18"/><path d="m6 6 12 12"/>',
    clipboard: '<rect width="8" height="4" x="8" y="2" rx="1" ry="1"/><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/>',
    download: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" x2="12" y1="15" y2="3"/>',
    refresh: '<path d="M3 12a9 9 0 0 1 15.5-6.2L21 8"/><path d="M21 3v5h-5"/><path d="M21 12a9 9 0 0 1-15.5 6.2L3 16"/><path d="M3 21v-5h5"/>',
    shield: '<path d="M20 13c0 5-3.5 7.5-7.7 8.9a1 1 0 0 1-.6 0C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.2-2.5a1.3 1.3 0 0 1 1.6 0C14.5 3.8 17 5 19 5a1 1 0 0 1 1 1z"/>',
    activity: '<path d="M22 12h-4l-3 9L9 3l-3 9H2"/>'
  };

  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const icon = (name, size = 16) => `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${icons[name] || ''}</svg>`;

  function pageContent(response) {
    if (response && Array.isArray(response.content)) {
      state.meta = {
        totalElements: Number(response.totalElements || response.content.length),
        page: Number(response.page || 0),
        size: Number(response.size || state.ui.pageSize)
      };
      return response.content;
    }
    state.meta = { totalElements: Array.isArray(response) ? response.length : 0, page: 0, size: state.ui.pageSize };
    return Array.isArray(response) ? response : [];
  }

  function parseDetails(row) {
    const details = row.afterState || row.details || row.detailJson || row;
    if (typeof details === 'string') {
      try { return JSON.parse(details); } catch (_) { return { value: details }; }
    }
    return details && typeof details === 'object' ? details : {};
  }

  function normalizeAction(action) {
    return String(action || 'UNKNOWN').replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
  }

  function firstNonEmpty(...values) {
    return values.map(value => String(value ?? '').trim()).find(value => value.length > 0) || '';
  }

  function isLoginAction(action) {
    return String(action || '').startsWith('LOGIN_');
  }

  function isUuidLike(value) {
    return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(String(value || '').trim());
  }

  function isGenericLoginMessage(message) {
    return /^LOGIN_(SUCCEEDED|FAILED) on Security [0-9a-f-]{36}$/i.test(String(message || '').trim());
  }

  function normalizeAuditEvent(row) {
    const timestamp = row.createdAt || row.timestamp || row.updatedAt || new Date().toISOString();
    const details = parseDetails(row);
    const action = row.action || 'UNKNOWN';
    const loginAction = isLoginAction(action);
    const actorSource = (row.actorSource || details.actorSource || '').toString().toLowerCase();
    const isSystemActor = actorSource === 'system';
    const actorName = firstNonEmpty(
      row.actorName, row.userName, details.actorName, details.displayName, details.username,
      isSystemActor ? 'System' : '', row.actorUsername, details.actorUsername,
      'System');
    const rawTargetId = firstNonEmpty(row.targetId, row.entityId);
    const targetId = loginAction && isUuidLike(rawTargetId) ? 'AUTHENTICATION' : rawTargetId;
    const defaultLoginMessage = action === 'LOGIN_FAILED'
      ? `Login failed${actorName ? ` for ${actorName}` : ''}`
      : `Login succeeded${actorName ? ` for ${actorName}` : ''}`;
    const rawMessage = firstNonEmpty(row.message, row.detail);
    const message = loginAction && isGenericLoginMessage(rawMessage)
      ? defaultLoginMessage
      : firstNonEmpty(rawMessage, loginAction ? defaultLoginMessage : `${normalizeAction(action)} ${row.entityType || 'activity'}`);
    return {
      id: String(row.id || row.eventId || row.auditId || cryptoFallbackId()),
      ts: Date.parse(timestamp),
      timestamp,
      tenantId: row.tenantId || '',
      countryCode: row.countryCode || '',
      environment: row.environment || '',
      category: row.category || row.entityType || 'Application',
      action,
      actionLabel: normalizeAction(action),
      actorUserId: firstNonEmpty(row.actorUserId, details.actorUserId, row.userId),
      actorUsername: firstNonEmpty(row.actorUsername, details.actorUsername, ''),
      actorSource: actorSource || (isSystemActor ? 'system' : 'user'),
      actorName,
      result: row.result || 'Success',
      severity: row.severity || 'Info',
      targetType: firstNonEmpty(row.targetType, details.targetType, row.entityType, 'Application'),
      targetId,
      message,
      details,
      ip: row.ipAddress || '',
      correlationId: row.correlationId || '',
      sessionId: row.sessionId || ''
    };
  }

  function cryptoFallbackId() {
    if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
    return `audit-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  function safeDate(ts) {
    const date = new Date(ts);
    return Number.isNaN(date.getTime()) ? new Date() : date;
  }

  function formatTime(ts) {
    const date = safeDate(ts);
    const diff = Date.now() - date.getTime();
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);
    if (minutes < 1) return 'Just now';
    if (minutes < 60) return `${minutes}m ago`;
    if (hours < 24) return `${hours}h ago`;
    if (days < 7) return `${days}d ago`;
    return date.toLocaleString();
  }

  function fullTime(ts) {
    return safeDate(ts).toLocaleString();
  }

  function dateFloor() {
    const now = Date.now();
    if (state.ui.dateRange === 'today') {
      const start = new Date();
      start.setHours(0, 0, 0, 0);
      return start.getTime();
    }
    if (state.ui.dateRange === '24h') return now - 86400000;
    if (state.ui.dateRange === '7d') return now - 7 * 86400000;
    if (state.ui.dateRange === '30d') return now - 30 * 86400000;
    return now - 15 * 86400000;
  }

  function filteredEvents() {
    const query = state.ui.searchQuery.trim().toLowerCase();
    return state.events
      .filter(e => !e.ts || e.ts >= dateFloor())
      .filter(e => state.ui.category === 'ALL' || e.category === state.ui.category)
      .filter(e => state.ui.result === 'ALL' || e.result === state.ui.result)
      .filter(e => {
        if (!query) return true;
        return [e.action, e.actionLabel, e.actorName, e.actorUserId, e.category, e.targetId, e.targetType, e.message, e.correlationId, e.countryCode, e.environment]
          .some(value => String(value || '').toLowerCase().includes(query));
      });
  }

  function stats(events) {
    return {
      total: events.length,
      failures: events.filter(e => e.result === 'Fail' || e.result === 'Failed').length,
      actors: new Set(events.map(e => e.actorUserId || e.actorName).filter(Boolean)).size,
      categories: new Set(events.map(e => e.category).filter(Boolean)).size
    };
  }

  function categories() {
    return [...new Set(state.events.map(e => e.category).filter(Boolean))].sort();
  }

  function chip(text, tone = 'slate') {
    return `<span class="audit-chip audit-chip-${tone}">${esc(text)}</span>`;
  }

  function resultTone(result) {
    return result === 'Fail' || result === 'Failed' ? 'red' : 'green';
  }

  function categoryTone(category) {
    const value = String(category || '').toLowerCase();
    if (value.includes('incident')) return 'red';
    if (value.includes('connector')) return 'blue';
    if (value.includes('schedule') || value.includes('job')) return 'amber';
    if (value.includes('user') || value.includes('role')) return 'purple';
    if (value.includes('security') || value.includes('setting')) return 'orange';
    return 'teal';
  }

  function render() {
    ensureHosts();
    const container = document.getElementById('audit-content') || document.getElementById('page-root') || document.getElementById('content-area');
    if (!container) return;

    const events = filteredEvents();
    const summary = stats(events);

    container.innerHTML = `
      <div class="kfh-phdr">
        <div class="kfh-phdr-titlewrap"><h1 class="kfh-phdr-title">Audit Activity</h1><span class="kfh-phdr-sub">${events.length} event${events.length === 1 ? '' : 's'}</span></div>
        <div class="kfh-phdr-search">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
          <input id="audit-search" type="text" placeholder="Search action, actor, target, correlation ID…" value="${esc(state.ui.searchQuery)}" autocomplete="off">
        </div>
        <div class="kfh-phdr-ctrls">
          <select id="date-range" class="kfh-phdr-select" aria-label="Date range">
            ${option('today', 'Today', state.ui.dateRange)}
            ${option('24h', 'Last 24 Hours', state.ui.dateRange)}
            ${option('7d', 'Last 7 Days', state.ui.dateRange)}
            ${option('15d', 'Last 15 Days', state.ui.dateRange)}
            ${option('30d', 'Last 30 Days', state.ui.dateRange)}
          </select>
          <select id="category-filter" class="kfh-phdr-select" aria-label="Category">
            ${option('ALL', 'All categories', state.ui.category)}
            ${categories().map(category => option(category, category, state.ui.category)).join('')}
          </select>
          <select id="result-filter" class="kfh-phdr-select" aria-label="Result">
            ${option('ALL', 'All results', state.ui.result)}
            ${option('Success', 'Success', state.ui.result)}
            ${option('Fail', 'Failure', state.ui.result)}
          </select>
          <button class="audit-btn audit-btn-secondary" onclick="Audit.refresh()">${icon('refresh', 15)} Refresh</button>
          <button class="audit-btn audit-btn-secondary" onclick="Audit.exportCSV()">${icon('download', 15)} CSV</button>
          <button class="kfh-phdr-btn-primary" onclick="Audit.exportJSON()">${icon('download', 15)} JSON</button>
        </div>
      </div>

      <div class="audit-page-body" style="padding:18px;">
      ${state.error ? `<div class="audit-alert audit-alert-error">${esc(state.error)}</div>` : ''}

      <div class="audit-stats-grid animate-fade-in">
        ${statCard('activity', summary.total, 'Visible activity', 'Filtered events in view')}
        ${statCard('shield', summary.failures, 'Failures', 'Failed or denied actions', summary.failures > 0 ? 'danger' : 'success')}
        ${statCard('clipboard', summary.actors, 'Actors', 'Unique users/systems')}
        ${statCard('activity', summary.categories, 'Categories', 'Modules with activity')}
      </div>

      <div class="kfh-card audit-activity-card animate-fade-in">
        <div class="audit-card-header">
          <div>
            <h2>Application activity</h2>
            <p>Showing ${events.length} of ${state.meta.totalElements || state.events.length} loaded audit records</p>
          </div>
          <select id="page-size" class="audit-select audit-page-size" aria-label="Rows to load">
            ${[25, 50, 100].map(size => option(String(size), String(size), String(state.ui.pageSize))).join('')}
          </select>
        </div>
        ${state.loading ? renderLoading() : renderActivityTable(events)}
      </div>
      </div>
    `;

    bindEvents();
  }

  function statCard(iconName, value, label, hint, tone = 'default') {
    return `
      <div class="kfh-card audit-stat-card audit-stat-card-${tone}">
        <div class="audit-stat-icon">${icon(iconName, 18)}</div>
        <div class="audit-stat-value audit-stat-value-${tone === 'danger' ? 'danger' : tone === 'success' ? 'success' : 'default'}">${value}</div>
        <div class="audit-stat-label">${esc(label)}</div>
        <div class="audit-stat-hint">${esc(hint)}</div>
      </div>
    `;
  }

  function option(value, label, selected) {
    return `<option value="${esc(value)}" ${String(selected) === String(value) ? 'selected' : ''}>${esc(label)}</option>`;
  }

  function renderLoading() {
    return `<div class="audit-loading"><div class="audit-spinner"></div><span>Loading activity...</span></div>`;
  }

  function renderActivityTable(events) {
    if (events.length === 0) return renderEmpty();
    return `
      <div class="audit-table-wrap">
        <table class="audit-table">
          <thead>
            <tr>
              <th>Time</th>
              <th>Activity</th>
              <th>Actor</th>
              <th>Target</th>
              <th>Scope</th>
              <th>Result</th>
              <th>Correlation</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            ${events.slice(0, state.ui.pageSize).map(event => `
              <tr>
                <td>
                  <div class="audit-time-main">${esc(formatTime(event.ts))}</div>
                  <div class="audit-time-sub">${esc(fullTime(event.ts))}</div>
                </td>
                <td>
                  <div class="audit-activity-main">${esc(event.actionLabel)}</div>
                  <div class="audit-activity-sub">${esc(event.message)}</div>
                  <div class="audit-row-chips">${chip(event.category, categoryTone(event.category))}${event.severity && event.severity !== 'Info' ? chip(event.severity, 'amber') : ''}</div>
                </td>
                <td>
                  <div class="audit-actor-name">${esc(event.actorName)}</div>
                  <div class="audit-actor-team">${esc(event.actorSource === 'system' ? 'System' : (event.actorUsername || event.actorUserId || 'System'))}</div>
                </td>
                <td>
                  <div class="audit-target-type">${esc(event.targetType || '-')}</div>
                  <div class="audit-target-id">${esc(event.targetId || '-')}</div>
                </td>
                <td>
                  <div class="audit-scope">${esc(event.countryCode || '-')} • ${esc(event.environment || '-')}</div>
                  <div class="audit-actor-team">Tenant scoped</div>
                </td>
                <td>${chip(event.result, resultTone(event.result))}</td>
                <td class="audit-correlation">${esc(event.correlationId || '-')}</td>
                <td><button onclick="Audit.openDrawer('${esc(event.id)}')" class="audit-action-btn">Details</button></td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>
    `;
  }

  function renderEmpty() {
    return `
      <div class="audit-empty">
        <div class="audit-empty-icon">${icon('clipboard', 56)}</div>
        <h3>No application activity found</h3>
        <p>No real audit events match the current filters. Activity appears here after users create, update, delete, test, run, or generate items in the application.</p>
        <button class="audit-btn audit-btn-secondary" onclick="Audit.clearAllFilters()">Clear filters</button>
      </div>
    `;
  }

  function ensureHosts() {
    if (!document.getElementById('audit-drawer-overlay')) {
      const overlay = document.createElement('div');
      overlay.id = 'audit-drawer-overlay';
      overlay.className = 'audit-drawer-overlay';
      overlay.addEventListener('click', closeDrawer);
      document.body.appendChild(overlay);
    }
    if (!document.getElementById('audit-drawer')) {
      const drawer = document.createElement('aside');
      drawer.id = 'audit-drawer';
      drawer.className = 'audit-drawer';
      drawer.setAttribute('aria-label', 'Audit event details');
      document.body.appendChild(drawer);
    }
  }

  function openDrawer(eventId) {
    const event = state.events.find(item => item.id === eventId);
    if (!event) return;
    state.ui.drawerEvent = event;
    ensureHosts();
    document.getElementById('audit-drawer-overlay').classList.add('open');
    document.getElementById('audit-drawer').classList.add('open');
    renderDrawer(event);
  }

  function closeDrawer() {
    state.ui.drawerEvent = null;
    document.getElementById('audit-drawer-overlay')?.classList.remove('open');
    document.getElementById('audit-drawer')?.classList.remove('open');
  }

  function renderDrawer(event) {
    const drawer = document.getElementById('audit-drawer');
    if (!drawer) return;
    drawer.innerHTML = `
      <div class="drawer-header">
        <div>
          <div class="drawer-header-title">Activity details</div>
          <div class="drawer-header-badges">${chip(event.category, categoryTone(event.category))}${chip(event.result, resultTone(event.result))}${chip(event.countryCode || 'Scope', 'slate')}</div>
        </div>
        <button onclick="Audit.closeDrawer()" class="drawer-close-btn" aria-label="Close details">${icon('close', 20)}</button>
      </div>
      <div class="drawer-body">
        <div class="drawer-info-grid">
          ${drawerInfo('Timestamp', fullTime(event.ts))}
          ${drawerInfo('Action', event.actionLabel)}
          ${drawerInfo('Actor', event.actorName, event.actorSource === 'system' ? 'System' : (event.actorUsername || event.actorUserId || 'System'))}
          ${drawerInfo('Target', event.targetType || '-', event.targetId || '-')}
          ${drawerInfo('Country / Env', `${event.countryCode || '-'} / ${event.environment || '-'}`)}
          ${drawerInfo('Correlation ID', event.correlationId || '-')}
        </div>
        <div class="drawer-section">
          <div class="drawer-section-title">Description</div>
          <p class="drawer-description">${esc(event.message)}</p>
        </div>
        <div class="drawer-section">
          <div class="drawer-section-title">
            <span>Activity payload</span>
            <button onclick="Audit.copyJSON('${esc(event.id)}')" class="audit-btn audit-btn-secondary audit-btn-small">Copy JSON</button>
          </div>
          <div class="json-viewer"><pre>${esc(JSON.stringify(event.details, null, 2))}</pre></div>
        </div>
      </div>
    `;
  }

  function drawerInfo(label, value, sub = '') {
    return `<div class="drawer-info-card"><div class="drawer-info-label">${esc(label)}</div><div class="drawer-info-value">${esc(value)}</div>${sub ? `<div class="drawer-info-sub">${esc(sub)}</div>` : ''}</div>`;
  }

  function bindEvents() {
    KFHUtils.bindLiveSearch('audit-search', function(value) {
      state.ui.searchQuery = value;
      render();
    });
    document.getElementById('date-range')?.addEventListener('change', event => { state.ui.dateRange = event.target.value; render(); });
    document.getElementById('category-filter')?.addEventListener('change', event => { state.ui.category = event.target.value; render(); });
    document.getElementById('result-filter')?.addEventListener('change', event => { state.ui.result = event.target.value; render(); });
    document.getElementById('page-size')?.addEventListener('change', async event => {
      state.ui.pageSize = Number(event.target.value);
      await loadAuditData();
      render();
    });
  }

  async function loadAuditData() {
    state.loading = true;
    state.error = '';
    render();
    try {
      if (!window.APIClient || !APIClient.audit) {
        state.events = [];
        state.error = 'Audit API client is unavailable.';
        return;
      }
      const response = await APIClient.audit.list({ page: 0, size: state.ui.pageSize });
      state.events = pageContent(response)
        .map(normalizeAuditEvent)
        .filter(event => event.correlationId !== 'seed-data')
        .sort((a, b) => (b.ts || 0) - (a.ts || 0));
    } catch (error) {
      console.warn('[Audit] Unable to load audit activity.', error);
      state.events = [];
      state.error = 'Unable to load audit activity. Check your audit permission and backend availability.';
    } finally {
      state.loading = false;
    }
  }

  function clearAllFilters() {
    state.ui.searchQuery = '';
    state.ui.dateRange = '15d';
    state.ui.category = 'ALL';
    state.ui.result = 'ALL';
    render();
  }

  function copyJSON(eventId) {
    const event = state.events.find(item => item.id === eventId);
    if (!event) return;
    const text = JSON.stringify(event.details, null, 2);
    if (!navigator.clipboard) {
      toast('error', 'Clipboard is not available');
      return;
    }
    navigator.clipboard.writeText(text).then(() => toast('success', 'Activity payload copied')).catch(() => toast('error', 'Unable to copy payload'));
  }

  function exportCSV() {
    const rows = filteredEvents();
    const header = ['timestamp', 'countryCode', 'environment', 'category', 'action', 'actor', 'targetType', 'targetId', 'result', 'correlationId'];
    const csv = [header.join(',')]
      .concat(rows.map(row => header.map(key => csvValue(key === 'actor' ? row.actorName : row[key])).join(',')))
      .join('\n');
    download(`audit-activity-${new Date().toISOString().slice(0, 10)}.csv`, 'text/csv', csv);
  }

  function exportJSON() {
    download(`audit-activity-${new Date().toISOString().slice(0, 10)}.json`, 'application/json', JSON.stringify(filteredEvents(), null, 2));
  }

  function csvValue(value) {
    return `"${String(value ?? '').replace(/"/g, '""')}"`;
  }

  function download(filename, type, content) {
    const blob = new Blob([content], { type });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(link.href);
  }

  function toast(type, msg) {
    const container = document.getElementById('toast-container') || document.body;
    const existing = document.querySelector('.toast-notification');
    existing?.remove();
    const item = document.createElement('div');
    item.className = `toast-notification audit-toast audit-toast-${type}`;
    item.textContent = msg;
    container.appendChild(item);
    setTimeout(() => item.remove(), 3500);
  }

  async function refresh() {
    await loadAuditData();
    render();
    toast('success', 'Audit activity refreshed');
  }

  async function init() {
    ensureHosts();
    await loadAuditData();
    render();
  }

  return {
    init,
    refresh,
    openDrawer,
    closeDrawer,
    copyJSON,
    exportCSV,
    exportJSON,
    clearAllFilters,
    toast,
    setTab: () => {}
  };
})();

window['Audit'] = Audit;
