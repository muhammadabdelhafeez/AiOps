# API Contracts (v1)

## Global rules
### Required headers
- X-Tenant-Id: <tenant uuid>
- X-User-Id: <user uuid>

If missing: 400/401 (implementation choice) with standard error response.

### Authorization
- Permission-based RBAC enforced in service layer.
- Scope policies restrict data visibility by app/resource/env/tags.

### Pagination
- GET list endpoints support: page, size, sort
- time filtering: from, to (ISO-8601)

### Standard error shape
- code
- message
- correlationId
- details (optional)
- timestamp

## Module endpoints (placeholders—fill as implemented)

### Dashboard
- GET /api/dashboard/summary
- GET /api/dashboard/hourly
- GET /api/dashboard/top-apps
- GET /api/dashboard/top-incidents

### Alerts
- GET /api/alerts/occurrences
- GET /api/alerts/groups
- GET /api/alerts/groups/{id}
- POST /api/alerts/groups/{id}/create-incident
- POST /api/alerts/groups/{id}/generate-evidence

### Alert Activity
- GET /api/alerts/activity

### Incidents
- GET /api/incidents
- GET /api/incidents/{id}
- POST /api/incidents/{id}/status
- POST /api/incidents/{id}/generate-retrieval-pack
- POST /api/incidents/{id}/generate-evidence
- POST /api/incidents/{id}/generate-ai-summary

### Connectors
- GET /api/connectors
  - Permission: config.connector.read
  - Query: page, size, sort, search
  - Returns: Page<ConnectorResponse>
- GET /api/connectors/{id}
  - Permission: config.connector.read
  - Returns: ConnectorResponse (no secrets)
- POST /api/connectors
  - Permission: config.connector.write
  - Body: ConnectorCreateRequest
  - Returns: ConnectorResponse
  - Creates audit log + outbox event
- PUT /api/connectors/{id}
  - Permission: config.connector.write
  - Body: ConnectorCreateRequest
  - Returns: ConnectorResponse
  - Creates audit log + outbox event
- DELETE /api/connectors/{id}
  - Permission: config.connector.write
  - Creates audit log + outbox event
- POST /api/connectors/test
  - Permission: config.connector.test
  - Body: ConnectorTestRequest
  - Returns: CompletableFuture<TestResult>
  - Async via outbox pattern
- GET /api/connectors/types
  - Permission: config.connector.read
  - Returns: Available connector types and schemas

### Applications
- GET /api/apps — requires X-Tenant-Id and X-User-Id; permission `applications:view`; returns list with incidentStats, onboardedSources, inventory counts.
- GET /api/apps/{id} — tenant-scoped application detail; permission `applications:view`.
- GET /api/apps/{id}/incidents — tenant + scope; supports pagination/time filters.
- GET /api/apps/{id}/topology — Neo4j-backed; return degraded=true when graph unavailable.
- POST /api/apps/{id}/analyze-hourly — queues outbox job for health refresh/evidence; permission `applications:analyze`; returns 202 + correlationId; audit log required.

### Inventory
- GET /api/inventory/resources
- GET /api/inventory/resources/{id}
- GET /api/inventory/resources/{id}/dependencies
- GET /api/inventory/resources/{id}/incidents

### Reports
- GET /api/reports
- GET /api/reports/{id}
- GET /api/reports/{id}/artifacts

### Admin: Connectors
- GET /api/v1/connectors — List connectors with optional filters
  - Query params: type, enabled, search, page, size, sort
  - Returns: List<ConnectorResponse> with configSummary, lastTest, lastRun, health
  - Permission: connectors:view
  
- GET /api/v1/connectors/{connectorId} — Get connector details
  - Returns: Full ConnectorResponse with config, secretsMask (never plaintext)
  - Permission: connectors:view
  
- POST /api/v1/connectors — Create new connector
  - Body: { type, name, enabled, config, secretsPlain }
  - Validates: config via plugin, SSRF for URLs
  - Audit: CONNECTOR_CREATED
  - Outbox: CONNECTOR_CREATED event
  - Permission: connectors:edit
  
- PUT /api/v1/connectors/{connectorId} — Update connector
  - Body: { name?, enabled?, config?, secretsPlain? }
  - Audit: CONNECTOR_UPDATED
  - Outbox: CONNECTOR_UPDATED event
  - Permission: connectors:edit
  
- DELETE /api/v1/connectors/{connectorId} — Delete connector
  - Cascades: secrets, runs, logs
  - Audit: CONNECTOR_DELETED
  - Outbox: CONNECTOR_DELETED event
  - Permission: connectors:edit
  
- POST /api/v1/connectors/{connectorId}/enable — Enable connector
- POST /api/v1/connectors/{connectorId}/disable — Disable connector
  - Audit: CONNECTOR_TOGGLED
  - Outbox: CONNECTOR_TOGGLED event
  - Permission: connectors:edit
  
- POST /api/v1/connectors/{connectorId}/test — Test connection
  - Creates run record (runType=TEST)
  - Executes via plugin testConnection()
  - Returns: TestResultResponse { connectorRunId, pass, latencyMs, message }
  - Audit: CONNECTOR_TEST
  - Permission: connectors:edit
  
- POST /api/v1/connectors/{connectorId}/collect-now — Manual collect
  - Body: { windowMinutes?, reason? }
  - Creates queued run (runType=COLLECT)
  - Emits outbox for async worker
  - Returns: { connectorRunId }
  - Audit: CONNECTOR_COLLECT_NOW
  - Outbox: CONNECTOR_COLLECT_REQUESTED
  - Permission: connectors:edit
  
- POST /api/v1/connectors/{connectorId}/reveal — Reveal secret (ADMIN)
  - Body: { field, reason }
  - Decrypts and returns single field value
  - Audit: SECRET_REVEALED (critical)
  - Permission: connectors:admin
  
- GET /api/v1/connectors/{connectorId}/history — Run history
  - Query: page, size
  - Returns: List<ConnectorRun>
  
- GET /api/v1/connector-runs/{runId} — Run details
- GET /api/v1/connector-runs/{runId}/logs — Run logs
  - Query: limit (default 500)

- GET /api/v1/connectors/types — List available plugin types
  - Returns: List<PluginMetadata> with configSchema for UI forms

### Admin: Schedules
- GET /api/admin/schedules
- POST /api/admin/schedules
- POST /api/admin/schedules/{id}/trigger

### Admin: Users & RBAC
- GET /api/admin/users
- GET /api/admin/roles
- POST /api/admin/roles
- POST /api/admin/roles/{id}/permissions
- POST /api/admin/users/{id}/roles

### Admin: Settings
- GET /api/admin/settings
- POST /api/admin/settings
- POST /api/admin/settings/test — queues outbox test for target (azure embeddings/gpt, neo4j, postgres, sharepoint, teams); requires `settings:write`; audit log on invocation.
