/**
 * KFH AIOps Command Center - Users & RBAC Module
 * User management, roles, policies, and audit logging
 */
const Users = (function() {
  'use strict';

  // State
  let users = [];
  let roles = [];
  let policies = [];
  let auditEvents = [];
  let activeTab = 'users';
  let selectedUser = null;
  let drawerTab = 'profile';
  let searchQuery = '';
  let auditFilters = { action: '', targetType: '' };

  // Constants
  const USER_STATUSES = { Active: 'Active', Disabled: 'Disabled' };
  const ENVIRONMENTS = ['Prod', 'DR', 'Both'];
  const DOMAINS = ['Core Banking', 'Digital Banking', 'Infrastructure', 'Security'];
  const TEAMS = ['Platform Ops', 'NOC', 'Digital Banking', 'Integration', 'Compliance', 'Core Banking', 'Security'];

  const PERMISSION_MODULES = {
    Dashboard: [{ key: 'view_dashboard', label: 'View Dashboard' }],
    Alerts: [{ key: 'view_alerts', label: 'View Alerts' }, { key: 'manage_alerts', label: 'Manage Alerts' }, { key: 'acknowledge_alerts', label: 'Acknowledge Alerts' }],
    Incidents: [{ key: 'view_incidents', label: 'View Incidents' }, { key: 'manage_incidents', label: 'Manage Incidents' }, { key: 'escalate_incidents', label: 'Escalate Incidents' }],
    Apps: [{ key: 'view_apps', label: 'View Applications' }, { key: 'manage_apps', label: 'Manage Applications' }],
    Inventory: [{ key: 'view_inventory', label: 'View Inventory' }, { key: 'manage_inventory', label: 'Manage Inventory' }],
    Connectors: [{ key: 'view_connectors', label: 'View Connectors' }, { key: 'manage_connectors', label: 'Manage Connectors' }],
    Schedules: [{ key: 'view_schedules', label: 'View Schedules' }, { key: 'manage_schedules', label: 'Manage Schedules' }, { key: 'run_jobs', label: 'Run Jobs' }],
    Reports: [{ key: 'view_reports', label: 'View Reports' }, { key: 'export_reports', label: 'Export Reports' }],
    Admin: [{ key: 'manage_users', label: 'Manage Users' }, { key: 'manage_roles', label: 'Manage Roles' }, { key: 'manage_policies', label: 'Manage Policies' }, { key: 'view_audit', label: 'View Audit Log' }]
  };

  // Utilities
  const randInt = (min, max) => Math.floor(Math.random() * (max - min + 1)) + min;
  const randChoice = arr => arr[Math.floor(Math.random() * arr.length)];
  const randChoices = (arr, n) => [...arr].sort(() => 0.5 - Math.random()).slice(0, n);
  const genId = () => Math.random().toString(36).substr(2, 9);
  const esc = s => s ? s.replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])) : '';

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

  function getInitials(name) {
    return name.split(' ').map(n => n[0]).join('').toUpperCase();
  }

  // Data Generation
  function generateData() {
    const now = Date.now();

    // Roles
    roles = [
      { id: 'role-001', name: 'Platform Admin', description: 'Full system access with all permissions', permissions: Object.values(PERMISSION_MODULES).flat().map(p => p.key) },
      { id: 'role-002', name: 'NOC Operator', description: 'View and manage alerts and incidents', permissions: ['view_dashboard', 'view_alerts', 'acknowledge_alerts', 'view_incidents', 'manage_incidents', 'view_apps'] },
      { id: 'role-003', name: 'NOC Viewer', description: 'Read-only access to monitoring', permissions: ['view_dashboard', 'view_alerts', 'view_incidents', 'view_apps'] },
      { id: 'role-004', name: 'Application Owner', description: 'Manage specific applications and their alerts', permissions: ['view_dashboard', 'view_alerts', 'manage_alerts', 'view_incidents', 'view_apps', 'manage_apps'] },
      { id: 'role-005', name: 'Integration Manager', description: 'Manage connectors and schedules', permissions: ['view_dashboard', 'view_connectors', 'manage_connectors', 'view_schedules', 'manage_schedules', 'run_jobs'] },
      { id: 'role-006', name: 'Compliance Auditor', description: 'View reports and audit logs', permissions: ['view_dashboard', 'view_reports', 'export_reports', 'view_audit'] }
    ];

    // Users
    const userTemplates = [
      { displayName: 'Ahmed Al-Mutairi', username: 'a.almutairi', email: 'ahmed.almutairi@kfh.com', teams: ['Platform Ops', 'Security'], title: 'Senior Platform Engineer', roleIds: ['role-001'] },
      { displayName: 'Fatima Al-Salem', username: 'f.alsalem', email: 'fatima.alsalem@kfh.com', teams: ['NOC'], title: 'NOC Operator', roleIds: ['role-002'] },
      { displayName: 'Mohammed Al-Rashid', username: 'm.alrashid', email: 'mohammed.alrashid@kfh.com', teams: ['Digital Banking'], title: 'Application Owner', roleIds: ['role-004'] },
      { displayName: 'Sarah Al-Qattan', username: 's.alqattan', email: 'sarah.alqattan@kfh.com', teams: ['Integration'], title: 'Integration Specialist', roleIds: ['role-005'] },
      { displayName: 'Khalid Al-Azmi', username: 'k.alazmi', email: 'khalid.alazmi@kfh.com', teams: ['Compliance'], title: 'Compliance Officer', roleIds: ['role-006'] },
      { displayName: 'Noura Al-Khaldi', username: 'n.alkhaldi', email: 'noura.alkhaldi@kfh.com', teams: ['NOC'], title: 'NOC Viewer', roleIds: ['role-003'], status: 'Disabled' },
      { displayName: 'Abdullah Al-Dosari', username: 'a.aldosari', email: 'abdullah.aldosari@kfh.com', teams: ['Core Banking'], title: 'Senior Developer', roleIds: ['role-004'] },
      { displayName: 'Maryam Al-Hajri', username: 'm.alhajri', email: 'maryam.alhajri@kfh.com', teams: ['NOC', 'Platform Ops'], title: 'NOC Team Lead', roleIds: ['role-002', 'role-005'] }
    ];

    users = userTemplates.map((u, i) => ({
      id: `user-${String(i + 1).padStart(3, '0')}`,
      ...u,
      status: u.status || 'Active',
      phone: `+965 2244 ${1001 + i}`,
      lastLoginAt: new Date(now - randInt(1, 48) * 3600000).toISOString(),
      createdAt: new Date(now - randInt(60, 300) * 86400000).toISOString()
    }));

    // Policies
    policies = users.map((u, i) => ({
      id: `policy-${String(i + 1).padStart(3, '0')}`,
      userId: u.id,
      allowedDomains: randChoices(DOMAINS, randInt(1, 3)),
      allowedEnvs: randChoices(['Prod', 'DR'], randInt(1, 2)),
      allowedAppIds: i < 4 ? ['APP-001', 'APP-002', 'APP-003'].slice(0, randInt(1, 3)) : [],
      allowedConnectorIds: i === 3 || i === 7 ? ['CONN-SNOW-001', 'CONN-SPL-001'] : [],
      dataMasking: i === 4
    }));

    // Audit Events
    const actions = ['user_created', 'user_updated', 'user_disabled', 'user_enabled', 'role_assigned', 'role_removed', 'policy_updated', 'login_success', 'login_failed', 'password_reset'];
    auditEvents = [];
    for (let i = 0; i < 50; i++) {
      const hoursAgo = randInt(1, 168);
      auditEvents.push({
        id: genId(),
        ts: new Date(now - hoursAgo * 3600000).toISOString(),
        actorUserId: randChoice(users).id,
        action: randChoice(actions),
        targetType: randChoice(['user', 'role', 'policy']),
        targetId: genId(),
        detail: 'Action performed successfully'
      });
    }
    auditEvents.sort((a, b) => new Date(b.ts) - new Date(a.ts));
  }

  // Stats
  function getStats() {
    const activeUsers = users.filter(u => u.status === 'Active').length;
    const disabledUsers = users.filter(u => u.status === 'Disabled').length;
    const adminUsers = users.filter(u => {
      const userRoles = getUserRoles(u.id);
      return userRoles.some(r => r.permissions.includes('manage_users'));
    }).length;
    const sevenDaysAgo = Date.now() - 7 * 86400000;
    const recentAudit = auditEvents.filter(e => new Date(e.ts).getTime() >= sevenDaysAgo).length;

    return [
      { label: 'Total Users', value: users.length, icon: 'users', color: 'blue' },
      { label: 'Active Users', value: activeUsers, icon: 'check', color: 'green' },
      { label: 'Disabled Users', value: disabledUsers, icon: 'ban', color: 'red' },
      { label: 'Roles', value: roles.length, icon: 'shield', color: 'purple' },
      { label: 'Admin Users', value: adminUsers, icon: 'key', color: 'gold' },
      { label: 'Audit (7d)', value: recentAudit, icon: 'clipboard', color: 'gray' }
    ];
  }

  // Helpers
  function getUserRoles(userId) {
    const user = users.find(u => u.id === userId);
    if (!user) return [];
    return roles.filter(r => user.roleIds.includes(r.id));
  }

  function getUserPermissions(userId) {
    const userRoles = getUserRoles(userId);
    const perms = new Set();
    userRoles.forEach(r => r.permissions.forEach(p => perms.add(p)));
    return Array.from(perms);
  }

  function getUserPolicy(userId) {
    return policies.find(p => p.userId === userId);
  }

  function getFilteredUsers() {
    return users.filter(u => {
      if (searchQuery && !u.displayName.toLowerCase().includes(searchQuery.toLowerCase())) return false;
      return true;
    });
  }

  function getFilteredAudit() {
    return auditEvents.filter(e => {
      if (auditFilters.action && e.action !== auditFilters.action) return false;
      if (auditFilters.targetType && e.targetType !== auditFilters.targetType) return false;
      return true;
    });
  }

  // Rendering
  function render() {
    const container = document.getElementById('users-content') || document.getElementById('page-root') || document.getElementById('content-area');
    if (!container) return;

    container.innerHTML = `
      <!-- KPI Strip -->
      <div class="users-kpi-grid animate-fade-in">
        ${getStats().map(s => renderKpiCard(s)).join('')}
      </div>

      <!-- Tabs -->
      <div class="users-tabs">
        <button onclick="Users.setTab('users')" class="users-tab ${activeTab === 'users' ? 'active' : ''}">Users</button>
        <button onclick="Users.setTab('roles')" class="users-tab ${activeTab === 'roles' ? 'active' : ''}">Roles</button>
        <button onclick="Users.setTab('policies')" class="users-tab ${activeTab === 'policies' ? 'active' : ''}">Policies</button>
        <button onclick="Users.setTab('audit')" class="users-tab ${activeTab === 'audit' ? 'active' : ''}">Audit Log</button>
      </div>

      <!-- Tab Content -->
      <div class="animate-fade-in">
        ${activeTab === 'users' ? renderUsersTable() : ''}
        ${activeTab === 'roles' ? renderRolesGrid() : ''}
        ${activeTab === 'policies' ? renderPoliciesSection() : ''}
        ${activeTab === 'audit' ? renderAuditSection() : ''}
      </div>
    `;

    bindEvents();
  }

  function renderKpiCard(stat) {
    const icons = {
      users: '<path stroke-linecap="round" stroke-linejoin="round" d="M15 19.128a9.38 9.38 0 002.625.372 9.337 9.337 0 004.121-.952 4.125 4.125 0 00-7.533-2.493M15 19.128v-.003c0-1.113-.285-2.16-.786-3.07M15 19.128v.106A12.318 12.318 0 018.624 21c-2.331 0-4.512-.645-6.374-1.766l-.001-.109a6.375 6.375 0 0111.964-3.07M12 6.375a3.375 3.375 0 11-6.75 0 3.375 3.375 0 016.75 0zm8.25 2.25a2.625 2.625 0 11-5.25 0 2.625 2.625 0 015.25 0z"/>',
      check: '<path stroke-linecap="round" stroke-linejoin="round" d="M4.5 12.75l6 6 9-13.5"/>',
      ban: '<path stroke-linecap="round" stroke-linejoin="round" d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636"/>',
      shield: '<path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75m-3-7.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285z"/>',
      key: '<path stroke-linecap="round" stroke-linejoin="round" d="M15.75 5.25a3 3 0 013 3m3 0a6 6 0 01-7.029 5.912c-.563-.097-1.159.026-1.563.43L10.5 17.25H8.25v2.25H6v2.25H2.25v-2.818c0-.597.237-1.17.659-1.591l6.499-6.499c.404-.404.527-1 .43-1.563A6 6 0 1121.75 8.25z"/>',
      clipboard: '<path stroke-linecap="round" stroke-linejoin="round" d="M9 12h3.75M9 15h3.75M9 18h3.75m3 .75H18a2.25 2.25 0 002.25-2.25V6.108c0-1.135-.845-2.098-1.976-2.192a48.424 48.424 0 00-1.123-.08m-5.801 0c-.065.21-.1.433-.1.664 0 .414.336.75.75.75h4.5a.75.75 0 00.75-.75 2.25 2.25 0 00-.1-.664m-5.8 0A2.251 2.251 0 0113.5 2.25H15c1.012 0 1.867.668 2.15 1.586m-5.8 0c-.376.023-.75.05-1.124.08C9.095 4.01 8.25 4.973 8.25 6.108V8.25m0 0H4.875c-.621 0-1.125.504-1.125 1.125v11.25c0 .621.504 1.125 1.125 1.125h9.75c.621 0 1.125-.504 1.125-1.125V9.375c0-.621-.504-1.125-1.125-1.125H8.25z"/>'
    };

    return `
      <div class="kfh-card users-kpi-card">
        <div class="users-kpi-header">
          <span class="users-kpi-label">${stat.label}</span>
          <div class="users-kpi-icon users-kpi-icon-${stat.color}">
            <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="1.5">${icons[stat.icon]}</svg>
          </div>
        </div>
        <span class="users-kpi-value">${stat.value}</span>
      </div>
    `;
  }

  function renderUsersTable() {
    const filteredUsers = getFilteredUsers();
    return `
      <div class="kfh-card users-table-card">
        <table class="users-table">
          <thead>
            <tr>
              <th>User</th>
              <th>Email</th>
              <th>Teams</th>
              <th>Roles</th>
              <th class="text-center">Status</th>
              <th>Last Login</th>
              <th class="text-center">Actions</th>
            </tr>
          </thead>
          <tbody>
            ${filteredUsers.map(user => renderUserRow(user)).join('')}
          </tbody>
        </table>
      </div>
    `;
  }

  function renderUserRow(user) {
    const userRoles = getUserRoles(user.id);
    const isActive = user.status === 'Active';

    return `
      <tr onclick="Users.openDrawer('${user.id}')">
        <td>
          <div class="user-cell">
            <div class="user-avatar">${getInitials(user.displayName)}</div>
            <div class="user-info">
              <h4>${esc(user.displayName)}</h4>
              <p>@${user.username}</p>
            </div>
          </div>
        </td>
        <td class="text-[#666666]">${user.email}</td>
        <td>
          <div class="tag-list">
            ${user.teams.slice(0, 2).map(t => `<span class="tag tag-blue">${esc(t)}</span>`).join('')}
            ${user.teams.length > 2 ? `<span class="tag tag-gray">+${user.teams.length - 2}</span>` : ''}
          </div>
        </td>
        <td>
          <div class="tag-list">
            ${userRoles.slice(0, 2).map(r => `<span class="tag tag-purple">${esc(r.name)}</span>`).join('')}
            ${userRoles.length > 2 ? `<span class="tag tag-gray">+${userRoles.length - 2}</span>` : ''}
          </div>
        </td>
        <td class="text-center">
          <button onclick="event.stopPropagation(); Users.toggleStatus('${user.id}')" class="status-toggle ${isActive ? 'active' : 'inactive'}">
            <span class="status-toggle-knob"></span>
          </button>
        </td>
        <td class="text-[#666666]">${formatRelativeTime(user.lastLoginAt)}</td>
        <td>
          <div class="user-actions">
            <button onclick="event.stopPropagation(); Users.openDrawer('${user.id}')" class="user-action-btn" title="View">
              <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="1.5"><path stroke-linecap="round" stroke-linejoin="round" d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z"/><path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/></svg>
            </button>
            <button onclick="event.stopPropagation(); Users.deleteUser('${user.id}')" class="user-action-btn delete" title="Delete">
              <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="1.5"><path stroke-linecap="round" stroke-linejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0"/></svg>
            </button>
          </div>
        </td>
      </tr>
    `;
  }

  function renderRolesGrid() {
    return `
      <div class="roles-grid">
        ${roles.map(role => `
          <div class="kfh-card role-card" onclick="Users.viewRole('${role.id}')">
            <div class="role-card-header">
              <div class="flex items-center gap-3">
                <div class="role-icon">
                  <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="1.5"><path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75m-3-7.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285z"/></svg>
                </div>
                <div class="role-info">
                  <h3>${esc(role.name)}</h3>
                  <p>${role.permissions.length} Permissions</p>
                </div>
              </div>
              <button onclick="event.stopPropagation(); Users.deleteRole('${role.id}')" class="role-delete-btn">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="1.5"><path stroke-linecap="round" stroke-linejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0"/></svg>
              </button>
            </div>
            <p class="role-description">${esc(role.description)}</p>
          </div>
        `).join('')}
      </div>
    `;
  }

  function renderPoliciesSection() {
    const templates = [
      { name: 'Admin - Full Access', domains: 3, envs: 'Prod, DR' },
      { name: 'NOC Viewer', domains: 2, envs: 'Prod' },
      { name: 'App Owner - Core Banking', domains: 1, envs: 'Prod, DR' }
    ];

    return `
      <div class="kfh-card p-6 mb-6">
        <h3 class="font-bold text-lg mb-4 flex items-center gap-2">
          <svg class="w-5 h-5 text-[#A79F91]" fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="1.5"><path stroke-linecap="round" stroke-linejoin="round" d="M15.75 5.25a3 3 0 013 3m3 0a6 6 0 01-7.029 5.912c-.563-.097-1.159.026-1.563.43L10.5 17.25H8.25v2.25H6v2.25H2.25v-2.818c0-.597.237-1.17.659-1.591l6.499-6.499c.404-.404.527-1 .43-1.563A6 6 0 1121.75 8.25z"/></svg>
          Policy Templates
        </h3>
        <div class="policy-templates">
          ${templates.map(t => `
            <div class="policy-template-card">
              <h4>${t.name}</h4>
              <p>Domains: <span>${t.domains}</span></p>
              <p>Envs: <span>${t.envs}</span></p>
            </div>
          `).join('')}
        </div>
      </div>

      <div class="kfh-card users-table-card">
        <table class="policies-table">
          <thead>
            <tr>
              <th>User</th>
              <th>Domains</th>
              <th>Envs</th>
              <th>Apps</th>
              <th>Connectors</th>
              <th class="text-center">Masking</th>
              <th class="text-center">Edit</th>
            </tr>
          </thead>
          <tbody>
            ${users.map(user => {
              const policy = getUserPolicy(user.id);
              if (!policy) return '';
              return `
                <tr>
                  <td>
                    <div class="user-cell">
                      <div class="user-avatar" style="width:32px;height:32px;font-size:12px">${getInitials(user.displayName)}</div>
                      <div class="user-info">
                        <h4>${esc(user.displayName)}</h4>
                        <p>@${user.username}</p>
                      </div>
                    </div>
                  </td>
                  <td>${policy.allowedDomains.length > 0 ? `${policy.allowedDomains.length} domains` : 'All'}</td>
                  <td>
                    ${policy.allowedEnvs.length > 0 
                      ? policy.allowedEnvs.map(e => `<span class="env-badge">${e}</span>`).join('') 
                      : '<span class="text-gray-400">All</span>'}
                  </td>
                  <td>${policy.allowedAppIds.length > 0 ? `${policy.allowedAppIds.length} apps` : 'All'}</td>
                  <td>${policy.allowedConnectorIds.length > 0 ? `${policy.allowedConnectorIds.length} conn` : 'All'}</td>
                  <td class="text-center">
                    ${policy.dataMasking 
                      ? '<svg class="w-4 h-4 mx-auto text-[#128754]" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.5 12.75l6 6 9-13.5"/></svg>'
                      : '<svg class="w-4 h-4 mx-auto text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/></svg>'}
                  </td>
                  <td class="text-center">
                    <button onclick="Users.openDrawer('${user.id}', 'scope')" class="user-action-btn">
                      <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="1.5"><path stroke-linecap="round" stroke-linejoin="round" d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L10.582 16.07a4.5 4.5 0 01-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 011.13-1.897l8.932-8.931z"/></svg>
                    </button>
                  </td>
                </tr>
              `;
            }).join('')}
          </tbody>
        </table>
      </div>
    `;
  }

  function renderAuditSection() {
    const filteredAudit = getFilteredAudit();

    return `
      <div class="audit-filters">
        <select id="audit-action-filter" class="audit-filter-select">
          <option value="">All Actions</option>
          <option value="user_created">User Created</option>
          <option value="user_updated">User Updated</option>
          <option value="user_disabled">User Disabled</option>
          <option value="login_success">Login Success</option>
          <option value="login_failed">Login Failed</option>
        </select>
        <select id="audit-target-filter" class="audit-filter-select">
          <option value="">All Types</option>
          <option value="user">User</option>
          <option value="role">Role</option>
          <option value="policy">Policy</option>
        </select>
      </div>

      <div class="kfh-card users-table-card">
        <div class="audit-table-container">
          <table class="audit-table">
            <thead>
              <tr>
                <th>Timestamp</th>
                <th>Actor</th>
                <th>Action</th>
                <th>Target</th>
                <th>Detail</th>
              </tr>
            </thead>
            <tbody>
              ${filteredAudit.slice(0, 30).map(event => {
                const actor = users.find(u => u.id === event.actorUserId);
                const actionClass = event.action.includes('created') || event.action.includes('enabled') || event.action.includes('success') ? 'audit-action-success' :
                                   event.action.includes('deleted') || event.action.includes('failed') ? 'audit-action-danger' :
                                   event.action.includes('disabled') ? 'audit-action-warning' : 'audit-action-info';
                return `
                  <tr>
                    <td class="text-[#666666]">${formatDateTime(event.ts)}</td>
                    <td>
                      <div class="flex items-center gap-2">
                        <div class="user-avatar" style="width:24px;height:24px;font-size:10px">${actor ? getInitials(actor.displayName) : '?'}</div>
                        <span class="font-medium">${actor ? esc(actor.displayName) : 'Unknown'}</span>
                      </div>
                    </td>
                    <td><span class="audit-action-badge ${actionClass}">${event.action.replace(/_/g, ' ')}</span></td>
                    <td><span class="audit-target-badge">${event.targetType}</span></td>
                    <td class="text-[#666666]">${esc(event.detail)}</td>
                  </tr>
                `;
              }).join('')}
            </tbody>
          </table>
        </div>
      </div>
    `;
  }

  // Drawer
  function openDrawer(userId, tab = 'profile') {
    selectedUser = users.find(u => u.id === userId);
    drawerTab = tab;
    renderDrawer();
    document.getElementById('user-drawer-overlay').classList.add('open');
    document.getElementById('user-drawer').classList.add('open');
  }

  function closeDrawer() {
    document.getElementById('user-drawer-overlay').classList.remove('open');
    document.getElementById('user-drawer').classList.remove('open');
    selectedUser = null;
  }

  function renderDrawer() {
    if (!selectedUser) return;
    const drawerContent = document.getElementById('drawer-content');
    const isActive = selectedUser.status === 'Active';
    const userRoles = getUserRoles(selectedUser.id);
    const userPerms = getUserPermissions(selectedUser.id);

    drawerContent.innerHTML = `
      <div class="drawer-header">
        <div class="drawer-user-info">
          <div class="drawer-avatar">${getInitials(selectedUser.displayName)}</div>
          <div>
            <h2 class="drawer-user-name">${esc(selectedUser.displayName)}</h2>
            <div class="drawer-user-meta">
              <span class="drawer-status-badge ${isActive ? 'active' : 'disabled'}">${selectedUser.status}</span>
              <span class="drawer-username">@${selectedUser.username}</span>
            </div>
          </div>
        </div>
        <button onclick="Users.closeDrawer()" class="drawer-close-btn">
          <svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/></svg>
        </button>
      </div>

      <div class="drawer-tabs">
        <button onclick="Users.setDrawerTab('profile')" class="drawer-tab ${drawerTab === 'profile' ? 'active' : ''}">
          <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="1.5"><path stroke-linecap="round" stroke-linejoin="round" d="M17.982 18.725A7.488 7.488 0 0012 15.75a7.488 7.488 0 00-5.982 2.975m11.963 0a9 9 0 10-11.963 0m11.963 0A8.966 8.966 0 0112 21a8.966 8.966 0 01-5.982-2.275M15 9.75a3 3 0 11-6 0 3 3 0 016 0z"/></svg>
          Profile
        </button>
        <button onclick="Users.setDrawerTab('roles')" class="drawer-tab ${drawerTab === 'roles' ? 'active' : ''}">
          <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="1.5"><path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75m-3-7.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285z"/></svg>
          Roles
        </button>
        <button onclick="Users.setDrawerTab('permissions')" class="drawer-tab ${drawerTab === 'permissions' ? 'active' : ''}">
          <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="1.5"><path stroke-linecap="round" stroke-linejoin="round" d="M15.75 5.25a3 3 0 013 3m3 0a6 6 0 01-7.029 5.912c-.563-.097-1.159.026-1.563.43L10.5 17.25H8.25v2.25H6v2.25H2.25v-2.818c0-.597.237-1.17.659-1.591l6.499-6.499c.404-.404.527-1 .43-1.563A6 6 0 1121.75 8.25z"/></svg>
          Permissions
        </button>
      </div>

      <div class="drawer-body">
        ${drawerTab === 'profile' ? renderProfileTab() : ''}
        ${drawerTab === 'roles' ? renderRolesTab(userRoles) : ''}
        ${drawerTab === 'permissions' ? renderPermissionsTab(userPerms) : ''}
      </div>
    `;
  }

  function renderProfileTab() {
    return `
      <div class="profile-section">
        <div class="profile-section-header">
          <h3 class="profile-section-title">Personal Information</h3>
        </div>
        <div class="profile-grid">
          <div class="profile-field"><label>Email</label><p>${selectedUser.email}</p></div>
          <div class="profile-field"><label>Title</label><p>${selectedUser.title || '-'}</p></div>
          <div class="profile-field"><label>Phone</label><p>${selectedUser.phone || '-'}</p></div>
          <div class="profile-field"><label>Created</label><p>${formatDateTime(selectedUser.createdAt)}</p></div>
          <div class="profile-field"><label>Teams</label><p>${selectedUser.teams.join(', ')}</p></div>
          <div class="profile-field"><label>Last Login</label><p>${formatDateTime(selectedUser.lastLoginAt)}</p></div>
        </div>
      </div>
    `;
  }

  function renderRolesTab(userRoles) {
    return `
      <div class="roles-info-banner">
        <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="1.5"><path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75m-3-7.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285z"/></svg>
        <p>This user has <strong>${userRoles.length}</strong> active roles assigned.</p>
      </div>
      ${roles.map(role => {
        const isAssigned = selectedUser.roleIds.includes(role.id);
        return `
          <div onclick="Users.toggleRole('${role.id}')" class="role-assignment-item ${isAssigned ? 'assigned' : 'unassigned'}">
            <div class="role-assignment-info">
              <h4>${esc(role.name)}</h4>
              <p>${esc(role.description)}</p>
            </div>
            <div class="role-checkbox">
              ${isAssigned ? '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.5 12.75l6 6 9-13.5"/></svg>' : ''}
            </div>
          </div>
        `;
      }).join('')}
    `;
  }

  function renderPermissionsTab(userPerms) {
    return Object.entries(PERMISSION_MODULES).map(([module, perms]) => {
      const hasPerms = perms.filter(p => userPerms.includes(p.key));
      if (hasPerms.length === 0) return '';
      return `
        <div class="permission-module">
          <h4>${module}</h4>
          <div class="permission-tags">
            ${hasPerms.map(p => `
              <span class="permission-tag">
                <svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.5 12.75l6 6 9-13.5"/></svg>
                ${p.label}
              </span>
            `).join('')}
          </div>
        </div>
      `;
    }).join('');
  }

  // Events
  function bindEvents() {
    document.getElementById('user-search')?.addEventListener('input', e => {
      searchQuery = e.target.value;
      render();
    });

    document.getElementById('audit-action-filter')?.addEventListener('change', e => {
      auditFilters.action = e.target.value;
      render();
    });

    document.getElementById('audit-target-filter')?.addEventListener('change', e => {
      auditFilters.targetType = e.target.value;
      render();
    });

    document.getElementById('user-drawer-overlay')?.addEventListener('click', closeDrawer);
  }

  // Actions
  function setTab(tab) {
    activeTab = tab;
    render();
  }

  function setDrawerTab(tab) {
    drawerTab = tab;
    renderDrawer();
  }

  function toggleStatus(userId) {
    const user = users.find(u => u.id === userId);
    if (user) {
      user.status = user.status === 'Active' ? 'Disabled' : 'Active';
      toast(`User ${user.status === 'Active' ? 'enabled' : 'disabled'}`, 'success');
      render();
    }
  }

  function toggleRole(roleId) {
    if (!selectedUser) return;
    const idx = selectedUser.roleIds.indexOf(roleId);
    if (idx > -1) {
      selectedUser.roleIds.splice(idx, 1);
    } else {
      selectedUser.roleIds.push(roleId);
    }
    toast('Role assignment updated', 'success');
    renderDrawer();
  }

  function deleteUser(userId) {
    const user = users.find(u => u.id === userId);
    users = users.filter(u => u.id !== userId);
    policies = policies.filter(p => p.userId !== userId);
    toast(`User "${user?.displayName}" deleted`, 'success');
    render();
  }

  function deleteRole(roleId) {
    const role = roles.find(r => r.id === roleId);
    users.forEach(u => u.roleIds = u.roleIds.filter(id => id !== roleId));
    roles = roles.filter(r => r.id !== roleId);
    toast(`Role "${role?.name}" deleted`, 'success');
    render();
  }

  function viewRole(roleId) {
    toast(`Viewing role: ${roles.find(r => r.id === roleId)?.name}`, 'info');
  }

  function addUser() {
    const newUser = {
      id: `user-${genId()}`,
      displayName: 'New User',
      username: 'newuser',
      email: 'newuser@kfh.com',
      status: 'Active',
      teams: [],
      title: '',
      phone: '',
      roleIds: [],
      lastLoginAt: null,
      createdAt: new Date().toISOString()
    };
    users.push(newUser);
    policies.push({
      id: `policy-${genId()}`,
      userId: newUser.id,
      allowedDomains: [],
      allowedEnvs: [],
      allowedAppIds: [],
      allowedConnectorIds: [],
      dataMasking: false
    });
    toast('User created successfully', 'success');
    render();
    openDrawer(newUser.id);
  }

  function toast(msg, type) {
    type = type || 'info';
    const existing = document.querySelector('.toast-notification');
    if (existing) existing.remove();

    const t = document.createElement('div');
    t.className = 'toast-notification fixed bottom-6 right-6 px-5 py-3 rounded-lg shadow-lg z-[100] animate-fade-in flex items-center gap-3';

    if (type === 'success') {
      t.style.background = '#E8F5EF';
      t.style.border = '1px solid #128754';
      t.style.color = '#128754';
    } else if (type === 'error') {
      t.style.background = '#FEE2E2';
      t.style.border = '1px solid #DC2626';
      t.style.color = '#DC2626';
    } else {
      t.style.background = '#E0F7FA';
      t.style.border = '1px solid #006064';
      t.style.color = '#006064';
    }

    t.innerHTML = '<span class="text-sm font-medium">' + esc(msg) + '</span>';
    document.body.appendChild(t);
    setTimeout(() => t.remove(), 3000);
  }

  // Init
  function init() {
    generateData();
    render();
    console.log('Users & RBAC module initialized');
  }

  return {
    init,
    setTab,
    setDrawerTab,
    openDrawer,
    closeDrawer,
    toggleStatus,
    toggleRole,
    deleteUser,
    deleteRole,
    viewRole,
    addUser,
    refresh: () => { generateData(); render(); toast('Data refreshed', 'success'); }
  };
})();

window.Users = Users;

