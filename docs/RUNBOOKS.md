# Runbooks — Development & Operations

## Local development (IntelliJ)
### Prerequisites
- Java 21
- PostgreSQL (local) - running on port 5432
- Neo4j (local) - running on port 7687
- (Optional) local SharePoint mock or dev integration config

### Backend run (Spring Boot)
1. Configure environment variables or use defaults in `application.properties`:
   - DB_URL, DB_USER, DB_PASS
   - NEO4J_URI, NEO4J_USER, NEO4J_PASS
   - SHAREPOINT_* (if enabled)
   - OPENAI_* (if enabled)
2. Run Flyway migrations on startup (includes V2__add_connector_plugins.sql).
3. Start backend from IntelliJ run config or via Maven:
   ```bash
   ./mvnw spring-boot:run
   ```
4. **Access the application at: http://localhost:8443/**
   - The root URL (`/`) automatically serves `index.html`
   - Navigate via hash routes: `http://localhost:8443/#dashboard`

### Frontend run
1. Start Spring Boot server (see above)
2. Open browser at **http://localhost:8443/** (port 8443 as configured)
3. **No external dependencies required** - all resources are local:
   - React from `vendor/js/react.production.min.js`
   - Outfit font from `vendor/css/outfit-font.css`
   - DOMPurify from `dompurify/purify.min.js` (XSS protection)
   - HTML Sanitizer from `vendor/js/html-sanitizer.js`
4. The SPA router handles navigation via URL hash (e.g., `#dashboard`, `#incidents`)
5. File structure:
   ```
   static/
   ├── index.html              # Main SPA entry point
   ├── shared/                 # Shared resources
   │   ├── css/base.css        # Complete CSS utilities (Tailwind-like, offline)
   │   ├── js/
   │   │   ├── config.js       # App configuration
   │   │   ├── api-client.js   # API wrapper (tenant/user headers)
   │   │   ├── utils.js        # Utility functions (XSS protection via DOMPurify)
   │   │   └── router.js       # SPA navigation
   │   └── components/
   │       ├── sidebar.js      # Navigation sidebar
   │       └── header.js       # Top header bar
   ├── pages/                  # Page modules
   │   ├── dashboard/          # Operations overview
   │   ├── incidents/          # Incident management
   │   ├── connectors/         # Data connector management
   │   ├── alerts/             # Alert explorer
   │   ├── applications/       # Application catalog
   │   ├── inventory/          # Resource inventory
   │   ├── reports/            # Report packs
   │   ├── connectors/         # Data connectors (admin)
   │   ├── schedules/          # Job schedules (admin)
   │   ├── users/              # User management (admin)
   │   ├── settings/           # System settings (admin)
   │   └── audit/              # Audit logs (admin)
   ├── vendor/                 # Third-party libraries (local)
   │   ├── css/outfit-font.css # Local font
   │   ├── fonts/              # Font files
   │   └── js/                 # React, sanitizers
   └── dompurify/              # XSS sanitization library
   ```
6. Each page module has:
   - `index.html` - Page HTML template
   - `script.js` - Page JavaScript logic (PageXxx module)
   - `styles.css` - Page-specific styles
6. API client automatically includes X-Tenant-Id and X-User-Id headers

## Operational runbook
### Hourly scheduled run
- Schedule triggers connector runs + normalization + correlation + evidence/report jobs.
- Output:
  - new incidents
  - updated recurring incidents
  - evidence CSVs
  - report pack index
  - Teams summary notification

### On-demand investigation
- Operator triggers:
  - evidence pack generation
  - AI summary generation
  - correlation refresh (if needed)

## Troubleshooting
### If Neo4j is down
- Expected behavior:
  - incident engine still works using fingerprints
  - correlation/topology features degraded
- Action:
  - restore Neo4j, re-run graph upsert jobs

### If OpenAI is down
- Expected behavior:
  - AI summaries marked pending
  - jobs queued for retry
- Action:
  - restore OpenAI, replay outbox AI jobs

### If SharePoint is down
- Evidence artifacts may fail to upload.
- Action:
  - retry artifact upload jobs, ensure report pack index still created.

## Support checks
- check last connector_run status
- check outbox queue depth
- check schedule_run status
- check report pack index generation

### Connector management operations
- Use UI Connectors page to configure BMC and other monitoring source integrations
- All connector operations require tenant/user headers and proper RBAC permissions:
  - `config.connector.read`: view connectors
  - `config.connector.write`: create/update/delete connectors  
  - `config.connector.test`: test connector connections
- Connector tests run async via outbox pattern to prevent UI blocking
- BMC connector includes SSRF protection - only corporate domains allowed
- Secrets encrypted at rest; never logged or returned in API responses
- All connector changes generate audit logs with before/after summaries
- Failed connectors enter degraded mode; system continues operating without them

### Settings validation
- Use UI Settings page to trigger configuration tests (Azure OpenAI, Neo4j, PostgreSQL, SharePoint, Teams) — each test enqueues an outbox job via POST /api/admin/settings/test with required tenant/user headers and `settings:write` permission.
- Saves POST /api/admin/settings are audited (tenantId, userId, action=settings.update, correlationId). Ensure secrets are masked client-side and not logged.

### Connector management

#### Adding a new connector
1. Navigate to Admin > Connectors
2. Click "Add Connector" button
3. Select connector type (BMC, SCOM, SolarWinds, etc.)
4. Fill in configuration:
   - Name (unique within tenant)
   - Base URL (SSRF validated)
   - Authentication credentials
   - Collection window and settings
5. Click "Test Connection" to verify
6. Click "Save" to create

#### Testing connectors
- POST /api/v1/connectors/{id}/test
- Creates a TEST run record
- Validates authentication and API connectivity
- Results logged in connector run history

#### Manual data collection
- POST /api/v1/connectors/{id}/collect-now
- Creates QUEUED run record
- Worker picks up via outbox pattern
- Supports custom window override

#### Revealing secrets (ADMIN only)
- POST /api/v1/connectors/{id}/reveal
- Requires reason for audit trail
- Decrypts and returns single field
- Critical audit log generated

#### Connector health monitoring
Health badges computed from:
- Last test result (SUCCESS/FAILED)
- Last collect result
- Enabled/disabled state

#### Troubleshooting connector issues
1. Check connector run history for errors
2. Review run logs for detailed steps
3. Test connection manually
4. Verify credentials haven't expired
5. Check SSRF validation for URL changes
6. Review audit log for recent changes

#### Connector secrets encryption
- Secrets encrypted using AES-256-GCM
- Master key from INSIGHT_MASTER_KEY env var
- Encrypted values prefixed with "$enc$"
- Never logged or returned in responses

