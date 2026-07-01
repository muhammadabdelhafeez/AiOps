/**
 * KFH AIOps — Log Explorer (Kibana-Discover style telemetry search).
 * Self-mounting page over POST /api/v1/logs/search (Custom Index Engine).
 * Country-scoped and RBAC-gated server-side; results are newest-first + paginated.
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
    loading: false,
    error: null,
    result: null,
    page: 0,
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
      page: state.page,
      size: PAGE_SIZE
    };
  }

  function runSearch() {
    if (!(window.APIClient && APIClient.logs && APIClient.logs.search)) {
      state.error = 'Search API is not available. Refresh the page and try again.';
      render();
      return;
    }
    state.loading = true;
    state.error = null;
    render();
    APIClient.logs.search(buildQuery()).then(function (res) {
      state.result = res;
      state.loading = false;
      render();
    }).catch(function (err) {
      state.error = (err && err.message) || 'Search failed';
      state.result = null;
      state.loading = false;
      render();
    });
  }

  function option(value, label, selected) {
    return '<option value="' + esc(value) + '"' + (selected === value ? ' selected' : '') + '>' + esc(label) + '</option>';
  }

  function filterBar() {
    var rangeOpts = ranges.map(function (r) { return option(r.id, r.label, state.filters.rangeId); }).join('');
    var kindOpts = option('', 'All kinds', state.filters.kind)
      + KINDS.map(function (k) { return option(k, title(k), state.filters.kind); }).join('');
    var sevOpts = option('', 'Any severity', state.filters.severity)
      + SEVERITIES.map(function (s) { return option(s, title(s), state.filters.severity); }).join('');
    return ''
      + '<div class="lx-filters">'
      +   '<input id="lx-text" class="lx-input lx-grow" type="text" placeholder="Search message text…" value="' + esc(state.filters.text) + '">'
      +   '<select id="lx-range" class="lx-input">' + rangeOpts + '</select>'
      +   '<select id="lx-kind" class="lx-input">' + kindOpts + '</select>'
      +   '<select id="lx-severity" class="lx-input">' + sevOpts + '</select>'
      +   '<input id="lx-source" class="lx-input" type="text" placeholder="Source (SCOM…)" value="' + esc(state.filters.source) + '">'
      +   '<input id="lx-service" class="lx-input" type="text" placeholder="Service" value="' + esc(state.filters.service) + '">'
      +   '<input id="lx-trace" class="lx-input" type="text" placeholder="Trace ID" value="' + esc(state.filters.trace) + '">'
      +   '<button id="lx-search" class="lx-btn">Search</button>'
      + '</div>';
  }

  function summaryBar() {
    if (state.loading) { return '<div class="lx-summary">Searching…</div>'; }
    if (!state.result) { return '<div class="lx-summary">Set filters and search the telemetry index.</div>'; }
    var r = state.result;
    return '<div class="lx-summary"><strong>' + esc(r.total) + '</strong> result' + (r.total === 1 ? '' : 's')
      + ' · ' + esc(r.tookMs) + ' ms</div>';
  }

  function sevBadge(sev) {
    var s = (sev || '').toLowerCase();
    return '<span class="lx-sev lx-sev-' + esc(s) + '">' + esc(sev || '-') + '</span>';
  }

  function resultsTable(rows) {
    if (state.loading) { return '<div class="lx-empty">Loading…</div>'; }
    if (!state.result) { return ''; }
    if (!rows.length) { return '<div class="lx-empty">No matching telemetry in this window.</div>'; }
    var body = rows.map(function (d) {
      return '<tr>'
        + '<td class="lx-nowrap">' + fmtTime(d.timestamp) + '</td>'
        + '<td>' + sevBadge(d.severity) + '</td>'
        + '<td>' + esc(d.sourceSystem) + '</td>'
        + '<td>' + esc(d.serviceId) + '</td>'
        + '<td>' + esc(d.resourceId) + '</td>'
        + '<td class="lx-msg">' + esc(d.message) + '</td>'
        + '</tr>';
    }).join('');
    return ''
      + '<div class="lx-table-wrap"><table class="lx-table">'
      +   '<thead><tr><th>Time</th><th>Severity</th><th>Source</th><th>Service</th><th>Resource</th><th>Message</th></tr></thead>'
      +   '<tbody>' + body + '</tbody>'
      + '</table></div>';
  }

  function pagination() {
    if (!state.result || !state.result.total) { return ''; }
    var r = state.result;
    var from = r.page * r.size + 1;
    var to = Math.min((r.page + 1) * r.size, r.total);
    var hasPrev = r.page > 0;
    var hasNext = to < r.total;
    return ''
      + '<div class="lx-pager">'
      +   '<span>' + esc(from) + '–' + esc(to) + ' of ' + esc(r.total) + '</span>'
      +   '<button id="lx-prev" class="lx-btn lx-btn-ghost"' + (hasPrev ? '' : ' disabled') + '>Prev</button>'
      +   '<button id="lx-next" class="lx-btn lx-btn-ghost"' + (hasNext ? '' : ' disabled') + '>Next</button>'
      + '</div>';
  }

  function render() {
    var el = root();
    if (!el) { return; }
    var rows = (state.result && state.result.hits) ? state.result.hits : [];
    el.innerHTML = ''
      + '<div class="lx-wrap">'
      +   '<div class="lx-head"><h1>Log Explorer</h1>'
      +     '<p>Search telemetry across the custom index — alerts, logs, traces, metrics, changes. Scoped to your session country.</p></div>'
      +   filterBar()
      +   summaryBar()
      +   (state.error ? '<div class="lx-error">' + esc(state.error) + '</div>' : '')
      +   resultsTable(rows)
      +   pagination()
      + '</div>';
    wire(el);
  }

  function val(el, id) { var n = el.querySelector('#' + id); return n ? n.value : ''; }

  function captureFilters(el) {
    state.filters.text = val(el, 'lx-text');
    state.filters.rangeId = val(el, 'lx-range') || '24h';
    state.filters.kind = val(el, 'lx-kind');
    state.filters.severity = val(el, 'lx-severity');
    state.filters.source = val(el, 'lx-source');
    state.filters.service = val(el, 'lx-service');
    state.filters.trace = val(el, 'lx-trace');
  }

  function wire(el) {
    var search = el.querySelector('#lx-search');
    if (search) { search.addEventListener('click', function () { captureFilters(el); state.page = 0; runSearch(); }); }
    var text = el.querySelector('#lx-text');
    if (text) { text.addEventListener('keydown', function (e) { if (e.key === 'Enter') { captureFilters(el); state.page = 0; runSearch(); } }); }
    var prev = el.querySelector('#lx-prev');
    if (prev) { prev.addEventListener('click', function () { if (state.page > 0) { state.page -= 1; runSearch(); } }); }
    var next = el.querySelector('#lx-next');
    if (next) { next.addEventListener('click', function () { state.page += 1; runSearch(); }); }
  }

  function init() { state.page = 0; render(); runSearch(); }

  // Expose for parity; self-mount now (router re-runs page scripts on each navigation).
  window.LogExplorer = { init: init };
  init();
})();
