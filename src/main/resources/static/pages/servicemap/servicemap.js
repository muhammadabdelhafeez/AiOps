/**
 * KFH AIOps — Service Map (Business Application Topology).
 * Flow view: each business application as a component flow bound to assets.
 * Graph view: a problem-graph / blast-radius topology with a left filter panel (find nodes, node-type
 * facets with counts, node-status counts, layout toggle, hide-healthy) — the operator view of the
 * Neo4j topology the correlation walks. Illustrative KFH seed until live topology (Stage 4).
 */
(function () {
  'use strict';

  function root() { return document.getElementById('page-root'); }

  function esc(v) {
    if (window.KFHUtils && KFHUtils.escapeHtml) { return KFHUtils.escapeHtml(v == null ? '' : String(v)); }
    return String(v == null ? '' : v).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  // Illustrative KFH topology seed (replaced by live Neo4j data in Stage 4).
  var APPLICATIONS = [
    {
      id: 'fund-transfer', name: 'Fund Transfer', criticality: 'Critical', owner: 'Digital Channels',
      journeys: ['Mobile Transfer', 'Net Banking Transfer'],
      flow: [
        { stage: 'Channel', name: 'Mobile Banking', assets: ['MOB-APP-KW'], health: 'ok' },
        { stage: 'Gateway', name: 'API Gateway', assets: ['API-GW-01', 'API-GW-02'], health: 'ok', shared: true },
        { stage: 'Service', name: 'Transfer Service', assets: ['SVC-TRANSFER'], health: 'warn' },
        { stage: 'Database', name: 'Oracle Core', assets: ['ORACLE-CORE-01'], health: 'warn', shared: true },
        { stage: 'Storage', name: 'SAN Storage', assets: ['SAN-STORAGE-02'], health: 'crit', shared: true }
      ]
    },
    {
      id: 'kfhonline', name: 'KFHOnline', criticality: 'Critical', owner: 'Digital Channels',
      journeys: ['Login', 'Account Summary'],
      flow: [
        { stage: 'Channel', name: 'Web Portal', assets: ['WEB-ONLINE-KW'], health: 'ok' },
        { stage: 'Gateway', name: 'API Gateway', assets: ['API-GW-01', 'API-GW-02'], health: 'ok', shared: true },
        { stage: 'Service', name: 'Auth Service', assets: ['SVC-AUTH'], health: 'ok' },
        { stage: 'Directory', name: 'LDAP / IAM', assets: ['LDAP-01'], health: 'ok' },
        { stage: 'Database', name: 'Oracle Core', assets: ['ORACLE-CORE-01'], health: 'warn', shared: true }
      ]
    },
    {
      id: 'wamd', name: 'WAMD', criticality: 'High', owner: 'Payments',
      journeys: ['Instant Payment'],
      flow: [
        { stage: 'Channel', name: 'Mobile Banking', assets: ['MOB-APP-KW'], health: 'ok' },
        { stage: 'Gateway', name: 'API Gateway', assets: ['API-GW-01', 'API-GW-02'], health: 'ok', shared: true },
        { stage: 'Service', name: 'Payment Service', assets: ['SVC-PAYMENT'], health: 'ok' },
        { stage: 'Database', name: 'Oracle Core', assets: ['ORACLE-CORE-01'], health: 'warn', shared: true },
        { stage: 'External', name: 'KNET Gateway', assets: ['KNET-EXT'], health: 'ok' }
      ]
    }
  ];

  var HEALTH = { ok: { label: 'Healthy', cls: 'sm-ok' }, warn: { label: 'Degraded', cls: 'sm-warn' }, crit: { label: 'Critical', cls: 'sm-crit' } };

  function node(comp) {
    var h = HEALTH[comp.health] || HEALTH.ok;
    var assets = (comp.assets || []).map(function (a) { return '<span class="sm-asset">' + esc(a) + '</span>'; }).join('');
    return '<div class="sm-node ' + h.cls + '">' +
      '<div class="sm-node-head"><span class="sm-dot"></span><span class="sm-stage">' + esc(comp.stage) + '</span>' +
      (comp.shared ? '<span class="sm-shared" title="Shared across applications">shared</span>' : '') + '</div>' +
      '<div class="sm-node-name">' + esc(comp.name) + '</div><div class="sm-assets">' + assets + '</div></div>';
  }

  function appCard(app) {
    var flow = app.flow.map(node).join('<div class="sm-arrow">&rarr;</div>');
    return '<section class="sm-card"><header class="sm-card-head"><div>' +
      '<h3 class="sm-app-name">' + esc(app.name) + '</h3>' +
      '<div class="sm-app-meta">' + esc(app.owner) + ' &middot; Journeys: ' + esc(app.journeys.join(', ')) + '</div></div>' +
      '<span class="sm-crit-badge sm-crit-' + esc(app.criticality.toLowerCase()) + '">' + esc(app.criticality) + '</span>' +
      '</header><div class="sm-flow">' + flow + '</div></section>';
  }

  var VIEW = { mode: 'flow' };
  var GRAPHF = { layout: 'layered', find: '', hideHealthy: false, off: {} };

  var GN = [
    { id: 'SAN', tier: 0, label: 'SAN-STORAGE-02', sub: 'Storage', h: 'crit', root: true },
    { id: 'ORA', tier: 1, label: 'ORACLE-CORE-01', sub: 'Database', h: 'warn' },
    { id: 'TRF', tier: 2, label: 'Transfer Service', sub: 'Service', h: 'warn' },
    { id: 'PAY', tier: 2, label: 'Payment Service', sub: 'Service', h: 'ok' },
    { id: 'GW', tier: 3, label: 'API Gateway', sub: 'Gateway', h: 'ok' },
    { id: 'FT', tier: 4, label: 'Fund Transfer', sub: 'Journey', h: 'crit' },
    { id: 'WM', tier: 4, label: 'WAMD', sub: 'Journey', h: 'warn' },
    { id: 'KO', tier: 4, label: 'KFHOnline', sub: 'Journey', h: 'ok' }
  ];
  var GE = [
    { f: 'SAN', t: 'ORA', s: 'crit' }, { f: 'ORA', t: 'TRF', s: 'crit' }, { f: 'ORA', t: 'PAY', s: 'ok' },
    { f: 'TRF', t: 'GW', s: 'crit' }, { f: 'PAY', t: 'GW', s: 'ok' },
    { f: 'GW', t: 'FT', s: 'crit' }, { f: 'GW', t: 'WM', s: 'warn' }, { f: 'GW', t: 'KO', s: 'ok' }
  ];
  var TYPES = ['Storage', 'Database', 'Service', 'Gateway', 'Journey'];

  function injectGraphStyles() {
    if (document.getElementById('sm-graph-styles')) return;
    var s = document.createElement('style');
    s.id = 'sm-graph-styles';
    s.textContent = [
      '.sm-graph-wrap{display:grid;grid-template-columns:250px 1fr;gap:14px;align-items:start;}',
      '.sm-gpanel{background:var(--surface-card);border:1px solid var(--surface-border);border-radius:12px;padding:14px;position:sticky;top:72px;}',
      '.sm-gp-title{font-weight:700;font-size:.95rem;color:var(--text-primary);}',
      '.sm-gp-sub{font-size:.72rem;color:var(--text-muted);margin:2px 0 10px;}',
      '.sm-gp-find input{width:100%;height:34px;padding:0 10px;border:1px solid var(--surface-border);border-radius:8px;font-size:.8rem;}',
      '.sm-gp-find input:focus{outline:none;border-color:var(--kfh-primary);box-shadow:0 0 0 3px rgba(0,99,74,.12);}',
      '.sm-gp-group{border-top:1px solid var(--surface-border-subtle);padding:10px 0 4px;}',
      '.sm-gp-group h5{margin:0 0 6px;font-size:.64rem;text-transform:uppercase;letter-spacing:.06em;color:var(--text-muted);font-weight:700;}',
      '.sm-gp-item{display:flex;align-items:center;gap:8px;padding:4px 0;font-size:.8rem;color:var(--text-secondary);cursor:pointer;}',
      '.sm-gp-item .c{margin-left:auto;font-weight:700;color:var(--text-muted);}',
      '.sm-graph-canvas{background:var(--surface-card);border:1px solid var(--surface-border);border-radius:12px;padding:12px;overflow:auto;}',
      '.sm-gp-toggle{display:flex;align-items:center;gap:8px;font-size:.8rem;color:var(--text-secondary);padding:8px 0 2px;cursor:pointer;}'
    ].join('');
    document.head.appendChild(s);
  }

  function counts() {
    var t = {}, st = { root: 0, affected: 0, healthy: 0 };
    TYPES.forEach(function (k) { t[k] = 0; });
    GN.forEach(function (n) {
      t[n.sub] = (t[n.sub] || 0) + 1;
      if (n.root) st.root++; else if (n.h === 'ok') st.healthy++; else st.affected++;
    });
    return { types: t, status: st };
  }

  function visibleNodes() {
    return GN.filter(function (n) {
      if (GRAPHF.off[n.sub]) return false;
      if (GRAPHF.hideHealthy && n.h === 'ok' && !n.root) return false;
      return true;
    });
  }

  function layout(nodes) {
    var byTier = {};
    nodes.forEach(function (n) { (byTier[n.tier] = byTier[n.tier] || []).push(n); });
    nodes.forEach(function (n) {
      var arr = byTier[n.tier], i = arr.indexOf(n), c = arr.length;
      if (GRAPHF.layout === 'vertical') { n.x = 500 + (i - (c - 1) / 2) * 165; n.y = 80 + n.tier * 105; }
      else { n.x = 110 + n.tier * 200; n.y = 250 + (i - (c - 1) / 2) * 118; }
    });
  }

  function svgGraph() {
    var nodes = visibleNodes();
    layout(nodes);
    var vis = {}; nodes.forEach(function (n) { vis[n.id] = n; });
    var q = GRAPHF.find.trim().toLowerCase();
    var dim = function (n) { return q && n.label.toLowerCase().indexOf(q) < 0 && n.sub.toLowerCase().indexOf(q) < 0; };

    var edges = GE.filter(function (e) { return vis[e.f] && vis[e.t]; }).map(function (e) {
      var a = vis[e.f], b = vis[e.t];
      var col = e.s === 'crit' ? '#ef4444' : e.s === 'warn' ? '#f59e0b' : '#cbd5e1';
      var op = (dim(a) || dim(b)) ? 0.12 : (e.s === 'ok' ? 0.5 : 0.95);
      return '<path d="M' + a.x + ',' + a.y + ' C' + ((a.x + b.x) / 2) + ',' + a.y + ' ' + ((a.x + b.x) / 2) + ',' + b.y + ' ' + b.x + ',' + b.y +
        '" fill="none" stroke="' + col + '" stroke-width="' + (e.s === 'crit' ? 2.5 : e.s === 'warn' ? 2 : 1.5) + '" opacity="' + op + '"/>';
    }).join('');

    var els = nodes.map(function (n) {
      var stroke = n.h === 'crit' ? '#ef4444' : n.h === 'warn' ? '#f59e0b' : '#cbd5e1';
      var fill = n.h === 'crit' ? '#fef2f2' : n.h === 'warn' ? '#fffbeb' : '#ffffff';
      var d = n.h === 'crit' ? '#dc2626' : n.h === 'warn' ? '#d97706' : '#16a34a';
      var op = dim(n) ? 0.2 : 1;
      var ring = n.root ? '<circle cx="' + n.x + '" cy="' + n.y + '" r="31" fill="none" stroke="#ef4444" stroke-width="1.5" opacity="' + (0.45 * op) + '"/>' : '';
      return '<g opacity="' + op + '">' + ring +
        '<circle cx="' + n.x + '" cy="' + n.y + '" r="24" fill="' + fill + '" stroke="' + stroke + '" stroke-width="' + (n.root ? 3 : 1.5) + '"/>' +
        '<circle cx="' + n.x + '" cy="' + (n.y - 1) + '" r="5" fill="' + d + '"/>' +
        '<text x="' + n.x + '" y="' + (n.y + 44) + '" text-anchor="middle" font-size="11" font-weight="700" fill="#1e293b">' + esc(n.label) + '</text>' +
        '<text x="' + n.x + '" y="' + (n.y + 58) + '" text-anchor="middle" font-size="9" fill="' + (n.root ? '#dc2626' : '#94a3b8') + '" font-weight="' + (n.root ? '700' : '400') + '">' + esc(n.sub) + (n.root ? ' · ROOT CAUSE' : '') + '</text></g>';
    }).join('');

    return '<svg viewBox="0 0 1000 620" style="width:100%;min-width:820px;height:auto;">' + edges + els + '</svg>';
  }

  function filterPanel() {
    var c = counts();
    var typeItems = TYPES.map(function (t) {
      return '<label class="sm-gp-item"><input type="checkbox" data-type="' + esc(t) + '" ' + (GRAPHF.off[t] ? '' : 'checked') + '/>' +
        '<span>' + esc(t) + '</span><span class="c">' + (c.types[t] || 0) + '</span></label>';
    }).join('');
    var statusItems =
      '<div class="sm-gp-item"><span class="kfhx-dot crit"></span><span>Root cause</span><span class="c">' + c.status.root + '</span></div>' +
      '<div class="sm-gp-item"><span class="kfhx-dot warn"></span><span>Affected</span><span class="c">' + c.status.affected + '</span></div>' +
      '<div class="sm-gp-item"><span class="kfhx-dot"></span><span>Healthy</span><span class="c">' + c.status.healthy + '</span></div>';
    return '<aside class="sm-gpanel">' +
      '<div class="sm-gp-title">Problem graph</div>' +
      '<div class="sm-gp-sub">See what a problem affects and how far it spreads.</div>' +
      '<div class="sm-gp-find"><input id="sm-find" type="search" placeholder="Find nodes by name or ID" value="' + esc(GRAPHF.find) + '"/></div>' +
      '<div class="sm-gp-group"><h5>Node types</h5>' + typeItems + '</div>' +
      '<div class="sm-gp-group"><h5>Node status</h5>' + statusItems + '</div>' +
      '<div class="sm-gp-group"><h5>Graph layout</h5>' +
        '<div class="kfhx-segment"><button data-layout="layered" class="' + (GRAPHF.layout === 'layered' ? 'active' : '') + '">Layered</button>' +
        '<button data-layout="vertical" class="' + (GRAPHF.layout === 'vertical' ? 'active' : '') + '">Vertical</button></div>' +
      '</div>' +
      '<label class="sm-gp-toggle"><input type="checkbox" id="sm-hidehealthy" ' + (GRAPHF.hideHealthy ? 'checked' : '') + '/> Hide healthy nodes</label>' +
      '</aside>';
  }

  function graphView() {
    injectGraphStyles();
    return '<div class="sm-graph-wrap">' + filterPanel() +
      '<div class="sm-graph-canvas">' +
        '<div style="font-size:.8rem;color:var(--text-muted);margin-bottom:6px;">Blast radius — failing path (red) from the root-cause asset up to the impacted journeys.</div>' +
        svgGraph() +
      '</div></div>';
  }

  function render() {
    var mount = root();
    if (!mount) { return; }
    var toggle =
      '<div class="kfhx-segment">' +
        '<button data-view="flow" class="' + (VIEW.mode === 'flow' ? 'active' : '') + '">Flow</button>' +
        '<button data-view="graph" class="' + (VIEW.mode === 'graph' ? 'active' : '') + '">Graph</button>' +
      '</div>';
    var body = VIEW.mode === 'graph' ? graphView() : APPLICATIONS.map(appCard).join('');
    mount.innerHTML =
      '<div class="sm-page">' +
        '<div class="sm-banner"><strong>Application Topology.</strong> Business applications modelled as component flows bound to assets. ' +
          'An alert’s CI resolves to the application(s) it impacts; shared components let one root cause impact multiple applications. ' +
          '<span class="sm-stage-tag">Live Neo4j topology arrives in Stage 4.</span></div>' +
        '<div style="display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap;margin-bottom:14px;">' +
          '<div class="sm-legend" style="margin:0;">' +
            '<span class="sm-legend-item"><span class="sm-dot sm-ok"></span> Healthy</span>' +
            '<span class="sm-legend-item"><span class="sm-dot sm-warn"></span> Degraded</span>' +
            '<span class="sm-legend-item"><span class="sm-dot sm-crit"></span> Critical</span>' +
            '<span class="sm-legend-item"><span class="sm-shared">shared</span> Shared (multi-application)</span>' +
          '</div>' + toggle +
        '</div>' + body +
      '</div>';

    mount.querySelectorAll('[data-view]').forEach(function (b) {
      b.addEventListener('click', function () { VIEW.mode = b.getAttribute('data-view'); render(); });
    });

    if (VIEW.mode === 'graph') {
      mount.querySelectorAll('[data-type]').forEach(function (cb) {
        cb.addEventListener('change', function () { GRAPHF.off[cb.getAttribute('data-type')] = !cb.checked; render(); });
      });
      mount.querySelectorAll('[data-layout]').forEach(function (b) {
        b.addEventListener('click', function () { GRAPHF.layout = b.getAttribute('data-layout'); render(); });
      });
      var hh = mount.querySelector('#sm-hidehealthy');
      if (hh) hh.addEventListener('change', function () { GRAPHF.hideHealthy = hh.checked; render(); });
      var find = mount.querySelector('#sm-find');
      if (find) {
        find.addEventListener('input', function () {
          GRAPHF.find = find.value;
          render();
          var f = root().querySelector('#sm-find');
          if (f) { f.focus(); f.setSelectionRange(f.value.length, f.value.length); }
        });
      }
    }
  }

  render();
})();
