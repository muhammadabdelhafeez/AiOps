package org.kfh.aiops.platform.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.kfh.aiops.platform.exception.ConflictException;
import org.kfh.aiops.platform.exception.NotFoundException;
import org.kfh.aiops.platform.exception.ValidationException;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class IdentityJdbcRepository {

    private static final Map<String, RoleDefinition> DEFAULT_ROLES = Map.of(
            "GLOBAL_ADMIN", new RoleDefinition("KFH Global Admin", List.of("*")),
            "COUNTRY_ADMIN", new RoleDefinition("Country Admin", List.of("DASHBOARD_READ", "INCIDENT_READ", "ALERT_READ", "IDENTITY_READ", "IDENTITY_WRITE")),
            "NOC_OPERATOR", new RoleDefinition("NOC Operator", List.of("DASHBOARD_READ", "INCIDENT_READ", "ALERT_READ", "IDENTITY_READ")),
            "VIEWER", new RoleDefinition("Viewer", List.of("DASHBOARD_READ", "INCIDENT_READ", "ALERT_READ")));

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public IdentityJdbcRepository(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    public void ensureTenant(UUID tenantId, String tenantName) {
        if (tenantExists(tenantId)) {
            return;
        }
        var name = tenantDisplayName(tenantId, tenantName, false);
        try {
            insertTenant(tenantId, name);
        } catch (DuplicateKeyException ex) {
            insertTenant(tenantId, tenantDisplayName(tenantId, tenantName, true));
        }
    }

    private boolean tenantExists(UUID tenantId) {
        var count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.tenants WHERE tenant_id = ?",
                Integer.class, tenantId);
        return count != null && count > 0;
    }

    private void insertTenant(UUID tenantId, String tenantName) {
        jdbcTemplate.update("""
                INSERT INTO public.tenants(tenant_id, name)
                VALUES (?, ?)
                ON CONFLICT (tenant_id) DO NOTHING
                """, tenantId, tenantName);
    }

    private static String tenantDisplayName(UUID tenantId, String tenantName, boolean forceUniqueSuffix) {
        var baseName = blankToNull(tenantName) == null ? "KFH Group" : tenantName.trim();
        return forceUniqueSuffix ? baseName + " [" + tenantId + "]" : baseName;
    }

    public boolean userExists(UUID tenantId, String username, String countryCode, String environment) {
        var count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM identity.users
                WHERE tenant_id = ?
                  AND lower(username) = lower(?)
                  AND country_code = ?
                  AND environment = ?
                """, Integer.class, tenantId, username, normalize(countryCode), normalize(environment));
        return count != null && count > 0;
    }

    @Transactional
    public BootstrapUserProvisionResult provisionBootstrapUser(TenantContext ctx, String name, Map<String, Object> fields) {
        ensureDefaultRoles(ctx.tenantId());
        var username = required(fields, "username");
        var password = required(fields, "password");
        var existing = bootstrapUser(ctx.tenantId(), username, ctx.countryCode(), ctx.environment());
        if (existing.isEmpty()) {
            return new BootstrapUserProvisionResult(createUser(ctx, name, fields), true, true);
        }

        var user = existing.getFirst();
        var passwordMatches = user.passwordHash() != null && passwordEncoder.matches(password, user.passwordHash());
        var changed = false;
        if (!passwordMatches || !user.active()) {
            jdbcTemplate.update("""
                    UPDATE identity.users
                    SET display_name = ?,
                        email = ?,
                        password_hash = ?,
                        is_active = true
                    WHERE tenant_id = ? AND user_id = ?
                    """, name, string(fields.get("email")), passwordEncoder.encode(password), ctx.tenantId(), user.userId());
            changed = true;
        }
        changed = assignRole(ctx.tenantId(), user.userId(), firstRoleToken(fields)) || changed;
        var row = findUser(ctx.tenantId(), user.userId()).orElseThrow(() -> new NotFoundException("Bootstrap user not found"));
        return new BootstrapUserProvisionResult(row, changed, false);
    }

    public List<Map<String, Object>> users(UUID tenantId, String countryCode, String environment) {
        return jdbcTemplate.query("""
                SELECT u.user_id, u.tenant_id, u.username, u.display_name, u.email, u.country_code, u.environment,
                       u.is_active, u.created_at, u.updated_at, u.last_login_at,
                       COALESCE(array_agg(r.name ORDER BY r.name) FILTER (WHERE r.name IS NOT NULL), ARRAY[]::text[]) AS roles,
                       COALESCE(array_agg(r.role_id::text ORDER BY r.name) FILTER (WHERE r.role_id IS NOT NULL), ARRAY[]::text[]) AS role_ids
                FROM identity.users u
                LEFT JOIN identity.user_roles ur ON ur.tenant_id = u.tenant_id AND ur.user_id = u.user_id
                LEFT JOIN identity.roles r ON r.tenant_id = ur.tenant_id AND r.role_id = ur.role_id
                WHERE u.tenant_id = ? AND u.country_code = ? AND u.environment = ?
                GROUP BY u.user_id
                ORDER BY u.updated_at DESC
                """, (rs, rowNum) -> userRow(rs), tenantId, normalize(countryCode), normalize(environment));
    }

    public Optional<Map<String, Object>> findUser(UUID tenantId, UUID userId) {
        var rows = jdbcTemplate.query("""
                SELECT u.user_id, u.tenant_id, u.username, u.display_name, u.email, u.country_code, u.environment,
                       u.is_active, u.created_at, u.updated_at, u.last_login_at,
                       COALESCE(array_agg(r.name ORDER BY r.name) FILTER (WHERE r.name IS NOT NULL), ARRAY[]::text[]) AS roles,
                       COALESCE(array_agg(r.role_id::text ORDER BY r.name) FILTER (WHERE r.role_id IS NOT NULL), ARRAY[]::text[]) AS role_ids
                FROM identity.users u
                LEFT JOIN identity.user_roles ur ON ur.tenant_id = u.tenant_id AND ur.user_id = u.user_id
                LEFT JOIN identity.roles r ON r.tenant_id = ur.tenant_id AND r.role_id = ur.role_id
                WHERE u.tenant_id = ? AND u.user_id = ?
                GROUP BY u.user_id
                """, (rs, rowNum) -> userRow(rs), tenantId, userId);
        return rows.stream().findFirst();
    }

    @Transactional
    public Map<String, Object> createUser(TenantContext ctx, String name, Map<String, Object> fields) {
        ensureTenant(ctx.tenantId(), "KFH Group");
        ensureDefaultRoles(ctx.tenantId());
        var username = required(fields, "username");
        var email = string(fields.get("email"));
        var password = required(fields, "password");
        if (userExists(ctx.tenantId(), username, ctx.countryCode(), ctx.environment())) {
            throw new ConflictException("A login user with this username already exists for the selected country");
        }
        var status = string(fields.getOrDefault("status", "Active"));
        var active = !"DISABLED".equalsIgnoreCase(status) && !"FALSE".equalsIgnoreCase(status);
        var userId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO identity.users(user_id, tenant_id, username, display_name, email, country_code, environment, password_hash, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, ctx.tenantId(), username, name, email, normalize(ctx.countryCode()), normalize(ctx.environment()),
                passwordEncoder.encode(password), active);
        assignRole(ctx.tenantId(), userId, firstRoleToken(fields));
        return findUser(ctx.tenantId(), userId).orElseThrow(() -> new NotFoundException("Created user not found"));
    }

    @Transactional
    public Map<String, Object> updateUser(UUID tenantId, UUID userId, String name, Map<String, Object> fields) {
        var username = string(fields.get("username"));
        var email = string(fields.get("email"));
        var status = string(fields.get("status"));
        var countryCode = optionalNormalized(fields.get("countryCode"));
        var passwordHash = optionalPasswordHash(fields);
        Boolean active = status == null ? null : (!"DISABLED".equalsIgnoreCase(status) && !"FALSE".equalsIgnoreCase(status));
        jdbcTemplate.update("""
                UPDATE identity.users
                SET username = COALESCE(?, username),
                    display_name = COALESCE(?, display_name),
                    email = COALESCE(?, email),
                    country_code = COALESCE(?, country_code),
                    is_active = COALESCE(?, is_active),
                    password_hash = COALESCE(?, password_hash)
                WHERE tenant_id = ? AND user_id = ?
                """, blankToNull(username), blankToNull(name), blankToNull(email), countryCode, active, passwordHash, tenantId, userId);
        var roleToken = firstRoleToken(fields);
        if (roleToken != null && !roleToken.isBlank()) {
            jdbcTemplate.update("DELETE FROM identity.user_roles WHERE tenant_id = ? AND user_id = ?", tenantId, userId);
            assignRole(tenantId, userId, roleToken);
        }
        return findUser(tenantId, userId).orElseThrow(() -> new NotFoundException("User not found"));
    }

    @Transactional
    public Map<String, Object> updatePassword(UUID tenantId, UUID userId, Map<String, Object> fields) {
        var password = required(fields, "password");
        jdbcTemplate.update("""
                UPDATE identity.users
                SET password_hash = ?
                WHERE tenant_id = ? AND user_id = ?
                """, passwordEncoder.encode(password), tenantId, userId);
        return findUser(tenantId, userId).orElseThrow(() -> new NotFoundException("User not found"));
    }

    @Transactional
    public Map<String, Object> toggleUser(UUID tenantId, UUID userId) {
        jdbcTemplate.update("UPDATE identity.users SET is_active = NOT is_active WHERE tenant_id = ? AND user_id = ?", tenantId, userId);
        return findUser(tenantId, userId).orElseThrow(() -> new NotFoundException("User not found"));
    }

    @Transactional
    public void deleteUser(UUID tenantId, UUID userId) {
        jdbcTemplate.update("DELETE FROM identity.users WHERE tenant_id = ? AND user_id = ?", tenantId, userId);
    }

    public List<Map<String, Object>> roles(UUID tenantId) {
        ensureTenant(tenantId, "KFH Group");
        ensureDefaultRoles(tenantId);
        return jdbcTemplate.query("""
                SELECT r.role_id, r.name, r.description,
                       COALESCE(array_agg(rp.permission ORDER BY rp.permission) FILTER (WHERE rp.permission IS NOT NULL), ARRAY[]::text[]) AS permissions
                FROM identity.roles r
                LEFT JOIN identity.role_permissions rp ON rp.tenant_id = r.tenant_id AND rp.role_id = r.role_id
                WHERE r.tenant_id = ?
                GROUP BY r.role_id
                ORDER BY r.name
                """, (rs, rowNum) -> roleRow(rs), tenantId);
    }

    public Optional<IdentitySignInResponse> signIn(IdentitySignInRequest request) {
        var rows = jdbcTemplate.query("""
                SELECT u.user_id, u.tenant_id, u.username, u.display_name, u.email, u.country_code, u.environment,
                       u.password_hash, u.is_active,
                       COALESCE((array_agg(r.role_id::text ORDER BY r.name) FILTER (WHERE r.role_id IS NOT NULL))[1], '') AS role_id,
                       COALESCE((array_agg(r.name ORDER BY r.name) FILTER (WHERE r.name IS NOT NULL))[1], 'VIEWER') AS role_name,
                       COALESCE(array_agg(DISTINCT rp.permission ORDER BY rp.permission) FILTER (WHERE rp.permission IS NOT NULL), ARRAY[]::text[]) AS permissions
                FROM identity.users u
                LEFT JOIN identity.user_roles ur ON ur.tenant_id = u.tenant_id AND ur.user_id = u.user_id
                LEFT JOIN identity.roles r ON r.tenant_id = ur.tenant_id AND r.role_id = ur.role_id
                LEFT JOIN identity.role_permissions rp ON rp.tenant_id = r.tenant_id AND rp.role_id = r.role_id
                WHERE lower(u.username) = lower(?) AND u.country_code = ? AND u.environment = ?
                GROUP BY u.user_id
                """, (rs, rowNum) -> signInCandidate(rs), request.username(), normalize(request.countryCode()), normalize(request.environment()));
        return rows.stream()
                .filter(candidate -> candidate.active() && candidate.passwordHash() != null)
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.passwordHash()))
                .findFirst()
                .map(candidate -> {
                    jdbcTemplate.update("UPDATE identity.users SET last_login_at = now() WHERE tenant_id = ? AND user_id = ?",
                            candidate.tenantId(), candidate.userId());
                    return new IdentitySignInResponse(candidate.tenantId(), candidate.userId(), candidate.username(), candidate.displayName(),
                            candidate.email(), candidate.countryCode(), countryName(candidate.countryCode()), "KFH Group", candidate.environment(),
                            candidate.roleId(), roleDisplayName(candidate.roleName()), candidate.permissions(), Instant.now());
                });
    }

    public SignInFailureDiagnostics signInFailureDiagnostics(IdentitySignInRequest request) {
        return jdbcTemplate.queryForObject("""
                SELECT
                    COUNT(*) FILTER (WHERE lower(username) = lower(?)) AS username_matches,
                    COUNT(*) FILTER (WHERE lower(username) = lower(?) AND country_code = ? AND environment = ?) AS scoped_matches,
                    COUNT(*) FILTER (WHERE lower(username) = lower(?) AND country_code = ? AND environment = ? AND is_active) AS active_scoped_matches,
                    COUNT(*) FILTER (WHERE lower(username) = lower(?) AND country_code = ? AND environment = ? AND password_hash IS NOT NULL) AS password_ready_scoped_matches
                FROM identity.users
                """, (rs, rowNum) -> new SignInFailureDiagnostics(
                        rs.getInt("username_matches"), rs.getInt("scoped_matches"),
                        rs.getInt("active_scoped_matches"), rs.getInt("password_ready_scoped_matches")),
                request.username(),
                request.username(), normalize(request.countryCode()), normalize(request.environment()),
                request.username(), normalize(request.countryCode()), normalize(request.environment()),
                request.username(), normalize(request.countryCode()), normalize(request.environment()));
    }

    @Transactional
    public void ensureDefaultRoles(UUID tenantId) {
        DEFAULT_ROLES.forEach((name, definition) -> {
            var roleId = findOrCreateRole(tenantId, name, definition.description());
            for (var permission : definition.permissions()) {
                jdbcTemplate.update("""
                        INSERT INTO identity.role_permissions(tenant_id, role_id, permission)
                        VALUES (?, ?, ?) ON CONFLICT DO NOTHING
                        """, tenantId, roleId, permission);
            }
        });
    }

    private UUID findOrCreateRole(UUID tenantId, String name, String description) {
        var existing = jdbcTemplate.query("SELECT role_id FROM identity.roles WHERE tenant_id = ? AND name = ?",
                (rs, rowNum) -> UUID.fromString(rs.getString("role_id")), tenantId, name);
        if (!existing.isEmpty()) {
            return existing.getFirst();
        }
        var roleId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO identity.roles(role_id, tenant_id, name, description) VALUES (?, ?, ?, ?)", roleId, tenantId, name, description);
        return roleId;
    }

    private boolean assignRole(UUID tenantId, UUID userId, String roleToken) {
        var token = roleToken == null || roleToken.isBlank() ? "VIEWER" : roleToken;
        var roleIds = jdbcTemplate.query("""
                SELECT role_id FROM identity.roles
                WHERE tenant_id = ? AND (role_id::text = ? OR name = ?)
                """, (rs, rowNum) -> UUID.fromString(rs.getString("role_id")), tenantId, token, token);
        var roleId = roleIds.isEmpty() ? findOrCreateRole(tenantId, "VIEWER", DEFAULT_ROLES.get("VIEWER").description()) : roleIds.getFirst();
        return jdbcTemplate.update("INSERT INTO identity.user_roles(tenant_id, user_id, role_id) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", tenantId, userId, roleId) > 0;
    }

    private List<BootstrapUserRow> bootstrapUser(UUID tenantId, String username, String countryCode, String environment) {
        return jdbcTemplate.query("""
                SELECT user_id, password_hash, is_active
                FROM identity.users
                WHERE tenant_id = ?
                  AND lower(username) = lower(?)
                  AND country_code = ?
                  AND environment = ?
                """, (rs, rowNum) -> new BootstrapUserRow(UUID.fromString(rs.getString("user_id")),
                        rs.getString("password_hash"), rs.getBoolean("is_active")),
                tenantId, username, normalize(countryCode), normalize(environment));
    }

    @SuppressWarnings("unchecked")
    private String firstRoleToken(Map<String, Object> fields) {
        var roleIds = fields.get("roleIds");
        if (roleIds instanceof List<?> list && !list.isEmpty()) {
            return String.valueOf(list.getFirst());
        }
        var roles = fields.get("roles");
        if (roles instanceof List<?> list && !list.isEmpty()) {
            return String.valueOf(list.getFirst());
        }
        return string(fields.get("roleId"));
    }

    private static Map<String, Object> userRow(ResultSet rs) throws SQLException {
        var row = new LinkedHashMap<String, Object>();
        row.put("id", rs.getString("user_id"));
        row.put("tenantId", rs.getString("tenant_id"));
        row.put("username", rs.getString("username"));
        row.put("displayName", rs.getString("display_name"));
        row.put("name", rs.getString("display_name"));
        row.put("email", rs.getString("email"));
        row.put("countryCode", rs.getString("country_code"));
        row.put("environment", rs.getString("environment"));
        row.put("status", rs.getBoolean("is_active") ? "ACTIVE" : "DISABLED");
        row.put("createdAt", instant(rs, "created_at"));
        row.put("updatedAt", instant(rs, "updated_at"));
        row.put("lastLoginAt", instant(rs, "last_login_at"));
        row.put("roles", textArray(rs, "roles"));
        row.put("roleIds", textArray(rs, "role_ids"));
        return row;
    }

    private static Map<String, Object> roleRow(ResultSet rs) throws SQLException {
        var row = new LinkedHashMap<String, Object>();
        row.put("id", rs.getString("role_id"));
        row.put("name", rs.getString("name"));
        row.put("description", rs.getString("description"));
        row.put("permissions", textArray(rs, "permissions"));
        return row;
    }

    private static SignInCandidate signInCandidate(ResultSet rs) throws SQLException {
        return new SignInCandidate(UUID.fromString(rs.getString("tenant_id")), UUID.fromString(rs.getString("user_id")),
                rs.getString("username"), rs.getString("display_name"), rs.getString("email"), rs.getString("country_code"),
                rs.getString("environment"), rs.getString("password_hash"), rs.getBoolean("is_active"), rs.getString("role_id"),
                rs.getString("role_name"), textArray(rs, "permissions"));
    }

    private static List<String> textArray(ResultSet rs, String column) throws SQLException {
        var array = rs.getArray(column);
        if (array == null) {
            return List.of();
        }
        var raw = (String[]) array.getArray();
        return List.of(raw);
    }

    private static String instant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant().toString();
    }

    private static String required(Map<String, Object> fields, String key) {
        var value = string(fields.get(key));
        if (value == null || value.isBlank()) {
            throw new ValidationException(key + " is required");
        }
        return value;
    }

    private String optionalPasswordHash(Map<String, Object> fields) {
        var password = string(fields.get("password"));
        return password == null || password.isBlank() ? null : passwordEncoder.encode(password);
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String optionalNormalized(Object value) {
        var raw = string(value);
        return raw == null || raw.isBlank() ? null : normalize(raw);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "PROD" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String roleDisplayName(String roleName) {
        var definition = DEFAULT_ROLES.get(roleName);
        return definition == null ? roleName : definition.description();
    }

    private static String countryName(String countryCode) {
        return switch (normalize(countryCode)) {
            case "ALL" -> "All countries";
            case "BH" -> "KFH Bahrain";
            case "EG" -> "KFH Egypt";
            default -> "KFH Kuwait";
        };
    }

    private record RoleDefinition(String description, List<String> permissions) {
    }

    private record SignInCandidate(UUID tenantId, UUID userId, String username, String displayName, String email,
            String countryCode, String environment, String passwordHash, boolean active, String roleId, String roleName,
            List<String> permissions) {
    }

    private record BootstrapUserRow(UUID userId, String passwordHash, boolean active) {
    }

    public record BootstrapUserProvisionResult(Map<String, Object> user, boolean changed, boolean created) {
    }

    public record SignInFailureDiagnostics(int usernameMatches, int scopedMatches,
            int activeScopedMatches, int passwordReadyScopedMatches) {
    }
}


