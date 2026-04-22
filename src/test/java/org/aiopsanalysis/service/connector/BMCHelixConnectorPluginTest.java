package org.aiopsanalysis.service.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.aiopsanalysis.config.SsrfValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for BMCHelixConnectorPlugin.
 * Tests configuration validation, SSRF protection, and schema generation.
 */
@ExtendWith(MockitoExtension.class)
class BMCHelixConnectorPluginTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private SsrfValidator ssrfValidator;

    private BMCHelixConnectorPlugin plugin;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        plugin = new BMCHelixConnectorPlugin(webClientBuilder, ssrfValidator, objectMapper);
    }

    @Test
    @DisplayName("Plugin should have correct type and metadata")
    void testPluginMetadata() {
        assertEquals("BMC", plugin.getType());
        assertEquals("BMC Helix ITSM", plugin.getDisplayName());
        assertEquals("1.0.0", plugin.getVersion());
        assertNotNull(plugin.getDescription());
    }

    @Nested
    @DisplayName("Configuration Schema Tests")
    class ConfigSchemaTests {

        @Test
        @DisplayName("Should provide valid configuration schema")
        void testGetConfigSchema() {
            ConnectorPlugin.ConfigSchema schema = plugin.getConfigSchema();

            assertNotNull(schema);
            assertNotNull(schema.configFields());
            assertNotNull(schema.secretFields());

            // Check required config fields exist
            List<String> configFieldNames = schema.configFields().stream()
                    .map(ConnectorPlugin.FieldDefinition::name)
                    .toList();
            assertTrue(configFieldNames.contains("endpointBaseUrl"));
            assertTrue(configFieldNames.contains("method"));
            assertTrue(configFieldNames.contains("env"));
            assertTrue(configFieldNames.contains("domain"));

            // Check secret fields exist
            List<String> secretFieldNames = schema.secretFields().stream()
                    .map(ConnectorPlugin.FieldDefinition::name)
                    .toList();
            assertTrue(secretFieldNames.contains("authType"));
            assertTrue(secretFieldNames.contains("username"));
            assertTrue(secretFieldNames.contains("password"));
        }

        @Test
        @DisplayName("Should mark required fields correctly")
        void testRequiredFields() {
            ConnectorPlugin.ConfigSchema schema = plugin.getConfigSchema();

            // endpointBaseUrl should be required
            var baseUrlField = schema.configFields().stream()
                    .filter(f -> f.name().equals("endpointBaseUrl"))
                    .findFirst()
                    .orElseThrow();
            assertTrue(baseUrlField.required());

            // notes should be optional
            var notesField = schema.configFields().stream()
                    .filter(f -> f.name().equals("notes"))
                    .findFirst()
                    .orElseThrow();
            assertFalse(notesField.required());
        }
    }

    @Nested
    @DisplayName("Configuration Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should pass validation for valid config")
        void testValidConfig() {
            ObjectNode config = objectMapper.createObjectNode();
            config.put("endpointBaseUrl", "https://bmc-helix.example.com");
            config.put("method", "API");
            config.put("env", "Prod");
            config.put("domain", "Infra");

            when(ssrfValidator.validate(anyString()))
                    .thenReturn(SsrfValidator.ValidationResult.success());

            List<String> errors = plugin.validateConfig(config);
            assertTrue(errors.isEmpty(), "Expected no validation errors but got: " + errors);
        }

        @Test
        @DisplayName("Should fail validation when required fields are missing")
        void testMissingRequiredFields() {
            ObjectNode config = objectMapper.createObjectNode();
            // Missing all required fields

            List<String> errors = plugin.validateConfig(config);

            assertFalse(errors.isEmpty());
            assertTrue(errors.stream().anyMatch(e -> e.contains("Base URL")));
            assertTrue(errors.stream().anyMatch(e -> e.contains("method")));
            assertTrue(errors.stream().anyMatch(e -> e.contains("Environment")));
            assertTrue(errors.stream().anyMatch(e -> e.contains("Domain")));
        }

        @Test
        @DisplayName("Should fail validation for SSRF-blocked URLs")
        void testSsrfBlockedUrl() {
            ObjectNode config = objectMapper.createObjectNode();
            config.put("endpointBaseUrl", "http://169.254.169.254/");
            config.put("method", "API");
            config.put("env", "Prod");
            config.put("domain", "Infra");

            when(ssrfValidator.validate("http://169.254.169.254/"))
                    .thenReturn(SsrfValidator.ValidationResult.invalid("Blocked metadata IP"));

            List<String> errors = plugin.validateConfig(config);

            assertFalse(errors.isEmpty());
            assertTrue(errors.stream().anyMatch(e -> e.contains("SSRF") || e.contains("Invalid URL")));
        }
    }

    @Nested
    @DisplayName("Test Result Factory Methods")
    class TestResultTests {

        @Test
        @DisplayName("Success result should have correct properties")
        void testSuccessResult() {
            ConnectorPlugin.TestResult result = ConnectorPlugin.TestResult.success(200, 150L, "OK");

            assertTrue(result.success());
            assertEquals(200, result.httpStatus());
            assertEquals(150L, result.latencyMs());
            assertEquals("OK", result.message());
        }

        @Test
        @DisplayName("Failure result should have correct properties")
        void testFailureResult() {
            ConnectorPlugin.TestResult result = ConnectorPlugin.TestResult.failure(401, 100L, "Unauthorized", null);

            assertFalse(result.success());
            assertEquals(401, result.httpStatus());
            assertEquals(100L, result.latencyMs());
            assertEquals("Unauthorized", result.message());
        }
    }

    @Nested
    @DisplayName("Collect Result Factory Methods")
    class CollectResultTests {

        @Test
        @DisplayName("Success result should have correct properties")
        void testCollectSuccess() {
            ConnectorPlugin.CollectResult result = ConnectorPlugin.CollectResult.success(100, 95, 5, 2000L, "https://sharepoint/artifact");

            assertTrue(result.success());
            assertEquals(100, result.alertsPulled());
            assertEquals(95, result.alertsNormalized());
            assertEquals(5, result.errors());
            assertEquals(2000L, result.durationMs());
            assertEquals("https://sharepoint/artifact", result.artifactUrl());
        }

        @Test
        @DisplayName("Failure result should have correct properties")
        void testCollectFailure() {
            ConnectorPlugin.CollectResult result = ConnectorPlugin.CollectResult.failure("Connection timeout", 1, 30000L);

            assertFalse(result.success());
            assertEquals(0, result.alertsPulled());
            assertEquals(0, result.alertsNormalized());
            assertEquals(1, result.errors());
            assertEquals(30000L, result.durationMs());
            assertEquals("Connection timeout", result.message());
        }
    }
}
