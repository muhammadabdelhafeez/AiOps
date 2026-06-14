package org.kfh.aiops.platform.identity;

import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "kfh.identity.bootstrap")
public class IdentityBootstrapProperties {

    private boolean enabled = true;
    private UUID tenantId = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private String tenantName = "KFH Group";
    private String username = "admin";
    private String password;
    private String displayName = "KFH Bootstrap Admin";
    private String email;
    private String countryCode = "KW";
    private String environment = "PROD";
    private String roleName = "GLOBAL_ADMIN";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
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

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCountryCode() {
        return normalize(countryCode, "KW");
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getEnvironment() {
        return normalize(environment, "PROD");
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getRoleName() {
        return normalize(roleName, "GLOBAL_ADMIN");
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public boolean hasPassword() {
        return password != null && !password.isBlank();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }
}
