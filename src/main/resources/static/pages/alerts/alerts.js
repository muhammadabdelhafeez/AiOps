/**
 * KFH AIOps Command Center — Alert Explorer, reconstructed to Dynatrace's "Events" organization
 * in KFH "Beyond Horizons" colors. Self-mounting, vanilla, uses the shared kfhx-* design system.
 *
 * Faceted rail (Severity/Source/Kind) + count-labeled severity tabs + volume timeline + dense event
 * stream (rich NL message, dedup count, linked incident) + master-detail with a properties key-value
 * table. Modelled on the canonical TelemetryDocument (BMC + SCOM). Illustrative data until the page
 * binds live to the Custom Index (/logs/search, ALERTS kind).
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

  // ---- Illustrative alerts (replaced by live index reads in later phases) ----
  var ALERTS = [
    { id: 'a1', time: 'Jul 1, 10:00:12', sev: 'Critical', source: 'SCOM', kind: 'ALERTS', entity: 'SAN-STORAGE-02', etype: 'Storage.SAN.Lun', cat: 'PerformanceHealth', msg: 'LUN-04 write latency rose from 2 ms to 82 ms (20×) on SAN-STORAGE-02.', count: 5, inc: 'INC-20260701-014', attrs: { errorCode: 'SAN.WriteLatency.High', host: 'san-ctrl-02', priority: 'High', resolutionState: 'New' } },
    { id: 'a2', time: 'Jul 1, 10:14:03', sev: 'High', source: 'SCOM', kind: 'ALERTS', entity: 'ORACLE-CORE-01', etype: 'OracleDatabase', cat: 'DatabaseHealth', msg: 'Oracle buffer-busy waits increased +480% on ORACLE-CORE-01.', count: 12, inc: 'INC-20260701-014', attrs: { errorCode: 'buffer_busy_waits', host: 'db-host-07', priority: 'High' } },
    { id: 'a3', time: 'Jul 1, 10:15:41', sev: 'High', source: 'BMC', kind: 'ALERTS', entity: 'Transfer Service', etype: 'CoreBanking', cat: 'ApplicationError', msg: 'Transfer Service DB timeouts: error_count reached 1,247 (baseline 3/min).', count: 1247, inc: 'INC-20260701-014', attrs: { errorCode: 'DB_TIMEOUT', alertName: 'DB_TIMEOUT', impactedService: 'Fund Transfer', status: 'OPEN' } },
    { id: 'a4', time: 'Jul 1, 10:16:20', sev: 'Critical', source: 'BMC', kind: 'ALERTS', entity: 'API-GATEWAY', etype: 'ApiGateway', cat: 'Availability', msg: 'API Gateway /transfer returned HTTP 502 at 412/min (baseline 0).', count: 412, inc: 'INC-20260701-014', attrs: { errorCode: 'http_502', alertName: 'GATEWAY_5XX', status: 'OPEN' } },
    { id: 'a5', time: 'Jul 1, 09:42:10', sev: 'High', source: 'SCOM', kind: 'ALERTS', entity: 'LDAP-01', etype: 'Directory', cat: 'PerformanceHealth', msg: 'LDAP bind time increased +300% on LDAP-01.', count: 3, inc: 'INC-20260701-011', attrs: { errorCode: 'LDAP.BindTime.High', host: 'ldap-01', priority: 'High' } },
    { id: 'a6', time: 'Jul 1, 08:05:00', sev: 'Medium', source: 'BMC', kind: 'ALERTS', entity: 'RPT-APP-03', etype: 'Server', cat: 'ResourceContention', msg: 'CPU utilization 95% sustained for 40 minutes on RPT-APP-03.', count: 8, inc: 'INC-20260701-007', attrs: { errorCode: 'CPU_HIGH', host: 'rpt-app-03' } },
    { id: 'a7', time: 'Jul 1, 10:18:55', sev: 'Low', source: 'SCOM', kind: 'ALERTS', entity: 'WIN-APP-07', etype: 'WindowsServer', cat: 'PerformanceHealth', msg: 'Memory usage 82% on WIN-APP-07 (informational threshold).', count: 2, inc: null, attrs: { errorCode: 'MEM_WARN', host: 'win-app-07' } },
    { id: 'a8', time: 'Jul 1, 07:31:12', sev: 'Low', source: 'BMC', kind: 'ALERTS', entity: 'SW-CORE-11', etype: 'Switch', cat: 'Availability', msg: 'Interface Gi0/3 flapped twice on SW-CORE-11.', count: 2, inc: null, attrs: { errorCode: 'IF_FLAP' } }
  ];

  var SEV_ORDER = ['Critical', 'High', 'Medium', 'Low'];
  var state = { filters: { severity: '', source: '', kind: '' }, tab: 'All', selected: null };

  function injectStyles() {
    if (document.getElementById('alx-styles')) return;
    var s = document.createElement('style');
    s.id = 'alx-styles';
    s.textContent = [
      '.alx-wrap{display:grid;grid-template-columns:200px 1fr;gap:16px;padding:18px;max-width:1560px;margin:0 auto;}',
      '.alx-rail{background:var(--surface-card);border:1px solid var(--surface-border);border-radius:12px;padding:14px;height:max-content;position:sticky;top:72px;}',
      '.alx-facet{margin-bottom:16px;}.alx-facet h4{font-size:.66rem;font-weight:700;text-transform:uppercase;letter-spacing:.06em;color:var(--text-muted);margin:0 0 8px;}',
      '.alx-facet button{display:flex;width:100%;justify-content:space-between;align-items:center;background:none;border:none;padding:5px 8px;border-radius:7px;font-size:.8rem;color:var(--text-secondary);cursor:pointer;text-align:left;}',
      '.alx-facet button:hover{background:var(--surface-off-white);}.alx-facet button.on{background:var(--kfh-primary-light);color:var(--kfh-primary-dark);font-weight:700;}',
      '.alx-tabs{display:flex;gap:6px;margin-bottom:12px;flex-wrap:wrap;}',
      '.alx-tab{display:inline-flex;align-items:center;gap:6px;padding:6px 12px;border-radius:999px;border:1px solid var(--surface-border);background:var(--surface-card);font-size:.78rem;font-weight:600;color:var(--text-secondary);cursor:pointer;}',
      '.alx-tab.on{background:var(--kfh-primary);color:#fff;border-color:var(--kfh-primary);}',
      '.alx-tab .n{font-weight:800;}',
      '.alx-timeline{display:flex;align-items:flex-end;gap:3px;height:60px;padding-top:8px;}.alx-timeline .b{flex:1;border-radius:2px 2px 0 0;}',
      '.alx-row{cursor:pointer;}.alx-row.on td{background:var(--kfh-primary-light);}',
      '.alx-sev{display:inline-flex;align-items:center;gap:6px;font-weight:700;font-size:.76rem;}',
      '.alx-count{display:inline-flex;align-items:center;height:18px;padding:0 7px;border-radius:999px;background:var(--surface-off-white);border:1px solid var(--surface-border);font-size:.68rem;font-weight:700;color:var(--text-secondary);}',
      '.alx-prop{display:grid;grid-template-columns:130px 1fr;gap:2px 10px;font-size:.8rem;}',
      '.alx-prop .k{color:var(--text-muted);}.alx-prop .v{color:var(--text-primary);font-family:var(--font-mono);font-size:.76rem;word-break:break-all;}'
    ].join('');
    document.head.appendChild(s);
  }

  function sevColor(sev) {
    return { Critical: 'var(--color-critical)', High: 'var(--color-high)', Medium: 'var(--color-medium)', Low: 'var(--color-low)' }[sev] || 'var(--text-muted)';
  }

  function passes(a) {
    var f = state.filters;
    if (f.severity && a.sev !== f.severity) return false;
    if (f.source && a.source !== f.source) return false;
    if (f.kind && a.kind !== f.kind) return false;
    if (state.tab !== 'All' && a.sev !== state.tab) return false;
    return true;
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

  function timeline() {
    var out = '';
    for (var i = 0; i < 44; i++) {
      var active = i > 28;
      var h = 8 + ((i * 5) % 34) + (active ? 12 : 0);
      out += '<div class="b" style="height:' + h + 'px;background:' + (active ? 'var(--color-critical)' : 'var(--surface-border)') + ';"></div>';
    }
    return out;
  }

  function sevTabs() {
    var tabs = [{ k: 'All', n: ALERTS.length }].concat(SEV_ORDER.map(function (s) { return { k: s, n: ALERTS.filter(function (a) { return a.sev === s; }).length }; }));
    return tabs.map(function (t) {
      var dot = t.k === 'All' ? '' : '<span class="kfhx-dot" style="background:' + sevColor(t.k) + ';"></span>';
      return '<div class="alx-tab ' + (state.tab === t.k ? 'on' : '') + '" data-tab="' + t.k + '">' + dot + esc(t.k) + ' <span class="n">' + t.n + '</span></div>';
    }).join('');
  }

  function tableRows(list) {
    return list.map(function (a) {
      var incCell = a.inc
        ? '<a href="#incidents" data-page="incidents" class="kfhx-badge info" style="text-decoration:none;">◈ ' + esc(a.inc.replace('INC-20260701-', 'INC-…')) + '</a>'
        : '<span style="color:var(--text-muted);font-size:.72rem;">—</span>';
      return '<tr class="alx-row ' + (state.selected === a.id ? 'on' : '') + '" data-id="' + a.id + '">' +
        '<td style="white-space:nowrap;color:var(--text-muted);">' + esc(a.time) + '</td>' +
        '<td><span class="alx-sev" style="color:' + sevColor(a.sev) + ';"><span class="kfhx-dot" style="background:' + sevColor(a.sev) + ';"></span>' + esc(a.sev) + '</span></td>' +
        '<td><span class="kfhx-badge info">' + esc(a.source) + '</span></td>' +
        '<td class="mono">' + esc(a.entity) + '</td>' +
        '<td>' + esc(a.cat) + '</td>' +
        '<td style="color:var(--text-primary);">' + esc(a.msg) + '</td>' +
        '<td style="text-align:right;"><span class="alx-count" title="occurrences (dedup)">×' + a.count + '</span></td>' +
        '<td>' + incCell + '</td>' +
        '</tr>';
    }).join('');
  }

  function detailPanel(a) {
    var props = Object.keys(a.attrs).map(function (k) {
      return '<div class="k">' + esc(k) + '</div><div class="v">' + esc(a.attrs[k]) + '</div>';
    }).join('');
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
    var list = ALERTS.filter(passes);
    var active = ALERTS.filter(function (a) { return a.sev === 'Critical' || a.sev === 'High'; }).length;
    var sel = state.selected ? ALERTS.filter(function (a) { return a.id === state.selected; })[0] : null;

    var tableHtml =
      '<div class="kfhx-panel" style="padding:0;overflow:hidden;">' +
        '<table class="kfhx-table"><thead><tr>' +
          '<th>Time</th><th>Severity</th><th>Source</th><th>Entity</th><th>Category</th><th>Message</th><th style="text-align:right;">Count</th><th>Incident</th>' +
        '</tr></thead><tbody>' + (tableRows(list) || '<tr><td colspan="8" style="padding:26px;text-align:center;color:var(--text-muted);">No alerts match the filter.</td></tr>') + '</tbody></table>' +
      '</div>';

    mount.innerHTML =
      '<div class="alx-wrap">' +
        '<aside class="alx-rail">' +
          facet('Severity', 'severity', SEV_ORDER) +
          facet('Source', 'source', ['BMC', 'SCOM']) +
          facet('Kind', 'kind', ['ALERTS', 'LOGS', 'METRICS']) +
        '</aside>' +
        '<section>' +
          '<div class="kfhx-section-head">' +
            '<div class="kfhx-section-title">Alert Explorer <span style="color:var(--color-critical);">' + active + ' actionable</span> <span style="color:var(--text-muted);font-weight:500;">/ ' + ALERTS.length + '</span></div>' +
            '<span class="kfhx-badge info">Illustrative — live from the index next</span>' +
          '</div>' +
          '<div class="alx-tabs">' + sevTabs() + '</div>' +
          '<div class="kfhx-panel" style="padding:10px 16px;margin-bottom:14px;">' +
            '<div style="font-size:.72rem;color:var(--text-muted);">Alert volume (last 2h)</div><div class="alx-timeline">' + timeline() + '</div>' +
          '</div>' +
          (sel
            ? '<div class="kfhx-split" style="grid-template-columns:1fr 380px;">' + tableHtml + detailPanel(sel) + '</div>'
            : tableHtml) +
        '</section>' +
      '</div>';

    mount.querySelectorAll('[data-facet]').forEach(function (b) {
      b.addEventListener('click', function () { state.filters[b.getAttribute('data-facet')] = b.getAttribute('data-val'); render(); });
    });
    mount.querySelectorAll('[data-tab]').forEach(function (t) {
      t.addEventListener('click', function () { state.tab = t.getAttribute('data-tab'); render(); });
    });
    mount.querySelectorAll('.alx-row').forEach(function (r) {
      r.addEventListener('click', function (e) {
        if (e.target.closest('a')) return; // let incident links navigate
        state.selected = (state.selected === r.getAttribute('data-id')) ? null : r.getAttribute('data-id');
        render();
      });
    });
  }

  render();
})();
