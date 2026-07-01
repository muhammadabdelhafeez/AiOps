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

  function render() {
    var mount = root();
    if (!mount) { return; }
    mount.innerHTML = '' +
      '<div class="sm-page">' +
        '<div class="sm-banner">' +
          '<strong>Application Topology.</strong> Each business application is modelled as a component flow bound to assets. ' +
          'During analysis, an alert’s CI is matched to a bound asset to resolve which application(s) it impacts — ' +
          'shared components (marked <em>shared</em>) let one root cause impact multiple applications. ' +
          '<span class="sm-stage-tag">Live Neo4j topology + real-time health arrive in Stage 4.</span>' +
        '</div>' +
        '<div class="sm-legend">' +
          '<span class="sm-legend-item"><span class="sm-dot sm-ok"></span> Healthy</span>' +
          '<span class="sm-legend-item"><span class="sm-dot sm-warn"></span> Degraded</span>' +
          '<span class="sm-legend-item"><span class="sm-dot sm-crit"></span> Critical</span>' +
          '<span class="sm-legend-item"><span class="sm-shared">shared</span> Shared component (multi-application)</span>' +
        '</div>' +
        APPLICATIONS.map(appCard).join('') +
      '</div>';
  }

  render();
})();
