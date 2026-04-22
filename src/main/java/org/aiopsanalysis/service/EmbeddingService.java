package org.aiopsanalysis.service;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.EmbeddingItem;
import com.azure.ai.openai.models.Embeddings;
import com.azure.ai.openai.models.EmbeddingsOptions;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.aiopsanalysis.config.AzureOpenAIConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Embedding service with dual Azure OpenAI deployment support.
 * Features:
 * - Round-robin load balancing between deployments
 * - Circuit breaker for failover
 * - Caching by fingerprint to reduce API calls
 * - Batching support
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final OpenAIClient clientA;
    private final OpenAIClient clientB;
    private final AzureOpenAIConfig config;
    
    // Round-robin counter
    private final AtomicInteger requestCounter = new AtomicInteger(0);
    
    // Health status for each deployment
    private volatile boolean deploymentAHealthy = true;
    private volatile boolean deploymentBHealthy = true;

    public EmbeddingService(
            @Qualifier("embeddingClientA") OpenAIClient clientA,
            @Qualifier("embeddingClientB") OpenAIClient clientB,
            AzureOpenAIConfig config) {
        this.clientA = clientA;
        this.clientB = clientB;
        this.config = config;
    }

    /**
     * Generate embedding for a single text with caching.
     * Cache key is the fingerprint (if provided) or text hash.
     *
     * @param text The text to embed
     * @param cacheKey Optional cache key (e.g., fingerprintFamily)
     * @return float array embedding
     */
    @Cacheable(value = "embeddings", key = "#cacheKey != null ? #cacheKey : #text.hashCode()")
    public float[] generateEmbedding(String text, String cacheKey) {
        log.debug("Generating embedding for text (cacheKey: {})", cacheKey);
        
        List<String> texts = List.of(text);
        List<float[]> embeddings = generateEmbeddingsBatch(texts);
        
        return embeddings.isEmpty() ? new float[0] : embeddings.get(0);
    }

    /**
     * Generate embeddings for multiple texts in batch.
     * Uses round-robin with failover.
     *
     * @param texts List of texts to embed
     * @return List of embeddings (same order as input)
     */
    public List<float[]> generateEmbeddingsBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }
        
        log.info("Generating embeddings for {} texts", texts.size());
        
        // Determine which deployment to use (round-robin with failover)
        boolean useDeploymentA = selectDeployment();
        
        try {
            if (useDeploymentA) {
                return callDeploymentA(texts);
            } else {
                return callDeploymentB(texts);
            }
        } catch (Exception e) {
            log.warn("Primary deployment failed, attempting failover: {}", e.getMessage());
            
            // Failover to the other deployment
            try {
                if (useDeploymentA) {
                    deploymentAHealthy = false;
                    return callDeploymentB(texts);
                } else {
                    deploymentBHealthy = false;
                    return callDeploymentA(texts);
                }
            } catch (Exception failoverEx) {
                log.error("Both deployments failed", failoverEx);
                throw new RuntimeException("Failed to generate embeddings from both deployments", failoverEx);
            }
        }
    }

    /**
     * Select deployment using round-robin with health checks.
     */
    private boolean selectDeployment() {
        int count = requestCounter.incrementAndGet();
        
        // If one deployment is unhealthy, use the other
        if (!deploymentAHealthy && deploymentBHealthy) {
            return false;
        }
        if (!deploymentBHealthy && deploymentAHealthy) {
            return true;
        }
        
        // Both healthy: round-robin
        return count % 2 == 0;
    }

    /**
     * Call Deployment A with circuit breaker.
     */
    @CircuitBreaker(name = "embeddingDeploymentA", fallbackMethod = "deploymentAFallback")
    private List<float[]> callDeploymentA(List<String> texts) {
        log.debug("Calling embedding deployment A");
        return callOpenAI(clientA, config.getEmbeddingDeploymentNameA(), texts);
    }

    /**
     * Call Deployment B with circuit breaker.
     */
    @CircuitBreaker(name = "embeddingDeploymentB", fallbackMethod = "deploymentBFallback")
    private List<float[]> callDeploymentB(List<String> texts) {
        log.debug("Calling embedding deployment B");
        return callOpenAI(clientB, config.getEmbeddingDeploymentNameB(), texts);
    }

    /**
     * Call Azure OpenAI API for embeddings.
     */
    private List<float[]> callOpenAI(OpenAIClient client, String deploymentName, List<String> texts) {
        EmbeddingsOptions options = new EmbeddingsOptions(texts);
        Embeddings response = client.getEmbeddings(deploymentName, options);
        
        List<float[]> results = new ArrayList<>();
        for (EmbeddingItem item : response.getData()) {
            List<Float> embedding = item.getEmbedding();
            float[] floatArray = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                floatArray[i] = embedding.get(i);
            }
            results.add(floatArray);
        }
        
        return results;
    }

    /**
     * Fallback for Deployment A failure.
     */
    private List<float[]> deploymentAFallback(List<String> texts, Throwable t) {
        log.warn("Deployment A circuit breaker triggered, marking unhealthy: {}", t.getMessage());
        deploymentAHealthy = false;
        throw new RuntimeException("Deployment A unavailable", t);
    }

    /**
     * Fallback for Deployment B failure.
     */
    private List<float[]> deploymentBFallback(List<String> texts, Throwable t) {
        log.warn("Deployment B circuit breaker triggered, marking unhealthy: {}", t.getMessage());
        deploymentBHealthy = false;
        throw new RuntimeException("Deployment B unavailable", t);
    }

    /**
     * Calculate cosine similarity between two embeddings.
     */
    public double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Reset health status (for testing or periodic health checks).
     */
    public void resetHealthStatus() {
        deploymentAHealthy = true;
        deploymentBHealthy = true;
        log.info("Deployment health status reset");
    }

    /**
     * Get current health status.
     */
    public String getHealthStatus() {
        return String.format("Deployment A: %s, Deployment B: %s",
            deploymentAHealthy ? "HEALTHY" : "UNHEALTHY",
            deploymentBHealthy ? "HEALTHY" : "UNHEALTHY");
    }
}
