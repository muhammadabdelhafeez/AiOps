package org.kfh.aiops.ingestion.scom;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ScomWinRmClientTest {

    private final ScomWinRmClient client = new ScomWinRmClient(new ScomProperties(), new ObjectMapper());

    @Test
    void parsesScomAlertWithWcfDatesWithinWindow() {
        // Exact JSON shape from Get-SCOMAlert | ConvertTo-Json (docs/SCOM_Collectors.md).
        var json = """
                [{"Id":"b3a2c1d4-5e6f-7890-abcd-1234567890ab","Name":"Logical Disk Free Space is low",
                  "Severity":"Error","Priority":"High","Category":"PerformanceHealth","ResolutionState":0,
                  "MonitoringObjectName":"C: on server01","MonitoringObjectPath":"server01.corp.example.com",
                  "NetbiosComputerName":"server01","TimeRaised":"/Date(1751349600000)/",
                  "TimeResolved":"/Date(-62135596800000)/","LastModified":"/Date(1751353200000)/",
                  "Description":"The disk C: on computer server01 is running out of disk space."}]
                """;

        var events = client.parseAlerts(json, 0L);

        assertThat(events).hasSize(1);
        var event = events.get(0);
        assertThat(event)
                .containsEntry("Id", "b3a2c1d4-5e6f-7890-abcd-1234567890ab")
                .containsEntry("Severity", "Error")
                .containsEntry("Priority", "High")
                .containsEntry("MonitoringObjectName", "C: on server01")
                .containsEntry("NetbiosComputerName", "server01")
                .containsEntry("ResolutionState", 0)
                .containsEntry("TimeRaised", 1_751_349_600_000L)
                .containsEntry("LastModified", 1_751_353_200_000L);
        // TimeResolved is the 0001-01-01 sentinel → dropped
        assertThat(event).doesNotContainKey("TimeResolved");
    }

    @Test
    void filtersAlertsOutsideTheUtcWindow() {
        var json = """
                [{"Id":"1","Name":"old","TimeRaised":"/Date(1751349600000)/","LastModified":"/Date(1751349600000)/"}]
                """;
        // cutoff just after the alert's newest activity → excluded
        assertThat(client.parseAlerts(json, 1_751_349_600_001L)).isEmpty();
        assertThat(client.parseAlerts(json, 1_751_349_600_000L)).hasSize(1);
    }

    @Test
    void deduplicatesByIdKeepingNewestActivity() {
        var json = """
                [{"Id":"same","Name":"a","TimeRaised":"/Date(1000)/","LastModified":"/Date(1000)/"},
                 {"Id":"same","Name":"a","TimeRaised":"/Date(1000)/","LastModified":"/Date(5000)/"}]
                """;
        var events = client.parseAlerts(json, 0L);

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).containsEntry("LastModified", 5_000L);
    }

    @Test
    void extractsJsonFromNoisyPowerShellOutput() {
        var noisy = "WARNING: some module notice\n[{\"Id\":\"x\"}]\nPS prompt noise";
        assertThat(ScomWinRmClient.extractJsonFromOutput(noisy)).isEqualTo("[{\"Id\":\"x\"}]");
        assertThat(ScomWinRmClient.extractJsonFromOutput("[]")).isEqualTo("[]");
        assertThat(ScomWinRmClient.extractJsonFromOutput("no json here")).isNull();
    }

    @Test
    void parsesWcfAndSkipsSentinelDates() {
        assertThat(ScomWinRmClient.wcfOrIsoToEpochMillis("/Date(1751349600000)/")).isEqualTo(1_751_349_600_000L);
        assertThat(ScomWinRmClient.wcfOrIsoToEpochMillis("2026-07-01T10:00:00Z"))
                .isEqualTo(java.time.Instant.parse("2026-07-01T10:00:00Z").toEpochMilli());
        assertThat(ScomWinRmClient.wcfOrIsoToEpochMillis("0001-01-01T00:00:00")).isNull();
        assertThat(ScomWinRmClient.wcfOrIsoToEpochMillis(null)).isNull();
    }

    @Test
    void escapesSingleQuotesForPowerShellInjectionSafety() {
        assertThat(ScomWinRmClient.escapePsSingleQuoted("pass'; whoami #")).isEqualTo("pass''; whoami #");
        assertThat(ScomWinRmClient.escapePsSingleQuoted(null)).isEmpty();
    }

    @Test
    void buildsInvokeCommandWithEscapedCredentialsAndBuffer() {
        var props = new ScomProperties();
        props.setManagementServer("scom-mgmt.corp.local");
        props.setUsername("svc_scom");
        props.setDomain("CORP");
        props.setPassword("p'wd");
        props.setAuthMethod("Kerberos");
        props.setWinrmPort(5986);
        props.setUseHttps(true);
        props.setServerLocalOffsetHours(3);
        var scoped = new ScomWinRmClient(props, new ObjectMapper());

        var script = scoped.buildInvokeCommandScript(ScomWinRmClient.buildRemoteScript(), 1);

        assertThat(script)
                .contains("CORP\\svc_scom")     // domain-qualified user
                .contains("p''wd")               // escaped password
                .contains("-Port 5986")
                .contains("-UseSSL")
                .contains("-Authentication Kerberos")
                .contains("AddHours(-4)");       // 1h window + 3h Kuwait buffer
    }

    @Test
    void ntlmNegotiationTypeMapsToWinRmNegotiateAuth() {
        var props = new ScomProperties();
        props.setManagementServer("scom-mgmt.corp.local");
        props.setUsername("svc_scom");
        props.setPassword("pwd");
        props.setAuthMethod("NTLM"); // operator picks NTLM because the collector can't reach a KDC
        props.setUseHttps(true);
        var scoped = new ScomWinRmClient(props, new ObjectMapper());

        var script = scoped.buildInvokeCommandScript(ScomWinRmClient.buildRemoteScript(), 1);

        // NTLM is carried by WinRM Negotiate (SPNEGO) with explicit credentials — no literal "NTLM".
        assertThat(script).contains("-Authentication Negotiate");
        assertThat(script).doesNotContain("-Authentication NTLM");
    }
}
