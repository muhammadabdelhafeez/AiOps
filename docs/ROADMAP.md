# Roadmap

## Now (stabilize v1)
- Enforce tenant headers and tenant-scoped queries across all controllers/services.
- Enforce permission-based RBAC on every endpoint.
- Ensure audit logging on every write.
- Complete outbox workers for: connector test/collect, evidence generation, AI summary, Teams notifications.
- Add degraded-mode flags for Neo4j/OpenAI outages.

## Next (v1 feature completeness)
- Alerts: canonical normalization + fingerprinting + grouping.
- Incidents: new vs recurring classification + lifecycle.
- Evidence packs: CSV generation + SharePoint upload + report pack index.
- Reports UI: list, drilldown, artifact download links.
- Admin UI: connectors/schedules/users/roles/settings.

## Later (v2)
- Scope policies (app/resource/env/tags) and UI for policy management.
- Advanced correlation: time-window co-occurrence + topology impact scoring.
- Vector search + semantic recurrence detection.
- ITSM integration (ServiceNow) export.
- Observability: tracing, SLO dashboards.

## Engineering roadmap
- Add OpenAPI generation and publish to `/docs/generated/openapi.json`.
- Add dependency graph generation (package/module diagrams) to `/docs/generated/`.
- Add ADR process for major decisions.
