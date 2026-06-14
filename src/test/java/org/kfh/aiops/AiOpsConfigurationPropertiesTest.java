package org.kfh.aiops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class AiOpsConfigurationPropertiesTest {

    @Test
    void shouldDisableRedisAndNeo4jHealthChecksByDefault() throws IOException {
        var properties = loadApplicationProperties();

        assertEquals("${REDIS_HEALTH_ENABLED:false}", properties.getProperty("management.health.redis.enabled"));
        assertEquals("${NEO4J_HEALTH_ENABLED:false}", properties.getProperty("management.health.neo4j.enabled"));
    }

    @Test
    void shouldKeepHttpsEnabledByDefaultAndAllowDevServerKeystorePasswordOverride() throws IOException {
        var properties = loadApplicationProperties();

        assertEquals("${SERVER_SSL_ENABLED:true}", properties.getProperty("server.ssl.enabled"));
        assertEquals(
                "${SERVER_SSL_KEY_STORE:file:src/main/resources/certs/UTVDISAP01_kfhtesting_local.pfx}",
                properties.getProperty("server.ssl.key-store"));
        var passwordPlaceholder = properties.getProperty("server.ssl.key-store-password");
        assertTrue(passwordPlaceholder.startsWith("${SERVER_SSL_KEY_STORE_PASSWORD:"));
        assertTrue(passwordPlaceholder.endsWith("}"));
        assertEquals("${SERVER_SSL_KEY_STORE_TYPE:PKCS12}", properties.getProperty("server.ssl.key-store-type"));
    }

    @Test
    void shouldEnableHttpsOnlyForHttpsLocalProfileWithExternalPassword() throws IOException {
        var properties = loadProperties("application-https-local.properties");

        assertEquals("true", properties.getProperty("server.ssl.enabled"));
        assertEquals(
                "${SERVER_SSL_KEY_STORE:file:src/main/resources/certs/UTVDISAP01_kfhtesting_local.pfx}",
                properties.getProperty("server.ssl.key-store"));
        assertEquals("${SERVER_SSL_KEY_STORE_PASSWORD}", properties.getProperty("server.ssl.key-store-password"));
        assertEquals("${SERVER_SSL_KEY_STORE_TYPE:PKCS12}", properties.getProperty("server.ssl.key-store-type"));
    }

    private Properties loadApplicationProperties() throws IOException {
        return loadProperties("application.properties");
    }

    private Properties loadProperties(String resourceName) throws IOException {
        var properties = new Properties();
        try (var input = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            properties.load(input);
        }
        return properties;
    }
}
