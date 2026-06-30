/**
 * KFH AIOps Command Center - User Management Module
 * Country-scoped user creation, listing, filtering, and administration.
 */
var Users = (function() {
  'use strict';
  let users = [];
  let roles = [];
  let selectedUser = null;
  let searchQuery = '';
  let countryFilter = '';
  let roleFilter = '';
  let isApiBacked = false;
  let createModalOpen = false;
  let isSavingUser = false;
  let editUserId = null;
  let isUpdatingUser = false;
  let resetPasswordUserId = null;
  let isResettingPassword = false;
  const ALL_COUNTRIES = 'ALL';
  const ROLE_CHOICES = [
    { id: 'ADMIN', name: 'Admin' },
    { id: 'OPERATOR', name: 'Operator' },
    { id: 'VIEWER', name: 'Viewer' }
  ];
  const genId = () => (window.crypto && window.crypto.randomUUID ? window.crypto.randomUUID() : String(Date.now()));
  const esc = value => String(value || '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  function scope() {
    const session = KFHConfig.getSession();
    return {
      countryCode: session.countryCode || 'KW',
      countryName: session.countryName || 'KFH Kuwait',
      environment: session.environment || 'PROD'
    };
  }
  function availableCountries() {
    if (canViewAllCountries()) {
      return (window.KFHConfig && Array.isArray(KFHConfig.COUNTRIES))
        ? KFHConfig.COUNTRIES
        : [currentCountryOption()];
    }
    return [currentCountryOption()];
  }
  function canViewAllCountries() {
    const session = window.KFHConfig && typeof KFHConfig.getSession === 'function' ? KFHConfig.getSession() : {};
    if (window.KFHConfig && typeof KFHConfig.canUseGlobalCountryScope === 'function') {
      return KFHConfig.canUseGlobalCountryScope(session);
    }
    const permissions = Array.isArray(session.permissions) ? session.permissions : [];
    const homeCountryCode = String(session.homeCountryCode || session.assignedCountryCode || session.countryCode || '').toUpperCase();
    return homeCountryCode === ALL_COUNTRIES && (permissions.includes('*') || permissions.includes('COUNTRY_GLOBAL_VIEW'));
  }
  function countryOptions(includeAll) {
    const countries = availableCountries();
    return includeAll && canViewAllCountries() ? [{ code: ALL_COUNTRIES, name: 'All countries' }, ...countries] : countries;
  }
  function countryByCode(countryCode) {
    const code = String(countryCode || '').toUpperCase();
    return countryOptions(true).find(country => country.code === code) || currentCountryOption();
  }
  function currentCountryOption() {
    const current = scope();
    const scopes = window.KFHConfig && Array.isArray(KFHConfig.COUNTRY_SCOPES) ? KFHConfig.COUNTRY_SCOPES : [];
    return scopes.find(country => country.code === String(current.countryCode || '').toUpperCase())
      || { code: current.countryCode, name: current.countryName, groupName: 'KFH Group' };
  }
  function roleCode(value) {
    const match = roles.find(role => role.id === value || role.name === value);
    const raw = match ? match.name : value;
    return String(raw || '').toUpperCase();
  }
  function roleChoiceForToken(value) {
    const code = roleCode(value);
    if (code === 'GLOBAL_ADMIN' || code === 'COUNTRY_ADMIN' || code === 'ADMIN') return 'ADMIN';
    if (code === 'NOC_OPERATOR' || code === 'OPERATOR') return 'OPERATOR';
    return 'VIEWER';
  }
  function roleDisplayName(value) {
    const choice = ROLE_CHOICES.find(item => item.id === roleChoiceForToken(value));
    return choice ? choice.name : 'Viewer';
  }
  function scopedRoleToken(choice, countryCode) {
    const selected = String(choice || 'VIEWER').toUpperCase();
    if (selected === 'ADMIN') return String(countryCode || '').toUpperCase() === ALL_COUNTRIES ? 'GLOBAL_ADMIN' : 'COUNTRY_ADMIN';
    if (selected === 'OPERATOR') return 'NOC_OPERATOR';
    return 'VIEWER';
  }
  function defaultRoleChoiceForCountry(countryCode) {
    if (String(countryCode || '').toUpperCase() === ALL_COUNTRIES) {
      return 'ADMIN';
    }
    return 'OPERATOR';
  }
  function selectedCreateCountryCode() {
    return countryFilter || scope().countryCode;
  }
  async function withCountryScope(countryCode, operation) {
    if (!window.KFHConfig || typeof KFHConfig.switchCountryGroup !== 'function') return operation();
    const originalSession = KFHConfig.getSession();
    const requested = countryByCode(countryCode).code;
    KFHConfig.switchCountryGroup(requested);
    try {
      return await operation();
    } finally {
      KFHConfig.setSession(originalSession);
      if (window.KFHAuth && typeof KFHAuth.updateShellSession === 'function') KFHAuth.updateShellSession();
    }
  }
  function pageContent(response) {
    return response && Array.isArray(response.content) ? response.content : Array.isArray(response) ? response : [];
  }
  function requireApiClient() {
    if (!window.APIClient || !APIClient.users) {
      throw new Error('User Management API client is not loaded');
    }
    return APIClient;
  }
  function normalizeRole(row) {
    return {
      id: String(row.id || row.name || genId()),
      name: String(row.name || row.roleName || 'Role'),
      description: String(row.description || row.title || 'Identity role'),
      permissions: Array.isArray(row.permissions) ? row.permissions : []
    };
  }
  function normalizeUser(row) {
    const roleNames = Array.isArray(row.roleIds) ? row.roleIds : Array.isArray(row.roles) ? row.roles : [];
    const roleIds = roleNames.map(roleName => {
      const match = roles.find(role => role.id === roleName || role.name === roleName);
      return match ? match.id : roleName;
    });
    const displayName = row.displayName || row.name || row.username || 'KFH User';
    return {
      id: String(row.id || `user-${genId()}`),
      displayName,
      username: row.username || String(displayName).toLowerCase().replace(/[^a-z0-9]+/g, '.').replace(/^\.|\.$/g, ''),
      email: row.email || '',
      status: String(row.status || 'ACTIVE').toUpperCase() === 'DISABLED' ? 'Disabled' : 'Active',
      phone: row.phone || '',
      title: row.title || '',
      teams: Array.isArray(row.teams) ? row.teams : [],
      roleIds,
      countryCode: row.countryCode || scope().countryCode,
      environment: row.environment || scope().environment,
      lastLoginAt: row.lastLoginAt || null,
      createdAt: row.createdAt || new Date().toISOString()
    };
  }
  function getInitials(name) {
    return String(name || 'User').split(' ').map(part => part[0]).join('').toUpperCase().substring(0, 2);
  }
  function formatDateTime(dateStr) {
    if (!dateStr) return 'Never';
    return new Date(dateStr).toLocaleString('en-US', { month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit' });
  }
  function formatRelativeTime(dateStr) {
    if (!dateStr) return 'Never';
    const diff = Date.now() - new Date(dateStr).getTime();
    if (diff < 60000) return 'Just now';
    if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`;
    if (diff < 86400000) return `${Math.floor(diff / 3600000)}h ago`;
    return `${Math.floor(diff / 86400000)}d ago`;
  }
  function getUserRoles(userId) {
    const user = users.find(item => item.id === userId);
    if (!user) return [];
    return roles.filter(role => user.roleIds.includes(role.id));
  }
  function getUserRoleChoices(user) {
    const sourceRoles = roles.filter(role => (user.roleIds || []).includes(role.id) || (user.roleIds || []).includes(role.name));
    const rawValues = sourceRoles.length ? sourceRoles.map(role => role.name) : (user.roleIds || []);
    return [...new Set(rawValues.map(roleChoiceForToken))];
  }
  function getFilteredUsers() {
    const query = searchQuery.trim().toLowerCase();
    return users.filter(user => {
      if (countryFilter && countryFilter !== ALL_COUNTRIES && String(user.countryCode || '').toUpperCase() !== countryFilter) return false;
      if (roleFilter && !getUserRoleChoices(user).includes(roleFilter)) return false;
      if (!query) return true;
      return [user.displayName, user.username, user.email, user.title].join(' ').toLowerCase().includes(query);
    });
  }
  function render() {
    const container = document.getElementById('users-content') || document.getElementById('page-root') || document.getElementById('content-area');
    if (!container) return;
    container.innerHTML = `
      <div class="users-page-shell">
        <section class="users-header-card animate-fade-in">
          <div class="users-header-top">
            <div class="users-header-copy">
              <h1>User Management</h1>
            </div>
          </div>
          <div class="users-header-controls" aria-label="User search and filters">
            <div class="users-search-box">
              <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/></svg>
              <input id="user-search" value="${esc(searchQuery)}" placeholder="Search users by name, username, or email..." aria-label="Search users by name, username, or email">
            </div>
            ${renderCountryFilterControl()}
            <select id="role-filter" class="users-filter-select" aria-label="Filter users by role">
              <option value="">All roles</option>
              ${ROLE_CHOICES.map(role => `<option value="${esc(role.id)}" ${roleFilter === role.id ? 'selected' : ''}>${esc(role.name)}</option>`).join('')}
            </select>
          </div>
        </section>
        <div id="users-table-region" class="animate-fade-in">${renderUsersTable()}</div>
        ${renderCreateUserModal()}
        ${renderEditUserModal()}
        ${renderResetPasswordModal()}
      </div>
    `;
    bindEvents();
  }
  function renderUsersRegion() {
    const region = document.getElementById('users-table-region');
    if (region) region.innerHTML = renderUsersTable();
  }
  function renderCountryFilterControl() {
    if (canViewAllCountries()) {
      return `<select id="country-filter" class="users-filter-select" aria-label="Filter users by country">
        ${countryOptions(true).map(country => `<option value="${esc(country.code)}" ${countryFilter === country.code ? 'selected' : ''}>${esc(country.name || country.code)}</option>`).join('')}
      </select>`;
    }
    const country = currentCountryOption();
    return `<div class="users-filter-select" role="status" aria-label="Assigned country scope">Platform: ${esc(country.groupName || 'KFH Group')} • ${esc(country.name || country.code)}</div>`;
  }
  function renderCountryFormControl(name, selectedCode) {
    if (canViewAllCountries()) {
      return `<label>Country<select name="${esc(name)}" required>${countryOptions(true).map(country => `<option value="${esc(country.code)}" ${selectedCode === country.code ? 'selected' : ''}>${esc(country.name || country.code)}</option>`).join('')}</select></label>`;
    }
    const country = currentCountryOption();
    return `<label>Country<input type="hidden" name="${esc(name)}" value="${esc(country.code)}"><span class="users-scope-locked">Platform: ${esc(country.groupName || 'KFH Group')} • ${esc(country.name || country.code)}</span></label>`;
  }
  function renderUsersTable() {
    const filtered = getFilteredUsers();
    return `
      <div class="kfh-card users-table-card">
        <div class="users-table-top">
          <div class="users-table-heading">
            <h2>Users</h2>
          </div>
          <button type="button" class="users-primary-action users-table-create" onclick="Users.openCreateUserModal()">
            <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M12 4v16m8-8H4"/></svg>
            Create User
          </button>
        </div>
        ${filtered.length === 0 ? renderEmptyUsersState() : `<table class="users-table">
          <thead>
            <tr>
              <th>Name</th><th>Email</th><th>Country</th><th>Role</th><th class="text-center">Status</th><th>Last Login</th><th class="text-center">Actions</th>
            </tr>
          </thead>
          <tbody>${filtered.map(renderUserRow).join('')}</tbody>
        </table>`}
      </div>
    `;
  }
  function renderEmptyUsersState() {
    return `
      <div class="users-empty-state">
        <div class="users-empty-icon">??</div>
        <h3>No users found</h3>
        <p>Create the first operator for this country or adjust the search filter.</p>
        <button type="button" class="users-primary-action" onclick="Users.openCreateUserModal()">Create User</button>
      </div>
    `;
  }
  function renderUserRow(user) {
    const roleChoices = getUserRoleChoices(user);
    const active = user.status === 'Active';
    return `
      <tr onclick="Users.openDrawer('${esc(user.id)}')">
        <td><div class="user-cell"><div class="user-avatar">${getInitials(user.displayName)}</div><div class="user-info"><h4>${esc(user.displayName)}</h4><p>@${esc(user.username)}</p></div></div></td>
        <td class="text-[#666666]">${esc(user.email) || '�'}</td>
        <td><span class="tag tag-blue">${esc(user.countryCode || scope().countryCode)}</span></td>
        <td><div class="tag-list">${roleChoices.length ? roleChoices.slice(0, 2).map(role => `<span class="tag tag-purple">${esc(roleDisplayName(role))}</span>`).join('') : '<span class="tag tag-gray">Unassigned</span>'}${roleChoices.length > 2 ? `<span class="tag tag-gray">+${roleChoices.length - 2}</span>` : ''}</div></td>
        <td class="text-center"><button onclick="event.stopPropagation(); Users.toggleStatus('${esc(user.id)}')" class="status-toggle ${active ? 'active' : 'inactive'}"><span class="status-toggle-knob"></span></button></td>
        <td class="text-[#666666]">${formatRelativeTime(user.lastLoginAt)}</td>
        <td><div class="user-actions"><button onclick="event.stopPropagation(); Users.openEditUserModal('${esc(user.id)}')" class="user-action-btn edit" title="Edit user" aria-label="Edit ${esc(user.displayName)}"><svg fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="m16.862 4.487 1.687-1.688a1.875 1.875 0 1 1 2.652 2.652L10.582 16.07a4.5 4.5 0 0 1-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 0 1 1.13-1.897l8.932-8.931Z"/><path stroke-linecap="round" stroke-linejoin="round" d="M19.5 7.125 16.875 4.5"/></svg><span>Edit</span></button><button onclick="event.stopPropagation(); Users.openResetPasswordModal('${esc(user.id)}')" class="user-action-btn password" title="Reset password" aria-label="Reset password for ${esc(user.displayName)}"><svg fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M15.75 5.25a3.75 3.75 0 1 1-6.557 2.493M10.5 6.75l-2.25-.75.75-2.25"/><path stroke-linecap="round" stroke-linejoin="round" d="M16.5 10.5V9a4.5 4.5 0 1 0-9 0v1.5m-.75 0h10.5A1.5 1.5 0 0 1 18.75 12v6.75a1.5 1.5 0 0 1-1.5 1.5H6.75a1.5 1.5 0 0 1-1.5-1.5V12a1.5 1.5 0 0 1 1.5-1.5Z"/></svg><span>Password</span></button><button onclick="event.stopPropagation(); Users.deleteUser('${esc(user.id)}')" class="user-action-btn delete" title="Delete user" aria-label="Delete ${esc(user.displayName)}"><svg fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="m14.74 9-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673A2.25 2.25 0 0 1 15.916 21H8.084a2.25 2.25 0 0 1-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 0 0-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 0 1 3.478-.397m7.5 0v-.916A2.25 2.25 0 0 0 13.5 2.25h-3A2.25 2.25 0 0 0 8.25 4.5v.916m7.5 0a48.667 48.667 0 0 0-7.5 0"/></svg><span>Delete</span></button></div></td>
      </tr>
    `;
  }
  function openDrawer(userId) {
    selectedUser = users.find(user => user.id === userId);
    renderDrawer();
    document.getElementById('user-drawer-overlay')?.classList.add('open');
    document.getElementById('user-drawer')?.classList.add('open');
  }
  function closeDrawer() {
    document.getElementById('user-drawer-overlay')?.classList.remove('open');
    document.getElementById('user-drawer')?.classList.remove('open');
    selectedUser = null;
  }
  function renderDrawer() {
    if (!selectedUser) return;
    const drawerContent = document.getElementById('drawer-content');
    if (!drawerContent) return;
    const active = selectedUser.status === 'Active';
    const roleChoices = getUserRoleChoices(selectedUser);
    drawerContent.innerHTML = `
      <div class="drawer-header">
        <div class="drawer-user-info"><div class="drawer-avatar">${getInitials(selectedUser.displayName)}</div><div><h2 class="drawer-user-name">${esc(selectedUser.displayName)}</h2><div class="drawer-user-meta"><span class="drawer-status-badge ${active ? 'active' : 'disabled'}">${esc(selectedUser.status)}</span><span class="drawer-username">@${esc(selectedUser.username)}</span></div></div></div>
        <button onclick="Users.closeDrawer()" class="drawer-close-btn"><svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/></svg></button>
      </div>
      <div class="drawer-body"><div class="profile-section"><div class="profile-section-header"><h3 class="profile-section-title">User details</h3></div><div class="profile-grid">
        <div class="profile-field"><label>Email</label><p>${esc(selectedUser.email) || '�'}</p></div>
        <div class="profile-field"><label>Username</label><p>@${esc(selectedUser.username)}</p></div>
        <div class="profile-field"><label>Country</label><p>${esc(selectedUser.countryCode || scope().countryCode)}</p></div>
        <div class="profile-field"><label>Created</label><p>${formatDateTime(selectedUser.createdAt)}</p></div>
        <div class="profile-field"><label>Last Login</label><p>${formatDateTime(selectedUser.lastLoginAt)}</p></div>
        <div class="profile-field"><label>Roles</label><p>${roleChoices.length ? roleChoices.map(role => esc(roleDisplayName(role))).join(', ') : 'Unassigned'}</p></div>
      </div></div></div>
    `;
  }
  function renderCreateUserModal() {
    if (!createModalOpen) return '';
    const current = scope();
    const createCountryCode = selectedCreateCountryCode();
    const createCountry = countryByCode(createCountryCode);
    const selectedRoleChoice = defaultRoleChoiceForCountry(createCountry.code);
    return `
      <div class="user-create-overlay" onclick="Users.closeCreateUserModal()">
        <form id="create-user-form" class="user-create-modal" onclick="event.stopPropagation()">
          <div class="user-create-header"><div><h2>New User</h2><span>${esc(createCountry.name || createCountry.code)} / ${esc(current.environment)}</span></div><button type="button" onclick="Users.closeCreateUserModal()" class="drawer-close-btn" aria-label="Close create user form"><svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/></svg></button></div>
          <div class="user-create-grid">
            <label>Username<input name="username" required maxlength="80" placeholder="f.alsalem"></label>
            <label>Full Name<input name="displayName" required maxlength="120" placeholder="e.g. Fatima Al-Salem"></label>
            <label>Email<input name="email" required type="email" maxlength="160" placeholder="user@kfh.com"></label>
            <label>Password<input name="password" required type="password" minlength="12" maxlength="128" autocomplete="new-password" placeholder="Minimum 12 characters"></label>
            <label>Confirm Password<input name="confirmPassword" required type="password" minlength="12" maxlength="128" autocomplete="new-password" placeholder="Re-enter password"></label>
            <label>Status<select name="status"><option value="Active">Active</option><option value="Disabled">Disabled</option></select></label>
            <label>Role<select name="roleId" required>${ROLE_CHOICES.map(role => `<option value="${esc(role.id)}" ${selectedRoleChoice === role.id ? 'selected' : ''}>${esc(role.name)}</option>`).join('')}</select></label>
            ${renderCountryFormControl('countryCode', createCountryCode)}
          </div>
          <div class="user-create-scope"><span>Environment: <strong>${esc(current.environment)}</strong></span></div>
          <div class="user-create-actions"><button type="button" onclick="Users.closeCreateUserModal()" class="users-secondary-action">Cancel</button><button type="submit" class="users-primary-action" ${isSavingUser ? 'disabled' : ''}>${isSavingUser ? 'Creating...' : 'Create User'}</button></div>
        </form>
      </div>
    `;
  }
  function renderEditUserModal() {
    if (!editUserId) return '';
    const user = users.find(item => item.id === editUserId);
    if (!user) return '';
    const current = scope();
    const selectedRoleChoice = getUserRoleChoices(user)[0] || defaultRoleChoiceForCountry(user.countryCode);
    const selectedCountryCode = countryByCode(user.countryCode || current.countryCode).code;
    return `
      <div class="user-create-overlay" onclick="Users.closeEditUserModal()">
        <form id="edit-user-form" class="user-create-modal" onclick="event.stopPropagation()">
          <div class="user-create-header"><div><h2>Edit User</h2><span>${esc(user.displayName)} · ${esc(user.countryCode || current.countryCode)} / ${esc(user.environment || current.environment)}</span></div><button type="button" onclick="Users.closeEditUserModal()" class="drawer-close-btn" aria-label="Close edit user form"><svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/></svg></button></div>
          <div class="user-create-grid">
            <label>Username<input name="username" required maxlength="80" value="${esc(user.username)}"></label>
            <label>Full Name<input name="displayName" required maxlength="120" value="${esc(user.displayName)}"></label>
            <label>Email<input name="email" required type="email" maxlength="160" value="${esc(user.email)}"></label>
            <label>Status<select name="status"><option value="Active" ${user.status === 'Active' ? 'selected' : ''}>Active</option><option value="Disabled" ${user.status !== 'Active' ? 'selected' : ''}>Disabled</option></select></label>
            <label>Role<select name="roleId" required>${ROLE_CHOICES.map(role => `<option value="${esc(role.id)}" ${selectedRoleChoice === role.id ? 'selected' : ''}>${esc(role.name)}</option>`).join('')}</select></label>
            ${renderCountryFormControl('countryCode', selectedCountryCode)}
          </div>
          <div class="user-create-scope"><span>Password changes are handled by the separate <strong>Reset Password</strong> action.</span><span>Environment: <strong>${esc(user.environment || current.environment)}</strong></span></div>
          <div class="user-create-actions"><button type="button" onclick="Users.closeEditUserModal()" class="users-secondary-action">Cancel</button><button type="submit" class="users-primary-action" ${isUpdatingUser ? 'disabled' : ''}>${isUpdatingUser ? 'Saving...' : 'Save Changes'}</button></div>
        </form>
      </div>
    `;
  }
  function renderResetPasswordModal() {
    if (!resetPasswordUserId) return '';
    const user = users.find(item => item.id === resetPasswordUserId);
    if (!user) return '';
    return `
      <div class="user-create-overlay" onclick="Users.closeResetPasswordModal()">
        <form id="reset-password-form" class="user-create-modal user-password-modal" onclick="event.stopPropagation()">
          <div class="user-create-header"><div><h2>Reset Password</h2><span>${esc(user.displayName)} · ${esc(user.countryCode || scope().countryCode)} / ${esc(user.environment || scope().environment)}</span></div><button type="button" onclick="Users.closeResetPasswordModal()" class="drawer-close-btn" aria-label="Close password reset form"><svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/></svg></button></div>
          <div class="user-password-note"><strong>Secure reset</strong><span>The new password is sent only to the backend for BCrypt hashing and is never displayed again.</span></div>
          <div class="user-create-grid user-password-grid">
            <label>New Password<input name="password" required type="password" minlength="12" maxlength="128" autocomplete="new-password" placeholder="Minimum 12 characters"></label>
            <label>Confirm Password<input name="confirmPassword" required type="password" minlength="12" maxlength="128" autocomplete="new-password" placeholder="Re-enter password"></label>
          </div>
          <div class="user-create-actions"><button type="button" onclick="Users.closeResetPasswordModal()" class="users-secondary-action">Cancel</button><button type="submit" class="users-primary-action" ${isResettingPassword ? 'disabled' : ''}>${isResettingPassword ? 'Resetting...' : 'Reset Password'}</button></div>
        </form>
      </div>
    `;
  }
  function bindEvents() {
    KFHUtils.bindLiveSearch('user-search', function(value) {
      searchQuery = value;
      renderUsersRegion();
    });
    document.getElementById('country-filter')?.addEventListener('change', event => setCountryFilter(event.target.value));
    document.getElementById('role-filter')?.addEventListener('change', event => { roleFilter = event.target.value; renderUsersRegion(); });
    document.querySelector('#create-user-form select[name="countryCode"]')?.addEventListener('change', event => {
      const roleSelect = document.querySelector('#create-user-form select[name="roleId"]');
      const defaultRole = defaultRoleChoiceForCountry(event.target.value);
      if (roleSelect && defaultRole) roleSelect.value = defaultRole;
    });
    document.getElementById('user-drawer-overlay')?.addEventListener('click', closeDrawer);
    document.getElementById('create-user-form')?.addEventListener('submit', handleCreateUserSubmit);
    document.getElementById('edit-user-form')?.addEventListener('submit', handleEditUserSubmit);
    document.getElementById('reset-password-form')?.addEventListener('submit', handleResetPasswordSubmit);
  }
  async function setCountryFilter(countryCode) {
    if (!canViewAllCountries()) {
      countryFilter = scope().countryCode;
      searchQuery = '';
      roleFilter = '';
      await loadData();
      render();
      return;
    }
    const requested = String(countryCode || scope().countryCode).toUpperCase();
    if (requested === ALL_COUNTRIES && canViewAllCountries()) {
      countryFilter = ALL_COUNTRIES;
      searchQuery = '';
      roleFilter = '';
      await loadData();
      render();
      return;
    }
    let activeCountry = requested;
    if (window.KFHConfig && typeof KFHConfig.switchCountryGroup === 'function') {
      const session = KFHConfig.switchCountryGroup(requested);
      activeCountry = session.countryCode || requested;
      if (window.KFHAuth && typeof KFHAuth.updateShellSession === 'function') KFHAuth.updateShellSession();
    }
    countryFilter = activeCountry;
    searchQuery = '';
    roleFilter = '';
    await loadData();
    render();
  }
  async function toggleStatus(userId) {
    const user = users.find(item => item.id === userId);
    if (!user) return;
    try {
      const updated = await withCountryScope(user.countryCode, () => requireApiClient().users.toggleStatus(user.id));
      Object.assign(user, normalizeUser(updated));
      toast(`User ${user.status === 'Active' ? 'enabled' : 'disabled'}`, 'success');
      render();
    } catch (error) {
      console.warn('[Users] API toggle unavailable.', error);
      toast('Unable to update user status in database', 'error');
    }
  }
  async function deleteUser(userId) {
    const user = users.find(item => item.id === userId);
    if (!user) return;
    try {
      await withCountryScope(user.countryCode, () => requireApiClient().users.delete(user.id));
      users = users.filter(item => item.id !== userId);
      toast(`User "${user.displayName || 'profile'}" deleted`, 'success');
      render();
    } catch (error) {
      console.warn('[Users] API delete unavailable.', error);
      toast('Unable to delete user from database', 'error');
    }
  }
  function openCreateUserModal() { createModalOpen = true; render(); }
  function closeCreateUserModal() { createModalOpen = false; isSavingUser = false; render(); }
  function openEditUserModal(userId) { editUserId = userId; closeDrawer(); render(); }
  function closeEditUserModal() { editUserId = null; isUpdatingUser = false; render(); }
  function openResetPasswordModal(userId) { resetPasswordUserId = userId; closeDrawer(); render(); }
  function closeResetPasswordModal() { resetPasswordUserId = null; isResettingPassword = false; render(); }
  function addUser() { openCreateUserModal(); }
  async function handleCreateUserSubmit(event) {
    event.preventDefault();
    if (isSavingUser) return;
    const form = event.currentTarget;
    form.confirmPassword.setCustomValidity('');
    if (!form.reportValidity()) return;
    if (form.password.value !== form.confirmPassword.value) {
      form.confirmPassword.setCustomValidity('Password and Confirm Password must match');
      form.confirmPassword.reportValidity();
      return;
    }
    const current = scope();
    const createCountry = countryByCode(form.countryCode.value);
    const roleToken = scopedRoleToken(form.roleId.value, createCountry.code);
    const newUser = {
      id: `user-${genId()}`,
      displayName: form.displayName.value.trim(),
      username: form.username.value.trim(),
      email: form.email.value.trim(),
      status: form.status.value,
      roleIds: [roleToken],
      countryCode: createCountry.code,
      environment: current.environment,
      lastLoginAt: null,
      createdAt: new Date().toISOString()
    };
    isSavingUser = true;
    render();
    try {
      const created = await withCountryScope(createCountry.code, () => requireApiClient().users.create({
        name: newUser.displayName,
        status: newUser.status,
        attributes: {
          username: newUser.username,
          email: newUser.email,
          countryCode: createCountry.code,
          environment: current.environment,
          password: form.password.value,
          roleIds: newUser.roleIds,
          roles: [roleToken],
          passwordSet: true
        }
      }));
      Object.assign(newUser, normalizeUser(created));
      isApiBacked = true;
    } catch (error) {
      console.warn('[Users] API create unavailable.', error);
      isSavingUser = false;
      render();
      toast(error && error.message ? `Unable to create user in database: ${error.message}` : 'Unable to create user in database', 'error');
      return;
    }
    if (!countryFilter || countryFilter === ALL_COUNTRIES || countryFilter === newUser.countryCode) users.unshift(newUser);
    createModalOpen = false;
    isSavingUser = false;
    toast('User created successfully', 'success');
    render();
    openDrawer(newUser.id);
  }
  async function handleEditUserSubmit(event) {
    event.preventDefault();
    if (isUpdatingUser || !editUserId) return;
    const user = users.find(item => item.id === editUserId);
    if (!user) return;
    const form = event.currentTarget;
    if (!form.reportValidity()) return;
    const editCountry = countryByCode(form.countryCode.value);
    const roleToken = scopedRoleToken(form.roleId.value, editCountry.code);
    const payload = {
      name: form.displayName.value.trim(),
      status: form.status.value,
      attributes: {
        username: form.username.value.trim(),
        email: form.email.value.trim(),
        countryCode: editCountry.code,
        roleIds: [roleToken],
        roles: [roleToken]
      }
    };
    isUpdatingUser = true;
    render();
    try {
      const updated = await withCountryScope(user.countryCode, () => requireApiClient().users.update(user.id, payload));
      Object.assign(user, normalizeUser(updated));
      editUserId = null;
      isUpdatingUser = false;
      toast('User updated successfully', 'success');
      render();
      if (!countryFilter || countryFilter === ALL_COUNTRIES || countryFilter === user.countryCode) {
        openDrawer(user.id);
      }
    } catch (error) {
      console.warn('[Users] API update unavailable.', error);
      isUpdatingUser = false;
      render();
      toast(error && error.message ? `Unable to update user in database: ${error.message}` : 'Unable to update user in database', 'error');
    }
  }
  async function handleResetPasswordSubmit(event) {
    event.preventDefault();
    if (isResettingPassword || !resetPasswordUserId) return;
    const user = users.find(item => item.id === resetPasswordUserId);
    if (!user) return;
    const form = event.currentTarget;
    form.confirmPassword.setCustomValidity('');
    if (!form.reportValidity()) return;
    if (form.password.value !== form.confirmPassword.value) {
      form.confirmPassword.setCustomValidity('Password and Confirm Password must match');
      form.confirmPassword.reportValidity();
      return;
    }
    isResettingPassword = true;
    render();
    try {
      await withCountryScope(user.countryCode, () => requireApiClient().users.resetPassword(user.id, {
        name: user.displayName,
        attributes: {
          password: form.password.value,
          passwordSet: true
        }
      }));
      resetPasswordUserId = null;
      isResettingPassword = false;
      toast('Password reset successfully', 'success');
      render();
    } catch (error) {
      console.warn('[Users] API password reset unavailable.', error);
      isResettingPassword = false;
      render();
      toast(error && error.message ? `Unable to reset password: ${error.message}` : 'Unable to reset password', 'error');
    }
  }
  async function loadData() {
    users = [];
    roles = [];
    isApiBacked = false;
    try {
      const current = scope();
      countryFilter = canViewAllCountries() ? (countryFilter || current.countryCode) : current.countryCode;
      const rolesResponse = await APIClient.users.getRoles();
      const countryCodes = countryFilter === ALL_COUNTRIES && canViewAllCountries() ? [ALL_COUNTRIES, ...availableCountries().map(country => country.code)] : [countryFilter];
      const userResponses = [];
      for (const countryCode of countryCodes) {
        userResponses.push(await withCountryScope(countryCode, () => APIClient.users.list({ page: 0, size: 100, country: countryCode, environment: current.environment })));
      }
      roles = pageContent(rolesResponse).map(normalizeRole);
      users = userResponses.flatMap(pageContent).map(normalizeUser);
      isApiBacked = true;
    } catch (error) {
      console.warn('[Users] API list unavailable; rendering empty state.', error);
    }
  }
  function shouldOpenCreateFromHash() {
    const hash = window.location.hash || '';
    return hash.includes('?') && new URLSearchParams(hash.split('?')[1]).get('create') === '1';
  }
  function toast(message, type) {
    const existing = document.querySelector('.toast-notification');
    if (existing) existing.remove();
    const element = document.createElement('div');
    element.className = 'toast-notification fixed bottom-6 right-6 px-5 py-3 rounded-lg shadow-lg z-[100] animate-fade-in flex items-center gap-3';
    element.style.background = type === 'error' ? '#FEE2E2' : '#E8F5EF';
    element.style.border = type === 'error' ? '1px solid #DC2626' : '1px solid #128754';
    element.style.color = type === 'error' ? '#DC2626' : '#128754';
    element.innerHTML = `<span class="text-sm font-medium">${esc(message)}</span>`;
    document.body.appendChild(element);
    setTimeout(() => element.remove(), 3000);
  }
  async function init() {
    await loadData();
    if (shouldOpenCreateFromHash()) createModalOpen = true;
    render();
    console.log('User Management module initialized');
  }
  return {
    init,
    openDrawer,
    closeDrawer,
    toggleStatus,
    deleteUser,
    setCountryFilter,
    openCreateUserModal,
    closeCreateUserModal,
    openEditUserModal,
    closeEditUserModal,
    openResetPasswordModal,
    closeResetPasswordModal,
    addUser,
    refresh: async () => { await loadData(); render(); toast('Data refreshed', 'success'); }
  };
})();