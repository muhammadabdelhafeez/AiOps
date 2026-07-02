/**
 * KFH AIOps — Connections (redirect stub).
 *
 * The standalone Connections page has been consolidated into
 * Settings → Connections (enterprise-grade catalog + Add/View popup).
 * The router now aliases the "connections" and legacy "connectors" hash
 * routes to the Settings module (see shared/js/router.js). This file
 * remains only as a safety net in case something loads it directly.
 */
(function () {
  'use strict';
  if (window.Router && typeof window.Router.navigate === 'function') {
    // Ensure the Settings module lands on the Connections section.
    if (window.Settings && typeof window.Settings.setTab === 'function') {
      try { window.Settings.setTab('connections'); } catch (e) { /* Settings not yet ready */ }
    }
    return;
  }
  var mount = document.getElementById('page-root') || document.getElementById('content-area');
  if (mount) {
    mount.innerHTML = '<div style="padding:24px;color:var(--text-muted,#64748b);font-family:Outfit,sans-serif;">Redirecting to Settings › Connections…</div>';
  }
  try { window.location.hash = '#settings'; } catch (e) { /* no-op */ }
})();
