/**
 * KFH AIOps Command Center - Shared Layout Component
 * Unified layout with sidebar and header for all pages
 * "Beyond Horizons" Design Identity
 */
window.KFHLayout = (function() {
  'use strict';

  // Navigation configuration - unified across all pages
  const NAV_GROUPS = [
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

  // Default user session (can be overridden)
  let userSession = {
    name: 'Ahmed Al-Rashid',
    initials: 'AR',
    role: 'Platform Admin'
  };

  // Current page ID
  let currentPageId = null;

  /**
   * Initialize the layout
   * @param {string} pageId - The current page ID (e.g., 'dashboard', 'applications')
   * @param {Object} options - Optional configuration
   */
  function init(pageId, options = {}) {
    currentPageId = pageId;

    if (options.user) {
      userSession = { ...userSession, ...options.user };
    }

    // Render sidebar
    const sidebarContainer = document.getElementById('sidebar-container') || document.getElementById('sidebar');
    if (sidebarContainer) {
      sidebarContainer.innerHTML = renderSidebar();
    }

    // Render header if container exists
    const headerContainer = document.getElementById('header-container');
    if (headerContainer && options.headerTitle) {
      headerContainer.innerHTML = renderHeader(options.headerTitle, options.headerActions);
    }

    // Set document title
    if (options.title) {
      document.title = `${options.title} - KFH AIOps Command Center`;
    }
  }

  /**
   * Render the sidebar HTML
   */
  function renderSidebar() {
    // Determine base path based on current location
    const basePath = getBasePath();

    return `
      <!-- Logo Area -->
      <div class="kfh-sidebar-logo">
        <div class="kfh-sidebar-logo-icon">
          <span>K</span>
        </div>
        <div class="kfh-sidebar-logo-text">
          <div class="kfh-sidebar-logo-title">KFH AIOps</div>
          <div class="kfh-sidebar-logo-subtitle">COMMAND CENTER</div>
        </div>
      </div>

      <!-- Navigation -->
      <div class="kfh-sidebar-nav">
        ${NAV_GROUPS.map(group => `
          <div class="kfh-sidebar-group">
            <div class="kfh-sidebar-group-title">${escapeHtml(group.title)}</div>
            <div class="kfh-sidebar-group-items">
              ${group.items.map(item => renderNavItem(item, basePath)).join('')}
            </div>
          </div>
        `).join('')}
      </div>

      <!-- User Profile -->
      <div class="kfh-sidebar-user">
        <div class="kfh-sidebar-user-content">
          <div class="kfh-sidebar-user-avatar">${escapeHtml(userSession.initials)}</div>
          <div class="kfh-sidebar-user-info">
            <div class="kfh-sidebar-user-name">${escapeHtml(userSession.name)}</div>
            <div class="kfh-sidebar-user-role">${escapeHtml(userSession.role)}</div>
          </div>
        </div>
      </div>
    `;
  }

  /**
   * Render a navigation item
   */
  function renderNavItem(item, basePath) {
    const isActive = currentPageId === item.id;
    const href = item.href.startsWith('../') ? basePath + item.href.substring(3) : item.href;
    const activeClass = isActive ? 'active' : '';

    return `
      <a href="${escapeHtml(href)}" 
         class="kfh-sidebar-nav-item ${activeClass}" 
         data-page="${escapeHtml(item.id)}">
        <span class="kfh-sidebar-nav-icon">${item.icon}</span>
        <span class="kfh-sidebar-nav-label">${escapeHtml(item.label)}</span>
      </a>
    `;
  }

  /**
   * Render the header HTML
   */
  function renderHeader(title, actions) {
    return `
      <div class="kfh-header-content">
        <div class="kfh-header-left">
          <h2 class="kfh-header-title">${escapeHtml(title)}</h2>
          <div class="kfh-header-divider"></div>
          <div class="kfh-header-search">
            <svg class="kfh-header-search-icon" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path>
            </svg>
            <input type="text" placeholder="Search (Ctrl+K)" class="kfh-header-search-input" id="global-search">
          </div>
        </div>
        <div class="kfh-header-right">
          ${actions || ''}
        </div>
      </div>
    `;
  }

  /**
   * Get the base path for navigation links
   */
  function getBasePath() {
    const path = window.location.pathname;
    // If we're in /pages/xxx/xxx.html, base path should be '../'
    if (path.includes('/pages/')) {
      return '../';
    }
    return 'pages/';
  }

  /**
   * Escape HTML to prevent XSS
   */
  function escapeHtml(str) {
    if (!str) return '';
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  }

  /**
   * Set user session
   */
  function setUser(user) {
    userSession = { ...userSession, ...user };
  }

  /**
   * Update the active navigation item
   */
  function setActivePage(pageId) {
    currentPageId = pageId;

    // Update active states
    document.querySelectorAll('.kfh-sidebar-nav-item').forEach(item => {
      if (item.dataset.page === pageId) {
        item.classList.add('active');
      } else {
        item.classList.remove('active');
      }
    });
  }

  // Public API
  return {
    init,
    renderSidebar,
    renderHeader,
    setUser,
    setActivePage,
    NAV_GROUPS
  };
})();
