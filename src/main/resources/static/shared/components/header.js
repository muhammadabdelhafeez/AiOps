/**
 * KFH AIOps Command Center - Header Component
 * Top navigation bar with search and status
 */
window.Header = (function() {
  'use strict';

  // System status types
  const STATUS_CONFIG = {
    stable: { label: 'SYSTEM STABLE', color: 'var(--kfh-primary)', bgColor: 'var(--kfh-primary-light)' },
    degraded: { label: 'DEGRADED', color: 'var(--color-warning)', bgColor: 'var(--color-warning-bg)' },
    critical: { label: 'CRITICAL', color: 'var(--color-critical)', bgColor: 'var(--color-critical-bg)' }
  };

  let currentStatus = 'stable';
  let notificationCount = 3;

  // Render header
  function render() {
    const status = STATUS_CONFIG[currentStatus];

    return `
      <!-- Search Section -->
      <div class="flex items-center gap-6 w-full max-w-2xl">
        <h2 id="page-title" class="text-2xl font-extrabold whitespace-nowrap tracking-tight" 
            style="color: var(--text-primary);">
          Dashboard
        </h2>
        <div class="h-6 w-px mx-2" style="background: #E5E7EB;"></div>
        <div class="relative w-full group">
          <svg class="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 transition-colors" 
               style="color: var(--text-muted);" 
               fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" 
                  d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
          </svg>
          <input type="text" 
                 id="global-search"
                 placeholder="Search (Ctrl+K)" 
                 class="kfh-input pl-10 pr-4"
                 style="border-radius: 12px;">
        </div>
      </div>

      <!-- Actions Section -->
      <div class="flex items-center gap-4">
        <!-- System Status -->
        <div class="flex items-center gap-2 px-3 py-1.5 rounded-full border shadow-sm"
             style="background: ${status.bgColor}; border-color: ${status.color}20;">
          <span class="relative flex h-2 w-2">
            <span class="absolute inline-flex h-full w-full rounded-full opacity-75 animate-ping"
                  style="background: ${status.color};"></span>
            <span class="relative inline-flex rounded-full h-2 w-2"
                  style="background: ${status.color};"></span>
          </span>
          <span class="text-xs font-bold tracking-wide" style="color: ${status.color};">
            ${status.label}
          </span>
        </div>

        <!-- Refresh Button -->
        <button onclick="Header.refresh()" 
                class="p-2.5 rounded-xl border shadow-sm transition-all"
                style="background: var(--surface-card); border-color: #E5E7EB; color: var(--text-secondary);"
                title="Refresh Data">
          <svg id="refresh-icon" class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" 
                  d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"/>
          </svg>
        </button>

        <!-- Notifications -->
        <button onclick="Header.showNotifications()" 
                class="relative p-2.5 rounded-xl border shadow-sm transition-all"
                style="background: var(--surface-card); border-color: #E5E7EB; color: var(--text-secondary);"
                title="Notifications">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" 
                  d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"/>
          </svg>
          ${notificationCount > 0 ? `
            <span class="absolute top-2 right-2 w-2 h-2 rounded-full border-2"
                  style="background: var(--color-critical); border-color: var(--surface-card);"></span>
          ` : ''}
        </button>

        <!-- Time Range Selector -->
        <select id="time-range-select" 
                class="kfh-input cursor-pointer"
                style="width: auto; min-width: 140px; border-radius: 8px;"
                onchange="Header.onTimeRangeChange(this.value)">
          ${KFHConfig.TIME_RANGES.map(tr => `
            <option value="${tr.id}" ${tr.id === '15d' ? 'selected' : ''}>
              ${tr.label}
            </option>
          `).join('')}
        </select>
      </div>
    `;
  }

  // Initialize header
  async function init() {
    const container = document.getElementById('header-container');
    if (container) {
      container.innerHTML = render();
      setupEventListeners();
    }
  }

  // Setup event listeners
  function setupEventListeners() {
    // Global search shortcut (Ctrl+K)
    document.addEventListener('keydown', (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
        e.preventDefault();
        const searchInput = document.getElementById('global-search');
        if (searchInput) searchInput.focus();
      }
    });

    // Search input handling
    const searchInput = document.getElementById('global-search');
    if (searchInput) {
      searchInput.addEventListener('input', KFHUtils.debounce((e) => {
        const query = e.target.value.trim();
        if (query.length >= 2) {
          performSearch(query);
        }
      }, 300));
    }
  }

  // Perform global search
  function performSearch(query) {
    console.log('Searching for:', query);
    // TODO: Implement global search
  }

  // Refresh data
  function refresh() {
    const icon = document.getElementById('refresh-icon');
    if (icon) {
      icon.classList.add('animate-spin');
      setTimeout(() => {
        icon.classList.remove('animate-spin');
        KFHUtils.showToast('Data refreshed', 'success');
        // Trigger page refresh
        const currentPage = Router.getCurrentPage();
        if (currentPage && window[`${currentPage.charAt(0).toUpperCase() + currentPage.slice(1)}Page`]?.refresh) {
          window[`${currentPage.charAt(0).toUpperCase() + currentPage.slice(1)}Page`].refresh();
        }
      }, 1000);
    }
  }

  // Show notifications panel
  function showNotifications() {
    KFHUtils.showToast('Notifications panel coming soon', 'info');
  }

  // Handle time range change
  function onTimeRangeChange(rangeId) {
    console.log('Time range changed to:', rangeId);
    // Dispatch custom event for pages to listen
    window.dispatchEvent(new CustomEvent('timeRangeChanged', { detail: { rangeId } }));
  }

  // Update system status
  function setStatus(status) {
    if (STATUS_CONFIG[status]) {
      currentStatus = status;
      init(); // Re-render header
    }
  }

  // Update notification count
  function setNotificationCount(count) {
    notificationCount = count;
    init(); // Re-render header
  }

  // Update page title
  function setTitle(title) {
    const titleEl = document.getElementById('page-title');
    if (titleEl) {
      titleEl.textContent = title;
    }
    document.title = `${title} | KFH AIOps`;
  }

  // Get current time range
  function getTimeRange() {
    const select = document.getElementById('time-range-select');
    return select ? select.value : '15d';
  }

  // Public API
  return {
    init,
    render,
    refresh,
    showNotifications,
    onTimeRangeChange,
    setStatus,
    setNotificationCount,
    setTitle,
    getTimeRange
  };
})();
