# UI Pages & Navigation Map

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
font-family: 'Outfit', 'Segoe UI', -apple-system, BlinkMacSystemFont, 'Roboto', 'Helvetica Neue', Arial, sans-serif;
```

### Color Variables
```css
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
│       ├── sidebar.js      # Navigation sidebar
│       └── header.js       # Top header bar
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
  - Users & RBAC
  - Settings
  - Audit Logs

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
- **KPI Strip**: 6 cards showing Total, Healthy, Degraded, Down, Disabled connectors and Events 24h
- **Filter Bar**: Search, status filter (Healthy/Degraded/Down/Disabled), type filter, grid/table view toggle
- **Grid View**: Connector cards with icon, name, type, status badge, toggle switch, scope badges, lag indicator, events/errors stats
- **Table View**: Compact tabular view with all connector info and action buttons
- **Add Connector Drawer**: Slide-in panel for adding new connectors:
  - Type selection step (loads available plugins from API)
  - Configuration form (dynamically generated from plugin schema)
  - Test connection with visual feedback
  - Secrets handling (encrypted at rest)
- **Connector Detail Drawer**: Slide-in panel for viewing/editing existing connectors:
  - Tab bar: OVERVIEW | CONFIGURATION | MAPPING | HEALTH | LOGS
  - Overview Tab: 2x2 KPI grid (Status, Events 24H, Errors 24H, Lag), Endpoints section with Active badge, Details section
  - Configuration Tab: Editable form for connector settings
  - Mapping Tab: Field mapping and severity mapping display
  - Health Tab: Health checks status (DNS, Auth, API, Data Parsing) with Run Test button
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
- `/pages/connectors/index.html` - React (UMD+Babel) page matching the KFH canvas; pulls React from `/vendor/js` and uses shared KFH theme/base CSS
- `/pages/connectors/script.js` - Shared connector utilities (mock data generation, filters, validation) used by the page and Node tests
- `/pages/connectors/connector-sidebar.js` - (legacy) drawer component hooks; retained only if older flows are reused
- `/pages/connectors/__tests__/connectors.spec.js` - Frontend utility tests

### Admin: Schedules
Purpose:
- Configure job schedules, access control, platform policies.

### Settings (Admin)
Purpose:
- Configure Azure OpenAI, databases, SharePoint, Teams webhooks.
- Enforce tenant-scoped loads, RBAC for edits (`settings:write`), audit on save, and outbox-queued tests.
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
