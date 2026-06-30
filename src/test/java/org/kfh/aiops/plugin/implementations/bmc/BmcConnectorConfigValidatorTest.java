package org.kfh.aiops.plugin.implementations.bmc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.platform.exception.ValidationException;

class BmcConnectorConfigValidatorTest {

    private final BmcConnectorConfigValidator validator = new BmcConnectorConfigValidator();

    @Test
    void shouldAllowPrivateBmcBaseUrlForHybridEnvironment() {
        var safe = validator.validateForCreate(Map.of(
                "baseUrl", "https://10.17.134.119",
                "secretsPlain", Map.of("accessKey", "bmc-key", "accessSecretKey", "bmc-secret")), "PROD");

        assertThat(safe)
                .containsEntry("pluginType", "BMC")
                .containsEntry("baseUrl", "https://10.17.134.119")
                .containsEntry("secretsMask", "configured");
        assertThat(safe.toString()).doesNotContain("bmc-key", "bmc-secret");
    }

    @Test
    void shouldPersistDisabledTlsCertificateVerificationWhenExplicitlyConfigured() {
        var safe = validator.validateForCreate(Map.of(
                "baseUrl", "https://bmc.example.com",
                "verifySsl", false,
                "secretsPlain", Map.of("accessKey", "bmc-key", "accessSecretKey", "bmc-secret")), "PROD");

        assertThat(safe).containsEntry("verifySsl", false);
    }

    @Test
    void shouldRejectMetadataBmcBaseUrl() {
        assertThatThrownBy(() -> validator.validateForCreate(Map.of(
                "baseUrl", "https://169.254.169.254",
                "secretsPlain", Map.of("accessKey", "bmc-key", "accessSecretKey", "bmc-secret")), "PROD"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("SSRF protection");
    }
}

