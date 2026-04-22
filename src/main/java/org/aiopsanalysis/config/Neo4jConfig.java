package org.aiopsanalysis.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Neo4j configuration for the AIOps platform.
 * Configures connection, transaction management, and enables repositories.
 */
@Configuration
@EnableNeo4jRepositories(basePackages = "org.aiopsanalysis.repository")
@EnableTransactionManagement
public class Neo4jConfig {

    @Value("${spring.neo4j.uri:bolt://localhost:7687}")
    private String neo4jUri;

    @Value("${spring.neo4j.authentication.username:neo4j}")
    private String neo4jUsername;

    @Value("${spring.neo4j.authentication.password:password}")
    private String neo4jPassword;

    @Value("${spring.neo4j.database:neo4j}")
    private String neo4jDatabase;

    /**
     * Create Neo4j driver bean.
     */
    @Bean
    public Driver neo4jDriver() {
        return GraphDatabase.driver(
            neo4jUri,
            AuthTokens.basic(neo4jUsername, neo4jPassword)
        );
    }

    /**
     * Configure database selection.
     */
    @Bean
    public DatabaseSelectionProvider databaseSelectionProvider() {
        return DatabaseSelectionProvider.createStaticDatabaseSelectionProvider(neo4jDatabase);
    }

    /**
     * Configure transaction manager for Neo4j.
     */
    @Bean
    public Neo4jTransactionManager transactionManager(Driver driver, DatabaseSelectionProvider databaseSelectionProvider) {
        return new Neo4jTransactionManager(driver, databaseSelectionProvider);
    }
}
