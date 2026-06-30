(function() {
  'use strict';

  const STORAGE_KEY = 'kfh.aiops.sidebar.collapsed';
  const COLLAPSED_CLASS = 'kfh-sidebar-collapsed';
  let cachedElements = null;

  function init() {
    const app = document.getElementById('app');
    const sidebar = document.getElementById('sidebar-container');
    const toggle = document.getElementById('sidebar-collapse-toggle');
    if (!app || !sidebar || !toggle) return;
    cachedElements = { app, sidebar, toggle };

    applyState(readCollapsed(), cachedElements);
    hydrateNavTitles(sidebar);

    toggle.addEventListener('click', () => {
      const collapsed = !app.classList.contains(COLLAPSED_CLASS);
      localStorage.setItem(STORAGE_KEY, String(collapsed));
      applyState(collapsed, { app, sidebar, toggle });
    });

    window.addEventListener('resize', () => {
      if (window.matchMedia('(max-width: 760px)').matches) {
        applyState(false, { app, sidebar, toggle }, false);
      } else {
        applyState(readCollapsed(), { app, sidebar, toggle }, false);
      }
    });
  }

  function readCollapsed() {
    if (window.matchMedia('(max-width: 760px)').matches) return false;
    return localStorage.getItem(STORAGE_KEY) === 'true';
  }

  function applyState(collapsed, elements, persist = true) {
    const { app, sidebar, toggle } = elements;
    const label = toggle.querySelector('.kfh-sidebar-collapse-label');
    const action = collapsed ? 'Collapse out' : 'Collapse in';
    app.classList.toggle(COLLAPSED_CLASS, collapsed);
    sidebar.classList.toggle(COLLAPSED_CLASS, collapsed);
    sidebar.setAttribute('aria-label', collapsed ? 'Collapsed main navigation' : 'Main navigation');
    toggle.setAttribute('aria-expanded', String(!collapsed));
    toggle.setAttribute('aria-label', action);
    toggle.setAttribute('title', action);
    if (label) label.textContent = action;
    if (persist) localStorage.setItem(STORAGE_KEY, String(collapsed));
  }

  function setCollapsed(collapsed, options = {}) {
    if (!cachedElements) return;
    const persist = options.persist !== false;
    const effectiveCollapsed = collapsed == null ? readCollapsed() : Boolean(collapsed);
    applyState(effectiveCollapsed, cachedElements, persist);
  }

  function hydrateNavTitles(sidebar) {
    sidebar.querySelectorAll('.kfh-sidebar-nav-item').forEach(item => {
      const label = item.querySelector('.kfh-sidebar-nav-label')?.textContent?.trim();
      if (label && !item.getAttribute('title')) {
        item.setAttribute('title', label);
      }
    });
  }

  window.KFHSidebarCollapse = { init, setCollapsed };
  document.addEventListener('DOMContentLoaded', init);
})();
