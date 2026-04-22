package org.aiopsanalysis.controller;

import org.aiopsanalysis.dto.ConnectorCreateRequest;
import org.aiopsanalysis.dto.ConnectorResponse;
import org.aiopsanalysis.dto.ConnectorTestRequest;
import org.aiopsanalysis.service.ConnectorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for connector management.
 * Enforces X-Tenant-Id and X-User-Id headers for multi-tenancy and audit.
 *
 * ALL endpoints require:
 * - X-Tenant-Id header
 * - X-User-Id header
 * - RBAC permissions (enforced at service layer)
 */
@RestController
@RequestMapping("/api/connectors")
@Validated
public class ConnectorController {

    private final ConnectorService connectorService;

    @Autowired
    public ConnectorController(ConnectorService connectorService) {
        this.connectorService = connectorService;
    }

    /**
     * Get all connectors for a tenant.
     * Requires: config.connector.read permission
     */
    @GetMapping
    public ResponseEntity<Page<ConnectorResponse>> getConnectors(
            @RequestHeader("X-Tenant-Id") @NotNull UUID tenantId,
            @PageableDefault(size = 20, sort = "updatedAt") Pageable pageable) {

        try {
            Page<ConnectorResponse> connectors = connectorService.getConnectors(tenantId, pageable);
            return ResponseEntity.ok(connectors);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get a specific connector by ID.
     * Requires: config.connector.read permission
     */
    @GetMapping("/{connectorId}")
    public ResponseEntity<ConnectorResponse> getConnector(
            @RequestHeader("X-Tenant-Id") @NotNull UUID tenantId,
            @PathVariable @NotNull UUID connectorId) {

        try {
            Optional<ConnectorResponse> connector = connectorService.getConnector(tenantId, connectorId);
            return connector
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Create a new connector.
     * Requires: config.connector.write permission
     */
    @PostMapping
    public ResponseEntity<ConnectorResponse> createConnector(
            @RequestHeader("X-Tenant-Id") @NotNull UUID tenantId,
            @RequestHeader("X-User-Id") @NotNull UUID userId,
            @RequestHeader(value = "X-Correlation-Id", defaultValue = "") String correlationId,
            @RequestBody @Valid ConnectorCreateRequest request) {

        try {
            // Generate correlation ID if not provided
            if (correlationId.isEmpty()) {
                correlationId = UUID.randomUUID().toString();
            }

            ConnectorResponse response = connectorService.createConnector(tenantId, userId, request, correlationId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Update an existing connector.
     * Requires: config.connector.write permission
     */
    @PutMapping("/{connectorId}")
    public ResponseEntity<ConnectorResponse> updateConnector(
            @RequestHeader("X-Tenant-Id") @NotNull UUID tenantId,
            @RequestHeader("X-User-Id") @NotNull UUID userId,
            @RequestHeader(value = "X-Correlation-Id", defaultValue = "") String correlationId,
            @PathVariable @NotNull UUID connectorId,
            @RequestBody @Valid ConnectorCreateRequest request) {

        try {
            // Generate correlation ID if not provided
            if (correlationId.isEmpty()) {
                correlationId = UUID.randomUUID().toString();
            }

            ConnectorResponse response = connectorService.updateConnector(tenantId, userId, connectorId, request, correlationId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Delete a connector.
     * Requires: config.connector.write permission
     */
    @DeleteMapping("/{connectorId}")
    public ResponseEntity<Void> deleteConnector(
            @RequestHeader("X-Tenant-Id") @NotNull UUID tenantId,
            @RequestHeader("X-User-Id") @NotNull UUID userId,
            @RequestHeader(value = "X-Correlation-Id", defaultValue = "") String correlationId,
            @PathVariable @NotNull UUID connectorId) {

        try {
            // Generate correlation ID if not provided
            if (correlationId.isEmpty()) {
                correlationId = UUID.randomUUID().toString();
            }

            connectorService.deleteConnector(tenantId, userId, connectorId, correlationId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Test a connector connection (async).
     * Requires: config.connector.test permission
     */
    @PostMapping("/test")
    public ResponseEntity<CompletableFuture<Map<String, Object>>> testConnector(
            @RequestHeader("X-Tenant-Id") @NotNull UUID tenantId,
            @RequestHeader("X-User-Id") @NotNull UUID userId,
            @RequestHeader(value = "X-Correlation-Id", defaultValue = "") String correlationId,
            @RequestBody @Valid ConnectorTestRequest request) {

        try {
            // Generate correlation ID if not provided
            if (correlationId.isEmpty()) {
                correlationId = UUID.randomUUID().toString();
            }

            CompletableFuture<Map<String, Object>> testResult = connectorService.testConnector(tenantId, userId, request, correlationId);
            return ResponseEntity.ok(testResult);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get connector types/plugins available.
     */
    @GetMapping("/types")
    public ResponseEntity<Map<String, Object>> getConnectorTypes(
            @RequestHeader("X-Tenant-Id") @NotNull UUID tenantId) {

        try {
            Map<String, Object> types = Map.of(
                    "types", new String[]{"BMC", "SOLARWINDS", "SCOM", "NAGIOS", "ZABBIX", "PROMETHEUS"},
                    "plugins", Map.of(
                            "BMC", Map.of(
                                    "name", "BMC Remedy ITSM",
                                    "description", "Connect to BMC Remedy for incident and alert data",
                                    "version", "1.0",
                                    "configSchema", getBMCConfigSchema()
                            ),
                            "SOLARWINDS", Map.of(
                                    "name", "SolarWinds",
                                    "description", "Connect to SolarWinds monitoring platform",
                                    "version", "1.0",
                                    "configSchema", Map.of("placeholder", "future implementation")
                            )
                    )
            );
            return ResponseEntity.ok(types);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get BMC configuration schema for frontend form generation.
     */
    private Map<String, Object> getBMCConfigSchema() {
        return Map.of(
                "config", Map.of(
                        "baseUrl", Map.of(
                                "type", "url",
                                "required", true,
                                "placeholder", "https://bmc-prod-server.kfh.com",
                                "validation", "Must be HTTPS and corporate domain"
                        ),
                        "username", Map.of(
                                "type", "text",
                                "required", true,
                                "placeholder", "service-account-username"
                        ),
                        "timeout", Map.of(
                                "type", "number",
                                "required", false,
                                "default", 30,
                                "min", 5,
                                "max", 300,
                                "placeholder", "30"
                        ),
                        "apiVersion", Map.of(
                                "type", "select",
                                "required", false,
                                "default", "v1",
                                "options", new String[]{"v1", "v2"}
                        )
                ),
                "secrets", Map.of(
                        "password", Map.of(
                                "type", "password",
                                "required", true,
                                "placeholder", "Service account password"
                        ),
                        "apiKey", Map.of(
                                "type", "password",
                                "required", false,
                                "placeholder", "Optional API key for enhanced access"
                        )
                )
        );
    }

    /**
     * Exception handler for validation errors.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleValidationError(IllegalArgumentException e) {
        Map<String, Object> error = Map.of(
                "code", "VALIDATION_ERROR",
                "message", e.getMessage(),
                "timestamp", java.time.Instant.now().toString(),
                "correlationId", UUID.randomUUID().toString()
        );
        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Exception handler for missing headers.
     */
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleMissingHeaders(jakarta.validation.ConstraintViolationException e) {
        Map<String, Object> error = Map.of(
                "code", "MISSING_HEADERS",
                "message", "Required headers X-Tenant-Id and X-User-Id must be provided",
                "timestamp", java.time.Instant.now().toString(),
                "correlationId", UUID.randomUUID().toString()
        );
        return ResponseEntity.badRequest().body(error);
    }
}
