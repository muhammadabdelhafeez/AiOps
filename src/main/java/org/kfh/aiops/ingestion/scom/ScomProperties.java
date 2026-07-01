package org.kfh.aiops.ingestion.scom;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds {@code kfh.ingestion.scom.*}. Secrets (username/password) come from environment only and are
 * never committed. {@code enabled=false} by default so no WinRM/PowerShell session is spawned until
 * configured. {@code serverLocalOffsetHours} is the SCOM server's local UTC offset (Kuwait = 3) used
 * for the PowerShell over-fetch buffer; Java still filters precisely by UTC epoch.
 */
@Component
@ConfigurationProperties(prefix = "kfh.ingestion.scom")
public class ScomProperties {

    private boolean enabled = false;
    private String managementServer;
    private String username;
    private String password;
    private String domain;
    private int hoursBack = 1;
    private int connectionTimeoutSeconds = 60;
    private boolean verifySsl = true;
    private int winrmPort = 5986;
    private boolean useHttps = true;
    private String authMethod = "Kerberos";
    private int serverLocalOffsetHours = 3;
    private long pollIntervalMs = 1_200_000L; // 20 minutes
    private long initialDelayMs = 90_000L;
    private String tenantId = "00000000-0000-4000-8000-000000000001";
    private String countryCode = "KW";
    private String environment = "PROD";

    /** True only when management server + both credentials are present. */
    public boolean isConfigured() {
        return hasText(managementServer) && hasText(username) && hasText(password);
    }

    /** Domain-qualified username ({@code DOMAIN\\user}) when a NetBIOS domain is set. */
    public String fullUsername() {
        if (!hasText(username)) {
            return username;
        }
        if (hasText(domain) && !username.contains("\\") && !username.contains("@")) {
            return domain + "\\" + username;
        }
        return username;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getManagementServer() {
        return managementServer;
    }

    public void setManagementServer(String managementServer) {
        this.managementServer = managementServer;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public int getHoursBack() {
        return hoursBack;
    }

    public void setHoursBack(int hoursBack) {
        this.hoursBack = hoursBack;
    }

    public int getConnectionTimeoutSeconds() {
        return connectionTimeoutSeconds;
    }

    public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
    }

    public boolean isVerifySsl() {
        return verifySsl;
    }

    public void setVerifySsl(boolean verifySsl) {
        this.verifySsl = verifySsl;
    }

    public int getWinrmPort() {
        return winrmPort;
    }

    public void setWinrmPort(int winrmPort) {
        this.winrmPort = winrmPort;
    }

    public boolean isUseHttps() {
        return useHttps;
    }

    public void setUseHttps(boolean useHttps) {
        this.useHttps = useHttps;
    }

    public String getAuthMethod() {
        return authMethod;
    }

    public void setAuthMethod(String authMethod) {
        this.authMethod = authMethod;
    }

    public int getServerLocalOffsetHours() {
        return serverLocalOffsetHours;
    }

    public void setServerLocalOffsetHours(int serverLocalOffsetHours) {
        this.serverLocalOffsetHours = serverLocalOffsetHours;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    public void setInitialDelayMs(long initialDelayMs) {
        this.initialDelayMs = initialDelayMs;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }
}
