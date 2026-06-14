package org.kfh.aiops.platform.identity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.kfh.aiops.commandcenter.support.CommandCenterReadModel;
import org.kfh.aiops.platform.audit.AuditService;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class IdentityBootstrapInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdentityBootstrapInitializer.class);
    private static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000000");

    private final IdentityJdbcRepository identityJdbcRepository;
    private final IdentityBootstrapProperties properties;
    private final AuditService auditService;
    private final CommandCenterReadModel readModel;

    public IdentityBootstrapInitializer(IdentityJdbcRepository identityJdbcRepository,
            IdentityBootstrapProperties properties, AuditService auditService, CommandCenterReadModel readModel) {
        this.identityJdbcRepository = identityJdbcRepository;
        this.properties = properties;
        this.auditService = auditService;
        this.readModel = readModel;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }

        identityJdbcRepository.ensureTenant(properties.getTenantId(), properties.getTenantName());
        identityJdbcRepository.ensureDefaultRoles(properties.getTenantId());

        if (!properties.hasPassword()) {
            LOGGER.warn("Identity bootstrap tenant and roles are ready, but no bootstrap password is configured; first admin user was not created");
            return;
        }

        var ctx = new TenantContext(properties.getTenantId(), SYSTEM_USER_ID, properties.getCountryCode(),
                properties.getEnvironment(), "identity-bootstrap", Set.of("*"));
        var provisioned = identityJdbcRepository.provisionBootstrapUser(ctx, displayName(), bootstrapFields());
        if (!provisioned.changed()) {
            LOGGER.info("Identity bootstrap admin is ready for tenantId={} countryCode={} environment={}",
                    properties.getTenantId(), properties.getCountryCode(), properties.getEnvironment());
            return;
        }
        var action = provisioned.created() ? "IDENTITY_BOOTSTRAP_ADMIN_CREATED" : "IDENTITY_BOOTSTRAP_ADMIN_UPDATED";
        auditService.recordWrite(ctx, action, "Identity", String.valueOf(provisioned.user().get("id")), null,
                Map.of("id", String.valueOf(provisioned.user().get("id")), "countryCode", properties.getCountryCode(),
                        "environment", properties.getEnvironment(), "roleName", properties.getRoleName()));
        readModel.appendAudit(ctx, action, "Identity", String.valueOf(provisioned.user().get("id")), Map.of(
                "message", "Bootstrap admin " + (provisioned.created() ? "created" : "updated"),
                "details", Map.of("countryCode", properties.getCountryCode(), "environment", properties.getEnvironment(),
                        "roleName", properties.getRoleName())));
        LOGGER.info("Identity bootstrap admin {} for tenantId={} countryCode={} environment={} roleName={}",
                provisioned.created() ? "created" : "updated", properties.getTenantId(), properties.getCountryCode(),
                properties.getEnvironment(), properties.getRoleName());
    }

    private Map<String, Object> bootstrapFields() {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("username", required(properties.getUsername(), "username"));
        fields.put("email", properties.getEmail());
        fields.put("password", properties.getPassword());
        fields.put("status", "ACTIVE");
        fields.put("roles", List.of(properties.getRoleName()));
        return fields;
    }

    private String displayName() {
        return required(properties.getDisplayName(), "displayName");
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Identity bootstrap " + fieldName + " must be configured");
        }
        return value.trim();
    }
}
