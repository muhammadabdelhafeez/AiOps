package org.aiopsanalysis.service;

import org.aiopsanalysis.domain.model.CanonicalAlert;
import org.aiopsanalysis.domain.model.Classification;
import org.aiopsanalysis.domain.model.Severity;
import org.aiopsanalysis.domain.neo4j.AlertGroupNode;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service for managing AlertGroups in Neo4j.
 * Handles:
 * - Exact fingerprint matching (deterministic: RECURRING_SURE)
 * - Family fingerprint matching (deterministic: RECURRING_LIKELY)
 * - Vector similarity matching (POSSIBLE_RECURRING)
 * - Group creation and update
 */
@Service
public class AlertGroupService {

    private static final Logger log = LoggerFactory.getLogger(AlertGroupService.class);

    private final Driver driver;
    private final EmbeddingService embeddingService;

    public AlertGroupService(Driver driver, EmbeddingService embeddingService) {
        this.driver = driver;
        this.embeddingService = embeddingService;
    }

    @Value("${aiops.retention.window-days:15}")
    private int windowDays;

    @Value("${aiops.similarity.threshold:0.85}")
    private double similarityThreshold;

    /**
     * Classify an alert and return/create the appropriate AlertGroup.
     * Classification priority:
     * 1. Exact fingerprint match -> RECURRING_SURE
     * 2. Family fingerprint match -> RECURRING_LIKELY
     * 3. Vector similarity match -> POSSIBLE_RECURRING
     * 4. No match -> NEW
     */
    public ClassificationResult classifyAlert(CanonicalAlert alert) {
        log.debug("Classifying alert: {} (fingerprint: {})", alert.getId(), alert.getFingerprintExact());
        
        long windowStartEpoch = Instant.now().minus(windowDays, ChronoUnit.DAYS).toEpochMilli();
        
        // 1. Try exact fingerprint match
        Optional<AlertGroupNode> exactMatch = queryExactFingerprint(alert.getFingerprintExact(), windowStartEpoch);
        if (exactMatch.isPresent()) {
            log.debug("Exact fingerprint match found for alert {}", alert.getId());
            return new ClassificationResult(
                Classification.RECURRING_SURE,
                exactMatch.get(),
                "Exact fingerprint match (first seen: " + Instant.ofEpochMilli(exactMatch.get().getFirstSeenEpoch()) + ")"
            );
        }
        
        // 2. Try family fingerprint match
        Optional<AlertGroupNode> familyMatch = queryFamilyFingerprint(alert.getFingerprintFamily(), windowStartEpoch);
        if (familyMatch.isPresent()) {
            log.debug("Family fingerprint match found for alert {}", alert.getId());
            return new ClassificationResult(
                Classification.RECURRING_LIKELY,
                familyMatch.get(),
                "Family pattern match (same alert type on similar resource)"
            );
        }
        
        // 3. Try vector similarity match (if embedding exists)
        if (alert.buildSignatureText() != null && !alert.buildSignatureText().isBlank()) {
            Optional<AlertGroupNode> similarMatch = queryVectorSimilar(alert, windowStartEpoch);
            if (similarMatch.isPresent()) {
                log.debug("Vector similarity match found for alert {}", alert.getId());
                return new ClassificationResult(
                    Classification.POSSIBLE_RECURRING,
                    similarMatch.get(),
                    "Semantic similarity match (threshold: " + similarityThreshold + ")"
                );
            }
        }
        
        // 4. No match - this is a new alert pattern
        log.debug("No match found, creating new group for alert {}", alert.getId());
        AlertGroupNode newGroup = createNewGroup(alert);
        return new ClassificationResult(
            Classification.NEW,
            newGroup,
            "No prior occurrence in " + windowDays + "-day window"
        );
    }

    /**
     * Query for exact fingerprint match within time window.
     */
    public Optional<AlertGroupNode> queryExactFingerprint(String fingerprintExact, long windowStartEpoch) {
        String cypher = """
            MATCH (g:AlertGroup {fingerprintExact: $fingerprint})
            WHERE g.lastSeenEpoch >= $windowStart
            RETURN g
            ORDER BY g.lastSeenEpoch DESC
            LIMIT 1
            """;
        
        try (Session session = driver.session()) {
            Result result = session.run(cypher, 
                Values.parameters("fingerprint", fingerprintExact, "windowStart", windowStartEpoch));
            
            if (result.hasNext()) {
                Record record = result.next();
                return Optional.of(mapToAlertGroupNode(record));
            }
        }
        return Optional.empty();
    }

    /**
     * Query for family fingerprint match within time window.
     */
    public Optional<AlertGroupNode> queryFamilyFingerprint(String fingerprintFamily, long windowStartEpoch) {
        String cypher = """
            MATCH (g:AlertGroup {fingerprintFamily: $fingerprint})
            WHERE g.lastSeenEpoch >= $windowStart
            RETURN g
            ORDER BY g.count15d DESC, g.lastSeenEpoch DESC
            LIMIT 1
            """;
        
        try (Session session = driver.session()) {
            Result result = session.run(cypher,
                Values.parameters("fingerprint", fingerprintFamily, "windowStart", windowStartEpoch));
            
            if (result.hasNext()) {
                Record record = result.next();
                return Optional.of(mapToAlertGroupNode(record));
            }
        }
        return Optional.empty();
    }

    /**
     * Query for vector similarity match using Neo4j vector index.
     */
    public Optional<AlertGroupNode> queryVectorSimilar(CanonicalAlert alert, long windowStartEpoch) {
        // Generate embedding for the alert
        float[] embedding = embeddingService.generateEmbedding(
            alert.buildSignatureText(), 
            alert.getFingerprintFamily()
        );
        
        if (embedding == null || embedding.length == 0) {
            return Optional.empty();
        }
        
        String cypher = """
            CALL db.index.vector.queryNodes('alertgroup_embedding', 5, $embedding)
            YIELD node, score
            WHERE score >= $threshold AND node.lastSeenEpoch >= $windowStart
            RETURN node, score
            ORDER BY score DESC
            LIMIT 1
            """;
        
        try (Session session = driver.session()) {
            Result result = session.run(cypher,
                Values.parameters(
                    "embedding", embedding,
                    "threshold", similarityThreshold,
                    "windowStart", windowStartEpoch
                ));
            
            if (result.hasNext()) {
                Record record = result.next();
                return Optional.of(mapToAlertGroupNode(record));
            }
        } catch (Exception e) {
            log.warn("Vector similarity query failed (index may not exist): {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Create a new AlertGroup for a new alert pattern.
     */
    public AlertGroupNode createNewGroup(CanonicalAlert alert) {
        long now = Instant.now().toEpochMilli();
        String groupId = "GRP:" + alert.getFingerprintExact();
        
        // Generate embedding for the new group
        float[] embedding = embeddingService.generateEmbedding(
            alert.buildSignatureText(),
            alert.getFingerprintFamily()
        );
        
        String cypher = """
            CREATE (g:AlertGroup {
                id: $id,
                fingerprintExact: $fingerprintExact,
                fingerprintFamily: $fingerprintFamily,
                alertTitle: $alertTitle,
                source: $source,
                severityMax: $severity,
                status: 'ACTIVE',
                classification: 'NEW',
                firstSeenEpoch: $now,
                lastSeenEpoch: $now,
                count15d: 1,
                countTotal: 1,
                signatureText: $signatureText,
                embedding: $embedding,
                embeddingModel: $embeddingModel,
                embeddingDim: $embeddingDim,
                lastRunId: $runId
            })
            RETURN g
            """;
        
        try (Session session = driver.session()) {
            Result result = session.run(cypher,
                Values.parameters(
                    "id", groupId,
                    "fingerprintExact", alert.getFingerprintExact(),
                    "fingerprintFamily", alert.getFingerprintFamily(),
                    "alertTitle", alert.getAlertTitle(),
                    "source", alert.getSource(),
                    "severity", alert.getSeverity().name(),
                    "now", now,
                    "signatureText", alert.buildSignatureText(),
                    "embedding", embedding,
                    "embeddingModel", "text-embedding-ada-002",
                    "embeddingDim", embedding != null ? embedding.length : 0,
                    "runId", alert.getRunId()
                ));
            
            if (result.hasNext()) {
                return mapToAlertGroupNode(result.next());
            }
        }
        
        throw new RuntimeException("Failed to create AlertGroup");
    }

    /**
     * Update an existing AlertGroup with new occurrence.
     */
    public void updateGroup(AlertGroupNode group, CanonicalAlert alert) {
        long now = Instant.now().toEpochMilli();
        
        String cypher = """
            MATCH (g:AlertGroup {id: $id})
            SET g.lastSeenEpoch = $now,
                g.count15d = g.count15d + 1,
                g.countTotal = g.countTotal + 1,
                g.severityMax = CASE WHEN $severityWeight > g.severityMax THEN $severity ELSE g.severityMax END,
                g.lastRunId = $runId
            RETURN g
            """;
        
        try (Session session = driver.session()) {
            session.run(cypher,
                Values.parameters(
                    "id", group.getId(),
                    "now", now,
                    "severityWeight", alert.getSeverity().getWeight(),
                    "severity", alert.getSeverity().name(),
                    "runId", alert.getRunId()
                ));
        }
    }

    /**
     * Map Neo4j record to AlertGroupNode.
     */
    private AlertGroupNode mapToAlertGroupNode(Record record) {
        var node = record.get("g").asNode();
        
        return AlertGroupNode.builder()
            .id(node.get("id").asString())
            .fingerprintExact(node.get("fingerprintExact").asString(null))
            .fingerprintFamily(node.get("fingerprintFamily").asString(null))
            .alertTitle(node.get("alertTitle").asString(null))
            .source(node.get("source").asString(null))
            .severityMax(Severity.valueOf(node.get("severityMax").asString("INFO")))
            .firstSeenEpoch(node.get("firstSeenEpoch").asLong(0))
            .lastSeenEpoch(node.get("lastSeenEpoch").asLong(0))
            .count15d(node.get("count15d").asInt(0))
            .countTotal(node.get("countTotal").asInt(0))
            .signatureText(node.get("signatureText").asString(null))
            .lastRunId(node.get("lastRunId").asString(null))
            .lastSummary(node.get("lastSummary").asString(null))
            .build();
    }

    /**
     * Result of classification operation.
     */
    public record ClassificationResult(
        Classification classification,
        AlertGroupNode group,
        String reason
    ) {}
}
