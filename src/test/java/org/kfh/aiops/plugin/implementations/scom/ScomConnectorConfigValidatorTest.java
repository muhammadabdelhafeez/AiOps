package org.kfh.aiops.plugin.implementations.scom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ScomConnectorConfigValidatorTest {

    private final ScomConnectorConfigValidator validator = new ScomConnectorConfigValidator();

    @Test
    void shouldValidateConfiguredScomConnectorWithoutReturningSecrets() {
        var safe = validator.validateForCreate(Map.of(
                "managementServer", "dcvscoap12.corp.kfh.kw",
                "domain", "corp.kfh.kw",
                "winrmPort", 5986,
                "authMethod", "Kerberos",
                "hoursBack", 1,
                "connectionTimeoutSeconds", 60,
                "secretsPlain", Map.of("username", "scom-user", "password", "scom-password")), "PROD");

        assertThat(safe)
                .containsEntry("pluginType", "SCOM")
                .containsEntry("managementServer", "dcvscoap12.corp.kfh.kw")
                .containsEntry("baseUrl", "https://dcvscoap12.corp.kfh.kw:5986/wsman")
                .containsEntry("authMode", "WinRM")
                .containsEntry("configurationStatus", "CONFIGURED")
                .containsEntry("secretsMask", "configured");
        assertThat(safe.toString()).doesNotContain("scom-user", "scom-password");
    }

    @Test
    void shouldInstallPendingScomPlaceholderWithoutCredentials() {
        var safe = validator.validateForCreate(Map.of("installOnly", true), "PROD");

        assertThat(safe)
                .containsEntry("pluginType", "SCOM")
                .containsEntry("configurationStatus", "PENDING")
                .containsEntry("secretsMask", "not_configured")
                .containsEntry("authMethod", "Kerberos")
                .containsEntry("winrmPort", 5986);
    }

    @Test
    void shouldAllowInternalScomManagementServerForHybridEnvironment() {
        var safe = validator.validateForCreate(Map.of(
                "managementServer", "172.17.134.118",
                "domain", "corp.kfh.kw",
                "secretsPlain", Map.of("username", "scom-user", "password", "scom-password")), "PROD");

        assertThat(safe)
                .containsEntry("managementServer", "172.17.134.118")
                .containsEntry("baseUrl", "https://172.17.134.118:5986/wsman");
    }

    @Test
    void shouldPersistDisabledTlsVerificationWhenExplicitlyConfigured() {
        var safe = validator.validateForCreate(Map.of(
                "managementServer", "scom.example.com",
                "domain", "corp.kfh.kw",
                "verifySsl", false,
                "secretsPlain", Map.of("username", "scom-user", "password", "scom-password")), "PROD");

        assertThat(safe).containsEntry("verifySsl", false);
    }

    @Test
    void shouldRejectScomCredentialRotationWhenOnlyOneSecretIsSupplied() {
        var existing = Map.<String, Object>of(
                "managementServer", "scom.example.com",
                "domain", "corp.kfh.kw",
                "secretsMask", "configured",
                "configurationStatus", "CONFIGURED");

        assertThatThrownBy(() -> validator.validateForUpdate(
                Map.of("secretsPlain", Map.of("username", "rotated-user")), existing, "PROD"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("SCOM username and password must be rotated together");
    }
}
