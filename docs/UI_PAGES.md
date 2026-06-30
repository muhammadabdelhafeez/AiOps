# UI Pages & Navigation Map

> **Architecture authority:** [`docs/CAUSAL_PIPELINE.md`](./CAUSAL_PIPELINE.md).
> Every page that shows AI output must display: business journey impacted, root cause entity, deterministic confidence, AI confidence, **cited evidence IDs**, the AI model used (DeepSeek vs Azure OpenAI 5.5), and a "cached / new narrative" indicator. UI must never show raw alert lists without business impact context.

## Implementation Status
All pages implemented as modular SPA components in `/src/main/resources/static/pages/`.

## KFH Design System
The UI follows the **KFH Beyond Horizons** design identity:
- **Primary Color**: `#128754` (KFH Green)
- **Sidebar Background**: `#24604f` (Dark Teal) - retained for sidebar
- **Page Background**: `#f7f7f7` (Light Gray) - main content area
- **Card Background**: `#ffffff` (White)
- **Accent Color**: `#C4A962` (KFH Gold)

### Theme Files
- `/shared/css/kfh-theme.css` - Complete KFH design system variables
- `/shared/css/base.css` - Base utilities and reset styles
- `/vendor/css/outfit-font.css` - Local Outfit font (no external dependencies)

### Font Stack
The application uses a system font stack with local fallbacks to avoid external dependencies:
```css
body {
  font-family: 'Outfit', 'Segoe UI', -apple-system, BlinkMacSystemFont, 'Roboto', 'Helvetica Neue', Arial, sans-serif;
}
```

### Color Variables
```css
:root {
  --kfh-primary: #128754;
  --kfh-primary-dark: #0E6B42;
  --kfh-gold: #C4A962;
  --kfh-sidebar-bg: #24604f;
  --surface-bg: #f7f7f7;
  --surface-card: #ffffff;
  --surface-border: #e2e8f0;
  --text-primary: #1f2937;
  --text-secondary: #4b5563;
  --text-muted: #6b7280;
}
```

## File Structure
```
static/
├── index.html              # Main entry point
├── shared/
│   ├── css/
│   │   ├── base.css        # Base utilities
│   │   └── kfh-theme.css   # KFH design system
│   ├── js/
│   │   ├── config.js       # App configuration
│   │   ├── api-client.js   # API wrapper (tenant/user headers)
│   │   ├── utils.js        # Utility functions
│   │   └── router.js       # SPA navigation
│   └── components/
│       ├── sidebar.js      # Navigation/sidebar patterns
│       └── header.js       # Legacy/shared header component; SPA shell uses page-owned headers
└── pages/
    ├── dashboard/          # Operations overview
    ├── incidents/          # Incident management
    ├── alerts/             # Alert explorer
    ├── applications/       # Application catalog
    ├── inventory/          # Resource inventory
    ├── reports/            # Report packs
    ├── connectors/         # Data connectors (admin)
    ├── schedules/          # Job schedules (admin)
    ├── users/              # User management (admin)
    ├── settings/           # System settings (admin)
    └── audit/              # Audit logs (admin)
```

## Primary navigation
- Dashboard
- Alerts
  - Alert Explorer
  - Alert Activity
- Incidents
- Applications
  - Application Details
- Inventory & Infrastructure
- Reports
- Admin
  - Connectors
  - Schedules
  - User Management
  - Settings
  - Audit Logs

## Global shell controls
- The sidebar menu includes a KFH Group country switcher for enabled countries (`KW`, `BH`, `EG`). Switching country updates the active tenant/user/country headers and reloads the current page data under that scope.
- The SPA shell no longer renders a global top header. Each page owns its title, search, filters, refresh, and action buttons so the body remains dedicated to page-specific operations.
- Users without `COUNTRY_GLOBAL_VIEW` or `*` remain limited to their signed-in country; backend country guards remain authoritative.

## Page relationships (how users move)
Dashboard → (Incidents, Alert Explorer, Applications, Reports)
Alert Explorer → (Incident Drilldown, Application Details, Inventory Drilldown)
Alert Activity → (Alert Explorer, Incident Drilldown)
Incidents → (Application Details, Inventory Drilldown, Evidence/Reports)
Applications → Application Details → (Incidents, Evidence, Inventory)
Inventory → (Incidents, Alerts, Application Details)
Reports → (Incident, Evidence CSVs, Run details)
Admin → affects all ingestion/automation/access

## Page specs

### Dashboard
Purpose:
- KPIs, hourly trends, New vs Recurring, top impacted apps/resources, latest run results.
Data:
- incidents summary + recurring ratio
- top alerts clusters
- report pack index
Links:
- Incidents (filtered), Alert Explorer, Applications, Reports

### Alert Explorer
Purpose:
- Filter/cluster alerts, see correlations, map to incidents.
Key features:
- time range, severity, source, app/resource
- grouping by fingerprint + similarity
Links:
- Incident details, Application details, Inventory drilldown

### Alert Activity
Purpose:
- Timeline feed for shift handover/audit.
Links:
- Alert Explorer, Incidents, Reports

### Incidents Hub
Purpose:
- Main operations workspace; New vs Recurring classification and evidence.
Key features:
- recurrence classes: NEW / RECURRING_SURE / RECURRING_LIKELY / POSSIBLE
- evidence pack links + AI narrative
Links:
- App details, Inventory, Reports

### Applications Catalog
- **Status**: Implemented (static pages)
- **Catalog Path**: `src/main/resources/static/pages/applications/applications.html`
- **Details/Config Path**: `src/main/resources/static/pages/applications/applicationconfig.html`
- **Behavior**:
  - `applications.html` = list/search/filter + create/open/edit actions
  - `applicationconfig.html` = details + tabs + view/edit/create modes

#### Routing (static-friendly)
- List: `applications.html`
- Open (view): `applicationconfig.html?appId=APP-001&mode=view&tab=overview`
- Edit: `applicationconfig.html?appId=APP-001&mode=edit&tab=configuration`
- Create: `applicationconfig.html?mode=create&tab=configuration`

#### Offline vendor dependencies only
Both pages load libraries only from `src/main/resources/static/vendor/js/`:
- `tailwindcss.cdn.js`
- `react.production.min.js`
- `react-dom18.production.min.js`
- `babel.min.js`
- `prop-types.min.js`
- `lucide.min.js` (icons; used by config page)

#### Design system
- "Beyond Horizons" tokens and components from:
  - `src/main/resources/static/shared/css/kfh-theme.css`
  - `src/main/resources/static/shared/css/base.css`
- KFH identity colors:
  - `--kfh-primary: #128754`
  - `--kfh-primary-dark: #0E6B42`
  - `--kfh-primary-light: #e2f7dd`
  - `--kfh-gold: #A79F91`

### Application Details
Purpose:
- Topology neighborhood, hourly analysis, incidents, evidence packs, inventory list.
Links:
- Incidents, Inventory, Reports

### Inventory & Infrastructure
Purpose:
- Resources, dependency map, health overlays, impacted incidents/alerts.
Links:
- Incidents, Alert Explorer, Application Details

### Reports
Purpose:
- Report packs list; export; evidence references; AI executive summaries.
Links:
- Incidents, Evidence CSVs

### Admin: Connectors
Purpose:
- Configure and manage data source integrations (BMC Helix, SCOM, SolarWinds, etc.)
- Monitor connector health, events collected, and error rates
- Test connections and run manual data collection

Key Features:
- **Management Header**: Compact Audit/User-style header card with title, search, one smart filter, view toggle, and Add Connector action in one horizontal desktop control row.
- **Modern Visual Refresh**: Connectors inventory and Marketplace use a richer Command Center layout with gradient hero cards, compact KPI cards, marketplace statistics, category pills, modern connector product cards, and a full connector product-detail page.
- **KPI Strip**: 5 compact cards showing Total, Healthy, Down, Disabled connectors and Events 24h.
- **Smart Filter**: A single popover groups status (Healthy/Degraded/Down/Disabled), type, and scope filters as pill chips, replacing the three separate dropdowns.
- **Header Controls**: Search, smart filter, table/card view toggle, and Add Connector live inside the page header for a shorter layout.
- **Default Table View**: Modern card-wrapped inventory table with connector identity, health, enable toggle, scope, sync lag, events/errors, configure, and test actions.
- **Card View**: Responsive compact-modern connector cards with icon, name, type, status badge, toggle switch, scope badges, lag indicator, and events/errors stats. Desktop card view renders **3 wider cards per row**, uses two-column detail chips to reduce card height, removes the old top accent line, and applies subtle status-tinted backgrounds (green healthy, amber degraded, red down, fully greyed-out disabled) while paginating at **10 connectors per page**; table view remains the full filtered inventory view.
- **Add Connector Drawer**: Slide-in panel for adding new connectors:
  - The primary action opens an in-page **Connector Marketplace** rather than a compact modal.
  - Marketplace loads available plugins from `GET /api/v1/connectors/types` and displays all connector cards with icon, category, availability, description, and details navigation.
  - Each marketplace card opens a modern full connector detail page with hero, status, overview, capability cards, security/governance cards, installation scope, and Install/Configure/Uninstall actions.
  - Current implemented connector creation types are **BMC Helix**, **AppDynamics**, **VMware vROps / Aria Operations**, **Microsoft SCOM**, and **EMCO Ping Monitor**. Lansweeper appears with an icon as a disabled/future connector card.
  - Clicking **Install** on BMC Helix, AppDynamics, vROps, SCOM, or EMCO creates a `configurationStatus=PENDING` connector row for the active country/environment; **Uninstall** removes the installed row.
  - Global/all-country admins can choose the target physical country (`KW`, `BH`, `EG`) on the connector detail page before installing. Country admins are locked to their signed-in country and cannot change the install country.
  - Clicking an installed BMC/AppDynamics/vROps/SCOM/EMCO connector row/card or Configure opens the configuration drawer, where the operator enters endpoint settings, collection guardrails, owner team, notes, TLS/certificate mode, and write-only credentials. SCOM configuration includes management server, domain, WinRM port, HTTPS transport toggle, PowerShell authentication method, explicit **Disable certificate validation for this SCOM test** option, lookback window, and connection timeout. EMCO configuration includes SQL Server host/port, KFH and CCTV database names, encrypted KFH/CCTV SQL credential pairs, SQL encryption/trust-server-certificate toggles, lookback window, SQL login timeout, and query timeout.
  - Connector Enablement uses state-based color cues: **Enabled** is green and **Disabled** is a polished neutral grey so operators can immediately distinguish active scheduled collection from intentionally paused/disabled collection without confusing it with warnings.
  - Physical-country sessions are locked to their country; all-country admins with global-country permission may choose the target physical country so each country can maintain independent connector connection settings and alert collection.
  - Credentials are sent only as `secretsPlain` or normalized server-side from known Basic Auth/access-key aliases, encrypted in the backend, stripped from responses, and shown only through the **Credential Status** row (`Encrypted credentials saved` / `Credentials not configured`). Password/access-key inputs intentionally render blank after Save.
- **Connector Detail Drawer**: Slide-in panel for viewing/editing existing connectors:
  - Header uses a compact modern connector identity block with status-aware icon, title, connector type, and an accessible modern close button.
  - Tab bar: OVERVIEW | CONFIGURATION | MAPPING | HEALTH | LOGS
  - Overview Tab: 2x2 KPI grid (Status, Events 24H, Errors 24H, Lag), Endpoints section with Active badge, Details section
  - Configuration Tab: Editable form for connector settings, including secure-default certificate verification. BMC, AppDynamics, and vROps show **Verify TLS Certificate Chain**; SCOM shows **Disable certificate validation for this SCOM test**, which maps to `verifySsl=false` and keeps HTTPS/5986 while passing `-SkipCACheck`, `-SkipCNCheck`, and `-SkipRevocationCheck` to PowerShell. Operators may use it only for governed dev/hybrid testing while PKI/CRL reachability is remediated; endpoint SSRF protections remain enforced and saved `verifySsl=false` values remain reflected after reload and during **Test Connection**.
  - Save/test/update messages render as a slim modern feedback strip near the lower drawer content instead of crowding the top of the page.
  - Mapping Tab: Field mapping and severity mapping display
  - Health Tab: Health checks status (DNS, Auth, API/WinRM, Data Parsing) with Run Test button; failed tests display the backend-provided secret-safe Java/HTTP/PowerShell/PKIX message in the health result and toast.
  - Logs Tab: Recent connector activity logs

Statuses:
- **Healthy**: Connector enabled, last run successful, lag < 5 minutes
- **Degraded**: Connector enabled but lag > 15 minutes
- **Down**: Connector enabled but last run failed (ERROR/TIMEOUT)
- **Disabled**: Connector manually disabled

API Integration:
- `GET /admin/connectors` - List connectors with filtering
- `POST /admin/connectors` - Create new connector
- `PUT /admin/connectors/{id}` - Update connector
- `DELETE /admin/connectors/{id}` - Delete connector
- `POST /admin/connectors/{id}/test` - Test connection (async via outbox)
- `GET /connectors/types` - Get available plugin types and schemas

Files:
- `src/main/resources/static/pages/connectors/connectors.html` - Static route redirect into `index.html#connectors`.
- `src/main/resources/static/pages/connectors/connectors.js` - SPA module for connector loading, filtering, table/card rendering, drawer/modal actions, test runs, and CRUD calls.
- `src/main/resources/static/pages/connectors/connectors.css` - Connectors-specific styling, including the modern management-page override layer.

### Admin: Schedules
Purpose:
- Configure job schedules, access control, platform policies.

### Admin: User Management
Purpose:
- Create and manage sign-in user profiles for the selected country/environment scope.
- The page is intentionally simple: compact page header, create-user action, search, country filter, role filter, summary cards, and a single login-user table.
- Roles, policies, and audit-log management are no longer embedded as tabs on this page; audit remains available from the dedicated Audit Logs page.
- The create-user modal shows the active country and environment; payload scope is informational only and the backend derives persisted country/environment from `TenantContext`.

### Settings (Admin)
Purpose:
- Configure Azure OpenAI, databases, SharePoint, Teams webhooks.
- Uses a focused admin layout: when `/settings` is open, the global main navigation automatically collapses into the compact icon rail while the Settings section menu stays visible beside the settings content. Operators can use the normal sidebar collapse control if they need to expand the main navigation.
- Enforce tenant-scoped loads, RBAC for edits (`settings:write`), audit on save, and outbox-queued tests.
- Test actions store/display the backend test result message. Unsupported notification tests show an explicit “no Teams webhook call was sent” failure instead of a generic toast until a dedicated notifier adapter is implemented.
- Settings cards and connector rows must remain visually static during **Test Connection**, **Test Only**, **Test & Save**, **Update**, and auto-save re-renders. Status/result areas reserve space, action buttons keep stable widths, and Settings-scoped hover/animation transforms are disabled so cards do not jump, move up/down, or vibrate while operators work.
- The **Servers & Index** Add/Edit popup is type-aware: **Redis Server** shows host/IP, port, ACL username, password, database, and TLS fields; **Kafka Server** shows bootstrap servers, security protocol, SASL mechanism, username/principal, password/secret, client ID, and optional truststore/CA path; **Index Storage Server** shows provider, path/URI, bucket/share, region, and optional access credentials.
- Database and infrastructure provider popups include **Test Connection** beside **Add/Update Connector**. Popup tests use the current unsaved draft and show a reserved pass/fail/latency message without closing the modal or persisting changes; saved rows keep their row-level **Test Connection** action for Neo4j, Redis, Kafka, and Index Storage providers.
- Database, SharePoint, and **Servers & Index** provider popups include a multi-select **Countries** field. Selecting **KFH Kuwait** stores/uses the provider for Kuwait scope; selecting **All countries** marks the row as group-wide metadata for KW, BH, and EG. Connector rows display the technical type/provider plus the selected country scope so NOC operators can distinguish `KW Kafka` from all-country Kafka metadata.
- Settings add/edit popups stay open until the operator uses an explicit close control such as **Cancel** or the modal **X** button. Clicking outside the popup on the blurred backdrop is absorbed by the Settings modal overlay and must not dismiss the dialog, which protects in-progress configuration edits for tenant- and country-scoped settings.
Links:
- Affects ingestion/automation across modules; degraded mode shows masked placeholders when backend unavailable.

## Admin / Configuration

### Schedules
- **Status**: Implemented (static page)
- **Path**: `src/main/resources/static/pages/schedules/schedules.html`
- **Assets**:
  - `src/main/resources/static/pages/schedules/schedules.jsx` (React 18 UMD + Babel, rendered in-browser)
  - `src/main/resources/static/pages/schedules/schedules.css`
  - **Offline vendor dependencies only** via `src/main/resources/static/vendor/js/`:
    - `tailwindcss.cdn.js`
    - `react.production.min.js`
    - `react-dom18.production.min.js`
    - `babel.min.js`
    - `Recharts.js`
- **Design system**: "Beyond Horizons" (`src/main/resources/static/shared/css/kfh-theme.css`) using KFH green identity (`--kfh-primary`, etc.).
- **Capabilities (UI/mock)**:
  - list + timeline views
  - search + filters
  - run-now simulation with logs/history (mock data)
  - add/edit job drawer + modal
