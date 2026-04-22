/**
 * KFH AIOps Command Center - Dashboard Page Module
 * SPA Page Module for the Dashboard
 */
window.DashboardPage = (function() {
  'use strict';

  /**
   * Render the dashboard content
   * @returns {string} HTML content
   */
  function render() {
    return `
      <div class="animate-fade-in">
        <!-- Page Header -->
        <div class="flex items-center justify-between mb-8">
          <div>
            <h1 class="text-3xl font-bold tracking-tight" style="color: var(--text-primary);">Operations Overview</h1>
            <p style="color: var(--text-secondary); font-size: 0.875rem; margin-top: 0.25rem;">Real-time monitoring across all connected sources</p>
          </div>
        </div>

        <!-- KPI Cards -->
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
          <!-- New Incidents -->
          <div class="kfh-card kfh-card-interactive p-6">
            <div class="flex items-center justify-between mb-4">
              <span style="font-size: 0.75rem; font-weight: 700; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.05em;">New Incidents</span>
              <span class="kfh-chip kfh-chip-danger">+12%</span>
            </div>
            <div style="font-size: 2.25rem; font-weight: 800; color: var(--text-primary);">24</div>
            <div style="font-size: 0.75rem; color: var(--text-muted); margin-top: 0.5rem;">Vs. previous period</div>
          </div>

          <!-- Recurring -->
          <div class="kfh-card kfh-card-interactive p-6">
            <div class="flex items-center justify-between mb-4">
              <span style="font-size: 0.75rem; font-weight: 700; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.05em;">Recurring</span>
              <span class="kfh-chip kfh-chip-recurring">Pattern</span>
            </div>
            <div style="font-size: 2.25rem; font-weight: 800; color: var(--text-primary);">8</div>
            <div style="font-size: 0.75rem; color: var(--text-muted); margin-top: 0.5rem;">Detected by AI Engine</div>
          </div>

          <!-- Open Critical -->
          <div class="kfh-card kfh-card-interactive p-6" style="border-left: 4px solid var(--color-critical);">
            <div class="flex items-center justify-between mb-4">
              <span style="font-size: 0.75rem; font-weight: 700; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.05em;">Open Critical</span>
              <svg style="width: 1.25rem; height: 1.25rem; color: var(--color-critical);" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path>
              </svg>
            </div>
            <div style="font-size: 2.25rem; font-weight: 800; color: var(--color-critical);">3</div>
            <div style="font-size: 0.75rem; color: var(--text-muted); margin-top: 0.5rem;">Requiring immediate attention</div>
          </div>

          <!-- Alert Groups -->
          <div class="kfh-card kfh-card-interactive p-6">
            <div class="flex items-center justify-between mb-4">
              <span style="font-size: 0.75rem; font-weight: 700; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.05em;">Alert Groups</span>
              <span class="kfh-chip kfh-chip-default">Correlated</span>
            </div>
            <div style="font-size: 2.25rem; font-weight: 800; color: var(--text-primary);">142</div>
            <div style="font-size: 0.75rem; color: var(--text-muted); margin-top: 0.5rem;">Reduced from 15,240 raw alerts</div>
          </div>
        </div>

        <!-- Charts Row -->
        <div class="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-8">
          <!-- Trend Chart -->
          <div class="lg:col-span-2 kfh-card p-6">
            <div class="flex items-center justify-between mb-6">
              <h3 style="font-weight: 700; font-size: 1.125rem; color: var(--text-primary);">Incident Trend (15 Days)</h3>
              <div style="display: flex; align-items: center; gap: 1rem; font-size: 0.75rem;">
                <span style="display: flex; align-items: center; gap: 0.5rem;">
                  <span style="width: 12px; height: 12px; border-radius: 50%; background: var(--kfh-primary);"></span> New
                </span>
                <span style="display: flex; align-items: center; gap: 0.5rem;">
                  <span style="width: 12px; height: 12px; border-radius: 50%; background: var(--kfh-gold);"></span> Recurring
                </span>
              </div>
            </div>
            <div id="trend-bars" style="display: flex; align-items: flex-end; gap: 4px; height: 160px;"></div>
            <div style="display: flex; justify-content: space-between; margin-top: 1rem; font-size: 0.75rem; color: var(--text-muted);">
              <span>15 days ago</span>
              <span>Today</span>
            </div>
          </div>

          <!-- Source Breakdown -->
          <div class="kfh-card p-6">
            <h3 style="font-weight: 700; font-size: 1.125rem; color: var(--text-primary); margin-bottom: 1.5rem;">Source Breakdown</h3>
            <div style="display: flex; flex-direction: column; gap: 1.25rem;">
              ${renderSourceItem('SCOM', 42, 'var(--kfh-primary)')}
              ${renderSourceItem('vROps', 28, 'var(--kfh-primary-dark)')}
              ${renderSourceItem('Elastic', 18, 'var(--kfh-gold)')}
              ${renderSourceItem('SolarWinds', 12, 'var(--kfh-gold-dark)')}
            </div>
          </div>
        </div>

        <!-- Bottom Row -->
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <!-- Top Impacted Apps -->
          <div class="kfh-card p-6">
            <div class="flex items-center justify-between mb-6">
              <h3 style="font-weight: 700; font-size: 1.125rem; color: var(--text-primary);">Top Impacted Applications</h3>
              <a href="pages/applications/applications.html" 
                 style="font-size: 0.75rem; color: var(--kfh-primary); font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em; text-decoration: none;">
                View All
              </a>
            </div>
            <div style="display: flex; flex-direction: column; gap: 1rem;">
              ${renderAppItem('CB', 'Core Banking', 'Infrastructure', [{ label: '5 Critical', type: 'critical' }, { label: '3 High', type: 'high' }])}
              ${renderAppItem('PG', 'Payment Gateway', 'Payments', [{ label: '4 High', type: 'high' }, { label: '2 Med', type: 'medium' }])}
              ${renderAppItem('MB', 'Mobile Banking', 'Digital', [{ label: '3 Med', type: 'medium' }, { label: '5 Low', type: 'low' }])}
            </div>
          </div>

          <!-- Last Hour Summary -->
          <div class="kfh-card p-6" style="position: relative; overflow: hidden;">
            <div style="position: absolute; top: 0; right: 0; width: 200px; height: 200px; background: linear-gradient(135deg, var(--kfh-primary-light) 0%, transparent 70%); border-radius: 0 0 0 100%; opacity: 0.5;"></div>
            <div class="flex items-center justify-between mb-6" style="position: relative; z-index: 1;">
              <h3 style="font-weight: 700; font-size: 1.125rem; color: var(--text-primary);">Last Hour Summary</h3>
              <span style="display: flex; align-items: center; gap: 0.5rem; padding: 0.375rem 0.75rem; background: var(--kfh-primary); color: white; border-radius: 9999px; font-size: 0.75rem; font-weight: 600;">
                <svg style="width: 1rem; height: 1rem;" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"></path>
                </svg>
                GPT-5.2-Pro
              </span>
            </div>
            <div style="padding: 1rem; background: var(--surface-off-white); border-radius: 12px; border-left: 4px solid var(--kfh-primary); position: relative; z-index: 1;">
              <p style="font-size: 0.875rem; color: var(--text-primary); line-height: 1.6;">
                No critical incidents in the last hour. System health is stable across all monitored infrastructure. 
                Minor latency detected in payment gateway (UAT) but within SLA limits.
              </p>
            </div>
            <div style="display: flex; align-items: center; gap: 1rem; margin-top: 1rem; font-size: 0.75rem; color: var(--text-muted); position: relative; z-index: 1;">
              <span style="display: flex; align-items: center; gap: 0.375rem; color: var(--kfh-primary);">
                <svg style="width: 1rem; height: 1rem;" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                </svg>
                Confidence: 94%
              </span>
              <span style="width: 4px; height: 4px; background: var(--surface-border); border-radius: 50%;"></span>
              <span>Evidence: 3 CSVs, 14 Logs</span>
            </div>
          </div>
        </div>
      </div>
    `;
  }

  /**
   * Render source item
   */
  function renderSourceItem(name, percent, color) {
    return `
      <div>
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.5rem;">
          <span style="display: flex; align-items: center; gap: 0.5rem;">
            <span style="width: 8px; height: 8px; border-radius: 50%; background: ${color};"></span>
            <span style="font-size: 0.875rem; font-weight: 500; color: var(--text-primary);">${name}</span>
          </span>
          <span style="font-size: 0.875rem; font-weight: 700; color: var(--text-primary);">${percent}%</span>
        </div>
        <div style="height: 8px; background: var(--surface-off-white); border-radius: 4px; overflow: hidden;">
          <div style="height: 100%; width: ${percent}%; background: ${color}; border-radius: 4px; transition: width 0.3s ease;"></div>
        </div>
      </div>
    `;
  }

  /**
   * Render app item
   */
  function renderAppItem(initials, name, domain, badges) {
    const badgeHtml = badges.map(b => `<span class="kfh-chip kfh-chip-${b.type}">${b.label}</span>`).join('');
    return `
      <div style="display: flex; justify-content: space-between; align-items: center; padding: 1rem; background: var(--surface-off-white); border-radius: 12px;">
        <div style="display: flex; align-items: center; gap: 0.75rem;">
          <div style="width: 40px; height: 40px; background: linear-gradient(135deg, var(--kfh-primary), var(--kfh-primary-dark)); border-radius: 10px; display: flex; align-items: center; justify-content: center; color: white; font-weight: 700; font-size: 0.875rem;">${initials}</div>
          <div>
            <div style="font-weight: 600; color: var(--text-primary);">${name}</div>
            <div style="font-size: 0.75rem; color: var(--text-muted);">${domain}</div>
          </div>
        </div>
        <div style="display: flex; gap: 0.5rem;">${badgeHtml}</div>
      </div>
    `;
  }

  /**
   * Initialize the dashboard after rendering
   */
  function init() {
    generateTrendBars();
    console.log('[AIOps] Dashboard initialized');
  }

  /**
   * Generate trend chart bars
   */
  function generateTrendBars() {
    const trendChart = document.getElementById('trend-bars');
    if (!trendChart) return;

    const bars = [];
    for (let i = 0; i < 15; i++) {
      const newHeight = Math.floor(Math.random() * 50) + 20;
      const recurringHeight = Math.floor(Math.random() * 30) + 10;

      bars.push(`
        <div style="flex: 1; display: flex; flex-direction: column; gap: 2px; align-items: center;">
          <div style="width: 100%; max-width: 24px; display: flex; flex-direction: column; gap: 2px; align-items: center;">
            <div style="width: 100%; height: ${newHeight}px; background: var(--kfh-primary); border-radius: 4px 4px 0 0;"></div>
            <div style="width: 100%; height: ${recurringHeight}px; background: var(--kfh-gold); border-radius: 0 0 4px 4px;"></div>
          </div>
        </div>
      `);
    }

    trendChart.innerHTML = bars.join('');
  }

  /**
   * Refresh the dashboard data
   */
  function refresh() {
    generateTrendBars();
    if (window.KFHUtils && KFHUtils.showToast) {
      KFHUtils.showToast('Dashboard refreshed', 'success');
    }
    console.log('[AIOps] Dashboard refreshed');
  }

  // Public API - The router expects these methods
  return {
    render,
    init,
    refresh
  };
})();

// Also keep the old Dashboard object for backwards compatibility
window.Dashboard = window.DashboardPage;
