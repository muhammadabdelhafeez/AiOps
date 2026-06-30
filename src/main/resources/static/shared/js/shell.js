/**
 * KFH AIOps Command Center - Unified Page Shell
 * Creates consistent page layout across all pages
 * Uses the shared CSS classes from kfh-theme.css
 */
window.KFHShell = (function() {
  'use strict';

  /**
   * Generate the complete sidebar HTML
   * @param {string} activePage - The current active page ID
   * @returns {string} HTML string for the sidebar
   */
  function renderSidebar(activePage) {
    const navGroups = [
      {
        title: 'Operations',
        items: [
          { id: 'dashboard', label: 'Dashboard', icon: '📊', href: '../dashboard/dashboard.html' },
          { id: 'incidents', label: 'Incidents', icon: '🔴', href: '../incidents/incidents.html' },
          { id: 'alerts', label: 'Alerts', icon: '⚠️', href: '../alerts/alerts.html' },
          { id: 'inventory', label: 'Inventory', icon: '💾', href: '../inventory/inventory.html' }
        ]
      },
      {
        title: 'Intelligence',
        items: [
          { id: 'applications', label: 'Applications', icon: '📱', href: '../applications/applications.html' },
          { id: 'reports', label: 'Reports', icon: '📄', href: '../reports/reports.html' }
        ]
      },
      {
        title: 'Configuration',
        items: [
          { id: 'connectors', label: 'Connectors', icon: '🔌', href: '../connectors/connectors.html' },
          { id: 'schedules', label: 'Schedules', icon: '⏰', href: '../schedules/schedules.html' },
          { id: 'users', label: 'User Management', icon: '👥', href: '../users/users.html' },
          { id: 'settings', label: 'Settings', icon: '⚙️', href: '../settings/settings.html' },
          { id: 'audit', label: 'Audit Logs', icon: '📋', href: '../audit/audit.html' }
        ]
      }
    ];

    // Fix href for current page (use just the filename)
    const fixHref = (href, itemId) => {
      if (itemId === activePage) {
        const filename = href.split('/').pop();
        return filename;
      }
      return href;
    };

    return `
      <!-- Logo Area -->
      <div class="kfh-sidebar-logo">
        <div class="kfh-sidebar-logo-icon">
          <img src="${window.location.pathname.includes('/pages/') ? '../../' : ''}images/kfh-logo.png" alt="KFH logo" loading="eager" decoding="async">
        </div>
        <div class="kfh-sidebar-logo-text">
          <div class="kfh-sidebar-logo-title">KFH AIOps</div>
          <div class="kfh-sidebar-logo-subtitle">COMMAND CENTER</div>
        </div>
      </div>

      <!-- Navigation -->
      <div class="kfh-sidebar-nav">
        ${navGroups.map(group => `
          <div class="kfh-sidebar-group">
            <div class="kfh-sidebar-group-title">${group.title}</div>
            <div class="kfh-sidebar-group-items">
              ${group.items.map(item => `
                <a href="${fixHref(item.href, item.id)}" class="kfh-sidebar-nav-item ${item.id === activePage ? 'active' : ''}">
                  <span class="kfh-sidebar-nav-icon">${item.icon}</span>
                  <span class="kfh-sidebar-nav-label">${item.label}</span>
                </a>
              `).join('')}
            </div>
          </div>
        `).join('')}
      </div>

      <!-- User Profile -->
      <div class="kfh-sidebar-user">
        <div class="kfh-sidebar-user-content">
          <div class="kfh-sidebar-user-avatar">AR</div>
          <div class="kfh-sidebar-user-info">
            <div class="kfh-sidebar-user-name">Ahmed Al-Rashid</div>
            <div class="kfh-sidebar-user-role">Platform Admin</div>
          </div>
        </div>
      </div>
    `;
  }

  /**
   * Generate the page header HTML
   * @param {Object} options - Header configuration
   * @returns {string} HTML string for the header
   */
  function renderHeader(options = {}) {
    const {
      title = 'Dashboard',
      showSearch = true,
      showStatus = true,
      showNotifications = true,
      customActions = ''
    } = options;

    const searchHtml = showSearch ? `
      <div class="kfh-header-divider"></div>
      <div class="kfh-header-search">
        <svg class="kfh-header-search-icon" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path>
        </svg>
        <input type="text" placeholder="Search (Ctrl+K)" class="kfh-header-search-input" id="global-search">
      </div>
    ` : '';

    const statusHtml = showStatus ? `
      <div class="kfh-status-indicator stable">
        <span class="kfh-status-dot">
          <span class="kfh-status-dot-ping"></span>
          <span class="kfh-status-dot-core"></span>
        </span>
        <span class="kfh-status-label">SYSTEM STABLE</span>
      </div>
    ` : '';

    const notificationsHtml = showNotifications ? `
      <button class="kfh-icon-btn" title="Notifications">
        <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"></path>
        </svg>
        <span class="kfh-icon-btn-badge"></span>
      </button>
    ` : '';

    return `
      <div class="kfh-header-content">
        <div class="kfh-header-left">
          <h2 class="kfh-header-title">${title}</h2>
          ${searchHtml}
        </div>
        <div class="kfh-header-right">
          ${statusHtml}
          ${notificationsHtml}
          ${customActions}
        </div>
      </div>
    `;
  }

  /**
   * Initialize the page shell
   * @param {Object} config - Page configuration
   */
  function init(config = {}) {
    const {
      pageId = 'dashboard',
      pageTitle = 'Dashboard',
      showSearch = true,
      showStatus = true,
      showNotifications = true,
      headerActions = ''
    } = config;

    // Render sidebar
    const sidebar = document.getElementById('sidebar');
    if (sidebar) {
      sidebar.innerHTML = renderSidebar(pageId);
    }

    // Render header
    const header = document.getElementById('page-header');
    if (header) {
      header.innerHTML = renderHeader({
        title: pageTitle,
        showSearch,
        showStatus,
        showNotifications,
        customActions: headerActions
      });
    }

    // Set document title
    document.title = `${pageTitle} - KFH AIOps Command Center`;

    // Setup keyboard shortcut for search
    if (showSearch) {
      document.addEventListener('keydown', (e) => {
        if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
          e.preventDefault();
          const searchInput = document.getElementById('global-search');
          if (searchInput) searchInput.focus();
        }
      });
    }
  }

  // Public API
  return {
    init,
    renderSidebar,
    renderHeader
  };
})();
