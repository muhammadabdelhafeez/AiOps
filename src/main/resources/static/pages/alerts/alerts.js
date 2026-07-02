/**
 * KFH AIOps Command Center — Alert Explorer, reconstructed to enterprise observability's "Events" organization
 * in KFH "Beyond Horizons" colors. Self-mounting, vanilla, uses the shared kfhx-* design system.
 *
 * Faceted rail (Severity/Source/Kind) + count-labeled severity tabs + query pill bar + volume timeline
 * + "Showing N of M" record header + column manager + dense event stream (rich NL message, dedup
 * count, linked incident) + master-detail with a properties key-value table. Modelled on the canonical
 * TelemetryDocument (BMC + SCOM). Illustrative data until bound live to the Custom Index.
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

  // Live alerts, loaded from the Custom Index via /api/v1/alerts. Empty until a connector ingests.
  var ALERTS = [];

  // Normalise CRITICAL/HIGH/MEDIUM/LOW/INFO (index/read-model) to the page's Title-case severities.
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
    return d.toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit' });
  }

  // Map a live read-model / index row (TelemetryDocument-shaped) into the display row this page renders.
  function mapAlert(r, i) {
    r = r || {};
    var attrs = r.attributes || r.attrs || {};
    return {
      id: r.id || r.alertId || r.fingerprint || ('a' + i),
      time: fmtTime(r.timestamp || r.time || r.creationTime || r.createdAt || r.occurredAt),
      sev: normSev(r.severity || r.sev),
      source: r.sourceSystem || r.source || r.sourceType || '—',
      kind: r.kind || 'ALERTS',
      entity: r.resourceId || r.entity || r.ci || r.host || r.sourceHostname || '—',
      etype: r.entityType || r.etype || r.resourceType || r.className || r.class || '',
      cat: r.category || r.cat || r.className || '',
      msg: r.message || r.msg || r.summary || r.alertName || '',
      count: Number(r.count || r.dedupeCount || r.occurrences || 1),
      inc: r.incidentId || r.incidentKey || r.inc || null,
      ts: r.timestamp || r.time || r.creationTime || r.createdAt || r.occurredAt || null,
      attrs: (attrs && typeof attrs === 'object') ? attrs : {}
    };
  }

  var SEV_ORDER = ['Critical', 'High', 'Medium', 'Low'];
  var REFRESHED = '';
  function sessionCountry() {
    try { var s = (window.KFHConfig && KFHConfig.getSession && KFHConfig.getSession()) || {}; return s.countryCode || 'ALL'; } catch (e) { return 'ALL'; }
  }
  // Persist the chosen country app-wide and notify listeners, so the header country
  // acts as the global scope filter (the sidebar scope switcher was removed).
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
    filters: { source: '', kind: '', text: '' }, tab: 'All', sort: 'sev_desc',
    country: sessionCountry(), selected: null, colsOpen: false, railOpen: true, searchFocus: false,
    groupsCollapsed: {},
    loading: true, error: null, loaded: false,
    cols: { time: true, sev: true, source: true, entity: true, category: true, message: true, count: true, incident: true }
  };

  function loadAlerts() {
    if (!(window.APIClient && APIClient.alerts && APIClient.alerts.list)) {
      state.loading = false; state.loaded = true; state.error = 'Alerts API is not available.'; render(); return;
    }
    state.loading = true; state.error = null; render();
    var params = { page: 0, size: 100 };
    if (state.country && state.country !== 'ALL') params.country = state.country;
    APIClient.alerts.list(params).then(function (res) {
      var rows = (res && (res.content || res.items || res.data || res.rows)) || (Array.isArray(res) ? res : []);
      ALERTS = rows.map(mapAlert);
      REFRESHED = new Date().toLocaleString();
      state.loading = false; state.loaded = true; render();
    }).catch(function (err) {
      ALERTS = [];
      REFRESHED = new Date().toLocaleString();
      state.loading = false; state.loaded = true; state.error = (err && err.message) || 'Failed to load alerts.'; render();
    });
  }

  function sevColor(sev) {
    return { Critical: 'var(--color-critical)', High: 'var(--color-high)', Medium: 'var(--color-medium)', Low: 'var(--color-low)' }[sev] || 'var(--text-muted)';
  }

  // Column definitions drive both header + rows, so the column manager can hide any of them.
  var COLS = [
    { key: 'time', label: 'Time', cell: function (a) { return '<td style="white-space:nowrap;color:var(--text-muted);">' + esc(a.time) + '</td>'; } },
    { key: 'sev', label: 'Severity', cell: function (a) { return '<td><span class="alx-sev" style="color:' + sevColor(a.sev) + ';"><span class="kfhx-dot" style="background:' + sevColor(a.sev) + ';"></span>' + esc(a.sev) + '</span></td>'; } },
    { key: 'source', label: 'Source', cell: function (a) { return '<td><span class="kfhx-badge info">' + esc(a.source) + '</span></td>'; } },
    { key: 'entity', label: 'Entity', cell: function (a) { return '<td class="mono">' + esc(a.entity) + '</td>'; } },
    { key: 'category', label: 'Category', cell: function (a) { return '<td>' + esc(a.cat) + '</td>'; } },
    { key: 'message', label: 'Message', cell: function (a) { return '<td style="color:var(--text-primary);">' + esc(a.msg) + '</td>'; } },
    { key: 'count', label: 'Count', align: 'right', cell: function (a) { return '<td style="text-align:right;"><span class="alx-count" title="occurrences (dedup)">×' + a.count + '</span></td>'; } },
    { key: 'incident', label: 'Incident', cell: function (a) { var lbl = a.inc ? (String(a.inc).length > 18 ? String(a.inc).slice(0, 16) + '…' : String(a.inc)) : ''; return '<td>' + (a.inc ? '<a href="#incidents" data-page="incidents" class="kfhx-badge info" style="text-decoration:none;">◈ ' + esc(lbl) + '</a>' : '<span style="color:var(--text-muted);font-size:.72rem;">—</span>') + '</td>'; } }
  ];

  function injectStyles() {
    if (document.getElementById('alx-styles')) return;
    var s = document.createElement('style');
    s.id = 'alx-styles';
    s.textContent = [
      '.alx-wrap{display:grid;grid-template-columns:200px 1fr;gap:16px;padding:18px;max-width:1600px;margin:0 auto;}',
      '.alx-rail{background:var(--surface-card);border:1px solid var(--surface-border);border-radius:12px;padding:14px;height:max-content;position:sticky;top:72px;}',
      '.alx-facet{margin-bottom:16px;}.alx-facet h4{font-size:.66rem;font-weight:700;text-transform:uppercase;letter-spacing:.06em;color:var(--text-muted);margin:0 0 8px;}',
      '.alx-facet button{display:flex;width:100%;justify-content:space-between;align-items:center;background:none;border:none;padding:5px 8px;border-radius:7px;font-size:.8rem;color:var(--text-secondary);cursor:pointer;text-align:left;}',
      '.alx-facet button:hover{background:var(--surface-off-white);}.alx-facet button.on{background:var(--kfh-primary-light);color:var(--kfh-primary-dark);font-weight:700;}',
      '.alx-tabs{display:flex;gap:6px;margin-bottom:12px;flex-wrap:wrap;}',
      '.alx-tab{display:inline-flex;align-items:center;gap:6px;padding:6px 12px;border-radius:999px;border:1px solid var(--surface-border);background:var(--surface-card);font-size:.78rem;font-weight:600;color:var(--text-secondary);cursor:pointer;}',
      '.alx-tab.on{background:var(--kfh-primary);color:#fff;border-color:var(--kfh-primary);}.alx-tab .n{font-weight:800;}',
      '.alx-qbar{display:flex;align-items:center;gap:8px;flex-wrap:wrap;background:var(--surface-card);border:1px solid var(--surface-border);border-radius:10px;padding:7px 10px;margin-bottom:12px;}',
      '.alx-pill{display:inline-flex;align-items:center;gap:6px;height:26px;padding:0 8px;border-radius:7px;background:var(--kfh-primary-light);color:var(--kfh-primary-dark);font-size:.74rem;font-weight:600;}',
      '.alx-pill .x{cursor:pointer;opacity:.7;font-weight:800;}.alx-pill .x:hover{opacity:1;}',
      '.alx-qinput{flex:1;min-width:150px;height:30px;border:none;outline:none;background:transparent;font-size:.85rem;color:var(--text-primary);}',
      '.alx-timeline{display:flex;align-items:flex-end;gap:3px;height:60px;padding-top:8px;}.alx-timeline .b{flex:1;border-radius:2px 2px 0 0;}',
      '.alx-reshead{display:flex;align-items:center;justify-content:space-between;margin-bottom:8px;position:relative;}',
      '.alx-reshead .cnt{font-size:.8rem;color:var(--text-secondary);}.alx-reshead .cnt b{color:var(--text-primary);}',
      '.alx-linkbtn{border:none;background:none;color:var(--kfh-primary);font-weight:700;font-size:.76rem;cursor:pointer;}',
      '.alx-colmenu{position:absolute;right:0;top:26px;z-index:20;background:var(--surface-card);border:1px solid var(--surface-border);border-radius:10px;box-shadow:var(--shadow-card-hover);padding:8px;min-width:170px;}',
      '.alx-colmenu label{display:flex;align-items:center;gap:8px;padding:4px 6px;font-size:.8rem;color:var(--text-secondary);cursor:pointer;border-radius:6px;}.alx-colmenu label:hover{background:var(--surface-off-white);}',
      '.alx-row{cursor:pointer;}.alx-row.on td{background:var(--kfh-primary-light);}',
      '.alx-sev{display:inline-flex;align-items:center;gap:6px;font-weight:700;font-size:.76rem;}',
      '.alx-count{display:inline-flex;align-items:center;height:18px;padding:0 7px;border-radius:999px;background:var(--surface-off-white);border:1px solid var(--surface-border);font-size:.68rem;font-weight:700;color:var(--text-secondary);}',
      '.alx-prop{display:grid;grid-template-columns:130px 1fr;gap:2px 10px;font-size:.8rem;}',
      '.alx-prop .k{color:var(--text-muted);}.alx-prop .v{color:var(--text-primary);font-family:var(--font-mono);font-size:.76rem;word-break:break-all;}',
      /* page body layout (header styles are shared: .kfh-phdr* in kfh-design-system.css) */
      '.alx-wrap{display:block;grid-template-columns:none;}'
    ].join('');
    document.head.appendChild(s);
  }

  function passes(a) {
    var f = state.filters;
    if (f.source && a.source !== f.source) return false;
    if (f.kind && a.kind !== f.kind) return false;
    if (state.tab !== 'All' && a.sev !== state.tab) return false;
    if (f.text) {
      var q = f.text.toLowerCase();
      var hay = ((a.msg || '') + ' ' + (a.entity || '') + ' ' + (a.source || '') + ' ' + (a.cat || '') + ' ' + (a.inc || '')).toLowerCase();
      if (hay.indexOf(q) < 0) return false;
    }
    return true;
  }

  function sortList(list) {
    var rank = { Critical: 4, High: 3, Medium: 2, Low: 1 };
    switch (state.sort) {
      case 'sev_asc': list.sort(function (a, b) { return (rank[a.sev] || 0) - (rank[b.sev] || 0); }); break;
      case 'count_desc': list.sort(function (a, b) { return (b.count || 0) - (a.count || 0); }); break;
      case 'time_desc': list.sort(function (a, b) { return new Date(b.ts || 0) - new Date(a.ts || 0); }); break;
      default: list.sort(function (a, b) { return (rank[b.sev] || 0) - (rank[a.sev] || 0); });
    }
    return list;
  }

  function uniqueVals(key, fallback) {
    var set = {};
    ALERTS.forEach(function (a) { if (a[key]) set[a[key]] = 1; });
    var vals = Object.keys(set);
    return vals.length ? vals : fallback;
  }

  function facet(title, key, values) {
    var cur = state.filters[key];
    var btns = ['<button class="' + (cur === '' ? 'on' : '') + '" data-facet="' + key + '" data-val="">All</button>'];
    values.forEach(function (v) {
      var c = ALERTS.filter(function (a) { return a[key === 'severity' ? 'sev' : key] === v; }).length;
      btns.push('<button class="' + (cur === v ? 'on' : '') + '" data-facet="' + key + '" data-val="' + esc(v) + '"><span>' + esc(v) + '</span><span class="alx-count">' + c + '</span></button>');
    });
    return '<div class="alx-facet"><h4>' + esc(title) + '</h4>' + btns.join('') + '</div>';
  }

  // Real hourly activity (24 buckets, by hour-of-day) computed from loaded alerts.
  function timeline() {
    var buckets = [];
    for (var i = 0; i < 24; i++) buckets.push(0);
    ALERTS.forEach(function (a) {
      if (!a.ts) return;
      var d = new Date(a.ts);
      if (isNaN(d.getTime())) return;
      buckets[d.getHours()]++;
    });
    var max = 1;
    buckets.forEach(function (n) { if (n > max) max = n; });
    return buckets.map(function (n, h) {
      var ht = 6 + Math.round((n / max) * 34);
      var col = n > 0 ? 'var(--color-critical)' : 'var(--surface-border)';
      var hh = (h < 10 ? '0' : '') + h;
      return '<div class="b" title="' + hh + ':00 — ' + n + ' alert(s)" style="height:' + ht + 'px;background:' + col + ';"></div>';
    }).join('');
  }

  // ---- Consolidated header (title + search + country + filters popover + sort) ----
  var COUNTRIES = [
    { v: 'ALL', t: 'All KFH Groups' }, { v: 'KW', t: 'Kuwait' }, { v: 'BH', t: 'Bahrain' }, { v: 'EG', t: 'Egypt' }
  ];
  var SORTS = [
    { v: 'sev_desc', t: 'Impact: High to low' }, { v: 'sev_asc', t: 'Impact: Low to high' },
    { v: 'count_desc', t: 'Most frequent' }, { v: 'time_desc', t: 'Newest first' }
  ];

  function header() {
    var total = ALERTS.length;
    var activeF = (state.tab !== 'All' ? 1 : 0) + (state.filters.source ? 1 : 0) + (state.filters.kind ? 1 : 0);
    var fLabel = activeF ? (activeF + ' filter' + (activeF > 1 ? 's' : '')) : 'All alerts';
    var countryOpts = COUNTRIES.map(function (c) { return '<option value="' + c.v + '" ' + (state.country === c.v ? 'selected' : '') + '>' + esc(c.t) + '</option>'; }).join('');
    var sortOpts = SORTS.map(function (o) { return '<option value="' + o.v + '" ' + (state.sort === o.v ? 'selected' : '') + '>' + esc(o.t) + '</option>'; }).join('');
    var badge = state.error
      ? '<span class="kfhx-badge" style="background:var(--color-critical);color:#fff;">' + esc(state.error) + '</span>'
      : '<span class="kfhx-badge info">Live</span>';
    return '<div class="kfh-phdr">' +
      '<div class="kfh-phdr-titlewrap"><h1 class="kfh-phdr-title">Alert Correlation</h1><span class="kfh-phdr-sub">' + total + ' issues · refreshed ' + esc(REFRESHED || '—') + '</span></div>' +
      '<div class="kfh-phdr-search">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>' +
        '<input id="alx-q" type="text" placeholder="Search alerts, hosts, teams…" value="' + esc(state.filters.text) + '"></div>' +
      '<div class="kfh-phdr-ctrls">' +
        badge +
        '<div class="kfh-phdr-cty"><span class="lbl">Country</span><select id="alx-country" class="kfh-phdr-select">' + countryOpts + '</select></div>' +
      '</div>' +
    '</div>';
  }

  // One collapsible filter section (accordion) with radio rows.
  function fltGroup(id, title, opts) {
    var collapsed = !!state.groupsCollapsed[id];
    var body = opts.map(function (o) {
      return '<label class="kfh-fltopt"><input type="radio" name="alx-flt-' + id + '" ' + (o.on ? 'checked' : '') + ' data-fgrp="' + id + '" data-fval="' + esc(o.value) + '">' +
        (o.dot ? '<span class="dot" style="background:' + o.dot + ';"></span>' : '') +
        '<span>' + esc(o.label) + '</span>' + (o.n != null ? '<span class="n">' + o.n + '</span>' : '') + '</label>';
    }).join('');
    return '<div class="kfh-fltg ' + (collapsed ? 'collapsed' : '') + '">' +
      '<div class="kfh-fltg-head" data-grptoggle="' + id + '"><span class="t">' + esc(title) + '</span><span class="chev">▾</span></div>' +
      '<div class="kfh-fltg-body">' + body + '</div></div>';
  }

  // Collapsible bordered filter panel (Dynatrace Problems-style) — radio rows.
  function filterRail() {
    var sevOpts = ['All'].concat(SEV_ORDER).map(function (s) {
      return { label: s, value: s, on: (s === 'All' ? state.tab === 'All' : state.tab === s),
        dot: s === 'All' ? null : sevColor(s),
        n: s === 'All' ? ALERTS.length : ALERTS.filter(function (a) { return a.sev === s; }).length };
    });
    var srcOpts = ['All'].concat(uniqueVals('source', ['BMC', 'SCOM'])).map(function (s) {
      return { label: s, value: (s === 'All' ? '' : s), on: (s === 'All' ? !state.filters.source : state.filters.source === s),
        n: s === 'All' ? ALERTS.length : ALERTS.filter(function (a) { return a.source === s; }).length };
    });
    var kindOpts = ['All'].concat(uniqueVals('kind', ['ALERTS', 'LOGS', 'METRICS'])).map(function (k) {
      return { label: k, value: (k === 'All' ? '' : k), on: (k === 'All' ? !state.filters.kind : state.filters.kind === k),
        n: k === 'All' ? ALERTS.length : ALERTS.filter(function (a) { return a.kind === k; }).length };
    });
    return '<aside class="kfh-filters">' +
      fltGroup('sev', 'Severity', sevOpts) +
      fltGroup('src', 'Source', srcOpts) +
      fltGroup('kind', 'Kind', kindOpts) +
      '<button id="alx-filters-clear" class="kfh-filters-clear">Clear all filters</button>' +
    '</aside>';
  }

  function visibleCols() { return COLS.filter(function (c) { return state.cols[c.key]; }); }

  function colMenu() {
    var boxes = COLS.map(function (c) {
      return '<label><input type="checkbox" data-col="' + c.key + '" ' + (state.cols[c.key] ? 'checked' : '') + '/> ' + esc(c.label) + '</label>';
    }).join('');
    return '<div class="alx-colmenu">' + boxes + '</div>';
  }

  function tableRows(list) {
    var vis = visibleCols();
    return list.map(function (a) {
      var cells = vis.map(function (c) { return c.cell(a); }).join('');
      return '<tr class="alx-row ' + (state.selected === a.id ? 'on' : '') + '" data-id="' + a.id + '">' + cells + '</tr>';
    }).join('');
  }

  function detailPanel(a) {
    var attrs = a.attrs || {};
    var props = Object.keys(attrs).map(function (k) { return '<div class="k">' + esc(k) + '</div><div class="v">' + esc(attrs[k]) + '</div>'; }).join('');
    return '<div class="kfhx-panel">' +
      '<div style="display:flex;justify-content:space-between;align-items:flex-start;">' +
        '<div><div class="kfhx-entity-title">' + esc(a.entity) + '</div><div class="kfhx-section-sub">' + esc(a.etype) + ' · ' + esc(a.source) + '</div></div>' +
        '<span class="alx-sev" style="color:' + sevColor(a.sev) + ';"><span class="kfhx-dot" style="background:' + sevColor(a.sev) + ';"></span>' + esc(a.sev) + '</span>' +
      '</div>' +
      '<div style="margin:12px 0;padding:12px;background:var(--surface-off-white);border-radius:10px;border-left:3px solid ' + sevColor(a.sev) + ';font-size:.85rem;line-height:1.55;color:var(--text-secondary);">' + esc(a.msg) + '</div>' +
      (a.inc ? '<div style="margin-bottom:12px;"><span style="font-size:.72rem;color:var(--text-muted);">Correlated into </span><a href="#incidents" data-page="incidents" class="kfhx-badge info" style="text-decoration:none;">◈ ' + esc(a.inc) + '</a></div>' : '') +
      '<div style="font-size:.7rem;font-weight:700;text-transform:uppercase;letter-spacing:.05em;color:var(--text-muted);margin-bottom:8px;">Properties</div>' +
      '<div class="alx-prop"><div class="k">timestamp</div><div class="v">' + esc(a.time) + '</div>' +
        '<div class="k">occurrences</div><div class="v">' + a.count + '</div>' +
        '<div class="k">category</div><div class="v">' + esc(a.cat) + '</div>' + props + '</div>' +
      '<a href="#explorer" data-page="explorer" class="kfhx-iconbtn" style="width:auto;padding:0 12px;margin-top:14px;font-size:.8rem;font-weight:600;text-decoration:none;">Open in Log Explorer →</a>' +
      '</div>';
  }

  function render() {
    injectStyles();
    if (state.loading && !state.loaded) {
      mount.innerHTML = '<div class="kfh-phdr"><div class="kfh-phdr-titlewrap"><h1 class="kfh-phdr-title">Alert Correlation</h1><span class="kfh-phdr-sub">loading…</span></div></div>' +
        '<div class="kfh-worklayout"><div class="kfh-workbody"><div class="kfhx-panel" style="padding:40px;text-align:center;color:var(--text-muted);">Loading alerts…</div></div></div>';
      return;
    }
    var list = sortList(ALERTS.filter(passes));
    var active = ALERTS.filter(function (a) { return a.sev === 'Critical' || a.sev === 'High'; }).length;
    var sel = state.selected ? ALERTS.filter(function (a) { return a.id === state.selected; })[0] : null;
    var vis = visibleCols();
    var hidden = COLS.length - vis.length;

    var headCells = vis.map(function (c) { return '<th' + (c.align === 'right' ? ' style="text-align:right;"' : '') + '>' + esc(c.label) + '</th>'; }).join('');
    var tableHtml =
      '<div class="kfhx-panel" style="padding:12px 14px;">' +
        '<div class="alx-reshead">' +
          '<div class="cnt">Showing <b>' + list.length + '</b> of <b>' + ALERTS.length + '</b> events</div>' +
          '<div><button id="alx-cols" class="alx-linkbtn">Columns' + (hidden ? ' (' + hidden + ' hidden)' : '') + '</button>' + (state.colsOpen ? colMenu() : '') + '</div>' +
        '</div>' +
        '<div style="overflow-x:auto;"><table class="kfhx-table"><thead><tr>' + headCells + '</tr></thead>' +
        '<tbody>' + (tableRows(list) || '<tr><td colspan="' + vis.length + '" style="padding:26px;text-align:center;color:var(--text-muted);">' + (ALERTS.length === 0 ? 'No alerts yet. Enable a connector under <a href="#settings" data-page="settings">Settings → Connections</a> to start ingesting.' : 'No alerts match the filter.') + '</td></tr>') + '</tbody></table></div>' +
      '</div>';

    mount.innerHTML =
      header() +
      '<div class="kfh-worklayout">' +
        filterRail() +
        '<div class="kfh-workbody">' +
          '<div class="kfhx-panel" style="padding:10px 16px;margin-bottom:14px;">' +
            '<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:2px;">' +
              '<div style="font-size:.7rem;font-weight:800;letter-spacing:.06em;color:var(--text-muted);text-transform:uppercase;">Hourly activity</div>' +
              '<div style="font-size:.72rem;color:var(--text-muted);"><b style="color:var(--color-critical);">' + active + '</b> actionable / ' + ALERTS.length + '</div>' +
            '</div>' +
            '<div class="alx-timeline">' + timeline() + '</div>' +
          '</div>' +
          (sel ? '<div class="kfhx-split" style="grid-template-columns:1fr 380px;">' + tableHtml + detailPanel(sel) + '</div>' : tableHtml) +
        '</div>' +
      '</div>';

    // Search (caret-preserving)
    var q = mount.querySelector('#alx-q');
    if (q) {
      q.addEventListener('input', function () { state.filters.text = q.value; state.searchFocus = true; render(); });
      q.addEventListener('focus', function () { state.searchFocus = true; });
      q.addEventListener('blur', function () { state.searchFocus = false; });
    }
    // Country — actionable global filter: scope this page's data AND persist the scope
    // app-wide (so other pages inherit it) now that the sidebar scope switcher is gone.
    var cty = mount.querySelector('#alx-country');
    if (cty) cty.addEventListener('change', function () { state.country = cty.value; applyCountryScope(cty.value); loadAlerts(); });
    // Sort
    var srt = mount.querySelector('#alx-sort');
    if (srt) srt.addEventListener('change', function () { state.sort = srt.value; render(); });
    // Per-group accordion collapse (chevron)
    mount.querySelectorAll('[data-grptoggle]').forEach(function (h) {
      h.addEventListener('click', function () { var g = h.getAttribute('data-grptoggle'); state.groupsCollapsed[g] = !state.groupsCollapsed[g]; render(); });
    });
    // Radio filter selection
    mount.querySelectorAll('input[data-fgrp]').forEach(function (r) {
      r.addEventListener('change', function () {
        var grp = r.getAttribute('data-fgrp'), val = r.getAttribute('data-fval');
        if (grp === 'sev') state.tab = val;
        else if (grp === 'src') state.filters.source = val;
        else if (grp === 'kind') state.filters.kind = val;
        render();
      });
    });
    var fclr = mount.querySelector('#alx-filters-clear');
    if (fclr) fclr.addEventListener('click', function () { state.tab = 'All'; state.filters.source = ''; state.filters.kind = ''; render(); });
    // Column manager
    var colsBtn = mount.querySelector('#alx-cols');
    if (colsBtn) colsBtn.addEventListener('click', function (e) { e.stopPropagation(); state.colsOpen = !state.colsOpen; render(); });
    mount.querySelectorAll('[data-col]').forEach(function (cb) {
      cb.addEventListener('change', function () { state.cols[cb.getAttribute('data-col')] = cb.checked; render(); });
    });
    // Rows
    mount.querySelectorAll('.alx-row').forEach(function (r) {
      r.addEventListener('click', function (e) {
        if (e.target.closest('a')) return;
        state.selected = (state.selected === r.getAttribute('data-id')) ? null : r.getAttribute('data-id');
        render();
      });
    });
    // Keep caret in the search box across re-renders
    if (state.searchFocus && q) { q.focus(); var v = q.value; q.value = ''; q.value = v; }
  }

  loadAlerts();
})();
