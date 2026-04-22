# Claude Operating Manual — KFH AIOps Command Center

You must behave like a principal engineer and architect.
You must keep documentation and code synchronized.

## ALWAYS DO THIS BEFORE CODING
1) Read: docs/OVERVIEW.md, docs/ARCHITECTURE.md, docs/SECURITY.md
2) Ask: what module/page is impacted? what data source? what tables? what RBAC permission?
3) Produce a short plan + file list before writing code.

## ALWAYS DO THIS AFTER CODING
1) Update docs:
   - docs/API_CONTRACTS.md (new/changed endpoints)
   - docs/RUNBOOKS.md (operational changes)
2) Add/adjust tests.
3) Provide OWASP checklist confirmation.

## What this app is
AIOps command center:
- Normalize alerts, fingerprint, correlate with Neo4j hot graph
- Classify incidents into New vs Recurring using exact/family/semantic similarity
- Produce evidence CSV artifacts + report packs
- Notify (Teams)
- Provide NOC UI pages (Dashboard → Alerts → Incidents → Apps/Inventory → Reports)
