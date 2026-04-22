/**
 * KFH AIOps Command Center - Application Configuration
 * Global settings and constants
 */
window.KFHConfig = (function() {
  'use strict';

  // API Configuration
  const API_BASE_URL = '/api/v1';

  // Default Headers (multi-tenancy support)
  const DEFAULT_TENANT_ID = 'KFH_PROD';
  const DEFAULT_USER_ID = 'system';

  // Current user session (will be populated from auth)
  let currentSession = {
    tenantId: DEFAULT_TENANT_ID,
    userId: DEFAULT_USER_ID,
    userName: 'Ahmed Al-Rashid',
    userInitials: 'AR',
    userRole: 'Platform Admin',
    permissions: ['*'] // All permissions for demo
  };

  // Navigation configuration
  const NAV_ITEMS = [
    {
      group: 'Operations',
      items: [
        { id: 'dashboard', label: 'Dashboard', icon: 'dashboard', path: '/dashboard' },
        { id: 'incidents', label: 'Incidents', icon: 'incident', path: '/incidents', badge: null },
        { id: 'alerts', label: 'Alert Explorer', icon: 'alert', path: '/alerts' },
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
        { id: 'users', label: 'Users & RBAC', icon: 'users', path: '/users' },
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
    'users': 'Users & RBAC',
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
    { id: 'vROps', label: 'vROps', icon: 'cloud' },
    { id: 'BMC', label: 'BMC Helix', icon: 'server' },
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
    NAV_ITEMS,
    PAGE_TITLES,
    SEVERITIES,
    STATUSES,
    CLASSIFICATIONS,
    CONNECTOR_TYPES,
    DOMAINS,
    ENVIRONMENTS,
    TEAMS,
    ASSET_TYPES,
    TIME_RANGES,

    // Session management
    getSession: function() {
      return { ...currentSession };
    },

    setSession: function(session) {
      currentSession = { ...currentSession, ...session };
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
})();
