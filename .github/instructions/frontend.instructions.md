---
applyTo: "**/frontend/**,**/*.ts,**/*.tsx,**/*.css"
---

# Frontend Instructions (React + TypeScript + Tailwind)

## UI principles
- Modern enterprise AIOps look aligned with KFH identity (navy/gold accents).
- Clear separation: pages vs components vs API client.
- Provide filters, drilldowns, and drawers for details (NOC-friendly).
- Emphasize New vs Recurring incidents and hourly analysis.

## Data & state
- All requests include tenant + user context (from auth/session layer).
- API client wrapper with:
  - base URL, headers, error handling, typed responses
  - retry only for safe GETs, not for writes

## Components
- Reusable tables, KPI cards, trend charts, health badges.
- Empty states, loading skeletons, error boundaries.
- Accessibility: keyboard nav, readable contrast, aria labels.

## Pages relationship
Dashboard → (Incidents, Alert Explorer, Applications, Reports)
Applications → Application Details → (Incidents, Inventory, Evidence)
Inventory → Resource Drilldown → (Incidents, Alerts)
