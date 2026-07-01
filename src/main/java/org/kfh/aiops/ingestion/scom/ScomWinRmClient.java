package org.kfh.aiops.ingestion.scom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fetches SCOM alerts by spawning the local {@code powershell.exe} and running {@code Get-SCOMAlert}
 * over WinRM (docs/SCOM_Collectors.md). Returns raw {@code Map<String,Object>} rows (with WCF dates
 * converted to epoch millis) for the shared ingestion pipeline. Parsing helpers are package-private so
 * they can be unit-tested without a live SCOM server.
 *
 * <p>Security: all values interpolated into the PowerShell command (server, username, password) are
 * single-quote-escaped; the auth method is whitelisted and the port range-checked, so a hostile config
 * value cannot break out of the quoted string. The password is never logged.
 */
@Component
public class ScomWinRmClient {

    private static final Logger log = LoggerFactory.getLogger(ScomWinRmClient.class);
    private static final Pattern WCF_DATE = Pattern.compile("/Date\\((-?\\d+)([+\\-]\\d{4})?\\)/");

    private final ScomProperties properties;
    private final ObjectMapper objectMapper;

    public ScomWinRmClient(ScomProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** Spawn PowerShell, run Get-SCOMAlert, parse + UTC-filter + dedup to raw event maps. */
    public List<Map<String, Object>> fetchRawEvents(int hoursBack) {
        var cutoffEpochMs = Instant.now().minusSeconds(hoursBack * 3600L).toEpochMilli();
        var json = executeWinRmCommand(hoursBack);
        var events = parseAlerts(json, cutoffEpochMs);
        log.info("SCOM returned {} alerts within window (last {}h)", events.size(), hoursBack);
        return events;
    }

    private String executeWinRmCommand(int hoursBack) {
        var psCommand = buildInvokeCommandScript(buildRemoteScript(), hoursBack);
        var pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-Command", psCommand);
        pb.redirectErrorStream(true);
        log.debug("Connecting to SCOM {} as {}", properties.getManagementServer(), properties.fullUsername());
        try {
            var process = pb.start();
            var output = new StringBuilder();
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            var completed = process.waitFor(properties.getConnectionTimeoutSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new IllegalStateException(
                        "SCOM PowerShell timed out after " + properties.getConnectionTimeoutSeconds() + "s");
            }
            var result = output.toString().trim();
            if (process.exitValue() != 0) {
                throw new IllegalStateException("SCOM PowerShell failed (exit " + process.exitValue() + "): "
                        + (result.length() > 500 ? result.substring(0, 500) : result));
            }
            return extractJsonFromOutput(result);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SCOM PowerShell interrupted", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("SCOM PowerShell invocation failed: " + ex.getMessage(), ex);
        }
    }

    /** Remote script executed on the SCOM MS — filters by TimeRaised, projects the fields we index. */
    static String buildRemoteScript() {
        return "Import-Module OperationsManager -ErrorAction Stop | Out-Null; "
                + "New-SCOMManagementGroupConnection -ComputerName $env:COMPUTERNAME | Out-Null; "
                + "$alerts = @(Get-SCOMAlert | Where-Object { $_.TimeRaised -ge $startUtcInner } | Select-Object "
                + "Id, Name, Severity, Priority, Category, ResolutionState, "
                + "@{Name='MonitoringObjectName'; Expression = {$_.MonitoringObjectDisplayName}}, "
                + "MonitoringObjectPath, NetbiosComputerName, TimeRaised, TimeResolved, LastModified, "
                + "Description); "
                + "$alerts | Sort-Object TimeRaised -Descending";
    }

    /** Local Invoke-Command wrapper; escapes interpolated values and over-fetches by the server offset. */
    String buildInvokeCommandScript(String remoteScript, int hoursBack) {
        var server = requireHost(properties.getManagementServer());
        var username = properties.fullUsername();
        var password = properties.getPassword() != null ? properties.getPassword() : "";
        var authMethod = requireAuthMethod(properties.getAuthMethod());
        var winRmPort = requirePort(properties.getWinrmPort());
        var useSslSwitch = properties.isUseHttps() ? " -UseSSL" : "";
        var psHoursBack = hoursBack + Math.max(0, properties.getServerLocalOffsetHours());

        return String.format(Locale.ROOT,
                "$ErrorActionPreference = 'Stop'; "
                        + "$secPass = ConvertTo-SecureString '%s' -AsPlainText -Force; "
                        + "$cred = New-Object System.Management.Automation.PSCredential('%s', $secPass); "
                        + "$startLocal = (Get-Date).AddHours(-%d); "
                        + "$result = Invoke-Command -ComputerName '%s' -Port %d%s -Credential $cred -Authentication %s "
                        + "-ScriptBlock { param([datetime]$startUtcInner) %s } -ArgumentList $startLocal; "
                        + "if ($null -eq $result -or $result.Count -eq 0) { Write-Output '[]' } "
                        + "else { $result | ConvertTo-Json -Depth 10 -Compress }",
                escapePsSingleQuoted(password),
                escapePsSingleQuoted(username),
                psHoursBack,
                escapePsSingleQuoted(server),
                winRmPort,
                useSslSwitch,
                authMethod,
                remoteScript);
    }

    /** Parse SCOM JSON → raw maps, keep only alerts within the UTC window, dedup by Id (newest wins). */
    List<Map<String, Object>> parseAlerts(String json, long cutoffEpochMs) {
        List<Map<String, Object>> parsed = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return parsed;
        }
        try {
            var root = objectMapper.readTree(sanitizeJsonString(json));
            if (root.isArray()) {
                for (JsonNode node : root) {
                    addIfWithinWindow(parsed, node, cutoffEpochMs);
                }
            } else if (root.isObject()) {
                addIfWithinWindow(parsed, root, cutoffEpochMs);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse SCOM alerts JSON: " + ex.getMessage(), ex);
        }
        return dedupById(parsed);
    }

    private void addIfWithinWindow(List<Map<String, Object>> out, JsonNode node, long cutoffEpochMs) {
        var event = toEventMap(node);
        if (event == null) {
            return;
        }
        var lastModified = (Long) event.get("LastModified");
        var timeRaised = (Long) event.get("TimeRaised");
        if ((lastModified != null && lastModified >= cutoffEpochMs)
                || (timeRaised != null && timeRaised >= cutoffEpochMs)) {
            out.add(event);
        }
    }

    private Map<String, Object> toEventMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        Map<String, Object> event = new LinkedHashMap<>();
        putText(event, node, "Id");
        putText(event, node, "Name");
        putText(event, node, "Severity");
        putText(event, node, "Priority");
        putText(event, node, "Category");
        putText(event, node, "MonitoringObjectName");
        putText(event, node, "MonitoringObjectPath");
        putText(event, node, "NetbiosComputerName");
        putText(event, node, "Description");
        var resolutionState = node.get("ResolutionState");
        if (resolutionState != null && !resolutionState.isNull()) {
            event.put("ResolutionState", resolutionState.asInt());
        }
        putEpoch(event, node, "TimeRaised");
        putEpoch(event, node, "TimeResolved");
        putEpoch(event, node, "LastModified");
        return event.isEmpty() ? null : event;
    }

    private static void putText(Map<String, Object> event, JsonNode node, String field) {
        var value = node.get(field);
        if (value != null && !value.isNull()) {
            var text = value.asText();
            if (text != null && !text.isBlank()) {
                event.put(field, text);
            }
        }
    }

    private static void putEpoch(Map<String, Object> event, JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || value.isNull()) {
            return;
        }
        var epoch = wcfOrIsoToEpochMillis(value.asText());
        if (epoch != null) {
            event.put(field, epoch);
        }
    }

    private static List<Map<String, Object>> dedupById(List<Map<String, Object>> events) {
        Map<String, Map<String, Object>> deduped = new LinkedHashMap<>();
        for (var event : events) {
            var key = String.valueOf(event.getOrDefault("Id",
                    event.getOrDefault("Name", "") + "|" + event.getOrDefault("MonitoringObjectName", "")));
            var existing = deduped.get(key);
            if (existing == null || activity(event) > activity(existing)) {
                deduped.put(key, event);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    private static long activity(Map<String, Object> event) {
        var timeRaised = (Long) event.get("TimeRaised");
        var lastModified = (Long) event.get("LastModified");
        return Math.max(timeRaised == null ? 0L : timeRaised, lastModified == null ? 0L : lastModified);
    }

    /**
     * WCF {@code /Date(ms±zzzz)/} or ISO-8601 → epoch millis. Non-positive results (the SCOM
     * {@code 0001-01-01} / {@code /Date(-62135596800000)/} "unset" sentinels) map to {@code null}.
     */
    static Long wcfOrIsoToEpochMillis(String value) {
        if (value == null || value.isBlank() || value.startsWith("0001-01-01")) {
            return null;
        }
        var matcher = WCF_DATE.matcher(value);
        if (matcher.find()) {
            try {
                return positiveOrNull(Long.parseLong(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // fall through to ISO parsing
            }
        }
        try {
            return positiveOrNull(Instant.parse(value).toEpochMilli());
        } catch (Exception ex) {
            return null;
        }
    }

    private static Long positiveOrNull(long epochMillis) {
        return epochMillis > 0 ? epochMillis : null;
    }

    /** Extract the JSON body from noisy PowerShell stdout via a bracket-balanced scan. */
    static String extractJsonFromOutput(String output) {
        if (output == null || output.isEmpty()) {
            return null;
        }
        var trimmed = output.trim();
        if ("[]".equals(trimmed)) {
            return "[]";
        }
        var start = trimmed.indexOf('[');
        var objStart = trimmed.indexOf('{');
        if (start == -1 && objStart == -1) {
            return trimmed.contains("@()") || trimmed.isEmpty() ? "[]" : null;
        }
        if (start == -1 || (objStart != -1 && objStart < start)) {
            start = objStart;
        }
        int depth = 0;
        var inString = false;
        var escape = false;
        int end = -1;
        for (int i = start; i < trimmed.length(); i++) {
            var c = trimmed.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (!inString) {
                if (c == '[' || c == '{') {
                    depth++;
                } else if (c == ']' || c == '}') {
                    if (--depth == 0) {
                        end = i;
                        break;
                    }
                }
            }
        }
        if (end <= start) {
            return null;
        }
        return trimmed.substring(start, end + 1);
    }

    /** Strip control chars (except tab/newline/CR) — SCOM descriptions can contain BEL etc. */
    static String sanitizeJsonString(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        var sb = new StringBuilder(json.length());
        for (int i = 0; i < json.length(); i++) {
            var c = json.charAt(i);
            if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Escape a value for a PowerShell single-quoted string ({@code '} → {@code ''}). */
    static String escapePsSingleQuoted(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private static String requireHost(String host) {
        if (host == null || !host.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Invalid SCOM management-server host");
        }
        return host;
    }

    private static String requireAuthMethod(String method) {
        var value = method == null ? "" : method.trim();
        return switch (value) {
            case "Kerberos", "Negotiate", "CredSSP", "Default" -> value;
            default -> throw new IllegalArgumentException("Invalid WinRM auth-method: " + method);
        };
    }

    private static int requirePort(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid WinRM port: " + port);
        }
        return port;
    }
}
