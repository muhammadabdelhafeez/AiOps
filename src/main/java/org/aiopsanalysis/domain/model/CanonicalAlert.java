package org.aiopsanalysis.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.HashMap;

/**
 * Canonical alert format - normalized representation of alerts from any source.
 * Stored in SharePoint as JSONL (system of record).
 * 
 * Key fields for fingerprinting:
 * - fingerprintExact: Deterministic exact match (source + alertTitle + resourceId + key attributes)
 * - fingerprintFamily: Pattern/family match (source + alertTitle + resourceType)
 */
public class CanonicalAlert {
    
    private String id;
    private String source;
    private Instant timestamp;
    private Severity severity;
    private String alertTitle;
    private String messageClean;
    private String messageRaw;
    private String resourceId;
    private String resourceName;
    private ResourceType resourceType;
    private String appId;
    private String appName;
    private String serviceId;
    private String serviceName;
    private String fingerprintExact;
    private String fingerprintFamily;
    private String rawRef;
    private String runId;
    private Map<String, String> attributes;

    public CanonicalAlert() {}

    public CanonicalAlert(String id, String source, Instant timestamp, Severity severity, String alertTitle,
                          String messageClean, String messageRaw, String resourceId, String resourceName,
                          ResourceType resourceType, String appId, String appName, String serviceId,
                          String serviceName, String fingerprintExact, String fingerprintFamily,
                          String rawRef, String runId, Map<String, String> attributes) {
        this.id = id;
        this.source = source;
        this.timestamp = timestamp;
        this.severity = severity;
        this.alertTitle = alertTitle;
        this.messageClean = messageClean;
        this.messageRaw = messageRaw;
        this.resourceId = resourceId;
        this.resourceName = resourceName;
        this.resourceType = resourceType;
        this.appId = appId;
        this.appName = appName;
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.fingerprintExact = fingerprintExact;
        this.fingerprintFamily = fingerprintFamily;
        this.rawRef = rawRef;
        this.runId = runId;
        this.attributes = attributes;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }

    public String getAlertTitle() { return alertTitle; }
    public void setAlertTitle(String alertTitle) { this.alertTitle = alertTitle; }

    public String getMessageClean() { return messageClean; }
    public void setMessageClean(String messageClean) { this.messageClean = messageClean; }

    public String getMessageRaw() { return messageRaw; }
    public void setMessageRaw(String messageRaw) { this.messageRaw = messageRaw; }

    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }

    public String getResourceName() { return resourceName; }
    public void setResourceName(String resourceName) { this.resourceName = resourceName; }

    public ResourceType getResourceType() { return resourceType; }
    public void setResourceType(ResourceType resourceType) { this.resourceType = resourceType; }

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getFingerprintExact() { return fingerprintExact; }
    public void setFingerprintExact(String fingerprintExact) { this.fingerprintExact = fingerprintExact; }

    public String getFingerprintFamily() { return fingerprintFamily; }
    public void setFingerprintFamily(String fingerprintFamily) { this.fingerprintFamily = fingerprintFamily; }

    public String getRawRef() { return rawRef; }
    public void setRawRef(String rawRef) { this.rawRef = rawRef; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public Map<String, String> getAttributes() { return attributes; }
    public void setAttributes(Map<String, String> attributes) { this.attributes = attributes; }

    /**
     * Epoch timestamp (for Neo4j indexing).
     */
    public long getTimestampEpoch() {
        return timestamp != null ? timestamp.toEpochMilli() : 0L;
    }
    
    /**
     * Build signature text for embedding generation.
     * Used for semantic similarity matching.
     */
    public String buildSignatureText() {
        StringBuilder sb = new StringBuilder();
        sb.append(source != null ? source : "").append(" ");
        sb.append(alertTitle != null ? alertTitle : "").append(" ");
        sb.append(resourceType != null ? resourceType.getDisplayName() : "").append(" ");
        sb.append(messageClean != null ? messageClean : "");
        return sb.toString().trim();
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final CanonicalAlert alert = new CanonicalAlert();

        public Builder id(String id) { alert.id = id; return this; }
        public Builder source(String source) { alert.source = source; return this; }
        public Builder timestamp(Instant timestamp) { alert.timestamp = timestamp; return this; }
        public Builder severity(Severity severity) { alert.severity = severity; return this; }
        public Builder alertTitle(String alertTitle) { alert.alertTitle = alertTitle; return this; }
        public Builder messageClean(String messageClean) { alert.messageClean = messageClean; return this; }
        public Builder messageRaw(String messageRaw) { alert.messageRaw = messageRaw; return this; }
        public Builder resourceId(String resourceId) { alert.resourceId = resourceId; return this; }
        public Builder resourceName(String resourceName) { alert.resourceName = resourceName; return this; }
        public Builder resourceType(ResourceType resourceType) { alert.resourceType = resourceType; return this; }
        public Builder appId(String appId) { alert.appId = appId; return this; }
        public Builder appName(String appName) { alert.appName = appName; return this; }
        public Builder serviceId(String serviceId) { alert.serviceId = serviceId; return this; }
        public Builder serviceName(String serviceName) { alert.serviceName = serviceName; return this; }
        public Builder fingerprintExact(String fingerprintExact) { alert.fingerprintExact = fingerprintExact; return this; }
        public Builder fingerprintFamily(String fingerprintFamily) { alert.fingerprintFamily = fingerprintFamily; return this; }
        public Builder rawRef(String rawRef) { alert.rawRef = rawRef; return this; }
        public Builder runId(String runId) { alert.runId = runId; return this; }
        public Builder attributes(Map<String, String> attributes) { alert.attributes = attributes; return this; }

        public CanonicalAlert build() { return alert; }
    }
}
