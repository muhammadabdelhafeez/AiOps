package org.kfh.aiops.plugin.implementations.scom;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.kfh.aiops.plugin.security.ConnectorEndpointGuard;
import org.kfh.aiops.plugin.security.ConnectorTlsSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/** Runs a bounded, secret-safe SCOM WinRM readiness probe through local PowerShell remoting. */
@Service
public class PowerShellScomConnectorLiveTester implements ScomConnectorLiveTester {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final Environment environment;
    private final ConnectorEndpointGuard endpointGuard;
    private final ScomCommandExecutor commandExecutor;

    @Autowired
    public PowerShellScomConnectorLiveTester(Environment environment) {
        this(environment, new DefaultScomCommandExecutor());
    }

    PowerShellScomConnectorLiveTester(Environment environment, ScomCommandExecutor commandExecutor) {
        this.environment = environment;
        this.endpointGuard = new ConnectorEndpointGuard(environment);
        this.commandExecutor = commandExecutor;
    }

    @Override
    public Map<String, Object> test(TenantContext ctx, Map<String, Object> connector, Map<String, String> secrets) {
        var started = System.nanoTime();
        var steps = new ArrayList<Map<String, Object>>();
        var connectorId = text(connector, "id", text(connector, "connectorId", ""));
        var verifySsl = ConnectorTlsSupport.verifySsl(connector);
        try {
            var config = ScomProbeConfig.from(connector, secrets);
            verifySsl = config.verifySsl();
            validateConfig(config);
            steps.add(step("Configuration", "pass", "Required SCOM WinRM endpoint, domain, username, and password are present."));
            steps.add(step("TLS certificate verification", "pass", ConnectorTlsSupport.verificationModeMessage(config.verifySsl())));
            var probe = executeProbe(config);
            steps.add(step("PowerShell WinRM probe", "pass", "Remote OperationsManager module and SCOM cmdlet probe completed."));
            steps.add(step("SCOM parsing", "pass", "Selected SCOM alert fields are available; sample count " + probe.sampleCount() + "."));
            return result(ctx, connectorId, true, latencyMs(started),
                    "SCOM connector is reachable and ready for governed WinRM alert collection.",
                    config.wsmanUrl(), steps, verifySsl);
        } catch (RuntimeException ex) {
            steps.add(step(steps.isEmpty() ? "Configuration" : "SCOM communication", "fail", safeMessage(ex)));
            return result(ctx, connectorId, false, latencyMs(started),
                    "SCOM connector test failed: " + safeMessage(ex), checkedEndpoint(connector), steps, verifySsl);
        }
    }

    private ScomProbeResult executeProbe(ScomProbeConfig config) {
        var completed = commandExecutor.execute(powerShellCommand(), processEnvironment(config),
                Duration.ofSeconds(config.timeoutSeconds()));
        if (completed.exitCode() != 0) {
            throw new IllegalStateException("PowerShell SCOM probe exited with code " + completed.exitCode()
                    + safeOutputSuffix(completed.stderr(), completed.stdout()));
        }
        try {
            var json = extractJson(completed.stdout());
            var parsed = JSON.readValue(json, MAP);
            return new ScomProbeResult(integer(parsed.get("sampleCount"), 0));
        } catch (Exception ex) {
            throw new IllegalStateException("PowerShell SCOM probe completed but did not return valid JSON");
        }
    }

    private List<String> powerShellCommand() {
        return List.of(powerShellExecutable(), "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-Command", powerShellScript());
    }

    private String powerShellExecutable() {
        return environment.getProperty("kfh.connectors.scom.powershell-executable", "powershell.exe");
    }

    private static Map<String, String> processEnvironment(ScomProbeConfig config) {
        var values = new LinkedHashMap<String, String>();
        values.put("KFH_AIOPS_SCOM_SERVER", config.managementServer());
        values.put("KFH_AIOPS_SCOM_QUALIFIED_USERNAME", config.qualifiedUsername());
        values.put("KFH_AIOPS_SCOM_PASSWORD", config.password());
        values.put("KFH_AIOPS_SCOM_PORT", String.valueOf(config.winrmPort()));
        values.put("KFH_AIOPS_SCOM_USE_SSL", String.valueOf(config.useHttps()));
        values.put("KFH_AIOPS_SCOM_VERIFY_SSL", String.valueOf(config.verifySsl()));
        values.put("KFH_AIOPS_SCOM_AUTH_METHOD", config.authMethod());
        return values;
    }

    private void validateConfig(ScomProbeConfig config) {
        if (config.managementServer().isBlank()) {
            throw new IllegalArgumentException("SCOM management server is required. Save connector configuration first.");
        }
        if (config.domain().isBlank()) {
            throw new IllegalArgumentException("SCOM domain is required.");
        }
        if (config.username().isBlank() || config.password().isBlank()) {
            throw new IllegalArgumentException("SCOM username and password are required. Save credentials first.");
        }
        endpointGuard.validateLiteralHost("SCOM", config.managementServer());
        if (environment.getProperty("kfh.security.ssrf.resolve-hosts", Boolean.class, true)) {
            endpointGuard.validateResolvedAddresses("SCOM", config.managementServer());
        }
    }

    private static String powerShellScript() {
        return """
                $ErrorActionPreference = 'Stop'
                $qualifiedUser = $env:KFH_AIOPS_SCOM_QUALIFIED_USERNAME
                $password = $env:KFH_AIOPS_SCOM_PASSWORD
                $server = $env:KFH_AIOPS_SCOM_SERVER
                $port = [int]$env:KFH_AIOPS_SCOM_PORT
                $useSsl = [System.Convert]::ToBoolean($env:KFH_AIOPS_SCOM_USE_SSL)
                $verifySsl = [System.Convert]::ToBoolean($env:KFH_AIOPS_SCOM_VERIFY_SSL)
                $auth = $env:KFH_AIOPS_SCOM_AUTH_METHOD
                if ([string]::IsNullOrWhiteSpace($qualifiedUser) -or [string]::IsNullOrWhiteSpace($password)) { throw 'SCOM credentials are not configured' }
                $securePassword = ConvertTo-SecureString $password -AsPlainText -Force
                $credential = New-Object System.Management.Automation.PSCredential($qualifiedUser, $securePassword)
                $invokeArgs = @{ ComputerName = $server; Port = $port; Credential = $credential; Authentication = $auth; ScriptBlock = {
                    Import-Module OperationsManager -ErrorAction Stop | Out-Null
                    New-SCOMManagementGroupConnection -ComputerName $env:COMPUTERNAME | Out-Null
                    $sample = @(Get-SCOMAlert | Select-Object -First 1 Id, Name, Severity, ResolutionState, MonitoringObjectDisplayName, MonitoringObjectPath, NetbiosComputerName, TimeRaised)
                    [pscustomobject]@{ module = 'OperationsManager'; sampleCount = $sample.Count; fields = @('Id','Name','Severity','Priority','Category','ResolutionState','MonitoringObjectName','MonitoringObjectPath','NetbiosComputerName','TimeRaised','TimeResolved','LastModified','Description') }
                } }
                if ($useSsl) { $invokeArgs.UseSSL = $true }
                if (-not $verifySsl) { $invokeArgs.SessionOption = New-PSSessionOption -SkipCACheck -SkipCNCheck -SkipRevocationCheck }
                Invoke-Command @invokeArgs | ConvertTo-Json -Depth 8 -Compress
                """;
    }

    private static Map<String, Object> result(TenantContext ctx, String connectorId, boolean pass, long latencyMs,
            String message, String checkedEndpoint, List<Map<String, Object>> steps, boolean verifySsl) {
        var result = new LinkedHashMap<String, Object>();
        result.put("connectorRunId", UUID.randomUUID().toString());
        result.put("connectorId", connectorId);
        result.put("pass", pass);
        result.put("readyToCollect", pass);
        result.put("status", pass ? "Pass" : "Fail");
        result.put("latencyMs", latencyMs);
        result.put("message", message);
        result.put("checkedEndpoint", checkedEndpoint);
        result.put("testedAt", Instant.now().toString());
        result.put("correlationId", ctx.correlationId());
        result.put("verifySsl", verifySsl);
        result.put("steps", steps);
        return result;
    }

    private static String extractJson(String output) {
        var text = Objects.toString(output, "").trim();
        var start = text.indexOf('{');
        var end = text.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("No JSON object returned");
        }
        return text.substring(start, end + 1).replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
    }

    private static String safeOutputSuffix(String stderr, String stdout) {
        var text = !text(stderr, "").isBlank() ? stderr : stdout;
        var safe = safeMessage(new IllegalStateException(text));
        return safe.isBlank() ? "" : ": " + safe;
    }

    private static String safeMessage(RuntimeException ex) {
        var message = Objects.toString(ex.getMessage(), ex.getClass().getSimpleName());
        return ConnectorTlsSupport.enrichCertificateFailure(message
                .replaceAll("(?i)(password|authorization|token|secret|credential|username|access\\s+key|access\\s+secret)\\s*[:=]\\s*[^,;\\s]+", "$1=masked")
                .replaceAll("(?i)(-credential|-authentication)\\s+[^,;\\s]+", "$1 masked")
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim());
    }

    private static Map<String, Object> step(String name, String status, String message) {
        return Map.of("name", name, "status", status, "message", message);
    }

    private static String checkedEndpoint(Map<String, Object> connector) {
        return text(connector, "endpointUrl", text(connector, "baseUrl", text(connector, "managementServer", "")));
    }

    private static long latencyMs(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private static String text(Map<String, ?> values, String key, String fallback) {
        var value = values == null ? null : values.get(key);
        return value == null ? fallback : String.valueOf(value).trim();
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int integer(Object value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private record ScomProbeResult(int sampleCount) { }

    private record ScomProbeConfig(String managementServer, String domain, String username, String password,
            int winrmPort, boolean useHttps, boolean verifySsl, String authMethod, int timeoutSeconds) {

        static ScomProbeConfig from(Map<String, Object> connector, Map<String, String> secrets) {
            var endpoint = text(connector, "endpointUrl", text(connector, "baseUrl", ""));
            var server = text(connector, "managementServer", text(connector, "host", ""));
            var port = integer(connector.get("winrmPort"), 5986);
            var useHttps = bool(connector.get("useHttps"));
            if (!endpoint.isBlank()) {
                var uri = URI.create(endpoint);
                server = text(uri.getHost(), server);
                port = uri.getPort() > 0 ? uri.getPort() : port;
                useHttps = "https".equalsIgnoreCase(uri.getScheme());
            }
            return new ScomProbeConfig(server, text(connector, "domain", ""),
                    text(secrets, "username", text(secrets, "user", text(secrets, "scomUsername", ""))),
                    text(secrets, "password", text(secrets, "scomPassword", "")),
                    port, useHttps, ConnectorTlsSupport.verifySsl(connector), text(connector, "authMethod", "Kerberos"),
                    integer(connector.get("connectionTimeoutSeconds"), integer(connector.get("timeoutSeconds"), 60)));
        }

        String wsmanUrl() {
            return (useHttps ? "https://" : "http://") + managementServer + ":" + winrmPort + "/wsman";
        }

        String qualifiedUsername() {
            if (username.contains("\\") || username.contains("@") || domain.isBlank()) {
                return username;
            }
            return domain + "\\" + username;
        }
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null || String.valueOf(value).isBlank() || Boolean.parseBoolean(String.valueOf(value));
    }
}

@FunctionalInterface
interface ScomCommandExecutor {
    ScomCommandResult execute(List<String> command, Map<String, String> environment, Duration timeout);
}

record ScomCommandResult(int exitCode, String stdout, String stderr) { }

final class DefaultScomCommandExecutor implements ScomCommandExecutor {

    @Override
    public ScomCommandResult execute(List<String> command, Map<String, String> environment, Duration timeout) {
        try {
            var processBuilder = new ProcessBuilder(command);
            processBuilder.environment().putAll(environment);
            var process = processBuilder.start();
            var stdout = CompletableFuture.supplyAsync(() -> read(process.getInputStream()));
            var stderr = CompletableFuture.supplyAsync(() -> read(process.getErrorStream()));
            if (!process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("PowerShell SCOM probe timed out");
            }
            return new ScomCommandResult(process.exitValue(), stdout.join(), stderr.join());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("PowerShell SCOM probe was interrupted");
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("PowerShell SCOM probe could not start or complete");
        }
    }

    private static String read(InputStream stream) {
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return String.join("\n", reader.lines().toList());
        } catch (Exception ex) {
            return "";
        }
    }
}
