/**
 * KFH AIOps — Connections (data-source catalog), Dynatrace-style organization in KFH colors.
 * Catalog of our connector integrations → per-integration list of connection instances (add multiple)
 * → view-connection panel. This is a presentation layer over the real connector management: "+ Connection",
 * Configure and "Manage in Connectors" deep-link to the live #connectors page. Illustrative instances
 * until bound to the connector API. Our own integration set + descriptions.
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

  var ICONS = {
    server: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="7" rx="1.5"/><rect x="3" y="13" width="18" height="7" rx="1.5"/><path d="M7 7.5h.01M7 16.5h.01"/></svg>',
    cloud: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M17.5 19a4.5 4.5 0 0 0 .5-8.98A6 6 0 0 0 6.3 9.5 3.5 3.5 0 0 0 7 19h10.5z"/></svg>',
    activity: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M22 12h-4l-3 8-4-16-3 8H2"/></svg>',
    database: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><ellipse cx="12" cy="5" rx="8" ry="3"/><path d="M4 5v14c0 1.66 3.58 3 8 3s8-1.34 8-3V5M4 12c0 1.66 3.58 3 8 3s8-1.34 8-3"/></svg>'
  };

  // Our own integration catalog + descriptions (not a third-party list).
  var INTEGRATIONS = [
    { id: 'BMC', name: 'BMC Helix', category: 'Event Management', icon: 'server', desc: 'Ingest events from BMC Helix / TrueSight event management into the causal funnel.' },
    { id: 'SCOM', name: 'Microsoft SCOM', category: 'Infrastructure', icon: 'server', desc: 'Collect infrastructure alerts from System Center Operations Manager over WinRM / PowerShell.' },
    { id: 'VROPS', name: 'VMware vROps', category: 'Infrastructure', icon: 'cloud', desc: 'Pull virtualization metrics and alerts from vRealize Operations / Aria Operations.' },
    { id: 'APPDYNAMICS', name: 'AppDynamics', category: 'Application Performance', icon: 'activity', desc: 'Ingest application errors, slow transactions and violations from AppDynamics.' },
    { id: 'EMCO', name: 'EMCO Ping Monitor', category: 'Network', icon: 'database', desc: 'Network availability and latency from EMCO Ping Monitor (SQL Server source).' }
  ];

  // Illustrative connection instances per integration (real management lives in #connectors).
  var CONNECTIONS = {
    BMC: [
      { name: 'kfh-bmc-kw-prod', scope: 'KW · PROD', status: 'Healthy', sync: '2m ago', endpoint: 'https://kfh-itom.onbmc.com', auth: 'Access key' },
      { name: 'kfh-bmc-bh-prod', scope: 'BH · PROD', status: 'Disabled', sync: '10d ago', endpoint: 'https://kfh-bh-itom.onbmc.com', auth: 'Access key' }
    ],
    SCOM: [{ name: 'kfh-scom-kw-prod', scope: 'KW · PROD', status: 'Disabled', sync: '9d ago', endpoint: 'scom-mgmt.corp.kfh.local:5986', auth: 'WinRM / Kerberos' }],
    VROPS: [],
    APPDYNAMICS: [],
    EMCO: [{ name: 'kfh-emco-kw-prod', scope: 'KW · PROD', status: 'Down', sync: '23m ago', endpoint: 'dcvsamdb01:11433', auth: 'SQL credentials' }]
  };

  var state = { view: 'catalog', type: null, search: '', modal: null };

  function injectStyles() {
    if (document.getElementById('conn-styles')) return;
    var s = document.createElement('style');
    s.id = 'conn-styles';
    s.textContent = [
      '.conn-page{padding:18px;}',
      '.conn-list{background:var(--surface-card);border:1px solid var(--surface-border);border-radius:12px;overflow:hidden;}',
      '.conn-item{display:flex;align-items:center;gap:14px;padding:14px 18px;border-bottom:1px solid var(--surface-border-subtle);cursor:pointer;transition:background .12s;}',
      '.conn-item:last-child{border-bottom:none;}.conn-item:hover{background:var(--surface-off-white);}',
      '.conn-ic{width:42px;height:42px;border-radius:11px;display:inline-flex;align-items:center;justify-content:center;background:var(--kfh-primary-light);color:var(--kfh-primary);flex:0 0 42px;}',
      '.conn-ic svg{width:22px;height:22px;}',
      '.conn-tt{flex:1;min-width:0;}',
      '.conn-tt .nm{font-size:.95rem;font-weight:700;color:var(--text-primary);}',
      '.conn-tt .ds{font-size:.8rem;color:var(--text-muted);margin-top:2px;}',
      '.conn-count{font-size:.72rem;font-weight:700;color:var(--text-secondary);background:var(--surface-off-white);border:1px solid var(--surface-border);border-radius:999px;padding:2px 9px;}',
      '.conn-chev{color:var(--text-muted);}',
      '.conn-crumb{font-size:.8rem;color:var(--text-muted);margin-bottom:10px;}.conn-crumb a{color:var(--kfh-primary);font-weight:700;text-decoration:none;cursor:pointer;}',
      '.conn-toolbar{display:flex;align-items:center;gap:10px;margin:12px 0;}',
      '.conn-search{flex:1;max-width:320px;height:34px;border:1px solid var(--surface-border);border-radius:8px;padding:0 10px;font-size:.82rem;}',
      '.conn-search:focus{outline:none;border-color:var(--kfh-primary);box-shadow:0 0 0 3px rgba(0,99,74,.12);}',
      '.conn-add{height:34px;padding:0 14px;border:none;border-radius:8px;background:var(--kfh-primary);color:#fff;font-size:.82rem;font-weight:700;cursor:pointer;display:inline-flex;align-items:center;gap:6px;}',
      '.conn-ghost{height:34px;padding:0 12px;border:1px solid var(--surface-border);border-radius:8px;background:var(--surface-card);color:var(--text-secondary);font-size:.8rem;font-weight:600;cursor:pointer;text-decoration:none;display:inline-flex;align-items:center;}',
      '.conn-row{cursor:pointer;}',
      '.conn-badge{display:inline-flex;align-items:center;gap:5px;height:22px;padding:0 9px;border-radius:999px;font-size:.7rem;font-weight:700;}',
      '.conn-modal-ov{position:fixed;inset:0;background:rgba(15,23,42,.35);backdrop-filter:blur(2px);z-index:120;display:flex;align-items:center;justify-content:center;padding:20px;}',
      '.conn-modal{background:var(--surface-card);border-radius:14px;box-shadow:var(--shadow-modal);width:100%;max-width:560px;max-height:88vh;overflow:auto;}',
      '.conn-modal-h{display:flex;align-items:center;justify-content:space-between;padding:16px 18px;border-bottom:1px solid var(--surface-border);}',
      '.conn-modal-b{padding:16px 18px;}',
      '.conn-field{margin-bottom:12px;}.conn-field label{display:block;font-size:.72rem;font-weight:700;color:var(--text-muted);text-transform:uppercase;letter-spacing:.04em;margin-bottom:4px;}',
      '.conn-field input{width:100%;height:34px;border:1px solid var(--surface-border);border-radius:8px;padding:0 10px;font-size:.82rem;background:var(--surface-off-white);color:var(--text-secondary);}'
    ].join('');
    document.head.appendChild(s);
  }

  function statusBadge(st) {
    var m = {
      Healthy: ['var(--color-success-bg,#f0fdf4)', 'var(--color-success,#10b981)'],
      Down: ['var(--color-critical-bg,#fef2f2)', 'var(--color-critical,#ef4444)'],
      Pending: ['var(--color-warning-bg,#fffbeb)', 'var(--color-warning,#f59e0b)'],
      Disabled: ['var(--surface-off-white,#f1f5f9)', 'var(--text-muted,#64748b)']
    }[st] || ['var(--surface-off-white)', 'var(--text-muted)'];
    return '<span class="conn-badge" style="background:' + m[0] + ';color:' + m[1] + ';"><span class="kfhx-dot" style="background:' + m[1] + ';"></span>' + esc(st) + '</span>';
  }

  function catalog() {
    var rows = INTEGRATIONS.map(function (it) {
      var n = (CONNECTIONS[it.id] || []).length;
      return '<div class="conn-item" data-type="' + it.id + '">' +
        '<span class="conn-ic">' + ICONS[it.icon] + '</span>' +
        '<span class="conn-tt"><span class="nm">' + esc(it.name) + '</span><span class="ds">' + esc(it.desc) + '</span></span>' +
        '<span class="conn-count">' + n + ' connection' + (n === 1 ? '' : 's') + '</span>' +
        '<span class="conn-count" style="background:var(--kfh-primary-light);color:var(--kfh-primary-dark);border-color:rgba(0,99,74,.2);">' + esc(it.category) + '</span>' +
        '<span class="conn-chev">›</span></div>';
    }).join('');
    return '<div class="conn-page">' +
      '<div class="kfhx-section-head"><div><div class="kfhx-section-title" style="font-size:1.15rem;">Connections</div>' +
      '<div class="kfhx-section-sub">Data-source integrations. Open one to view or add its connections.</div></div>' +
      '<a class="conn-ghost" href="#connectors">Connectors inventory →</a></div>' +
      '<div class="conn-list">' + rows + '</div></div>';
  }

  function integration(it) {
    var list = (CONNECTIONS[it.id] || []).filter(function (c) {
      return !state.search || c.name.toLowerCase().indexOf(state.search.toLowerCase()) > -1;
    });
    var rows = list.length ? list.map(function (c) {
      return '<tr class="conn-row" data-conn="' + esc(c.name) + '">' +
        '<td style="font-weight:600;color:var(--text-primary);">' + esc(c.name) + '</td>' +
        '<td class="mono">' + esc(c.scope) + '</td>' +
        '<td>' + statusBadge(c.status) + '</td>' +
        '<td style="color:var(--text-muted);">' + esc(c.sync) + '</td>' +
        '<td style="text-align:right;color:var(--text-muted);">⋮</td></tr>';
    }).join('') : '<tr><td colspan="5" style="padding:26px;text-align:center;color:var(--text-muted);">No connections yet. Use “+ Connection” to add one.</td></tr>';

    return '<div class="conn-page">' +
      '<div class="conn-crumb"><a data-crumb="catalog">Connections</a> › ' + esc(it.name) + '</div>' +
      '<div style="display:flex;align-items:center;gap:14px;">' +
        '<span class="conn-ic" style="width:52px;height:52px;">' + ICONS[it.icon] + '</span>' +
        '<div><div class="kfhx-section-title" style="font-size:1.2rem;">' + esc(it.name) + '</div>' +
        '<div class="kfhx-section-sub">' + esc(it.desc) + '</div></div></div>' +
      '<div class="conn-toolbar"><input class="conn-search" id="conn-search" placeholder="Search by name" value="' + esc(state.search) + '"/>' +
        '<div style="margin-left:auto;display:flex;gap:8px;"><a class="conn-ghost" href="#connectors">Manage in Connectors →</a>' +
        '<button class="conn-add" id="conn-add">+ Connection</button></div></div>' +
      '<div class="conn-list" style="padding:0;">' +
        '<table class="kfhx-table"><thead><tr><th>Connection</th><th>Scope</th><th>Status</th><th>Last sync</th><th style="text-align:right;"></th></tr></thead>' +
        '<tbody>' + rows + '</tbody></table></div></div>';
  }

  function viewModal(it, c) {
    return '<div class="conn-modal-ov" id="conn-ov"><div class="conn-modal">' +
      '<div class="conn-modal-h"><div style="display:flex;align-items:center;gap:10px;"><span class="conn-ic" style="width:34px;height:34px;">' + ICONS[it.icon] + '</span>' +
        '<div><div style="font-weight:700;">' + esc(c.name) + '</div><div style="font-size:.75rem;color:var(--text-muted);">' + esc(it.name) + ' connection</div></div></div>' +
        '<button id="conn-close" class="conn-ghost" style="border:none;font-size:1.1rem;">×</button></div>' +
      '<div class="conn-modal-b">' +
        '<div style="margin-bottom:12px;">' + statusBadge(c.status) + ' <span style="font-size:.75rem;color:var(--text-muted);margin-left:6px;">last sync ' + esc(c.sync) + '</span></div>' +
        field('Connection name', c.name) + field('Type', it.name) + field('Scope', c.scope) +
        field('Authentication', c.auth) + field('Endpoint', c.endpoint) + field('Secret', '••••••••••••') +
        '<div style="display:flex;justify-content:flex-end;gap:8px;margin-top:14px;">' +
          '<a class="conn-ghost" href="#connectors">Edit in Connectors →</a>' +
          '<button id="conn-close2" class="conn-add">Close</button></div>' +
      '</div></div></div>';
  }

  function field(label, val) {
    return '<div class="conn-field"><label>' + esc(label) + '</label><input value="' + esc(val) + '" readonly></div>';
  }

  function render() {
    injectStyles();
    var it = state.type ? INTEGRATIONS.filter(function (x) { return x.id === state.type; })[0] : null;
    var body = (state.view === 'integration' && it) ? integration(it) : catalog();
    var modalHtml = '';
    if (state.modal && it) {
      var c = (CONNECTIONS[it.id] || []).filter(function (x) { return x.name === state.modal; })[0];
      if (c) modalHtml = viewModal(it, c);
    }
    mount.innerHTML = body + modalHtml;
    wire();
  }

  function wire() {
    mount.querySelectorAll('[data-type]').forEach(function (r) {
      r.addEventListener('click', function () { state.type = r.getAttribute('data-type'); state.view = 'integration'; state.search = ''; render(); });
    });
    var crumb = mount.querySelector('[data-crumb]');
    if (crumb) crumb.addEventListener('click', function () { state.view = 'catalog'; state.type = null; render(); });
    var search = mount.querySelector('#conn-search');
    if (search) search.addEventListener('input', function () { state.search = search.value; render(); var f = mount.querySelector('#conn-search'); if (f) { f.focus(); f.setSelectionRange(f.value.length, f.value.length); } });
    var add = mount.querySelector('#conn-add');
    if (add) add.addEventListener('click', function () { if (window.Router) Router.navigate('connectors'); });
    mount.querySelectorAll('[data-conn]').forEach(function (r) {
      r.addEventListener('click', function () { state.modal = r.getAttribute('data-conn'); render(); });
    });
    var close = mount.querySelector('#conn-close'); var close2 = mount.querySelector('#conn-close2'); var ov = mount.querySelector('#conn-ov');
    function closeModal() { state.modal = null; render(); }
    if (close) close.addEventListener('click', closeModal);
    if (close2) close2.addEventListener('click', closeModal);
    if (ov) ov.addEventListener('click', function (e) { if (e.target === ov) closeModal(); });
  }

  render();
})();
