/**
 * KFH AIOps Command Center - Alerts Explorer
 * KFH "Beyond Horizons" Design System
 * Uses: React 18, ReactDOM 18, Recharts (local vendor libs)
 * ES5-compatible for maximum browser support
 */

(function() {
  'use strict';

  // Mount element - try page-root first (SPA mode), then fallback
  var mountEl = document.getElementById('page-root') || document.getElementById('content-area') || document.getElementById('root') || document.getElementById('app');
  if (!mountEl) return;

  // Check for required libraries
  if (!window.React || !window.ReactDOM || !window.Recharts) {
    mountEl.innerHTML = '<div style="padding:24px;font-family:Outfit,sans-serif;"><div class="kfh-card" style="padding:32px;border-left:4px solid #D32F2F;"><h3 style="color:#D32F2F;margin-bottom:8px;">Alerts Error</h3><p style="color:#666;">Missing vendor libraries (React/ReactDOM/Recharts). Please ensure all vendor scripts are loaded.</p></div></div>';
    return;
  }

  var React = window.React;
  var ReactDOM = window.ReactDOM;
  var Recharts = window.Recharts;
  var h = React.createElement;

  // Recharts components
  var BarChart = Recharts.BarChart;
  var Bar = Recharts.Bar;
  var LineChart = Recharts.LineChart;
  var Line = Recharts.Line;
  var PieChart = Recharts.PieChart;
  var Pie = Recharts.Pie;
  var Cell = Recharts.Cell;
  var XAxis = Recharts.XAxis;
  var YAxis = Recharts.YAxis;
  var CartesianGrid = Recharts.CartesianGrid;
  var Tooltip = Recharts.Tooltip;
  var ResponsiveContainer = Recharts.ResponsiveContainer;

  // ============= CONSTANTS =============
  var SOURCES = ['SCOM', 'vROps', 'BMC', 'SolarWinds', 'Elastic'];
  var SEVERITIES = ['critical', 'high', 'medium', 'low', 'info'];
  var STATUSES = ['Open', 'Ack', 'Closed', 'Suppressed'];
  var ENVS = ['Prod', 'DR', 'UAT'];
  var DOMAINS = ['Banking', 'Digital', 'ATM', 'Core', 'Network', 'Security'];

  // KFH Colors
  var COLORS = {
    primary: '#128754',
    primaryDark: '#0E6B42',
    primaryLight: '#e2f7dd',
    gold: '#A79F91',
    critical: '#D32F2F',
    criticalBg: '#FFF8F8',
    high: '#D97706',
    highBg: '#FEF3C7',
    medium: '#CA8A04',
    mediumBg: '#FEF9C3',
    low: '#2563EB',
    lowBg: '#EFF6FF',
    info: '#006064',
    infoBg: '#E0F7FA',
    success: '#128754',
    successBg: '#E8F5EF',
    textPrimary: '#1D1D1D',
    textSecondary: '#666666',
    textMuted: '#9CA3AF',
    surface: '#FFFFFF',
    surfaceBg: '#F3F4F7',
    border: 'rgba(0,0,0,0.04)',
    sidebarBg: '#24604f'
  };

  var CHART_COLORS = [COLORS.primary, COLORS.gold, COLORS.info, COLORS.high, '#8B5CF6'];


  // Production data is loaded from /api/v1 through APIClient. Empty arrays preserve the page design.
  var alertsData = [];
  var hourlyData = [];
  var sourceData = [];
  var severityData = [];

  function pageContent(response) {
    return response && Array.isArray(response.content) ? response.content : Array.isArray(response) ? response : [];
  }

  function normalizeAlert(row) {
    var severity = String(row.severity || 'info').toLowerCase();
    return {
      id: String(row.id || row.eventId || row.alertId || ''),
      ts: row.timestamp || row.ts || row.createdAt || new Date().toISOString(),
      source: row.sourceSystem || row.source || row.name || 'Unknown',
      severity: severity,
      title: row.title || row.message || row.name || 'Untitled alert',
      deviceOrHost: row.resourceName || row.resourceId || row.deviceOrHost || '-',
      domain: row.businessDomain || row.domain || '-',
      env: row.environment || row.env || 'PROD',
      appId: row.applicationId || row.applicationName || row.appId || '-',
      status: row.status || 'Open',
      noiseScore: Number(row.noiseScore || 0),
      alertGroupId: row.alertGroupId || row.groupId || null,
      correlationKeyExact: row.correlationKeyExact || null,
      correlationKeyFamily: row.correlationKeyFamily || null
    };
  }

  function buildHourlyData(alerts) {
    var buckets = {};
    alerts.forEach(function(alert) {
      var d = new Date(alert.ts);
      if (!isNaN(d.getTime())) {
        var key = String(d.getHours()).padStart(2, '0') + ':00';
        buckets[key] = (buckets[key] || 0) + 1;
      }
    });
    return Object.keys(buckets).sort().map(function(hour) { return { hour: hour, value: buckets[hour] }; });
  }

  function countBy(alerts, keyFn) {
    var counts = {};
    alerts.forEach(function(alert) {
      var key = keyFn(alert) || 'Unknown';
      counts[key] = (counts[key] || 0) + 1;
    });
    return Object.keys(counts).map(function(key) { return { name: key, value: counts[key] }; });
  }

  // Generate Alert Groups from alerts data
  function generateAlertGroups() {
    var groupMap = {};
    alertsData.forEach(function(alert) {
      if (alert.alertGroupId) {
        if (!groupMap[alert.alertGroupId]) {
          groupMap[alert.alertGroupId] = {
            id: alert.alertGroupId,
            title: alert.title,
            severity: alert.severity,
            source: alert.source,
            alertCount: 0,
            apps: [],
            assets: [],
            fingerprints: [],
            correlationType: alert.correlationKeyExact ? 'SURE' : 'LIKELY',
            windowDays: 7
          };
        }
        var group = groupMap[alert.alertGroupId];
        group.alertCount++;
        if (group.apps.indexOf(alert.appId) < 0) group.apps.push(alert.appId);
        if (group.assets.indexOf(alert.deviceOrHost) < 0) group.assets.push(alert.deviceOrHost);
        if (alert.correlationKeyExact && group.fingerprints.indexOf(alert.correlationKeyExact) < 0) {
          group.fingerprints.push(alert.correlationKeyExact);
        } else if (alert.correlationKeyFamily && group.fingerprints.indexOf(alert.correlationKeyFamily) < 0) {
          group.fingerprints.push(alert.correlationKeyFamily);
        }
      }
    });
    return Object.values(groupMap);
  }

  var alertGroupsData = generateAlertGroups();

  // ============= KPI CARD COMPONENT =============
  function KpiCard(props) {
    var iconBgStyle = {
      width: 28, height: 28, borderRadius: 8, background: props.iconBg || COLORS.primaryLight,
      display: 'flex', alignItems: 'center', justifyContent: 'center', color: props.iconFg || COLORS.primary, fontWeight: 700, fontSize: 12
    };
    
    return h('div', { className: 'kfh-card', style: { padding: '16px 20px' } },
      h('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' } },
        h('div', {
          style: { fontSize: 10, fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.08em', color: COLORS.textSecondary }
        }, props.label),
        h('div', { style: iconBgStyle }, '•')
      ),
      h('div', {
        style: { marginTop: 8, fontSize: 28, fontWeight: 700, color: props.valueColor || COLORS.textPrimary }
      }, props.value)
    );
  }

  // ============= BADGE COMPONENTS =============
  function SeverityBadge(props) {
    var config = {
      critical: { bg: COLORS.criticalBg, color: COLORS.critical },
      high: { bg: COLORS.highBg, color: COLORS.high },
      medium: { bg: COLORS.mediumBg, color: COLORS.medium },
      low: { bg: COLORS.lowBg, color: COLORS.low },
      info: { bg: COLORS.infoBg, color: COLORS.info }
    };
    var cfg = config[props.severity] || config.info;
    var style = {
      display: 'inline-flex', padding: '4px 10px', fontSize: 11, fontWeight: 700,
      borderRadius: 6, background: cfg.bg, color: cfg.color, textTransform: 'uppercase'
    };
    return h('span', { style: style }, props.severity);
  }

  function StatusBadge(props) {
    var config = {
      Open: { bg: COLORS.criticalBg, color: COLORS.critical },
      Ack: { bg: COLORS.highBg, color: COLORS.high },
      Closed: { bg: '#F3F4F6', color: COLORS.textSecondary },
      Suppressed: { bg: '#F0EFE9', color: COLORS.gold }
    };
    var cfg = config[props.status] || config.Closed;
    var style = {
      display: 'inline-flex', padding: '4px 10px', fontSize: 11, fontWeight: 600,
      borderRadius: 6, background: cfg.bg, color: cfg.color
    };
    return h('span', { style: style }, props.status);
  }

  function CorrelationBadge(props) {
    if (!props.alert.correlationKeyFamily) return null;
    var isSure = props.alert.correlationKeyExact;
    var style = {
      display: 'inline-flex', padding: '4px 10px', fontSize: 11, fontWeight: 600,
      borderRadius: 6,
      background: isSure ? COLORS.successBg : COLORS.infoBg,
      color: isSure ? COLORS.success : COLORS.info
    };
    return h('span', { style: style }, isSure ? 'SURE' : 'LIKELY');
  }

  function SourceBadge(props) {
    var sourceColors = {
      SCOM: { bg: '#EDE9FE', color: '#7C3AED' },
      vROps: { bg: COLORS.primaryLight, color: COLORS.primary },
      BMC: { bg: '#FCE7F3', color: '#DB2777' },
      SolarWinds: { bg: COLORS.highBg, color: COLORS.high },
      Elastic: { bg: COLORS.infoBg, color: COLORS.info }
    };
    var cfg = sourceColors[props.source] || { bg: '#F3F4F6', color: COLORS.textSecondary };
    return h('span', {
      style: {
        display: 'inline-flex', padding: '4px 10px', fontSize: 11, fontWeight: 600,
        borderRadius: 6, background: cfg.bg, color: cfg.color
      }
    }, props.source);
  }

  // ============= ALERT GROUP CARD COMPONENT =============
  function AlertGroupCard(props) {
    var group = props.group;
    var onClick = props.onClick;

    return h('div', {
      className: 'kfh-card',
      style: { padding: 20, cursor: 'pointer', transition: 'box-shadow 0.2s' },
      onClick: onClick
    },
      // Header: badges + alert count
      h('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 } },
        h('div', { style: { display: 'flex', flexWrap: 'wrap', gap: 8 } },
          h(SeverityBadge, { severity: group.severity }),
          h(SourceBadge, { source: group.source }),
          h('span', {
            style: {
              display: 'inline-flex', padding: '4px 10px', fontSize: 11, fontWeight: 600,
              borderRadius: 6, background: group.correlationType === 'SURE' ? COLORS.successBg : COLORS.infoBg,
              color: group.correlationType === 'SURE' ? COLORS.success : COLORS.info
            }
          }, group.correlationType)
        ),
        h('span', { style: { fontSize: 13, fontWeight: 600, color: COLORS.textSecondary } }, group.alertCount + ' alerts')
      ),

      // Title
      h('h4', { style: { fontSize: 15, fontWeight: 700, color: COLORS.textPrimary, marginBottom: 16, lineHeight: 1.4 } }, group.title),

      // Stats row
      h('div', { style: { display: 'flex', gap: 12 } },
        h('div', { style: { flex: 1, background: COLORS.surfaceBg, borderRadius: 8, padding: '10px 12px' } },
          h('div', { style: { fontSize: 10, fontWeight: 700, textTransform: 'uppercase', color: COLORS.textSecondary, marginBottom: 4 } }, 'WINDOW'),
          h('div', { style: { fontSize: 14, fontWeight: 700, color: COLORS.textPrimary } }, group.windowDays + ' days')
        ),
        h('div', { style: { flex: 1, background: COLORS.surfaceBg, borderRadius: 8, padding: '10px 12px' } },
          h('div', { style: { fontSize: 10, fontWeight: 700, textTransform: 'uppercase', color: COLORS.textSecondary, marginBottom: 4 } }, 'APPS'),
          h('div', { style: { fontSize: 14, fontWeight: 700, color: COLORS.textPrimary } }, group.apps.length)
        ),
        h('div', { style: { flex: 1, background: COLORS.surfaceBg, borderRadius: 8, padding: '10px 12px' } },
          h('div', { style: { fontSize: 10, fontWeight: 700, textTransform: 'uppercase', color: COLORS.textSecondary, marginBottom: 4 } }, 'ASSETS'),
          h('div', { style: { fontSize: 14, fontWeight: 700, color: COLORS.textPrimary } }, group.assets.length)
        )
      ),

      // Fingerprints
      group.fingerprints.length > 0 && h('div', { style: { marginTop: 12 } },
        h('a', {
          style: { fontSize: 12, color: COLORS.primary, fontWeight: 600, textDecoration: 'none', cursor: 'pointer' },
          onClick: function(e) { e.stopPropagation(); }
        }, group.fingerprints.length + ' fingerprint' + (group.fingerprints.length > 1 ? 's' : ''))
      )
    );
  }

  // ============= MAIN ALERTS EXPLORER COMPONENT =============
  function AlertsExplorer() {
    var _useState = React.useState;
    var _useMemo = React.useMemo;

    var viewState = _useState('raw');
    var view = viewState[0];
    var setView = viewState[1];

    var searchState = _useState('');
    var search = searchState[0];
    var setSearch = searchState[1];

    var selectedAlertState = _useState(null);
    var selectedAlert = selectedAlertState[0];
    var setSelectedAlert = selectedAlertState[1];

    // Calculate KPIs
    var kpis = _useMemo(function() {
      var total = alertsData.length;
      var critical = alertsData.filter(function(a) { return a.severity === 'critical'; }).length;
      var suppressed = alertsData.filter(function(a) { return a.status === 'Suppressed' || a.noiseScore > 80; }).length;
      var devices = [];
      alertsData.forEach(function(a) { if (devices.indexOf(a.deviceOrHost) < 0) devices.push(a.deviceOrHost); });
      var apps = [];
      alertsData.forEach(function(a) { if (apps.indexOf(a.appId) < 0) apps.push(a.appId); });
      var correlated = alertsData.filter(function(a) { return a.alertGroupId; }).length;
      var groups = [];
      alertsData.forEach(function(a) { if (a.alertGroupId && groups.indexOf(a.alertGroupId) < 0) groups.push(a.alertGroupId); });
      return {
        total: total,
        critical: critical,
        suppressed: suppressed,
        devices: devices.length,
        apps: apps.length,
        correlated: correlated,
        groups: groups.length
      };
    }, [alertsData.length]);

    // Filter alerts
    var filteredAlerts = _useMemo(function() {
      return alertsData.filter(function(alert) {
        if (search) {
          var s = search.toLowerCase();
          if (alert.title.toLowerCase().indexOf(s) < 0 && alert.deviceOrHost.toLowerCase().indexOf(s) < 0) return false;
        }
        return true;
      });
    }, [search, alertsData.length]);

    // Tooltip style
    var tooltipStyle = { backgroundColor: COLORS.surface, border: 'none', borderRadius: 12, boxShadow: '0 4px 12px rgba(0,0,0,0.1)' };

    // ============= RENDER =============
    // Note: Sidebar and Header are provided by index.html
    return h('div', { style: { height: '100%', display: 'flex', flexDirection: 'column' } },
      // Main Content
      h('main', { style: { flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0, overflow: 'hidden', position: 'relative' } },
        // Top gradient
        h('div', {
          style: { position: 'absolute', top: 0, left: 0, width: '100%', height: 256, background: 'linear-gradient(to bottom, rgba(226, 247, 221, 0.6), transparent)', pointerEvents: 'none', zIndex: 0 }
        }),

        // Content - starts immediately, no duplicate header
        h('div', { style: { flex: 1, overflowY: 'auto', padding: '24px 32px 32px', zIndex: 10, position: 'relative' } },
          h('div', { className: 'kfh-card', style: { padding: 20, marginBottom: 24, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16 } },
            h('div', null,
              h('p', { style: { margin: 0, color: COLORS.gold, fontSize: 12, fontWeight: 900, letterSpacing: '0.08em', textTransform: 'uppercase' } }, 'Telemetry Explorer'),
              h('h1', { style: { margin: '4px 0 0', fontSize: 28, fontWeight: 900, color: COLORS.textPrimary } }, 'Alerts')
            ),
            h('div', { style: { display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap', justifyContent: 'flex-end' } },
              h('input', {
                className: 'kfh-input',
                value: search,
                onChange: function(e) { setSearch(e.target.value || ''); },
                placeholder: 'Search alerts or resources',
                style: { width: 280 }
              }),
              h('div', { style: { display: 'flex', background: '#F3F4F7', borderRadius: 8, padding: 3 } },
                h('button', {
                  className: 'alerts-view-btn',
                  style: { padding: '6px 14px', borderRadius: 6, border: 'none', cursor: 'pointer', fontWeight: 600, fontSize: 13, background: view === 'raw' ? 'white' : 'transparent', color: view === 'raw' ? COLORS.textPrimary : COLORS.textSecondary, boxShadow: view === 'raw' ? '0 1px 3px rgba(0,0,0,0.1)' : 'none' },
                  onClick: function() { setView('raw'); }
                }, 'Raw Alerts'),
                h('button', {
                  className: 'alerts-view-btn',
                  style: { padding: '6px 14px', borderRadius: 6, border: 'none', cursor: 'pointer', fontWeight: 600, fontSize: 13, background: view === 'groups' ? 'white' : 'transparent', color: view === 'groups' ? COLORS.textPrimary : COLORS.textSecondary, boxShadow: view === 'groups' ? '0 1px 3px rgba(0,0,0,0.1)' : 'none' },
                  onClick: function() { setView('groups'); }
                }, 'Alert Groups')
              )
            )
          ),
          // KPI Strip - all 7 KPIs in one row
          h('div', {
            style: { display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 12, marginBottom: 24 }
          },
            h(KpiCard, { label: 'Total Alerts', value: kpis.total, iconBg: COLORS.primaryLight, iconFg: COLORS.primary }),
            h(KpiCard, { label: 'Critical', value: kpis.critical, iconBg: COLORS.criticalBg, iconFg: COLORS.critical, valueColor: COLORS.critical }),
            h(KpiCard, { label: 'Suppressed', value: kpis.suppressed, iconBg: '#F0EFE9', iconFg: COLORS.gold }),
            h(KpiCard, { label: 'Devices', value: kpis.devices, iconBg: COLORS.infoBg, iconFg: COLORS.info }),
            h(KpiCard, { label: 'Apps Impacted', value: kpis.apps, iconBg: COLORS.primaryLight, iconFg: COLORS.primary }),
            h(KpiCard, { label: 'Correlated', value: kpis.correlated, iconBg: COLORS.successBg, iconFg: COLORS.success }),
            h(KpiCard, { label: 'Groups', value: kpis.groups, iconBg: COLORS.highBg, iconFg: COLORS.high })
          ),

          // Charts row
          h('div', { style: { display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 24, marginBottom: 24 } },
            // Volume Trend
            h('div', { className: 'kfh-card', style: { padding: 24 } },
              h('h3', { style: { fontSize: 16, fontWeight: 700, color: COLORS.textPrimary, marginBottom: 16 } }, 'Alert Volume Trend'),
              h('div', { style: { height: 200 } },
                h(ResponsiveContainer, { width: '100%', height: '100%' },
                  h(LineChart, { data: hourlyData },
                    h(CartesianGrid, { strokeDasharray: '3 3', stroke: '#E5E7EB' }),
                    h(XAxis, { dataKey: 'hour', tick: { fill: COLORS.textMuted, fontSize: 11 } }),
                    h(YAxis, { tick: { fill: COLORS.textMuted, fontSize: 11 } }),
                    h(Tooltip, { contentStyle: tooltipStyle }),
                    h(Line, { type: 'monotone', dataKey: 'value', stroke: COLORS.primary, strokeWidth: 2, dot: false })
                  )
                )
              )
            ),

            // Source Distribution
            h('div', { className: 'kfh-card', style: { padding: 24 } },
              h('h3', { style: { fontSize: 16, fontWeight: 700, color: COLORS.textPrimary, marginBottom: 16 } }, 'Source Distribution'),
              h('div', { style: { height: 200 } },
                h(ResponsiveContainer, { width: '100%', height: '100%' },
                  h(PieChart, null,
                    h(Pie, { data: sourceData, cx: '50%', cy: '50%', innerRadius: 40, outerRadius: 70, paddingAngle: 2, dataKey: 'value' },
                      sourceData.map(function(entry, index) {
                        return h(Cell, { key: 'cell-' + index, fill: CHART_COLORS[index % CHART_COLORS.length] });
                      })
                    ),
                    h(Tooltip, { contentStyle: tooltipStyle })
                  )
                )
              ),
              h('div', { style: { display: 'flex', flexWrap: 'wrap', justifyContent: 'center', gap: 8, marginTop: 8 } },
                sourceData.map(function(s, idx) {
                  return h('div', { key: s.name, style: { display: 'flex', alignItems: 'center', gap: 4, fontSize: 11, color: COLORS.textSecondary } },
                    h('div', { style: { width: 8, height: 8, borderRadius: '50%', background: CHART_COLORS[idx % CHART_COLORS.length] } }),
                    s.name + ' ' + Math.round(s.value / sourceData.reduce(function(a, b) { return a + b.value; }, 0) * 100) + '%'
                  );
                })
              )
            ),

            // Severity Breakdown
            h('div', { className: 'kfh-card', style: { padding: 24 } },
              h('h3', { style: { fontSize: 16, fontWeight: 700, color: COLORS.textPrimary, marginBottom: 16 } }, 'Severity Breakdown'),
              h('div', { style: { height: 200 } },
                h(ResponsiveContainer, { width: '100%', height: '100%' },
                  h(BarChart, { data: severityData },
                    h(CartesianGrid, { strokeDasharray: '3 3', stroke: '#E5E7EB' }),
                    h(XAxis, { dataKey: 'name', tick: { fill: COLORS.textMuted, fontSize: 10 } }),
                    h(YAxis, { tick: { fill: COLORS.textMuted, fontSize: 11 } }),
                    h(Tooltip, { contentStyle: tooltipStyle }),
                    h(Bar, { dataKey: 'value', fill: COLORS.primary, radius: [4, 4, 0, 0] })
                  )
                )
              )
            )
          ),

          // Conditional: Raw Alerts Table OR Alert Groups Grid
          view === 'raw' ?
          // Raw Alerts Table
          h('div', { className: 'kfh-card', style: { overflow: 'hidden' } },
            h('div', { style: { overflowX: 'auto' } },
              h('table', { style: { width: '100%', borderCollapse: 'collapse' } },
                h('thead', null,
                  h('tr', { style: { borderBottom: '1px solid #E5E7EB' } },
                    h('th', { style: { padding: '16px 24px', textAlign: 'left', fontSize: 12, fontWeight: 600, color: COLORS.textSecondary } }, 'Time'),
                    h('th', { style: { padding: '16px 24px', textAlign: 'left', fontSize: 12, fontWeight: 600, color: COLORS.textSecondary } }, 'Severity'),
                    h('th', { style: { padding: '16px 24px', textAlign: 'left', fontSize: 12, fontWeight: 600, color: COLORS.textSecondary } }, 'Source'),
                    h('th', { style: { padding: '16px 24px', textAlign: 'left', fontSize: 12, fontWeight: 600, color: COLORS.textSecondary } }, 'Title'),
                    h('th', { style: { padding: '16px 24px', textAlign: 'left', fontSize: 12, fontWeight: 600, color: COLORS.textSecondary } }, 'Device'),
                    h('th', { style: { padding: '16px 24px', textAlign: 'left', fontSize: 12, fontWeight: 600, color: COLORS.textSecondary } }, 'App'),
                    h('th', { style: { padding: '16px 24px', textAlign: 'left', fontSize: 12, fontWeight: 600, color: COLORS.textSecondary } }, 'Env'),
                    h('th', { style: { padding: '16px 24px', textAlign: 'left', fontSize: 12, fontWeight: 600, color: COLORS.textSecondary } }, 'Status'),
                    h('th', { style: { padding: '16px 24px', textAlign: 'left', fontSize: 12, fontWeight: 600, color: COLORS.textSecondary } }, 'Noise'),
                    h('th', { style: { padding: '16px 24px', textAlign: 'left', fontSize: 12, fontWeight: 600, color: COLORS.textSecondary } }, 'Correlation'),
                    h('th', { style: { padding: '16px 24px', textAlign: 'center', fontSize: 12, fontWeight: 600, color: COLORS.textSecondary } }, 'Actions')
                  )
                ),
                h('tbody', null,
                  filteredAlerts.slice(0, 30).map(function(alert, idx) {
                    var rowStyle = { borderBottom: '1px solid #F3F4F6', cursor: 'pointer' };
                    if (idx % 2 === 1) rowStyle.background = '#F9FAFB';
                    return h('tr', {
                      key: alert.id,
                      style: rowStyle,
                      onClick: function() { setSelectedAlert(alert); }
                    },
                      h('td', { style: { padding: '16px 24px', fontSize: 13, color: COLORS.textSecondary } },
                        new Date(alert.ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                      ),
                      h('td', { style: { padding: '16px 24px' } }, h(SeverityBadge, { severity: alert.severity })),
                      h('td', { style: { padding: '16px 24px', fontSize: 13 } }, alert.source),
                      h('td', { style: { padding: '16px 24px', fontSize: 13, fontWeight: 500 } }, alert.title),
                      h('td', { style: { padding: '16px 24px', fontSize: 13 } }, alert.deviceOrHost),
                      h('td', { style: { padding: '16px 24px', fontSize: 13 } }, alert.appId),
                      h('td', { style: { padding: '16px 24px', fontSize: 13 } }, alert.env),
                      h('td', { style: { padding: '16px 24px' } }, h(StatusBadge, { status: alert.status })),
                      h('td', { style: { padding: '16px 24px', fontSize: 13, fontWeight: 600, color: alert.noiseScore > 80 ? COLORS.critical : COLORS.textPrimary } }, alert.noiseScore),
                      h('td', { style: { padding: '16px 24px' } }, h(CorrelationBadge, { alert: alert })),
                      h('td', { style: { padding: '16px 24px', textAlign: 'center' } },
                        h('button', {
                          style: { padding: 8, borderRadius: 6, background: 'transparent', border: 'none', cursor: 'pointer', color: COLORS.textSecondary },
                          onClick: function(e) { e.stopPropagation(); setSelectedAlert(alert); }
                        }, '👁️')
                      )
                    );
                  })
                )
              )
            )
          )
          :
          // Alert Groups View
          h('div', null,
            h('div', { style: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 } },
              h('h2', { style: { fontSize: 18, fontWeight: 700, color: COLORS.textPrimary } }, 'Alert Groups (' + alertGroupsData.length + ')')
            ),
            h('div', {
              style: { display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 16 }
            },
              alertGroupsData.slice(0, 20).map(function(group) {
                return h(AlertGroupCard, {
                  key: group.id,
                  group: group,
                  onClick: function() {
                    // Find first alert in this group and show details
                    var groupAlert = alertsData.find(function(a) { return a.alertGroupId === group.id; });
                    if (groupAlert) setSelectedAlert(groupAlert);
                  }
                });
              })
            )
          )
        )
      ),

      // Alert Details Drawer
      selectedAlert && h(AlertDrawer, {
        alert: selectedAlert,
        onClose: function() { setSelectedAlert(null); }
      })
    );
  }

  // ============= ALERT DRAWER COMPONENT WITH TABS =============
  function AlertDrawer(props) {
    var alert = props.alert;
    var onClose = props.onClose;
    var _useState = React.useState;

    var tabState = _useState('summary');
    var activeTab = tabState[0];
    var setActiveTab = tabState[1];

    var tabs = [
      { id: 'summary', label: 'Summary' },
      { id: 'evidence', label: 'Evidence' },
      { id: 'timeline', label: 'Timeline' },
      { id: 'correlations', label: 'Correlations' }
    ];

    // Demo evidence data
    var evidenceData = [
      { id: 'ev-1', type: 'Error', source: alert.source, time: '2 min ago', snippet: 'ERROR [' + alert.deviceOrHost + '] ' + alert.title + '\n  at com.kfh.banking.core.ServiceManager.process(ServiceManager.java:142)' },
      { id: 'ev-2', type: 'Warning', source: 'ServiceNow', time: '5 min ago', snippet: 'WARN: Resource utilization at 92%\nService: ' + alert.appId + ' | Environment: ' + alert.env },
      { id: 'ev-3', type: 'Metric', source: 'Datadog', time: '8 min ago', snippet: 'cpu.usage: 95%\nmemory.usage: 87%\ndisk.io.wait: 234ms' }
    ];

    // Demo timeline data
    var timelineData = [
      { time: '2 min ago', event: 'Alert Created', desc: 'AIOps detected anomaly and created alert', color: COLORS.critical },
      { time: '5 min ago', event: 'Threshold Breached', desc: 'Resource utilization exceeded 90% threshold', color: COLORS.high },
      { time: '8 min ago', event: 'Warning Triggered', desc: alert.source + ' detected abnormal metrics', color: COLORS.info },
      { time: '15 min ago', event: 'Normal Operation', desc: 'System operating within normal parameters', color: COLORS.success }
    ];

    // Demo correlation data
    var correlationsData = [
      { id: 'alert-99', title: 'Similar alert - Same Device', device: alert.deviceOrHost, time: '3 days ago', similarity: 92, status: 'Resolved' },
      { id: 'alert-88', title: 'Related resource issue', device: 'srv-' + rand(1, 30), time: '5 days ago', similarity: 87, status: 'Resolved' },
      { id: 'alert-77', title: 'Connection timeout detected', device: 'srv-' + rand(1, 30), time: '12 days ago', similarity: 85, status: 'Resolved' }
    ];

    // Tab content render function
    function renderTabContent() {
      if (activeTab === 'summary') {
        return h('div', null,
          // Badges
          h('div', { style: { display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 20 } },
            h(SeverityBadge, { severity: alert.severity }),
            h(StatusBadge, { status: alert.status }),
            h(SourceBadge, { source: alert.source }),
            alert.correlationKeyFamily && h(CorrelationBadge, { alert: alert })
          ),

          // Title
          h('h3', { style: { fontSize: 18, fontWeight: 700, color: COLORS.textPrimary, marginBottom: 20, lineHeight: 1.4 } }, alert.title),

          // Details grid
          h('div', { style: { display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 16, marginBottom: 24 } },
            h('div', null,
              h('span', { style: { display: 'block', fontSize: 12, color: COLORS.textSecondary, marginBottom: 4 } }, 'Device'),
              h('span', { style: { fontSize: 14, fontWeight: 600, color: COLORS.textPrimary } }, alert.deviceOrHost)
            ),
            h('div', null,
              h('span', { style: { display: 'block', fontSize: 12, color: COLORS.textSecondary, marginBottom: 4 } }, 'Application'),
              h('span', { style: { fontSize: 14, fontWeight: 600, color: COLORS.textPrimary } }, alert.appId)
            ),
            h('div', null,
              h('span', { style: { display: 'block', fontSize: 12, color: COLORS.textSecondary, marginBottom: 4 } }, 'Environment'),
              h('span', { style: { fontSize: 14, fontWeight: 600, color: COLORS.textPrimary } }, alert.env)
            ),
            h('div', null,
              h('span', { style: { display: 'block', fontSize: 12, color: COLORS.textSecondary, marginBottom: 4 } }, 'Domain'),
              h('span', { style: { fontSize: 14, fontWeight: 600, color: COLORS.textPrimary } }, alert.domain)
            ),
            h('div', null,
              h('span', { style: { display: 'block', fontSize: 12, color: COLORS.textSecondary, marginBottom: 4 } }, 'Noise Score'),
              h('span', { style: { fontSize: 14, fontWeight: 700, color: alert.noiseScore > 80 ? COLORS.critical : COLORS.primary } }, alert.noiseScore)
            ),
            h('div', null,
              h('span', { style: { display: 'block', fontSize: 12, color: COLORS.textSecondary, marginBottom: 4 } }, 'Timestamp'),
              h('span', { style: { fontSize: 14, fontWeight: 600, color: COLORS.textPrimary } }, new Date(alert.ts).toLocaleString())
            )
          ),

          // RCA Card
          h('div', { style: { background: COLORS.highBg, borderLeft: '4px solid ' + COLORS.high, borderRadius: 12, padding: 16, marginBottom: 24 } },
            h('h4', { style: { fontSize: 14, fontWeight: 700, color: COLORS.textPrimary, marginBottom: 8 } }, 'Root Cause Analysis'),
            h('p', { style: { fontSize: 13, color: COLORS.textSecondary, lineHeight: 1.5, marginBottom: 12 } },
              'This alert indicates ' + alert.title.toLowerCase() + ' on ' + alert.deviceOrHost + '. Common causes include resource exhaustion, configuration issues, or upstream service degradation.'
            ),
            h('div', { style: { fontSize: 13, fontWeight: 600, color: COLORS.textPrimary, marginBottom: 8 } }, 'Recommended Actions:'),
            h('ul', { style: { fontSize: 13, color: COLORS.textSecondary, paddingLeft: 20, margin: 0 } },
              h('li', { style: { marginBottom: 4 } }, 'Check resource utilization on ' + alert.deviceOrHost),
              h('li', { style: { marginBottom: 4 } }, 'Review recent configuration changes'),
              h('li', null, 'Verify upstream service health')
            )
          ),

          // Actions
          h('div', { style: { display: 'flex', flexDirection: 'column', gap: 12 } },
            h('button', {
              style: {
                width: '100%', padding: '14px 20px', borderRadius: 8, background: COLORS.primary, color: COLORS.surface,
                fontSize: 14, fontWeight: 600, border: 'none', cursor: 'pointer'
              }
            }, 'Acknowledge Alert'),
            h('button', {
              style: {
                width: '100%', padding: '14px 20px', borderRadius: 8, background: COLORS.surface, color: COLORS.textPrimary,
                fontSize: 14, fontWeight: 600, border: '1px solid rgba(0,0,0,0.1)', cursor: 'pointer'
              }
            }, 'Suppress Alert'),
            h('button', {
              style: {
                width: '100%', padding: '14px 20px', borderRadius: 8, background: COLORS.surface, color: COLORS.textPrimary,
                fontSize: 14, fontWeight: 600, border: '1px solid rgba(0,0,0,0.1)', cursor: 'pointer'
              }
            }, 'Create Incident')
          )
        );
      }

      if (activeTab === 'evidence') {
        return h('div', null,
          h('div', { style: { marginBottom: 16 } },
            h('span', { style: { fontSize: 14, fontWeight: 600, color: COLORS.textPrimary } }, evidenceData.length + ' evidence items')
          ),
          h('div', { style: { display: 'flex', flexDirection: 'column', gap: 16 } },
            evidenceData.map(function(ev) {
              var typeCfg = ev.type === 'Error' ? { bg: COLORS.criticalBg, color: COLORS.critical }
                          : ev.type === 'Warning' ? { bg: COLORS.highBg, color: COLORS.high }
                          : { bg: COLORS.infoBg, color: COLORS.info };
              return h('div', { key: ev.id, style: { border: '1px solid rgba(0,0,0,0.06)', borderRadius: 12, padding: 16 } },
                h('div', { style: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 } },
                  h('div', { style: { display: 'flex', alignItems: 'center', gap: 8 } },
                    h('span', { style: { padding: '4px 10px', fontSize: 11, fontWeight: 600, borderRadius: 6, background: typeCfg.bg, color: typeCfg.color } }, ev.type),
                    h('span', { style: { fontSize: 13, fontWeight: 600, color: COLORS.textPrimary } }, ev.source)
                  ),
                  h('span', { style: { fontSize: 12, color: COLORS.textSecondary } }, ev.time)
                ),
                h('pre', { style: { background: COLORS.textPrimary, color: COLORS.primaryLight, padding: 12, borderRadius: 8, fontSize: 12, fontFamily: 'monospace', overflow: 'auto', margin: 0 } }, ev.snippet)
              );
            })
          )
        );
      }

      if (activeTab === 'timeline') {
        return h('div', null,
          h('div', { style: { display: 'flex', flexDirection: 'column', gap: 0 } },
            timelineData.map(function(item, idx) {
              return h('div', { key: idx, style: { display: 'flex', gap: 16 } },
                h('div', { style: { display: 'flex', flexDirection: 'column', alignItems: 'center' } },
                  h('div', { style: { width: 12, height: 12, borderRadius: '50%', background: item.color, flexShrink: 0 } }),
                  idx < timelineData.length - 1 && h('div', { style: { width: 2, flex: 1, background: 'rgba(0,0,0,0.08)', minHeight: 40 } })
                ),
                h('div', { style: { paddingBottom: 24, flex: 1 } },
                  h('div', { style: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 4 } },
                    h('span', { style: { fontSize: 14, fontWeight: 600, color: COLORS.textPrimary } }, item.event),
                    h('span', { style: { fontSize: 12, color: COLORS.textSecondary } }, item.time)
                  ),
                  h('p', { style: { fontSize: 13, color: COLORS.textSecondary, margin: 0 } }, item.desc)
                )
              );
            })
          )
        );
      }

      if (activeTab === 'correlations') {
        return h('div', null,
          h('h4', { style: { fontSize: 14, fontWeight: 700, color: COLORS.textPrimary, marginBottom: 16 } }, 'Related Alerts'),
          h('div', { style: { display: 'flex', flexDirection: 'column', gap: 12 } },
            correlationsData.map(function(corr) {
              return h('div', { key: corr.id, style: { border: '1px solid rgba(0,0,0,0.06)', borderRadius: 12, padding: 16, cursor: 'pointer', transition: 'box-shadow 0.2s' } },
                h('div', { style: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 } },
                  h('span', { style: { fontSize: 12, fontFamily: 'monospace', color: COLORS.textSecondary } }, corr.id),
                  h('span', { style: { padding: '4px 10px', fontSize: 11, fontWeight: 600, borderRadius: 6, background: COLORS.successBg, color: COLORS.success } }, corr.status)
                ),
                h('h5', { style: { fontSize: 14, fontWeight: 600, color: COLORS.textPrimary, marginBottom: 8 } }, corr.title),
                h('div', { style: { display: 'flex', alignItems: 'center', gap: 16, fontSize: 12, color: COLORS.textSecondary } },
                  h('span', null, corr.time),
                  h('span', null, 'Similarity: ' + corr.similarity + '%')
                )
              );
            })
          ),
          // Pattern Analysis
          h('div', { style: { marginTop: 24, background: COLORS.surfaceBg, borderRadius: 12, padding: 16 } },
            h('h4', { style: { fontSize: 14, fontWeight: 700, color: COLORS.textPrimary, marginBottom: 8 } }, 'Pattern Analysis'),
            h('p', { style: { fontSize: 13, color: COLORS.textSecondary, margin: 0 } },
              'Pattern analysis will appear here when correlation evidence is returned by the alert/RCA APIs.'
            )
          )
        );
      }

      return null;
    }

    return h('div', null,
      // Overlay
      h('div', {
        style: { position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)', zIndex: 100, cursor: 'pointer' },
        onClick: onClose
      }),

      // Drawer
      h('div', {
        style: {
          position: 'fixed', top: 0, right: 0, width: 520, maxWidth: '90vw', height: '100%',
          background: COLORS.surface, boxShadow: '-4px 0 20px rgba(0,0,0,0.1)', zIndex: 101, display: 'flex', flexDirection: 'column'
        }
      },
        // Header
        h('div', {
          style: {
            background: COLORS.surface, borderBottom: '1px solid rgba(0,0,0,0.06)',
            padding: '16px 24px', display: 'flex', alignItems: 'center', justifyContent: 'space-between'
          }
        },
          h('h2', { style: { fontSize: 18, fontWeight: 700, color: COLORS.textPrimary } }, 'Alert Details'),
          h('button', {
            style: { padding: 8, borderRadius: 8, background: 'transparent', border: 'none', cursor: 'pointer', fontSize: 18 },
            onClick: onClose
          }, '✕')
        ),

        // Tabs
        h('div', {
          style: {
            display: 'flex', gap: 0, padding: '0 24px', borderBottom: '1px solid rgba(0,0,0,0.06)', background: COLORS.surface
          }
        },
          tabs.map(function(tab) {
            var isActive = activeTab === tab.id;
            return h('button', {
              key: tab.id,
              onClick: function() { setActiveTab(tab.id); },
              style: {
                padding: '12px 16px', fontSize: 14, fontWeight: isActive ? 600 : 500,
                color: isActive ? COLORS.primary : COLORS.textSecondary,
                background: 'transparent', border: 'none', borderBottom: isActive ? '2px solid ' + COLORS.primary : '2px solid transparent',
                cursor: 'pointer', transition: 'all 0.2s'
              }
            }, tab.label);
          })
        ),

        // Tab Content (scrollable)
        h('div', { style: { flex: 1, overflowY: 'auto', padding: 24 } },
          renderTabContent()
        )
      )
    );
  }

  // ============= MOUNT APPLICATION =============
  function renderApp(root) {
    if (root) root.render(h(AlertsExplorer));
    else ReactDOM.render(h(AlertsExplorer), mountEl);
  }

  async function loadAlerts(root) {
    if (!window.APIClient || !APIClient.alerts) return;
    try {
      var response = await APIClient.alerts.list({ page: 0, size: 100 });
      alertsData = pageContent(response).map(normalizeAlert);
      hourlyData = buildHourlyData(alertsData);
      sourceData = countBy(alertsData, function(alert) { return alert.source; });
      severityData = countBy(alertsData, function(alert) { return alert.severity.toUpperCase(); });
      alertGroupsData = generateAlertGroups();
      renderApp(root);
    } catch (error) {
      console.warn('[Alerts] Unable to load production alerts; rendering empty state.', error);
    }
  }

  try {
    var root = ReactDOM.createRoot(mountEl);
    renderApp(root);
    loadAlerts(root);
  } catch (e) {
    renderApp(null);
    loadAlerts(null);
  }

})();
