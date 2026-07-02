/**
 * KFH AIOps Command Center — Incidents (Problems)
 * Dynatrace-parity list view in KFH "Beyond Horizons" colors.
 *
 * List view  : dense faceted filter rail (flush with content) + toolbar
 *              (Type-to-filter, time range, refresh) + area chart + compact
 *              incident table, matching Dynatrace Problems page density.
 * Detail view: header (status·id·severity·category·duration + Explain/Graph/Notify)
 *              → impact tile strip → tabs → Overview = Impact ↔ Root cause
 *              + Visual resolution path. Each card is a drill-down.
 *
 * Data below is the Fund Transfer / SAN-STORAGE worked example — illustrative
 * until the correlation + RCA + AI stages (Phases 2–4) light it up live.
 */
(function () {
  'use strict';

  var mount = document.getElementById('page-root');
  if (!mount) return;

  function esc(v) {
    return String(v == null ? '' : v).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  var SEV_CLASS = { Critical: 'sev-critical', High: 'sev-high', Medium: 'sev-medium', Low: 'sev-low' };

  // ---- Illustrative incidents (replaced by live correlation/RCA output in Phases 2–4) ----
  var INCIDENTS = [
    {
      id: 'INC-20260701-014', status: 'Open', classification: 'New', severity: 'Critical', category: 'Availability',
      title: 'Mobile Banking — Fund Transfer degraded', started: 'Jul 1, 2026, 10:00', duration: '33m',
      apps: ['Fund Transfer', 'KFHOnline', 'WAMD'], services: 3, assets: 5, journeys: 2, alerts: 142, sources: ['BMC', 'SCOM'],
      rootCause: {
        id: 'SAN-STORAGE-02', type: 'SAN Storage', confidence: 0.74,
        evidence: [
          { sev: 'crit', src: 'SCOM', ref: 'E1', title: 'LUN-04 write latency 2ms → 82ms (20×)', time: '10:00' },
          { sev: 'high', src: 'SCOM', ref: 'E2', title: 'Oracle buffer-busy waits +480%', time: '10:14' },
          { sev: 'high', src: 'BMC', ref: 'E3', title: 'Transfer Service DB timeouts (1,247)', time: '10:15' },
          { sev: 'crit', src: 'BMC', ref: 'E4', title: 'API Gateway /transfer HTTP 502 (412/min)', time: '10:16' }
        ]
      },
      ai: 'SAN Storage 02 write latency rose 20× at 10:00, causing Oracle buffer-busy waits at 10:14, Transfer Service DB timeouts at 10:15 and API gateway 502s at 10:16. Mobile Banking Fund Transfer success fell to 81.4%.',
      recommended: 'Engage Storage team for SAN-STORAGE-02 LUN-04 controller; fail transfer workloads over to SAN-STORAGE-03 if latency persists > 15 min.',
      journeysDetail: [{ name: 'Fund Transfer', before: 99.2, after: 81.4 }, { name: 'WAMD Instant Payment', before: 99.6, after: 92.1 }],
      path: [
        { name: 'SAN-STORAGE-02', stage: 'Storage', health: 'crit' },
        { name: 'ORACLE-CORE-01', stage: 'Database', health: 'warn' },
        { name: 'Transfer Service', stage: 'Service', health: 'warn' },
        { name: 'API Gateway', stage: 'Gateway', health: 'ok' }
      ]
    },
    {
      id: 'INC-20260701-011', status: 'Open', classification: 'Recurring', severity: 'High', category: 'Performance',
      title: 'KFHOnline login latency elevated', started: 'Jul 1, 2026, 09:42', duration: '51m',
      apps: ['KFHOnline'], services: 2, assets: 3, journeys: 1, alerts: 63, sources: ['SCOM'],
      rootCause: { id: 'LDAP-01', type: 'Directory', confidence: 0.61, evidence: [{ sev: 'high', src: 'SCOM', ref: 'E1', title: 'LDAP bind time +300%', time: '09:42' }] },
      ai: 'LDAP-01 bind latency tripled at 09:42, slowing KFHOnline authentication.', recommended: 'Check LDAP-01 replication and connection pool.',
      journeysDetail: [{ name: 'KFHOnline Login', before: 99.9, after: 96.4 }],
      path: [{ name: 'LDAP-01', stage: 'Directory', health: 'warn' }, { name: 'Auth Service', stage: 'Service', health: 'warn' }, { name: 'API Gateway', stage: 'Gateway', health: 'ok' }]
    },
    {
      id: 'INC-20260701-007', status: 'Closed', classification: 'Recurring', severity: 'Medium', category: 'Resource contention',
      title: 'Report generator CPU saturation', started: 'Jul 1, 2026, 08:05', duration: '1h 12m',
      apps: ['Reporting'], services: 1, assets: 2, journeys: 0, alerts: 28, sources: ['BMC'],
      rootCause: { id: 'RPT-APP-03', type: 'Server', confidence: 0.55, evidence: [{ sev: 'medium', src: 'BMC', ref: 'E1', title: 'CPU 95% sustained 40m', time: '08:05' }] },
      ai: 'RPT-APP-03 CPU saturated during batch reporting window.', recommended: 'Stagger batch schedule or scale the report worker.',
      journeysDetail: [], path: [{ name: 'RPT-APP-03', stage: 'Server', health: 'warn' }, { name: 'Report Generator', stage: 'Service', health: 'ok' }]
    }
  ];

  var state = {
    view: 'list',
    selected: null,
    filters: {
      status: 'all',
      categories: [],
      impacts: [],
      severities: [],
      search: ''
    },
    open: { status: true, category: true, impact: true, severity: true },
    timeRange: 'Last 2 hours',
    showChart: true,
    tab: 'overview',
    impactTab: 'journeys',
    eventEntity: null
  };

  var CATEGORY_VALUES = ['Availability', 'Error', 'Slowdown', 'Resource contention', 'Custom alert', 'Monitoring unavailable', 'Performance'];
  var IMPACT_VALUES = ['Frontends', 'Services', 'Infrastructure', 'Synthetic monitors', 'Environment'];
  var SEVERITY_VALUES = ['Critical', 'Major', 'Minor', 'Warning', 'Informational'];
  var SEVERITY_MAP_UI = { Critical: 'Critical', High: 'Major', Medium: 'Minor', Low: 'Warning' };
  var SEVERITY_MAP_FROM_UI = { Critical: ['Critical'], Major: ['High'], Minor: ['Medium'], Warning: ['Low'], Informational: [] };
  var IMPACT_MAP_FROM_STAGE = { Storage: 'Infrastructure', Database: 'Infrastructure', Server: 'Infrastructure', Service: 'Services', Gateway: 'Services', Directory: 'Infrastructure' };

  function impactsOf(x) {
    var set = {};
    (x.path || []).forEach(function (n) { var m = IMPACT_MAP_FROM_STAGE[n.stage]; if (m) set[m] = 1; });
    if ((x.journeysDetail || []).length) set.Frontends = 1;
    return Object.keys(set);
  }

  function injectStyles() {
    if (document.getElementById('inc-styles')) return;
    var s = document.createElement('style');
    s.id = 'inc-styles';
    s.textContent = [
      /* ========== Dynatrace-parity list layout (KFH green) ========== */
      '.incx-shell{display:grid;grid-template-columns:264px 1fr;gap:12px;background:#f4f6f8;min-height:calc(100vh - 60px);font-size:12.5px;color:#242a33;padding:12px;}',
      '.incx-rail{border:1px solid #dfe3e8;background:#fff;padding:12px 12px 18px;overflow-y:auto;border-radius:10px;box-shadow:0 1px 2px rgba(15,23,42,.04);height:max-content;position:sticky;top:12px;max-height:calc(100vh - 84px);}',
      '.incx-rail-head{display:flex;align-items:center;justify-content:space-between;gap:6px;padding:2px 2px 12px;}',
      '.incx-filter-chip{display:inline-flex;align-items:center;gap:6px;padding:3px 10px 3px 12px;border-radius:14px;background:#e6f2ec;color:#00634A;font-size:12px;font-weight:600;border:1px solid #cfe5d8;}',
      '.incx-filter-chip .chk{color:#00834f;font-weight:700;}',
      '.incx-bell{width:26px;height:26px;display:inline-flex;align-items:center;justify-content:center;border-radius:6px;color:#5a6470;cursor:pointer;border:1px solid transparent;background:none;}',
      '.incx-bell:hover{background:#eef1f4;color:#242a33;}',
      '.incx-facet{border-top:1px solid #eceff3;padding:10px 0 4px;}',
      '.incx-facet:first-of-type{border-top:0;}',
      '.incx-facet-head{display:flex;align-items:center;justify-content:space-between;cursor:pointer;padding:2px 2px 6px;color:#242a33;font-size:12px;font-weight:600;user-select:none;}',
      '.incx-facet-head .chev{color:#5a6470;font-size:10px;transition:transform .12s ease;}',
      '.incx-facet.closed .chev{transform:rotate(-90deg);}',
      '.incx-facet-body{display:flex;flex-direction:column;gap:2px;padding-bottom:6px;}',
      '.incx-facet.closed .incx-facet-body{display:none;}',
      '.incx-opt{display:flex;align-items:center;gap:8px;padding:4px 4px;border-radius:5px;font-size:12.5px;color:#242a33;cursor:pointer;user-select:none;}',
      '.incx-opt:hover{background:#eef1f4;}',
      '.incx-opt input{margin:0;accent-color:#00634A;width:14px;height:14px;flex:0 0 auto;}',
      '.incx-opt-label{flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}',
      '.incx-opt-count{font-size:11px;color:#78828e;font-variant-numeric:tabular-nums;}',

      '.incx-main{display:flex;flex-direction:column;min-width:0;background:#fff;border:1px solid #dfe3e8;border-radius:10px;box-shadow:0 1px 2px rgba(15,23,42,.04);overflow:hidden;}',
      '.incx-topbar{display:flex;align-items:center;gap:10px;padding:10px 16px 8px;border-bottom:1px solid #eceff3;}',
      '.incx-icobtn{width:28px;height:28px;display:inline-flex;align-items:center;justify-content:center;border-radius:6px;background:#fff;border:1px solid #dfe3e8;color:#242a33;cursor:pointer;font-size:14px;}',
      '.incx-icobtn:hover{background:#f4f6f8;}',
      '.incx-title{font-size:13.5px;font-weight:600;color:#242a33;display:inline-flex;align-items:center;gap:6px;}',
      '.incx-badge-active{display:inline-flex;align-items:center;gap:5px;padding:1px 8px;border-radius:10px;background:#fbeaea;color:#c11e23;font-size:11.5px;font-weight:600;}',
      '.incx-badge-active .dot{width:6px;height:6px;border-radius:50%;background:#e0353b;display:inline-block;}',
      '.incx-title-total{color:#78828e;font-weight:500;font-size:12.5px;}',
      '.incx-topbar-spacer{flex:1;}',
      '.incx-linkbtn{display:inline-flex;align-items:center;gap:6px;background:none;border:0;color:#242a33;font-size:12.5px;cursor:pointer;padding:4px 6px;border-radius:5px;}',
      '.incx-linkbtn:hover{background:#eef1f4;}',
      '.incx-linkbtn .ico{color:#5a6470;}',
      '.incx-topbar-meta{font-size:12px;color:#78828e;}',

      '.incx-toolbar{display:flex;align-items:center;gap:8px;padding:10px 16px;border-bottom:1px solid #eceff3;background:#fff;}',
      '.incx-entity-picker{width:34px;height:32px;border:1px solid #dfe3e8;border-radius:6px;display:inline-flex;align-items:center;justify-content:center;color:#5a6470;background:#fff;cursor:pointer;}',
      '.incx-entity-picker:hover{background:#f4f6f8;}',
      '.incx-search{flex:1;min-width:180px;position:relative;}',
      '.incx-search input{width:100%;height:32px;padding:0 10px 0 32px;border:1px solid #dfe3e8;border-radius:6px;background:#fff;font-size:12.5px;color:#242a33;outline:none;font-family:inherit;}',
      '.incx-search input:focus{border-color:#00634A;box-shadow:0 0 0 2px rgba(0,99,74,.14);}',
      '.incx-search svg{position:absolute;left:10px;top:50%;transform:translateY(-50%);width:14px;height:14px;color:#78828e;}',
      '.incx-time{height:32px;padding:0 10px;border:1px solid #dfe3e8;border-radius:6px;background:#fff;font-size:12.5px;color:#242a33;cursor:pointer;font-family:inherit;outline:none;}',
      '.incx-tbtn{width:32px;height:32px;border:1px solid #dfe3e8;border-radius:6px;background:#fff;color:#5a6470;cursor:pointer;display:inline-flex;align-items:center;justify-content:center;}',
      '.incx-tbtn:hover{background:#f4f6f8;color:#242a33;}',
      '.incx-refresh{height:32px;padding:0 12px;border:1px solid #dfe3e8;border-radius:6px;background:#fff;font-size:12.5px;color:#242a33;cursor:pointer;display:inline-flex;align-items:center;gap:6px;font-family:inherit;}',
      '.incx-refresh:hover{background:#f4f6f8;}',

      '.incx-body{padding:14px 16px 20px;overflow-y:auto;}',
      '.incx-chart{background:#fff;border:1px solid #eceff3;border-radius:6px;padding:8px 12px 4px;}',
      '.incx-chart svg{display:block;width:100%;height:180px;}',

      '.incx-tablewrap{margin-top:14px;background:#fff;border:1px solid #eceff3;border-radius:6px;overflow-x:auto;}',
      '.incx-tablehead-note{display:flex;align-items:center;justify-content:flex-end;gap:12px;padding:6px 8px;font-size:11.5px;color:#78828e;}',
      '.incx-table{width:100%;border-collapse:collapse;font-size:12.5px;color:#242a33;}',
      '.incx-table thead th{position:sticky;top:0;background:#fff;text-align:left;font-weight:600;color:#5a6470;font-size:11.5px;padding:8px 10px;border-bottom:1px solid #eceff3;white-space:nowrap;}',
      '.incx-table tbody td{padding:9px 10px;border-bottom:1px solid #f2f4f7;white-space:nowrap;vertical-align:middle;}',
      '.incx-table tbody tr{cursor:pointer;}',
      '.incx-table tbody tr:hover td{background:#f6f9f7;}',
      '.incx-table tbody tr.crit td:first-child{border-left:3px solid #e0353b;padding-left:8px;}',
      '.incx-cellid{color:#242a33;font-family:ui-monospace,Menlo,Consolas,monospace;font-size:12px;}',
      '.incx-cellname{color:#242a33;font-weight:500;}',
      '.incx-pill-status{display:inline-flex;align-items:center;gap:5px;padding:1px 8px;border-radius:10px;font-size:11.5px;font-weight:600;}',
      '.incx-pill-status.open{background:#fbeaea;color:#c11e23;}',
      '.incx-pill-status.open .d{width:6px;height:6px;border-radius:50%;background:#e0353b;}',
      '.incx-pill-status.closed{background:#eef1f4;color:#5a6470;}',
      '.incx-pill-cat{display:inline-flex;align-items:center;gap:5px;color:#242a33;font-size:12px;}',
      '.incx-pill-cat .ci{width:14px;height:14px;border-radius:50%;display:inline-flex;align-items:center;justify-content:center;font-size:9px;color:#fff;}',
      '.incx-pill-cat .ci.availability{background:#e0353b;}',
      '.incx-pill-cat .ci.error{background:#e0353b;}',
      '.incx-pill-cat .ci.performance{background:#c78900;}',
      '.incx-pill-cat .ci.resource{background:#7a5cc4;}',
      '.incx-pill-cat .ci.custom{background:#5a6470;}',
      '.incx-pill-affected{display:inline-flex;align-items:center;gap:6px;padding:2px 8px;border-radius:4px;background:#eef4f0;color:#00634A;font-size:12px;font-weight:500;}',
      '.incx-pill-affected .ico{color:#00634A;}',
      '.incx-pill-more{display:inline-flex;align-items:center;padding:2px 6px;border-radius:4px;background:#eef1f4;color:#5a6470;font-size:11.5px;margin-left:4px;}',
      '.incx-cellmuted{color:#78828e;}',
      '.incx-cellchk{width:26px;text-align:center;padding-left:6px;padding-right:0;}',
      '.incx-cellchk input{width:14px;height:14px;margin:0;accent-color:#00634A;}',
      '.incx-empty{padding:36px;text-align:center;color:#78828e;font-size:13px;}',

      /* ========== Original .inc-* styles (retained for detail view) ========== */
      '.inc-timeline{display:flex;align-items:flex-end;gap:3px;height:70px;padding:12px 0 0;}',
      '.inc-timeline .b{flex:1;border-radius:2px 2px 0 0;background:var(--surface-border);}',
      '.inc-pill{display:inline-flex;align-items:center;gap:4px;height:20px;padding:0 8px;border-radius:999px;background:var(--surface-off-white);border:1px solid var(--surface-border);font-size:.7rem;font-weight:600;color:var(--text-secondary);margin:1px 2px 1px 0;}',
      '.inc-path{display:flex;align-items:center;gap:2px;flex-wrap:wrap;}',
      '.inc-pnode{border:1px solid var(--surface-border);border-radius:9px;padding:8px 12px;background:var(--surface-off-white);min-width:120px;}',
      '.inc-pnode.crit{border-color:#fecaca;background:#fef2f2;}.inc-pnode.warn{border-color:#fde68a;background:#fffbeb;}',
      '.inc-pnode .st{font-size:.6rem;text-transform:uppercase;letter-spacing:.05em;color:var(--text-muted);font-weight:700;}',
      '.inc-pnode .nm{font-size:.82rem;font-weight:700;color:var(--text-primary);}',
      '.inc-arrow{color:#ef4444;font-weight:700;padding:0 2px;}',
      '.inc-tabs{display:flex;gap:4px;border-bottom:1px solid var(--surface-border);margin:14px 0;}',
      '.inc-tab{padding:8px 14px;font-size:.85rem;font-weight:600;color:var(--text-secondary);cursor:pointer;border-bottom:2px solid transparent;}',
      '.inc-tab.on{color:var(--kfh-primary);border-bottom-color:var(--kfh-primary);}',
      '.inc-ev{display:flex;align-items:flex-start;gap:8px;padding:8px 0;border-bottom:1px solid var(--surface-border-subtle);}',
      '.inc-ev:last-child{border-bottom:none;}',
      '.inc-subtabs{display:flex;gap:6px;flex-wrap:wrap;}',
      '.inc-subtab{padding:5px 10px;border-radius:999px;border:1px solid var(--surface-border);font-size:.76rem;font-weight:600;color:var(--text-secondary);cursor:pointer;}',
      '.inc-subtab.on{background:var(--kfh-primary);color:#fff;border-color:var(--kfh-primary);}',
      '.inc-subn{font-weight:800;opacity:.85;}',
      '.inc-impact-row{display:flex;justify-content:space-between;align-items:center;padding:8px 0;border-bottom:1px solid var(--surface-border-subtle);}',
      '.inc-erow{cursor:pointer;}.inc-erow.on td{background:var(--kfh-primary-light);}',

      /* Responsive: collapse rail on narrow screens */
      '@media (max-width: 960px){.incx-shell{grid-template-columns:1fr;}.incx-rail{display:none;}}'
    ].join('');
    document.head.appendChild(s);
  }

  // ---- Filter matching ----
  function passes(x) {
    var f = state.filters;
    if (f.status === 'active' && x.status !== 'Open') return false;
    if (f.status === 'closed' && x.status !== 'Closed') return false;
    if (f.categories.length && f.categories.indexOf(x.category) < 0) return false;
    if (f.severities.length) {
      var mapped = SEVERITY_MAP_UI[x.severity] || x.severity;
      if (f.severities.indexOf(mapped) < 0) return false;
    }
    if (f.impacts.length) {
      var imp = impactsOf(x);
      var any = f.impacts.some(function (v) { return imp.indexOf(v) >= 0; });
      if (!any) return false;
    }
    if (f.search) {
      var q = f.search.toLowerCase();
      var hay = (x.id + ' ' + x.title + ' ' + x.category + ' ' + x.severity + ' ' + x.status + ' ' + (x.rootCause && x.rootCause.id ? x.rootCause.id : '') + ' ' + (x.apps || []).join(' ')).toLowerCase();
      if (hay.indexOf(q) < 0) return false;
    }
    return true;
  }

  // Count how many incidents pass all OTHER active filters (used for per-option counts)
  function countWith(overrides) {
    var saved = JSON.parse(JSON.stringify(state.filters));
    Object.keys(overrides).forEach(function (k) { state.filters[k] = overrides[k]; });
    var n = INCIDENTS.filter(passes).length;
    state.filters = saved;
    return n;
  }

  // ---- Filter rail ----
  function chevronSvg() {
    return '<svg class="chev" viewBox="0 0 12 12" width="10" height="10" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 5 L6 8 L9 5"/></svg>';
  }
  function facetHead(key, label) {
    var closed = !state.open[key];
    return '<div class="incx-facet-head" data-facet-toggle="' + key + '"><span>' + esc(label) + '</span>' + chevronSvg() + '</div>';
  }
  function radioFacet(key, label, options) {
    var body = options.map(function (o) {
      var val = o.value, lbl = o.label;
      var checked = state.filters.status === val;
      return '<label class="incx-opt"><input type="radio" name="status" value="' + esc(val) + '" ' + (checked ? 'checked' : '') + ' data-radio-status><span class="incx-opt-label">' + esc(lbl) + '</span></label>';
    }).join('');
    return '<div class="incx-facet ' + (state.open[key] ? '' : 'closed') + '">' + facetHead(key, label) + '<div class="incx-facet-body">' + body + '</div></div>';
  }
  function checkboxFacet(key, filterKey, label, options) {
    var body = options.map(function (v) {
      var checked = state.filters[filterKey].indexOf(v) >= 0;
      var overrides = {};
      overrides[filterKey] = checked ? state.filters[filterKey] : state.filters[filterKey].concat(v);
      var count = countWith(overrides);
      return '<label class="incx-opt"><input type="checkbox" value="' + esc(v) + '" ' + (checked ? 'checked' : '') + ' data-cb="' + esc(filterKey) + '"><span class="incx-opt-label">' + esc(v) + '</span><span class="incx-opt-count">' + count + '</span></label>';
    }).join('');
    return '<div class="incx-facet ' + (state.open[key] ? '' : 'closed') + '">' + facetHead(key, label) + '<div class="incx-facet-body">' + body + '</div></div>';
  }

  function railHtml() {
    return '<aside class="incx-rail">' +
      '<div class="incx-rail-head">' +
        '<span class="incx-filter-chip">Default filter <span class="chk">✓</span></span>' +
        '<button class="incx-bell" title="Notification settings" aria-label="Notification settings">' +
          '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.7 21a2 2 0 0 1-3.4 0"/></svg>' +
        '</button>' +
      '</div>' +
      radioFacet('status', 'Status', [
        { value: 'all', label: 'All' },
        { value: 'active', label: 'Active' },
        { value: 'closed', label: 'Closed' }
      ]) +
      checkboxFacet('category', 'categories', 'Category', CATEGORY_VALUES) +
      checkboxFacet('impact', 'impacts', 'Impact', IMPACT_VALUES) +
      checkboxFacet('severity', 'severities', 'Severity', SEVERITY_VALUES) +
    '</aside>';
  }

  // ---- Header + toolbar + chart ----
  function chartSvg(list) {
    // Stack bars over ~24 buckets; height proportional to incidents active during that bucket.
    var buckets = 24;
    var maxRows = Math.max(4, list.length + 2);
    var w = 1200, h = 180, pad = 24, colW = (w - pad * 2) / buckets;
    var bars = '';
    for (var i = 0; i < buckets; i++) {
      // Density curve: mostly full, slight tail-off on the last bucket
      var count = i === buckets - 1 ? Math.max(1, list.length - 2) : list.length;
      var bh = (count / maxRows) * (h - 40);
      var x = pad + i * colW;
      var y = h - 20 - bh;
      bars += '<rect x="' + (x + 1) + '" y="' + y + '" width="' + (colW - 2) + '" height="' + bh + '" fill="#e0353b" opacity="0.78"/>';
    }
    // baseline
    bars += '<line x1="' + pad + '" y1="' + (h - 20) + '" x2="' + (w - pad) + '" y2="' + (h - 20) + '" stroke="#e6e8ec" stroke-width="1"/>';
    // simple x-axis ticks
    var ticks = '';
    var labels = ['05:45', '06:00', '06:15', '06:30', '06:45', '07:00', '07:15', '07:30'];
    for (var t = 0; t < labels.length; t++) {
      var tx = pad + (t / (labels.length - 1)) * (w - pad * 2);
      ticks += '<text x="' + tx + '" y="' + (h - 4) + '" font-size="10" fill="#78828e" text-anchor="middle" font-family="inherit">' + labels[t] + '</text>';
    }
    return '<svg viewBox="0 0 ' + w + ' ' + h + '" preserveAspectRatio="none">' + bars + ticks + '</svg>';
  }

  function catIconClass(cat) {
    var c = String(cat || '').toLowerCase();
    if (c.indexOf('avail') >= 0) return 'availability';
    if (c.indexOf('error') >= 0) return 'error';
    if (c.indexOf('perf') >= 0 || c.indexOf('slow') >= 0) return 'performance';
    if (c.indexOf('resource') >= 0) return 'resource';
    return 'custom';
  }
  function catIconGlyph(cat) {
    var c = String(cat || '').toLowerCase();
    if (c.indexOf('avail') >= 0) return '!';
    if (c.indexOf('error') >= 0) return '✕';
    if (c.indexOf('perf') >= 0 || c.indexOf('slow') >= 0) return '~';
    if (c.indexOf('resource') >= 0) return '≡';
    return '•';
  }

  function affectedEntitiesCell(x) {
    var apps = (x.apps || []).slice(0, 1).map(function (a) {
      return '<span class="incx-pill-affected"><span class="ico">◈</span>' + esc(a) + '</span>';
    }).join('');
    var extra = (x.apps && x.apps.length > 1) ? '<span class="incx-pill-more">+ ' + (x.apps.length - 1) + '</span>' : '';
    return apps + extra;
  }

  function rowsHtml(list) {
    if (!list.length) {
      return '<tr><td colspan="10" class="incx-empty">No incidents match the current filters.</td></tr>';
    }
    return list.map(function (x) {
      var cls = x.status === 'Open' ? 'crit' : '';
      var status = x.status === 'Open'
        ? '<span class="incx-pill-status open"><span class="d"></span>Active</span>'
        : '<span class="incx-pill-status closed">Closed</span>';
      var catIco = '<span class="incx-pill-cat"><span class="ci ' + catIconClass(x.category) + '">' + catIconGlyph(x.category) + '</span>' + esc(x.category) + '</span>';
      return '<tr class="' + cls + '" data-id="' + esc(x.id) + '">' +
        '<td class="incx-cellchk" onclick="event.stopPropagation()"><input type="checkbox" aria-label="Select ' + esc(x.id) + '"></td>' +
        '<td class="incx-cellid">' + esc(x.id) + '</td>' +
        '<td class="incx-cellname">' + esc(x.title) + '</td>' +
        '<td>' + status + '</td>' +
        '<td>' + catIco + '</td>' +
        '<td class="incx-cellmuted">' + (x.apps ? x.apps.length : 0) + '</td>' +
        '<td>' + affectedEntitiesCell(x) + '</td>' +
        '<td>' + (x.rootCause && x.rootCause.id ? '<span class="incx-pill-affected"><span class="ico">◈</span>' + esc(x.rootCause.id) + '</span>' : '<span class="incx-cellmuted">—</span>') + '</td>' +
        '<td class="incx-cellmuted">' + esc(x.started) + '</td>' +
        '<td>' + esc(x.duration) + '</td>' +
        '</tr>';
    }).join('');
  }

  function toolbarHtml() {
    return '<div class="incx-toolbar">' +
      '<button class="incx-entity-picker" title="Entity picker" aria-label="Entity picker">' +
        '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M3 7l9-4 9 4-9 4-9-4z"/><path d="M3 12l9 4 9-4"/><path d="M3 17l9 4 9-4"/></svg>' +
      '</button>' +
      '<div class="incx-search">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="M21 21l-4.35-4.35"/></svg>' +
        '<input id="incx-search-input" type="text" placeholder="Type to filter" value="' + esc(state.filters.search) + '" autocomplete="off">' +
      '</div>' +
      '<select class="incx-time" id="incx-time-select" aria-label="Time range">' +
        ['Last 30 minutes', 'Last 2 hours', 'Last 24 hours', 'Last 7 days', 'Last 15 days'].map(function (t) {
          return '<option value="' + esc(t) + '" ' + (t === state.timeRange ? 'selected' : '') + '>' + esc(t) + '</option>';
        }).join('') +
      '</select>' +
      '<button class="incx-tbtn" title="Previous" aria-label="Previous">‹</button>' +
      '<button class="incx-tbtn" title="Next" aria-label="Next">›</button>' +
      '<button class="incx-refresh" id="incx-refresh" title="Refresh">' +
        '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M23 4v6h-6"/><path d="M20.49 15A9 9 0 1 1 5.64 5.64L23 10"/></svg>' +
        '<span>Refresh</span>' +
      '</button>' +
      '<button class="incx-tbtn" title="More" aria-label="More">⌄</button>' +
    '</div>';
  }

  function headerHtml(list, active, total) {
    return '<div class="incx-topbar">' +
      '<button class="incx-icobtn" title="Collapse filter rail" aria-label="Collapse filter rail">' +
        '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="16" rx="2"/><line x1="9" y1="4" x2="9" y2="20"/></svg>' +
      '</button>' +
      '<span class="incx-title">Incidents <span class="incx-badge-active"><span class="dot"></span>' + active + ' active</span><span class="incx-title-total">/ ' + total + '</span></span>' +
      '<span class="incx-topbar-spacer"></span>' +
      '<button class="incx-linkbtn" id="incx-toggle-chart">' +
        '<svg class="ico" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.9"><rect x="3" y="10" width="4" height="10"/><rect x="10" y="4" width="4" height="16"/><rect x="17" y="14" width="4" height="6"/></svg>' +
        (state.showChart ? 'Hide chart' : 'Show chart') +
      '</button>' +
      '<span class="incx-topbar-meta">⟳ refreshed 20 sec. ago</span>' +
    '</div>';
  }

  function renderList() {
    var list = INCIDENTS.filter(passes);
    var active = INCIDENTS.filter(function (x) { return x.status === 'Open'; }).length;
    var chartHtml = state.showChart ? '<div class="incx-chart">' + chartSvg(list) + '</div>' : '';

    mount.innerHTML =
      '<div class="incx-shell">' +
        railHtml() +
        '<section class="incx-main">' +
          headerHtml(list, active, INCIDENTS.length) +
          toolbarHtml() +
          '<div class="incx-body">' +
            chartHtml +
            '<div class="incx-tablewrap">' +
              '<div class="incx-tablehead-note">7 columns hidden <span>⬇</span></div>' +
              '<table class="incx-table"><thead><tr>' +
                '<th class="incx-cellchk"><input type="checkbox" aria-label="Select all"></th>' +
                '<th>ID</th>' +
                '<th>Name</th>' +
                '<th>Status</th>' +
                '<th>Category</th>' +
                '<th>Affected</th>' +
                '<th>Affected entities</th>' +
                '<th>Root cause</th>' +
                '<th>Started</th>' +
                '<th>Duration</th>' +
              '</tr></thead><tbody>' + rowsHtml(list) + '</tbody></table>' +
            '</div>' +
          '</div>' +
        '</section>' +
      '</div>';

    // Facet section toggles
    mount.querySelectorAll('[data-facet-toggle]').forEach(function (el) {
      el.addEventListener('click', function () {
        var k = el.getAttribute('data-facet-toggle');
        state.open[k] = !state.open[k];
        renderList();
      });
    });
    // Status radios
    mount.querySelectorAll('[data-radio-status]').forEach(function (r) {
      r.addEventListener('change', function () {
        state.filters.status = r.value;
        renderList();
      });
    });
    // Category / impact / severity checkboxes
    mount.querySelectorAll('[data-cb]').forEach(function (c) {
      c.addEventListener('change', function () {
        var key = c.getAttribute('data-cb');
        var v = c.value;
        var arr = state.filters[key];
        var idx = arr.indexOf(v);
        if (c.checked && idx < 0) arr.push(v);
        else if (!c.checked && idx >= 0) arr.splice(idx, 1);
        renderList();
      });
    });
    // Search — debounced re-render
    var search = mount.querySelector('#incx-search-input');
    if (search) {
      var timer = null;
      search.addEventListener('input', function () {
        clearTimeout(timer);
        var v = search.value;
        timer = setTimeout(function () {
          state.filters.search = v;
          renderList();
          var f = mount.querySelector('#incx-search-input');
          if (f) { f.focus(); f.setSelectionRange(v.length, v.length); }
        }, 180);
      });
    }
    // Time range
    var time = mount.querySelector('#incx-time-select');
    if (time) time.addEventListener('change', function () { state.timeRange = time.value; });
    // Refresh
    var refresh = mount.querySelector('#incx-refresh');
    if (refresh) refresh.addEventListener('click', function () { renderList(); });
    // Hide/Show chart
    var tc = mount.querySelector('#incx-toggle-chart');
    if (tc) tc.addEventListener('click', function () { state.showChart = !state.showChart; renderList(); });
    // Row click → detail
    mount.querySelectorAll('.incx-table tbody tr[data-id]').forEach(function (r) {
      r.addEventListener('click', function () {
        state.selected = r.getAttribute('data-id');
        state.tab = 'overview';
        render();
      });
    });
  }

  // ---- Detail view (retained from prior implementation) ----
  function tile(label, value) {
    return '<div class="kfhx-tile"><div class="kfhx-tile-label">' + esc(label) + '</div><div class="kfhx-tile-value" style="font-size:1.5rem;">' + esc(value) + '</div></div>';
  }

  function evidenceRow(e) {
    return '<div class="inc-ev">' +
      '<span class="kfhx-dot ' + (e.sev === 'crit' ? 'crit' : 'warn') + '" style="margin-top:5px;"></span>' +
      '<div style="flex:1;"><div style="font-size:.82rem;font-weight:600;color:var(--text-primary);">' + esc(e.title) + '</div>' +
      '<div style="font-size:.72rem;color:var(--text-muted);">' + esc(e.ref) + ' · ' + esc(e.src) + ' · ' + esc(e.time) + '</div></div>' +
      '<a href="#explorer" data-page="explorer" class="kfhx-section-sub" style="color:var(--kfh-primary);font-weight:700;text-decoration:none;">View →</a>' +
      '</div>';
  }

  function pathHtml(path) {
    return path.map(function (n) {
      return '<div class="inc-pnode ' + (n.health === 'crit' ? 'crit' : n.health === 'warn' ? 'warn' : '') + '"><div class="st">' + esc(n.stage) + '</div><div class="nm">' + esc(n.name) + '</div></div>';
    }).join('<span class="inc-arrow">→</span>');
  }

  function affectedEntities(x) {
    var ents = x.path.map(function (n) {
      return { name: n.name, type: n.stage, health: n.health, root: n.name === x.rootCause.id,
        events: n.name === x.rootCause.id ? x.rootCause.evidence.length : 1 };
    });
    x.journeysDetail.forEach(function (j) { ents.push({ name: j.name, type: 'Journey', health: 'crit', events: 1 }); });
    return ents;
  }

  function problemChart() {
    var pts = [8, 6, 7, 5, 40, 55, 48, 62, 51, 70, 58, 66, 60, 44, 20, 8];
    var w = 320, h = 130, n = pts.length, max = 90, step = (w - 20) / (n - 1);
    var bandX = 10 + 4 * step, bandW = 9 * step;
    var line = pts.map(function (v, i) { return (i ? 'L' : 'M') + Math.round(10 + i * step) + ',' + Math.round(h - 14 - v / max * (h - 28)); }).join(' ');
    return '<svg viewBox="0 0 ' + w + ' ' + h + '" style="width:100%;height:130px;">' +
      '<rect x="' + bandX + '" y="6" width="' + bandW + '" height="' + (h - 12) + '" fill="#ef4444" opacity="0.07"/>' +
      '<rect x="' + bandX + '" y="4" width="' + bandW + '" height="4" rx="2" fill="#ef4444" opacity="0.85"/>' +
      '<path d="' + line + '" fill="none" stroke="#2563eb" stroke-width="2"/>' +
      '</svg>';
  }

  function impactPanel(x) {
    var subs = [['journeys', 'Journeys', x.journeysDetail.length], ['applications', 'Applications', x.apps.length], ['services', 'Services', x.services], ['assets', 'Assets', x.assets]];
    var tabsHtml = subs.map(function (s) {
      return '<div class="inc-subtab ' + (state.impactTab === s[0] ? 'on' : '') + '" data-subtab="' + s[0] + '">' + esc(s[1]) + ' <span class="inc-subn">' + s[2] + '</span></div>';
    }).join('');
    var body;
    if (state.impactTab === 'applications') {
      body = x.apps.map(function (a) { return '<div class="inc-impact-row"><span style="font-weight:600;font-size:.85rem;color:var(--text-primary);">' + esc(a) + '</span><span class="kfhx-badge crit">impacted</span></div>'; }).join('');
    } else if (state.impactTab === 'services') {
      body = x.path.filter(function (n) { return n.stage === 'Service' || n.stage === 'Gateway'; }).map(function (n) {
        return '<div class="inc-impact-row"><span class="mono" style="font-size:.82rem;">' + esc(n.name) + '</span><span class="kfhx-dot ' + (n.health === 'crit' ? 'crit' : n.health === 'warn' ? 'warn' : '') + '"></span></div>';
      }).join('') || '<div style="color:var(--text-muted);font-size:.82rem;padding:8px 0;">No impacted services.</div>';
    } else if (state.impactTab === 'assets') {
      body = x.path.map(function (n) { return '<div class="inc-impact-row"><span class="mono" style="font-size:.82rem;">' + esc(n.name) + '</span><span style="font-size:.72rem;color:var(--text-muted);display:inline-flex;align-items:center;gap:6px;">' + esc(n.stage) + ' <span class="kfhx-dot ' + (n.health === 'crit' ? 'crit' : n.health === 'warn' ? 'warn' : '') + '"></span></span></div>'; }).join('');
    } else {
      body = x.journeysDetail.map(function (j) {
        return '<div class="inc-impact-row"><span style="font-weight:600;color:var(--text-primary);font-size:.85rem;">' + esc(j.name) + '</span>' +
          '<span style="font-size:.82rem;"><span style="color:var(--text-muted);">' + j.before + '%</span> <span class="inc-arrow">→</span> <span style="color:var(--color-critical);font-weight:700;">' + j.after + '%</span></span></div>';
      }).join('') || '<div style="color:var(--text-muted);font-size:.82rem;padding:8px 0;">No customer-facing journey impact.</div>';
    }
    return '<div class="kfhx-panel"><div class="kfhx-section-title" style="margin-bottom:10px;">Impact</div>' +
      '<div class="inc-subtabs">' + tabsHtml + '</div><div style="margin-top:10px;">' + body + '</div></div>';
  }

  function eventsTab(x) {
    var ents = affectedEntities(x);
    var sel = state.eventEntity ? (ents.filter(function (e) { return e.name === state.eventEntity; })[0] || ents[0]) : ents[0];
    var rows = ents.map(function (e) {
      return '<tr class="inc-erow ' + (sel && sel.name === e.name ? 'on' : '') + '" data-ent="' + esc(e.name) + '">' +
        '<td>' + esc(e.name) + (e.root ? ' <span class="kfhx-badge crit">Root cause</span>' : '') + '</td>' +
        '<td style="color:var(--text-muted);">' + esc(e.type) + '</td>' +
        '<td style="text-align:right;font-weight:700;">' + e.events + '</td></tr>';
    }).join('');
    var evHtml = sel && sel.root ? x.rootCause.evidence.map(evidenceRow).join('')
      : '<div class="inc-ev"><span class="kfhx-dot warn" style="margin-top:5px;"></span><div style="flex:1;"><div style="font-size:.82rem;font-weight:600;color:var(--text-primary);">Symptom on ' + esc(sel ? sel.name : '') + '</div><div style="font-size:.72rem;color:var(--text-muted);">Correlated to the incident window (downstream of the root cause).</div></div></div>';
    var detail = sel ? '<div class="kfhx-panel">' +
      '<div style="display:flex;align-items:center;gap:8px;"><span class="kfhx-entity-title">' + esc(sel.name) + '</span>' + (sel.root ? '<span class="kfhx-badge crit">Root cause</span>' : '') + '</div>' +
      '<div class="kfhx-section-sub">' + esc(sel.type) + ' · ' + sel.events + ' event' + (sel.events === 1 ? '' : 's') + '</div>' +
      '<div style="margin:12px 0 4px;font-size:.7rem;font-weight:700;text-transform:uppercase;letter-spacing:.05em;color:var(--text-muted);">Failure rate increase</div>' +
      problemChart() + '<div style="margin-top:10px;">' + evHtml + '</div></div>' : '';
    return '<div class="kfhx-split" style="grid-template-columns:1fr 380px;">' +
      '<div class="kfhx-panel" style="padding:0;overflow:hidden;"><div style="padding:12px 14px;font-weight:700;font-size:.9rem;">Affected entities</div>' +
      '<table class="kfhx-table"><thead><tr><th>Name</th><th>Type</th><th style="text-align:right;">Events</th></tr></thead><tbody>' + rows + '</tbody></table></div>' +
      detail + '</div>';
  }

  function changesTab(x) {
    var svc = x.path.filter(function (n) { return n.stage === 'Service'; }).map(function (n) { return n.name; })[0] || 'Service';
    var cards = [
      { t: 'Config change', name: x.rootCause.id, time: 'shortly before onset', desc: 'Infrastructure change on ' + x.rootCause.id + ' immediately before the incident window — a likely trigger; verify against the change record.' },
      { t: 'Deployment', name: svc, time: 'in window', desc: 'No application deployment recorded for ' + svc + ' during the window; change correlation points infrastructure-side.' }
    ];
    return cards.map(function (c) {
      return '<div class="kfhx-panel" style="margin-bottom:10px;"><div style="display:flex;justify-content:space-between;align-items:center;">' +
        '<div><span class="kfhx-badge info">' + esc(c.t) + '</span> <span class="mono" style="font-weight:700;">' + esc(c.name) + '</span></div>' +
        '<span style="font-size:.72rem;color:var(--text-muted);">' + esc(c.time) + '</span></div>' +
        '<div style="font-size:.82rem;color:var(--text-secondary);margin-top:6px;">' + esc(c.desc) + '</div></div>';
    }).join('') + '<div style="font-size:.72rem;color:var(--text-muted);padding:4px;">Live deployment/config change correlation arrives with CHANGES ingestion (Phase 7).</div>';
  }

  function renderDetail(x) {
    mount.innerHTML =
      '<div class="kfhx-page">' +
        '<a id="inc-back" href="#incidents" data-page="incidents" style="font-size:.82rem;color:var(--kfh-primary);font-weight:700;text-decoration:none;">← All incidents</a>' +
        '<div class="kfhx-section-head" style="margin-top:8px;">' +
          '<div>' +
            '<div class="kfhx-section-title" style="font-size:1.25rem;">' + esc(x.title) + '</div>' +
            '<div class="kfhx-section-sub" style="margin-top:4px;">' +
              (x.status === 'Open' ? '<span class="kfhx-badge crit"><span class="kfhx-dot crit"></span>Open</span>' : '<span class="kfhx-badge ok">Closed</span>') +
              ' <span class="mono" style="color:var(--text-muted);">' + esc(x.id) + '</span> · <span class="kfh-chip kfh-chip-' + x.severity.toLowerCase() + '">' + esc(x.severity) + '</span> · ' + esc(x.category) + ' · Started ' + esc(x.started) + ' for ' + esc(x.duration) +
            '</div>' +
          '</div>' +
          '<div style="display:flex;gap:8px;">' +
            '<button class="kfhx-iconbtn" style="width:auto;padding:0 12px;gap:6px;font-size:.8rem;font-weight:600;">✦ Explain</button>' +
            '<a href="#servicemap" data-page="servicemap" class="kfhx-iconbtn" style="width:auto;padding:0 12px;gap:6px;font-size:.8rem;font-weight:600;text-decoration:none;">Topology graph</a>' +
            '<button class="kfhx-iconbtn" style="width:auto;padding:0 12px;font-size:.8rem;font-weight:600;">Notify</button>' +
          '</div>' +
        '</div>' +

        '<div class="kfhx-tiles" style="grid-template-columns:repeat(auto-fit,minmax(150px,1fr));">' +
          tile('Applications', x.apps.length) + tile('Services', x.services) + tile('Affected assets', x.assets) +
          tile('Journeys', x.journeys) + tile('Alerts', x.alerts) + tile('Sources', x.sources.join(' · ')) +
        '</div>' +

        '<div class="inc-tabs">' +
          ['overview', 'changes', 'events', 'logs', 'troubleshooting'].map(function (t) {
            return '<div class="inc-tab ' + (state.tab === t ? 'on' : '') + '" data-tab="' + t + '">' + t.charAt(0).toUpperCase() + t.slice(1) + '</div>';
          }).join('') +
        '</div>' +

        (state.tab === 'overview'
          ? '<div class="kfhx-split">' +
              impactPanel(x) +
              '<div class="kfhx-panel">' +
                '<div class="kfhx-section-title" style="margin-bottom:10px;">Root cause</div>' +
                '<div style="display:flex;align-items:center;gap:8px;margin-bottom:2px;"><span class="kfhx-entity-title">◈ ' + esc(x.rootCause.id) + '</span><span class="kfhx-badge crit">Root cause</span></div>' +
                '<div class="kfhx-section-sub">' + esc(x.rootCause.type) + ' · confidence ' + x.rootCause.confidence + '</div>' +
                '<div style="margin:12px 0;">' + x.rootCause.evidence.map(evidenceRow).join('') + '</div>' +
                '<div style="padding:12px;background:var(--surface-off-white);border-radius:10px;border-left:3px solid var(--kfh-primary);">' +
                  '<div style="font-size:.7rem;font-weight:700;text-transform:uppercase;letter-spacing:.05em;color:var(--kfh-primary-dark);margin-bottom:4px;">✦ AI root-cause narrative</div>' +
                  '<div style="font-size:.84rem;line-height:1.55;color:var(--text-secondary);">' + esc(x.ai) + '</div>' +
                  '<div style="font-size:.8rem;margin-top:8px;color:var(--text-primary);"><b>Recommended:</b> ' + esc(x.recommended) + '</div>' +
                '</div>' +
              '</div>' +
            '</div>' +
            '<div class="kfhx-panel" style="margin-top:14px;">' +
              '<div class="kfhx-section-head"><div class="kfhx-section-title">Visual resolution path</div>' +
              '<a href="#servicemap" data-page="servicemap" class="kfhx-section-sub" style="color:var(--kfh-primary);font-weight:700;text-decoration:none;">Open topology →</a></div>' +
              '<div class="inc-path">' + pathHtml(x.path) + '</div>' +
              '<div style="font-size:.72rem;color:var(--text-muted);margin-top:10px;">Most-upstream failing node that went bad first and whose downstream covers all symptoms.</div>' +
            '</div>'
          : state.tab === 'events' ? eventsTab(x)
          : state.tab === 'changes' ? changesTab(x)
          : '<div class="kfhx-panel">' + emptyTab(x, state.tab) + '</div>') +
      '</div>';

    mount.querySelectorAll('.inc-tab').forEach(function (t) {
      t.addEventListener('click', function () { state.tab = t.getAttribute('data-tab'); render(); });
    });
    mount.querySelectorAll('[data-subtab]').forEach(function (s) {
      s.addEventListener('click', function () { state.impactTab = s.getAttribute('data-subtab'); render(); });
    });
    mount.querySelectorAll('[data-ent]').forEach(function (r) {
      r.addEventListener('click', function () { state.eventEntity = r.getAttribute('data-ent'); render(); });
    });
  }

  function emptyTab(x, tab) {
    var map = {
      changes: 'Deployment & configuration changes correlated to this window appear here (CHANGES ingestion, Phase 7).',
      events: 'All normalized alerts/events that formed this incident, with properties — wired to the Custom Index (Phase 3).',
      logs: 'Related logs from the impacted assets, filtered to the incident window (Phase 7).',
      troubleshooting: 'Runbooks, similar past incidents, and recommended actions from the knowledge base (Phase 4).'
    };
    return '<div style="padding:24px;text-align:center;color:var(--text-muted);font-size:.85rem;">' + (map[tab] || '') + '</div>';
  }

  function render() {
    injectStyles();
    if (state.selected) {
      var x = INCIDENTS.filter(function (i) { return i.id === state.selected; })[0];
      if (x) { renderDetail(x); return; }
    }
    state.selected = null;
    renderList();
  }

  mount.addEventListener('click', function (e) {
    var back = e.target.closest && e.target.closest('#inc-back');
    if (back) { e.preventDefault(); e.stopPropagation(); state.selected = null; render(); }
  }, true);

  render();
})();
