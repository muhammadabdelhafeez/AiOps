package org.kfh.aiops.plugin.implementations.emco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EmcoConnectorConfigValidatorTest {

    private final EmcoConnectorConfigValidator validator = new EmcoConnectorConfigValidator();

    @Test
    void shouldValidateConfiguredEmcoConnectorWithoutReturningSecrets() {
        var safe = validator.validateForCreate(Map.of(
                "sqlServer", "DCVSAMDB01:11433",
                "kfhDatabase", "EMCO_KFH_PROD",
                "cctvDatabase", "EMCO_CCTV_PROD",
                "minutesBack", 60,
                "connectionTimeoutSeconds", 30,
                "queryTimeoutSeconds", 120,
                "secretsPlain", Map.of(
                        "kfhUsername", "emco-kfh-user",
                        "kfhPassword", "emco-kfh-password",
                        "cctvUsername", "emco-cctv-user",
                        "cctvPassword", "emco-cctv-password")), "PROD");

        assertThat(safe)
                .containsEntry("pluginType", "EMCO")
                .containsEntry("sqlServer", "dcvsamdb01")
                .containsEntry("sqlPort", 11433)
                .containsEntry("kfhDatabase", "EMCO_KFH_PROD")
                .containsEntry("cctvDatabase", "EMCO_CCTV_PROD")
                .containsEntry("authMode", "SqlServerCredentials")
                .containsEntry("configurationStatus", "CONFIGURED")
                .containsEntry("secretsMask", "configured");
        assertThat(safe.toString()).doesNotContain("emco-kfh-user", "emco-kfh-password", "emco-cctv-user", "emco-cctv-password");
    }

    @Test
    void shouldInstallPendingEmcoPlaceholderWithoutCredentials() {
        var safe = validator.validateForCreate(Map.of("installOnly", true), "PROD");

        assertThat(safe)
                .containsEntry("pluginType", "EMCO")
                .containsEntry("configurationStatus", "PENDING")
                .containsEntry("secretsMask", "not_configured")
                .containsEntry("sqlServer", "DCVSAMDB01")
                .containsEntry("sqlPort", 11433);
    }

    @Test
    void shouldRejectUnsafeEmcoSqlServerUrl() {
        assertThatThrownBy(() -> validator.validateForCreate(Map.of(
                "sqlServer", "http://localhost:11433/path?user=bad",
                "kfhDatabase", "EMCO_KFH_PROD",
                "cctvDatabase", "EMCO_CCTV_PROD",
                "secretsPlain", Map.of(
                        "kfhUsername", "emco-kfh-user",
                        "kfhPassword", "emco-kfh-password",
                        "cctvUsername", "emco-cctv-user",
                        "cctvPassword", "emco-cctv-password")), "PROD"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("EMCO sqlServer must be a SQL Server hostname or IP address");
    }

    @Test
    void shouldPersistSqlServerCertificateTrustSettingWhenExplicitlyConfigured() {
        var safe = validator.validateForCreate(Map.of(
                "sqlServer", "emco-sql.example.com",
                "kfhDatabase", "EMCO_KFH_PROD",
                "cctvDatabase", "EMCO_CCTV_PROD",
                "trustServerCertificate", true,
                "secretsPlain", Map.of(
                        "kfhUsername", "emco-kfh-user",
                        "kfhPassword", "emco-kfh-password",
                        "cctvUsername", "emco-cctv-user",
                        "cctvPassword", "emco-cctv-password")), "PROD");

        assertThat(safe).containsEntry("trustServerCertificate", true);
    }

    @Test
    void shouldRejectEmcoCredentialRotationWhenOneDomainPairIsPartial() {
        var existing = Map.<String, Object>of(
                "sqlServer", "emco-sql.example.com",
                "kfhDatabase", "EMCO_KFH_PROD",
                "cctvDatabase", "EMCO_CCTV_PROD",
                "secretsMask", "configured",
                "configurationStatus", "CONFIGURED");

        assertThatThrownBy(() -> validator.validateForUpdate(
                Map.of("secretsPlain", Map.of("kfhUsername", "rotated-kfh-user")), existing, "PROD"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("EMCO KFH username and password must be rotated together");
    }
}

