// KFH AIOps Command Center - Neo4j Schema (Neo4j 5.x Community Edition)
// Execute each statement separately in Neo4j Browser or use cypher-shell

// === CONSTRAINTS ===
CREATE CONSTRAINT app_id_unique IF NOT EXISTS FOR (a:Application) REQUIRE a.appId IS UNIQUE;
CREATE CONSTRAINT svc_id_unique IF NOT EXISTS FOR (s:Service) REQUIRE s.serviceId IS UNIQUE;
CREATE CONSTRAINT res_id_unique IF NOT EXISTS FOR (r:Resource) REQUIRE r.resourceId IS UNIQUE;
CREATE CONSTRAINT run_id_unique IF NOT EXISTS FOR (r:Run) REQUIRE r.runId IS UNIQUE;
CREATE CONSTRAINT occ_id_unique IF NOT EXISTS FOR (o:AlertOccurrence) REQUIRE o.occId IS UNIQUE;
CREATE CONSTRAINT group_id_unique IF NOT EXISTS FOR (g:AlertGroup) REQUIRE g.groupId IS UNIQUE;
CREATE CONSTRAINT incident_ref_id_unique IF NOT EXISTS FOR (i:IncidentRef) REQUIRE i.incidentId IS UNIQUE;

// === INDEXES ===
CREATE INDEX app_tenant IF NOT EXISTS FOR (a:Application) ON (a.tenantId);
CREATE INDEX svc_tenant IF NOT EXISTS FOR (s:Service) ON (s.tenantId);
CREATE INDEX res_tenant IF NOT EXISTS FOR (r:Resource) ON (r.tenantId);
CREATE INDEX group_tenant IF NOT EXISTS FOR (g:AlertGroup) ON (g.tenantId);
CREATE INDEX occ_tenant IF NOT EXISTS FOR (o:AlertOccurrence) ON (o.tenantId);
CREATE INDEX group_fp_exact IF NOT EXISTS FOR (g:AlertGroup) ON (g.fingerprintExact);
CREATE INDEX group_fp_family IF NOT EXISTS FOR (g:AlertGroup) ON (g.fingerprintFamily);
CREATE INDEX group_lastSeen IF NOT EXISTS FOR (g:AlertGroup) ON (g.lastSeen);
CREATE INDEX occ_ts IF NOT EXISTS FOR (o:AlertOccurrence) ON (o.ts);
CREATE INDEX res_name IF NOT EXISTS FOR (r:Resource) ON (r.name);
CREATE INDEX app_name IF NOT EXISTS FOR (a:Application) ON (a.name);
CREATE INDEX svc_name IF NOT EXISTS FOR (s:Service) ON (s.name);
CREATE INDEX incident_status IF NOT EXISTS FOR (i:IncidentRef) ON (i.status);

// === VECTOR INDEX (Neo4j 5.11+) ===
CREATE VECTOR INDEX group_embedding IF NOT EXISTS FOR (g:AlertGroup) ON (g.embedding) OPTIONS {indexConfig: {`vector.dimensions`: 1536, `vector.similarity_function`: 'cosine'}};
