package org.aiopsanalysis.service;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Service for managing Neo4j schema - constraints, indexes, and vector indexes.
 * Ensures schema is created on application startup.
 */
@Service
public class Neo4jSchemaService {

    private static final Logger log = LoggerFactory.getLogger(Neo4jSchemaService.class);

    private final Driver driver;

    public Neo4jSchemaService(Driver driver) {
        this.driver = driver;
    }

    @Value("${aiops.neo4j.embedding-dimension:1536}")
    private int embeddingDimension;

    @Value("${aiops.neo4j.vector-similarity:cosine}")
    private String vectorSimilarity;

    /**
     * Initialize Neo4j schema on application startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureSchema() {
        log.info("Initializing Neo4j schema...");
        
        try (Session session = driver.session()) {
            // Create constraints for uniqueness
            createConstraints(session);
            
            // Create indexes for query performance
            createIndexes(session);
            
            // Create vector index for embedding similarity search
            createVectorIndex(session);
            
            log.info("Neo4j schema initialization completed successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Neo4j schema", e);
            throw new RuntimeException("Neo4j schema initialization failed", e);
        }
    }

    /**
     * Create uniqueness constraints on node IDs.
     */
    private void createConstraints(Session session) {
        log.info("Creating uniqueness constraints...");
        
        // AlertGroup constraint
        executeIfNotExists(session, 
            "CREATE CONSTRAINT alertgroup_id IF NOT EXISTS FOR (g:AlertGroup) REQUIRE g.id IS UNIQUE");
        
        // AlertOccurrence constraint
        executeIfNotExists(session,
            "CREATE CONSTRAINT alertoccurrence_id IF NOT EXISTS FOR (o:AlertOccurrence) REQUIRE o.id IS UNIQUE");
        
        // Resource constraint
        executeIfNotExists(session,
            "CREATE CONSTRAINT resource_id IF NOT EXISTS FOR (r:Resource) REQUIRE r.id IS UNIQUE");
        
        // App constraint
        executeIfNotExists(session,
            "CREATE CONSTRAINT app_id IF NOT EXISTS FOR (a:App) REQUIRE a.id IS UNIQUE");
        
        // Service constraint
        executeIfNotExists(session,
            "CREATE CONSTRAINT service_id IF NOT EXISTS FOR (s:Service) REQUIRE s.id IS UNIQUE");
        
        // Run constraint
        executeIfNotExists(session,
            "CREATE CONSTRAINT run_id IF NOT EXISTS FOR (r:Run) REQUIRE r.runId IS UNIQUE");
        
        log.info("Uniqueness constraints created");
    }

    /**
     * Create indexes for query performance.
     */
    private void createIndexes(Session session) {
        log.info("Creating performance indexes...");
        
        // AlertGroup indexes
        executeIfNotExists(session,
            "CREATE INDEX alertgroup_fingerprint_exact IF NOT EXISTS FOR (g:AlertGroup) ON (g.fingerprintExact)");
        executeIfNotExists(session,
            "CREATE INDEX alertgroup_fingerprint_family IF NOT EXISTS FOR (g:AlertGroup) ON (g.fingerprintFamily)");
        executeIfNotExists(session,
            "CREATE INDEX alertgroup_last_seen IF NOT EXISTS FOR (g:AlertGroup) ON (g.lastSeenEpoch)");
        executeIfNotExists(session,
            "CREATE INDEX alertgroup_severity IF NOT EXISTS FOR (g:AlertGroup) ON (g.severityMax)");
        
        // AlertOccurrence indexes
        executeIfNotExists(session,
            "CREATE INDEX alertoccurrence_ts IF NOT EXISTS FOR (o:AlertOccurrence) ON (o.tsEpoch)");
        executeIfNotExists(session,
            "CREATE INDEX alertoccurrence_run IF NOT EXISTS FOR (o:AlertOccurrence) ON (o.runId)");
        executeIfNotExists(session,
            "CREATE INDEX alertoccurrence_resource IF NOT EXISTS FOR (o:AlertOccurrence) ON (o.resourceId)");
        
        // Resource indexes
        executeIfNotExists(session,
            "CREATE INDEX resource_type IF NOT EXISTS FOR (r:Resource) ON (r.type)");
        executeIfNotExists(session,
            "CREATE INDEX resource_name IF NOT EXISTS FOR (r:Resource) ON (r.name)");
        
        // Run indexes
        executeIfNotExists(session,
            "CREATE INDEX run_ts IF NOT EXISTS FOR (r:Run) ON (r.tsEpoch)");
        
        log.info("Performance indexes created");
    }

    /**
     * Create vector index for embedding similarity search.
     */
    private void createVectorIndex(Session session) {
        log.info("Creating vector index for AlertGroup embeddings (dimension: {}, similarity: {})...", 
            embeddingDimension, vectorSimilarity);
        
        String vectorIndexQuery = String.format("""
            CREATE VECTOR INDEX alertgroup_embedding IF NOT EXISTS
            FOR (g:AlertGroup)
            ON (g.embedding)
            OPTIONS {
                indexConfig: {
                    `vector.dimensions`: %d,
                    `vector.similarity_function`: '%s'
                }
            }
            """, embeddingDimension, vectorSimilarity);
        
        executeIfNotExists(session, vectorIndexQuery);
        
        log.info("Vector index created");
    }

    /**
     * Execute a Cypher statement, logging any errors but not failing.
     */
    private void executeIfNotExists(Session session, String cypher) {
        try {
            session.run(cypher);
        } catch (Exception e) {
            // Index/constraint might already exist
            log.debug("Schema element may already exist: {}", e.getMessage());
        }
    }

    /**
     * Drop all schema elements (for testing/reset purposes).
     */
    public void dropSchema() {
        log.warn("Dropping all Neo4j schema elements...");
        
        try (Session session = driver.session()) {
            // Drop constraints
            session.run("DROP CONSTRAINT alertgroup_id IF EXISTS");
            session.run("DROP CONSTRAINT alertoccurrence_id IF EXISTS");
            session.run("DROP CONSTRAINT resource_id IF EXISTS");
            session.run("DROP CONSTRAINT app_id IF EXISTS");
            session.run("DROP CONSTRAINT service_id IF EXISTS");
            session.run("DROP CONSTRAINT run_id IF EXISTS");
            
            // Drop indexes
            session.run("DROP INDEX alertgroup_fingerprint_exact IF EXISTS");
            session.run("DROP INDEX alertgroup_fingerprint_family IF EXISTS");
            session.run("DROP INDEX alertgroup_last_seen IF EXISTS");
            session.run("DROP INDEX alertgroup_severity IF EXISTS");
            session.run("DROP INDEX alertoccurrence_ts IF EXISTS");
            session.run("DROP INDEX alertoccurrence_run IF EXISTS");
            session.run("DROP INDEX alertoccurrence_resource IF EXISTS");
            session.run("DROP INDEX resource_type IF EXISTS");
            session.run("DROP INDEX resource_name IF EXISTS");
            session.run("DROP INDEX run_ts IF EXISTS");
            session.run("DROP INDEX alertgroup_embedding IF EXISTS");
            
            log.info("Schema dropped");
        }
    }
}
