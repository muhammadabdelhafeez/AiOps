package org.kfh.aiops.ingestion.bmc;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds {@code kfh.ingestion.bmc.*}. Secrets (access key/secret) are supplied via environment only and
 * never committed; {@code enabled=false} by default so no outbound BMC calls happen until configured.
 * {@code countryCode}/{@code environment}/{@code tenantId} scope events pulled by the scheduled poll
 * (the manual endpoint uses the caller's scope instead).
 */
@Component
@ConfigurationProperties(prefix = "kfh.ingestion.bmc")
public class BmcProperties {

    private boolean enabled = false;
    private String baseUrl;
    private String accessKey;
    private String accessSecretKey;
    private String loginEndpoint = "/ims/api/v1/access_keys/login";
    private String eventsEndpoint = "/events-service/api/v1.0/events/msearch";
    private int minutesBack = 30;
    private int maxEvents = 500;
    private long pollIntervalMs = 1_200_000L; // 20 minutes
    private long initialDelayMs = 60_000L;
    private String tenantId = "00000000-0000-4000-8000-000000000001";
    private String countryCode = "KW";
    private String environment = "PROD";

    /** True only when base URL + both credentials are present. */
    public boolean isConfigured() {
        return hasText(baseUrl) && hasText(accessKey) && hasText(accessSecretKey);
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

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getAccessSecretKey() {
        return accessSecretKey;
    }

    public void setAccessSecretKey(String accessSecretKey) {
        this.accessSecretKey = accessSecretKey;
    }

    public String getLoginEndpoint() {
        return loginEndpoint;
    }

    public void setLoginEndpoint(String loginEndpoint) {
        this.loginEndpoint = loginEndpoint;
    }

    public String getEventsEndpoint() {
        return eventsEndpoint;
    }

    public void setEventsEndpoint(String eventsEndpoint) {
        this.eventsEndpoint = eventsEndpoint;
    }

    public int getMinutesBack() {
        return minutesBack;
    }

    public void setMinutesBack(int minutesBack) {
        this.minutesBack = minutesBack;
    }

    public int getMaxEvents() {
        return maxEvents;
    }

    public void setMaxEvents(int maxEvents) {
        this.maxEvents = maxEvents;
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
