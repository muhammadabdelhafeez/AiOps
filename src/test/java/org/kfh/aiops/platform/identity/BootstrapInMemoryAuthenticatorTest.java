package org.kfh.aiops.platform.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class BootstrapInMemoryAuthenticatorTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String PASSWORD = "Strong-Password-123!";

    @Test
    void shouldAuthenticateConfiguredBootstrapAdmin() {
        var authenticator = authenticator(PASSWORD);

        var response = authenticator.authenticate(
                new IdentitySignInRequest("mymabdelhafeez", PASSWORD, "KW", "PROD"));

        assertTrue(response.isPresent());
        assertEquals(TENANT_ID, response.get().tenantId());
        assertEquals("mymabdelhafeez", response.get().username());
        assertEquals("KW", response.get().countryCode());
        assertEquals("PROD", response.get().environment());
        assertEquals("GLOBAL_ADMIN", response.get().roleId());
        assertTrue(response.get().permissions().contains("*"));
    }

    @Test
    void shouldRejectWhenPasswordDoesNotMatch() {
        var authenticator = authenticator(PASSWORD);

        var response = authenticator.authenticate(
                new IdentitySignInRequest("mymabdelhafeez", "wrong", "KW", "PROD"));

        assertTrue(response.isEmpty());
    }

    @Test
    void shouldRejectWhenCountryDiffers() {
        var authenticator = authenticator(PASSWORD);

        var response = authenticator.authenticate(
                new IdentitySignInRequest("mymabdelhafeez", PASSWORD, "BH", "PROD"));

        assertTrue(response.isEmpty());
    }

    @Test
    void shouldRejectWhenBootstrapDisabled() {
        var properties = properties(PASSWORD);
        properties.setEnabled(false);
        var authenticator = new BootstrapInMemoryAuthenticator(properties, new BCryptPasswordEncoder());

        var response = authenticator.authenticate(
                new IdentitySignInRequest("mymabdelhafeez", PASSWORD, "KW", "PROD"));

        assertTrue(response.isEmpty());
    }

    @Test
    void shouldRejectWhenPasswordIsBlank() {
        var authenticator = authenticator("   ");

        var response = authenticator.authenticate(
                new IdentitySignInRequest("mymabdelhafeez", "anything", "KW", "PROD"));

        assertTrue(response.isEmpty());
    }

    @Test
    void shouldBeCaseInsensitiveForUsernameAndCountry() {
        var authenticator = authenticator(PASSWORD);

        var response = authenticator.authenticate(
                new IdentitySignInRequest("MYMABDELHAFEEZ", PASSWORD, "kw", "prod"));

        assertTrue(response.isPresent());
    }

    @Test
    void shouldReturnAllCountryScopeWhenBootstrapConfiguredAsAllCountriesAdmin() {
        var properties = properties(PASSWORD);
        properties.setCountryCode("ALL");
        var authenticator = new BootstrapInMemoryAuthenticator(properties, new BCryptPasswordEncoder());

        var responseKw = authenticator.authenticate(new IdentitySignInRequest("mymabdelhafeez", PASSWORD, "KW", "PROD"));
        var responseBh = authenticator.authenticate(new IdentitySignInRequest("mymabdelhafeez", PASSWORD, "BH", "PROD"));
        var responseAll = authenticator.authenticate(new IdentitySignInRequest("mymabdelhafeez", PASSWORD, "ALL", "PROD"));

        assertTrue(responseKw.isPresent());
        assertEquals("ALL", responseKw.get().countryCode());
        assertEquals("All countries", responseKw.get().countryName());
        assertTrue(responseBh.isPresent());
        assertEquals("ALL", responseBh.get().countryCode());
        assertTrue(responseAll.isPresent());
        assertEquals("ALL", responseAll.get().countryCode());
        assertTrue(responseAll.get().permissions().contains("*"));
    }

    @Test
    void shouldGrantAuditReadToCountryAdminWithoutGlobalCountryView() {
        var properties = properties(PASSWORD);
        properties.setRoleName("COUNTRY_ADMIN");
        var authenticator = new BootstrapInMemoryAuthenticator(properties, new BCryptPasswordEncoder());

        var response = authenticator.authenticate(new IdentitySignInRequest("mymabdelhafeez", PASSWORD, "KW", "PROD"));

        assertTrue(response.isPresent());
        assertEquals("COUNTRY_ADMIN", response.get().roleId());
        assertTrue(response.get().permissions().contains("AUDIT_READ"));
        assertFalse(response.get().permissions().contains("COUNTRY_GLOBAL_VIEW"));
    }

    @Test
    void shouldExposeSecretSafeDiagnosticsWhenBootstrapPasswordDiffers() {
        var properties = properties(PASSWORD);
        properties.setCountryCode("ALL");
        var authenticator = new BootstrapInMemoryAuthenticator(properties, new BCryptPasswordEncoder());

        var diagnostics = authenticator.diagnostics(new IdentitySignInRequest("mymabdelhafeez", "wrong", "KW", "PROD"));

        assertTrue(diagnostics.enabled());
        assertTrue(diagnostics.passwordConfigured());
        assertTrue(diagnostics.usernameMatched());
        assertTrue(diagnostics.countryMatched());
        assertTrue(diagnostics.environmentMatched());
        org.junit.jupiter.api.Assertions.assertFalse(diagnostics.passwordMatched());
        org.junit.jupiter.api.Assertions.assertFalse(diagnostics.accepted());
        assertEquals("ALL", diagnostics.configuredCountryScope());
        assertEquals("PROD", diagnostics.configuredEnvironmentScope());
        assertEquals("GLOBAL_ADMIN", diagnostics.configuredRole());
    }

    @Test
    void shouldAuthenticateConfiguredDevBootstrapAdminFromApplicationProperties() throws IOException {
        var configured = loadConfiguredBootstrapProperties();
        var authenticator = new BootstrapInMemoryAuthenticator(configured, new BCryptPasswordEncoder());

        var response = authenticator.authenticate(new IdentitySignInRequest(
                configured.getUsername(), configured.getPassword(), "KW", configured.getEnvironment()));

        assertTrue(response.isPresent());
        assertEquals("ALL", response.get().countryCode());
        assertEquals("All countries", response.get().countryName());
        assertEquals("GLOBAL_ADMIN", response.get().roleId());
        assertTrue(response.get().permissions().contains("*"));
    }

    private static BootstrapInMemoryAuthenticator authenticator(String password) {
        return new BootstrapInMemoryAuthenticator(properties(password), new BCryptPasswordEncoder());
    }

    private static IdentityBootstrapProperties properties(String password) {
        var properties = new IdentityBootstrapProperties();
        properties.setTenantId(TENANT_ID);
        properties.setTenantName("KFH Group");
        properties.setUsername("mymabdelhafeez");
        properties.setPassword(password);
        properties.setDisplayName("KFH Bootstrap Admin");
        properties.setCountryCode("KW");
        properties.setEnvironment("PROD");
        properties.setRoleName("GLOBAL_ADMIN");
        return properties;
    }

    private static IdentityBootstrapProperties loadConfiguredBootstrapProperties() throws IOException {
        var raw = new Properties();
        try (var input = BootstrapInMemoryAuthenticatorTest.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            raw.load(input);
        }
        var properties = new IdentityBootstrapProperties();
        properties.setTenantId(UUID.fromString(placeholderDefault(raw, "kfh.identity.bootstrap.tenant-id")));
        properties.setTenantName(placeholderDefault(raw, "kfh.identity.bootstrap.tenant-name"));
        properties.setUsername(placeholderDefault(raw, "kfh.identity.bootstrap.username"));
        properties.setPassword(placeholderDefault(raw, "kfh.identity.bootstrap.password"));
        properties.setDisplayName(placeholderDefault(raw, "kfh.identity.bootstrap.display-name"));
        properties.setEmail(placeholderDefault(raw, "kfh.identity.bootstrap.email"));
        properties.setCountryCode(placeholderDefault(raw, "kfh.identity.bootstrap.country-code"));
        properties.setEnvironment(placeholderDefault(raw, "kfh.identity.bootstrap.environment"));
        properties.setRoleName(placeholderDefault(raw, "kfh.identity.bootstrap.role-name"));
        return properties;
    }

    private static String placeholderDefault(Properties properties, String key) {
        var value = properties.getProperty(key);
        var matcher = Pattern.compile("^\\$\\{[^:}]+:([^}]*)}$").matcher(value == null ? "" : value);
        return matcher.matches() ? matcher.group(1) : value;
    }
}

