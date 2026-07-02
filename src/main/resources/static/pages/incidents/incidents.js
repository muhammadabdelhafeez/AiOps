/**
 * KFH AIOps Command Center — Incidents (Problems)
 * enterprise-parity list view in KFH "Beyond Horizons" colors.
 *
 * List view  : dense faceted filter rail (flush with content) + toolbar
 *              (Type-to-filter, time range, refresh) + area chart + compact
 *              incident table, matching enterprise observability Problems page density.
 * Detail view: header (status·id·severity·category·duration + Explain/Graph/Notify)
 *              → impact tile strip → tabs → Overview = Impact ↔ Root cause
 *              + Visual resolution path. Each card is a drill-down.
 *
 * Live incidents, loaded from correlation/RCA output via /api/v1/incidents.
 * Empty until the correlation + RCA + AI stages produce real incidents.
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

  // Live incidents, loaded from /api/v1/incidents. Empty until correlation produces incidents.
  var INCIDENTS = [];

  // Normalise CRITICAL/HIGH/MEDIUM/LOW/INFO (read-model) to the page's Title-case severities.
  function normSev(v) {
    switch (String(v == null ? '' : v).toUpperCase()) {
      case 'CRITICAL': return 'Critical';
      case 'HIGH': return 'High';
      case 'MEDIUM': return 'Medium';
      case 'LOW': case 'INFO': case 'INFORMATIONAL': return 'Low';
      default: return v ? String(v) : 'Low';
    }
  }

  function fmtTime(v) {
    if (!v) return '';
    var d = new Date(v);
    if (isNaN(d.getTime())) return String(v);
    return d.toLocaleString(undefined, { month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit' });
  }

  function titleCase(v) {
    var s = String(v == null ? '' : v);
    if (!s) return '';
    return s.charAt(0).toUpperCase() + s.slice(1).toLowerCase();
  }

  // Map a live incident row into the exact display shape the list + detail view consume.
  function mapIncident(r, i) {
    r = r || {};
    var rc = r.rootCause || {};
    return {
      id: r.id || r.incidentKey || r.incidentId || ('INC-' + i),
      status: titleCase(r.status) || 'Open',
      classification: r.classification || 'New',
      severity: normSev(r.severity),
      category: r.category || 'Availability',
      title: r.title || r.name || r.summary || '(untitled incident)',
      started: fmtTime(r.startedAt || r.started || r.createdAt || r.firstSeenAt || r.timestamp),
      duration: r.duration || '',
      apps: r.apps || r.impactedApplications || [],
      services: Number(r.services || 0),
      assets: Number(r.assets || 0),
      journeys: Number(r.journeys || 0),
      alerts: Number(r.alerts || r.alertCount || (Array.isArray(r.evidence) ? r.evidence.length : 0)),
      sources: r.sources || [],
      rootCause: {
        id: rc.id || r.rootCauseAssetCi || r.rootCauseComponentName || '',
        type: rc.type || r.rootCauseComponentName || '',
        confidence: Number(rc.confidence || r.confidence || 0),
        evidence: (rc.evidence || r.evidence || []).map(function (e, j) {
          e = e || {};
          var rawSev = e.sev || e.severity;
          return {
            sev: rawSev ? String(rawSev).toLowerCase().slice(0, 4) : 'info',
            src: e.src || e.sourceSystem || e.source || '',
            ref: e.ref || ('E' + (j + 1)),
            title: e.title || e.message || e.msg || '',
            time: (e.time || e.timestamp) ? fmtTime(e.time || e.timestamp) : ''
          };
        })
      },
      ai: r.ai || r.aiSummary || r.summary || '',
      recommended: r.recommended || r.recommendation || '',
      journeysDetail: r.journeysDetail || [],
      path: r.path || []
    };
  }

  var REFRESHED = '';
  function sessionCountry() {
    try { var s = (window.KFHConfig && KFHConfig.getSession && KFHConfig.getSession()) || {}; return s.countryCode || 'ALL'; } catch (e) { return 'ALL'; }
  }
  // Persist the chosen country app-wide (header country is the global scope filter now).
  function applyCountryScope(code) {
    try {
      if (!(window.KFHConfig && KFHConfig.getSession && KFHConfig.setSession)) return;
      var s = KFHConfig.getSession() || {};
      if (s.countryCode === code) return;
      s.countryCode = code;
      KFHConfig.setSession(s);
      window.dispatchEvent(new CustomEvent('kfh:scope-changed', { detail: { countryCode: code } }));
    } catch (e) { /* non-fatal */ }
  }

  var state = {
    view: 'list',
    selected: null,
    loading: true,
    error: null,
    loaded: false,
    filters: {
      status: 'all',
      categories: [],
      impacts: [],
      severities: [],
      recurrence: 'all',
      search: ''
    },
    country: sessionCountry(),
    sort: 'sev_desc',
    railOpen: true,
    searchFocus: false,
    groupsCollapsed: {},
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

  function loadIncidents() {
    if (!(window.APIClient && APIClient.incidents && APIClient.incidents.list)) {
      state.loading = false; state.loaded = true; state.error = 'Incidents API is not available.'; render(); return;
    }
    state.loading = true; state.error = null; render();
    var params = { page: 0, size: 100 };
    if (state.country && state.country !== 'ALL') params.country = state.country;
    APIClient.incidents.list(params).then(function (res) {
      var rows = (res && (res.content || res.items || res.data || res.rows)) || (Array.isArray(res) ? res : []);
      INCIDENTS = rows.map(mapIncident);
      REFRESHED = new Date().toLocaleString();
      state.loading = false; state.loaded = true; render();
    }).catch(function (err) {
      INCIDENTS = [];
      REFRESHED = new Date().toLocaleString();
      state.loading = false; state.loaded = true; state.error = (err && err.message) || 'Failed to load incidents.'; render();
    });
  }

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
      /* ========== enterprise-parity list layout (KFH green) ========== */
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
    if (f.recurrence === 'recurring' && String(x.classification || '').toLowerCase() !== 'recurring') return false;
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

  // ---- Chart ----
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
      var msg = INCIDENTS.length === 0
        ? 'No incidents yet. Incidents are produced by correlation from ingested alerts once connectors are enabled under <a href="#settings" data-page="settings">Settings → Connections</a>.'
        : 'No incidents match the current filters.';
      return '<tr><td colspan="10" class="incx-empty">' + msg + '</td></tr>';
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

  // ---- Consolidated slim header (title + search + country + filters popover + sort) ----
  var COUNTRIES = [
    { v: 'ALL', t: 'All KFH Groups' }, { v: 'KW', t: 'Kuwait' }, { v: 'BH', t: 'Bahrain' }, { v: 'EG', t: 'Egypt' }
  ];
  var SORTS = [
    { v: 'sev_desc', t: 'Severity: High to low' }, { v: 'sev_asc', t: 'Severity: Low to high' },
    { v: 'time_desc', t: 'Newest first' }, { v: 'time_asc', t: 'Oldest first' }
  ];

  function sortList(list) {
    var rank = { Critical: 4, High: 3, Medium: 2, Low: 1 };
    function started(x) { var d = new Date(x.started); return isNaN(d.getTime()) ? 0 : d.getTime(); }
    switch (state.sort) {
      case 'sev_asc': list.sort(function (a, b) { return (rank[a.severity] || 0) - (rank[b.severity] || 0); }); break;
      case 'time_desc': list.sort(function (a, b) { return started(b) - started(a); }); break;
      case 'time_asc': list.sort(function (a, b) { return started(a) - started(b); }); break;
      default: list.sort(function (a, b) { return (rank[b.severity] || 0) - (rank[a.severity] || 0); });
    }
    return list;
  }

  function activeFilterCount() {
    var f = state.filters, n = 0;
    if (f.status !== 'all') n++;
    if (f.recurrence !== 'all') n++;
    n += f.categories.length + f.severities.length;
    return n;
  }

  function header() {
    var total = INCIDENTS.length;
    var n = activeFilterCount();
    var fLabel = n ? (n + ' filter' + (n > 1 ? 's' : '')) : 'All incidents';
    var countryOpts = COUNTRIES.map(function (c) { return '<option value="' + c.v + '" ' + (state.country === c.v ? 'selected' : '') + '>' + esc(c.t) + '</option>'; }).join('');
    var sortOpts = SORTS.map(function (o) { return '<option value="' + o.v + '" ' + (state.sort === o.v ? 'selected' : '') + '>' + esc(o.t) + '</option>'; }).join('');
    var badge = state.error
      ? '<span class="kfhx-badge" style="background:var(--color-critical);color:#fff;">' + esc(state.error) + '</span>'
      : '<span class="kfhx-badge info">Live</span>';
    return '<div class="kfh-phdr">' +
      '<div class="kfh-phdr-titlewrap"><h1 class="kfh-phdr-title">Incidents</h1><span class="kfh-phdr-sub">' + total + ' incident' + (total === 1 ? '' : 's') + ' · refreshed ' + esc(REFRESHED || '—') + '</span></div>' +
      '<div class="kfh-phdr-search">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>' +
        '<input id="inc-q" type="text" placeholder="Search incidents, hosts, teams…" value="' + esc(state.filters.search) + '"></div>' +
      '<div class="kfh-phdr-ctrls">' +
        badge +
        '<div class="kfh-phdr-cty"><span class="lbl">Country</span><select id="inc-country" class="kfh-phdr-select">' + countryOpts + '</select></div>' +
      '</div>' +
    '</div>';
  }

  // One collapsible filter section (accordion) with radio rows (single-select).
  function fltRadio(id, title, opts) {
    var collapsed = !!state.groupsCollapsed[id];
    var body = opts.map(function (o) {
      return '<label class="kfh-fltopt"><input type="radio" name="inc-flt-' + id + '" ' + (o.on ? 'checked' : '') + ' data-fgrp="' + id + '" data-fval="' + esc(o.value) + '">' +
        (o.dot ? '<span class="dot" style="background:' + o.dot + ';"></span>' : '') +
        '<span>' + esc(o.label) + '</span>' + (o.n != null ? '<span class="n">' + o.n + '</span>' : '') + '</label>';
    }).join('');
    return '<div class="kfh-fltg ' + (collapsed ? 'collapsed' : '') + '">' +
      '<div class="kfh-fltg-head" data-grptoggle="' + id + '"><span class="t">' + esc(title) + '</span><span class="chev">▾</span></div>' +
      '<div class="kfh-fltg-body">' + body + '</div></div>';
  }

  // One collapsible filter section (accordion) with checkbox rows (multi-select).
  function fltCheck(id, title, opts) {
    var collapsed = !!state.groupsCollapsed[id];
    var body = opts.map(function (o) {
      return '<label class="kfh-fltopt"><input type="checkbox" ' + (o.on ? 'checked' : '') + ' data-fgrpc="' + id + '" data-fval="' + esc(o.value) + '">' +
        (o.dot ? '<span class="dot" style="background:' + o.dot + ';"></span>' : '') +
        '<span>' + esc(o.label) + '</span>' + (o.n != null ? '<span class="n">' + o.n + '</span>' : '') + '</label>';
    }).join('');
    return '<div class="kfh-fltg ' + (collapsed ? 'collapsed' : '') + '">' +
      '<div class="kfh-fltg-head" data-grptoggle="' + id + '"><span class="t">' + esc(title) + '</span><span class="chev">▾</span></div>' +
      '<div class="kfh-fltg-body">' + body + '</div></div>';
  }

  // Collapsible bordered filter panel (Dynatrace Problems-style) — accordion sections.
  function filterRail() {
    // Status (single select)
    var statusOpts = [['all', 'All'], ['active', 'Open'], ['closed', 'Closed']].map(function (o) {
      var n = o[0] === 'all' ? INCIDENTS.length
        : o[0] === 'active' ? INCIDENTS.filter(function (x) { return x.status === 'Open'; }).length
        : INCIDENTS.filter(function (x) { return x.status === 'Closed'; }).length;
      return { label: o[1], value: o[0], on: state.filters.status === o[0], n: n };
    });
    // Severity (UI vocabulary, multi-select)
    var sevOpts = SEVERITY_VALUES.map(function (v) {
      return { label: v, value: v, on: state.filters.severities.indexOf(v) >= 0,
        n: INCIDENTS.filter(function (x) { return (SEVERITY_MAP_UI[x.severity] || x.severity) === v; }).length };
    });
    // Category (multi-select)
    var catOpts = CATEGORY_VALUES.map(function (v) {
      return { label: v, value: v, on: state.filters.categories.indexOf(v) >= 0,
        n: INCIDENTS.filter(function (x) { return x.category === v; }).length };
    });
    // Recurrence (single select)
    var recOpts = [['all', 'All'], ['recurring', 'Recurring only']].map(function (o) {
      var n = o[0] === 'all' ? INCIDENTS.length
        : INCIDENTS.filter(function (x) { return String(x.classification || '').toLowerCase() === 'recurring'; }).length;
      return { label: o[1], value: o[0], on: state.filters.recurrence === o[0], n: n };
    });
    return '<aside class="kfh-filters">' +
      fltRadio('status', 'Status', statusOpts) +
      fltCheck('sev', 'Severity', sevOpts) +
      fltCheck('cat', 'Category', catOpts) +
      fltRadio('rec', 'Recurrence', recOpts) +
      '<button id="inc-filters-clear" class="kfh-filters-clear">Clear all filters</button>' +
    '</aside>';
  }

  function renderList() {
    if (state.loading && !state.loaded) {
      mount.innerHTML = '<div class="kfh-phdr"><div class="kfh-phdr-titlewrap"><h1 class="kfh-phdr-title">Incidents</h1><span class="kfh-phdr-sub">loading…</span></div></div>' +
        '<div class="kfh-worklayout"><div class="kfh-workbody"><div class="incx-empty" style="padding:48px;">Loading incidents…</div></div></div>';
      return;
    }
    var list = sortList(INCIDENTS.filter(passes));
    var chartHtml = state.showChart ? '<div class="incx-chart">' + chartSvg(list) + '</div>' : '';

    mount.innerHTML =
      header() +
      '<div class="kfh-worklayout">' +
        filterRail() +
        '<div class="kfh-workbody">' +
          chartHtml +
          '<div class="incx-tablewrap">' +
            '<div class="incx-tablehead-note">Showing ' + list.length + ' of ' + INCIDENTS.length + ' <span>⬇</span></div>' +
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
      '</div>';

    // Search (caret-preserving)
    var q = mount.querySelector('#inc-q');
    if (q) {
      q.addEventListener('input', function () { state.filters.search = q.value; state.searchFocus = true; renderList(); });
      q.addEventListener('focus', function () { state.searchFocus = true; });
      q.addEventListener('blur', function () { state.searchFocus = false; });
    }
    // Country
    var cty = mount.querySelector('#inc-country');
    if (cty) cty.addEventListener('change', function () { state.country = cty.value; applyCountryScope(cty.value); loadIncidents(); });
    // Sort
    var srt = mount.querySelector('#inc-sort');
    if (srt) srt.addEventListener('change', function () { state.sort = srt.value; renderList(); });
    // Per-group accordion collapse (chevron)
    mount.querySelectorAll('[data-grptoggle]').forEach(function (h) {
      h.addEventListener('click', function () { var g = h.getAttribute('data-grptoggle'); state.groupsCollapsed[g] = !state.groupsCollapsed[g]; renderList(); });
    });
    // Radio filters (single select): Status, Recurrence
    mount.querySelectorAll('input[data-fgrp]').forEach(function (r) {
      r.addEventListener('change', function () {
        var grp = r.getAttribute('data-fgrp'), val = r.getAttribute('data-fval');
        if (grp === 'status') state.filters.status = val;
        else if (grp === 'rec') state.filters.recurrence = val;
        renderList();
      });
    });
    // Checkbox filters (multi select): Severity, Category
    mount.querySelectorAll('input[data-fgrpc]').forEach(function (c) {
      c.addEventListener('change', function () {
        var grp = c.getAttribute('data-fgrpc'), val = c.getAttribute('data-fval');
        var arr = grp === 'sev' ? state.filters.severities : state.filters.categories;
        var i = arr.indexOf(val);
        if (i >= 0) arr.splice(i, 1); else arr.push(val);
        renderList();
      });
    });
    // Clear all
    var fclr = mount.querySelector('#inc-filters-clear');
    if (fclr) fclr.addEventListener('click', function () {
      state.filters.status = 'all'; state.filters.recurrence = 'all';
      state.filters.categories = []; state.filters.severities = []; state.filters.impacts = [];
      renderList();
    });
    // Row click → detail
    mount.querySelectorAll('.incx-table tbody tr[data-id]').forEach(function (r) {
      r.addEventListener('click', function () {
        state.selected = r.getAttribute('data-id');
        state.tab = 'overview';
        render();
      });
    });
    // Keep caret in the search box across re-renders
    if (state.searchFocus && q) { q.focus(); var v = q.value; q.value = ''; q.value = v; }
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

  loadIncidents();
})();
