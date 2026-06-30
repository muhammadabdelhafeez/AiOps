package org.kfh.aiops.platform.identity;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * In-memory bootstrap admin authenticator backed by {@link IdentityBootstrapProperties}.
 * Lets the configured bootstrap admin sign in even if the database is unavailable or
 * the bootstrap row has not been reconciled yet. Compares the supplied plaintext
 * password to the configured bootstrap password using a constant-time check and
 * never logs/returns the password.
 */
@Component
public class BootstrapInMemoryAuthenticator {

    private static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000000");
    private static final Map<String, RoleProfile> ROLE_PROFILES = Map.of(
            "GLOBAL_ADMIN", new RoleProfile("KFH Global Admin", List.of("*")),
            "COUNTRY_ADMIN", new RoleProfile("Country Admin",
                    List.of("DASHBOARD_READ", "INCIDENT_READ", "ALERT_READ", "IDENTITY_READ", "IDENTITY_WRITE", "AUDIT_READ")),
            "NOC_OPERATOR", new RoleProfile("NOC Operator",
                    List.of("DASHBOARD_READ", "INCIDENT_READ", "ALERT_READ", "IDENTITY_READ")),
            "VIEWER", new RoleProfile("Viewer",
                    List.of("DASHBOARD_READ", "INCIDENT_READ", "ALERT_READ")));

    private final IdentityBootstrapProperties properties;
    private final PasswordEncoder passwordEncoder;
    private volatile String cachedPasswordHash;
    private volatile String cachedRawPassword;

    public BootstrapInMemoryAuthenticator(IdentityBootstrapProperties properties, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<IdentitySignInResponse> authenticate(IdentitySignInRequest request) {
        var diagnostics = diagnostics(request);
        if (!diagnostics.accepted()) {
            return Optional.empty();
        }
        var role = roleProfile(properties.getRoleName());
        var responseCountryCode = resolveResponseCountryCode(request.countryCode());
        var responseEnvironment = resolveResponseEnvironment(request.environment());
        return Optional.of(new IdentitySignInResponse(
                properties.getTenantId(),
                SYSTEM_USER_ID,
                properties.getUsername(),
                properties.getDisplayName(),
                properties.getEmail(),
                responseCountryCode,
                countryName(responseCountryCode),
                properties.getTenantName(),
                responseEnvironment,
                properties.getRoleName(),
                role.displayName(),
                role.permissions(),
                Instant.now()));
    }

    public BootstrapDiagnostics diagnostics(IdentitySignInRequest request) {
        var enabled = properties.isEnabled();
        var passwordConfigured = properties.hasPassword();
        var usernameMatched = equalsIgnoreCase(request.username(), properties.getUsername());
        var countryMatched = countryMatches(request.countryCode());
        var environmentMatched = environmentMatches(request.environment());
        var passwordMatched = passwordConfigured && passwordMatches(request.password());
        var accepted = enabled && passwordConfigured && usernameMatched && countryMatched && environmentMatched && passwordMatched;
        return new BootstrapDiagnostics(enabled, passwordConfigured, usernameMatched, countryMatched,
                environmentMatched, passwordMatched, configuredScope(properties.getCountryCode()),
                configuredScope(properties.getEnvironment()), configuredScope(properties.getRoleName()), accepted);
    }

    /**
     * When the configured bootstrap country is {@code ALL}, the bootstrap admin behaves as a true global admin and may
     * sign in from any country selection on the login screen. Otherwise the configured country must match the request.
     */
    private boolean countryMatches(String requestedCountry) {
        if (isWildcard(properties.getCountryCode())) {
            return requestedCountry == null || !requestedCountry.isBlank();
        }
        return equalsNormalized(requestedCountry, properties.getCountryCode());
    }

    private boolean environmentMatches(String requestedEnvironment) {
        if (isWildcard(properties.getEnvironment())) {
            return requestedEnvironment == null || !requestedEnvironment.isBlank();
        }
        return equalsNormalized(requestedEnvironment, properties.getEnvironment());
    }

    private String resolveResponseCountryCode(String requestedCountry) {
        if (isWildcard(properties.getCountryCode())) {
            return "ALL";
        }
        return normalize(properties.getCountryCode());
    }

    private String resolveResponseEnvironment(String requestedEnvironment) {
        if (isWildcard(properties.getEnvironment())) {
            return normalize(requestedEnvironment);
        }
        return normalize(properties.getEnvironment());
    }

    private static boolean isWildcard(String value) {
        return value != null && "ALL".equalsIgnoreCase(value.trim());
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean passwordMatches(String submitted) {
        if (submitted == null) {
            return false;
        }
        var configured = properties.getPassword();
        if (configured == null || configured.isBlank()) {
            return false;
        }
        var hash = cachedPasswordHash;
        if (hash == null || !configured.equals(cachedRawPassword)) {
            hash = passwordEncoder.encode(configured);
            cachedPasswordHash = hash;
            cachedRawPassword = configured;
        }
        return passwordEncoder.matches(submitted, hash);
    }

    private static RoleProfile roleProfile(String roleName) {
        var key = roleName == null ? "VIEWER" : roleName.trim().toUpperCase(Locale.ROOT);
        return ROLE_PROFILES.getOrDefault(key, ROLE_PROFILES.get("VIEWER"));
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }

    private static boolean equalsNormalized(String left, String right) {
        return left != null && right != null
                && left.trim().toUpperCase(Locale.ROOT).equals(right.trim().toUpperCase(Locale.ROOT));
    }

    private static String countryName(String countryCode) {
        return switch (countryCode == null ? "" : countryCode.trim().toUpperCase(Locale.ROOT)) {
            case "ALL" -> "All countries";
            case "BH" -> "KFH Bahrain";
            case "EG" -> "KFH Egypt";
            case "KW" -> "KFH Kuwait";
            default -> "KFH Group";
        };
    }

    private record RoleProfile(String displayName, List<String> permissions) {
    }

    private static String configuredScope(String value) {
        return value == null || value.isBlank() ? "-" : value.trim().toUpperCase(Locale.ROOT);
    }

    public record BootstrapDiagnostics(boolean enabled, boolean passwordConfigured, boolean usernameMatched,
            boolean countryMatched, boolean environmentMatched, boolean passwordMatched, String configuredCountryScope,
            String configuredEnvironmentScope, String configuredRole, boolean accepted) {
    }
}

