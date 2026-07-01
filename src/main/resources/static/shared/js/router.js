/**
 * KFH AIOps Command Center - Router
 * SPA navigation and page loading
 */
window.Router = (function() {
  'use strict';

  // Current state
  let currentPage = null;
  let currentRoute = null;
  let pageCache = {};
  let loadedScripts = {};
  const SETTINGS_FOCUS_CLASS = 'kfh-settings-focus-mode';

  // Page configuration - each page has a JS file and optional CSS
  const PAGES = {
    'dashboard': {
      js: 'pages/dashboard/dashboard.js',
      css: 'pages/dashboard/dashboard.css',
      title: 'Dashboard',
      usesReactModule: true  // Uses DashboardPage module pattern
    },
    'incidents': {
      js: 'pages/incidents/incidents.js',
      css: 'pages/incidents/incidents.css',
      title: 'Incidents',
      usesReactModule: false  // Self-mounting React component
    },
    'alerts': {
      js: 'pages/alerts/alerts.js',
      css: 'pages/alerts/alerts.css',
      title: 'Alert Explorer',
      usesReactModule: false
    },
    'explorer': {
      js: 'pages/explorer/explorer.js',
      css: 'pages/explorer/explorer.css',
      title: 'Log Explorer',
      usesReactModule: false
    },
    'inventory': {
      js: 'pages/inventory/inventory.js',
      css: 'pages/inventory/inventory.css',
      title: 'Inventory & Infrastructure',
      usesReactModule: false,
      usesJSX: true  // Requires Babel transformation
    },
    'servicemap': {
      js: 'pages/servicemap/servicemap.js',
      css: 'pages/servicemap/servicemap.css',
      title: 'Service Map — Application Topology',
      usesReactModule: false
    },
    'applications': {
      js: 'pages/applications/applications.js',
      css: 'pages/applications/applications.css',
      title: 'Applications',
      usesReactModule: false
    },
    'reports': {
      js: 'pages/reports/reports.js',
      css: 'pages/reports/reports.css',
      title: 'Reports',
      usesReactModule: false
    },
    'connectors': {
      js: 'pages/connectors/connectors.js',
      css: 'pages/connectors/connectors.css',
      title: 'Connections',
      usesReactModule: false,
      sidebarPageId: 'settings' // Connectors now live under Settings; keep Settings highlighted
    },
    'schedules': {
      js: 'pages/schedules/schedules.js',
      css: 'pages/schedules/schedules.css',
      title: 'Schedules',
      usesReactModule: false,
      usesJSX: true  // schedules.js contains JSX and must be Babel-transformed
    },
    'users': {
      js: 'pages/users/users.js',
      css: 'pages/users/users.css',
      title: 'User Management',
      usesReactModule: false
    },
    'settings': {
      js: 'pages/settings/settings.js',
      css: 'pages/settings/settings.css',
      title: 'Settings',
      usesReactModule: false
    },
    'audit': {
      js: 'pages/audit/audit.js',
      css: 'pages/audit/audit.css',
      title: 'Audit Logs',
      usesReactModule: false
    },
    'applicationconfig': {
      js: 'pages/applications/applicationconfig.js',
      css: 'pages/applications/applicationconfig.css',
      title: 'Application Configuration',
      usesReactModule: false,
      sidebarPageId: 'applications' // keep "Applications" highlighted in sidebar
    }
  };

  // Default page
  const DEFAULT_PAGE = 'dashboard';

  // Initialize router
  function init() {
    // Bind click handlers to all nav links
    document.querySelectorAll('[data-page]').forEach(link => {
      link.addEventListener('click', function(e) {
        e.preventDefault();
        const pageId = this.getAttribute('data-page');
        navigate(pageId);
      });
    });

    // Handle browser back/forward
    window.addEventListener('popstate', handlePopState);

    // Handle hash changes
    window.addEventListener('hashchange', function() {
      const hash = window.location.hash.slice(1) || DEFAULT_PAGE;
      navigate(hash, false);
    });

    // Get initial page from URL hash
    const hash = window.location.hash.slice(1) || DEFAULT_PAGE;
    navigate(hash, false);
  }

  // Handle popstate event
  function handlePopState(event) {
    const hash = window.location.hash.slice(1) || DEFAULT_PAGE;
    navigate(hash, false);
  }

  // Navigate to a page
  async function navigate(pageId, pushState = true) {
    // Support query params in hash (e.g. "applications?create=1")
    const rawPageId = String(pageId || '');
    const normalizedPageId = rawPageId.split('?')[0];

    // Allow legacy/typed routes like "schedules.html" in the hash
    const normalizedWithoutHtml = normalizedPageId.toLowerCase().endsWith('.html')
      ? normalizedPageId.slice(0, -'.html'.length)
      : normalizedPageId;

    pageId = normalizedWithoutHtml || DEFAULT_PAGE;

    // Validate page exists
    if (!PAGES[pageId]) {
      console.warn(`[Router] Page not found: ${pageId}, redirecting to dashboard`);
      pageId = DEFAULT_PAGE;
    }

    // Skip only exact same hash route; query-only changes can drive page actions.
    if (currentPage === pageId && currentRoute === rawPageId) return;

    console.log(`[Router] Navigating to: ${pageId}`);

    const pageConfig = PAGES[pageId];
    const contentArea = document.getElementById('content-area');

    // Cleanup previous page's React root if exists
    if (contentArea._reactRoot) {
      try {
        contentArea._reactRoot.unmount();
      } catch (e) {}
      contentArea._reactRoot = null;
    }

    // Also cleanup any React root mounted on the previous page container (#page-root)
    // Some pages (e.g., schedules) store the root on the mount element.
    const existingPageRoot = document.getElementById('page-root');
    if (existingPageRoot && existingPageRoot._reactRoot) {
      try {
        existingPageRoot._reactRoot.unmount();
      } catch (e) {}
      existingPageRoot._reactRoot = null;
    }

    // Also clear any previous page-root content to avoid stale DOM/layout when switching pages
    // (Some self-mounting pages assume a clean container.)
    if (existingPageRoot) {
      existingPageRoot.innerHTML = '';
    }

    // Show loading state only if the swap takes long enough for a user to
    // notice. Wiping the content area to a spinner on every menu click makes
    // navigation feel like a full page reload. With the deferred spinner the
    // previous page stays visible until the new one is ready.
    const loadingTimer = setTimeout(() => showLoading(contentArea), 200);

    // Update URL
    if (pushState) {
      // Preserve any query params in the hash
      window.history.pushState({ page: pageId }, '', `#${rawPageId || pageId}`);
    }

    // Update sidebar active state
    updateSidebarState(pageId);
    updateShellMode(pageId);

    // Update header title
    updateHeaderTitle(pageConfig.title);

    try {
      // Load CSS if not already loaded
      await loadCSS(pageId, pageConfig.css);
      // NOTE: We intentionally do NOT re-append the parity theme CSS on every
      // navigation. Moving an existing <link> element triggers a style
      // recalculation which causes a visible "menu flicker / refresh" effect
      // in the sidebar. loadCSS() now inserts page CSS *before* the parity
      // CSS link so the parity theme remains last-loaded automatically.

      // Check if this page uses the module pattern
      if (pageConfig.usesReactModule) {
        // Load as module with render() function
        if (!pageCache[pageId]) {
          await loadPageModule(pageId, pageConfig.js);
        }

        const pageModule = pageCache[pageId];
        if (pageModule && typeof pageModule.render === 'function') {
          contentArea.innerHTML = '';
          const pageContent = await pageModule.render();

          if (typeof pageContent === 'string') {
            contentArea.innerHTML = pageContent;
          } else if (pageContent instanceof Node) {
            contentArea.appendChild(pageContent);
          }

          if (typeof pageModule.init === 'function') {
            await pageModule.init();
          }
        }
      } else {
        // Self-mounting React component - prepare container and load script
        contentArea.innerHTML = '<div id="page-root" class="h-full"></div>';

        // Check if page uses JSX and needs Babel transformation
        if (pageConfig.usesJSX) {
          await loadJSXScript(pageId, pageConfig.js);
        } else {
          // Load and execute the page script
          await loadPageScript(pageId, pageConfig.js);
        }

        // Page-specific init hooks for non-module pages
        // (Some pages expose a global init() but are loaded dynamically by the router.)
        if (pageId === 'reports' && window.Reports && typeof window.Reports.init === 'function') {
          window.Reports.init();
        }
        if (pageId === 'connectors' && window.Connectors && typeof window.Connectors.init === 'function') {
          window.Connectors.init();
        }
        if (pageId === 'users' && window.Users && typeof window.Users.init === 'function') {
          window.Users.init();
        }
        if (pageId === 'settings' && window.Settings && typeof window.Settings.init === 'function') {
          window.Settings.init();
        }
        if (pageId === 'audit' && window.Audit && typeof window.Audit.init === 'function') {
          window.Audit.init();
        }
        if (pageId === 'schedules') {
          // schedules.js is a self-mounting React page; no explicit init required.
          // This hook is kept for parity and future non-React initialization.
        }
      }

      currentPage = pageId;
      currentRoute = rawPageId || pageId;
      clearTimeout(loadingTimer);
      console.log(`[Router] Successfully loaded: ${pageId}`);
    } catch (error) {
      clearTimeout(loadingTimer);
      console.error(`[Router] Failed to load page: ${pageId}`, error);
      showError(contentArea, pageId, error);
    }
  }

  // Load JSX script with Babel transformation
  async function loadJSXScript(pageId, path) {
    return new Promise(async (resolve, reject) => {
      try {
        // Always remove any previously injected script for this page.
        // JSX pages are injected as inline scripts; re-injecting without removal causes
        // "Identifier 'useState' has already been declared" and similar redeclare errors.
        const existingScript = document.querySelector(`script[data-page="${pageId}"]`);
        if (existingScript) {
          existingScript.remove();
        }
        loadedScripts[pageId] = false;

        // Ensure Tailwind runtime is available for pages that set tailwind.config at runtime.
        // Without this, some pages can throw during evaluation and render only after a full refresh.
        if (!window.tailwind) {
          await loadTailwind();
        }

        // Ensure Babel is loaded
        if (!window.Babel) {
          await loadBabel();
        }

        // Fetch the JSX source code
        const response = await fetch(`${path}?v=${Date.now()}`);
        if (!response.ok) {
          throw new Error(`Failed to fetch ${path}: ${response.status}`);
        }
        const jsxCode = await response.text();

        // Transform JSX to JavaScript using Babel
        const transformed = window.Babel.transform(jsxCode, {
          presets: ['react']
        });

        // Execute the transformed code
        const script = document.createElement('script');
        script.setAttribute('data-page', pageId);
        script.textContent = transformed.code;
        document.head.appendChild(script);

        loadedScripts[pageId] = true;
        resolve();
      } catch (error) {
        console.error(`[Router] Failed to load JSX: ${path}`, error);
        reject(error);
      }
    });
  }

  // Load Babel standalone if not already loaded
  function loadBabel() {
    return new Promise((resolve, reject) => {
      if (window.Babel) {
        resolve();
        return;
      }

      const script = document.createElement('script');
      // Repo vendors Babel as babel-standalone.min.js
      script.src = 'vendor/js/babel-standalone.min.js';
      script.onload = () => {
        console.log('[Router] Babel loaded');
        resolve();
      };
      script.onerror = () => {
        // Fallback for older filename
        const fallback = document.createElement('script');
        fallback.src = 'vendor/js/babel.min.js';
        fallback.onload = () => {
          console.log('[Router] Babel loaded (fallback)');
          resolve();
        };
        fallback.onerror = () => reject(new Error('Failed to load Babel'));
        document.head.appendChild(fallback);
      };
      document.head.appendChild(script);
    });
  }

  function loadTailwind() {
    return new Promise((resolve, reject) => {
      if (window.tailwind) {
        resolve();
        return;
      }
      const script = document.createElement('script');
      // Repo vendors Tailwind runtime as tailwindcss.cdn.js
      script.src = 'vendor/js/tailwindcss.cdn.js';
      script.onload = () => resolve();
      script.onerror = () => {
        // Backward-compatible fallback if filename changes
        const fallback = document.createElement('script');
        fallback.src = 'vendor/js/tailwindcss.js';
        fallback.onload = () => resolve();
        fallback.onerror = () => reject(new Error('Failed to load Tailwind runtime'));
        document.head.appendChild(fallback);
      };
      document.head.appendChild(script);
    });
  }

  // Load CSS file. Inserts the link BEFORE the parity-theme stylesheet
  // (`#kfh-aiops-parity-css`) so the parity theme always stays last in <head>
  // without needing to be moved on every navigation (which caused sidebar
  // style flicker / "menu refresh" feel).
  function loadCSS(pageId, cssPath) {
    return new Promise((resolve) => {
      const linkId = `css-${pageId}`;
      if (document.getElementById(linkId)) {
        resolve();
        return;
      }

      const link = document.createElement('link');
      link.id = linkId;
      link.rel = 'stylesheet';
      link.href = cssPath;
      link.onload = resolve;
      link.onerror = resolve; // Don't fail if CSS doesn't exist

      const parity = document.getElementById('kfh-aiops-parity-css');
      if (parity && parity.parentNode) {
        parity.parentNode.insertBefore(link, parity);
      } else {
        document.head.appendChild(link);
      }
    });
  }

  // Legacy helper retained for backwards compatibility. It now only ensures
  // the parity stylesheet exists; it no longer reorders it on each call.
  function ensureFinalThemeCSS() {
    if (document.getElementById('kfh-aiops-parity-css')) return;
    const link = document.createElement('link');
    link.id = 'kfh-aiops-parity-css';
    link.rel = 'stylesheet';
    link.href = 'shared/css/kfh-aiops-parity.css';
    document.head.appendChild(link);
  }

  // Load page module (for pages that export a module)
  async function loadPageModule(pageId, path) {
    return new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = `${path}?v=${Date.now()}`;
      script.onload = () => {
        const moduleName = `${capitalize(pageId)}Page`;
        if (window[moduleName]) {
          pageCache[pageId] = window[moduleName];
          resolve();
        } else {
          reject(new Error(`Module ${moduleName} not found in ${path}`));
        }
      };
      script.onerror = () => reject(new Error(`Failed to load: ${path}`));
      document.head.appendChild(script);
    });
  }

  // Load self-mounting page script
  async function loadPageScript(pageId, path) {
    return new Promise((resolve, reject) => {
      // Check if already loaded
      if (loadedScripts[pageId]) {
        // Re-execute by re-adding the script
        const existingScript = document.querySelector(`script[data-page="${pageId}"]`);
        if (existingScript) {
          existingScript.remove();
        }
      }

      const script = document.createElement('script');
      script.src = `${path}?v=${Date.now()}`;
      script.setAttribute('data-page', pageId);
      script.onload = () => {
        loadedScripts[pageId] = true;
        resolve();
      };
      script.onerror = () => reject(new Error(`Failed to load: ${path}`));
      document.head.appendChild(script);
    });
  }

  // Show loading state
  function showLoading(container) {
    container.innerHTML = `
      <div style="display: flex; align-items: center; justify-content: center; height: 100%;">
        <div style="text-align: center;">
          <div style="width: 48px; height: 48px; border: 4px solid var(--surface-border); border-top-color: var(--kfh-primary); border-radius: 50%; animation: spin 1s linear infinite; margin: 0 auto 16px;"></div>
          <p style="color: var(--text-muted); font-size: 14px;">Loading...</p>
        </div>
      </div>
    `;
  }

  // Show error state
  function showError(container, pageId, error) {
    const pageConfig = PAGES[pageId];
    const pageTitle = pageConfig?.title || pageId;

    container.innerHTML = `
      <div style="display: flex; align-items: center; justify-content: center; height: 100%;">
        <div style="text-align: center; max-width: 400px; padding: 32px;">
          <div style="width: 80px; height: 80px; background: var(--color-warning-bg, #FEF3C7); border-radius: 16px; display: flex; align-items: center; justify-content: center; margin: 0 auto 24px;">
            <svg style="width: 40px; height: 40px; color: var(--color-warning, #D97706);" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/>
            </svg>
          </div>
          <h2 style="font-size: 24px; font-weight: 700; color: var(--text-primary); margin-bottom: 12px;">
            Failed to Load ${pageTitle}
          </h2>
          <p style="color: var(--text-secondary); margin-bottom: 24px; font-size: 14px;">
            ${error?.message || 'An error occurred while loading this page.'}
          </p>
          <button onclick="Router.navigate('dashboard')" class="kfh-btn kfh-btn-primary">
            Return to Dashboard
          </button>
        </div>
      </div>
    `;
  }

  // Update sidebar active state
  function updateSidebarState(pageId) {
    const config = PAGES[pageId];
    const sidebarId = (config && config.sidebarPageId) ? config.sidebarPageId : pageId;
    document.querySelectorAll('.sidebar-nav-item, .kfh-sidebar-nav-item').forEach(item => {
      item.classList.remove('active');
      item.removeAttribute('aria-current');
    });

    document.querySelectorAll(`[data-page="${sidebarId}"]`).forEach(activeItem => {
      activeItem.classList.add('active');
      activeItem.setAttribute('aria-current', 'page');
    });
  }

  function updateShellMode(pageId) {
    const app = document.getElementById('app');
    const sidebar = document.getElementById('sidebar-container');
    const settingsFocus = pageId === 'settings';

    if (app) {
      app.classList.toggle(SETTINGS_FOCUS_CLASS, settingsFocus);
    }
    if (sidebar) {
      sidebar.classList.toggle(SETTINGS_FOCUS_CLASS, settingsFocus);
      sidebar.setAttribute('aria-hidden', 'false');
    }

    if (window.KFHSidebarCollapse?.setCollapsed) {
      window.KFHSidebarCollapse.setCollapsed(settingsFocus ? true : null, { persist: false });
    }
  }

  // Update header title
  function updateHeaderTitle(title) {
    const headerTitle = document.getElementById('page-title');
    if (headerTitle) {
      headerTitle.textContent = title;
    }
    document.title = `${title} | KFH AIOps`;
  }

  // Helper function
  function capitalize(str) {
    return str.charAt(0).toUpperCase() + str.slice(1);
  }

  // Get current page
  function getCurrentPage() {
    return currentPage;
  }

  function reloadCurrent() {
    const route = currentRoute || (window.location.hash || '').slice(1) || DEFAULT_PAGE;
    currentRoute = null;
    return navigate(route, false);
  }

  // Clear cache
  function clearCache() {
    pageCache = {};
    loadedScripts = {};
  }

  // Public API
  return {
    init,
    navigate,
    reloadCurrent,
    getCurrentPage,
    clearCache
  };
})();
