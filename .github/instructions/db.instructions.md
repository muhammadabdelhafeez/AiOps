---
applyTo: "**/db/migration/**,**/*.sql"
---

# Database Instructions (Flyway / PostgreSQL)

- Use Flyway versioned migrations: V{n}__description.sql
- Never modify applied migrations; create new V{n+1} migration.
- Always include tenant_id where applicable.
- Add indexes for:
  - tenant_id + time (alerts/incidents)
  - fingerprint fields
  - foreign keys used in filters
- Use constraints (FK/unique) to protect integrity.
- Avoid long locks; prefer additive changes.
