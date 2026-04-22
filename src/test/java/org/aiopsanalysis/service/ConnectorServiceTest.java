package org.aiopsanalysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.aiopsanalysis.domain.Connector.ConnectorType;
import org.aiopsanalysis.domain.postgres.Connector;
import org.aiopsanalysis.dto.ConnectorCreateRequest;
import org.aiopsanalysis.dto.ConnectorResponse;
import org.aiopsanalysis.repository.ConnectorRepository;
import org.aiopsanalysis.repository.ConnectorSecretRepository;
import org.aiopsanalysis.repository.AuditLogRepository;
import org.aiopsanalysis.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for ConnectorService with tenant isolation and RBAC enforcement.
 *
 * Tests cover:
 * - Tenant isolation validation
 * - RBAC permission enforcement
 * - Input validation
 * - Audit logging
 * - Error handling
 */
@ExtendWith(MockitoExtension.class)
@SpringJUnitConfig
class ConnectorServiceTest {

    @Mock
    private ConnectorRepository connectorRepository;

    @Mock
    private ConnectorSecretRepository connectorSecretRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ConnectorPluginService connectorPluginService;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private ObjectMapper objectMapper;

    private ConnectorService connectorService;
    private UUID tenantId;
    private UUID userId;
    private UUID connectorId;

    @BeforeEach
    void setUp() {
        connectorService = new ConnectorService(
                connectorRepository,
                connectorSecretRepository,
                auditLogRepository,
                outboxEventRepository,
                connectorPluginService,
                encryptionService,
                objectMapper
        );

        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        connectorId = UUID.randomUUID();
    }

    @Test
    @WithMockUser(authorities = {"config.connector.read"})
    void testGetConnectors_WithValidTenant_ReturnsConnectors() {
        // Given
        List<Connector> connectors = Arrays.asList(
                createTestConnector("BMC Prod"),
                createTestConnector("SolarWinds Dev")
        );
        Page<Connector> connectorPage = new PageImpl<>(connectors);

        when(connectorRepository.findByTenantIdOrderByUpdatedAtDesc(eq(tenantId), any(Pageable.class)))
                .thenReturn(connectorPage);

        // When
        Page<ConnectorResponse> result = connectorService.getConnectors(tenantId, Pageable.unpaged());

        // Then
        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        verify(connectorRepository).findByTenantIdOrderByUpdatedAtDesc(eq(tenantId), any(Pageable.class));
    }

    @Test
    void testGetConnectors_WithNullTenant_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            connectorService.getConnectors(null, Pageable.unpaged());
        });
    }

    @Test
    @WithMockUser(authorities = {"config.connector.read"})
    void testGetConnector_WithValidIds_ReturnsConnector() {
        // Given
        Connector connector = createTestConnector("BMC Prod");
        when(connectorRepository.findByConnectorIdAndTenantId(connectorId, tenantId))
                .thenReturn(Optional.of(connector));

        // When
        Optional<ConnectorResponse> result = connectorService.getConnector(tenantId, connectorId);

        // Then
        assertTrue(result.isPresent());
        assertEquals("BMC Prod", result.get().getName());
        verify(connectorRepository).findByConnectorIdAndTenantId(connectorId, tenantId);
    }

    @Test
    @WithMockUser(authorities = {"config.connector.read"})
    void testGetConnector_WithInvalidIds_ReturnsEmpty() {
        // Given
        when(connectorRepository.findByConnectorIdAndTenantId(connectorId, tenantId))
                .thenReturn(Optional.empty());

        // When
        Optional<ConnectorResponse> result = connectorService.getConnector(tenantId, connectorId);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    @WithMockUser(authorities = {"config.connector.write"})
    void testCreateConnector_WithValidRequest_CreatesSuccessfully() {
        // Given
        ConnectorCreateRequest request = createValidBMCRequest();
        Connector savedConnector = createTestConnector("BMC Prod");

        when(connectorRepository.existsByTenantIdAndName(tenantId, "BMC Prod")).thenReturn(false);
        when(connectorRepository.save(any(Connector.class))).thenReturn(savedConnector);
        when(encryptionService.encryptSecrets(anyMap())).thenReturn(Map.of("password", "encrypted"));

        // When
        ConnectorResponse result = connectorService.createConnector(
                tenantId, userId, request, "test-correlation-id");

        // Then
        assertNotNull(result);
        assertEquals("BMC Prod", result.getName());
        verify(connectorRepository).save(any(Connector.class));
        verify(auditLogRepository).save(any());
        verify(outboxEventRepository).save(any());
    }

    @Test
    @WithMockUser(authorities = {"config.connector.write"})
    void testCreateConnector_WithDuplicateName_ThrowsException() {
        // Given
        ConnectorCreateRequest request = createValidBMCRequest();
        when(connectorRepository.existsByTenantIdAndName(tenantId, "BMC Prod")).thenReturn(true);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            connectorService.createConnector(tenantId, userId, request, "test-correlation-id");
        });
    }

    @Test
    @WithMockUser(authorities = {"config.connector.write"})
    void testCreateConnector_WithInvalidBMCUrl_ThrowsException() {
        // Given
        ConnectorCreateRequest request = createValidBMCRequest();
        request.getConfig().put("baseUrl", "http://localhost:8080"); // Invalid URL

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            connectorService.createConnector(tenantId, userId, request, "test-correlation-id");
        });
    }

    @Test
    void testCreateConnector_WithoutPermission_ThrowsException() {
        // Given
        ConnectorCreateRequest request = createValidBMCRequest();

        // When & Then - Should fail due to missing @WithMockUser
        assertThrows(Exception.class, () -> {
            connectorService.createConnector(tenantId, userId, request, "test-correlation-id");
        });
    }

    @Test
    @WithMockUser(authorities = {"config.connector.write"})
    void testDeleteConnector_WithValidIds_DeletesSuccessfully() {
        // Given
        Connector connector = createTestConnector("BMC Prod");
        when(connectorRepository.findByConnectorIdAndTenantId(connectorId, tenantId))
                .thenReturn(Optional.of(connector));

        // When
        connectorService.deleteConnector(tenantId, userId, connectorId, "test-correlation-id");

        // Then
        verify(connectorRepository).deleteByConnectorIdAndTenantId(connectorId, tenantId);
        verify(auditLogRepository).save(any());
        verify(outboxEventRepository).save(any());
    }

    @Test
    @WithMockUser(authorities = {"config.connector.write"})
    void testDeleteConnector_WithInvalidIds_ThrowsException() {
        // Given
        when(connectorRepository.findByConnectorIdAndTenantId(connectorId, tenantId))
                .thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            connectorService.deleteConnector(tenantId, userId, connectorId, "test-correlation-id");
        });
    }

    // Test helper methods

    private Connector createTestConnector(String name) {
        // Create JsonNode for config using a real ObjectMapper
        ObjectMapper testMapper = new ObjectMapper();
        ObjectNode configNode = testMapper.createObjectNode();
        configNode.put("baseUrl", "https://bmc-prod.kfh.com");
        configNode.put("username", "test");

        return Connector.builder()
                .connectorId(UUID.randomUUID())
                .tenantId(tenantId)
                .type("bmc")
                .name(name)
                .enabled(true)
                .config(configNode)
                .build();
    }

    private ConnectorCreateRequest createValidBMCRequest() {
        ConnectorCreateRequest request = new ConnectorCreateRequest();
        request.setType(ConnectorType.BMC);
        request.setName("BMC Prod");
        request.setEnabled(true);

        Map<String, Object> config = new HashMap<>();
        config.put("baseUrl", "https://bmc-prod.kfh.com");
        config.put("username", "service-account");
        config.put("timeout", 30);
        request.setConfig(config);

        Map<String, String> secrets = new HashMap<>();
        secrets.put("password", "test-password");
        request.setSecrets(secrets);

        return request;
    }
}
