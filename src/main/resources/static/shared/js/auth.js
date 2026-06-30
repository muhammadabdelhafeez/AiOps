/**
 * KFH AIOps Command Center - Country-aware login/session shell.
 * Phase 1 scaffold: captures tenant/country/user context headers for the backend.
 */
window.KFHAuth = (function() {
  'use strict';

  let onLoginCallback = null;
  let pendingHash = '#dashboard';

  function init(options = {}) {
    onLoginCallback = options.onLogin || null;
    updateShellSession();
    wireLogout();
    wireCountrySwitcher();
    wireScopeChanged();

    if (window.location.hash && window.location.hash !== '#login') {
      pendingHash = window.location.hash;
    }

    if (!KFHConfig.isAuthenticated() || window.location.hash === '#login') {
      showLogin();
      return false;
    }

    hideLogin();
    return true;
  }

  function isAuthenticated() {
    return KFHConfig.isAuthenticated();
  }

  function showLogin() {
    const app = document.getElementById('app');
    const root = document.getElementById('auth-root');
    if (app) app.setAttribute('aria-hidden', 'true');
    if (!root) return;

    root.innerHTML = renderLogin();
    root.hidden = false;
    bindLoginForm();
  }

  function hideLogin() {
    const app = document.getElementById('app');
    const root = document.getElementById('auth-root');
    if (app) app.removeAttribute('aria-hidden');
    if (root) {
      root.hidden = true;
      root.innerHTML = '';
    }
  }

  function renderLogin() {
    const countries = KFHConfig.COUNTRY_SCOPES || KFHConfig.COUNTRIES;
    const defaultCountry = KFHConfig.COUNTRIES[0];

    return `
      <section class="kfh-login-page" aria-label="KFH AIOps login">
        <form id="country-login-form" class="kfh-login-card" autocomplete="off">
          <div class="kfh-login-card-header">
            <div class="kfh-login-logo">KFH</div>
            <p>Beyond Horizons</p>
            <h1>KFH AIOps</h1>
            <span>Sign in with your username and password, then choose the country scope.</span>
          </div>

          <label class="kfh-login-label" for="login-username">Username</label>
          <input id="login-username" class="kfh-login-input" maxlength="80" placeholder="Enter username" autocomplete="username" required autofocus>

          <label class="kfh-login-label" for="login-password">Password</label>
          <input id="login-password" class="kfh-login-input" type="password" placeholder="Enter password" autocomplete="current-password" required>

          <div class="kfh-country-picker">
            <input id="login-country" type="hidden" value="${defaultCountry.code}">
            <button id="country-picker-button" class="kfh-country-button" type="button" aria-haspopup="listbox" aria-expanded="false">
              <span class="kfh-country-flag">${defaultCountry.flag}</span>
              <span class="kfh-country-name">${defaultCountry.name}</span>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M6 9l6 6 6-6"/>
              </svg>
            </button>
            <div id="country-picker-menu" class="kfh-country-menu" role="listbox" hidden>
              ${countries.map(country => `
                <button type="button" class="kfh-country-option" role="option" data-country="${country.code}">
                  <span>${country.flag}</span>
                  <strong>${country.name}</strong>
                </button>
              `).join('')}
            </div>
          </div>

          <p class="kfh-login-note">
            Your country scope and permissions are loaded from the KFH AIOps identity profile.
          </p>

          <button class="kfh-login-submit" type="submit">Sign In</button>
        </form>
      </section>
    `;
  }

  function bindLoginForm() {
    const country = document.getElementById('login-country');
    const countryButton = document.getElementById('country-picker-button');
    const countryMenu = document.getElementById('country-picker-menu');
    const form = document.getElementById('country-login-form');

    function closeCountryMenu() {
      countryMenu.hidden = true;
      countryButton.setAttribute('aria-expanded', 'false');
    }

    function selectCountry(countryCode) {
      const selected = KFHConfig.getCountry(countryCode);
      country.value = selected.code;
      countryButton.querySelector('.kfh-country-flag').textContent = selected.flag;
      countryButton.querySelector('.kfh-country-name').textContent = selected.name;
      closeCountryMenu();
    }

    countryButton.addEventListener('click', function() {
      const isOpen = !countryMenu.hidden;
      countryMenu.hidden = isOpen;
      countryButton.setAttribute('aria-expanded', String(!isOpen));
    });

    countryMenu.querySelectorAll('[data-country]').forEach(option => {
      option.addEventListener('click', function() {
        selectCountry(this.dataset.country);
      });
    });

    document.addEventListener('click', function(event) {
      if (!countryButton.contains(event.target) && !countryMenu.contains(event.target)) {
        closeCountryMenu();
      }
    });

    form.addEventListener('submit', async function(event) {
      event.preventDefault();
      const selectedCountry = KFHConfig.getCountry(country.value);
      const userName = document.getElementById('login-username').value.trim() || 'KFH Operator';
      const passwordInput = document.getElementById('login-password');
      const password = passwordInput.value;
      passwordInput.setCustomValidity('');

      try {
        const response = await fetch('/api/v1/auth/sign-in', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-Correlation-Id': KFHUtils.generateUUID()
          },
          body: JSON.stringify({ username: userName, password, countryCode: selectedCountry.code, environment: 'PROD' })
        });
        if (!response.ok) {
          if (response.status === 404 || response.status === 503) throw new Error('Auth unavailable');
          passwordInput.setCustomValidity('Invalid username, password, country, or disabled account');
          passwordInput.reportValidity();
          return;
        }
        const session = await response.json();
        KFHConfig.setSession({
          tenantId: session.tenantId,
          userId: session.userId,
          countryCode: session.countryCode,
          homeCountryCode: session.countryCode,
          countryName: session.countryName || selectedCountry.name,
          countryGroupName: session.countryGroupName || selectedCountry.groupName,
          environment: session.environment || 'PROD',
          roleId: session.roleId,
          userRole: session.userRole,
          permissions: Array.isArray(session.permissions) ? session.permissions : [],
          userName: session.displayName || session.username || userName,
          userInitials: initials(session.displayName || session.username || userName),
          authenticatedAt: session.authenticatedAt || new Date().toISOString()
        });
        completeLogin();
        return;
      } catch (error) {
        console.warn('[Auth] Database sign-in unavailable; using local scaffold fallback.', error);
      }

      const selectedRole = KFHConfig.getLoginRole('GLOBAL_ADMIN');

      KFHConfig.setSession({
        tenantId: selectedCountry.tenantId,
        userId: selectedCountry.defaultUserId,
        countryCode: selectedCountry.code,
        homeCountryCode: selectedCountry.code,
        countryName: selectedCountry.name,
        environment: 'PROD',
        roleId: selectedRole.id,
        userRole: selectedRole.name,
        permissions: selectedRole.permissions,
        userName,
        userInitials: initials(userName),
        authenticatedAt: new Date().toISOString()
      });

      completeLogin();
    });

    function completeLogin() {
      updateShellSession();
      hideLogin();
      window.location.hash = pendingHash === '#login' ? '#dashboard' : pendingHash;
      if (typeof onLoginCallback === 'function') {
        onLoginCallback();
      }
    }
  }

  function wireLogout() {
    const logoutButton = document.getElementById('logout-btn');
    if (logoutButton && !logoutButton.dataset.bound) {
      logoutButton.dataset.bound = 'true';
      logoutButton.addEventListener('click', logout);
    }
  }

  function wireCountrySwitcher() {
    const button = document.getElementById('country-scope-button');
    const menu = document.getElementById('country-scope-menu');
    if (!button || !menu || button.dataset.bound) return;

    button.dataset.bound = 'true';
    renderCountryMenu(menu);

    button.addEventListener('click', function(event) {
      event.stopPropagation();
      if (!canSwitchCountryScope()) {
        closeCountrySwitcher();
        return;
      }
      const isOpen = !menu.hidden;
      menu.hidden = isOpen;
      button.setAttribute('aria-expanded', String(!isOpen));
    });

    menu.addEventListener('click', function(event) {
      const option = event.target.closest('[data-country-scope]');
      if (!option) return;
      const before = KFHConfig.getSession().countryCode;
      const after = KFHConfig.switchCountryGroup(option.dataset.countryScope);
      closeCountrySwitcher();
      if (after.countryCode === before && option.dataset.countryScope !== before) {
        toast('You do not have permission to switch country group.', 'error');
        return;
      }
      updateShellSession();
      reloadCurrentPage();
      toast(`Switched to ${after.countryName}`, 'success');
    });

    document.addEventListener('click', function(event) {
      if (!button.contains(event.target) && !menu.contains(event.target)) {
        closeCountrySwitcher();
      }
    });
  }

  function wireScopeChanged() {
    if (window.KFHAuthScopeListenerBound) return;
    window.KFHAuthScopeListenerBound = true;
    window.addEventListener('kfh:scope-changed', updateShellSession);
  }

  function renderCountryMenu(menu) {
    const session = KFHConfig.getSession();
    const canSwitch = canSwitchCountryScope(session);
    if (!canSwitch) {
      menu.innerHTML = '';
      menu.hidden = true;
      return;
    }
    const scopes = KFHConfig.COUNTRY_SCOPES || KFHConfig.COUNTRIES;
    menu.classList.add('kfh-scope-menu--modern');
    menu.innerHTML = `
      <div class="kfh-scope-menu-header">
        <span class="kfh-scope-menu-eyebrow">Country Scope</span>
        <span class="kfh-scope-menu-subtitle">Switch the tenant scope used across the platform</span>
      </div>
      <div class="kfh-scope-menu-list" role="presentation">
        ${scopes.map(country => {
          const selected = country.code === session.countryCode;
          return `
            <button type="button"
                    class="kfh-scope-option kfh-scope-option--modern ${selected ? 'is-active' : ''}"
                    data-country-scope="${country.code}"
                    role="option"
                    aria-selected="${selected ? 'true' : 'false'}">
              <span class="kfh-scope-monogram" data-country="${country.code}">${countryMonogram(country)}</span>
              <span class="kfh-scope-option-text">
                <strong>${country.name}</strong>
                <small>${country.allCountries ? 'Enterprise all-country scope' : `${country.groupName} tenant scope`}</small>
              </span>
              <span class="kfh-scope-check" aria-hidden="${selected ? 'false' : 'true'}">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M5 12l5 5L20 7"/>
                </svg>
              </span>
            </button>
          `;
        }).join('')}
      </div>
    `;
  }

  // Returns a small SVG monogram for the country badge inside the dropdown.
  // Each country gets its own accent color so the menu is visually scannable
  // without depending on emoji rendering (which varies across OS/fonts).
  function countryMonogram(country) {
    if (country.allCountries) {
      return `
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <circle cx="12" cy="12" r="9"/>
          <path d="M3 12h18"/>
          <path d="M12 3a14 14 0 0 1 0 18"/>
          <path d="M12 3a14 14 0 0 0 0 18"/>
        </svg>
      `;
    }
    return `<span class="kfh-scope-monogram-text">${(country.code || '').slice(0, 2)}</span>`;
  }

  function closeCountrySwitcher() {
    const button = document.getElementById('country-scope-button');
    const menu = document.getElementById('country-scope-menu');
    if (menu) menu.hidden = true;
    if (button) button.setAttribute('aria-expanded', 'false');
  }

  function logout() {
    KFHConfig.clearSession();
    pendingHash = window.location.hash || '#dashboard';
    window.location.hash = '#login';
    showLogin();
  }

  function updateShellSession() {
    const session = KFHConfig.getSession();
    const country = KFHConfig.getCountry(session.countryCode);
    const canSwitch = canSwitchCountryScope(session);
    setText('sidebar-user-initials', session.userInitials);
    setText('sidebar-user-name', session.userName);
    setText('sidebar-user-role', `${session.countryCode || 'KW'} • ${session.userRole}`);
    setText('session-country-badge', `${country.flag} ${session.countryCode || 'KW'} / ${session.environment || 'PROD'}`);
    setText('session-country-name', session.countryName || country.name);
    const button = document.getElementById('country-scope-button');
    if (button) {
      button.disabled = !canSwitch;
      button.setAttribute('aria-disabled', String(!canSwitch));
      button.setAttribute('aria-label', canSwitch ? 'Switch country group' : 'Country scope locked to assigned country');
      button.title = canSwitch ? 'Switch country group' : 'Country scope is limited to your assigned country';
      const icon = button.querySelector('svg');
      if (icon) icon.hidden = !canSwitch;
    }
    const menu = document.getElementById('country-scope-menu');
    if (menu) renderCountryMenu(menu);
  }

  function canSwitchCountryScope(session) {
    return window.KFHConfig && typeof KFHConfig.canUseGlobalCountryScope === 'function'
      ? KFHConfig.canUseGlobalCountryScope(session)
      : false;
  }

  function reloadCurrentPage() {
    if (window.Router && typeof Router.reloadCurrent === 'function') {
      Router.reloadCurrent();
      return;
    }
    const currentPage = window.Router && typeof Router.getCurrentPage === 'function' ? Router.getCurrentPage() : null;
    const moduleName = currentPage ? currentPage.charAt(0).toUpperCase() + currentPage.slice(1) + 'Page' : null;
    if (moduleName && window[moduleName] && typeof window[moduleName].refresh === 'function') {
      window[moduleName].refresh();
    }
  }

  function toast(message, type) {
    const container = document.getElementById('toast-container') || document.body;
    const node = document.createElement('div');
    node.className = `kfh-toast kfh-toast-${type || 'info'}`;
    node.setAttribute('role', type === 'error' ? 'alert' : 'status');
    node.setAttribute('aria-live', type === 'error' ? 'assertive' : 'polite');

    const text = document.createElement('span');
    text.className = 'kfh-toast-message';
    text.textContent = message;
    node.appendChild(text);

    const close = document.createElement('button');
    close.type = 'button';
    close.className = 'kfh-toast-close';
    close.setAttribute('aria-label', 'Dismiss notification');
    close.innerHTML = '&times;';
    node.appendChild(close);

    const dismiss = () => {
      if (node.classList.contains('is-leaving')) return;
      node.classList.add('is-leaving');
      setTimeout(() => node.remove(), 240);
    };
    close.addEventListener('click', dismiss);

    container.appendChild(node);
    setTimeout(dismiss, 3500);
  }

  function setText(id, value) {
    const element = document.getElementById(id);
    if (element) element.textContent = value || '';
  }

  function initials(name) {
    return String(name || 'KFH Operator')
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map(part => part[0])
      .join('')
      .toUpperCase() || 'KO';
  }

  return {
    init,
    isAuthenticated,
    showLogin,
    logout,
    updateShellSession
  };
})();
