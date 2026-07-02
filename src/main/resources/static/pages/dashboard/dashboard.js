/**
 * KFH AIOps Command Center — Home / Operations Overview (Phase 1).
 * enterprise-grade "Home": KPI tiles, active-problem feed, ingest & source health, incident trend,
 * top impacted applications, and an AI executive summary — all in KFH "Beyond Horizons" colors using
 * the shared kfhx-* design system. Element IDs are preserved so later phases bind live data.
 */
window.DashboardPage = (function () {
  'use strict';

  function tile(label, id, caption, variant) {
    return `
      <div class="kfhx-tile ${variant || ''}">
        <div class="kfhx-tile-label">${label}</div>
        <div class="kfhx-tile-value" id="${id}">—</div>
        <div class="kfhx-tile-delta" style="color: var(--text-muted); font-weight: 600;">${caption}</div>
      </div>`;
  }

  function emptyState(text) {
    return `<div style="padding: 28px 12px; text-align: center; color: var(--text-muted); font-size: 0.82rem;">${text}</div>`;
  }

  function render() {
    return `
      <!-- Unified slim page header (full-width; shared .kfh-phdr* styles) -->
      <div class="kfh-phdr">
        <div class="kfh-phdr-titlewrap">
          <h1 class="kfh-phdr-title">Operations Overview</h1>
          <span class="kfh-phdr-sub">Real-time correlation across BMC, SCOM and connected sources</span>
        </div>
        <div class="kfh-phdr-search">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
          <input id="dashboard-q" type="text" placeholder="Search logs — press Enter to open Log Explorer…">
        </div>
        <div class="kfh-phdr-ctrls">
          <span class="kfhx-badge info"><span class="kfhx-dot"></span> Live</span>
        </div>
      </div>

      <div class="kfhx-page animate-fade-in">

        <!-- KPI tiles -->
        <div class="kfhx-tiles">
          ${tile('Open Critical', 'dashboard-critical', 'Immediate attention', 'is-critical')}
          ${tile('New Incidents', 'dashboard-new-incidents', 'Last 24 hours', '')}
          ${tile('Recurring', 'dashboard-recurring', 'Known patterns', '')}
          ${tile('Alert Groups', 'dashboard-alert-groups', 'Correlated', 'is-warning')}
          ${tile('Ingested (24h)', 'dashboard-ingested', 'BMC + SCOM events', 'is-success')}
          ${tile('MTTR', 'dashboard-mttr', 'Mean time to resolve', '')}
        </div>

        <!-- Split: problem feed + source health -->
        <div class="kfhx-split" style="margin-bottom: 14px;">
          <div class="kfhx-panel">
            <div class="kfhx-section-head">
              <div class="kfhx-section-title">Active Problems</div>
              <a href="#incidents" data-page="incidents" class="kfhx-section-sub" style="color: var(--kfh-primary); font-weight: 700; text-decoration: none;">View all →</a>
            </div>
            <div id="dashboard-problem-feed">
              ${emptyState('No active problems in the selected range.<br>Correlated incidents appear here once RCA (Phase 3) is enabled.')}
            </div>
          </div>

          <div class="kfhx-panel">
            <div class="kfhx-section-head">
              <div class="kfhx-section-title">Ingest &amp; Source Health</div>
            </div>
            <div id="dashboard-source-breakdown">
              ${emptyState('No source data returned yet.')}
            </div>
          </div>
        </div>

        <!-- Row: trend + top apps -->
        <div class="kfhx-split" style="grid-template-columns: 1fr 1fr;">
          <div class="kfhx-panel">
            <div class="kfhx-section-head">
              <div class="kfhx-section-title">Incident Trend (15 days)</div>
              <div style="display:flex; gap:14px; font-size:0.72rem; color: var(--text-muted);">
                <span style="display:flex; align-items:center; gap:6px;"><span style="width:10px;height:10px;border-radius:2px;background:var(--kfh-primary);"></span>New</span>
                <span style="display:flex; align-items:center; gap:6px;"><span style="width:10px;height:10px;border-radius:2px;background:var(--kfh-gold);"></span>Recurring</span>
              </div>
            </div>
            <div id="trend-bars" style="display:flex; align-items:flex-end; gap:4px; height:150px;"></div>
            <div style="display:flex; justify-content:space-between; margin-top:10px; font-size:0.72rem; color: var(--text-muted);">
              <span>15 days ago</span><span>Today</span>
            </div>
          </div>

          <div class="kfhx-panel">
            <div class="kfhx-section-head">
              <div class="kfhx-section-title">Top Impacted Applications</div>
              <a href="#servicemap" data-page="servicemap" class="kfhx-section-sub" style="color: var(--kfh-primary); font-weight: 700; text-decoration: none;">Service Map →</a>
            </div>
            <div id="dashboard-top-apps">
              ${emptyState('No impacted applications yet.')}
            </div>
          </div>
        </div>

        <!-- AI executive summary -->
        <div class="kfhx-panel" style="margin-top: 14px;">
          <div class="kfhx-section-head">
            <div class="kfhx-section-title">AI Executive Summary</div>
            <span class="kfhx-badge info">DeepSeek → Azure</span>
          </div>
          <div id="dashboard-ai-summary" style="padding: 14px 16px; background: var(--surface-off-white); border-radius: 10px; border-left: 3px solid var(--kfh-primary); font-size: 0.86rem; line-height: 1.6; color: var(--text-secondary);">
            The AI executive summary will appear here once the AI-led RCA stage (Phase 4) is enabled — a grounded, evidence-cited narrative of the period's most impactful incidents and their root causes.
          </div>
        </div>
      </div>`;
  }

  /** Source health row (used when live data arrives). */
  function renderSourceItem(name, percent, color) {
    return `
      <div style="margin-bottom: 12px;">
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:6px;">
          <span style="display:flex; align-items:center; gap:8px; font-size:0.82rem; font-weight:600; color: var(--text-primary);">
            <span class="kfhx-dot" style="background:${color};"></span>${name}
          </span>
          <span style="font-size:0.82rem; font-weight:700; color: var(--text-primary);">${percent}%</span>
        </div>
        <div class="progress-bar"><div class="progress-bar-fill" style="width:${percent}%; background:${color};"></div></div>
      </div>`;
  }

  /** Impacted-application row (used when live data arrives). */
  function renderAppItem(initials, name, domain, badges) {
    const badgeHtml = (badges || []).map(b => `<span class="kfh-chip kfh-chip-${b.type}">${b.label}</span>`).join('');
    return `
      <div class="kfhx-entity">
        <div class="kfhx-entity-rail"></div>
        <div class="kfhx-entity-body">
          <div class="kfhx-entity-head"><span class="kfhx-entity-title">${name}</span></div>
          <div class="kfhx-entity-meta">${domain}</div>
        </div>
        <div class="kfhx-entity-side">${badgeHtml}</div>
      </div>`;
  }

  function init() {
    generateTrendBars();
    wireHeaderSearch();
    console.log('[AIOps] Home initialized');
  }

  /** Header search routes to the Log Explorer on Enter. */
  function wireHeaderSearch() {
    var q = document.getElementById('dashboard-q');
    if (!q) return;
    q.addEventListener('keydown', function (e) {
      if (e.key !== 'Enter') return;
      var val = q.value.trim();
      if (window.Router) Router.navigate('explorer?q=' + encodeURIComponent(val));
    });
  }

  function generateTrendBars() {
    const trendChart = document.getElementById('trend-bars');
    if (!trendChart) return;
    const bars = [];
    for (let i = 0; i < 15; i++) {
      bars.push(`
        <div style="flex:1; display:flex; flex-direction:column; gap:2px; align-items:center;">
          <div style="width:100%; max-width:22px; display:flex; flex-direction:column; gap:2px; align-items:center;">
            <div style="width:100%; height:4px; background: var(--surface-border); border-radius:3px 3px 0 0;"></div>
            <div style="width:100%; height:4px; background: var(--surface-border); border-radius:0 0 3px 3px;"></div>
          </div>
        </div>`);
    }
    trendChart.innerHTML = bars.join('');
  }

  function refresh() {
    generateTrendBars();
    if (window.KFHUtils && KFHUtils.showToast) {
      KFHUtils.showToast('Home refreshed', 'success');
    }
  }

  return { render, init, refresh };
})();

// Backwards compatibility alias.
window.Dashboard = window.DashboardPage;
