package org.kfh.aiops.plugin.implementations.vrops;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class VropsConnectorConfigValidatorTest {

    private final VropsConnectorConfigValidator validator = new VropsConnectorConfigValidator();

    @Test
    void shouldValidateConfiguredVropsConnectorWithoutReturningSecrets() {
        var safe = validator.validateForCreate(Map.of(
                "host", "vrops.example.com",
                "authSource", "KFH AD",
                "hours", 1,
                "pageSize", 1000,
                "maxPages", 200,
                "maxWorkers", 12,
                "timeoutSeconds", 120,
                "secretsPlain", Map.of("username", "vrops-user", "password", "vrops-password")), "PROD");

        assertThat(safe)
                .containsEntry("pluginType", "VROPS")
                .containsEntry("host", "vrops.example.com")
                .containsEntry("baseUrl", "https://vrops.example.com/suite-api/api")
                .containsEntry("authMode", "Token")
                .containsEntry("authSource", "KFH AD")
                .containsEntry("secretsMask", "configured")
                .containsEntry("configurationStatus", "CONFIGURED");
        assertThat(safe.toString()).doesNotContain("vrops-user", "vrops-password");
    }

    @Test
    void shouldInstallPendingVropsPlaceholderWithoutCredentials() {
        var safe = validator.validateForCreate(Map.of("installOnly", true), "PROD");

        assertThat(safe)
                .containsEntry("pluginType", "VROPS")
                .containsEntry("configurationStatus", "PENDING")
                .containsEntry("secretsMask", "not_configured")
                .containsEntry("authSource", "KFH AD");
    }

    @Test
    void shouldAllowPrivateVropsIpForHybridEnvironment() {
        var safe = validator.validateForCreate(Map.of(
                "host", "10.2.243.66",
                "secretsPlain", Map.of("username", "vrops-user", "password", "vrops-password")), "PROD");

        assertThat(safe)
                .containsEntry("host", "10.2.243.66")
                .containsEntry("baseUrl", "https://10.2.243.66/suite-api/api");
    }

    @Test
    void shouldPersistDisabledTlsCertificateVerificationWhenExplicitlyConfigured() {
        var safe = validator.validateForCreate(Map.of(
                "host", "vrops.example.com",
                "verifySsl", false,
                "secretsPlain", Map.of("username", "vrops-user", "password", "vrops-password")), "PROD");

        assertThat(safe).containsEntry("verifySsl", false);
    }
}

