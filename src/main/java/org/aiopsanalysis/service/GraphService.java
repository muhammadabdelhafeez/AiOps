package org.aiopsanalysis.service;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for Neo4j graph operations.
 * 
 * Responsibilities:
 * - Incident topology subgraph generation for UI visualization
 * - Correlation edge management (RELATED_TO between AlertGroups)
 * - CMDB topology sync (Application -> Service -> Resource)
 * - IncidentRef mirror management
 * 
 * Neo4j is the "graph intelligence" layer - PostgreSQL is system-of-record.
 */
@Service
public class GraphService {

    private static final Logger log = LoggerFactory.getLogger(GraphService.class);

    private final Driver neo4jDriver;

    @Value("${aiops.neo4j.hot-window-days:15}")
    private int hotWindowDays;

    public GraphService(Driver neo4jDriver) {
        this.neo4jDriver = neo4jDriver;
    }

    /**
     * Get incident topology subgraph for UI visualization.
     * Returns nodes[] and edges[] for rendering.
     * 
     * @param tenantId Tenant identifier
     * @param incidentId Incident identifier (from PostgreSQL)
     * @return TopologyResult with nodes and edges
     */
    public TopologyResult getIncidentTopology(String tenantId, String incidentId) {
        String query = """
            MATCH (i:IncidentRef {tenantId: $tenantId, incidentId: $incidentId})-[:HAS_GROUP]->(g:AlertGroup)
            OPTIONAL MATCH (g)-[:IMPACTS_APP]->(a:Application)
            OPTIONAL MATCH (a)-[:HAS_SERVICE]->(s:Service)-[:RUNS_ON]->(r:Resource)
            OPTIONAL MATCH (s)-[dep:DEPENDS_ON]->(s2:Service)
            OPTIONAL MATCH (g)-[rel:RELATED_TO]->(g2:AlertGroup)
            RETURN 
                collect(DISTINCT {id: a.appId, type: 'Application', name: a.name, criticality: a.criticality}) AS apps,
                collect(DISTINCT {id: s.serviceId, type: 'Service', name: s.name, serviceType: s.type, appId: a.appId}) AS services,
                collect(DISTINCT {id: r.resourceId, type: 'Resource', name: r.name, kind: r.kind}) AS resources,
                collect(DISTINCT {id: g.groupId, type: 'AlertGroup', title: g.alertTitle, severity: g.severityMax, 
                    countHot: g.countHot, lastSeen: g.lastSeen, classification: g.classification}) AS alertGroups,
                collect(DISTINCT {from: s.serviceId, to: s2.serviceId, type: 'DEPENDS_ON', depType: dep.depType}) AS dependencies,
                collect(DISTINCT {from: g.groupId, to: g2.groupId, type: 'RELATED_TO', score: rel.score, method: rel.method}) AS correlations
            """;

        try (Session session = neo4jDriver.session()) {
            Result result = session.run(query, Values.parameters("tenantId", tenantId, "incidentId", incidentId));

            if (result.hasNext()) {
                Record record = result.next();
                return new TopologyResult(
                    extractNodes(record),
                    extractEdges(record)
                );
            }
        } catch (Exception e) {
            log.error("Error fetching incident topology for {}", incidentId, e);
        }

        return new TopologyResult(Collections.emptyList(), Collections.emptyList());
    }

    /**
     * Upsert IncidentRef node in Neo4j (mirror of PostgreSQL incident).
     */
    public void upsertIncidentRef(String tenantId, String incidentId, String appId, 
                                   String status, String classificationLabel, String title,
                                   List<String> groupIds) {
        String query = """
            MERGE (i:IncidentRef {tenantId: $tenantId, incidentId: $incidentId})
            SET i.appId = $appId, i.status = $status, i.classificationLabel = $classificationLabel,
                i.title = $title, i.updatedAt = timestamp()
            WITH i
            UNWIND $groupIds AS gid
            MATCH (g:AlertGroup {tenantId: $tenantId, groupId: gid})
            MERGE (i)-[:HAS_GROUP]->(g)
            """;

        try (Session session = neo4jDriver.session()) {
            session.run(query, Values.parameters(
                "tenantId", tenantId,
                "incidentId", incidentId,
                "appId", appId,
                "status", status,
                "classificationLabel", classificationLabel,
                "title", title,
                "groupIds", groupIds
            ));
            log.debug("Upserted IncidentRef {} with {} groups", incidentId, groupIds.size());
        } catch (Exception e) {
            log.error("Error upserting IncidentRef {}", incidentId, e);
        }
    }

    /**
     * Create RELATED_TO correlation edge between AlertGroups.
     */
    public void createCorrelationEdge(String tenantId, String groupId1, String groupId2, 
                                       double score, String method) {
        String query = """
            MATCH (g1:AlertGroup {tenantId: $tenantId, groupId: $groupId1})
            MATCH (g2:AlertGroup {tenantId: $tenantId, groupId: $groupId2})
            MERGE (g1)-[rel:RELATED_TO]->(g2)
            SET rel.score = $score, rel.method = $method, rel.updatedAt = timestamp()
            """;

        try (Session session = neo4jDriver.session()) {
            session.run(query, Values.parameters(
                "tenantId", tenantId,
                "groupId1", groupId1,
                "groupId2", groupId2,
                "score", score,
                "method", method
            ));
        } catch (Exception e) {
            log.error("Error creating correlation edge {} -> {}", groupId1, groupId2, e);
        }
    }

    /**
     * Find similar AlertGroups using vector similarity search.
     * 
     * @param embedding The query embedding vector
     * @param k Number of results to return
     * @return List of similar groups with scores
     */
    public List<SimilarGroup> findSimilarGroups(float[] embedding, int k) {
        String query = """
            CALL db.index.vector.queryNodes('group_embedding', $k, $embedding)
            YIELD node, score
            RETURN node.groupId AS groupId, node.fingerprintExact AS fingerprint, 
                   node.alertTitle AS title, score
            ORDER BY score DESC
            """;

        List<SimilarGroup> results = new ArrayList<>();
        try (Session session = neo4jDriver.session()) {
            // Convert float[] to List<Double> for Neo4j driver
            List<Double> embeddingList = new ArrayList<>();
            for (float f : embedding) {
                embeddingList.add((double) f);
            }

            Result result = session.run(query, Values.parameters("k", k, "embedding", embeddingList));
            while (result.hasNext()) {
                Record record = result.next();
                results.add(new SimilarGroup(
                    record.get("groupId").asString(),
                    record.get("fingerprint").asString(),
                    record.get("title").asString(),
                    record.get("score").asDouble()
                ));
            }
        } catch (Exception e) {
            log.error("Error in vector similarity search", e);
        }
        return results;
    }

    /**
     * Get groups impacting a specific application.
     */
    public List<Map<String, Object>> getGroupsImpactingApp(String tenantId, String appId) {
        String query = """
            MATCH (g:AlertGroup {tenantId: $tenantId})-[:IMPACTS_APP]->(a:Application {appId: $appId})
            RETURN g.groupId AS groupId, g.alertTitle AS title, g.severityMax AS severity,
                   g.countHot AS count, g.lastSeen AS lastSeen, g.classification AS classification
            ORDER BY g.severityMax DESC, g.countHot DESC
            """;

        List<Map<String, Object>> results = new ArrayList<>();
        try (Session session = neo4jDriver.session()) {
            Result result = session.run(query, Values.parameters("tenantId", tenantId, "appId", appId));
            while (result.hasNext()) {
                Record record = result.next();
                Map<String, Object> group = new HashMap<>();
                group.put("groupId", record.get("groupId").asString());
                group.put("title", record.get("title").asString());
                group.put("severity", record.get("severity").asString());
                group.put("count", record.get("count").asInt());
                group.put("lastSeen", record.get("lastSeen").asLong());
                group.put("classification", record.get("classification").asString());
                results.add(group);
            }
        } catch (Exception e) {
            log.error("Error getting groups for app {}", appId, e);
        }
        return results;
    }

    /**
     * Cleanup old AlertOccurrences outside the hot window.
     * AlertGroups are kept but occurrences are pruned.
     */
    public int cleanupOldOccurrences(String tenantId) {
        long cutoffTs = System.currentTimeMillis() - (hotWindowDays * 24L * 60L * 60L * 1000L);
        
        String query = """
            MATCH (o:AlertOccurrence {tenantId: $tenantId})
            WHERE o.ts < $cutoffTs
            DETACH DELETE o
            RETURN count(o) AS deleted
            """;

        try (Session session = neo4jDriver.session()) {
            Result result = session.run(query, Values.parameters("tenantId", tenantId, "cutoffTs", cutoffTs));
            if (result.hasNext()) {
                int deleted = result.next().get("deleted").asInt();
                log.info("Cleaned up {} old occurrences for tenant {}", deleted, tenantId);
                return deleted;
            }
        } catch (Exception e) {
            log.error("Error cleaning up old occurrences", e);
        }
        return 0;
    }

    /**
     * Recompute group counters after occurrence cleanup.
     */
    public void recomputeGroupCounters(String tenantId) {
        String query = """
            MATCH (g:AlertGroup {tenantId: $tenantId})
            OPTIONAL MATCH (o:AlertOccurrence {tenantId: $tenantId})-[:INSTANCE_OF]->(g)
            WITH g, count(o) AS c, max(o.ts) AS lastTs, min(o.ts) AS firstTs
            SET g.countHot = c,
                g.lastSeen = coalesce(lastTs, g.lastSeen),
                g.firstSeen = coalesce(firstTs, g.firstSeen)
            """;

        try (Session session = neo4jDriver.session()) {
            session.run(query, Values.parameters("tenantId", tenantId));
            log.info("Recomputed group counters for tenant {}", tenantId);
        } catch (Exception e) {
            log.error("Error recomputing group counters", e);
        }
    }

    /**
     * Extract nodes from query result.
     */
    private List<Map<String, Object>> extractNodes(Record record) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        
        // Add applications
        record.get("apps").asList(v -> v.asMap()).forEach(nodes::add);
        // Add services
        record.get("services").asList(v -> v.asMap()).forEach(nodes::add);
        // Add resources
        record.get("resources").asList(v -> v.asMap()).forEach(nodes::add);
        // Add alert groups
        record.get("alertGroups").asList(v -> v.asMap()).forEach(nodes::add);
        
        // Filter out null/empty entries
        nodes.removeIf(n -> n.get("id") == null);
        
        return nodes;
    }

    /**
     * Extract edges from query result.
     */
    private List<Map<String, Object>> extractEdges(Record record) {
        List<Map<String, Object>> edges = new ArrayList<>();
        
        // Add dependencies
        record.get("dependencies").asList(v -> v.asMap()).forEach(edges::add);
        // Add correlations
        record.get("correlations").asList(v -> v.asMap()).forEach(edges::add);
        
        // Filter out null/empty entries
        edges.removeIf(e -> e.get("from") == null || e.get("to") == null);
        
        return edges;
    }

    /**
     * Result class for topology queries.
     */
    public record TopologyResult(
        List<Map<String, Object>> nodes,
        List<Map<String, Object>> edges
    ) {}

    /**
     * Result class for similar group queries.
     */
    public record SimilarGroup(
        String groupId,
        String fingerprint,
        String title,
        double score
    ) {}
}
