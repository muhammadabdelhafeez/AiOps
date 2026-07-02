/**
 * KFH AIOps — Global context bar (Phase 0 shell).
 * A persistent enterprise-grade top bar: current scope (country) + environment + a global time-range
 * segmented control + global search. Time-range selection is published on window.KFHGlobalFilter and
 * a 'kfh:timerange-changed' event so pages can consume it. Search routes to the Log Explorer.
 */
(function () {
  'use strict';

  var RANGES = (window.KFHConfig && KFHConfig.TIME_RANGES) || [
    { id: '1h', label: '1h', ms: 3600000 },
    { id: '24h', label: '24h', ms: 86400000 },
    { id: '7d', label: '7d', ms: 604800000 },
    { id: '30d', label: '30d', ms: 2592000000 }
  ];

  window.KFHGlobalFilter = window.KFHGlobalFilter || { rangeId: '24h' };

  function session() {
    try { return (window.KFHConfig && KFHConfig.getSession()) || {}; } catch (e) { return {}; }
  }

  function esc(v) {
    return String(v == null ? '' : v).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function flagFor(code) {
    return ({ KW: '🇰🇼', BH: '🇧🇭', EG: '🇪🇬', ALL: '🌐' })[String(code || '').toUpperCase()] || '🏳️';
  }

  function shortLabel(r) {
    // Compact enterprise-grade label (1h, 24h, 7d, 30d) regardless of the verbose config label.
    return String(r.id || r.label || '').toUpperCase();
  }

  function segment() {
    return RANGES.map(function (r) {
      var active = r.id === window.KFHGlobalFilter.rangeId ? ' active' : '';
      return '<button type="button" data-range="' + esc(r.id) + '" class="' + active.trim() + '">' + esc(shortLabel(r)) + '</button>';
    }).join('');
  }

  function render() {
    var mount = document.getElementById('global-bar');
    if (!mount) { return; }
    var s = session();
    var country = esc(s.countryName || s.countryCode || 'KFH');
    var code = esc(s.countryCode || 'KW');
    var env = esc(s.environment || 'PROD');

    mount.className = 'kfhx-globalbar';
    mount.innerHTML = '' +
      '<div class="kfhx-gb-left">' +
        '<span class="kfhx-chip" title="Country scope">' + flagFor(code) + ' <span>' + country + '</span></span>' +
        '<span class="kfhx-chip kfhx-chip-env" title="Environment"><span class="kfhx-chip-label">env</span> ' + env + '</span>' +
        '<div class="kfhx-segment" role="group" aria-label="Time range">' + segment() + '</div>' +
      '</div>' +
      '<div class="kfhx-gb-right">' +
        '<div class="kfhx-gb-search">' +
          '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>' +
          '<input id="kfhx-global-search" type="search" placeholder="Search alerts, applications, assets…" autocomplete="off" />' +
        '</div>' +
        '<button class="kfhx-iconbtn" id="kfhx-refresh" title="Refresh" aria-label="Refresh">' +
          '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12a9 9 0 1 1-2.64-6.36"/><path d="M21 3v6h-6"/></svg>' +
        '</button>' +
      '</div>';

    wire(mount);
  }

  function wire(mount) {
    mount.querySelectorAll('.kfhx-segment button').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var id = btn.getAttribute('data-range');
        window.KFHGlobalFilter.rangeId = id;
        mount.querySelectorAll('.kfhx-segment button').forEach(function (b) { b.classList.remove('active'); });
        btn.classList.add('active');
        window.dispatchEvent(new CustomEvent('kfh:timerange-changed', { detail: { rangeId: id } }));
      });
    });

    var search = mount.querySelector('#kfhx-global-search');
    if (search) {
      search.addEventListener('keydown', function (e) {
        if (e.key === 'Enter') {
          var q = search.value.trim();
          window.KFHGlobalFilter.query = q;
          if (window.Router && typeof Router.navigate === 'function') {
            Router.navigate('explorer' + (q ? ('?q=' + encodeURIComponent(q)) : ''));
          }
        }
      });
    }

    var refresh = mount.querySelector('#kfhx-refresh');
    if (refresh) {
      refresh.addEventListener('click', function () {
        if (window.Router && typeof Router.reloadCurrent === 'function') { Router.reloadCurrent(); }
      });
    }
  }

  // Re-render when the country/environment scope changes.
  window.addEventListener('kfh:scope-changed', render);
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', render);
  } else {
    render();
  }
})();
