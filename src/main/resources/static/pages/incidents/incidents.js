/**
 * KFH AIOps Command Center — Incidents (Problems), reconstructed to the Dynatrace "Problem" organization
 * in KFH "Beyond Horizons" colors. Self-mounting, vanilla, uses the shared kfhx-* design system.
 *
 * List view  : faceted filter rail + timeline strip + dense incident table.
 * Detail view: header (status·id·severity·category·duration + Explain/Graph/Notify) → impact tile strip →
 *              tabs → Overview = Impact ↔ Root cause + Visual resolution path. Each card is a drill-down.
 *
 * Data below is the Fund Transfer / SAN-STORAGE worked example — illustrative until the correlation +
 * RCA + AI stages (Phases 2–4) light it up with live incidents.
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

  var state = { view: 'list', selected: null, filters: { status: '', severity: '', category: '', source: '' }, tab: 'overview', impactTab: 'journeys', eventEntity: null };

  function injectStyles() {
    if (document.getElementById('inc-styles')) return;
    var s = document.createElement('style');
    s.id = 'inc-styles';
    s.textContent = [
      '.inc-wrap{display:grid;grid-template-columns:220px 1fr;gap:16px;padding:18px;max-width:1560px;margin:0 auto;}',
      '.inc-rail{background:var(--surface-card);border:1px solid var(--surface-border);border-radius:12px;padding:14px;height:max-content;position:sticky;top:72px;}',
      '.inc-facet{margin-bottom:16px;}',
      '.inc-facet h4{font-size:.66rem;font-weight:700;text-transform:uppercase;letter-spacing:.06em;color:var(--text-muted);margin:0 0 8px;}',
      '.inc-facet button{display:flex;width:100%;justify-content:space-between;align-items:center;background:none;border:none;padding:5px 8px;border-radius:7px;font-size:.8rem;color:var(--text-secondary);cursor:pointer;text-align:left;}',
      '.inc-facet button:hover{background:var(--surface-off-white);}',
      '.inc-facet button.on{background:var(--kfh-primary-light);color:var(--kfh-primary-dark);font-weight:700;}',
      '.inc-timeline{display:flex;align-items:flex-end;gap:3px;height:70px;padding:12px 0 0;}',
      '.inc-timeline .b{flex:1;border-radius:2px 2px 0 0;background:var(--surface-border);}',
      '.inc-row{cursor:pointer;}',
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
      '.inc-erow{cursor:pointer;}.inc-erow.on td{background:var(--kfh-primary-light);}'
    ].join('');
    document.head.appendChild(s);
  }

  function timelineBars() {
    var out = '';
    for (var i = 0; i < 40; i++) {
      var active = i > 26;
      var hgt = 12 + ((i * 7) % 40) + (active ? 15 : 0);
      out += '<div class="b" style="height:' + hgt + 'px;background:' + (active ? '#ef4444' : 'var(--surface-border)') + ';"></div>';
    }
    return out;
  }

  function facet(title, key, values) {
    var cur = state.filters[key];
    var btns = ['<button class="' + (cur === '' ? 'on' : '') + '" data-facet="' + key + '" data-val="">All</button>'];
    values.forEach(function (v) {
      var count = INCIDENTS.filter(function (x) { return matchesExcept(x, key) && String(x[mapKey(key)]).indexOf(v) > -1 || (key === 'source' && x.sources.indexOf(v) > -1 && matchesExcept(x, key)); }).length;
      // simple count by direct field
      var c = INCIDENTS.filter(function (x) { return valueOf(x, key) === v || (key === 'source' && x.sources.indexOf(v) > -1); }).length;
      btns.push('<button class="' + (cur === v ? 'on' : '') + '" data-facet="' + key + '" data-val="' + esc(v) + '"><span>' + esc(v) + '</span><span style="color:var(--text-muted);font-weight:700;">' + c + '</span></button>');
    });
    return '<div class="inc-facet"><h4>' + esc(title) + '</h4>' + btns.join('') + '</div>';
  }

  function mapKey(key) { return key; }
  function valueOf(x, key) { return x[key]; }
  function matchesExcept() { return true; }

  function passes(x) {
    var f = state.filters;
    if (f.status && x.status !== f.status) return false;
    if (f.severity && x.severity !== f.severity) return false;
    if (f.category && x.category !== f.category) return false;
    if (f.source && x.sources.indexOf(f.source) < 0) return false;
    return true;
  }

  function renderList() {
    var rows = INCIDENTS.filter(passes).map(function (x) {
      var apps = x.apps.slice(0, 2).map(function (a) { return '<span class="inc-pill">' + esc(a) + '</span>'; }).join('') + (x.apps.length > 2 ? '<span class="inc-pill">+' + (x.apps.length - 2) + '</span>' : '');
      var statusBadge = x.status === 'Open'
        ? '<span class="kfhx-badge crit"><span class="kfhx-dot crit"></span>Open</span>'
        : '<span class="kfhx-badge ok">Closed</span>';
      var cls = x.classification === 'New'
        ? '<span class="kfh-chip kfh-chip-new">New</span>'
        : '<span class="kfh-chip kfh-chip-recurring">Recurring</span>';
      return '<tr class="inc-row" data-id="' + x.id + '">' +
        '<td class="mono">' + esc(x.id) + '</td>' +
        '<td><div style="font-weight:600;color:var(--text-primary);">' + esc(x.title) + '</div><div style="font-size:.72rem;color:var(--text-muted);">' + cls + ' · ' + esc(x.category) + '</div></td>' +
        '<td>' + statusBadge + '</td>' +
        '<td><span class="kfh-chip kfh-chip-' + x.severity.toLowerCase() + '">' + esc(x.severity) + '</span></td>' +
        '<td>' + apps + '</td>' +
        '<td><span class="inc-pill" title="Root cause">◈ ' + esc(x.rootCause.id) + '</span></td>' +
        '<td style="color:var(--text-muted);">' + esc(x.started) + '</td>' +
        '<td style="font-weight:600;">' + esc(x.duration) + '</td>' +
        '<td style="text-align:right;font-weight:700;">' + x.alerts + '</td>' +
        '</tr>';
    }).join('');

    var active = INCIDENTS.filter(function (x) { return x.status === 'Open'; }).length;

    mount.innerHTML =
      '<div class="inc-wrap">' +
        '<aside class="inc-rail">' +
          facet('Status', 'status', ['Open', 'Closed']) +
          facet('Severity', 'severity', ['Critical', 'High', 'Medium', 'Low']) +
          facet('Category', 'category', ['Availability', 'Performance', 'Resource contention']) +
          facet('Source', 'source', ['BMC', 'SCOM']) +
        '</aside>' +
        '<section>' +
          '<div class="kfhx-section-head">' +
            '<div class="kfhx-section-title">Incidents <span style="color:var(--color-critical);">' + active + ' active</span> <span style="color:var(--text-muted);font-weight:500;">/ ' + INCIDENTS.length + '</span></div>' +
            '<span class="kfhx-badge info">Illustrative — live from Phase 2–4</span>' +
          '</div>' +
          '<div class="kfhx-panel" style="padding:10px 16px;margin-bottom:14px;">' +
            '<div style="font-size:.72rem;color:var(--text-muted);">Active incidents over time (last 2h)</div>' +
            '<div class="inc-timeline">' + timelineBars() + '</div>' +
          '</div>' +
          '<div class="kfhx-panel" style="padding:0;overflow:hidden;">' +
            '<table class="kfhx-table"><thead><tr>' +
              '<th>ID</th><th>Name</th><th>Status</th><th>Severity</th><th>Impacted apps</th><th>Root cause</th><th>Started</th><th>Duration</th><th style="text-align:right;">Alerts</th>' +
            '</tr></thead><tbody>' + (rows || '<tr><td colspan="9" style="padding:30px;text-align:center;color:var(--text-muted);">No incidents match the filter.</td></tr>') + '</tbody></table>' +
          '</div>' +
        '</section>' +
      '</div>';

    mount.querySelectorAll('[data-facet]').forEach(function (b) {
      b.addEventListener('click', function () { state.filters[b.getAttribute('data-facet')] = b.getAttribute('data-val'); renderList(); });
    });
    mount.querySelectorAll('.inc-row').forEach(function (r) {
      r.addEventListener('click', function () { state.selected = r.getAttribute('data-id'); state.tab = 'overview'; render(); });
    });
  }

  function tile(label, value, icon) {
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

  // Error-rate chart with a red problem-window band over the incident interval.
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

  // "← All incidents" and other data-page links inside are intercepted by the router globally,
  // but the back link should just return to the list without a full navigation.
  mount.addEventListener('click', function (e) {
    var back = e.target.closest && e.target.closest('#inc-back');
    if (back) { e.preventDefault(); e.stopPropagation(); state.selected = null; render(); }
  }, true);

  render();
})();
