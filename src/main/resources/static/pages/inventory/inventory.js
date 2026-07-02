/**
 * KFH AIOps Command Center - Inventory Page (React)
 * Runs in-browser via React + Babel (no build step) and follows KFH Beyond Horizons colors.
 * Wrapped in IIFE to prevent global scope pollution on page reloads.
 */

/* global React, ReactDOM, Recharts */

(function() {
'use strict';

// Ensure lucide is available - check multiple possible locations at runtime
const getLucide = () => {
  // Try direct global access
  if (typeof lucide !== 'undefined' && lucide !== null) return lucide;
  // Try window property (lower case - UMD export)
  if (typeof window !== 'undefined' && window.lucide) return window.lucide;
  // Try window property (Pascal case - alternative export)
  if (typeof window !== 'undefined' && window.Lucide) return window.Lucide;
  // Log warning and return null
  console.warn('[Inventory] Lucide library not found. Icons may not render correctly.');
  return null;
};

// Verify lucide is available on load
(function verifyDependencies() {
  const lib = getLucide();
  if (lib) {
    console.log('[Inventory] Lucide library loaded successfully');
  }
})();

const { useState, useEffect, useMemo, useCallback, createContext, useContext } = React;
const {
  ResponsiveContainer,
  BarChart,
  Bar,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
} = Recharts;

// --- KFH Palette ---
const KFH = {
  primary: '#128754',
  primaryDark: '#0E6B42',
  primaryDarker: '#0A5535',
  primaryLight: '#e2f7dd',
  primaryLighter: '#E8F5EF',
  gold: '#A79F91',
  goldLight: '#F0EFE9',
  goldDark: '#8B8578',
  bgBody: '#F3F4F7',
  card: '#FFFFFF',
  offWhite: '#F8F9FA',
  textMain: '#1D1D1D',
  textMuted: '#666666',
  border: 'rgba(0,0,0,0.04)',
  criticalBg: '#FFF8F8',
  critical: '#D32F2F',
  warningBg: '#FEF3C7',
  warning: '#D97706',
  infoBg: '#E0F7FA',
  info: '#006064',
};

// --- Lucide Icons via UMD ---
// Lucide UMD exports each icon as an array: ["svg", defaultAttrs, children]
// where children is an array of [tag, attrs] tuples
// Icon names are PascalCase (e.g., 'AlertTriangle', 'HardDrive')
const toPascalCase = (str) => {
  return str.split('-').map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()).join('');
};

const Icon = (name) => {
  return function LucideIcon(props) {
    // Get the lucide library from available sources
    const lucideLib = getLucide();

    // Check if lucide is available
    if (!lucideLib) {
      console.warn('Lucide library not loaded');
      // Return a fallback icon (simple placeholder)
      const size = props?.size || 24;
      return React.createElement('svg', {
        xmlns: 'http://www.w3.org/2000/svg',
        width: size,
        height: size,
        viewBox: '0 0 24 24',
        fill: 'none',
        stroke: 'currentColor',
        strokeWidth: 2,
        strokeLinecap: 'round',
        strokeLinejoin: 'round',
        className: props?.className || '',
        style: props?.style
      }, React.createElement('circle', { cx: 12, cy: 12, r: 10 }));
    }

    // Convert kebab-case to PascalCase for Lucide lookup
    const pascalName = toPascalCase(name);
    const iconData = lucideLib[pascalName];
    if (!iconData || !Array.isArray(iconData)) {
      console.warn(`Lucide icon "${name}" (${pascalName}) not found`);
      return null;
    }

    const size = props?.size || 24;
    const color = props?.color || 'currentColor';
    const strokeWidth = props?.strokeWidth || 2;
    const className = props?.className || '';

    // iconData format: ["svg", {defaultAttrs}, [[tag, attrs], ...]]
    const [, , childrenData] = iconData;

    const children = Array.isArray(childrenData)
      ? childrenData.map((child, index) => {
          if (Array.isArray(child) && child.length >= 2) {
            const [tag, attrs] = child;
            return React.createElement(tag, { key: index, ...attrs });
          }
          return null;
        }).filter(Boolean)
      : [];

    return React.createElement('svg', {
      xmlns: 'http://www.w3.org/2000/svg',
      width: size,
      height: size,
      viewBox: '0 0 24 24',
      fill: 'none',
      stroke: color,
      strokeWidth: strokeWidth,
      strokeLinecap: 'round',
      strokeLinejoin: 'round',
      className: className,
      style: props?.style
    }, children);
  };
};

const Server = Icon('server');
const Database = Icon('database');
const Cloud = Icon('cloud');
const Network = Icon('network');
const Box = Icon('box');
const Globe = Icon('globe');
const Shield = Icon('shield');
const HardDrive = Icon('hard-drive');
const Search = Icon('search');
const Filter = Icon('filter');
const Plus = Icon('plus');
const Grid = Icon('layout-grid');
const List = Icon('list');
const GitBranch = Icon('git-branch');
const X = Icon('x');
const Eye = Icon('eye');
const Edit = Icon('pencil');
const Save = Icon('save');
const AlertTriangle = Icon('alert-triangle');
const AlertCircle = Icon('alert-circle');
const CheckCircle = Icon('check-circle');
const Activity = Icon('activity');
const Tag = Icon('tag');
const LinkIcon = Icon('link');

// ============= CUSTOM STYLES (KFH) =============
const CustomStyles = () => (
  <style>{`
    :root {
      --kfh-primary: ${KFH.primary};
      --kfh-primary-dark: ${KFH.primaryDark};
      --kfh-primary-darker: ${KFH.primaryDarker};
      --kfh-primary-light: ${KFH.primaryLight};
      --kfh-primary-lighter: ${KFH.primaryLighter};
      --kfh-gold: ${KFH.gold};
      --kfh-gold-dark: ${KFH.goldDark};
      --bg-body: ${KFH.bgBody};
      --text-main: ${KFH.textMain};
      --text-muted: ${KFH.textMuted};
      --border: ${KFH.border};
    }

    /* KFH card styles are inherited from inventory.css and kfh-theme.css */

    .chart-container {
      filter: drop-shadow(0 4px 6px rgba(0, 0, 0, 0.1));
    }

    .health-bar {
      position: relative;
      height: 6px;
      background: ${KFH.offWhite};
      border-radius: 3px;
      overflow: hidden;
    }
    .health-bar-fill {
      height: 100%;
      transition: width 0.3s ease, background 0.3s ease;
      border-radius: 3px;
    }

    @keyframes pulse-critical {
      0%, 100% { opacity: 1; }
      50% { opacity: 0.7; }
    }
    .pulse-critical { animation: pulse-critical 1.5s ease-in-out infinite; }

    @keyframes slideIn {
      from { transform: translateX(100%); opacity: 0; }
      to { transform: translateX(0); opacity: 1; }
    }
    .drawer-enter { animation: slideIn 0.3s ease-out; }
    .toast { animation: slideIn 0.3s ease-out; }

    /* KFH Scrollbar */
    ::-webkit-scrollbar { width: 8px; height: 8px; }
    ::-webkit-scrollbar-track { background: transparent; }
    ::-webkit-scrollbar-thumb { background: ${KFH.gold}; border-radius: 4px; }
    ::-webkit-scrollbar-thumb:hover { background: ${KFH.goldDark}; }

    ::selection { background: ${KFH.primary}; color: #fff; }
    
    /* Fix inline-flex and flex display issues */
    .inline-flex {
      display: inline-flex !important;
    }
    .flex {
      display: flex !important;
    }
    .flex-wrap {
      flex-wrap: wrap !important;
    }
    .items-center {
      align-items: center !important;
    }
    .justify-between {
      justify-content: space-between !important;
    }
    .gap-1 { gap: 0.25rem !important; }
    .gap-2 { gap: 0.5rem !important; }
    .gap-3 { gap: 0.75rem !important; }
    .gap-4 { gap: 1rem !important; }
    
    /* Badge fixes */
    .rounded-md {
      border-radius: 6px !important;
    }
    .rounded-lg {
      border-radius: 8px !important;
    }
  `}</style>
);

// ============= UTILITIES =============
const generateId = () => (window.crypto && window.crypto.randomUUID ? window.crypto.randomUUID() : String(Date.now()));

// ============= CONSTANTS =============
const ASSET_TYPES = ['Server', 'VM', 'DB', 'LoadBalancer', 'Firewall', 'Switch', 'Router', 'Storage', 'K8sCluster', 'Namespace', 'Pod', 'Service', 'URL', 'Queue'];
const ENVIRONMENTS = ['Production', 'DR', 'UAT', 'Development'];
const DOMAINS = ['Core Banking', 'Digital Channels', 'Treasury', 'Risk Management', 'HR Systems', 'Infrastructure'];
const TEAMS = ['Platform Ops', 'DBA Team', 'Network Ops', 'Security Ops', 'App Support', 'DevOps', 'Cloud Ops'];
const STATUSES = ['Good', 'Degraded', 'Critical', 'Unknown'];
const PLATFORMS = ['Windows Server 2019', 'Windows Server 2022', 'RHEL 8', 'RHEL 9', 'Ubuntu 22.04', 'Oracle Linux', 'Kubernetes 1.28', 'Kubernetes 1.27', 'Docker', 'OracleDB 19c', 'PostgreSQL 15', 'MySQL 8.0', 'MongoDB 7'];
const LOCATIONS = ['DC1-Kuwait', 'DC2-Kuwait', 'DR-Bahrain', 'Azure-EastUS', 'AWS-EU-West', 'On-Premises'];
const SOURCES = ['SCOM', 'vROps', 'BMC', 'SolarWinds', 'Elastic', 'enterprise observability', 'Prometheus'];
const TAG_POOL = ['critical', 'payment', 'core', 'api', 'frontend', 'backend', 'database', 'cache', 'messaging', 'compliance', 'pci-dss', 'legacy', 'migration', 'monitored', 'prod-ready'];

const getAssetIcon = (type) => {
  const iconMap = {
    Server,
    VM: Cloud,
    DB: Database,
    LoadBalancer: Network,
    Firewall: Shield,
    Switch: Network,
    Router: Network,
    Storage: HardDrive,
    K8sCluster: Box,
    Namespace: Box,
    Pod: Box,
    Service: Globe,
    URL: Globe,
    Queue: Box,
  };
  return iconMap[type] || Server;
};

// ============= API DATA =============
const pageContent = (response) => response && Array.isArray(response.content) ? response.content : Array.isArray(response) ? response : [];
const normalizeAsset = (row) => ({
  id: String(row.id || row.resourceId || generateId()),
  type: row.type || row.resourceType || 'Server',
  name: row.name || row.resourceName || 'Unnamed resource',
  environment: row.environment || 'Production',
  domain: row.businessDomain || row.domain || '',
  ipOrHost: row.ipOrHost || row.hostname || row.ipAddress || '',
  location: row.location || '',
  platform: row.platform || row.os || '',
  ownerTeam: row.ownerTeam || row.team || '',
  tags: Array.isArray(row.tags) ? row.tags : [],
  onboardedSources: Array.isArray(row.onboardedSources) ? row.onboardedSources : [],
  healthScore: Number(row.healthScore || 0),
  status: row.status || 'Unknown',
  lastSeen: Date.parse(row.lastSeen || row.updatedAt || new Date().toISOString()),
  dependencies: Array.isArray(row.dependencies) ? row.dependencies : [],
  relatedAppIds: Array.isArray(row.relatedAppIds) ? row.relatedAppIds : [],
  metrics: row.metrics || null,
  openIncidents: row.openIncidents || { total: 0, critical: 0 },
  topFingerprint: row.topFingerprint || null,
  notes: row.notes || '',
});

const normalizeEdge = (row) => ({
  from: String(row.from || row.sourceId || ''),
  to: String(row.to || row.targetId || ''),
  kind: row.kind || row.relationshipType || 'depends_on',
  port: row.port || null,
});

// ============= STORE & CONTEXT =============
const StoreContext = createContext(null);

const useStore = () => {
  const context = useContext(StoreContext);
  if (!context) throw new Error('useStore must be used within StoreProvider');
  return context;
};

const StoreProvider = ({ children }) => {
  const [assets, setAssets] = useState([]);
  const [connectivity, setConnectivity] = useState([]);
  const [toasts, setToasts] = useState([]);

  useEffect(() => {
    let cancelled = false;
    async function loadInventory() {
      if (!window.APIClient || !APIClient.inventory) {
        if (!cancelled) { setAssets([]); setConnectivity([]); }
        return;
      }
      try {
        const [assetsResponse, topologyResponse] = await Promise.all([
          APIClient.inventory.list({ page: 0, size: 500 }),
          APIClient.inventory.topology ? APIClient.inventory.topology({ page: 0, size: 1000 }) : Promise.resolve([])
        ]);
        if (cancelled) return;
        setAssets(pageContent(assetsResponse).map(normalizeAsset));
        setConnectivity(pageContent(topologyResponse).map(normalizeEdge).filter(edge => edge.from && edge.to));
      } catch (error) {
        console.warn('[Inventory] Unable to load production inventory; rendering empty state.', error);
        if (!cancelled) { setAssets([]); setConnectivity([]); }
      }
    }
    loadInventory();
    return () => { cancelled = true; };
  }, []);

  const showToast = useCallback((message, type = 'info') => {
    const id = generateId();
    setToasts(prev => [...prev, { id, message, type }]);
    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), 3000);
  }, []);

  const getAssets = useCallback((filters = {}) => {
    return assets.filter(asset => {
      if (filters.type && asset.type !== filters.type) return false;
      if (filters.environment && asset.environment !== filters.environment) return false;
      if (filters.domain && asset.domain !== filters.domain) return false;
      if (filters.ownerTeam && asset.ownerTeam !== filters.ownerTeam) return false;
      if (filters.status && asset.status !== filters.status) return false;
      if (filters.source && !asset.onboardedSources.includes(filters.source)) return false;
      if (filters.tag && !asset.tags.includes(filters.tag)) return false;
      if (filters.search) {
        const search = String(filters.search).toLowerCase();
        const ipHost = asset.ipOrHost ? String(asset.ipOrHost).toLowerCase() : '';
        if (!asset.name.toLowerCase().includes(search) &&
          !asset.id.toLowerCase().includes(search) &&
          !ipHost.includes(search)) return false;
      }
      return true;
    });
  }, [assets]);

  const getKpis = useCallback(() => {
    const total = assets.length;
    const critical = assets.filter(a => a.status === 'Critical').length;
    const withIncidents = assets.filter(a => a.openIncidents.total > 0).length;
    const unknown = assets.filter(a => a.status === 'Unknown' || a.onboardedSources.length === 0).length;
    const k8sAssets = assets.filter(a => ['K8sCluster', 'Namespace', 'Pod', 'Service'].includes(a.type)).length;
    const withMultipleSources = assets.filter(a => a.onboardedSources.length >= 2).length;
    const coverage = total > 0 ? Math.round((withMultipleSources / total) * 100) : 0;
    return { total, critical, withIncidents, unknown, k8sAssets, coverage };
  }, [assets]);

  const getStatusBreakdown = useCallback(() => {
    return STATUSES.map(status => ({ name: status, value: assets.filter(a => a.status === status).length }));
  }, [assets]);

  const getTypeBreakdown = useCallback(() => {
    const grouped = {};
    assets.forEach(a => {
      if (a.status === 'Critical') grouped[a.type] = (grouped[a.type] || 0) + 1;
    });
    return Object.entries(grouped)
      .map(([type, count]) => ({ type, count }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 8);
  }, [assets]);

  const addAsset = useCallback((asset) => {
    const newAsset = { ...asset, id: `asset-${generateId()}`, lastSeen: Date.now() };
    setAssets(prev => [...prev, newAsset]);
    showToast(`Asset ${newAsset.name} added successfully`, 'success');
    return newAsset;
  }, [showToast]);

  const updateAsset = useCallback((id, updates) => {
    setAssets(prev => prev.map(a => a.id === id ? { ...a, ...updates } : a));
    showToast('Asset updated successfully', 'success');
  }, [showToast]);

  const deleteAsset = useCallback((id) => {
    const asset = assets.find(a => a.id === id);
    setAssets(prev => prev.filter(a => a.id !== id));
    setConnectivity(prev => prev.filter(e => e.from !== id && e.to !== id));
    showToast(`Asset ${asset?.name || id} deleted`, 'info');
  }, [assets, showToast]);

  const value = {
    assets,
    connectivity,
    getAssets,
    getKpis,
    getStatusBreakdown,
    getTypeBreakdown,
    addAsset,
    updateAsset,
    deleteAsset,
    toasts,
  };

  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>;
};

// ============= UI COMPONENTS =============
const Badge = ({ children, variant = 'default', size = 'sm', className = '' }) => {
  const baseStyle = {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '4px',
    fontWeight: 500,
    borderRadius: '6px',
    border: '1px solid',
  };

  const variantStyles = {
    default: { background: KFH.offWhite, color: KFH.textMuted, borderColor: '#E5E7EB' },
    critical: { background: KFH.criticalBg, color: KFH.critical, borderColor: '#FECACA' },
    warning: { background: KFH.warningBg, color: KFH.warning, borderColor: '#FDE68A' },
    success: { background: KFH.primaryLighter, color: KFH.primary, borderColor: '#A7F3D0' },
    info: { background: KFH.infoBg, color: KFH.info, borderColor: '#A5F3FC' },
    purple: { background: '#F5F3FF', color: '#7C3AED', borderColor: '#DDD6FE' },
  };

  const sizeStyles = {
    xs: { padding: '2px 6px', fontSize: '10px' },
    sm: { padding: '4px 8px', fontSize: '12px' },
    md: { padding: '6px 10px', fontSize: '14px' },
  };

  const style = {
    ...baseStyle,
    ...(variantStyles[variant] || variantStyles.default),
    ...(sizeStyles[size] || sizeStyles.sm),
  };

  return (
    <span style={style} className={className}>
      {children}
    </span>
  );
};

const StatusBadge = ({ status }) => {
  const variants = { Good: 'success', Degraded: 'warning', Critical: 'critical', Unknown: 'default' };
  return <Badge variant={variants[status] || 'default'} size="sm">{status}</Badge>;
};

const Button = ({ children, variant = 'default', size = 'md', onClick, disabled, className = '', type = 'button' }) => {
  const baseStyle = {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: '8px',
    fontWeight: 500,
    borderRadius: '8px',
    border: '1px solid',
    transition: 'all 0.2s ease',
    cursor: disabled ? 'not-allowed' : 'pointer',
    opacity: disabled ? 0.5 : 1,
  };

  const variantStyles = {
    default: { background: '#FFFFFF', color: '#374151', borderColor: '#E5E7EB' },
    primary: { background: KFH.primary, color: '#FFFFFF', borderColor: 'transparent', boxShadow: '0 4px 6px rgba(18,135,84,0.2)' },
    success: { background: KFH.primary, color: '#FFFFFF', borderColor: 'transparent', boxShadow: '0 4px 6px rgba(18,135,84,0.2)' },
    danger: { background: KFH.critical, color: '#FFFFFF', borderColor: 'transparent' },
    ghost: { background: 'transparent', color: '#4B5563', borderColor: 'transparent' },
    outline: { background: '#FFFFFF', color: '#374151', borderColor: '#E5E7EB' },
  };

  const sizeStyles = {
    xs: { padding: '4px 8px', fontSize: '12px' },
    sm: { padding: '6px 12px', fontSize: '13px' },
    md: { padding: '8px 16px', fontSize: '14px' },
    lg: { padding: '10px 20px', fontSize: '15px' },
  };

  const style = {
    ...baseStyle,
    ...(variantStyles[variant] || variantStyles.default),
    ...(sizeStyles[size] || sizeStyles.md),
  };

  return (
    <button
      onClick={onClick}
      disabled={disabled}
      type={type}
      style={style}
      className={className}
    >
      {children}
    </button>
  );
};

const Input = ({ label, value, onChange, placeholder, type = 'text', className = '', required = false }) => (
  <div className="space-y-1">
    {label && <label className="block text-xs font-medium text-gray-600">{label}</label>}
    <input
      type={type}
      value={value}
      onChange={onChange}
      placeholder={placeholder}
      required={required}
      className={`w-full px-3 py-2 bg-white border border-gray-200 rounded-[8px] text-sm text-gray-900 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-[rgba(18,135,84,0.2)] focus:border-[${KFH.primary}] ${className}`}
    />
  </div>
);

const Select = ({ label, value, onChange, options, className = '', disabled = false }) => (
  <div className="space-y-1">
    {label && <label className="block text-xs font-medium text-gray-600">{label}</label>}
    <select
      value={value}
      onChange={onChange}
      disabled={disabled}
      className={`w-full px-3 py-2 bg-white border border-gray-200 rounded-[8px] text-sm text-gray-900 focus:outline-none focus:ring-2 focus:ring-[rgba(18,135,84,0.2)] focus:border-[${KFH.primary}] cursor-pointer ${className} ${disabled ? 'opacity-50 cursor-not-allowed' : ''}`}
    >
      {options.map((opt) => (
        <option key={opt.value} value={opt.value}>{opt.label}</option>
      ))}
    </select>
  </div>
);

const KpiCard = ({ label, value, icon, variant = 'default', className = '' }) => {
  const iconBg = {
    info: KFH.infoBg,
    critical: KFH.criticalBg,
    warning: KFH.warningBg,
    success: KFH.primaryLighter,
    default: KFH.offWhite,
  }[variant] || KFH.offWhite;

  const iconColor = {
    info: KFH.info,
    critical: KFH.critical,
    warning: KFH.warning,
    success: KFH.primary,
    default: KFH.textMuted,
  }[variant] || KFH.textMuted;

  return (
    <div className={`kfh-card p-5 ${className}`}>
      <div className="flex items-start justify-between mb-2">
        <span className="text-xs font-bold text-gray-500 uppercase tracking-wider">{label}</span>
        {icon && (
          <span className="inline-flex items-center justify-center rounded-[10px]" style={{ width: 36, height: 36, background: iconBg, color: iconColor }}>
            {icon}
          </span>
        )}
      </div>
      <span className="text-3xl font-bold" style={{ color: KFH.textMain }}>{value}</span>
    </div>
  );
};

const HealthBar = ({ score }) => {
  const color = score > 80 ? KFH.primary : score > 50 ? KFH.warning : score > 0 ? KFH.critical : '#9CA3AF';
  return (
    <div className="health-bar">
      <div className="health-bar-fill" style={{ width: `${score}%`, background: color }} />
    </div>
  );
};

const Toast = ({ message, type, onClose }) => {
  const variants = {
    success: { bg: KFH.primaryLighter, border: KFH.primary, text: KFH.primary },
    error: { bg: KFH.criticalBg, border: KFH.critical, text: KFH.critical },
    warning: { bg: KFH.warningBg, border: KFH.warning, text: KFH.warning },
    info: { bg: KFH.infoBg, border: KFH.info, text: KFH.info },
  };
  const v = variants[type] || variants.info;

  return (
    <div className="toast flex items-center gap-3 px-4 py-3 rounded-[8px] border shadow-xl" style={{ background: v.bg, borderColor: v.border, color: v.text }}>
      {type === 'success' && <CheckCircle size={16} />}
      {type === 'error' && <AlertCircle size={16} />}
      {type === 'warning' && <AlertTriangle size={16} />}
      {type === 'info' && <AlertCircle size={16} />}
      <span className="text-sm font-medium flex-1" style={{ color: KFH.textMain }}>{message}</span>
      <button onClick={onClose} className="hover:opacity-70 transition-opacity" aria-label="Close">
        <X size={16} />
      </button>
    </div>
  );
};

const ToastContainer = ({ toasts, onDismiss }) => (
  <div className="fixed top-4 right-4 z-50 space-y-2 max-w-md">
    {toasts.map((t) => (
      <Toast key={t.id} message={t.message} type={t.type} onClose={() => onDismiss(t.id)} />
    ))}
  </div>
);

// ============= ASSET COMPONENTS =============
const AssetCard = ({ asset, onClick }) => {
  const IconCmp = getAssetIcon(asset.type);
  const iconBg = asset.status === 'Critical' ? KFH.criticalBg : asset.status === 'Degraded' ? KFH.warningBg : KFH.primaryLight;
  const iconColor = asset.status === 'Critical' ? KFH.critical : asset.status === 'Degraded' ? KFH.warning : KFH.primary;

  return (
    <div className="kfh-card p-5 cursor-pointer" onClick={onClick} role="button" tabIndex={0}>
      <div className="flex items-start justify-between mb-3">
        <div className="flex items-center gap-3 min-w-0">
          <div className="p-2 rounded-[10px]" style={{ background: iconBg, color: iconColor }}>
            <IconCmp size={16} />
          </div>
          <div className="flex-1 min-w-0">
            <h3 className="text-sm font-semibold truncate" style={{ color: KFH.textMain }}>{asset.name}</h3>
            <p className="text-xs font-medium" style={{ color: KFH.textMuted }}>{asset.type}</p>
          </div>
        </div>
        <StatusBadge status={asset.status} />
      </div>

      <div className="space-y-2 mb-3">
        <div className="flex items-center justify-between text-xs">
          <span style={{ color: KFH.textMuted }}>Health Score</span>
          <span className="font-semibold" style={{ color: KFH.textMain }}>{asset.healthScore}%</span>
        </div>
        <HealthBar score={asset.healthScore} />
      </div>

      <div className="space-y-2 text-xs">
        <div className="flex items-center gap-2" style={{ color: KFH.textMuted }}>
          <Badge size="xs" variant="default">{asset.environment}</Badge>
          <span>•</span>
          <span className="truncate">{asset.domain}</span>
        </div>

        {asset.openIncidents.total > 0 && (
          <div className="flex items-center gap-2">
            <AlertTriangle style={{ color: KFH.critical }} size={12} />
            <span style={{ color: KFH.critical }} className="font-medium">
              {asset.openIncidents.critical > 0 && `${asset.openIncidents.critical} Critical`}
              {asset.openIncidents.critical > 0 && asset.openIncidents.total > asset.openIncidents.critical && ', '}
              {asset.openIncidents.total > asset.openIncidents.critical && `${asset.openIncidents.total - asset.openIncidents.critical} Other`}
            </span>
          </div>
        )}

        {asset.onboardedSources.length > 0 && (
          <div className="flex items-center gap-1 flex-wrap">
            {asset.onboardedSources.slice(0, 3).map((src) => (
              <Badge key={src} size="xs" variant="info">{src}</Badge>
            ))}
            {asset.onboardedSources.length > 3 && (
              <span style={{ color: KFH.textMuted }}>+{asset.onboardedSources.length - 3}</span>
            )}
          </div>
        )}

        <div className="flex items-center gap-1 flex-wrap">
          {asset.tags.slice(0, 3).map((tag) => (
            <div key={tag} className="inline-flex items-center gap-0.5 px-1.5 py-0.5 rounded text-[10px]" style={{ background: '#F5F3FF', color: '#7C3AED' }}>
              <Tag size={10} />
              {tag}
            </div>
          ))}
          {asset.tags.length > 3 && (
            <span className="text-[10px]" style={{ color: KFH.textMuted }}>+{asset.tags.length - 3}</span>
          )}
        </div>
      </div>
    </div>
  );
};

const AssetsTable = ({ assets, onRowClick, sortConfig, onSort }) => {
  const handleSort = (key) => onSort(key);

  const headers = [
    { key: 'type', label: 'Type' },
    { key: 'name', label: 'Name' },
    { key: 'environment', label: 'Env' },
    { key: 'domain', label: 'Domain' },
    { key: 'status', label: 'Status' },
    { key: 'healthScore', label: 'Health', align: 'center' },
    { key: null, label: 'Incidents', align: 'center' },
    { key: null, label: 'Team' },
    { key: null, label: 'Sources' },
    { key: 'lastSeen', label: 'Last Seen' },
    { key: null, label: 'Actions', align: 'center' },
  ];

  return (
    <div className="overflow-x-auto">
      <table className="w-full">
        <thead>
          <tr style={{ background: KFH.offWhite, borderBottom: `1px solid ${KFH.border}` }}>
            {headers.map((h) => (
              <th
                key={h.label}
                onClick={h.key ? () => handleSort(h.key) : undefined}
                className={`px-4 py-3 text-xs font-bold uppercase tracking-wider ${h.align === 'center' ? 'text-center' : 'text-left'} ${h.key ? 'cursor-pointer' : ''}`}
                style={{ color: KFH.textMuted }}
              >
                {h.label}{' '}
                {h.key && sortConfig.key === h.key && (sortConfig.direction === 'asc' ? '↑' : '↓')}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {assets.map((asset, idx) => {
            const IconCmp = getAssetIcon(asset.type);
            return (
              <tr
                key={asset.id}
                className="cursor-pointer"
                style={{ borderBottom: `1px solid ${KFH.border}`, background: idx % 2 === 0 ? '#FFFFFF' : '#FCFCFD' }}
                onClick={() => onRowClick(asset)}
              >
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
                    <IconCmp size={16} style={{ color: KFH.textMuted }} />
                    <span className="text-xs" style={{ color: KFH.textMain }}>{asset.type}</span>
                  </div>
                </td>
                <td className="px-4 py-3">
                  <div className="max-w-xs">
                    <div className="text-sm truncate" style={{ color: KFH.textMain }}>{asset.name}</div>
                    {asset.ipOrHost && <div className="text-xs font-mono truncate" style={{ color: KFH.textMuted }}>{asset.ipOrHost}</div>}
                  </div>
                </td>
                <td className="px-4 py-3"><Badge size="xs" variant="default">{asset.environment}</Badge></td>
                <td className="px-4 py-3"><span className="text-xs" style={{ color: KFH.textMuted }}>{asset.domain}</span></td>
                <td className="px-4 py-3"><StatusBadge status={asset.status} /></td>
                <td className="px-4 py-3">
                  <div className="flex flex-col items-center gap-1">
                    <span className="text-sm font-semibold" style={{ color: KFH.textMain }}>{asset.healthScore}%</span>
                    <div className="w-16"><HealthBar score={asset.healthScore} /></div>
                  </div>
                </td>
                <td className="px-4 py-3 text-center">
                  {asset.openIncidents.total > 0 ? (
                    <div className="text-xs">
                      {asset.openIncidents.critical > 0 && (
                        <span style={{ color: KFH.critical }} className="font-semibold">{asset.openIncidents.critical}C</span>
                      )}
                      {asset.openIncidents.critical > 0 && asset.openIncidents.total > asset.openIncidents.critical && (
                        <span style={{ color: KFH.textMuted }}> / </span>
                      )}
                      {asset.openIncidents.total > asset.openIncidents.critical && (
                        <span style={{ color: KFH.textMuted }}>{asset.openIncidents.total - asset.openIncidents.critical}O</span>
                      )}
                    </div>
                  ) : (
                    <span className="text-xs" style={{ color: KFH.textMuted }}>-</span>
                  )}
                </td>
                <td className="px-4 py-3"><span className="text-xs" style={{ color: KFH.textMuted }}>{asset.ownerTeam}</span></td>
                <td className="px-4 py-3">
                  <div className="flex flex-wrap gap-1">
                    {asset.onboardedSources.slice(0, 2).map((src) => (
                      <Badge key={src} size="xs" variant="info">{src}</Badge>
                    ))}
                    {asset.onboardedSources.length > 2 && (
                      <span className="text-xs" style={{ color: KFH.textMuted }}>+{asset.onboardedSources.length - 2}</span>
                    )}
                  </div>
                </td>
                <td className="px-4 py-3"><span className="text-xs" style={{ color: KFH.textMuted }}>{new Date(asset.lastSeen).toLocaleDateString()}</span></td>
                <td className="px-4 py-3 text-center">
                  <Button size="xs" variant="ghost" onClick={(e) => { e.stopPropagation(); onRowClick(asset); }}>
                    <Eye size={16} />
                  </Button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
};

const TopologyView = ({ assets, connectivity, selectedAsset, onAssetClick }) => {
  if (!selectedAsset) {
    return (
      <div className="flex items-center justify-center h-96" style={{ color: KFH.textMuted }}>
        <div className="text-center">
          <Network className="w-16 h-16 mx-auto mb-4 opacity-50" />
          <p>Select an asset to view its topology</p>
        </div>
      </div>
    );
  }

  const edges = connectivity.filter((e) => e.from === selectedAsset.id || e.to === selectedAsset.id);
  const relatedIds = new Set();
  edges.forEach((e) => { relatedIds.add(e.from); relatedIds.add(e.to); });
  const relatedAssets = assets.filter((a) => relatedIds.has(a.id));

  const statusColor = (s) => s === 'Critical' ? KFH.critical : s === 'Degraded' ? KFH.warning : KFH.primary;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-bold" style={{ color: KFH.textMain }}>Topology: {selectedAsset.name}</h3>
        <Badge variant="info" size="sm">{relatedAssets.length} connected assets</Badge>
      </div>

      <svg className="w-full rounded-[16px]" viewBox="0 0 1000 600" style={{ minHeight: '600px', background: KFH.card, border: `1px solid ${KFH.border}` }}>
        <g transform="translate(500, 300)">
          <circle r="60" fill={statusColor(selectedAsset.status)} opacity="0.15" />
          <circle r="50" fill="#FFFFFF" stroke={statusColor(selectedAsset.status)} strokeWidth="3" className="topology-node" />
          <text y="-10" textAnchor="middle" fill={KFH.textMain} fontSize="12" fontWeight="700">{selectedAsset.name.slice(0, 20)}</text>
          <text y="8" textAnchor="middle" fill={KFH.textMuted} fontSize="10">{selectedAsset.type}</text>
          <text y="24" textAnchor="middle" fill={KFH.textMuted} fontSize="9">{selectedAsset.healthScore}%</text>
        </g>

        {relatedAssets.slice(0, 12).map((asset, idx) => {
          if (asset.id === selectedAsset.id) return null;
          const angle = (idx / Math.max(relatedAssets.length - 1, 1)) * 2 * Math.PI;
          const radius = 220;
          const cx = 500 + Math.cos(angle) * radius;
          const cy = 300 + Math.sin(angle) * radius;
          const color = statusColor(asset.status);

          return (
            <g key={asset.id}>
              <line x1="500" y1="300" x2={cx} y2={cy} stroke={KFH.gold} strokeWidth="2" strokeDasharray="5,5" opacity="0.5" />
              <g transform={`translate(${cx}, ${cy})`} className="topology-node cursor-pointer" onClick={() => onAssetClick(asset)}>
                <circle r="35" fill="#FFFFFF" stroke={color} strokeWidth="2" />
                <text y="0" textAnchor="middle" fill={KFH.textMain} fontSize="9" fontWeight="600">{asset.name.slice(0, 12)}</text>
                <text y="12" textAnchor="middle" fill={KFH.textMuted} fontSize="8">{asset.type}</text>
              </g>
            </g>
          );
        })}
      </svg>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        {[{
          label: 'Direct Connections',
          value: edges.length
        }, {
          label: 'Related Assets',
          value: Math.max(relatedAssets.length - 1, 0)
        }, {
          label: 'Critical Nodes',
          value: relatedAssets.filter((a) => a.status === 'Critical').length,
          valueColor: KFH.critical,
        }, {
          label: 'Avg Health',
          value: `${Math.round(relatedAssets.reduce((sum, a) => sum + a.healthScore, 0) / Math.max(relatedAssets.length, 1))}%`
        }].map((k) => (
          <div key={k.label} className="kfh-card p-4">
            <span className="text-xs font-bold uppercase tracking-wider" style={{ color: KFH.textMuted }}>{k.label}</span>
            <div className="text-xl font-bold" style={{ color: k.valueColor || KFH.textMain }}>{k.value}</div>
          </div>
        ))}
      </div>
    </div>
  );
};

const AssetDrawer = ({ asset, onClose, onUpdate }) => {
  const [activeTab, setActiveTab] = useState('overview');
  const [editMode, setEditMode] = useState(false);
  const [formData, setFormData] = useState({ ...asset });

  if (!asset) return null;

  const handleSave = () => {
    onUpdate(asset.id, formData);
    setEditMode(false);
  };

  const handleReset = () => {
    setFormData({ ...asset });
    setEditMode(false);
  };

  const IconCmp = getAssetIcon(asset.type);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-end" style={{ background: 'rgba(0,0,0,0.35)' }} onClick={onClose}>
      <div className="drawer-enter w-full max-w-3xl h-full overflow-y-auto" style={{ background: KFH.card, borderLeft: `1px solid ${KFH.border}` }} onClick={(e) => e.stopPropagation()}>
        <div className="sticky top-0 z-10 p-6" style={{ background: KFH.card, borderBottom: `1px solid ${KFH.border}` }}>
          <div className="flex items-start justify-between mb-4">
            <div className="flex items-center gap-3">
              <div className="p-3 rounded-[12px]" style={{ background: KFH.primaryLight, color: KFH.primary }}>
                <IconCmp size={20} />
              </div>
              <div>
                <h2 className="text-xl font-bold" style={{ color: KFH.textMain }}>{asset.name}</h2>
                <p className="text-sm font-mono" style={{ color: KFH.textMuted }}>{asset.id}</p>
              </div>
            </div>
            <button onClick={onClose} className="hover:opacity-70 transition-colors" style={{ color: KFH.textMuted }} aria-label="Close">
              <X size={20} />
            </button>
          </div>

          <div className="flex items-center gap-2 mb-4 flex-wrap">
            <StatusBadge status={asset.status} />
            <Badge variant="default">{asset.type}</Badge>
            <Badge variant="info">{asset.environment}</Badge>
            {asset.openIncidents.total > 0 && (
              <Badge variant="critical">
                <AlertTriangle size={12} />
                {asset.openIncidents.critical} Critical, {asset.openIncidents.total} Total
              </Badge>
            )}
          </div>

          <div className="flex gap-1 rounded-[8px] p-1" style={{ background: KFH.offWhite, border: `1px solid ${KFH.border}` }}>
            {['overview', 'metrics', 'incidents', 'dependencies', 'configuration'].map((tab) => (
              <button
                key={tab}
                onClick={() => setActiveTab(tab)}
                className="px-4 py-2 text-sm font-medium rounded-[8px] transition-all capitalize"
                style={activeTab === tab ? { background: KFH.primary, color: '#fff' } : { color: KFH.textMuted }}
              >
                {tab}
              </button>
            ))}
          </div>
        </div>

        <div className="p-6" style={{ background: KFH.bgBody }}>
          {activeTab === 'overview' && (
            <div className="space-y-6">
              <div className="kfh-card p-6">
                <h3 className="text-sm font-bold mb-4" style={{ color: KFH.textMain }}>Asset Details</h3>
                <div className="grid grid-cols-2 gap-4 text-sm">
                  {[
                    ['Domain', asset.domain],
                    ['Environment', asset.environment],
                    ['Platform', asset.platform],
                    ['Location', asset.location],
                    ['Owner Team', asset.ownerTeam],
                  ].map(([k, v]) => (
                    <div key={k}>
                      <span style={{ color: KFH.textMuted }}>{k}</span>
                      <div className="font-medium" style={{ color: KFH.textMain }}>{v}</div>
                    </div>
                  ))}

                  {asset.ipOrHost && (
                    <div>
                      <span style={{ color: KFH.textMuted }}>IP/Host</span>
                      <div className="font-mono text-xs" style={{ color: KFH.textMain }}>{asset.ipOrHost}</div>
                    </div>
                  )}

                  <div>
                    <span style={{ color: KFH.textMuted }}>Health Score</span>
                    <div className="flex items-center gap-2">
                      <span className="font-bold" style={{ color: KFH.textMain }}>{asset.healthScore}%</span>
                      <div className="flex-1"><HealthBar score={asset.healthScore} /></div>
                    </div>
                  </div>

                  <div>
                    <span style={{ color: KFH.textMuted }}>Last Seen</span>
                    <div className="text-xs" style={{ color: KFH.textMain }}>{new Date(asset.lastSeen).toLocaleString()}</div>
                  </div>
                </div>
              </div>

              <div className="kfh-card p-6">
                <h3 className="text-sm font-bold mb-3" style={{ color: KFH.textMain }}>Monitoring Sources</h3>
                {asset.onboardedSources.length > 0 ? (
                  <div className="flex flex-wrap gap-2">
                    {asset.onboardedSources.map((src) => <Badge key={src} variant="info">{src}</Badge>)}
                  </div>
                ) : (
                  <p className="text-sm" style={{ color: KFH.textMuted }}>No sources configured</p>
                )}
              </div>

              <div className="kfh-card p-6">
                <h3 className="text-sm font-bold mb-3" style={{ color: KFH.textMain }}>Tags</h3>
                <div className="flex flex-wrap gap-2">
                  {asset.tags.map((tag) => (
                    <div key={tag} className="inline-flex items-center gap-1 px-2 py-1 rounded text-xs" style={{ background: '#F5F3FF', color: '#7C3AED' }}>
                      <Tag size={12} />
                      {tag}
                    </div>
                  ))}
                </div>
              </div>

              <div className="kfh-card p-6">
                <h3 className="text-sm font-bold mb-3" style={{ color: KFH.textMain }}>Related Applications</h3>
                {asset.relatedAppIds.length > 0 ? (
                  <div className="space-y-2">
                    {asset.relatedAppIds.map((appId) => (
                      <div key={appId} className="flex items-center gap-2 p-2 rounded" style={{ background: KFH.offWhite }}>
                        <LinkIcon style={{ color: KFH.textMuted }} size={14} />
                        <span className="text-sm font-mono" style={{ color: KFH.textMain }}>{appId}</span>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-sm" style={{ color: KFH.textMuted }}>No related applications</p>
                )}
              </div>
            </div>
          )}

          {activeTab === 'metrics' && (
            <div className="space-y-6">
              {asset.metrics ? (
                <>
                  {(asset.metrics.cpu !== undefined || asset.metrics.mem !== undefined || asset.metrics.disk !== undefined) && (
                    <div className="kfh-card p-6">
                      <h3 className="text-sm font-bold mb-4" style={{ color: KFH.textMain }}>Resource Utilization</h3>
                      <div className="space-y-4">
                        {asset.metrics.cpu !== undefined && (
                          <div>
                            <div className="flex justify-between text-xs mb-1">
                              <span style={{ color: KFH.textMuted }}>CPU Usage</span>
                              <span className="font-semibold" style={{ color: KFH.textMain }}>{asset.metrics.cpu}%</span>
                            </div>
                            <HealthBar score={100 - asset.metrics.cpu} />
                          </div>
                        )}
                        {asset.metrics.mem !== undefined && (
                          <div>
                            <div className="flex justify-between text-xs mb-1">
                              <span style={{ color: KFH.textMuted }}>Memory Usage</span>
                              <span className="font-semibold" style={{ color: KFH.textMain }}>{asset.metrics.mem}%</span>
                            </div>
                            <HealthBar score={100 - asset.metrics.mem} />
                          </div>
                        )}
                        {asset.metrics.disk !== undefined && (
                          <div>
                            <div className="flex justify-between text-xs mb-1">
                              <span style={{ color: KFH.textMuted }}>Disk Usage</span>
                              <span className="font-semibold" style={{ color: KFH.textMain }}>{asset.metrics.disk}%</span>
                            </div>
                            <HealthBar score={100 - asset.metrics.disk} />
                          </div>
                        )}
                      </div>
                    </div>
                  )}

                  {(asset.metrics.qps !== undefined || asset.metrics.latency !== undefined) && (
                    <div className="kfh-card p-6">
                      <h3 className="text-sm font-bold mb-4" style={{ color: KFH.textMain }}>Performance Metrics</h3>
                      <div className="grid grid-cols-2 gap-4">
                        {asset.metrics.qps !== undefined && (
                          <div className="rounded-[16px] p-4" style={{ background: KFH.offWhite }}>
                            <span className="text-xs" style={{ color: KFH.textMuted }}>Queries/sec</span>
                            <div className="text-2xl font-bold" style={{ color: KFH.textMain }}>{asset.metrics.qps}</div>
                          </div>
                        )}
                        {asset.metrics.latency !== undefined && (
                          <div className="rounded-[16px] p-4" style={{ background: KFH.offWhite }}>
                            <span className="text-xs" style={{ color: KFH.textMuted }}>Latency (ms)</span>
                            <div className="text-2xl font-bold" style={{ color: KFH.textMain }}>{asset.metrics.latency}</div>
                          </div>
                        )}
                      </div>
                    </div>
                  )}
                </>
              ) : (
                <div className="text-center py-12" style={{ color: KFH.textMuted }}>
                  <Activity className="w-12 h-12 mx-auto mb-4 opacity-50" />
                  <p>No metrics available for this asset type</p>
                </div>
              )}
            </div>
          )}

          {activeTab === 'incidents' && (
            <div className="kfh-card p-6">
              <h3 className="text-sm font-bold mb-4" style={{ color: KFH.textMain }}>Open Incidents ({asset.openIncidents.total})</h3>
              {asset.openIncidents.total > 0 ? (
                <div className="space-y-3">
                  <div className="flex items-center gap-3 p-3 rounded-[16px]" style={{ background: KFH.offWhite }}>
                    <AlertTriangle style={{ color: asset.openIncidents.critical > 0 ? KFH.critical : KFH.warning }} size={16} />
                    <div className="flex-1">
                      <div className="text-sm" style={{ color: KFH.textMain }}>
                        {asset.openIncidents.total} open production incident{asset.openIncidents.total === 1 ? '' : 's'} linked to {asset.name}
                      </div>
                      <div className="text-xs" style={{ color: KFH.textMuted }}>
                        {asset.openIncidents.critical} critical • open the incident page for tenant-scoped details
                      </div>
                    </div>
                    <Button size="xs" variant="ghost" onClick={() => {
                      if (window.Router && typeof window.Router.navigate === 'function') {
                        window.Router.navigate('incidents');
                      } else {
                        window.location.hash = '#incidents';
                      }
                    }}>
                      <Eye size={16} />
                    </Button>
                  </div>
                </div>
              ) : (
                <div className="text-center py-8" style={{ color: KFH.textMuted }}>
                  <CheckCircle className="w-12 h-12 mx-auto mb-3 opacity-50" />
                  <p>No open incidents</p>
                </div>
              )}
            </div>
          )}

          {activeTab === 'dependencies' && (
            <div className="kfh-card p-6">
              <h3 className="text-sm font-bold mb-4" style={{ color: KFH.textMain }}>Dependencies ({asset.dependencies.length})</h3>
              {asset.dependencies.length > 0 ? (
                <div className="space-y-2">
                  {asset.dependencies.slice(0, 10).map((depId) => (
                    <div key={depId} className="flex items-center gap-2 p-2 rounded" style={{ background: KFH.offWhite }}>
                      <GitBranch style={{ color: KFH.textMuted }} size={16} />
                      <span className="text-sm font-mono" style={{ color: KFH.textMain }}>{depId}</span>
                    </div>
                  ))}
                  {asset.dependencies.length > 10 && (
                    <p className="text-xs text-center pt-2" style={{ color: KFH.textMuted }}>+{asset.dependencies.length - 10} more dependencies</p>
                  )}
                </div>
              ) : (
                <div className="text-center py-8" style={{ color: KFH.textMuted }}>
                  <p>No dependencies configured</p>
                </div>
              )}
            </div>
          )}

          {activeTab === 'configuration' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-bold" style={{ color: KFH.textMain }}>Asset Configuration</h3>
                {!editMode ? (
                  <Button size="sm" variant="primary" onClick={() => setEditMode(true)}>
                    <Edit size={14} />
                    Edit
                  </Button>
                ) : (
                  <div className="flex gap-2">
                    <Button size="sm" variant="success" onClick={handleSave}>
                      <Save size={14} />
                      Save
                    </Button>
                    <Button size="sm" variant="ghost" onClick={handleReset}>Reset</Button>
                  </div>
                )}
              </div>

              <div className="kfh-card p-6 space-y-4">
                <Select
                  label="Owner Team"
                  value={formData.ownerTeam}
                  onChange={(e) => setFormData((prev) => ({ ...prev, ownerTeam: e.target.value }))
                  }
                  options={TEAMS.map(t => ({ value: t, label: t }))}
                  disabled={!editMode}
                />

                <Select
                  label="Environment"
                  value={formData.environment}
                  onChange={(e) => setFormData((prev) => ({ ...prev, environment: e.target.value }))
                  }
                  options={ENVIRONMENTS.map(v => ({ value: v, label: v }))}
                  disabled={!editMode}
                />

                <div>
                  <label className="block text-xs font-medium mb-2" style={{ color: KFH.textMuted }}>Monitoring Sources</label>
                  <div className="space-y-2">
                    {SOURCES.map((src) => (
                      <label key={src} className="flex items-center gap-2 text-sm">
                        <input
                          type="checkbox"
                          checked={formData.onboardedSources.includes(src)}
                          onChange={(e) => {
                            if (e.target.checked) {
                              setFormData((prev) => ({ ...prev, onboardedSources: [...prev.onboardedSources, src] }));
                            } else {
                              setFormData((prev) => ({ ...prev, onboardedSources: prev.onboardedSources.filter((s) => s !== src) }));
                            }
                          }}
                          disabled={!editMode}
                          className="rounded"
                        />
                        <span style={{ color: editMode ? KFH.textMain : KFH.textMuted }}>{src}</span>
                      </label>
                    ))}
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-medium mb-1" style={{ color: KFH.textMuted }}>Notes</label>
                  <textarea
                    value={formData.notes}
                    onChange={(e) => setFormData((prev) => ({ ...prev, notes: e.target.value }))
                    }
                    disabled={!editMode}
                    rows={4}
                    className="w-full px-3 py-2 bg-white border border-gray-200 rounded-[8px] text-sm text-gray-900 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-[rgba(18,135,84,0.2)]"
                    placeholder="Add notes about this asset..."
                  />
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

const AssetForm = ({ onSubmit, onCancel, editAsset = null }) => {
  const [formData, setFormData] = useState(editAsset || {
    type: 'Server',
    name: '',
    environment: 'Production',
    domain: DOMAINS[0],
    platform: PLATFORMS[0],
    location: LOCATIONS[0],
    ownerTeam: TEAMS[0],
    ipOrHost: '',
    tags: [],
    onboardedSources: [],
    notes: '',
  });
  const [newTag, setNewTag] = useState('');

  const handleSubmit = (e) => {
    e.preventDefault();
    const asset = {
      ...formData,
      healthScore: 0,
      status: 'Unknown',
      lastSeen: Date.now(),
      dependencies: [],
      relatedAppIds: [],
      metrics: null,
      openIncidents: { total: 0, critical: 0 },
      topFingerprint: null,
    };
    onSubmit(asset);
  };

  const addTag = () => {
    if (newTag && !formData.tags.includes(newTag)) {
      setFormData((prev) => ({ ...prev, tags: [...prev.tags, newTag] }));
      setNewTag('');
    }
  };

  const removeTag = (tag) => {
    setFormData((prev) => ({ ...prev, tags: prev.tags.filter((t) => t !== tag) }));
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ background: 'rgba(0,0,0,0.35)' }} onClick={onCancel}>
      <div className="kfh-card w-full max-w-2xl max-h-[90vh] overflow-y-auto p-6" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-xl font-bold" style={{ color: KFH.textMain }}>{editAsset ? 'Edit Asset' : 'Add New Asset'}</h2>
          <button onClick={onCancel} className="hover:opacity-70 transition-colors" style={{ color: KFH.textMuted }} aria-label="Close">
            <X size={20} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <Select
              label="Asset Type"
              value={formData.type}
              onChange={(e) => setFormData((prev) => ({ ...prev, type: e.target.value }))
              }
              options={ASSET_TYPES.map(t => ({ value: t, label: t }))}
            />

            <Input
              label="Name"
              value={formData.name}
              onChange={(e) => setFormData((prev) => ({ ...prev, name: e.target.value }))
              }
              placeholder="Enter asset name"
              required
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <Select
              label="Environment"
              value={formData.environment}
              onChange={(e) => setFormData((prev) => ({ ...prev, environment: e.target.value }))
              }
              options={ENVIRONMENTS.map(v => ({ value: v, label: v }))}
            />

            <Select
              label="Domain"
              value={formData.domain}
              onChange={(e) => setFormData((prev) => ({ ...prev, domain: e.target.value }))
              }
              options={DOMAINS.map(v => ({ value: v, label: v }))}
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <Select
              label="Platform"
              value={formData.platform}
              onChange={(e) => setFormData((prev) => ({ ...prev, platform: e.target.value }))
              }
              options={PLATFORMS.map(v => ({ value: v, label: v }))}
            />

            <Select
              label="Location"
              value={formData.location}
              onChange={(e) => setFormData((prev) => ({ ...prev, location: e.target.value }))
              }
              options={LOCATIONS.map(v => ({ value: v, label: v }))}
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <Select
              label="Owner Team"
              value={formData.ownerTeam}
              onChange={(e) => setFormData((prev) => ({ ...prev, ownerTeam: e.target.value }))
              }
              options={TEAMS.map(v => ({ value: v, label: v }))}
            />

            <Input
              label="IP/Host (optional)"
              value={formData.ipOrHost}
              onChange={(e) => setFormData((prev) => ({ ...prev, ipOrHost: e.target.value }))
              }
              placeholder="e.g., 10.0.0.1 or hostname.kfh.com"
            />
          </div>

          <div>
            <label className="block text-xs font-medium mb-2" style={{ color: KFH.textMuted }}>Tags</label>
            <div className="flex gap-2 mb-2">
              <input
                type="text"
                value={newTag}
                onChange={(e) => setNewTag(e.target.value)}
                onKeyPress={(e) => e.key === 'Enter' && (e.preventDefault(), addTag())}
                placeholder="Add tag..."
                className="flex-1 px-3 py-2 bg-white border border-gray-200 rounded-[8px] text-sm text-gray-900 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-[rgba(18,135,84,0.2)]"
              />
              <Button type="button" size="sm" onClick={addTag}>
                <Plus size={16} />
              </Button>
            </div>
            <div className="flex flex-wrap gap-2">
              {formData.tags.map((tag) => (
                <div key={tag} className="inline-flex items-center gap-1 px-2 py-1 rounded text-xs" style={{ background: '#F5F3FF', color: '#7C3AED' }}>
                  <Tag size={12} />
                  {tag}
                  <button type="button" onClick={() => removeTag(tag)} className="ml-1 hover:opacity-70" aria-label="Remove tag">
                    <X style={{ width: '12px', height: '12px' }} />
                  </button>
                </div>
              ))}
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium mb-2" style={{ color: KFH.textMuted }}>Monitoring Sources</label>
            <div className="grid grid-cols-2 gap-2">
              {SOURCES.map((src) => (
                <label key={src} className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={formData.onboardedSources.includes(src)}
                    onChange={(e) => {
                      if (e.target.checked) {
                        setFormData((prev) => ({ ...prev, onboardedSources: [...prev.onboardedSources, src] }));
                      } else {
                        setFormData((prev) => ({ ...prev, onboardedSources: prev.onboardedSources.filter((s) => s !== src) }));
                      }
                    }}
                    className="rounded"
                  />
                  <span style={{ color: KFH.textMain }}>{src}</span>
                </label>
              ))}
            </div>
          </div>

          <div className="flex gap-3 pt-4">
            <Button type="submit" variant="primary" size="md" className="flex-1">
              <Save size={16} />
              {editAsset ? 'Update Asset' : 'Create Asset'}
            </Button>
            <Button type="button" variant="ghost" size="md" onClick={onCancel}>
              Cancel
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};

// ============= INVENTORY PAGE =============
const InventoryPage = () => {
  const { getAssets, getKpis, getStatusBreakdown, getTypeBreakdown, addAsset, updateAsset, connectivity } = useStore();
  const [viewMode, setViewMode] = useState('cards');
  const [filters, setFilters] = useState({ type: '', environment: '', domain: '', ownerTeam: '', status: '', source: '', tag: '', search: '' });
  const [sortConfig, setSortConfig] = useState({ key: 'name', direction: 'asc' });
  const [selectedAsset, setSelectedAsset] = useState(null);
  const [drawerAsset, setDrawerAsset] = useState(null);
  const [showAssetForm, setShowAssetForm] = useState(false);

  const kpis = useMemo(() => getKpis(), [getKpis]);
  const statusBreakdown = useMemo(() => getStatusBreakdown(), [getStatusBreakdown]);
  const typeBreakdown = useMemo(() => getTypeBreakdown(), [getTypeBreakdown]);

  const filteredAssets = useMemo(() => {
    const list = getAssets(filters);
    return [...list].sort((a, b) => {
      const modifier = sortConfig.direction === 'asc' ? 1 : -1;
      if (typeof a[sortConfig.key] === 'string') return a[sortConfig.key].localeCompare(b[sortConfig.key]) * modifier;
      return (a[sortConfig.key] - b[sortConfig.key]) * modifier;
    });
  }, [getAssets, filters, sortConfig]);

  const handleSort = (key) => {
    setSortConfig((prev) => ({ key, direction: prev.key === key && prev.direction === 'desc' ? 'asc' : 'desc' }));
  };

  const handleAssetClick = (asset) => {
    if (viewMode === 'topology') setSelectedAsset(asset);
    else setDrawerAsset(asset);
  };

  const handleAddAsset = (asset) => {
    addAsset(asset);
    setShowAssetForm(false);
  };

  const CHART_COLORS = [KFH.primary, KFH.warning, KFH.critical, '#9CA3AF'];

  return (
    <>
      <div className="kfh-phdr">
        <div className="kfh-phdr-titlewrap">
          <h1 className="kfh-phdr-title">Inventory</h1>
          <span className="kfh-phdr-sub">{filteredAssets.length} assets</span>
        </div>
        <div className="kfh-phdr-search">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
          <input
            type="text"
            placeholder="Search assets…"
            value={filters.search}
            onChange={(e) => setFilters((prev) => ({ ...prev, search: e.target.value }))}
          />
        </div>
        <div className="kfh-phdr-ctrls">
          <button className="kfh-phdr-btn-primary" onClick={() => setShowAssetForm(true)}>＋ Add asset</button>
        </div>
      </div>
    <div className="h-full overflow-y-auto" style={{ background: KFH.bgBody }}>
      <div className="max-w-[1800px] mx-auto p-6 space-y-6">
        {/* KPI Cards Row */}
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
          <KpiCard label="Total Assets" value={kpis.total} variant="info" icon={<Server size={16} />} />
          <KpiCard label="Critical Assets" value={kpis.critical} variant="critical" icon={<AlertTriangle size={16} />} className="pulse-critical" />
          <KpiCard label="With Incidents" value={kpis.withIncidents} variant="warning" icon={<AlertCircle size={16} />} />
          <KpiCard label="No Telemetry" value={kpis.unknown} variant="warning" icon={<Activity size={16} />} />
          <KpiCard label="K8s Assets" value={kpis.k8sAssets} variant="success" icon={<Box size={16} />} />
          <KpiCard label="Coverage" value={`${kpis.coverage}%`} variant="success" icon={<CheckCircle size={16} />} />
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div className="kfh-card p-6">
            <h3 className="text-lg font-bold mb-4 flex items-center gap-2" style={{ color: KFH.textMain }}>
              <Activity size={16} />
              Status Distribution
            </h3>
            <div className="h-64 chart-container">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={statusBreakdown} cx="50%" cy="50%" innerRadius={50} outerRadius={80} paddingAngle={2} dataKey="value">
                    {statusBreakdown.map((_, index) => (
                      <Cell key={`cell-${index}`} fill={CHART_COLORS[index % CHART_COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip contentStyle={{ backgroundColor: '#FFFFFF', border: 'none', borderRadius: '8px', boxShadow: '0 4px 12px rgba(0,0,0,0.1)' }} />
                </PieChart>
              </ResponsiveContainer>
            </div>
            <div className="flex flex-wrap justify-center gap-3 mt-2">
              {statusBreakdown.map((s, idx) => (
                <div key={s.name} className="flex items-center gap-1.5">
                  <div className="w-2 h-2 rounded-full" style={{ backgroundColor: CHART_COLORS[idx % CHART_COLORS.length] }} />
                  <span className="text-xs" style={{ color: KFH.textMuted }}>{s.name}: {s.value}</span>
                </div>
              ))}
            </div>
          </div>

          <div className="kfh-card p-6">
            <h3 className="text-lg font-bold mb-4 flex items-center gap-2" style={{ color: KFH.textMain }}>
              <AlertTriangle size={16} />
              Critical Assets by Type
            </h3>
            <div className="h-64 chart-container">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={typeBreakdown} layout="vertical">
                  <CartesianGrid strokeDasharray="3 3" stroke="#E5E7EB" horizontal={false} />
                  <XAxis type="number" stroke="#9CA3AF" tick={{ fill: '#9CA3AF', fontSize: 10 }} />
                  <YAxis dataKey="type" type="category" stroke="#9CA3AF" tick={{ fill: '#9CA3AF', fontSize: 10 }} width={100} />
                  <Tooltip contentStyle={{ backgroundColor: '#FFFFFF', border: 'none', borderRadius: '8px', boxShadow: '0 4px 12px rgba(0,0,0,0.1)' }} />
                  <Bar dataKey="count" fill={KFH.critical} radius={[0, 4, 4, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>
        </div>

        <div className="kfh-card overflow-hidden">
          {/* Catalog Header with Controls */}
          <div className="p-4" style={{ borderBottom: `1px solid ${KFH.border}` }}>
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-bold" style={{ color: KFH.textMain }}>Assets Catalog ({filteredAssets.length})</h3>

              <div className="flex items-center gap-3">
                {/* View Mode Toggle */}
                <div className="flex gap-1 rounded-lg p-1" style={{ background: KFH.offWhite, border: `1px solid ${KFH.border}` }}>
                  <button onClick={() => setViewMode('cards')} className="p-2 rounded-md" style={viewMode === 'cards' ? { background: KFH.primary, color: '#fff' } : { color: KFH.textMuted }} aria-label="Cards">
                    <Grid size={16} />
                  </button>
                  <button onClick={() => setViewMode('table')} className="p-2 rounded-md" style={viewMode === 'table' ? { background: KFH.primary, color: '#fff' } : { color: KFH.textMuted }} aria-label="Table">
                    <List size={16} />
                  </button>
                  <button onClick={() => setViewMode('topology')} className="p-2 rounded-md" style={viewMode === 'topology' ? { background: KFH.primary, color: '#fff' } : { color: KFH.textMuted }} aria-label="Topology">
                    <GitBranch size={16} />
                  </button>
                </div>

                {viewMode === 'topology' && selectedAsset && (
                  <Button size="sm" variant="outline" onClick={() => setDrawerAsset(selectedAsset)}>
                    <Eye size={16} />
                    View Details
                  </Button>
                )}
              </div>
            </div>

            {/* Filters Row */}
            <div className="flex items-center gap-3">
              <div className="flex items-center gap-2" style={{ color: KFH.textMuted }}>
                <Filter size={16} />
                <span className="text-sm font-bold">Filters</span>
              </div>
              <div className="flex-1 grid grid-cols-2 md:grid-cols-4 lg:grid-cols-7 gap-3">
                <Select value={filters.type} onChange={(e) => setFilters((prev) => ({ ...prev, type: e.target.value }))} options={[{ value: '', label: 'All Types' }, ...ASSET_TYPES.map(t => ({ value: t, label: t }))]} />
                <Select value={filters.environment} onChange={(e) => setFilters((prev) => ({ ...prev, environment: e.target.value }))} options={[{ value: '', label: 'All Environments' }, ...ENVIRONMENTS.map(v => ({ value: v, label: v }))]} />
                <Select value={filters.domain} onChange={(e) => setFilters((prev) => ({ ...prev, domain: e.target.value }))} options={[{ value: '', label: 'All Domains' }, ...DOMAINS.map(v => ({ value: v, label: v }))]} />
                <Select value={filters.ownerTeam} onChange={(e) => setFilters((prev) => ({ ...prev, ownerTeam: e.target.value }))} options={[{ value: '', label: 'All Teams' }, ...TEAMS.map(v => ({ value: v, label: v }))]} />
                <Select value={filters.status} onChange={(e) => setFilters((prev) => ({ ...prev, status: e.target.value }))} options={[{ value: '', label: 'All Statuses' }, ...STATUSES.map(v => ({ value: v, label: v }))]} />
                <Select value={filters.source} onChange={(e) => setFilters((prev) => ({ ...prev, source: e.target.value }))} options={[{ value: '', label: 'All Sources' }, ...SOURCES.map(v => ({ value: v, label: v }))]} />
                <Select value={filters.tag} onChange={(e) => setFilters((prev) => ({ ...prev, tag: e.target.value }))} options={[{ value: '', label: 'All Tags' }, ...TAG_POOL.map(v => ({ value: v, label: v }))]} />
              </div>
              {Object.values(filters).some((v) => v && v !== '') && (
                <Button size="xs" variant="ghost" onClick={() => setFilters({ type: '', environment: '', domain: '', ownerTeam: '', status: '', source: '', tag: '', search: '' })}>
                  Clear All
                </Button>
              )}
            </div>
          </div>

          <div className="p-6">
            {viewMode === 'cards' && (
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                {filteredAssets.slice(0, 100).map((asset) => (
                  <AssetCard key={asset.id} asset={asset} onClick={() => handleAssetClick(asset)} />
                ))}
              </div>
            )}

            {viewMode === 'table' && (
              <AssetsTable
                assets={filteredAssets.slice(0, 100)}
                onRowClick={handleAssetClick}
                sortConfig={sortConfig}
                onSort={handleSort}
              />
            )}

            {viewMode === 'topology' && (
              <TopologyView
                assets={filteredAssets}
                connectivity={connectivity}
                selectedAsset={selectedAsset}
                onAssetClick={handleAssetClick}
              />
            )}

            {filteredAssets.length === 0 && (
              <div className="text-center py-12" style={{ color: KFH.textMuted }}>
                <Server className="w-16 h-16 mx-auto mb-4 opacity-50" />
                <p>No assets found</p>
              </div>
            )}
          </div>
        </div>
      </div>

      {drawerAsset && (
        <AssetDrawer asset={drawerAsset} onClose={() => setDrawerAsset(null)} onUpdate={updateAsset} />
      )}

      {showAssetForm && (
        <AssetForm onSubmit={handleAddAsset} onCancel={() => setShowAssetForm(false)} />
      )}
    </div>
    </>
  );
};

const App = () => {
  const { toasts } = useStore();
  const [toastList, setToastList] = useState(toasts);

  useEffect(() => setToastList(toasts), [toasts]);

  return (
    <>
      <CustomStyles />
      <InventoryPage />
      <ToastContainer toasts={toastList} onDismiss={(id) => setToastList((prev) => prev.filter((t) => t.id !== id))} />
    </>
  );
};

function mount() {
  const rootEl = document.getElementById('page-root') || document.getElementById('content-area') || document.getElementById('inventory-app');
  if (!rootEl) return;

  const app = (
    <StoreProvider>
      <App />
    </StoreProvider>
  );

  // React 18
  if (ReactDOM && typeof ReactDOM.createRoot === 'function') {
    const root = ReactDOM.createRoot(rootEl);
    root.render(app);
    // Store root reference for cleanup
    rootEl._reactRoot = root;
    return;
  }

  // React 17 fallback
  if (ReactDOM && typeof ReactDOM.render === 'function') {
    ReactDOM.render(app, rootEl);
  }
}

// Expose render function for router to call when re-navigating to this page
window.renderInventoryPage = function() {
  mount();
};

mount();

})(); // End IIFE

