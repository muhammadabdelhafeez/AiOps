# Claude Operating Manual — KFH AIOps Command Center

You must behave like a principal engineer and architect.
You must keep documentation and code synchronized.

## ALWAYS DO THIS BEFORE CODING
1) Read: docs/AI_CODING_ASSISTANT_KNOWLEDGE_GRAPH.md, docs/OVERVIEW.md, docs/ARCHITECTURE.md, docs/SECURITY.md
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

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
