/**
 * KFH AIOps — Log Explorer over POST /api/v1/logs/search (Custom Index Engine).
 * Dynatrace-logs-style search/present in KFH colors: left facet rail (with counts), a query pill bar,
 * a volume timeseries, and a dense results table with expandable property rows + CSV export.
 * Country-scoped + RBAC-gated server-side; newest-first + paginated. Live search integration preserved.
 */
(function () {
  'use strict';

  var PAGE_SIZE = 25;
  var KINDS = ['ALERTS', 'LOGS', 'TRACES', 'METRICS', 'CHANGES'];
  var SEVERITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];

  var ranges = (window.KFHConfig && KFHConfig.TIME_RANGES) || [
    { id: '1h', label: 'Last Hour', ms: 3600000 },
    { id: '24h', label: 'Last 24 Hours', ms: 86400000 },
    { id: '7d', label: 'Last 7 Days', ms: 604800000 },
    { id: '30d', label: 'Last 30 Days', ms: 2592000000 }
  ];

  var state = {
    loading: false, error: null, result: null, page: 0, showChart: true, expanded: {},
    filters: { rangeId: '24h', kind: '', severity: '', source: '', service: '', trace: '', text: '' }
  };

  function root() { return document.getElementById('page-root'); }

  function esc(v) {
    if (window.KFHUtils && KFHUtils.escapeHtml) { return KFHUtils.escapeHtml(v == null ? '' : String(v)); }
    return String(v == null ? '' : v).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  function fmtTime(iso) { try { return new Date(iso).toLocaleString(); } catch (e) { return esc(iso); } }
  function title(s) { return s ? s.charAt(0) + s.slice(1).toLowerCase() : s; }
  function hits() { return (state.result && state.result.hits) ? state.result.hits : []; }

  function injectStyles() {
    if (document.getElementById('lx2-styles')) return;
    var s = document.createElement('style');
    s.id = 'lx2-styles';
    s.textContent = [
      '.lx2-wrap{display:grid;grid-template-columns:220px 1fr;gap:14px;padding:18px;max-width:1600px;margin:0 auto;}',
      '.lx2-rail{background:var(--surface-card);border:1px solid var(--surface-border);border-radius:12px;padding:12px;position:sticky;top:72px;height:max-content;}',
      '.lx2-facet{border-top:1px solid var(--surface-border-subtle);padding:9px 0 3px;}.lx2-facet:first-child{border-top:none;}',
      '.lx2-facet h5{margin:0 0 5px;font-size:.62rem;text-transform:uppercase;letter-spacing:.06em;color:var(--text-muted);font-weight:700;}',
      '.lx2-fitem{display:flex;align-items:center;gap:8px;padding:4px 6px;border-radius:6px;font-size:.8rem;color:var(--text-secondary);cursor:pointer;}',
      '.lx2-fitem:hover{background:var(--surface-off-white);}.lx2-fitem.on{background:var(--kfh-primary-light);color:var(--kfh-primary-dark);font-weight:700;}',
      '.lx2-fitem .c{margin-left:auto;font-weight:700;color:var(--text-muted);font-size:.72rem;}',
      '.lx2-qbar{display:flex;align-items:center;gap:8px;flex-wrap:wrap;background:var(--surface-card);border:1px solid var(--surface-border);border-radius:10px;padding:7px 10px;margin-bottom:12px;}',
      '.lx2-pill{display:inline-flex;align-items:center;gap:6px;height:26px;padding:0 8px;border-radius:7px;background:var(--kfh-primary-light);color:var(--kfh-primary-dark);font-size:.74rem;font-weight:600;}',
      '.lx2-pill .x{cursor:pointer;opacity:.7;font-weight:800;}.lx2-pill .x:hover{opacity:1;}',
      '.lx2-qinput{flex:1;min-width:160px;height:30px;border:none;outline:none;font-size:.85rem;background:transparent;color:var(--text-primary);}',
      '.lx2-sel{height:30px;border:1px solid var(--surface-border);border-radius:7px;font-size:.78rem;padding:0 6px;background:var(--surface-card);}',
      '.lx2-run{height:30px;padding:0 14px;border:none;border-radius:7px;background:var(--kfh-primary);color:#fff;font-size:.78rem;font-weight:700;cursor:pointer;}',
      '.lx2-ts{display:flex;align-items:flex-end;gap:2px;height:60px;margin-top:6px;}',
      '.lx2-b{flex:1;background:var(--kfh-primary);border-radius:2px 2px 0 0;opacity:.85;min-height:2px;}',
      '.lx2-reshead{display:flex;align-items:center;justify-content:space-between;margin-bottom:8px;}',
      '.lx2-reshead .cnt{font-size:.8rem;color:var(--text-secondary);}.lx2-reshead .cnt b{color:var(--text-primary);}',
      '.lx2-row{cursor:pointer;}.lx2-row:hover td{background:var(--surface-off-white);}',
      '.lx2-msg{font-family:var(--font-mono);font-size:.78rem;color:var(--text-primary);}',
      '.lx2-prop{display:grid;grid-template-columns:150px 1fr;gap:2px 10px;font-size:.78rem;padding:10px 14px;background:var(--surface-off-white);}',
      '.lx2-prop .k{color:var(--text-muted);}.lx2-prop .v{color:var(--text-primary);font-family:var(--font-mono);font-size:.74rem;word-break:break-all;}',
      '.lx2-linkbtn{border:none;background:none;color:var(--kfh-primary);font-weight:700;font-size:.76rem;cursor:pointer;}'
    ].join('');
    document.head.appendChild(s);
  }

  function buildQuery() {
    var range = ranges.filter(function (r) { return r.id === state.filters.rangeId; })[0] || ranges[1];
    var now = Date.now();
    return {
      kinds: state.filters.kind ? [state.filters.kind] : KINDS.slice(),
      from: new Date(now - (range ? range.ms : 86400000)).toISOString(),
      to: new Date(now).toISOString(),
      severity: state.filters.severity || null,
      sourceSystem: state.filters.source || null,
      serviceId: state.filters.service || null,
      traceId: state.filters.trace || null,
      text: state.filters.text || null,
      page: state.page, size: PAGE_SIZE
    };
  }

  function runSearch() {
    if (!(window.APIClient && APIClient.logs && APIClient.logs.search)) {
      state.error = 'Search API is not available. Refresh the page and try again.'; render(); return;
    }
    state.loading = true; state.error = null; state.expanded = {}; render();
    APIClient.logs.search(buildQuery()).then(function (res) {
      state.result = res; state.loading = false; render();
    }).catch(function (err) {
      state.error = (err && err.message) || 'Search failed'; state.result = null; state.loading = false; render();
    });
  }

  function facetCounts(field) {
    var m = {}; hits().forEach(function (d) { var v = d[field]; if (v) { m[v] = (m[v] || 0) + 1; } });
    return m;
  }

  function facetGroup(title, filterKey, valueField, values) {
    var counts = facetCounts(valueField);
    var cur = state.filters[filterKey];
    var items = ['<div class="lx2-fitem ' + (cur === '' ? 'on' : '') + '" data-facet="' + filterKey + '" data-val="">All</div>'];
    values.forEach(function (v) {
      var c = counts[v];
      items.push('<div class="lx2-fitem ' + (cur === v ? 'on' : '') + '" data-facet="' + filterKey + '" data-val="' + esc(v) + '">' +
        '<span>' + esc(title === 'Source' ? v : (title === 'Kind' || title === 'Status' ? (v.charAt(0) + v.slice(1).toLowerCase()) : v)) + '</span>' +
        (c != null ? '<span class="c">' + c + '</span>' : '') + '</div>');
    });
    return '<div class="lx2-facet"><h5>' + esc(title) + '</h5>' + items.join('') + '</div>';
  }

  function facetRail() {
    var sources = {}; hits().forEach(function (d) { if (d.sourceSystem) sources[d.sourceSystem] = 1; });
    var sourceVals = Object.keys(sources); if (!sourceVals.length) sourceVals = ['BMC', 'SCOM'];
    return '<aside class="lx2-rail">' +
      facetGroup('Kind', 'kind', 'kind', KINDS) +
      facetGroup('Status', 'severity', 'severity', SEVERITIES) +
      facetGroup('Source', 'source', 'sourceSystem', sourceVals) +
      '<div class="lx2-facet"><h5>Facet counts</h5><div style="font-size:.7rem;color:var(--text-muted);padding:2px 6px;">Reflect loaded results. Global counts arrive with the aggregation endpoint.</div></div>' +
      '</aside>';
  }

  function pill(label, key) {
    var v = state.filters[key]; if (!v) return '';
    return '<span class="lx2-pill">' + esc(label) + ': <b>' + esc(v) + '</b> <span class="x" data-clear="' + key + '">×</span></span>';
  }

  function queryBar() {
    var rangeOpts = ranges.map(function (r) {
      return '<option value="' + esc(r.id) + '"' + (state.filters.rangeId === r.id ? ' selected' : '') + '>' + esc(r.label) + '</option>';
    }).join('');
    return '<div class="lx2-qbar">' +
      pill('kind', 'kind') + pill('status', 'severity') + pill('source', 'source') + pill('service', 'service') + pill('trace', 'trace') +
      '<input id="lx-text" class="lx2-qinput" type="text" placeholder="Type to filter — message text…" value="' + esc(state.filters.text) + '">' +
      '<select id="lx-range" class="lx2-sel">' + rangeOpts + '</select>' +
      '<button id="lx-run" class="lx2-run">Run query</button>' +
      '</div>';
  }

  function timeseries() {
    if (!state.showChart) return '';
    var range = ranges.filter(function (r) { return r.id === state.filters.rangeId; })[0] || ranges[1];
    var to = Date.now(), from = to - (range ? range.ms : 86400000), N = 44;
    var buckets = []; for (var i = 0; i < N; i++) buckets.push(0);
    hits().forEach(function (d) {
      var t = new Date(d.timestamp).getTime();
      var idx = Math.floor((t - from) / (to - from) * N);
      if (idx >= 0 && idx < N) buckets[idx]++;
    });
    var max = Math.max.apply(null, buckets.concat([1]));
    var bars = buckets.map(function (b) { return '<div class="lx2-b" style="height:' + (Math.round(b / max * 54) + 2) + 'px;"></div>'; }).join('');
    return '<div class="kfhx-panel" style="padding:10px 14px;margin-bottom:12px;">' +
      '<div style="display:flex;justify-content:space-between;align-items:center;">' +
      '<span style="font-size:.72rem;color:var(--text-muted);">Log volume over time (loaded window)</span>' +
      '<button id="lx-hidechart" class="lx2-linkbtn">Hide chart</button></div>' +
      '<div class="lx2-ts">' + bars + '</div></div>';
  }

  function sevBadge(sev) {
    var s = (sev || '').toLowerCase();
    var col = { critical: 'var(--color-critical)', high: 'var(--color-high)', medium: 'var(--color-medium)', low: 'var(--color-low)', info: 'var(--kfh-primary)' }[s] || 'var(--text-muted)';
    return '<span style="display:inline-flex;align-items:center;gap:5px;font-weight:700;font-size:.74rem;color:' + col + ';"><span class="kfhx-dot" style="background:' + col + ';"></span>' + esc(sev || '-') + '</span>';
  }

  function propRows(d) {
    var keys = Object.keys(d);
    var body = keys.map(function (k) {
      var v = d[k]; if (v && typeof v === 'object') v = JSON.stringify(v);
      return '<div class="k">' + esc(k) + '</div><div class="v">' + esc(v) + '</div>';
    }).join('');
    return '<tr class="lx2-detrow"><td colspan="6" style="padding:0;"><div class="lx2-prop">' + body + '</div></td></tr>';
  }

  function resultsPanel() {
    if (state.loading) return '<div class="kfhx-panel" style="padding:26px;text-align:center;color:var(--text-muted);">Searching…</div>';
    if (state.error) return '<div class="kfhx-panel" style="padding:16px;color:var(--color-critical);">' + esc(state.error) + '</div>';
    if (!state.result) return '<div class="kfhx-panel" style="padding:26px;text-align:center;color:var(--text-muted);">Run a query to search the telemetry index.</div>';
    var rows = hits(), r = state.result;
    var from = r.total ? (r.page * r.size + 1) : 0, to = Math.min((r.page + 1) * r.size, r.total);
    var tbody = rows.length ? rows.map(function (d, i) {
      var main = '<tr class="lx2-row" data-i="' + i + '">' +
        '<td class="lx-nowrap" style="white-space:nowrap;color:var(--text-muted);">' + fmtTime(d.timestamp) + '</td>' +
        '<td>' + sevBadge(d.severity) + '</td>' +
        '<td><span class="kfhx-badge info">' + esc(d.sourceSystem) + '</span></td>' +
        '<td class="mono">' + esc(d.resourceId) + '</td>' +
        '<td class="mono">' + esc(d.serviceId) + '</td>' +
        '<td class="lx2-msg">' + esc(d.message) + '</td></tr>';
      return main + (state.expanded[i] ? propRows(d) : '');
    }).join('') : '<tr><td colspan="6" style="padding:26px;text-align:center;color:var(--text-muted);">No matching telemetry in this window.</td></tr>';

    var pager = r.total ? '<div style="display:flex;gap:8px;align-items:center;">' +
      '<button id="lx-prev" class="lx2-linkbtn"' + (r.page > 0 ? '' : ' disabled') + '>‹ Prev</button>' +
      '<button id="lx-next" class="lx2-linkbtn"' + (to < r.total ? '' : ' disabled') + '>Next ›</button></div>' : '';

    return '<div class="kfhx-panel" style="padding:12px 14px;">' +
      '<div class="lx2-reshead">' +
        '<div class="cnt">Showing <b>' + esc(from) + '–' + esc(to) + '</b> of <b>' + esc(r.total) + '</b> records · ' + esc(r.tookMs) + ' ms</div>' +
        '<div style="display:flex;gap:12px;align-items:center;">' + pager +
          '<button id="lx-csv" class="lx2-linkbtn">Download CSV</button></div>' +
      '</div>' +
      '<div style="overflow-x:auto;"><table class="kfhx-table"><thead><tr>' +
        '<th>Time</th><th>Status</th><th>Source</th><th>Resource</th><th>Service</th><th>Message</th>' +
      '</tr></thead><tbody>' + tbody + '</tbody></table></div>' +
      '</div>';
  }

  function render() {
    injectStyles();
    var el = root(); if (!el) return;
    el.innerHTML = '<div class="lx2-wrap">' + facetRail() +
      '<div class="lx2-main">' +
        '<div class="kfhx-section-head" style="margin-bottom:10px;"><div class="kfhx-section-title" style="font-size:1.15rem;">Log Explorer</div>' +
        (state.showChart ? '' : '<button id="lx-showchart" class="lx2-linkbtn">Show chart</button>') + '</div>' +
        queryBar() + timeseries() + resultsPanel() +
      '</div></div>';
    wire(el);
  }

  function setFilter(key, val) { state.filters[key] = val; state.page = 0; runSearch(); }

  function wire(el) {
    el.querySelectorAll('[data-facet]').forEach(function (n) {
      n.addEventListener('click', function () { setFilter(n.getAttribute('data-facet'), n.getAttribute('data-val')); });
    });
    el.querySelectorAll('[data-clear]').forEach(function (n) {
      n.addEventListener('click', function () { setFilter(n.getAttribute('data-clear'), ''); });
    });
    var run = el.querySelector('#lx-run');
    if (run) run.addEventListener('click', function () { var t = el.querySelector('#lx-text'); state.filters.text = t ? t.value : ''; var rg = el.querySelector('#lx-range'); if (rg) state.filters.rangeId = rg.value; state.page = 0; runSearch(); });
    var text = el.querySelector('#lx-text');
    if (text) text.addEventListener('keydown', function (e) { if (e.key === 'Enter') { state.filters.text = text.value; state.page = 0; runSearch(); } });
    var rg = el.querySelector('#lx-range');
    if (rg) rg.addEventListener('change', function () { state.filters.rangeId = rg.value; state.page = 0; runSearch(); });
    var prev = el.querySelector('#lx-prev');
    if (prev) prev.addEventListener('click', function () { if (state.page > 0) { state.page -= 1; runSearch(); } });
    var next = el.querySelector('#lx-next');
    if (next) next.addEventListener('click', function () { state.page += 1; runSearch(); });
    var hide = el.querySelector('#lx-hidechart');
    if (hide) hide.addEventListener('click', function () { state.showChart = false; render(); });
    var show = el.querySelector('#lx-showchart');
    if (show) show.addEventListener('click', function () { state.showChart = true; render(); });
    var csv = el.querySelector('#lx-csv');
    if (csv) csv.addEventListener('click', downloadCsv);
    el.querySelectorAll('.lx2-row').forEach(function (r) {
      r.addEventListener('click', function () { var i = r.getAttribute('data-i'); state.expanded[i] = !state.expanded[i]; render(); });
    });
  }

  function downloadCsv() {
    var rows = hits(); if (!rows.length) return;
    var cols = ['timestamp', 'severity', 'sourceSystem', 'serviceId', 'resourceId', 'message'];
    var lines = [cols.join(',')];
    rows.forEach(function (d) {
      lines.push(cols.map(function (c) { return '"' + String(d[c] == null ? '' : d[c]).replace(/"/g, '""') + '"'; }).join(','));
    });
    try {
      var blob = new Blob([lines.join('\n')], { type: 'text/csv' });
      var a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = 'kfh-logs.csv';
      document.body.appendChild(a); a.click(); document.body.removeChild(a); URL.revokeObjectURL(a.href);
    } catch (e) { /* ignore */ }
  }

  function init() { state.page = 0; render(); runSearch(); }
  window.LogExplorer = { init: init };
  init();
})();
