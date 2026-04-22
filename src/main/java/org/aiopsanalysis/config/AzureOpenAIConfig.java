package org.aiopsanalysis.config;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Azure OpenAI configuration for embeddings and GPT-5.2-Pro.
 * Supports two embedding deployments for load balancing.
 */
@Configuration
public class AzureOpenAIConfig {

    // Embedding Deployment A
    @Value("${azure.openai.embedding.deployment-a.endpoint}")
    private String embeddingEndpointA;

    @Value("${azure.openai.embedding.deployment-a.key}")
    private String embeddingKeyA;

    @Value("${azure.openai.embedding.deployment-a.deployment-name}")
    private String embeddingDeploymentNameA;

    // Embedding Deployment B
    @Value("${azure.openai.embedding.deployment-b.endpoint}")
    private String embeddingEndpointB;

    @Value("${azure.openai.embedding.deployment-b.key}")
    private String embeddingKeyB;

    @Value("${azure.openai.embedding.deployment-b.deployment-name}")
    private String embeddingDeploymentNameB;

    // GPT-5.2-Pro Configuration
    @Value("${azure.openai.gpt.endpoint}")
    private String gptEndpoint;

    @Value("${azure.openai.gpt.key}")
    private String gptKey;

    @Value("${azure.openai.gpt.deployment-name}")
    private String gptDeploymentName;

    // Common settings
    @Value("${azure.openai.embedding.model:text-embedding-ada-002}")
    private String embeddingModel;

    @Value("${azure.openai.embedding.dimension:1536}")
    private int embeddingDimension;

    // Getters
    public String getEmbeddingEndpointA() { return embeddingEndpointA; }
    public String getEmbeddingKeyA() { return embeddingKeyA; }
    public String getEmbeddingDeploymentNameA() { return embeddingDeploymentNameA; }
    public String getEmbeddingEndpointB() { return embeddingEndpointB; }
    public String getEmbeddingKeyB() { return embeddingKeyB; }
    public String getEmbeddingDeploymentNameB() { return embeddingDeploymentNameB; }
    public String getGptEndpoint() { return gptEndpoint; }
    public String getGptKey() { return gptKey; }
    public String getGptDeploymentName() { return gptDeploymentName; }
    public String getEmbeddingModel() { return embeddingModel; }
    public int getEmbeddingDimension() { return embeddingDimension; }

    /**
     * Create OpenAI client for Embedding Deployment A.
     */
    @Bean(name = "embeddingClientA")
    public OpenAIClient embeddingClientA() {
        return new OpenAIClientBuilder()
            .endpoint(embeddingEndpointA)
            .credential(new AzureKeyCredential(embeddingKeyA))
            .buildClient();
    }

    /**
     * Create OpenAI client for Embedding Deployment B.
     */
    @Bean(name = "embeddingClientB")
    public OpenAIClient embeddingClientB() {
        return new OpenAIClientBuilder()
            .endpoint(embeddingEndpointB)
            .credential(new AzureKeyCredential(embeddingKeyB))
            .buildClient();
    }

    /**
     * Create OpenAI client for GPT-5.2-Pro reasoning.
     */
    @Bean(name = "gptClient")
    public OpenAIClient gptClient() {
        return new OpenAIClientBuilder()
            .endpoint(gptEndpoint)
            .credential(new AzureKeyCredential(gptKey))
            .buildClient();
    }
}
