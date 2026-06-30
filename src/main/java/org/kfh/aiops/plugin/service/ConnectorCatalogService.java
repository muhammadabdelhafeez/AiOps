package org.kfh.aiops.plugin.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.kfh.aiops.plugin.dto.ConnectorFieldSchemaDto;
import org.kfh.aiops.plugin.dto.ConnectorTypeMetadataDto;
import org.springframework.stereotype.Service;

@Service
public class ConnectorCatalogService {

    public static final String BMC_PLUGIN_TYPE = "BMC";
    public static final String APPDYNAMICS_PLUGIN_TYPE = "APPDYNAMICS";
    public static final String VROPS_PLUGIN_TYPE = "VROPS";
    public static final String SCOM_PLUGIN_TYPE = "SCOM";
    public static final String EMCO_PLUGIN_TYPE = "EMCO";

    private static final List<String> COUNTRIES = List.of("KW", "BH", "EG");
    private static final List<String> ENVIRONMENTS = List.of("PROD", "UAT", "DEV");

    public List<ConnectorTypeMetadataDto> types() {
        return List.of(
                bmcHelix(),
                appDynamics(),
                vrops(),
                scom(),
                emco(),
                future("LANSWEEPER", "Lansweeper", "Inventory", "file",
                        "Excel-exported scan events and software-change evidence."));
    }

    public String normalizePluginType(Object value) {
        var raw = value == null ? "" : String.valueOf(value).trim();
        if (raw.isBlank()) {
            return "";
        }
        var normalized = raw.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "BMC", "BMC_HELIX", "BMCHELIX" -> BMC_PLUGIN_TYPE;
            case "APPD", "APPDYNAMICS", "APP_DYNAMICS" -> APPDYNAMICS_PLUGIN_TYPE;
            case "VROPS", "VRO", "VROPS_ARIA", "VMWARE_VROPS", "VMWARE_ARIA", "ARIA_OPERATIONS" -> VROPS_PLUGIN_TYPE;
            case "SCOM", "MICROSOFT_SCOM", "MS_SCOM", "SYSTEM_CENTER_OPERATIONS_MANAGER" -> SCOM_PLUGIN_TYPE;
            case "EMCO", "EMCO_PING", "EMCO_PING_MONITOR", "PING_MONITOR" -> EMCO_PLUGIN_TYPE;
            default -> normalized;
        };
    }

    public boolean isAvailable(String pluginType) {
        return types().stream()
                .anyMatch(type -> type.pluginType().equals(normalizePluginType(pluginType)) && type.available());
    }

    private ConnectorTypeMetadataDto bmcHelix() {
        return new ConnectorTypeMetadataDto(
                BMC_PLUGIN_TYPE,
                "BMC Helix",
                "Event Management",
                "server",
                "BMC Helix event ingestion using access-key authentication and paged msearch collection.",
                true,
                COUNTRIES,
                ENVIRONMENTS,
                List.of(
                        select("countryCode", "Country", "Scope", true, "Country-specific connector installation", null, COUNTRIES),
                        select("environment", "Environment", "Scope", true, "Collection environment for this connector", "PROD", ENVIRONMENTS),
                        field("baseUrl", "BMC Base URL", "url", "Connection", true, false,
                                "https://kfh-itom.onbmc.com", "HTTPS BMC tenant URL; no paths, query strings, or credentials", null),
                        field("loginEndpoint", "Login Endpoint", "text", "Connection", true, false,
                                "/ims/api/v1/access_keys/login", "Safe relative path for BMC access-key login", "/ims/api/v1/access_keys/login"),
                        field("eventsEndpoint", "Events Search Endpoint", "text", "Connection", true, false,
                                "/events-service/api/v1.0/events/msearch", "Safe relative path for BMC event msearch", "/events-service/api/v1.0/events/msearch"),
                        field("verifySsl", "Verify TLS Certificate Chain", "checkbox", "Connection", false, false,
                                null, "Keep enabled by default. Clear only for governed dev/hybrid tests while the corporate CA is not yet in the JVM truststore.", true),
                        field("accessKey", "Access Key", "password", "Credentials", true, true,
                                "Stored encrypted server-side", "Never returned by the API or written to logs", null),
                        field("accessSecretKey", "Access Secret Key", "password", "Credentials", true, true,
                                "Stored encrypted server-side", "Never returned by the API or written to logs", null),
                        field("minutesBack", "Collection Window Minutes", "number", "Collection", true, false,
                                "60", "How far back each BMC event search should query", 60),
                        field("pageSize", "Page Size", "number", "Collection", true, false,
                                "100", "BMC msearch page size", 100),
                        field("maxEvents", "Max Events Per Run", "number", "Collection", true, false,
                                "500", "Hard cap per connector run to prevent raw alert floods", 500),
                        field("timeoutSeconds", "Timeout Seconds", "number", "Collection", true, false,
                                "120", "Outbound request timeout for BMC API calls", 120),
                        field("intervalMin", "Sync Interval Minutes", "number", "Schedule", true, false,
                                "15", "Minimum scheduled collection interval", 15)),
                Map.of(
                        "authMode", "AccessKey",
                        "minutesBack", 60,
                        "pageSize", 100,
                        "maxEvents", 500,
                        "timeoutSeconds", 120,
                        "verifySsl", true,
                        "intervalMin", 15,
                        "loginEndpoint", "/ims/api/v1/access_keys/login",
                        "eventsEndpoint", "/events-service/api/v1.0/events/msearch"));
    }

    private ConnectorTypeMetadataDto appDynamics() {
        return new ConnectorTypeMetadataDto(
                APPDYNAMICS_PLUGIN_TYPE,
                "AppDynamics",
                "Application Performance",
                "activity",
                "AppDynamics application discovery, business transactions, error snapshots, health-rule violations, and slow transactions.",
                true,
                COUNTRIES,
                ENVIRONMENTS,
                List.of(
                        select("countryCode", "Country", "Scope", true, "Country-specific connector installation", null, COUNTRIES),
                        select("environment", "Environment", "Scope", true, "Collection environment for this connector", "PROD", ENVIRONMENTS),
                        field("controllerUrl", "Controller URL", "url", "Connection", true, false,
                                "https://appd.corp.kfh.kw/controller", "HTTPS AppDynamics controller URL ending with /controller", null),
                        field("verifySsl", "Verify TLS Certificate Chain", "checkbox", "Connection", false, false,
                                null, "Keep enabled by default. Clear only for governed dev/hybrid tests while the corporate CA is not yet in the JVM truststore.", true),
                        field("username", "Username", "password", "Credentials", true, true,
                                "Stored encrypted server-side", "AppDynamics Basic Auth username; never returned by the API", null),
                        field("password", "Password", "password", "Credentials", true, true,
                                "Stored encrypted server-side", "AppDynamics Basic Auth password; never returned by the API", null),
                        field("durationMinutes", "Duration Minutes", "number", "Collection", true, false,
                                "60", "How far back AppDynamics snapshot and violation queries should inspect", 60),
                        field("timeoutSeconds", "Timeout Seconds", "number", "Collection", true, false,
                                "120", "Outbound request timeout for AppDynamics Controller API calls", 120),
                        field("maxWorkers", "Max Workers", "number", "Collection", true, false,
                                "15", "Maximum concurrent application-level collection workers", 15),
                        field("fetchErrors", "Fetch Error Snapshots", "checkbox", "Collection", false, false,
                                null, "Collect request snapshots where userExperience is ERROR or errorOccurred is true", true),
                        field("fetchViolations", "Fetch Health Rule Violations", "checkbox", "Collection", false, false,
                                null, "Collect health-rule violations from each application", true),
                        field("fetchSlowTransactions", "Fetch Slow Transactions", "checkbox", "Collection", false, false,
                                null, "Collect SLOW, VERY_SLOW, and STALL request snapshots", true),
                        field("intervalMin", "Sync Interval Minutes", "number", "Schedule", true, false,
                                "15", "Minimum scheduled collection interval", 15)),
                Map.of(
                        "authMode", "BasicAuth",
                        "durationMinutes", 60,
                        "timeoutSeconds", 120,
                        "verifySsl", true,
                        "maxWorkers", 15,
                        "intervalMin", 15,
                        "fetchErrors", true,
                        "fetchViolations", true,
                        "fetchSlowTransactions", true));
    }

    private ConnectorTypeMetadataDto vrops() {
        return new ConnectorTypeMetadataDto(
                VROPS_PLUGIN_TYPE,
                "VMware vROps",
                "Infrastructure",
                "cloud",
                "VMware Aria Operations alert discovery, resource health, and resource enrichment telemetry.",
                true,
                COUNTRIES,
                ENVIRONMENTS,
                List.of(
                        select("countryCode", "Country", "Scope", true, "Country-specific connector installation", null, COUNTRIES),
                        select("environment", "Environment", "Scope", true, "Collection environment for this connector", "PROD", ENVIRONMENTS),
                        field("host", "vROps Host or URL", "text", "Connection", true, false,
                                "10.2.243.66", "Hostname, IP, or HTTPS URL ending with /suite-api/api", null),
                        field("authSource", "Auth Source", "text", "Connection", true, false,
                                "KFH AD", "vROps authentication source name", "KFH AD"),
                        field("verifySsl", "Verify TLS Certificate Chain", "checkbox", "Connection", false, false,
                                null, "Keep enabled by default. Clear only for governed dev/hybrid tests while the corporate CA is not yet in the JVM truststore.", true),
                        field("username", "Username", "password", "Credentials", true, true,
                                "Stored encrypted server-side", "vROps username; never returned by the API", null),
                        field("password", "Password", "password", "Credentials", true, true,
                                "Stored encrypted server-side", "vROps password; never returned by the API", null),
                        field("hours", "Collection Window Hours", "number", "Collection", true, false,
                                "1", "How far back each vROps alert search should inspect", 1),
                        field("pageSize", "Page Size", "number", "Collection", true, false,
                                "1000", "vROps alerts page size", 1000),
                        field("maxPages", "Max Pages", "number", "Collection", true, false,
                                "200", "Maximum alert pages per run", 200),
                        field("maxWorkers", "Max Workers", "number", "Collection", true, false,
                                "12", "Maximum concurrent resource enrichment workers", 12),
                        field("timeoutSeconds", "Timeout Seconds", "number", "Collection", true, false,
                                "120", "Outbound request timeout for vROps API calls", 120),
                        field("intervalMin", "Sync Interval Minutes", "number", "Schedule", true, false,
                                "15", "Minimum scheduled collection interval", 15)),
                Map.of(
                        "authMode", "Token",
                        "authSource", "KFH AD",
                        "hours", 1,
                        "pageSize", 1000,
                        "maxPages", 200,
                        "maxWorkers", 12,
                        "timeoutSeconds", 120,
                        "intervalMin", 15,
                        "verifySsl", true));
    }

    private ConnectorTypeMetadataDto scom() {
        return new ConnectorTypeMetadataDto(
                SCOM_PLUGIN_TYPE,
                "Microsoft SCOM",
                "Infrastructure",
                "server",
                "Microsoft SCOM alert collection through governed WinRM/PowerShell execution and OperationsManager cmdlets.",
                true,
                COUNTRIES,
                ENVIRONMENTS,
                List.of(
                        select("countryCode", "Country", "Scope", true, "Country-specific connector installation", null, COUNTRIES),
                        select("environment", "Environment", "Scope", true, "Collection environment for this connector", "PROD", ENVIRONMENTS),
                        field("managementServer", "SCOM Management Server", "text", "Connection", true, false,
                                "dcvscoap12.corp.kfh.kw", "SCOM management server FQDN/IP or WinRM URL ending with /wsman", null),
                        field("domain", "Domain", "text", "Connection", true, false,
                                "corp.kfh.kw", "Domain used to build DOMAIN\\username unless the username is already qualified", "corp.kfh.kw"),
                        field("winrmPort", "WinRM Port", "number", "Connection", true, false,
                                "5986", "Default is 5986 for HTTPS WinRM or 5985 for HTTP WinRM", 5986),
                        field("useHttps", "Use HTTPS WinRM", "checkbox", "Connection", false, false,
                                null, "Use SSL transport for WinRM remoting", true),
                        field("verifySsl", "Verify TLS Certificate Chain", "checkbox", "Connection", false, false,
                                null, "Keep enabled by default. Clear only for governed dev/hybrid tests while WinRM certificate trust is being remediated.", true),
                        select("authMethod", "PowerShell Authentication", "Connection", true,
                                "PowerShell remoting authentication method", "Kerberos", List.of("Kerberos", "Negotiate", "Default", "CredSSP")),
                        field("username", "Username", "password", "Credentials", true, true,
                                "Stored encrypted server-side", "SCOM service account username; never returned by the API", null),
                        field("password", "Password", "password", "Credentials", true, true,
                                "Stored encrypted server-side", "SCOM service account password; never returned by the API", null),
                        field("hoursBack", "Collection Window Hours", "number", "Collection", true, false,
                                "1", "How far back each SCOM alert query should inspect", 1),
                        field("connectionTimeoutSeconds", "Connection Timeout Seconds", "number", "Collection", true, false,
                                "60", "PowerShell/WinRM readiness timeout", 60),
                        field("intervalMin", "Sync Interval Minutes", "number", "Schedule", true, false,
                                "15", "Minimum scheduled collection interval", 15)),
                Map.of(
                        "authMode", "WinRM",
                        "managementServer", "dcvscoap12.corp.kfh.kw",
                        "domain", "corp.kfh.kw",
                        "winrmPort", 5986,
                        "useHttps", true,
                        "verifySsl", true,
                        "authMethod", "Kerberos",
                        "hoursBack", 1,
                        "connectionTimeoutSeconds", 60,
                        "intervalMin", 15));
    }

    private ConnectorTypeMetadataDto emco() {
        return new ConnectorTypeMetadataDto(
                EMCO_PLUGIN_TYPE,
                "EMCO Ping Monitor",
                "Network",
                "database",
                "SQL Server-backed network connectivity events from EMCO Ping Monitor for KFH and CCTV domains.",
                true,
                COUNTRIES,
                ENVIRONMENTS,
                List.of(
                        select("countryCode", "Country", "Scope", true, "Country-specific connector installation", null, COUNTRIES),
                        select("environment", "Environment", "Scope", true, "Collection environment for this connector", "PROD", ENVIRONMENTS),
                        field("sqlServer", "SQL Server Host", "text", "Connection", true, false,
                                "DCVSAMDB01", "SQL Server hostname or IP only; credentials, paths, query strings, and metadata targets are blocked", "DCVSAMDB01"),
                        field("sqlPort", "SQL Server Port", "number", "Connection", true, false,
                                "11433", "EMCO SQL Server listener port", 11433),
                        field("kfhDatabase", "KFH Database", "text", "Connection", true, false,
                                "EMCO_KFH_PROD", "Database containing db_owner.tb_host_events and db_owner.tb_hosts", "EMCO_KFH_PROD"),
                        field("cctvDatabase", "CCTV Database", "text", "Connection", true, false,
                                "EMCO_CCTV_PROD", "Database containing dbo.tb_host_events and dbo.tb_hosts", "EMCO_CCTV_PROD"),
                        field("kfhUsername", "KFH SQL Username", "password", "Credentials", true, true,
                                "Stored encrypted server-side", "Never returned by the API or written to logs", null),
                        field("kfhPassword", "KFH SQL Password", "password", "Credentials", true, true,
                                "Stored encrypted server-side", "Never returned by the API or written to logs", null),
                        field("cctvUsername", "CCTV SQL Username", "password", "Credentials", true, true,
                                "Stored encrypted server-side", "Never returned by the API or written to logs", null),
                        field("cctvPassword", "CCTV SQL Password", "password", "Credentials", true, true,
                                "Stored encrypted server-side", "Never returned by the API or written to logs", null),
                        field("minutesBack", "Collection Window Minutes", "number", "Collection", true, false,
                                "60", "How far back each EMCO SQL query should inspect", 60),
                        field("connectionTimeoutSeconds", "Connection Timeout Seconds", "number", "Collection", true, false,
                                "30", "SQL Server login timeout", 30),
                        field("queryTimeoutSeconds", "Query Timeout Seconds", "number", "Collection", true, false,
                                "120", "PreparedStatement query timeout for KFH/CCTV probes", 120),
                        field("encrypt", "Encrypt SQL Connection", "checkbox", "Connection", false, false,
                                null, "Keep enabled for governed SQL Server TLS transport", true),
                        field("trustServerCertificate", "Trust Server Certificate", "checkbox", "Connection", false, false,
                                null, "Keep disabled unless approved for dev/hybrid certificate remediation", false),
                        field("intervalMin", "Sync Interval Minutes", "number", "Schedule", true, false,
                                "15", "Minimum scheduled collection interval", 15)),
                Map.ofEntries(
                        Map.entry("authMode", "SqlServerCredentials"),
                        Map.entry("sqlServer", "DCVSAMDB01"),
                        Map.entry("sqlPort", 11433),
                        Map.entry("kfhDatabase", "EMCO_KFH_PROD"),
                        Map.entry("cctvDatabase", "EMCO_CCTV_PROD"),
                        Map.entry("minutesBack", 60),
                        Map.entry("connectionTimeoutSeconds", 30),
                        Map.entry("queryTimeoutSeconds", 120),
                        Map.entry("encrypt", true),
                        Map.entry("trustServerCertificate", false),
                        Map.entry("intervalMin", 15)));
    }

    private ConnectorTypeMetadataDto future(String pluginType, String name, String category, String icon, String description) {
        return new ConnectorTypeMetadataDto(pluginType, name, category, icon, description, false,
                COUNTRIES, ENVIRONMENTS, List.of(), Map.of("status", "COMING_SOON"));
    }

    private static ConnectorFieldSchemaDto field(String key, String label, String type, String section,
            boolean required, boolean secret, String placeholder, String helpText, Object defaultValue) {
        return new ConnectorFieldSchemaDto(key, label, type, section, required, secret, placeholder, helpText,
                defaultValue, List.of());
    }

    private static ConnectorFieldSchemaDto select(String key, String label, String section, boolean required,
            String helpText, Object defaultValue, List<String> options) {
        return new ConnectorFieldSchemaDto(key, label, "select", section, required, false, null, helpText,
                defaultValue, options);
    }
}

