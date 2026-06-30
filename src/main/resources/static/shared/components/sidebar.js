/**
 * KFH AIOps Command Center - Sidebar Component
 * Navigation sidebar with KFH Beyond Horizons design
 */
window.Sidebar = (function() {
  'use strict';

  // SVG Icons
  const ICONS = {
    dashboard: `<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M4 5a1 1 0 011-1h4a1 1 0 011 1v5a1 1 0 01-1 1H5a1 1 0 01-1-1V5zM14 5a1 1 0 011-1h4a1 1 0 011 1v2a1 1 0 01-1 1h-4a1 1 0 01-1-1V5zM4 14a1 1 0 011-1h4a1 1 0 011 1v5a1 1 0 01-1 1H5a1 1 0 01-1-1v-5zM14 11a1 1 0 011-1h4a1 1 0 011 1v8a1 1 0 01-1 1h-4a1 1 0 01-1-1v-8z"/></svg>`,

    incident: `<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/></svg>`,

    alert: `<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"/></svg>`,

    inventory: `<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M5 12h14M5 12a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v4a2 2 0 01-2 2M5 12a2 2 0 00-2 2v4a2 2 0 002 2h14a2 2 0 002-2v-4a2 2 0 00-2-2m-2-4h.01M17 16h.01"/></svg>`,

    app: `<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M4 5a1 1 0 011-1h14a1 1 0 011 1v2a1 1 0 01-1 1H5a1 1 0 01-1-1V5zM4 13a1 1 0 011-1h6a1 1 0 011 1v6a1 1 0 01-1 1H5a1 1 0 01-1-1v-6zM16 13a1 1 0 011-1h2a1 1 0 011 1v6a1 1 0 01-1 1h-2a1 1 0 01-1-1v-6z"/></svg>`,

    report: `<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 17v-2m3 2v-4m3 4v-6m2 10H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/></svg>`,

    connector: `<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1"/></svg>`,

    schedule: `<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>`,

    users: `<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z"/></svg>`,

    settings: `<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/></svg>`,

    audit: `<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01"/></svg>`
  };

  // Render sidebar
  function render() {
    const session = KFHConfig.getSession();
    const navItems = KFHConfig.NAV_ITEMS;
    const assetBasePath = window.location.pathname.includes('/pages/') ? '../../' : '';

    return `
      <!-- Logo Area -->
      <div class="p-6 border-b" style="border-color: rgba(255,255,255,0.1);">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-xl flex items-center justify-center p-1.5 overflow-hidden"
               style="background: rgba(255,255,255,0.96); border: 1px solid rgba(255,255,255,0.16); box-shadow: 0 10px 24px rgba(0,0,0,0.16);">
            <img src="${assetBasePath}images/kfh-logo.png" alt="KFH logo" loading="eager" decoding="async" class="w-full h-full object-contain">
          </div>
          <div>
            <div class="font-bold text-lg tracking-tight text-white leading-tight">KFH AIOps</div>
            <div class="text-xs font-medium tracking-wide" style="color: var(--kfh-gold);">COMMAND CENTER</div>
          </div>
        </div>
      </div>

      <!-- Navigation -->
      <nav class="flex-1 overflow-y-auto py-6 px-3">
        ${navItems.map(group => `
          <div class="mb-6">
            <div class="px-4 mb-3 text-xs font-bold uppercase tracking-wider" style="color: var(--kfh-gold);">
              ${KFHUtils.escapeHtml(group.group)}
            </div>
            <div class="space-y-1">
              ${group.items.map(item => renderNavItem(item)).join('')}
            </div>
          </div>
        `).join('')}
      </nav>

      <!-- User Profile -->
      <div class="p-4 border-t" style="border-color: rgba(255,255,255,0.1);">
        <div class="flex items-center gap-3 px-3 py-2 rounded-lg" 
             style="background: rgba(0,0,0,0.2); border: 1px solid rgba(255,255,255,0.05);">
          <div class="w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold text-white"
               style="background: linear-gradient(135deg, var(--kfh-gold), var(--kfh-gold-dark));">
            ${KFHUtils.escapeHtml(session.userInitials)}
          </div>
          <div class="overflow-hidden flex-1">
            <div class="text-sm font-bold text-white truncate">
              ${KFHUtils.escapeHtml(session.userName)}
            </div>
            <div class="text-xs truncate uppercase tracking-wide" style="color: rgba(255,255,255,0.6);">
              ${KFHUtils.escapeHtml(session.userRole)}
            </div>
          </div>
        </div>
      </div>
    `;
  }

  // Render single nav item
  function renderNavItem(item) {
    const icon = ICONS[item.icon] || ICONS.dashboard;
    const badgeHtml = item.badge
      ? `<span class="ml-auto px-2 py-0.5 text-xs font-bold rounded-full" 
               style="background: var(--color-critical); color: white;">
           ${item.badge}
         </span>`
      : '';

    return `
      <a href="#${item.id}" 
         onclick="Router.navigate('${item.id}'); return false;"
         class="sidebar-nav-item"
         data-page="${item.id}">
        ${icon}
        <span>${KFHUtils.escapeHtml(item.label)}</span>
        ${badgeHtml}
      </a>
    `;
  }

  // Initialize sidebar
  async function init() {
    const container = document.getElementById('sidebar-container');
    if (container) {
      container.innerHTML = render();
    }
  }

  // Update badge count
  function updateBadge(pageId, count) {
    const navItems = KFHConfig.NAV_ITEMS;
    navItems.forEach(group => {
      const item = group.items.find(i => i.id === pageId);
      if (item) {
        item.badge = count > 0 ? count : null;
      }
    });
    // Re-render sidebar
    init();
  }

  // Public API
  return {
    init,
    render,
    updateBadge
  };
})();
