package org.kfh.aiops.plugin.implementations.appdynamics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.platform.exception.ValidationException;

class AppDynamicsConnectorConfigValidatorTest {

    private final AppDynamicsConnectorConfigValidator validator = new AppDynamicsConnectorConfigValidator();

    @Test
    void shouldValidateConfiguredAppDynamicsConnectorWithoutReturningSecrets() {
        var safe = validator.validateForCreate(Map.of(
                "controllerUrl", "https://appd.example.com/controller",
                "durationMinutes", 60,
                "maxWorkers", 15,
                "timeoutSeconds", 120,
                "secretsPlain", Map.of("username", "appd-user", "password", "appd-password")), "PROD");

        assertThat(safe)
                .containsEntry("pluginType", "APPDYNAMICS")
                .containsEntry("controllerUrl", "https://appd.example.com/controller")
                .containsEntry("authMode", "BasicAuth")
                .containsEntry("secretsMask", "configured")
                .containsEntry("configurationStatus", "CONFIGURED");
        assertThat(safe.toString()).doesNotContain("appd-user", "appd-password");
    }

    @Test
    void shouldPersistDisabledTlsCertificateVerificationWhenExplicitlyConfigured() {
        var safe = validator.validateForCreate(Map.of(
                "controllerUrl", "https://appd.example.com/controller",
                "verifySsl", false,
                "secretsPlain", Map.of("username", "appd-user", "password", "appd-password")), "PROD");

        assertThat(safe).containsEntry("verifySsl", false);
    }

    @Test
    void shouldRejectUnsafeAppDynamicsControllerUrl() {
        assertThatThrownBy(() -> validator.validateForCreate(Map.of(
                "controllerUrl", "http://169.254.169.254/controller",
                "secretsPlain", Map.of("username", "appd-user", "password", "appd-password")), "PROD"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void shouldAllowPrivateAppDynamicsControllerIpForHybridEnvironment() {
        var safe = validator.validateForCreate(Map.of(
                "controllerUrl", "https://10.17.134.118/controller",
                "secretsPlain", Map.of("username", "appd-user", "password", "appd-password")), "PROD");

        assertThat(safe).containsEntry("controllerUrl", "https://10.17.134.118/controller");
    }

    @Test
    void shouldRejectMetadataAppDynamicsController() {
        assertThatThrownBy(() -> validator.validateForCreate(Map.of(
                "controllerUrl", "https://169.254.169.254/controller",
                "secretsPlain", Map.of("username", "appd-user", "password", "appd-password")), "PROD"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("SSRF protection");
    }

    @Test
    void shouldRequireAtLeastOneAppDynamicsFetchOption() {
        assertThatThrownBy(() -> validator.validateForCreate(Map.of(
                "controllerUrl", "https://appd.example.com/controller",
                "fetchErrors", false,
                "fetchViolations", false,
                "fetchSlowTransactions", false,
                "secretsPlain", Map.of("username", "appd-user", "password", "appd-password")), "PROD"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("at least one fetch option");
    }

    @Test
    void shouldInstallPendingAppDynamicsPlaceholderWithoutCredentials() {
        var safe = validator.validateForCreate(Map.of("installOnly", true), "PROD");

        assertThat(safe)
                .containsEntry("pluginType", "APPDYNAMICS")
                .containsEntry("configurationStatus", "PENDING")
                .containsEntry("secretsMask", "not_configured");
    }
}

