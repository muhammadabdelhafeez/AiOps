# KFH AIOps Command Center — Overview

## What this application is
KFH AIOps Command Center is an enterprise platform that consolidates alerts, incidents, topology, inventory, evidence, and reporting into one governed, multi-tenant system.

It transforms raw monitoring events into canonical alerts, correlates them using a hot graph (Neo4j), classifies incidents as New vs Recurring, generates evidence/report packs, and notifies stakeholders.

## Goals
- Provide a single command center for NOC/SRE operations.
- Reduce MTTA/MTTR by clustering + correlation + evidence packs.
- Detect and explain recurring incidents using fingerprints + similarity.
- Produce audit-friendly evidence artifacts (CSV packs) and report pack index.
- Enforce enterprise governance: tenant isolation, RBAC, audit logging.

## Non-goals (v1)
- Replace monitoring tools (SolarWinds/SCOM/BMC/etc.) — we ingest from them.
- Fully automated remediation.
- Full ITSM replacement (ServiceNow etc.) — we integrate and export evidence.

## Users & personas
- NOC Operator: triage, verify, escalate.
- SRE / App Support: deep incident analysis, recurrence review.
- Infra Team: resource health, dependency impact.
- Admin/Owner: connectors, schedules, tenants, RBAC, security.
- Leadership: KPIs, recurring ratio, summary reporting.

## Key concepts
- Canonical Alert: normalized event from any connector/source.
- Alert Occurrence: single alert instance.
- Alert Group: clustered occurrences in a time window.
- Fingerprint:
  - exact_fingerprint: strict recurrence signature
  - family_fingerprint: broader pattern family
- Incident: operational event requiring action.
- Retrieval Pack: curated evidence bundle for AI/report generation.
- Evidence Pack: CSV artifacts + pointers to sources.
- Report Pack Index: DB index linking reports to evidence + incidents.

## Two operating modes
### A) Hourly scheduled run
Collect raw alerts → normalize + fingerprint → upsert hot graph → embeddings → retrieval pack → GPT summary → evidence CSVs → report pack index → notifications.

### B) On-demand investigation
Operator uses UI (Dashboard/Alerts/Incidents/Apps) → requests deeper correlation/evidence → retrieval/evidence packs generated → incident/report updated.

## Tech stack (v1)
- Backend: Java 21, Spring Boot 3
- Frontend: React + TypeScript + Tailwind
- Datastores:
  - PostgreSQL (system of record)
  - Neo4j (hot correlation/topology)
  - SharePoint (raw/evidence/output artifacts)
- AI: Embeddings + GPT-5.2-Pro (evidence-based only)
- Notifications: Teams summary alerts
