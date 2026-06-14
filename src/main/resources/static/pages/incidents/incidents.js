/**
 * KFH AIOps Command Center - Incidents Dashboard
 * KFH "Beyond Horizons" Design System
 * Uses: React 18, ReactDOM 18, Recharts (local vendor libs)
 */

(function() {
  'use strict';

  // Mount element - try page-root first (SPA mode), then fallback to content-area or root
  var mountEl = document.getElementById('page-root') || document.getElementById('content-area') || document.getElementById('root') || document.getElementById('app');
  if (!mountEl) return;

  // Check for required libraries
  if (!window.React || !window.ReactDOM || !window.Recharts) {
    mountEl.innerHTML = '<div style="padding:24px;font-family:Outfit,sans-serif;"><div class="kfh-card" style="padding:32px;border-left:4px solid #D32F2F;"><h3 style="color:#D32F2F;margin-bottom:8px;">Incidents Error</h3><p style="color:#666;">Missing vendor libraries (React/ReactDOM/Recharts). Please ensure all vendor scripts are loaded.</p></div></div>';
    return;
  }

  var React = window.React;
  var ReactDOM = window.ReactDOM;
  var Recharts = window.Recharts;
  var h = React.createElement;

  // Recharts components
  var AreaChart = Recharts.AreaChart;
  var Area = Recharts.Area;
  var BarChart = Recharts.BarChart;
  var Bar = Recharts.Bar;
  var PieChart = Recharts.PieChart;
  var Pie = Recharts.Pie;
  var Cell = Recharts.Cell;
  var XAxis = Recharts.XAxis;
  var YAxis = Recharts.YAxis;
  var CartesianGrid = Recharts.CartesianGrid;
  var Tooltip = Recharts.Tooltip;
  var Legend = Recharts.Legend;
  var ResponsiveContainer = Recharts.ResponsiveContainer;
  var ComposedChart = Recharts.ComposedChart;
  var Line = Recharts.Line;

  // ============= CONSTANTS =============
  var SOURCES = ['SCOM', 'vROps', 'BMC', 'SolarWinds', 'Elastic', 'Dynatrace'];
  var SEVERITIES = ['Critical', 'High', 'Medium', 'Low'];
  var STATUSES = ['Open', 'Acknowledged', 'InProgress', 'Resolved', 'Closed'];
  var DOMAINS = ['Core Banking', 'Digital Channels', 'Treasury', 'Risk Management', 'HR Systems', 'Infrastructure'];
  var ENVIRONMENTS = ['Production', 'UAT', 'Development', 'DR'];
  var TEAMS = ['Platform Ops', 'DBA Team', 'Network Ops', 'Security Ops', 'App Support', 'DevOps', 'Cloud Ops'];
  var FINGERPRINT_PATTERNS = [
    'CPU_HIGH_SUSTAINED', 'MEMORY_EXHAUSTION', 'DISK_SPACE_LOW', 'NETWORK_LATENCY',
    'SERVICE_UNAVAILABLE', 'AUTH_FAILURE', 'DB_CONNECTION_POOL', 'QUEUE_BACKLOG',
    'SSL_CERT_EXPIRY', 'LOG_VOLUME_SPIKE', 'POD_RESTART_LOOP', 'NODE_PRESSURE'
  ];
  var SERVICES = [
    'Core Banking System', 'Mobile Banking App', 'Payment Gateway', 'Customer Portal',
    'Risk Engine', 'Loan Processing', 'Authentication Service', 'Notification Service',
    'Transaction Processor', 'Report Generator', 'Data Warehouse', 'API Gateway'
  ];

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
    border: 'rgba(0,0,0,0.04)'
  };

  var CHART_COLORS = [COLORS.primary, COLORS.gold, COLORS.info, COLORS.high, '#8B5CF6', '#EC4899'];

  // ============= UTILITY FUNCTIONS =============
  function uniq(arr) {
    return Array.from(new Set(arr));
  }


  // Production data is loaded from /api/v1 through APIClient. Empty arrays preserve the page design.
  var incidentsData = [];
  var hourlyTrends = [];

  function pageContent(response) {
    return response && Array.isArray(response.content) ? response.content : Array.isArray(response) ? response : [];
  }

  function normalizeIncident(row) {
    var title = row.title || row.name || row.summary || 'Untitled incident';
    var severity = String(row.severity || 'Low').toLowerCase();
    var status = row.status || 'Open';
    var lastSeen = Date.parse(row.lastEventAt || row.updatedAt || row.createdAt || row.timestamp || new Date().toISOString());
    return {
      id: row.incidentNumber || row.id || '',
      title: title,
      service: row.serviceName || row.service || row.applicationName || '-',
      domain: row.businessDomain || row.domain || row.businessJourney || '-',
      environment: row.environment || 'PROD',
      severity: severity.charAt(0).toUpperCase() + severity.slice(1),
      status: status,
      classification: row.classification || 'NEW',
      fingerprintExact: row.fingerprintExact || null,
      fingerprintFamily: row.fingerprintFamily || null,
      embeddingSimilarity: row.embeddingSimilarity || null,
      confidence: Number(row.confidence || 0),
      ownerTeam: row.ownerTeam || row.team || '-',
      firstSeen: Date.parse(row.firstEventAt || row.createdAt || new Date().toISOString()),
      lastSeen: isNaN(lastSeen) ? Date.now() : lastSeen,
      occurrences15d: Number(row.occurrences15d || row.occurrenceCount || 1),
      sources: Array.isArray(row.sources) ? row.sources : []
    };
  }

  function buildHourlyTrends(incidents) {
    var buckets = {};
    incidents.forEach(function(incident) {
      var d = new Date(incident.lastSeen);
      if (!isNaN(d.getTime())) {
        var key = String(d.getHours()).padStart(2, '0') + ':00';
        if (!buckets[key]) buckets[key] = { hour: key, total: 0, new: 0, recurring: 0, critical: 0, high: 0, medium: 0, low: 0 };
        buckets[key].total++;
        if (incident.classification === 'NEW') buckets[key].new++; else buckets[key].recurring++;
        var severityKey = String(incident.severity || '').toLowerCase();
        if (buckets[key][severityKey] !== undefined) buckets[key][severityKey]++;
      }
    });
    return Object.keys(buckets).sort().map(function(key) { return buckets[key]; });
  }

  // ============= KPI CARD COMPONENT =============
  function KpiCard(props) {
    var iconBgStyle = { width: 32, height: 32, borderRadius: 8, background: props.iconBg || COLORS.primaryLight, display: 'flex', alignItems: 'center', justifyContent: 'center' };
    var cardClass = 'kfh-card kpi-mini' + (props.pulse ? ' pulse-critical' : '');
    var cardStyle = props.leftBorder ? { borderLeft: '4px solid ' + props.leftBorder } : {};

    return h('div', { className: cardClass, style: cardStyle },
      h('div', { className: 'kpi-mini-header' },
        h('span', { className: 'kpi-mini-label' }, props.label),
        h('div', { style: iconBgStyle },
          h('span', { style: { fontSize: 14, color: props.iconFg || COLORS.primary } }, props.icon)
        )
      ),
      h('div', { className: 'kpi-mini-value', style: props.valueColor ? { color: props.valueColor } : {} }, props.value)
    );
  }

  // ============= BADGE COMPONENTS =============
  function SeverityBadge(props) {
    var config = {
      Critical: { bg: COLORS.criticalBg, color: COLORS.critical, border: '#FECACA' },
      High: { bg: COLORS.highBg, color: COLORS.high, border: '#FDE68A' },
      Medium: { bg: COLORS.mediumBg, color: COLORS.medium, border: '#FEF08A' },
      Low: { bg: COLORS.lowBg, color: COLORS.low, border: '#BFDBFE' }
    };
    var cfg = config[props.severity] || config.Low;
    var style = {
      display: 'inline-flex', alignItems: 'center', padding: '4px 10px', fontSize: 12, fontWeight: 600,
      borderRadius: 6, background: cfg.bg, color: cfg.color, border: '1px solid ' + cfg.border
    };
    return h('span', { style: style }, props.severity);
  }

  function ClassificationBadge(props) {
    var config = {
      NEW: { label: 'New', bg: COLORS.infoBg, color: COLORS.info, border: '#B2EBF2' },
      RECURRING_SURE: { label: 'Sure', bg: COLORS.criticalBg, color: COLORS.critical, border: '#FECACA' },
      RECURRING_LIKELY: { label: 'Likely', bg: COLORS.highBg, color: COLORS.high, border: '#FDE68A' },
      POSSIBLE: { label: 'Possible', bg: '#F3E8FF', color: '#7E22CE', border: '#E9D5FF' }
    };
    var cfg = config[props.classification] || config.NEW;
    var style = {
      display: 'inline-flex', alignItems: 'center', padding: '4px 10px', fontSize: 12, fontWeight: 600,
      borderRadius: 6, background: cfg.bg, color: cfg.color, border: '1px solid ' + cfg.border
    };
    return h('span', { style: style }, cfg.label);
  }

  function StatusBadge(props) {
    var config = {
      Open: { bg: COLORS.infoBg, color: COLORS.info },
      Acknowledged: { bg: COLORS.highBg, color: COLORS.high },
      InProgress: { bg: COLORS.primaryLight, color: COLORS.primary },
      Resolved: { bg: COLORS.successBg, color: COLORS.success },
      Closed: { bg: '#F3F4F6', color: COLORS.textSecondary }
    };
    var cfg = config[props.status] || config.Open;
    var style = {
      display: 'inline-flex', alignItems: 'center', padding: '4px 10px', fontSize: 12, fontWeight: 600,
      borderRadius: 6, background: cfg.bg, color: cfg.color
    };
    return h('span', { style: style }, props.status);
  }

  function SourceBadge(props) {
    var colors = {
      SCOM: { bg: '#EFF6FF', color: '#2563EB' },
      vROps: { bg: COLORS.successBg, color: COLORS.success },
      BMC: { bg: '#FFF7ED', color: '#EA580C' },
      SolarWinds: { bg: COLORS.highBg, color: COLORS.high },
      Elastic: { bg: '#FDF2F8', color: '#DB2777' },
      Dynatrace: { bg: '#F3E8FF', color: '#7E22CE' }
    };
    var cfg = colors[props.source] || { bg: '#F3F4F6', color: COLORS.textSecondary };
    var style = {
      display: 'inline-flex', padding: '2px 8px', fontSize: 10, fontWeight: 600,
      borderRadius: 4, background: cfg.bg, color: cfg.color, marginRight: 4
    };
    return h('span', { style: style }, props.source);
  }

  // ============= MAIN DASHBOARD COMPONENT =============
  function IncidentsDashboard() {
    var _useState = React.useState;
    var _useMemo = React.useMemo;

    var activeTabState = _useState('all');
    var activeTab = activeTabState[0];
    var setActiveTab = activeTabState[1];

    var searchState = _useState('');
    var search = searchState[0];
    var setSearch = searchState[1];

    var severityFilterState = _useState('');
    var severityFilter = severityFilterState[0];
    var setSeverityFilter = severityFilterState[1];

    var selectedIncidentState = _useState(null);
    var selectedIncident = selectedIncidentState[0];
    var setSelectedIncident = selectedIncidentState[1];

    // Calculate KPIs
    var kpis = _useMemo(function() {
      var open = incidentsData.filter(function(i) { return i.status === 'Open' || i.status === 'Acknowledged' || i.status === 'InProgress'; });
      var criticalOpen = open.filter(function(i) { return i.severity === 'Critical'; });
      var new15d = incidentsData.filter(function(i) { return i.classification === 'NEW'; });
      var recurring15d = incidentsData.filter(function(i) { return i.classification !== 'NEW'; });
      return {
        totalOpen: open.length,
        criticalOpen: criticalOpen.length,
        new15d: new15d.length,
        recurring15d: recurring15d.length,
        noisyGroups: 0,
        avgMTTA: 'N/A',
        avgMTTR: 'N/A'
      };
    }, [incidentsData.length]);

    // Calculate breakdowns
    var breakdowns = _useMemo(function() {
      var bySeverity = SEVERITIES.map(function(sev) {
        return { name: sev, value: incidentsData.filter(function(i) { return i.severity === sev; }).length };
      });
      var bySource = SOURCES.map(function(src) {
        return { name: src, value: incidentsData.filter(function(i) { return i.sources.indexOf(src) >= 0; }).length };
      }).filter(function(s) { return s.value > 0; });
      return { bySeverity: bySeverity, bySource: bySource };
    }, [incidentsData.length]);

    // Filter incidents
    var filteredIncidents = _useMemo(function() {
      return incidentsData.filter(function(inc) {
        if (activeTab === 'new' && inc.classification !== 'NEW') return false;
        if (activeTab === 'recurring' && inc.classification === 'NEW') return false;
        if (severityFilter && inc.severity !== severityFilter) return false;
        if (search) {
          var s = search.toLowerCase();
          if (inc.title.toLowerCase().indexOf(s) < 0 && inc.id.toLowerCase().indexOf(s) < 0) return false;
        }
        return true;
      });
    }, [activeTab, severityFilter, search, incidentsData.length]);

    // Tooltip style for Recharts
    var tooltipStyle = { backgroundColor: COLORS.surface, border: 'none', borderRadius: 12, boxShadow: '0 4px 12px rgba(0,0,0,0.1)' };

    // ============= RENDER =============
    return h('div', { style: { maxWidth: 1800, margin: '0 auto', padding: '24px 32px 32px' } },
      h('div', { className: 'kfh-card', style: { padding: 20, marginBottom: 24, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16 } },
        h('div', null,
          h('p', { style: { margin: 0, color: COLORS.gold, fontSize: 12, fontWeight: 900, letterSpacing: '0.08em', textTransform: 'uppercase' } }, 'Incident Management'),
          h('h1', { style: { margin: '4px 0 0', fontSize: 28, fontWeight: 900, color: COLORS.textPrimary } }, 'Incidents')
        ),
        h('input', {
          className: 'kfh-input',
          value: search,
          onChange: function(e) { setSearch(e.target.value || ''); },
          placeholder: 'Search incidents',
          style: { width: 320 }
        })
      ),
      // KPI Strip
      h('div', { className: 'kpi-strip', style: { marginBottom: 24 } },
        h(KpiCard, { label: 'Total Open', value: kpis.totalOpen, icon: '📋', iconBg: COLORS.primaryLight, iconFg: COLORS.primary }),
        h(KpiCard, { label: 'Critical Open', value: kpis.criticalOpen, icon: '🔴', iconBg: COLORS.criticalBg, iconFg: COLORS.critical, valueColor: COLORS.critical, pulse: true, leftBorder: COLORS.critical }),
        h(KpiCard, { label: 'New (15d)', value: kpis.new15d, icon: '✨', iconBg: COLORS.infoBg, iconFg: COLORS.info }),
        h(KpiCard, { label: 'Recurring (15d)', value: kpis.recurring15d, icon: '🔄', iconBg: COLORS.highBg, iconFg: COLORS.high }),
        h(KpiCard, { label: 'Noisy Groups', value: kpis.noisyGroups, icon: '📢', iconBg: '#F0EFE9', iconFg: COLORS.gold }),
        h(KpiCard, { label: 'Avg MTTA', value: kpis.avgMTTA, icon: '⏱️', iconBg: COLORS.primaryLight, iconFg: COLORS.primary }),
        h(KpiCard, { label: 'Avg MTTR', value: kpis.avgMTTR, icon: '✅', iconBg: COLORS.successBg, iconFg: COLORS.success })
      ),

      // Charts Grid
      h('div', { style: { display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 24, marginBottom: 24 } },
        // Incidents per Hour
        h('div', { className: 'kfh-card chart-card' },
          h('h3', { className: 'chart-title' }, '📈 Incidents per Hour'),
          h('div', { className: 'chart-container' },
            h(ResponsiveContainer, { width: '100%', height: '100%' },
              h(AreaChart, { data: hourlyTrends },
                h('defs', null,
                  h('linearGradient', { id: 'colorTotal', x1: 0, y1: 0, x2: 0, y2: 1 },
                    h('stop', { offset: '5%', stopColor: COLORS.primary, stopOpacity: 0.3 }),
                    h('stop', { offset: '95%', stopColor: COLORS.primary, stopOpacity: 0 })
                  )
                ),
                h(CartesianGrid, { strokeDasharray: '3 3', stroke: '#E5E7EB' }),
                h(XAxis, { dataKey: 'hour', tick: { fill: COLORS.textMuted, fontSize: 12 }, axisLine: { stroke: '#E5E7EB' } }),
                h(YAxis, { tick: { fill: COLORS.textMuted, fontSize: 12 }, axisLine: { stroke: '#E5E7EB' } }),
                h(Tooltip, { contentStyle: tooltipStyle }),
                h(Area, { type: 'monotone', dataKey: 'total', stroke: COLORS.primary, fill: 'url(#colorTotal)', strokeWidth: 2 })
              )
            )
          )
        ),

        // New vs Recurring
        h('div', { className: 'kfh-card chart-card' },
          h('h3', { className: 'chart-title' }, '📊 New vs Recurring'),
          h('div', { className: 'chart-container' },
            h(ResponsiveContainer, { width: '100%', height: '100%' },
              h(ComposedChart, { data: hourlyTrends },
                h(CartesianGrid, { strokeDasharray: '3 3', stroke: '#E5E7EB' }),
                h(XAxis, { dataKey: 'hour', tick: { fill: COLORS.textMuted, fontSize: 12 } }),
                h(YAxis, { tick: { fill: COLORS.textMuted, fontSize: 12 } }),
                h(Tooltip, { contentStyle: tooltipStyle }),
                h(Legend, null),
                h(Bar, { dataKey: 'new', name: 'New', fill: COLORS.primary, radius: [4, 4, 0, 0] }),
                h(Line, { type: 'monotone', dataKey: 'recurring', name: 'Recurring', stroke: COLORS.high, strokeWidth: 2 })
              )
            )
          )
        ),

        // Source Distribution
        h('div', { className: 'kfh-card chart-card' },
          h('h3', { className: 'chart-title' }, '🔌 Source Distribution'),
          h('div', { className: 'chart-container' },
            h(ResponsiveContainer, { width: '100%', height: '100%' },
              h(PieChart, null,
                h(Pie, { data: breakdowns.bySource, cx: '50%', cy: '50%', innerRadius: 50, outerRadius: 80, paddingAngle: 2, dataKey: 'value' },
                  breakdowns.bySource.map(function(entry, index) {
                    return h(Cell, { key: 'cell-' + index, fill: CHART_COLORS[index % CHART_COLORS.length] });
                  })
                ),
                h(Tooltip, { contentStyle: tooltipStyle })
              )
            )
          ),
          h('div', { style: { display: 'flex', flexWrap: 'wrap', justifyContent: 'center', gap: 8, marginTop: 12 } },
            breakdowns.bySource.map(function(s, idx) {
              return h('div', { key: s.name, style: { display: 'flex', alignItems: 'center', gap: 6 } },
                h('div', { style: { width: 8, height: 8, borderRadius: '50%', background: CHART_COLORS[idx % CHART_COLORS.length] } }),
                h('span', { style: { fontSize: 12, color: COLORS.textSecondary } }, s.name + ': ' + s.value)
              );
            })
          )
        ),

        // Severity Breakdown
        h('div', { className: 'kfh-card chart-card' },
          h('h3', { className: 'chart-title' }, '⚠️ Severity Breakdown'),
          h('div', { className: 'chart-container' },
            h(ResponsiveContainer, { width: '100%', height: '100%' },
              h(BarChart, { data: breakdowns.bySeverity, layout: 'vertical' },
                h(CartesianGrid, { strokeDasharray: '3 3', stroke: '#E5E7EB', horizontal: false }),
                h(XAxis, { type: 'number', tick: { fill: COLORS.textMuted, fontSize: 12 } }),
                h(YAxis, { dataKey: 'name', type: 'category', tick: { fill: COLORS.textMuted, fontSize: 12 }, width: 70 }),
                h(Tooltip, { contentStyle: tooltipStyle }),
                h(Bar, { dataKey: 'value', radius: [0, 4, 4, 0] },
                  breakdowns.bySeverity.map(function(entry) {
                    var colors = { Critical: COLORS.critical, High: COLORS.high, Medium: COLORS.medium, Low: COLORS.low };
                    return h(Cell, { key: entry.name, fill: colors[entry.name] || COLORS.primary });
                  })
                )
              )
            )
          )
        )
      ),

      // Filters Card
      h('div', { className: 'kfh-card', style: { padding: 20, marginBottom: 24 } },
        h('div', { style: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 } },
          h('h3', { style: { fontSize: 14, fontWeight: 700, color: COLORS.textPrimary } }, '🔍 Filters'),
          h('button', {
            style: { fontSize: 12, color: COLORS.primary, fontWeight: 600, background: 'none', border: 'none', cursor: 'pointer' },
            onClick: function() { setSeverityFilter(''); setSearch(''); }
          }, 'Clear All')
        ),
        h('div', { style: { display: 'grid', gridTemplateColumns: 'repeat(6, 1fr)', gap: 12 } },
          h('select', {
            className: 'kfh-input',
            value: severityFilter,
            onChange: function(e) { setSeverityFilter(e.target.value); }
          },
            h('option', { value: '' }, 'All Severities'),
            SEVERITIES.map(function(s) { return h('option', { key: s, value: s }, s); })
          ),
          h('select', { className: 'kfh-input' },
            h('option', { value: '' }, 'All Domains'),
            DOMAINS.map(function(d) { return h('option', { key: d, value: d }, d); })
          ),
          h('select', { className: 'kfh-input' },
            h('option', { value: '' }, 'All Environments'),
            ENVIRONMENTS.map(function(e) { return h('option', { key: e, value: e }, e); })
          ),
          h('select', { className: 'kfh-input' },
            h('option', { value: '' }, 'All Statuses'),
            STATUSES.map(function(s) { return h('option', { key: s, value: s }, s); })
          ),
          h('select', { className: 'kfh-input' },
            h('option', { value: '' }, 'All Teams'),
            TEAMS.map(function(t) { return h('option', { key: t, value: t }, t); })
          ),
          h('select', { className: 'kfh-input' },
            h('option', { value: '' }, 'All Sources'),
            SOURCES.map(function(s) { return h('option', { key: s, value: s }, s); })
          )
        )
      ),

      // Incidents Table
      h('div', { className: 'kfh-card incidents-table-wrap' },
        // Table Header
        h('div', { className: 'table-header' },
          h('div', { className: 'table-tabs' },
            h('button', {
              className: 'table-tab' + (activeTab === 'all' ? ' active' : ''),
              onClick: function() { setActiveTab('all'); }
            }, 'All'),
            h('button', {
              className: 'table-tab' + (activeTab === 'new' ? ' active' : ''),
              onClick: function() { setActiveTab('new'); }
            }, 'New'),
            h('button', {
              className: 'table-tab' + (activeTab === 'recurring' ? ' active' : ''),
              onClick: function() { setActiveTab('recurring'); }
            }, 'Recurring')
          ),
          h('span', { className: 'table-count' }, filteredIncidents.length + ' incidents')
        ),

        // Table
        h('div', { style: { overflowX: 'auto' } },
          h('table', { className: 'incidents-table' },
            h('thead', null,
              h('tr', null,
                h('th', null, 'Severity'),
                h('th', null, 'Title'),
                h('th', null, 'Service'),
                h('th', null, 'Sources'),
                h('th', null, 'Classification'),
                h('th', { className: 'text-center' }, 'Occur.'),
                h('th', null, 'Last Seen'),
                h('th', { className: 'text-center' }, 'Confidence'),
                h('th', null, 'Team'),
                h('th', null, 'Status'),
                h('th', { className: 'text-center' }, 'Actions')
              )
            ),
            h('tbody', null,
              filteredIncidents.slice(0, 50).map(function(inc) {
                return h('tr', {
                  key: inc.id,
                  onClick: function() { setSelectedIncident(inc); }
                },
                  h('td', null, h(SeverityBadge, { severity: inc.severity })),
                  h('td', null,
                    h('div', { className: 'incident-title' }, inc.title),
                    h('div', { className: 'incident-id' }, inc.id)
                  ),
                  h('td', null,
                    h('div', { className: 'incident-service' }, inc.service),
                    h('div', { className: 'incident-domain' }, inc.domain)
                  ),
                  h('td', null,
                    inc.sources.map(function(src) { return h(SourceBadge, { key: src, source: src }); })
                  ),
                  h('td', null, h(ClassificationBadge, { classification: inc.classification })),
                  h('td', { style: { textAlign: 'center', fontWeight: 600 } }, inc.occurrences15d),
                  h('td', { style: { color: COLORS.textSecondary, fontSize: 13 } }, new Date(inc.lastSeen).toLocaleString()),
                  h('td', { style: { textAlign: 'center' } },
                    inc.confidence > 0
                      ? h('span', { style: { fontWeight: 700, color: COLORS.primary } }, inc.confidence + '%')
                      : h('span', { style: { color: COLORS.textMuted } }, 'N/A')
                  ),
                  h('td', { style: { fontSize: 13, color: COLORS.textSecondary } }, inc.ownerTeam),
                  h('td', null, h(StatusBadge, { status: inc.status })),
                  h('td', { style: { textAlign: 'center' } },
                    h('button', {
                      style: { padding: 8, borderRadius: 8, background: 'transparent', border: 'none', cursor: 'pointer', color: COLORS.textSecondary },
                      onClick: function(e) { e.stopPropagation(); setSelectedIncident(inc); }
                    }, '👁️')
                  )
                );
              })
            )
          )
        ),

        // Empty state
        filteredIncidents.length === 0 && h('div', { style: { textAlign: 'center', padding: 48, color: COLORS.textMuted } },
          h('div', { style: { fontSize: 48, marginBottom: 16 } }, '📭'),
          h('p', null, 'No incidents found matching your criteria')
        )
      ),

      // Incident Details Drawer
      selectedIncident && h(IncidentDrawer, {
        incident: selectedIncident,
        onClose: function() { setSelectedIncident(null); }
      })
    );
  }

  // ============= INCIDENT DRAWER COMPONENT =============
  function IncidentDrawer(props) {
    var incident = props.incident;
    var onClose = props.onClose;
    var _useState = React.useState;
    var activeTabState = _useState('summary');
    var activeTab = activeTabState[0];
    var setActiveTab = activeTabState[1];

    var tabs = ['Summary', 'Evidence', 'Timeline', 'Correlations', 'Topology'];

    // Demo evidence data
    var evidenceData = [
      { id: 'ev-1', type: 'Error', source: incident.sources[0] || 'SCOM', time: '2 min ago', snippet: 'ERROR [' + incident.service + '] ' + incident.title + '\n  at com.kfh.banking.core.ServiceManager.process(ServiceManager.java:142)\n  at com.kfh.banking.core.RequestHandler.handle(RequestHandler.java:89)' },
      { id: 'ev-2', type: 'Warning', source: 'ServiceNow', time: '5 min ago', snippet: 'WARN: Resource utilization exceeded threshold\nService: ' + incident.service + ' | Environment: ' + incident.environment + '\nThreshold: 90% | Current: 94%' },
      { id: 'ev-3', type: 'Metric', source: 'Datadog', time: '8 min ago', snippet: 'cpu.usage: 94.2%\nmemory.usage: 87.5%\nconnection.pool.active: 98\nconnection.pool.max: 100' }
    ];

    // Demo timeline data
    var timelineData = [
      { time: '2 min ago', event: 'Incident Created', desc: 'AIOps automatically created incident from correlated alerts', color: COLORS.critical },
      { time: '5 min ago', event: 'Threshold Breached', desc: 'Resource utilization exceeded 90% threshold on ' + incident.service, color: COLORS.high },
      { time: '8 min ago', event: 'Alert Triggered', desc: incident.sources[0] + ' detected anomaly in service metrics', color: COLORS.info },
      { time: '15 min ago', event: 'Normal Operation', desc: 'System operating within normal parameters', color: COLORS.success }
    ];

    // Demo correlations data
    var correlationsData = [
      { id: 'INC-001198', title: 'Similar incident - Same Service', time: '3 days ago', similarity: 92, status: 'Resolved', resolution: 'Restarted service and applied connection leak fix' },
      { id: 'INC-001156', title: 'Related resource issue', time: '5 days ago', similarity: 87, status: 'Resolved', resolution: 'Optimized query performance and increased pool size' },
      { id: 'INC-001089', title: 'Connection leak detected', time: '12 days ago', similarity: 85, status: 'Resolved', resolution: 'Applied code fix to properly close connections' }
    ];

    // Topology services
    var topologyServices = [
      { name: 'PostgreSQL DB', status: 'Critical', color: COLORS.critical },
      { name: incident.service, status: 'Critical', color: COLORS.critical },
      { name: 'Payment Gateway', status: 'Degraded', color: COLORS.high },
      { name: 'Mobile App', status: 'Healthy', color: COLORS.success }
    ];

    function renderTabContent() {
      if (activeTab === 'summary') {
        return h('div', null,
          // Badges row
          h('div', { style: { display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 24 } },
            h(SeverityBadge, { severity: incident.severity }),
            h(StatusBadge, { status: incident.status }),
            h(ClassificationBadge, { classification: incident.classification }),
            h('span', {
              style: { display: 'inline-flex', padding: '4px 10px', fontSize: 12, fontWeight: 600, borderRadius: 6, background: COLORS.primaryLight, color: COLORS.primaryDark }
            }, incident.environment)
          ),

          // Title
          h('h3', { style: { fontSize: 20, fontWeight: 700, color: COLORS.textPrimary, marginBottom: 24, lineHeight: 1.4 } }, incident.title),

          // Key info grid
          h('div', { style: { display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 16, marginBottom: 24 } },
            h('div', null,
              h('span', { style: { display: 'block', fontSize: 12, color: COLORS.textSecondary, marginBottom: 4 } }, 'Service'),
              h('span', { style: { fontSize: 14, fontWeight: 600, color: COLORS.textPrimary } }, incident.service)
            ),
            h('div', null,
              h('span', { style: { display: 'block', fontSize: 12, color: COLORS.textSecondary, marginBottom: 4 } }, 'Team'),
              h('span', { style: { fontSize: 14, fontWeight: 600, color: COLORS.textPrimary } }, incident.ownerTeam)
            ),
            h('div', null,
              h('span', { style: { display: 'block', fontSize: 12, color: COLORS.textSecondary, marginBottom: 4 } }, 'Last Seen'),
              h('span', { style: { fontSize: 14, fontWeight: 600, color: COLORS.textPrimary } }, new Date(incident.lastSeen).toLocaleString())
            ),
            h('div', null,
              h('span', { style: { display: 'block', fontSize: 12, color: COLORS.textSecondary, marginBottom: 4 } }, 'Confidence'),
              h('span', { style: { fontSize: 14, fontWeight: 700, color: COLORS.primary } }, incident.confidence > 0 ? incident.confidence + '%' : 'N/A')
            )
          ),

          // Stats card
          h('div', { style: { background: COLORS.surfaceBg, borderRadius: 12, padding: 16, marginBottom: 24 } },
            h('div', { style: { display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 16, textAlign: 'center' } },
              h('div', null,
                h('div', { style: { fontSize: 12, color: COLORS.textSecondary, marginBottom: 4 } }, 'Occurrences'),
                h('div', { style: { fontSize: 20, fontWeight: 700, color: COLORS.textPrimary } }, incident.occurrences15d)
              ),
              h('div', null,
                h('div', { style: { fontSize: 12, color: COLORS.textSecondary, marginBottom: 4 } }, 'Sources'),
                h('div', { style: { fontSize: 20, fontWeight: 700, color: COLORS.textPrimary } }, incident.sources.length)
              ),
              h('div', null,
                h('div', { style: { fontSize: 12, color: COLORS.textSecondary, marginBottom: 4 } }, 'Domain'),
                h('div', { style: { fontSize: 14, fontWeight: 600, color: COLORS.textPrimary } }, incident.domain)
              )
            )
          ),

          // RCA Section
          h('div', {
            style: {
              background: COLORS.highBg, borderLeft: '4px solid ' + COLORS.high, borderRadius: 12, padding: 16, marginBottom: 24
            }
          },
            h('div', { style: { display: 'flex', alignItems: 'flex-start', gap: 12 } },
              h('span', { style: { fontSize: 20 } }, '💡'),
              h('div', null,
                h('h4', { style: { fontWeight: 700, color: COLORS.textPrimary, marginBottom: 8 } }, 'Root Cause Analysis'),
                h('p', { style: { fontSize: 13, color: COLORS.textSecondary, marginBottom: 12, lineHeight: 1.6 } },
                  incident.classification === 'NEW'
                    ? 'This is a new incident pattern. No historical matches found. The system will continue monitoring for recurrence.'
                    : 'This is a recurring pattern. Similar incidents have been observed ' + incident.occurrences15d + ' times in the last 15 days.'
                ),
                h('h5', { style: { fontWeight: 700, fontSize: 13, color: COLORS.textPrimary, marginBottom: 8 } }, 'Recommended Actions'),
                h('ul', { style: { fontSize: 13, color: COLORS.textSecondary, paddingLeft: 16, margin: 0 } },
                  h('li', { style: { marginBottom: 4 } }, '• Check service logs for ' + incident.service),
                  h('li', { style: { marginBottom: 4 } }, '• Review recent deployment changes'),
                  h('li', { style: { marginBottom: 4 } }, '• Verify resource utilization metrics'),
                  h('li', null, '• Escalate to ' + incident.ownerTeam + ' if issue persists')
                )
              )
            )
          ),

          // Sources
          h('div', { style: { marginBottom: 24 } },
            h('h4', { style: { fontSize: 12, fontWeight: 700, color: COLORS.textSecondary, textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 8 } }, 'Sources'),
            h('div', { style: { display: 'flex', flexWrap: 'wrap', gap: 8 } },
              incident.sources.map(function(src) { return h(SourceBadge, { key: src, source: src }); })
            )
          ),

          // Actions
          h('div', { style: { display: 'flex', flexDirection: 'column', gap: 12 } },
            h('button', {
              style: {
                width: '100%', padding: '14px 20px', borderRadius: 8, background: COLORS.primary, color: COLORS.surface,
                fontSize: 14, fontWeight: 600, border: 'none', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8
              }
            }, '✓ Acknowledge Incident'),
            h('button', {
              style: {
                width: '100%', padding: '14px 20px', borderRadius: 8, background: COLORS.surface, color: COLORS.textPrimary,
                fontSize: 14, fontWeight: 600, border: '1px solid rgba(0,0,0,0.1)', cursor: 'pointer'
              }
            }, 'Close Incident'),
            h('button', {
              style: {
                width: '100%', padding: '14px 20px', borderRadius: 8, background: COLORS.surface, color: COLORS.textPrimary,
                fontSize: 14, fontWeight: 600, border: '1px solid rgba(0,0,0,0.1)', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8
              }
            }, '📄 Create Situation')
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
                h('pre', { style: { background: COLORS.textPrimary, color: COLORS.primaryLight, padding: 12, borderRadius: 8, fontSize: 12, fontFamily: 'monospace', overflow: 'auto', margin: 0, whiteSpace: 'pre-wrap' } }, ev.snippet)
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
          h('h4', { style: { fontSize: 14, fontWeight: 700, color: COLORS.textPrimary, marginBottom: 16 } }, 'Related Incidents'),
          h('div', { style: { display: 'flex', flexDirection: 'column', gap: 12 } },
            correlationsData.map(function(corr) {
              return h('div', { key: corr.id, style: { border: '1px solid rgba(0,0,0,0.06)', borderRadius: 12, padding: 16, cursor: 'pointer', transition: 'box-shadow 0.2s' } },
                h('div', { style: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 } },
                  h('span', { style: { fontSize: 12, fontFamily: 'monospace', color: COLORS.textSecondary } }, corr.id),
                  h('span', { style: { padding: '4px 10px', fontSize: 11, fontWeight: 600, borderRadius: 6, background: COLORS.successBg, color: COLORS.success } }, corr.status)
                ),
                h('h5', { style: { fontSize: 14, fontWeight: 600, color: COLORS.textPrimary, marginBottom: 8 } }, corr.title),
                h('p', { style: { fontSize: 12, color: COLORS.textSecondary, marginBottom: 8 } }, corr.resolution),
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
              'Pattern analysis will appear here when recurrence evidence is returned by the RCA and incident APIs.'
            )
          )
        );
      }

      if (activeTab === 'topology') {
        return h('div', null,
          h('h4', { style: { fontSize: 14, fontWeight: 700, color: COLORS.textPrimary, marginBottom: 16 } }, 'Service Dependency Map'),
          h('div', { style: { background: COLORS.surfaceBg, borderRadius: 12, padding: 24, marginBottom: 24 } },
            h('div', { style: { display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 16 } },
              topologyServices.map(function(svc, idx) {
                return h('div', { key: idx, style: { display: 'flex', flexDirection: 'column', alignItems: 'center' } },
                  h('div', {
                    style: { width: 80, height: 80, borderRadius: 12, background: svc.color, display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 8 }
                  },
                    h('span', { style: { fontSize: 32, color: COLORS.surface } }, '🔷')
                  ),
                  h('span', { style: { fontSize: 13, fontWeight: 600, color: COLORS.textPrimary, textAlign: 'center' } }, svc.name),
                  h('span', { style: { fontSize: 12, color: svc.color, fontWeight: 600 } }, svc.status),
                  idx < topologyServices.length - 1 && h('div', { style: { width: 2, height: 24, background: '#A79F91', margin: '8px 0' } })
                );
              })
            )
          ),
          // Impact Analysis
          h('h4', { style: { fontSize: 14, fontWeight: 700, color: COLORS.textPrimary, marginBottom: 12 } }, 'Impact Analysis'),
          h('div', { style: { display: 'flex', flexDirection: 'column', gap: 8 } },
            h('div', { style: { display: 'flex', alignItems: 'center', gap: 8, padding: 12, background: COLORS.criticalBg, borderRadius: 8 } },
              h('div', { style: { width: 8, height: 8, borderRadius: '50%', background: COLORS.critical } }),
              h('div', null,
                h('p', { style: { fontSize: 13, fontWeight: 600, color: COLORS.textPrimary, margin: 0 } }, 'PostgreSQL DB → ' + incident.service),
                h('p', { style: { fontSize: 12, color: COLORS.textSecondary, margin: 0 } }, 'Connection pool exhaustion preventing new transactions')
              )
            ),
            h('div', { style: { display: 'flex', alignItems: 'center', gap: 8, padding: 12, background: COLORS.highBg, borderRadius: 8 } },
              h('div', { style: { width: 8, height: 8, borderRadius: '50%', background: COLORS.high } }),
              h('div', null,
                h('p', { style: { fontSize: 13, fontWeight: 600, color: COLORS.textPrimary, margin: 0 } }, incident.service + ' → Payment Gateway'),
                h('p', { style: { fontSize: 12, color: COLORS.textSecondary, margin: 0 } }, 'Increased latency and timeout errors')
              )
            ),
            h('div', { style: { display: 'flex', alignItems: 'center', gap: 8, padding: 12, background: COLORS.successBg, borderRadius: 8 } },
              h('div', { style: { width: 8, height: 8, borderRadius: '50%', background: COLORS.success } }),
              h('div', null,
                h('p', { style: { fontSize: 13, fontWeight: 600, color: COLORS.textPrimary, margin: 0 } }, 'Client Applications'),
                h('p', { style: { fontSize: 12, color: COLORS.textSecondary, margin: 0 } }, 'Operating normally with graceful error handling')
              )
            )
          )
        );
      }

      return null;
    }

    return h('div', null,
      // Overlay
      h('div', {
        style: {
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)', zIndex: 100, cursor: 'pointer'
        },
        onClick: onClose
      }),

      // Drawer
      h('div', {
        style: {
          position: 'fixed', top: 0, right: 0, width: 600, maxWidth: '90vw', height: '100%',
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
          h('h2', { style: { fontSize: 18, fontWeight: 700, color: COLORS.textPrimary } }, incident.id),
          h('button', {
            style: { padding: 8, borderRadius: 8, background: 'transparent', border: 'none', cursor: 'pointer', fontSize: 18 },
            onClick: onClose
          }, '✕')
        ),

        // Tabs
        h('div', {
          style: {
            background: COLORS.surface, borderBottom: '1px solid rgba(0,0,0,0.06)',
            padding: '0 24px', display: 'flex', gap: 0
          }
        },
          tabs.map(function(tab) {
            var tabId = tab.toLowerCase();
            var isActive = activeTab === tabId;
            return h('button', {
              key: tab,
              style: {
                padding: '12px 16px', fontSize: 14, fontWeight: isActive ? 600 : 500, color: isActive ? COLORS.primary : COLORS.textSecondary,
                background: 'none', border: 'none', borderBottom: isActive ? '2px solid ' + COLORS.primary : '2px solid transparent',
                cursor: 'pointer', transition: 'all 0.2s'
              },
              onClick: function() { setActiveTab(tabId); }
            }, tab);
          })
        ),

        // Content (scrollable)
        h('div', { style: { flex: 1, overflowY: 'auto', padding: 24 } },
          renderTabContent()
        )
      )
    );
  }

  // ============= MOUNT APPLICATION =============
  function renderApp(root) {
    if (root) root.render(h(IncidentsDashboard));
    else ReactDOM.render(h(IncidentsDashboard), mountEl);
  }

  async function loadIncidents(root) {
    if (!window.APIClient || !APIClient.incidents) return;
    try {
      var response = await APIClient.incidents.list({ page: 0, size: 100 });
      incidentsData = pageContent(response).map(normalizeIncident);
      hourlyTrends = buildHourlyTrends(incidentsData);
      renderApp(root);
    } catch (error) {
      console.warn('[Incidents] Unable to load production incidents; rendering empty state.', error);
    }
  }

  try {
    var root = ReactDOM.createRoot(mountEl);
    renderApp(root);
    loadIncidents(root);
  } catch (e) {
    renderApp(null);
    loadIncidents(null);
  }

})();
