/**
 * KFH AIOps — Service Map (Business Application Topology).
 * Self-mounting page. Renders each business application as a left-to-right COMPONENT FLOW
 * (Channel → Gateway → Service → Database → Storage …), with the concrete ASSETS bound to each
 * component. This is the model Stage 4 correlation walks: an alert's CI (resourceId) is matched to a
 * bound asset, which resolves to the application(s) it impacts — including shared components that make
 * one root cause impact multiple applications.
 *
 * The topology below is an illustrative KFH seed. Stage 4 replaces it with the live Neo4j graph and
 * overlays real-time health from the ingested BMC + SCOM alerts.
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

  var HEALTH = {
    ok: { label: 'Healthy', cls: 'sm-ok' },
    warn: { label: 'Degraded', cls: 'sm-warn' },
    crit: { label: 'Critical', cls: 'sm-crit' }
  };

  function node(comp) {
    var h = HEALTH[comp.health] || HEALTH.ok;
    var assets = (comp.assets || []).map(function (a) {
      return '<span class="sm-asset">' + esc(a) + '</span>';
    }).join('');
    return '' +
      '<div class="sm-node ' + h.cls + '">' +
        '<div class="sm-node-head">' +
          '<span class="sm-dot"></span>' +
          '<span class="sm-stage">' + esc(comp.stage) + '</span>' +
          (comp.shared ? '<span class="sm-shared" title="Shared across applications">shared</span>' : '') +
        '</div>' +
        '<div class="sm-node-name">' + esc(comp.name) + '</div>' +
        '<div class="sm-assets">' + assets + '</div>' +
      '</div>';
  }

  function appCard(app) {
    var flow = app.flow.map(node).join('<div class="sm-arrow">&rarr;</div>');
    return '' +
      '<section class="sm-card">' +
        '<header class="sm-card-head">' +
          '<div>' +
            '<h3 class="sm-app-name">' + esc(app.name) + '</h3>' +
            '<div class="sm-app-meta">' + esc(app.owner) + ' &middot; Journeys: ' + esc(app.journeys.join(', ')) + '</div>' +
          '</div>' +
          '<span class="sm-crit-badge sm-crit-' + esc(app.criticality.toLowerCase()) + '">' + esc(app.criticality) + '</span>' +
        '</header>' +
        '<div class="sm-flow">' + flow + '</div>' +
      '</section>';
  }

  var VIEW = { mode: 'flow' };

  // Smartscape-style blast-radius graph (illustrative until live Neo4j topology).
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

  function graphView() {
    var byTier = {};
    GN.forEach(function (n) { (byTier[n.tier] = byTier[n.tier] || []).push(n); });
    GN.forEach(function (n) {
      var arr = byTier[n.tier];
      n.x = 100 + n.tier * 200;
      n.y = 230 + (arr.indexOf(n) - (arr.length - 1) / 2) * 112;
    });
    var byId = {}; GN.forEach(function (n) { byId[n.id] = n; });

    var edges = GE.map(function (e) {
      var a = byId[e.f], b = byId[e.t];
      var col = e.s === 'crit' ? '#ef4444' : e.s === 'warn' ? '#f59e0b' : '#cbd5e1';
      var mx = (a.x + b.x) / 2;
      return '<path d="M' + (a.x + 26) + ',' + a.y + ' C' + mx + ',' + a.y + ' ' + mx + ',' + b.y + ' ' + (b.x - 26) + ',' + b.y +
        '" fill="none" stroke="' + col + '" stroke-width="' + (e.s === 'crit' ? 2.5 : e.s === 'warn' ? 2 : 1.5) + '" opacity="' + (e.s === 'ok' ? 0.5 : 0.95) + '"/>';
    }).join('');

    var nodes = GN.map(function (n) {
      var stroke = n.h === 'crit' ? '#ef4444' : n.h === 'warn' ? '#f59e0b' : '#cbd5e1';
      var fill = n.h === 'crit' ? '#fef2f2' : n.h === 'warn' ? '#fffbeb' : '#ffffff';
      var dot = n.h === 'crit' ? '#dc2626' : n.h === 'warn' ? '#d97706' : '#16a34a';
      var ring = n.root ? '<circle cx="' + n.x + '" cy="' + n.y + '" r="31" fill="none" stroke="#ef4444" stroke-width="1.5" opacity="0.45"/>' : '';
      return ring +
        '<circle cx="' + n.x + '" cy="' + n.y + '" r="24" fill="' + fill + '" stroke="' + stroke + '" stroke-width="' + (n.root ? 3 : 1.5) + '"/>' +
        '<circle cx="' + n.x + '" cy="' + (n.y - 1) + '" r="5" fill="' + dot + '"/>' +
        '<text x="' + n.x + '" y="' + (n.y + 44) + '" text-anchor="middle" font-size="11" font-weight="700" fill="#1e293b">' + esc(n.label) + '</text>' +
        '<text x="' + n.x + '" y="' + (n.y + 58) + '" text-anchor="middle" font-size="9" fill="' + (n.root ? '#dc2626' : '#94a3b8') + '" font-weight="' + (n.root ? '700' : '400') + '">' + esc(n.sub) + (n.root ? ' · ROOT CAUSE' : '') + '</text>';
    }).join('');

    return '<section class="sm-card">' +
      '<div style="font-size:0.8rem;color:var(--text-muted,#64748b);margin-bottom:8px;">Blast radius — the failing path (red) from the root-cause asset up through the topology to the impacted journeys.</div>' +
      '<div style="overflow-x:auto;"><svg viewBox="0 0 1000 480" style="width:100%;min-width:900px;height:auto;">' + edges + nodes + '</svg></div>' +
      '</section>';
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
    mount.innerHTML = '' +
      '<div class="sm-page">' +
        '<div class="sm-banner">' +
          '<strong>Application Topology.</strong> Each business application is modelled as a component flow bound to assets. ' +
          'During analysis, an alert’s CI is matched to a bound asset to resolve which application(s) it impacts — ' +
          'shared components (marked <em>shared</em>) let one root cause impact multiple applications. ' +
          '<span class="sm-stage-tag">Live Neo4j topology + real-time health arrive in Stage 4.</span>' +
        '</div>' +
        '<div style="display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap;margin-bottom:14px;">' +
          '<div class="sm-legend" style="margin:0;">' +
            '<span class="sm-legend-item"><span class="sm-dot sm-ok"></span> Healthy</span>' +
            '<span class="sm-legend-item"><span class="sm-dot sm-warn"></span> Degraded</span>' +
            '<span class="sm-legend-item"><span class="sm-dot sm-crit"></span> Critical</span>' +
            '<span class="sm-legend-item"><span class="sm-shared">shared</span> Shared (multi-application)</span>' +
          '</div>' + toggle +
        '</div>' +
        body +
      '</div>';

    mount.querySelectorAll('[data-view]').forEach(function (b) {
      b.addEventListener('click', function () { VIEW.mode = b.getAttribute('data-view'); render(); });
    });
  }

  render();
})();
