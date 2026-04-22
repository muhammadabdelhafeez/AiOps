# Frontend Modules (Static SPA)

This project serves a static SPA from `src/main/resources/static/`.

## Key folders
- `static/index.html` — SPA entry
- `static/shared/js/api-client.js` — API wrapper (adds tenant/user headers)
- `static/shared/js/router.js` — hash router
- `static/shared/components/` — shared UI components
- `static/pages/*` — page modules (dashboard, alerts, incidents, connectors, etc.)

## Page responsibilities
- Dashboard: KPIs, trends, new vs recurring
- Alerts: filter + cluster + link to incidents/apps
- Incidents: list + drilldown + evidence + AI narrative
- Applications: portfolio + topology + incidents
- Inventory: resources + dependencies
- Reports: report pack index + exports
- Admin: connectors, schedules, users/RBAC, settings, audit

## Security
- UI must never display or log secrets.
- Sanitize any HTML content rendered from server using DOMPurify.
