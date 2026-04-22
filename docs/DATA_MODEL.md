# Data Model (Logical)

This is a logical view to help readers. The physical schema is defined by Flyway migrations in `src/main/resources/db/migration/`.

## Core entities (PostgreSQL)

```mermaid
erDiagram
  TENANT ||--o{ USER : has
  TENANT ||--o{ ROLE : has
  ROLE ||--o{ ROLE_PERMISSION : grants
  USER ||--o{ USER_ROLE : assigned

  TENANT ||--o{ CONNECTOR : owns
  CONNECTOR ||--o{ CONNECTOR_RUN : produces

  TENANT ||--o{ ALERT_OCCURRENCE : owns
  TENANT ||--o{ ALERT_GROUP : owns
  ALERT_GROUP ||--o{ INCIDENT : creates

  TENANT ||--o{ REPORT_PACK : owns
  REPORT_PACK ||--o{ REPORT_ARTIFACT : contains

  TENANT ||--o{ AUDIT_LOG : records
  TENANT ||--o{ OUTBOX_EVENT : queues

  TENANT {
    uuid id PK
    string name
  }
  USER {
    uuid id PK
    uuid tenant_id FK
    string email
  }
  CONNECTOR {
    uuid id PK
    uuid tenant_id FK
    string type
    string name
    boolean enabled
  }
  CONNECTOR_RUN {
    uuid id PK
    uuid tenant_id FK
    uuid connector_id FK
    string run_type
    string status
    datetime started_at
    datetime finished_at
  }
  ALERT_GROUP {
    uuid id PK
    uuid tenant_id FK
    string exact_fingerprint
    string family_fingerprint
    datetime window_start
    datetime window_end
  }
  INCIDENT {
    uuid id PK
    uuid tenant_id FK
    uuid alert_group_id FK
    string status
    boolean recurring
  }
  OUTBOX_EVENT {
    uuid id PK
    uuid tenant_id FK
    string event_type
    string status
    datetime created_at
  }
  AUDIT_LOG {
    uuid id PK
    uuid tenant_id FK
    uuid user_id
    string action
    datetime created_at
  }
```

## Neo4j (hot graph)
- Nodes: Application, Resource, AlertGroup, Incident (optional)
- Relationships: DEPENDS_ON, IMPACTS, CO_OCCURS_WITH

```mermaid
flowchart LR
  App[Application] -->|DEPENDS_ON| Res[Resource]
  Res -->|DEPENDS_ON| Res2[Resource]
  AG[AlertGroup] -->|IMPACTS| App
  AG -->|CO_OCCURS_WITH| AG2[AlertGroup]
```
