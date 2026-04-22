package org.aiopsanalysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aiopsanalysis.domain.Connector.ConnectorType;
import org.aiopsanalysis.domain.postgres.Connector;
import org.aiopsanalysis.domain.postgres.ConnectorSecret;
import org.aiopsanalysis.domain.postgres.AuditLog;
import org.aiopsanalysis.domain.postgres.OutboxEvent;
import org.aiopsanalysis.dto.ConnectorCreateRequest;
import org.aiopsanalysis.dto.ConnectorResponse;
import org.aiopsanalysis.dto.ConnectorTestRequest;
import org.aiopsanalysis.repository.ConnectorRepository;
import org.aiopsanalysis.repository.ConnectorSecretRepository;
import org.aiopsanalysis.repository.AuditLogRepository;
import org.aiopsanalysis.repository.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing connector configurations with RBAC enforcement.
 * All methods enforce tenant isolation and audit logging.
 *
 * RBAC Permissions:
 * - config.connector.read: view connectors
 * - config.connector.write: create/update/delete connectors
 * - config.connector.test: test connector connections
 */
@Service
@Transactional
public class ConnectorService {

    private final ConnectorRepository connectorRepository;
    private final ConnectorSecretRepository connectorSecretRepository;
    private final AuditLogRepository auditLogRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ConnectorPluginService connectorPluginService;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ConnectorService(
            ConnectorRepository connectorRepository,
            ConnectorSecretRepository connectorSecretRepository,
            AuditLogRepository auditLogRepository,
            OutboxEventRepository outboxEventRepository,
            ConnectorPluginService connectorPluginService,
            EncryptionService encryptionService,
            ObjectMapper objectMapper) {
        this.connectorRepository = connectorRepository;
        this.connectorSecretRepository = connectorSecretRepository;
        this.auditLogRepository = auditLogRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.connectorPluginService = connectorPluginService;
        this.encryptionService = encryptionService;
        this.objectMapper = objectMapper;
    }

    /**
     * Get all connectors for a tenant.
     * TENANT ISOLATION ENFORCED.
     */
    @PreAuthorize("hasAuthority('config.connector.read')")
    @Transactional(readOnly = true)
    public Page<ConnectorResponse> getConnectors(UUID tenantId, Pageable pageable) {
        validateTenantId(tenantId);

        Page<Connector> connectors = connectorRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId, pageable);
        return connectors.map(this::toConnectorResponse);
    }

    /**
     * Get connector by ID.
     * TENANT ISOLATION + RBAC ENFORCED.
     */
    @PreAuthorize("hasAuthority('config.connector.read')")
    @Transactional(readOnly = true)
    public Optional<ConnectorResponse> getConnector(UUID tenantId, UUID connectorId) {
        validateTenantId(tenantId);
        validateConnectorId(connectorId);

        return connectorRepository.findByConnectorIdAndTenantId(connectorId, tenantId)
                .map(this::toConnectorResponse);
    }

    /**
     * Create new connector.
     * TENANT ISOLATION + RBAC + AUDIT + OUTBOX ENFORCED.
     */
    @PreAuthorize("hasAuthority('config.connector.write')")
    @Transactional
    public ConnectorResponse createConnector(UUID tenantId, UUID userId,
                                           @Valid ConnectorCreateRequest request,
                                           String correlationId) {
        validateTenantId(tenantId);
        validateUserId(userId);
        validateCreateRequest(request, tenantId);

        // Plugin-specific validation
        connectorPluginService.validateConfiguration(request.getType().name(), request.getConfig(), request.getSecrets());

        // Create connector entity
        Connector connector = Connector.builder()
                .tenantId(tenantId)
                .type(request.getType().name().toLowerCase())
                .name(request.getName())
                .enabled(request.getEnabled())
                .config(objectMapper.valueToTree(maskSecretsFromConfig(request.getConfig())))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Connector savedConnector = connectorRepository.save(connector);

        // Store encrypted secrets separately
        if (request.getSecrets() != null && !request.getSecrets().isEmpty()) {
            Map<String, Object> encryptedSecrets = encryptionService.encryptSecrets(request.getSecrets());

            ConnectorSecret secret = ConnectorSecret.builder()
                    .connectorId(savedConnector.getConnectorId())
                    .tenantId(tenantId)
                    .secretEnc(convertToJsonNode(encryptedSecrets))
                    .updatedAt(Instant.now())
                    .build();

            connectorSecretRepository.save(secret);
        }

        // Audit logging
        logAudit(tenantId, userId, "CREATE_CONNECTOR", "connector",
                savedConnector.getConnectorId().toString(), null,
                "Created connector: " + savedConnector.getName(), correlationId);

        // Emit outbox event for async processing
        emitConnectorEvent("CONNECTOR_CREATED", savedConnector, tenantId, correlationId);

        return toConnectorResponse(savedConnector);
    }

    /**
     * Test connector connection.
     * RBAC + OUTBOX (async) ENFORCED.
     */
    @PreAuthorize("hasAuthority('config.connector.test')")
    @Transactional
    public CompletableFuture<Map<String, Object>> testConnector(UUID tenantId, UUID userId,
                                                               @Valid ConnectorTestRequest request,
                                                               String correlationId) {
        validateTenantId(tenantId);
        validateUserId(userId);

        Map<String, Object> config = request.getConfig();
        Map<String, String> secrets = request.getSecrets();

        // If testing existing connector, load stored configuration
        if (request.getConnectorId() != null) {
            Optional<Connector> existingConnector = connectorRepository
                    .findByConnectorIdAndTenantId(request.getConnectorId(), tenantId);

            if (existingConnector.isEmpty()) {
                throw new IllegalArgumentException("Connector not found or access denied");
            }

            Connector connector = existingConnector.get();

            // Use stored config if not overridden
            if (config == null || config.isEmpty()) {
                config = convertFromJsonNode(connector.getConfig());
            }

            // Use stored secrets if not overridden
            if (secrets == null || secrets.isEmpty()) {
                Optional<ConnectorSecret> connectorSecret = connectorSecretRepository
                        .findByConnectorIdAndTenantId(connector.getConnectorId(), tenantId);

                if (connectorSecret.isPresent()) {
                    Map<String, Object> decryptedSecrets = encryptionService
                            .decryptSecrets(convertFromJsonNode(connectorSecret.get().getSecretEnc()));
                    secrets = new HashMap<>();
                    for (Map.Entry<String, Object> entry : decryptedSecrets.entrySet()) {
                        secrets.put(entry.getKey(), entry.getValue().toString());
                    }
                }
            }
        }

        // Plugin-specific validation
        connectorPluginService.validateConfiguration(request.getType().name(), config, secrets);

        // Emit outbox event for async testing (prevents blocking UI)
        Map<String, Object> testEventData = new HashMap<>();
        testEventData.put("type", request.getType().name());
        testEventData.put("config", maskSecretsFromConfig(config));
        testEventData.put("timeoutSeconds", request.getTimeoutSeconds());
        testEventData.put("correlationId", correlationId);

        String aggregateId = request.getConnectorId() != null
                ? request.getConnectorId().toString()
                : UUID.randomUUID().toString();

        OutboxEvent testEvent = OutboxEvent.builder()
                .tenantId(tenantId)
                .aggregateType("CONNECTOR")
                .aggregateId(aggregateId)
                .eventType("CONNECTOR_TEST_REQUESTED")
                .payload(convertToJsonNode(testEventData))
                .createdAt(Instant.now())
                .build();

        outboxEventRepository.save(testEvent);

        // Return immediate response (actual test runs async)
        return CompletableFuture.completedFuture(Map.of(
                "status", "test_queued",
                "message", "Connection test queued for async execution",
                "correlationId", correlationId,
                "estimatedDurationSeconds", request.getTimeoutSeconds()
        ));
    }

    /**
     * Update connector configuration.
     */
    @PreAuthorize("hasAuthority('config.connector.write')")
    @Transactional
    public ConnectorResponse updateConnector(UUID tenantId, UUID userId, UUID connectorId,
                                           @Valid ConnectorCreateRequest request,
                                           String correlationId) {
        validateTenantId(tenantId);
        validateUserId(userId);
        validateConnectorId(connectorId);

        Optional<Connector> existingConnector = connectorRepository
                .findByConnectorIdAndTenantId(connectorId, tenantId);

        if (existingConnector.isEmpty()) {
            throw new IllegalArgumentException("Connector not found or access denied");
        }

        Connector connector = existingConnector.get();
        String beforeSummary = "Connector: " + connector.getName() + ", Enabled: " + connector.getEnabled();

        // Plugin-specific validation
        connectorPluginService.validateConfiguration(request.getType().name(), request.getConfig(), request.getSecrets());

        // Update configuration
        connector.setType(request.getType().name().toLowerCase());
        connector.setName(request.getName());
        connector.setEnabled(request.getEnabled());
        connector.setConfig(convertToJsonNode(maskSecretsFromConfig(request.getConfig())));
        connector.setUpdatedAt(Instant.now());

        Connector savedConnector = connectorRepository.save(connector);

        // Update secrets if provided
        if (request.getSecrets() != null && !request.getSecrets().isEmpty()) {
            Map<String, Object> encryptedSecrets = encryptionService.encryptSecrets(request.getSecrets());

            Optional<ConnectorSecret> existingSecret = connectorSecretRepository
                    .findByConnectorIdAndTenantId(connectorId, tenantId);

            if (existingSecret.isPresent()) {
                ConnectorSecret secret = existingSecret.get();
                secret.setSecretEnc(convertToJsonNode(encryptedSecrets));
                secret.setUpdatedAt(Instant.now());
                connectorSecretRepository.save(secret);
            } else {
                ConnectorSecret secret = ConnectorSecret.builder()
                        .connectorId(connectorId)
                        .tenantId(tenantId)
                        .secretEnc(convertToJsonNode(encryptedSecrets))
                        .updatedAt(Instant.now())
                        .build();
                connectorSecretRepository.save(secret);
            }
        }

        // Audit logging
        String afterSummary = "Connector: " + savedConnector.getName() + ", Enabled: " + savedConnector.getEnabled();
        logAudit(tenantId, userId, "UPDATE_CONNECTOR", "connector", connectorId.toString(),
                beforeSummary, afterSummary, correlationId);

        // Emit outbox event
        emitConnectorEvent("CONNECTOR_UPDATED", savedConnector, tenantId, correlationId);

        return toConnectorResponse(savedConnector);
    }

    /**
     * Delete connector.
     */
    @PreAuthorize("hasAuthority('config.connector.write')")
    @Transactional
    public void deleteConnector(UUID tenantId, UUID userId, UUID connectorId, String correlationId) {
        validateTenantId(tenantId);
        validateUserId(userId);
        validateConnectorId(connectorId);

        Optional<Connector> existingConnector = connectorRepository
                .findByConnectorIdAndTenantId(connectorId, tenantId);

        if (existingConnector.isEmpty()) {
            throw new IllegalArgumentException("Connector not found or access denied");
        }

        Connector connector = existingConnector.get();
        String beforeSummary = "Connector: " + connector.getName() + ", Type: " + connector.getType();

        // Delete connector (cascade will handle secrets)
        connectorRepository.deleteByConnectorIdAndTenantId(connectorId, tenantId);

        // Audit logging
        logAudit(tenantId, userId, "DELETE_CONNECTOR", "connector", connectorId.toString(),
                beforeSummary, "DELETED", correlationId);

        // Emit outbox event for delete notification
        Map<String, Object> deleteEventData = new HashMap<>();
        deleteEventData.put("connectorId", connectorId.toString());
        deleteEventData.put("name", connector.getName());
        deleteEventData.put("type", connector.getType());
        deleteEventData.put("action", "deleted");
        deleteEventData.put("correlationId", correlationId);

        OutboxEvent deleteEvent = OutboxEvent.builder()
                .tenantId(tenantId)
                .aggregateType("CONNECTOR")
                .aggregateId(connectorId.toString())
                .eventType("CONNECTOR_DELETED")
                .payload(convertToJsonNode(deleteEventData))
                .createdAt(Instant.now())
                .build();

        outboxEventRepository.save(deleteEvent);
    }

    // Helper methods

    private void validateTenantId(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID is required");
        }
    }

    private void validateUserId(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
    }

    private void validateConnectorId(UUID connectorId) {
        if (connectorId == null) {
            throw new IllegalArgumentException("Connector ID is required");
        }
    }

    private void validateCreateRequest(ConnectorCreateRequest request, UUID tenantId) {
        if (request == null) {
            throw new IllegalArgumentException("Connector request is required");
        }

        if (connectorRepository.existsByTenantIdAndName(tenantId, request.getName())) {
            throw new IllegalArgumentException("Connector name already exists in tenant");
        }

        // Plugin-specific validation
        if (request.getType() == ConnectorType.BMC) {
            request.validateBMCConfiguration();
        }
    }

    private ConnectorResponse toConnectorResponse(Connector connector) {
        ConnectorResponse response = new ConnectorResponse();
        response.setConnectorId(connector.getConnectorId());
        response.setType(ConnectorType.valueOf(connector.getType().toUpperCase()));
        response.setName(connector.getName());
        response.setEnabled(connector.getEnabled());
        response.setConfig(convertFromJsonNode(connector.getConfig()));
        response.setCreatedAt(connector.getCreatedAt().atZone(java.time.ZoneOffset.UTC).toLocalDateTime());
        response.setUpdatedAt(connector.getUpdatedAt().atZone(java.time.ZoneOffset.UTC).toLocalDateTime());

        // Check if secrets exist
        boolean hasSecrets = connectorSecretRepository
                .findByConnectorIdAndTenantId(connector.getConnectorId(), connector.getTenantId())
                .isPresent();
        response.setHasSecrets(hasSecrets);

        return response;
    }

    private Map<String, Object> maskSecretsFromConfig(Map<String, Object> config) {
        Map<String, Object> maskedConfig = new HashMap<>(config);
        // Remove any accidentally included secrets from config
        maskedConfig.remove("password");
        maskedConfig.remove("apiKey");
        maskedConfig.remove("clientSecret");
        maskedConfig.remove("token");
        return maskedConfig;
    }

    private void logAudit(UUID tenantId, UUID userId, String action, String entity,
                         String entityId, String beforeSummary, String afterSummary,
                         String correlationId) {
        // Build details JSON with before/after/correlation
        Map<String, Object> detailsMap = new HashMap<>();
        if (beforeSummary != null) {
            detailsMap.put("before", beforeSummary);
        }
        if (afterSummary != null) {
            detailsMap.put("after", afterSummary);
        }
        detailsMap.put("correlationId", correlationId);

        AuditLog auditLog = AuditLog.builder()
                .tenantId(tenantId)
                .actorUserId(userId)
                .action(action)
                .entityType(entity)
                .entityId(entityId)
                .details(convertToJsonNode(detailsMap))
                .at(Instant.now())
                .build();

        auditLogRepository.save(auditLog);
    }

    private void emitConnectorEvent(String eventType, Connector connector, UUID tenantId, String correlationId) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("connectorId", connector.getConnectorId().toString());
        eventData.put("name", connector.getName());
        eventData.put("type", connector.getType());
        eventData.put("enabled", connector.getEnabled());
        eventData.put("action", eventType.toLowerCase());
        eventData.put("correlationId", correlationId);

        OutboxEvent event = OutboxEvent.builder()
                .tenantId(tenantId)
                .aggregateType("CONNECTOR")
                .aggregateId(connector.getConnectorId().toString())
                .eventType(eventType)
                .payload(convertToJsonNode(eventData))
                .createdAt(Instant.now())
                .build();

        outboxEventRepository.save(event);
    }

    // JSON conversion methods
    private JsonNode convertToJsonNode(Map<String, Object> map) {
        if (map == null) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.valueToTree(map);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertFromJsonNode(JsonNode jsonNode) {
        if (jsonNode == null || jsonNode.isNull()) {
            return new HashMap<>();
        }
        return objectMapper.convertValue(jsonNode, Map.class);
    }
}
