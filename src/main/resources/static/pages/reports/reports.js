/**
 * KFH AIOps Command Center - Reports Module
 * "Beyond Horizons" Design System
 * Report runs and artifacts management with full feature set
 */
var Reports = (function() {
  'use strict';

  // ===== STATE =====
  const state = {
    runs: [],
    artifacts: [],
    apps: [],
    resources: [],
    alertGroups: [],
    filteredRuns: [],
    searchQuery: '',
    timeRange: '24h',
    filters: {
      status: [],
      criticalOpenOnly: false,
      hasOriginalCSV: false,
      hasFailures: false,
      windowMinutes: []
    },
    selectedRun: null,
    activeTab: 'overview'
  };

  // ===== UTILITIES =====
  const esc = s => s ? s.replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])) : '';

  function formatDate(timestamp) {
    return new Date(timestamp).toLocaleString('en-US', {
      month: 'short', day: 'numeric', year: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  }

  function formatTimeAgo(timestamp) {
    const now = Date.now();
    const diff = now - timestamp;
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);

    if (minutes < 1) return 'Just now';
    if (minutes < 60) return `${minutes}m ago`;
    if (hours < 24) return `${hours}h ago`;
    return `${days}d ago`;
  }

  function formatDuration(ms) {
    const seconds = Math.floor(Number(ms || 0) / 1000);
    const minutes = Math.floor(seconds / 60);
    if (minutes < 1) return `${seconds}s`;
    return `${minutes}m ${seconds % 60}s`;
  }

  // ===== API DATA =====
      function pageContent(response) {
        return response && Array.isArray(response.content) ? response.content : Array.isArray(response) ? response : [];
      }

      function normalizeRun(row) {
        const startedAt = Date.parse(row.startedAt || row.createdAt || new Date().toISOString());
        const completedAt = row.completedAt ? Date.parse(row.completedAt) : null;
        return {
          runId: String(row.runId || row.id || ''),
          startedAt,
          completedAt,
          windowMinutes: Number(row.windowMinutes || 0),
          status: row.status || 'Completed',
          totals: row.totals || { alerts: 0, alertGroups: 0, incidents: 0, newCount: 0, recurringCount: 0, criticalOpen: 0, mtta: 0, mttr: 0, noisySuppressed: 0 },
          topApps: Array.isArray(row.topApps) ? row.topApps : [],
          topFingerprints: Array.isArray(row.topFingerprints) ? row.topFingerprints : [],
          sourceBreakdown: row.sourceBreakdown || {},
          severityBreakdown: row.severityBreakdown || {},
          notes: Array.isArray(row.notes) ? row.notes : [],
          executionLog: Array.isArray(row.executionLog) ? row.executionLog.map(log => ({ ...log, ts: Date.parse(log.ts || log.timestamp || new Date().toISOString()) })) : []
        };
      }

      function normalizeArtifact(row) {
        return {
          id: String(row.id || row.artifactId || ''),
          runId: String(row.runId || ''),
          type: row.type || row.artifactType || 'Artifact',
          fileName: row.fileName || row.name || 'artifact',
          sizeKB: Number(row.sizeKB || row.sizeKb || 0),
          createdAt: Date.parse(row.createdAt || new Date().toISOString()),
          checksum: row.checksum || '',
          rows: row.rows || null
        };
      }

      async function loadData() {
        state.runs = [];
        state.artifacts = [];
        state.apps = [];
        state.resources = [];
        state.alertGroups = [];
        if (!window.APIClient || !APIClient.reports) {
          applyFilters();
          return;
        }
        try {
          const [runsResponse, artifactsResponse] = await Promise.all([
            APIClient.reports.listRuns({ page: 0, size: 100 }),
            APIClient.reports.listArtifacts({ page: 0, size: 200 })
          ]);
          state.runs = pageContent(runsResponse).map(normalizeRun).sort((a, b) => b.startedAt - a.startedAt);
          state.artifacts = pageContent(artifactsResponse).map(normalizeArtifact);
        } catch (error) {
          console.warn('[Reports] Unable to load production report data; rendering empty state.', error);
        }
        applyFilters();
  }

  // ===== FILTERING =====
  function getTimeRangeFilter() {
    const now = Date.now();
    switch (state.timeRange) {
      case '24h': return now - 86400000;
      case '7d': return now - 7 * 86400000;
      case '15d': return now - 15 * 86400000;
      default: return now - 86400000;
    }
  }

  function applyFilters() {
    let runs = [...state.runs];

    // Time range
    const timeFilter = getTimeRangeFilter();
    runs = runs.filter(r => r.startedAt >= timeFilter);

    // Search
    if (state.searchQuery) {
      const query = state.searchQuery.toLowerCase();
      runs = runs.filter(r => {
        return r.runId.toLowerCase().includes(query) ||
               r.notes.some(n => n.toLowerCase().includes(query)) ||
               r.topApps.some(a => a.appName.toLowerCase().includes(query)) ||
               r.topFingerprints.some(f => f.fingerprint.toLowerCase().includes(query)) ||
               getRunArtifacts(r.runId).some(a => a.fileName.toLowerCase().includes(query));
      });
    }

    // Critical open filter
    if (state.filters.criticalOpenOnly) {
      runs = runs.filter(r => r.totals.criticalOpen > 0);
    }

    // Has original CSV filter
    if (state.filters.hasOriginalCSV) {
      runs = runs.filter(r => {
        const artifacts = getRunArtifacts(r.runId);
        return artifacts.some(a => a.type === 'OriginalCSV');
      });
    }

    // Has failures filter
    if (state.filters.hasFailures) {
      runs = runs.filter(r => r.status === 'Failed');
    }

    state.filteredRuns = runs;
  }

  function getRunArtifacts(runId) {
    return state.artifacts.filter(a => a.runId === runId);
  }

  // ===== TOAST NOTIFICATIONS =====
  function toast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    const toastEl = document.createElement('div');
    toastEl.className = `toast toast-${type}`;
    toastEl.innerHTML = `<span class="toast-message">${esc(message)}</span>`;
    container.appendChild(toastEl);

    setTimeout(() => {
      toastEl.style.opacity = '0';
      toastEl.style.transform = 'translateX(100%)';
      toastEl.style.transition = 'all 0.3s ease-out';
      setTimeout(() => toastEl.remove(), 300);
    }, 3000);
  }

  // ===== ACTIONS =====
  async function generateReportPack() {
    const latestRun = state.runs.find(r => r.status === 'Completed');
    if (!latestRun) {
      toast('No completed runs available', 'error');
      return;
    }

    const existingArtifacts = getRunArtifacts(latestRun.runId);
    if (existingArtifacts.length >= 6) {
      toast(`Report pack already exists for ${latestRun.runId}`, 'info');
      return;
    }

    if (!window.APIClient || !APIClient.reports || !APIClient.reports.generatePack) {
      toast('Report generation API is unavailable', 'error');
      return;
    }
    toast(`Generating report pack for ${latestRun.runId}...`, 'info');
    try {
      await APIClient.reports.generatePack(latestRun.runId);
      await loadData();
      render();
      toast(`Report pack requested for ${latestRun.runId}`, 'success');
    } catch (error) {
      console.warn('[Reports] Unable to generate report pack.', error);
      toast('Unable to generate report pack', 'error');
    }
  }

  function exportIndexCSV() {
    toast('Exporting runs index to CSV...', 'info');
    setTimeout(() => {
      const headers = ['Run ID', 'Started At', 'Status', 'Window (min)', 'Alerts', 'Alert Groups', 'Incidents', 'New', 'Recurring', 'Critical Open', 'MTTA (min)', 'MTTR (min)'];
      const rows = state.filteredRuns.map(r => [
        r.runId,
        new Date(r.startedAt).toISOString(),
        r.status,
        r.windowMinutes,
        r.totals.alerts,
        r.totals.alertGroups,
        r.totals.incidents,
        r.totals.newCount,
        r.totals.recurringCount,
        r.totals.criticalOpen,
        r.totals.mtta,
        r.totals.mttr
      ]);

      const csv = [headers, ...rows].map(row => row.join(',')).join('\n');
      downloadBlob(csv, 'runs_index.csv', 'text/csv');
      toast('Runs index CSV downloaded', 'success');
    }, 500);
  }

  async function downloadArtifact(artifact) {
    if (!window.APIClient || !APIClient.reports || !APIClient.reports.downloadArtifact) {
      toast('Artifact download API is unavailable', 'error');
      return;
    }
    toast(`Downloading ${artifact.fileName}...`, 'info');
    try {
      const blob = await APIClient.reports.downloadArtifact(artifact.id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = artifact.fileName;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      toast(`Downloaded ${artifact.fileName}`, 'success');
    } catch (error) {
      console.warn('[Reports] Unable to download artifact.', error);
      toast('Unable to download artifact', 'error');
    }
  }

  function downloadBlob(content, fileName, mimeType) {
    const blob = new Blob([content], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }

  // ===== DRAWER =====
  function openDrawer(runId) {
    state.selectedRun = state.runs.find(r => r.runId === runId);
    if (!state.selectedRun) return;
    state.activeTab = 'overview';

    // Ensure drawer shell exists (some SPA shells may not include it)
    ensureDrawerShell();

    renderDrawer();

    const overlay = document.getElementById('drawer-overlay');
    const drawer = document.getElementById('run-drawer');
    overlay?.classList.add('open');
    drawer?.classList.add('open');
  }

  function closeDrawer() {
    document.getElementById('drawer-overlay')?.classList.remove('open');
    document.getElementById('run-drawer')?.classList.remove('open');
    state.selectedRun = null;
  }

  function switchTab(tab) {
    state.activeTab = tab;
    renderDrawer();
  }

  function ensureDrawerShell() {
    if (document.getElementById('drawer-content')) return;

    // Attach to the current page container so the drawer works even without global markup.
    const host = document.getElementById('content-area') || document.body;

    if (!document.getElementById('drawer-overlay')) {
      const overlay = document.createElement('div');
      overlay.id = 'drawer-overlay';
      overlay.className = 'drawer-overlay';
      host.appendChild(overlay);
    }

    if (!document.getElementById('run-drawer')) {
      const drawer = document.createElement('div');
      drawer.id = 'run-drawer';
      drawer.className = 'run-drawer';
      drawer.innerHTML = '<div id="drawer-content"></div>';
      host.appendChild(drawer);
    } else if (!document.getElementById('drawer-content')) {
      const drawer = document.getElementById('run-drawer');
      const content = document.createElement('div');
      content.id = 'drawer-content';
      drawer.appendChild(content);
    }

    // Ensure overlay click closes
    document.getElementById('drawer-overlay')?.addEventListener('click', closeDrawer);
  }

  function renderDrawer() {
    if (!state.selectedRun) return;

    const drawerContent = document.getElementById('drawer-content');
    if (!drawerContent) {
      // Last-resort: try to create shell and retry once.
      ensureDrawerShell();
    }

    const drawerContent2 = document.getElementById('drawer-content');
    if (!drawerContent2) {
      console.error('[Reports] Drawer container (#drawer-content) not found; cannot render drawer');
      return;
    }

    const run = state.selectedRun;
    const artifacts = getRunArtifacts(run.runId);
    const statusChip = run.status === 'Completed' ? chip('Completed', 'completed') :
                       run.status === 'Failed' ? chip('Failed', 'failed') :
                       chip('Running', 'running');

    const tabs = [
      { id: 'overview', label: 'Overview' },
      { id: 'analysis', label: 'Analysis' },
      { id: 'artifacts', label: `Artifacts (${artifacts.length})` },
      { id: 'evidence', label: 'Evidence' },
      { id: 'audit', label: 'Audit' }
    ];

    let tabContent = '';
    switch (state.activeTab) {
      case 'overview': tabContent = renderOverviewTab(run); break;
      case 'analysis': tabContent = renderAnalysisTab(run); break;
      case 'artifacts': tabContent = renderArtifactsTab(run, artifacts); break;
      case 'evidence': tabContent = renderEvidenceTab(run, artifacts); break;
      case 'audit': tabContent = renderAuditTab(run); break;
    }

    drawerContent2.innerHTML = `
      <!-- Header -->
      <div class="drawer-header">
        <div style="display: flex; align-items: flex-start; justify-content: space-between;">
          <div>
            <h2 class="drawer-title">Run Report: ${esc(run.runId)}</h2>
            <div class="drawer-meta">
              ${statusChip}
              <span>${formatDate(run.startedAt)}</span>
            </div>
          </div>
          <button onclick="Reports.closeDrawer()" class="drawer-close-btn">
            <svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/></svg>
          </button>
        </div>
      </div>

      <!-- Tabs -->
      <div class="drawer-tabs">
        ${tabs.map(tab => `
          <button onclick="Reports.switchTab('${tab.id}')" class="drawer-tab ${state.activeTab === tab.id ? 'active' : ''}">
            ${tab.label}
          </button>
        `).join('')}
      </div>

      <!-- Body -->
      <div class="drawer-body">
        ${tabContent}
      </div>
    `;
  }

  function renderOverviewTab(run) {
    return `
      <!-- KPI Strip -->
      <div class="drawer-kpi-grid">
        <div class="drawer-kpi-card drawer-kpi-card-blue">
          <div class="drawer-kpi-value drawer-kpi-value-blue">${run.totals.alerts.toLocaleString()}</div>
          <div class="drawer-kpi-label drawer-kpi-label-blue">Alerts Analyzed</div>
        </div>
        <div class="drawer-kpi-card drawer-kpi-card-purple">
          <div class="drawer-kpi-value drawer-kpi-value-purple">${run.totals.alertGroups}</div>
          <div class="drawer-kpi-label drawer-kpi-label-purple">Alert Groups</div>
        </div>
        <div class="drawer-kpi-card drawer-kpi-card-green">
          <div class="drawer-kpi-value drawer-kpi-value-green">${run.totals.incidents}</div>
          <div class="drawer-kpi-label drawer-kpi-label-green">Incidents Created</div>
        </div>
        <div class="drawer-kpi-card drawer-kpi-card-amber">
          <div class="drawer-kpi-value drawer-kpi-value-amber">${run.totals.criticalOpen}</div>
          <div class="drawer-kpi-label drawer-kpi-label-amber">Critical Open</div>
        </div>
      </div>

      <div class="drawer-metrics-row">
        <div class="drawer-metric-card">
          <div class="drawer-metric-value">${run.totals.mtta}m</div>
          <div class="drawer-metric-label">Mean Time to Acknowledge</div>
        </div>
        <div class="drawer-metric-card">
          <div class="drawer-metric-value">${run.totals.mttr}m</div>
          <div class="drawer-metric-label">Mean Time to Resolve</div>
        </div>
        <div class="drawer-metric-card">
          <div class="drawer-metric-value">${run.totals.noisySuppressed}</div>
          <div class="drawer-metric-label">Noisy Alerts Suppressed</div>
        </div>
      </div>

      <!-- Key Findings -->
      <div class="drawer-section">
        <div class="drawer-section-header">
          <h3 class="drawer-section-title">Key Findings</h3>
        </div>
        <div class="drawer-section-body">
          <div style="display: flex; flex-direction: column; gap: 12px;">
            ${run.notes.map(note => `
              <div class="findings-item">
                <span class="findings-bullet">•</span>
                <span>${esc(note)}</span>
              </div>
            `).join('')}
          </div>
        </div>
      </div>

      <!-- Run Info -->
      <div class="drawer-section">
        <div class="drawer-section-header">
          <h3 class="drawer-section-title">Run Information</h3>
        </div>
        <div class="drawer-section-body">
          <div style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px;">
            <div>
              <div style="font-size: 12px; color: #666666;">Started At</div>
              <div style="font-size: 14px; font-weight: 600; color: #1D1D1D; margin-top: 4px;">${formatDate(run.startedAt)}</div>
            </div>
            <div>
              <div style="font-size: 12px; color: #666666;">Completed At</div>
              <div style="font-size: 14px; font-weight: 600; color: #1D1D1D; margin-top: 4px;">${run.completedAt ? formatDate(run.completedAt) : 'Running...'}</div>
            </div>
            <div>
              <div style="font-size: 12px; color: #666666;">Analysis Window</div>
              <div style="font-size: 14px; font-weight: 600; color: #1D1D1D; margin-top: 4px;">${run.windowMinutes} minutes</div>
            </div>
            <div>
              <div style="font-size: 12px; color: #666666;">Duration</div>
              <div style="font-size: 14px; font-weight: 600; color: #1D1D1D; margin-top: 4px;">${run.completedAt ? formatDuration(run.completedAt - run.startedAt) : 'In progress...'}</div>
            </div>
          </div>
        </div>
      </div>
    `;
  }

  function renderAnalysisTab(run) {
    return `
      <!-- New vs Recurring -->
      <div class="drawer-section">
        <div class="drawer-section-header">
          <h3 class="drawer-section-title">New vs Recurring Incidents</h3>
        </div>
        <div class="drawer-section-body">
          <div class="nvr-grid">
            <div class="nvr-card nvr-card-new">
              <div class="nvr-value nvr-value-new">${run.totals.newCount}</div>
              <div class="nvr-label nvr-label-new">New Incident Patterns</div>
              <div class="nvr-sublabel nvr-sublabel-new">${Math.round(run.totals.newCount / run.totals.incidents * 100)}% of total</div>
            </div>
            <div class="nvr-card nvr-card-recurring">
              <div class="nvr-value nvr-value-recurring">${run.totals.recurringCount}</div>
              <div class="nvr-label nvr-label-recurring">Recurring Issues</div>
              <div class="nvr-sublabel nvr-sublabel-recurring">${Math.round(run.totals.recurringCount / run.totals.incidents * 100)}% of total</div>
            </div>
          </div>

          <h4 style="font-size: 14px; font-weight: 700; color: #374151; margin-bottom: 12px;">Top Fingerprints</h4>
          <div>
            ${run.topFingerprints.map(fp => {
              const sevChip = fp.severity === 'Critical' ? chip('Critical', 'red') :
                              fp.severity === 'High' ? chip('High', 'orange') :
                              chip(fp.severity, 'slate');
              return `
                <div class="fingerprint-item">
                  <div style="flex: 1;">
                    <div class="fingerprint-name">${esc(fp.fingerprint)}</div>
                    <div class="fingerprint-badges">
                      ${sevChip}
                      ${fp.isRecurring ? chip('Recurring', 'amber') : ''}
                    </div>
                  </div>
                  <div class="fingerprint-count">${fp.count}</div>
                </div>
              `;
            }).join('')}
          </div>
        </div>
      </div>

      <!-- Top Impacted Apps -->
      <div class="drawer-section">
        <div class="drawer-section-header">
          <h3 class="drawer-section-title">Top Impacted Applications</h3>
        </div>
        <div style="overflow-x: auto;">
          <table class="data-table">
            <thead>
              <tr>
                <th>Application</th>
                <th>Incidents</th>
                <th>Alerts</th>
                <th>Critical</th>
                <th>High</th>
                <th>Impact Score</th>
              </tr>
            </thead>
            <tbody>
              ${run.topApps.map(app => {
                const impactScore = app.criticalCount * 3 + app.highCount;
                return `
                  <tr>
                    <td style="font-weight: 600;">${esc(app.appName)}</td>
                    <td>${app.incidentCount}</td>
                    <td>${app.alertCount}</td>
                    <td style="color: #D32F2F; font-weight: 600;">${app.criticalCount}</td>
                    <td style="color: #C2410C; font-weight: 600;">${app.highCount}</td>
                    <td>
                      <div class="impact-bar-container">
                        <div class="impact-bar-track">
                          <div class="impact-bar-fill bar-chart-bar" style="width: ${Math.min(impactScore / 20 * 100, 100)}%"></div>
                        </div>
                        <span class="impact-bar-value">${impactScore}</span>
                      </div>
                    </td>
                  </tr>
                `;
              }).join('')}
            </tbody>
          </table>
        </div>
      </div>

      <!-- Source Breakdown -->
      <div class="drawer-section">
        <div class="drawer-section-header">
          <h3 class="drawer-section-title">Alert Sources Breakdown</h3>
        </div>
        <div class="drawer-section-body">
          ${Object.entries(run.sourceBreakdown).map(([source, count]) => {
            const percentage = (count / run.totals.alerts * 100).toFixed(1);
            return `
              <div class="source-bar-item">
                <div class="source-bar-header">
                  <span class="source-bar-label">${esc(source)}</span>
                  <span class="source-bar-value">${count.toLocaleString()} (${percentage}%)</span>
                </div>
                <div class="source-bar-track">
                  <div class="source-bar-fill bar-chart-bar" style="width: ${percentage}%"></div>
                </div>
              </div>
            `;
          }).join('')}
        </div>
      </div>

      <!-- Severity Distribution -->
      <div class="drawer-section">
        <div class="drawer-section-header">
          <h3 class="drawer-section-title">Severity Distribution</h3>
        </div>
        <div class="drawer-section-body">
          <div class="severity-grid">
            ${Object.entries(run.severityBreakdown).map(([severity, count]) => {
              const pct = (count / run.totals.alerts * 100).toFixed(1);
              const cls = severity.toLowerCase();
              return `
                <div class="severity-card severity-card-${cls}">
                  <div class="severity-value severity-value-${cls}">${count.toLocaleString()}</div>
                  <div class="severity-label severity-label-${cls}">${severity}</div>
                  <div class="severity-percent severity-percent-${cls}">${pct}%</div>
                </div>
              `;
            }).join('')}
          </div>
        </div>
      </div>
    `;
  }

  function renderArtifactsTab(run, artifacts) {
    const groupedArtifacts = {
      'Original CSV': artifacts.filter(a => a.type === 'OriginalCSV'),
      'Derived CSVs': artifacts.filter(a => a.type === 'DerivedCSV'),
      'Evidence CSVs': artifacts.filter(a => a.type === 'EvidenceCSV'),
      'Reports': artifacts.filter(a => a.type === 'ReportHTML' || a.type === 'ReportPDF')
    };

    return `
      <div class="artifacts-info-banner">
        <span class="artifacts-info-icon">ℹ️</span>
        <div>
          <div class="artifacts-info-title">Report Pack Artifacts</div>
          <div class="artifacts-info-text">Each analysis run produces a complete report pack including original data, derived outputs, and summary reports. All files are downloadable.</div>
        </div>
      </div>

      ${Object.entries(groupedArtifacts).map(([groupName, groupArtifacts]) => {
        if (groupArtifacts.length === 0) return '';
        const isOriginal = groupName === 'Original CSV';
        
        return `
          <div class="artifacts-group ${isOriginal ? 'artifacts-group-original' : ''}">
            <div class="artifacts-group-header ${isOriginal ? 'artifacts-group-header-original' : ''}">
              <h3 class="artifacts-group-title ${isOriginal ? 'artifacts-group-title-original' : ''}">${groupName}${isOriginal ? ' (Source Data)' : ''}</h3>
              <span class="artifacts-group-count ${isOriginal ? 'artifacts-group-count-original' : ''}">${groupArtifacts.length} file${groupArtifacts.length > 1 ? 's' : ''}</span>
            </div>
            ${groupArtifacts.map(artifact => `
              <div class="artifact-row">
                <div style="flex: 1;">
                  <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px;">
                    <span class="artifact-name">${esc(artifact.fileName)}</span>
                    ${artifact.type === 'OriginalCSV' ? chip('Source', 'teal') : ''}
                  </div>
                  <div class="artifact-meta-grid">
                    <div>
                      <div class="artifact-meta-label">Size</div>
                      <div class="artifact-meta-value">${artifact.sizeKB} KB</div>
                    </div>
                    ${artifact.rowsMock != null ? `
                      <div>
                        <div class="artifact-meta-label">Rows</div>
                        <div class="artifact-meta-value">${artifact.rowsMock.toLocaleString()}</div>
                      </div>
                    ` : ''}
                    <div>
                      <div class="artifact-meta-label">Created</div>
                      <div class="artifact-meta-value">${formatTimeAgo(artifact.createdAt)}</div>
                    </div>
                    <div>
                      <div class="artifact-meta-label">Checksum</div>
                      <div class="artifact-meta-value" style="white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 150px;">${esc(artifact.checksumMock.substring(0, 20))}...</div>
                    </div>
                  </div>
                </div>
                <button onclick="Reports.downloadArtifact('${artifact.id}')" class="artifact-download-btn">
                  ⬇ Download
                </button>
              </div>
            `).join('')}
          </div>
        `;
      }).join('')}
    `;
  }

  function renderEvidenceTab(run, artifacts) {
    const evidenceArtifacts = artifacts.filter(a => a.type === 'EvidenceCSV');
    const evidenceCount = evidenceArtifacts.reduce((sum, a) => sum + (a.rows || 0), 0);

    return `
      <!-- Evidence Summary -->
      <div class="drawer-section">
        <div class="drawer-section-header">
          <h3 class="drawer-section-title">Evidence Collection Summary</h3>
        </div>
        <div class="drawer-section-body">
          <div class="evidence-summary-grid">
            <div class="evidence-summary-card">
              <div class="evidence-summary-value">${evidenceCount}</div>
              <div class="evidence-summary-label">Total Evidence Items</div>
            </div>
            <div class="evidence-summary-card">
              <div class="evidence-summary-value">${run.totals.incidents}</div>
              <div class="evidence-summary-label">Incidents with Evidence</div>
            </div>
            <div class="evidence-summary-card">
              <div class="evidence-summary-value">${evidenceArtifacts.length}</div>
              <div class="evidence-summary-label">Evidence CSV Files</div>
            </div>
          </div>
          <div class="evidence-note">Evidence artifacts are stored in SharePoint and referenced in CSV exports. Download the Evidence CSV for complete reference links.</div>
        </div>
      </div>

      ${evidenceArtifacts.length > 0 ? `
        <div class="drawer-section">
          <div class="drawer-section-header">
            <h3 class="drawer-section-title">Evidence CSV Files</h3>
          </div>
          ${evidenceArtifacts.map(artifact => `
            <div class="artifact-row">
              <div>
                <div class="artifact-name" style="margin-bottom: 4px;">${esc(artifact.fileName)}</div>
                <div style="font-size: 12px; color: #666666;">${artifact.rowsMock} evidence items • ${artifact.sizeKB} KB • ${formatTimeAgo(artifact.createdAt)}</div>
              </div>
              <button onclick="Reports.downloadArtifact('${artifact.id}')" class="artifact-download-btn">⬇ Download</button>
            </div>
          `).join('')}
        </div>
      ` : ''}

      <!-- Evidence Preview -->
      <div class="drawer-section">
        <div class="drawer-section-header">
          <h3 class="drawer-section-title">Evidence Preview (First 5 Items)</h3>
        </div>
        <div class="drawer-section-body">
          <div class="empty-state">Evidence previews are loaded from production artifacts only. Download the evidence CSV artifact when available.</div>
        </div>
      </div>
    `;
  }

  function renderAuditTab(run) {
    const errorCount = run.executionLog.filter(log => log.level === 'ERROR').length;
    const warnCount = run.executionLog.filter(log => log.level === 'WARN').length;

    return `
      <!-- Execution Summary -->
      <div class="drawer-section">
        <div class="drawer-section-header">
          <h3 class="drawer-section-title">Execution Summary</h3>
        </div>
        <div class="drawer-section-body">
          <div class="audit-summary-grid">
            <div class="audit-summary-card">
              <div class="audit-summary-label">Duration</div>
              <div class="audit-summary-value">${run.completedAt ? formatDuration(run.completedAt - run.startedAt) : 'Running...'}</div>
            </div>
            <div class="audit-summary-card">
              <div class="audit-summary-label">Status</div>
              <div class="audit-summary-value" style="margin-top: 4px;">
                ${run.status === 'Completed' ? chip('Completed', 'completed') :
                  run.status === 'Failed' ? chip('Failed', 'failed') :
                  chip('Running', 'running')}
              </div>
            </div>
            <div class="audit-summary-card">
              <div class="audit-summary-label">Errors / Warnings</div>
              <div class="audit-summary-value">${errorCount} / ${warnCount}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- Execution Log -->
      <div class="drawer-section">
        <div class="drawer-section-header">
          <h3 class="drawer-section-title">Execution Log</h3>
        </div>
        <div class="drawer-section-body">
          <div class="execution-log">
            ${run.executionLog.map(log => {
              const timeStr = new Date(log.ts).toLocaleTimeString();
              const levelClass = log.level === 'ERROR' ? 'log-level-error' :
                                 log.level === 'WARN' ? 'log-level-warn' :
                                 'log-level-info';
              return `
                <div class="log-entry">
                  <span class="log-timestamp">[${timeStr}]</span>
                  <span class="${levelClass}">${log.level}</span>
                  ${esc(log.message)}
                </div>
              `;
            }).join('')}
          </div>
        </div>
      </div>
    `;
  }

  // ===== MAIN RENDER =====
  function render() {
    const container = document.getElementById('reports-content') || document.getElementById('page-root') || document.getElementById('content-area');
    if (!container) return;

    const runs = state.filteredRuns;

    container.innerHTML = `
      <div class="kfh-phdr">
        <div class="kfh-phdr-titlewrap"><h1 class="kfh-phdr-title">Reports</h1><span class="kfh-phdr-sub">${runs.length} run${runs.length === 1 ? '' : 's'}</span></div>
        <div class="kfh-phdr-search">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
          <input id="reports-search" type="text" maxlength="120" placeholder="Search reports, runs, or notes…" value="${esc(state.searchQuery)}">
        </div>
        <div class="kfh-phdr-ctrls">
          <button type="button" class="kfh-phdr-btn-primary" onclick="Reports.generatePack()">＋ Run report</button>
        </div>
      </div>
      <!-- Page Content -->
      <div style="padding: 24px;">
        <!-- Filters (minimal) -->
        <div class="filters-card" style="margin-bottom: 16px;">
          <div style="display:flex; align-items:center; gap:10px; flex-wrap:wrap;">
            <span style="font-size: 13px; font-weight: 600; color: var(--text-secondary);">Filters</span>
            <label class="filter-checkbox-label">
              <input type="checkbox" id="filter-critical" class="filter-checkbox" ${state.filters.criticalOpenOnly ? 'checked' : ''}>
              <span style="font-size: 13px; color: var(--text-secondary);">Critical Open &gt; 0</span>
            </label>
            <label class="filter-checkbox-label">
              <input type="checkbox" id="filter-failures" class="filter-checkbox" ${state.filters.hasFailures ? 'checked' : ''}>
              <span style="font-size: 13px; color: var(--text-secondary);">Failed Runs</span>
            </label>
            <label class="filter-checkbox-label">
              <input type="checkbox" id="filter-csv" class="filter-checkbox" ${state.filters.hasOriginalCSV ? 'checked' : ''}>
              <span style="font-size: 13px; color: var(--text-secondary);">Has Original CSV</span>
            </label>
          </div>
        </div>

        <!-- Runs List -->
        <div style="display: flex; flex-direction: column; gap: 16px;">
          ${runs.length === 0 ? `
            <div class="run-card">
              <div class="empty-state">
                <p>No runs match your filters</p>
              </div>
            </div>
          ` : ''}

          ${runs.map(run => {
            const artifacts = getRunArtifacts(run.runId);
            const hasOriginalCSV = artifacts.some(a => a.type === 'OriginalCSV');
            const statusChip = run.status === 'Completed' ? chip('Completed', 'completed') :
                               run.status === 'Failed' ? chip('Failed', 'failed') :
                               chip('Running', 'running');

            return `
              <div class="run-card" onclick="Reports.openDrawer('${run.runId}')">
                <div style="display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 16px; gap: 12px;">
                  <div style="flex: 1; min-width: 240px;">
                    <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 8px; flex-wrap: wrap;">
                      <h3 class="run-id">${esc(run.runId)}</h3>
                      ${statusChip}
                      ${hasOriginalCSV ? chip('Original CSV', 'teal') : ''}
                      ${artifacts.length > 0 ? chip(`${artifacts.length} artifacts`, 'blue') : ''}
                    </div>
                    <div class="run-meta">
                      ${formatDate(run.startedAt)} • ${run.windowMinutes}min window${run.completedAt ? ` • Duration: ${formatDuration(run.completedAt - run.startedAt)}` : ''}
                    </div>
                  </div>
                  <button type="button" class="btn-primary" style="white-space: nowrap;" onclick="event.preventDefault(); event.stopPropagation(); Reports.openDrawer('${run.runId}')">
                    Open report
                  </button>
                </div>

                <!-- Stats Grid -->
                <div class="stats-grid" style="margin-bottom: 16px;">
                  <div class="stat-box">
                    <div class="stat-box-value">${run.totals.alerts.toLocaleString()}</div>
                    <div class="stat-box-label">Alerts</div>
                  </div>
                  <div class="stat-box">
                    <div class="stat-box-value">${run.totals.alertGroups.toLocaleString()}</div>
                    <div class="stat-box-label">Alert Groups</div>
                  </div>
                  <div class="stat-box">
                    <div class="stat-box-value">${run.totals.incidents.toLocaleString()}</div>
                    <div class="stat-box-label">Incidents</div>
                  </div>
                  <div class="stat-box ${run.totals.criticalOpen > 0 ? 'stat-box-critical' : 'stat-box-safe'}">
                    <div class="stat-box-value">${run.totals.criticalOpen}</div>
                    <div class="stat-box-label">Critical Open</div>
                  </div>
                  <div class="stat-box">
                    <div class="stat-box-value" style="font-size: 18px;">${run.totals.mtta}m / ${run.totals.mttr}m</div>
                    <div class="stat-box-label">MTTA / MTTR</div>
                  </div>
                </div>

                <!-- Key Findings Preview -->
                <div style="display: flex; flex-direction: column; gap: 4px;">
                  ${run.notes.slice(0, 2).map(note => `
                    <div class="findings-item">
                      <span class="findings-bullet">•</span>
                      <span>${esc(note)}</span>
                    </div>
                  `).join('')}
                </div>
              </div>
            `;
          }).join('')}
        </div>
      </div>
    `;

    bindEvents();
  }

  function bindEvents() {
    KFHUtils.bindLiveSearch('reports-search', function(value) {
      state.searchQuery = value || '';
      applyFilters();
      render();
    });

    // Time range UI removed; keep default state.timeRange and allow changing it only via code.

    document.getElementById('filter-critical')?.addEventListener('change', e => {
      state.filters.criticalOpenOnly = e.target.checked;
      applyFilters();
      render();
    });

    document.getElementById('filter-failures')?.addEventListener('change', e => {
      state.filters.hasFailures = e.target.checked;
      applyFilters();
      render();
    });

    document.getElementById('filter-csv')?.addEventListener('change', e => {
      state.filters.hasOriginalCSV = e.target.checked;
      applyFilters();
      render();
    });

    document.getElementById('drawer-overlay')?.addEventListener('click', closeDrawer);

  }

  // ===== INIT =====
  async function init() {
    await loadData();
    render();
    console.log('Reports module initialized with KFH Beyond Horizons design');
  }

  // ===== PUBLIC API =====
  return {
    init,
    openDrawer,
    closeDrawer,
    switchTab,
    downloadArtifact: (id) => {
      const artifact = state.artifacts.find(a => a.id === id);
      if (artifact) downloadArtifact(artifact);
    },
    generatePack: generateReportPack,
    exportIndexCSV,
    toast,
    refresh: async () => {
      await loadData();
      render();
      toast('Reports refreshed', 'success');
    }
  };
})();

// Export to global scope for HTML onclick handlers
if (typeof window !== 'undefined') {
  window.Reports = Reports;
}
