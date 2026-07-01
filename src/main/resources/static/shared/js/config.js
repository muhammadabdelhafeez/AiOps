/**
 * KFH AIOps Command Center - Application Configuration
 * Global settings and constants
 */
window.KFHConfig = (function() {
  'use strict';

  // API Configuration
  const API_BASE_URL = '/api/v1';
  const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

  // Default Headers (multi-tenancy support)
  const DEFAULT_TENANT_ID = '00000000-0000-4000-8000-000000000001';
  const DEFAULT_USER_ID = '00000000-0000-4000-8000-000000000101';
  const SESSION_STORAGE_KEY = 'kfh.aiops.commandcenter.session.v1';

  const COUNTRY_GROUP_NAME = 'KFH Group';
  const ALL_COUNTRIES_CODE = 'ALL';
  const ALL_COUNTRY_SCOPE = { code: ALL_COUNTRIES_CODE, name: 'All countries', groupName: COUNTRY_GROUP_NAME, tenantId: DEFAULT_TENANT_ID, defaultUserId: DEFAULT_USER_ID, flag: '', allCountries: true };

  const COUNTRIES = [
    { code: 'KW', name: 'KFH Kuwait', groupName: COUNTRY_GROUP_NAME, tenantId: DEFAULT_TENANT_ID, defaultUserId: DEFAULT_USER_ID, flag: '' },
    { code: 'BH', name: 'KFH Bahrain', groupName: COUNTRY_GROUP_NAME, tenantId: DEFAULT_TENANT_ID, defaultUserId: '00000000-0000-4000-8000-000000000102', flag: '' },
    { code: 'EG', name: 'KFH Egypt', groupName: COUNTRY_GROUP_NAME, tenantId: DEFAULT_TENANT_ID, defaultUserId: '00000000-0000-4000-8000-000000000103', flag: '' }
  ];
  const COUNTRY_SCOPES = [ALL_COUNTRY_SCOPE, ...COUNTRIES];

  const LOGIN_ENVIRONMENTS = [
    { code: 'PROD', name: 'Production' },
    { code: 'UAT', name: 'UAT' },
    { code: 'DEV', name: 'Development' }
  ];

  const LOGIN_ROLES = [
    { id: 'GLOBAL_ADMIN', name: 'KFH Global Admin', permissions: ['*'] },
    { id: 'COUNTRY_ADMIN', name: 'Country Admin', permissions: ['DASHBOARD_READ', 'INCIDENT_READ', 'ALERT_READ', 'IDENTITY_READ', 'IDENTITY_WRITE'] },
    { id: 'NOC_OPERATOR', name: 'NOC Operator', permissions: ['DASHBOARD_READ', 'INCIDENT_READ', 'ALERT_READ', 'IDENTITY_READ'] },
    { id: 'VIEWER', name: 'Viewer', permissions: ['DASHBOARD_READ', 'INCIDENT_READ', 'ALERT_READ'] }
  ];

  let currentSession = loadStoredSession();

  // Navigation configuration
  const NAV_ITEMS = [
    {
      group: 'Operations',
      items: [
        { id: 'dashboard', label: 'Dashboard', icon: 'dashboard', path: '/dashboard' },
        { id: 'incidents', label: 'Incidents', icon: 'incident', path: '/incidents', badge: null },
        { id: 'alerts', label: 'Alert Explorer', icon: 'alert', path: '/alerts' },
        { id: 'explorer', label: 'Log Explorer', icon: 'report', path: '/explorer' },
        { id: 'inventory', label: 'Inventory', icon: 'inventory', path: '/inventory' }
      ]
    },
    {
      group: 'Intelligence',
      items: [
        { id: 'applications', label: 'Applications', icon: 'app', path: '/applications' },
        { id: 'reports', label: 'Reports', icon: 'report', path: '/reports' }
      ]
    },
    {
      group: 'Configuration',
      items: [
        { id: 'connectors', label: 'Connectors', icon: 'connector', path: '/connectors' },
        { id: 'schedules', label: 'Schedules', icon: 'schedule', path: '/schedules' },
        { id: 'users', label: 'User Management', icon: 'users', path: '/users' },
        { id: 'settings', label: 'Settings', icon: 'settings', path: '/settings' },
        { id: 'audit', label: 'Audit Logs', icon: 'audit', path: '/audit' }
      ]
    }
  ];

  // Page titles mapping
  const PAGE_TITLES = {
    'dashboard': 'Dashboard',
    'incidents': 'Incidents',
    'alerts': 'Alert Explorer',
    'inventory': 'Inventory & Infrastructure',
    'applications': 'Applications',
    'reports': 'Reports',
    'connectors': 'Connectors',
    'schedules': 'Schedules',
    'users': 'User Management',
    'settings': 'Settings',
    'audit': 'Audit Logs'
  };

  // Severity configuration
  const SEVERITIES = {
    critical: { label: 'Critical', color: '#D32F2F', bgColor: '#FFF8F8', borderColor: '#FECACA' },
    high: { label: 'High', color: '#D97706', bgColor: '#FEF3C7', borderColor: '#FDE68A' },
    medium: { label: 'Medium', color: '#CA8A04', bgColor: '#FEF9C3', borderColor: '#FEF08A' },
    low: { label: 'Low', color: '#2563EB', bgColor: '#EFF6FF', borderColor: '#BFDBFE' },
    info: { label: 'Info', color: '#006064', bgColor: '#E0F7FA', borderColor: '#B2EBF2' }
  };

  // Status configuration
  const STATUSES = {
    open: { label: 'Open', color: '#D32F2F', bgColor: '#FFF8F8' },
    acknowledged: { label: 'Acknowledged', color: '#D97706', bgColor: '#FEF3C7' },
    inProgress: { label: 'In Progress', color: '#006064', bgColor: '#E0F7FA' },
    resolved: { label: 'Resolved', color: '#128754', bgColor: '#E8F5EF' },
    closed: { label: 'Closed', color: '#666666', bgColor: '#F8F9FA' }
  };

  // Classification types
  const CLASSIFICATIONS = {
    NEW: { label: 'New', color: '#128754', bgColor: '#128754', textColor: '#FFFFFF' },
    RECURRING_SURE: { label: 'Recurring', color: '#7E22CE', bgColor: '#F3E8FF', borderColor: '#E9D5FF' },
    RECURRING_LIKELY: { label: 'Recurring (Likely)', color: '#D97706', bgColor: '#FEF3C7', borderColor: '#FDE68A' },
    POSSIBLE: { label: 'Possible Match', color: '#006064', bgColor: '#E0F7FA', borderColor: '#B2EBF2' }
  };

  // Connector types
  const CONNECTOR_TYPES = [
    { id: 'SCOM', label: 'SCOM', icon: 'server' },
    { id: 'VROPS', label: 'vROps', icon: 'cloud' },
    { id: 'BMC', label: 'BMC Helix', icon: 'server' },
    { id: 'APPDYNAMICS', label: 'AppDynamics', icon: 'activity' },
    { id: 'SolarWinds', label: 'SolarWinds', icon: 'monitor' },
    { id: 'Elastic', label: 'Elastic', icon: 'database' },
    { id: 'Azure', label: 'Azure Monitor', icon: 'cloud' },
    { id: 'Syslog', label: 'Syslog', icon: 'file' },
    { id: 'SMTP', label: 'SMTP/Email', icon: 'mail' },
    { id: 'SharePoint', label: 'SharePoint', icon: 'folder' },
    { id: 'Teams', label: 'MS Teams', icon: 'message' }
  ];

  // Domains
  const DOMAINS = [
    'Core Banking',
    'Digital Channels',
    'Treasury',
    'Risk Management',
    'HR Systems',
    'Infrastructure'
  ];

  // Environments
  const ENVIRONMENTS = ['Production', 'DR', 'UAT', 'Development'];

  // Teams
  const TEAMS = [
    'Platform Ops',
    'DBA Team',
    'Network Ops',
    'Security Ops',
    'App Support',
    'DevOps',
    'Cloud Ops',
    'NOC'
  ];

  // Asset types
  const ASSET_TYPES = [
    'Server', 'VM', 'DB', 'LoadBalancer', 'Firewall',
    'Switch', 'Router', 'Storage', 'K8sCluster',
    'Namespace', 'Pod', 'Service', 'URL', 'Queue'
  ];

  // Time range options
  const TIME_RANGES = [
    { id: '1h', label: 'Last Hour', ms: 60 * 60 * 1000 },
    { id: '24h', label: 'Last 24 Hours', ms: 24 * 60 * 60 * 1000 },
    { id: '7d', label: 'Last 7 Days', ms: 7 * 24 * 60 * 60 * 1000 },
    { id: '15d', label: 'Last 15 Days', ms: 15 * 24 * 60 * 60 * 1000 },
    { id: '30d', label: 'Last 30 Days', ms: 30 * 24 * 60 * 60 * 1000 }
  ];

  // Public API
  return {
    API_BASE_URL,
    DEFAULT_TENANT_ID,
    DEFAULT_USER_ID,
    COUNTRY_GROUP_NAME,
    ALL_COUNTRIES_CODE,
    ALL_COUNTRY_SCOPE,
    NAV_ITEMS,
    PAGE_TITLES,
    SEVERITIES,
    STATUSES,
    CLASSIFICATIONS,
    CONNECTOR_TYPES,
    COUNTRIES,
    COUNTRY_SCOPES,
    LOGIN_ENVIRONMENTS,
    LOGIN_ROLES,
    DOMAINS,
    ENVIRONMENTS,
    TEAMS,
    ASSET_TYPES,
    TIME_RANGES,

    // Session management
    getSession: function() {
      return currentSession ? { ...currentSession } : {
        tenantId: DEFAULT_TENANT_ID,
        userId: DEFAULT_USER_ID,
        countryCode: 'KW',
        environment: 'PROD',
        userName: 'Unauthenticated',
        userInitials: 'NA',
        userRole: 'Not signed in',
        permissions: [],
        homeCountryCode: 'KW'
      };
    },

    setSession: function(session) {
      currentSession = normalizeSession({ ...(currentSession || {}), ...session });
      window.localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(currentSession));
      notifyScopeChanged(currentSession);
      return { ...currentSession };
    },

    switchCountryGroup: function(countryCode) {
      const selected = COUNTRY_SCOPES.find(country => country.code === String(countryCode || '').trim().toUpperCase());
      if (!selected) {
        return this.getSession();
      }
      const session = this.getSession();
      const canSwitch = canUseGlobalCountryScope(session);
      if (!canSwitch && session.countryCode !== selected.code) {
        return session;
      }
      return this.setSession({
        ...session,
        tenantId: session.tenantId || selected.tenantId,
        userId: session.userId || selected.defaultUserId,
        countryCode: selected.code,
        countryName: selected.name,
        countryGroupName: selected.groupName
      });
    },

    clearSession: function() {
      currentSession = null;
      window.localStorage.removeItem(SESSION_STORAGE_KEY);
    },

    isAuthenticated: function() {
      return hasValidApiContext(currentSession);
    },

    hasValidApiContext: function(session) {
      return Boolean(session)
        && UUID_PATTERN.test(String(session.tenantId || '').trim())
        && UUID_PATTERN.test(String(session.userId || '').trim());
    },

    getCountry: function(countryCode) {
      return COUNTRY_SCOPES.find(country => country.code === String(countryCode || '').trim().toUpperCase()) || COUNTRIES[0];
    },

    canUseGlobalCountryScope: function(session) {
      return canUseGlobalCountryScope(session || this.getSession());
    },

    getLoginRole: function(roleId) {
      return LOGIN_ROLES.find(role => role.id === roleId) || LOGIN_ROLES[2];
    },

    // Permission check
    hasPermission: function(permission) {
      if (currentSession.permissions.includes('*')) return true;
      return currentSession.permissions.includes(permission);
    },

    // Get severity config
    getSeverity: function(key) {
      return SEVERITIES[key.toLowerCase()] || SEVERITIES.info;
    },

    // Get status config
    getStatus: function(key) {
      return STATUSES[key.toLowerCase()] || STATUSES.open;
    },

    // Get page title
    getPageTitle: function(pageId) {
      return PAGE_TITLES[pageId] || 'KFH AIOps';
    },

    // Build timestamp for cache busting
    BUILD_TS: window.BUILD_TS || Date.now()
  };

  function loadStoredSession() {
    try {
      const raw = window.localStorage.getItem(SESSION_STORAGE_KEY);
      return raw ? normalizeSession(JSON.parse(raw)) : null;
    } catch (error) {
      window.localStorage.removeItem(SESSION_STORAGE_KEY);
      return null;
    }
  }

  function normalizeSession(session) {
    const country = COUNTRY_SCOPES.find(item => item.code === String(session.countryCode || '').trim().toUpperCase()) || COUNTRIES[0];
    const role = LOGIN_ROLES.find(item => item.id === session.roleId) || LOGIN_ROLES[2];
    const userName = String(session.userName || 'KFH Operator').trim();
    const environment = String(session.environment || 'PROD').toUpperCase();
    const homeCountryCode = String(session.homeCountryCode || session.assignedCountryCode || country.code).trim().toUpperCase();
    const tenantId = normalizeUuid(session.tenantId, country.tenantId);
    const userId = normalizeUuid(session.userId, country.defaultUserId);

    return {
      tenantId,
      userId,
      userName,
      userInitials: session.userInitials || initials(userName),
      userRole: session.userRole || role.name,
      roleId: role.id,
      permissions: Array.isArray(session.permissions) && session.permissions.length > 0 ? session.permissions : role.permissions,
      countryCode: country.code,
      homeCountryCode,
      countryName: country.name,
      countryGroupName: country.groupName,
      environment,
      authenticatedAt: session.authenticatedAt || new Date().toISOString()
    };
  }

  function notifyScopeChanged(session) {
    window.dispatchEvent(new CustomEvent('kfh:scope-changed', {
      detail: {
        tenantId: session.tenantId,
        userId: session.userId,
        countryCode: session.countryCode,
        countryName: session.countryName,
        environment: session.environment
      }
    }));
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

  function canUseGlobalCountryScope(session) {
    const permissions = Array.isArray(session.permissions) ? session.permissions : [];
    const homeCountryCode = String(session.homeCountryCode || session.assignedCountryCode || session.countryCode || '').trim().toUpperCase();
    return homeCountryCode === ALL_COUNTRIES_CODE
      && (permissions.includes('*') || permissions.includes('COUNTRY_GLOBAL_VIEW'));
  }

  function hasValidApiContext(session) {
    return Boolean(session)
      && UUID_PATTERN.test(String(session.tenantId || '').trim())
      && UUID_PATTERN.test(String(session.userId || '').trim());
  }

  function normalizeUuid(value, fallback) {
    const candidate = String(value || '').trim();
    if (UUID_PATTERN.test(candidate)) {
      return candidate;
    }
    return fallback;
  }
})();
