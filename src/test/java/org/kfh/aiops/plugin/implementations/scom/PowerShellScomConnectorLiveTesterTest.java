package org.kfh.aiops.plugin.implementations.scom;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.mock.env.MockEnvironment;

class PowerShellScomConnectorLiveTesterTest {

    @Test
    void shouldPassWhenPowerShellProbeReturnsValidJson() {
        var executor = new CapturingExecutor(new ScomCommandResult(0,
                "warning before json\n{\"module\":\"OperationsManager\",\"sampleCount\":1}", ""));
        var tester = tester(executor);

        var result = tester.test(context(), connector(), Map.of("username", "scom-user", "password", "scom-password"));

        assertThat(result)
                .containsEntry("pass", true)
                .containsEntry("readyToCollect", true)
                .containsEntry("checkedEndpoint", "https://scom.example.com:5986/wsman");
        assertThat(executor.commands).hasSize(1);
        assertThat(executor.commands.getFirst()).contains("powershell.exe", "-NoProfile", "-NonInteractive");
        assertThat(executor.environments.getFirst())
                .containsEntry("KFH_AIOPS_SCOM_SERVER", "scom.example.com")
                .containsEntry("KFH_AIOPS_SCOM_QUALIFIED_USERNAME", "corp.kfh.kw\\scom-user")
                .containsEntry("KFH_AIOPS_SCOM_PASSWORD", "scom-password");
        assertThat(result.toString()).doesNotContain("scom-user", "scom-password");
    }

    @Test
    void shouldFailSafelyWhenScomCredentialsAreMissing() {
        var executor = new CapturingExecutor(new ScomCommandResult(0, "{}", ""));
        var tester = tester(executor);

        var result = tester.test(context(), connector(), Map.of());

        assertThat(result)
                .containsEntry("pass", false)
                .containsEntry("readyToCollect", false);
        assertThat(executor.commands).isEmpty();
        assertThat(result.toString()).doesNotContain("KFH_AIOPS_SCOM_PASSWORD");
    }

    @Test
    void shouldFailSafelyWhenPowerShellReturnsErrorWithoutLeakingSecrets() {
        var executor = new CapturingExecutor(new ScomCommandResult(1, "",
                "Access Key: corp.example\\scom-user Access Secret: scom-password Kerberos authentication failed"));
        var tester = tester(executor);

        var result = tester.test(context(), connector(), Map.of("username", "scom-user", "password", "scom-password"));

        assertThat(result)
                .containsEntry("pass", false)
                .containsEntry("readyToCollect", false);
        assertThat(String.valueOf(result.get("message"))).contains("PowerShell SCOM probe exited with code 1");
        assertThat(result.toString()).doesNotContain("corp.example", "scom-user", "scom-password");
    }

    @Test
    void shouldBuildQualifiedUsernameInJavaWithoutPowerShellDomainConcatenation() {
        var executor = new CapturingExecutor(new ScomCommandResult(0,
                "{\"module\":\"OperationsManager\",\"sampleCount\":0}", ""));
        var tester = tester(executor);

        tester.test(context(), connector(), Map.of("username", "scom-user", "password", "scom-password"));

        var script = executor.commands.getFirst().getLast();
        assertThat(executor.environments.getFirst())
                .containsEntry("KFH_AIOPS_SCOM_QUALIFIED_USERNAME", "corp.kfh.kw\\scom-user")
                .doesNotContainKeys("KFH_AIOPS_SCOM_DOMAIN", "KFH_AIOPS_SCOM_USERNAME");
        assertThat(script)
                .contains("$qualifiedUser = $env:KFH_AIOPS_SCOM_QUALIFIED_USERNAME")
                .doesNotContain("$domain")
                .doesNotContain("$username")
                .doesNotContain("[string]::Concat")
                .doesNotContain("[char]92");
    }

    @Test
    void shouldKeepAlreadyQualifiedCrossDomainUsername() {
        var executor = new CapturingExecutor(new ScomCommandResult(0,
                "{\"module\":\"OperationsManager\",\"sampleCount\":0}", ""));
        var tester = tester(executor);

        tester.test(context(), connector(), Map.of("username", "corp.example\\scom-user", "password", "scom-password"));

        assertThat(executor.environments.getFirst())
                .containsEntry("KFH_AIOPS_SCOM_QUALIFIED_USERNAME", "corp.example\\scom-user");
    }

    @Test
    void shouldKeepUpnUsernameWithoutAddingDomain() {
        var executor = new CapturingExecutor(new ScomCommandResult(0,
                "{\"module\":\"OperationsManager\",\"sampleCount\":0}", ""));
        var tester = tester(executor);

        tester.test(context(), connector(), Map.of("username", "scom-user@corp.example", "password", "scom-password"));

        assertThat(executor.environments.getFirst())
                .containsEntry("KFH_AIOPS_SCOM_QUALIFIED_USERNAME", "scom-user@corp.example");
    }

    @Test
    void shouldExplainExpiredWinRmCertificateWithoutLeakingSecrets() {
        var executor = new CapturingExecutor(new ScomCommandResult(1, "",
                "Connecting to remote server scom.example.com failed: The SSL certificate is expired. "
                        + "Access Key: corp.example\\scom-user Access Secret: scom-password"));
        var tester = tester(executor);

        var result = tester.test(context(), connector(), Map.of("username", "corp.example\\scom-user", "password", "scom-password"));

        assertThat(result)
                .containsEntry("pass", false)
                .containsEntry("readyToCollect", false);
        assertThat(String.valueOf(result.get("message")))
                .containsOnlyOnce("The remote TLS certificate is expired")
                .contains("Renew/rebind the connector or WinRM HTTPS certificate")
                .contains("include a SAN/CN matching the configured hostname")
                .contains("does not make an expired WinRM HTTPS certificate acceptable");
        assertThat(result.toString()).doesNotContain("corp.example", "scom-user", "scom-password");
    }

    @Test
    void shouldExplainWinRmRevocationCheckFailure() {
        var executor = new CapturingExecutor(new ScomCommandResult(1, "",
                "The SSL certificate could not be checked for revocation. "
                        + "The server used to check for revocation might be unreachable."));
        var tester = tester(executor);

        var result = tester.test(context(), connector(), Map.of("username", "corp.example\\scom-user", "password", "scom-password"));

        assertThat(result)
                .containsEntry("pass", false)
                .containsEntry("readyToCollect", false);
        assertThat(String.valueOf(result.get("message")))
                .containsOnlyOnce("cannot complete certificate revocation checking")
                .contains("certificate CRL/OCSP endpoints")
                .contains("SkipRevocationCheck")
                .contains("HTTPS encryption remains enabled");
        assertThat(result.toString()).doesNotContain("corp.example", "scom-user", "scom-password");
    }

    @Test
    void shouldUseSkipCertificateSessionOptionWhenTlsVerificationDisabled() {
        var executor = new CapturingExecutor(new ScomCommandResult(0,
                "{\"module\":\"OperationsManager\",\"sampleCount\":0}", ""));
        var tester = tester(executor);
        var connector = new LinkedHashMap<>(connector());
        connector.put("verifySsl", false);

        var result = tester.test(context(), connector, Map.of("username", "corp.example\\scom-user", "password", "scom-password"));

        assertThat(result)
                .containsEntry("pass", true)
                .containsEntry("verifySsl", false);
        assertThat(executor.environments.getFirst()).containsEntry("KFH_AIOPS_SCOM_VERIFY_SSL", "false");
        assertThat(executor.commands.getFirst().getLast())
                .contains("New-PSSessionOption -SkipCACheck -SkipCNCheck -SkipRevocationCheck")
                .contains("if (-not $verifySsl)");
    }

    private static PowerShellScomConnectorLiveTester tester(ScomCommandExecutor executor) {
        var environment = new MockEnvironment().withProperty("kfh.security.ssrf.resolve-hosts", "false");
        return new PowerShellScomConnectorLiveTester(environment, executor);
    }

    private static TenantContext context() {
        return new TenantContext(UUID.randomUUID(), UUID.randomUUID(), "KW", "PROD", "corr-scom-test", Set.of("CONNECTOR_TEST"));
    }

    private static Map<String, Object> connector() {
        return Map.of(
                "id", UUID.randomUUID().toString(),
                "pluginType", "SCOM",
                "managementServer", "scom.example.com",
                "domain", "corp.kfh.kw",
                "winrmPort", 5986,
                "useHttps", true,
                "authMethod", "Kerberos",
                "connectionTimeoutSeconds", 5,
                "endpointUrl", "https://scom.example.com:5986/wsman");
    }

    private static final class CapturingExecutor implements ScomCommandExecutor {
        private final ScomCommandResult result;
        private final List<List<String>> commands = new ArrayList<>();
        private final List<Map<String, String>> environments = new ArrayList<>();

        private CapturingExecutor(ScomCommandResult result) {
            this.result = result;
        }

        @Override
        public ScomCommandResult execute(List<String> command, Map<String, String> environment, Duration timeout) {
            commands.add(List.copyOf(command));
            environments.add(new LinkedHashMap<>(environment));
            return result;
        }
    }
}
