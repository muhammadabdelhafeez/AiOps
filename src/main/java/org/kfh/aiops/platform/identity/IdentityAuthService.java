package org.kfh.aiops.platform.identity;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.kfh.aiops.commandcenter.support.CommandCenterReadModel;
import org.kfh.aiops.platform.audit.AuditService;
import org.kfh.aiops.platform.exception.ForbiddenAccessException;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class IdentityAuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdentityAuthService.class);
    private static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000000");

    private final IdentityJdbcRepository identityJdbcRepository;
    private final BootstrapInMemoryAuthenticator bootstrapAuthenticator;
    private final IdentityBootstrapProperties bootstrapProperties;
    private final AuditService auditService;
    private final CommandCenterReadModel readModel;

    public IdentityAuthService(IdentityJdbcRepository identityJdbcRepository,
            BootstrapInMemoryAuthenticator bootstrapAuthenticator, IdentityBootstrapProperties bootstrapProperties,
            AuditService auditService, CommandCenterReadModel readModel) {
        this.identityJdbcRepository = identityJdbcRepository;
        this.bootstrapAuthenticator = bootstrapAuthenticator;
        this.bootstrapProperties = bootstrapProperties;
        this.auditService = auditService;
        this.readModel = readModel;
    }

    public IdentitySignInResponse signIn(IdentitySignInRequest request) {
        var bootstrap = bootstrapAuthenticator.authenticate(request);
        if (bootstrap.isPresent()) {
            LOGGER.info("sign-in accepted via bootstrap admin countryCode={} environment={}",
                    request.countryCode(), request.environment());
            var response = bootstrap.get();
            recordLoginSucceeded(request, response, "BOOTSTRAP");
            return response;
        }
        var response = identityJdbcRepository.signIn(request);
        if (response.isPresent()) {
            recordLoginSucceeded(request, response.get(), "DATABASE");
            return response.get();
        }
        var diagnostics = identityJdbcRepository.signInFailureDiagnostics(request);
        LOGGER.warn("sign-in rejected countryCode={} environment={} usernameMatches={} scopedMatches={} activeScopedMatches={} passwordReadyScopedMatches={}",
                request.countryCode(), request.environment(), diagnostics.usernameMatches(), diagnostics.scopedMatches(),
                diagnostics.activeScopedMatches(), diagnostics.passwordReadyScopedMatches());
        recordLoginFailed(request);
        throw new ForbiddenAccessException("Invalid username, password, country, or status");
    }

    private void recordLoginSucceeded(IdentitySignInRequest request, IdentitySignInResponse response, String source) {
        var ctx = new TenantContext(response.tenantId(), response.userId(), response.countryCode(), response.environment(),
                correlationId("auth-success"), response.permissions() == null ? Set.of() : Set.copyOf(response.permissions()));
        var details = Map.of(
                "actorName", response.username(),
                "result", "Success",
                "severity", "Info",
                "message", "User signed in successfully",
                "details", Map.of("username", response.username(), "countryCode", response.countryCode(),
                        "environment", response.environment(), "source", source, "requestedCountryCode", safe(request.countryCode())));
        auditService.recordWrite(ctx, "LOGIN_SUCCEEDED", "Security", response.userId().toString(), null,
                Map.of("username", response.username(), "source", source));
        readModel.appendAudit(ctx, "LOGIN_SUCCEEDED", "Security", response.userId().toString(), details);
    }

    private void recordLoginFailed(IdentitySignInRequest request) {
        var ctx = new TenantContext(bootstrapProperties.getTenantId(), SYSTEM_USER_ID, safe(request.countryCode()),
                safe(request.environment()), correlationId("auth-failed"), Set.of("AUDIT_READ"));
        var submittedUsername = safe(request.username());
        var details = Map.of(
                "actorName", submittedUsername.isBlank() ? "Unknown sign-in attempt" : submittedUsername,
                "result", "Fail",
                "severity", "Warn",
                "message", "User sign-in failed",
                "details", Map.of("username", submittedUsername, "countryCode", safe(request.countryCode()),
                        "environment", safe(request.environment()), "reason", "INVALID_CREDENTIALS_OR_SCOPE"));
        auditService.recordWrite(ctx, "LOGIN_FAILED", "Security", "LOGIN_ATTEMPT", null,
                Map.of("username", submittedUsername, "reason", "INVALID_CREDENTIALS_OR_SCOPE"));
        readModel.appendAudit(ctx, "LOGIN_FAILED", "Security", submittedUsername, details);
    }

    private static String correlationId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}

