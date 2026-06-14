package org.kfh.aiops.platform.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
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
    void shouldAcceptAnyCountryWhenBootstrapConfiguredAsAllCountriesAdmin() {
        var properties = properties(PASSWORD);
        properties.setCountryCode("ALL");
        var authenticator = new BootstrapInMemoryAuthenticator(properties, new BCryptPasswordEncoder());

        var responseKw = authenticator.authenticate(new IdentitySignInRequest("mymabdelhafeez", PASSWORD, "KW", "PROD"));
        var responseBh = authenticator.authenticate(new IdentitySignInRequest("mymabdelhafeez", PASSWORD, "BH", "PROD"));
        var responseAll = authenticator.authenticate(new IdentitySignInRequest("mymabdelhafeez", PASSWORD, "ALL", "PROD"));

        assertTrue(responseKw.isPresent());
        assertEquals("KW", responseKw.get().countryCode());
        assertTrue(responseBh.isPresent());
        assertEquals("BH", responseBh.get().countryCode());
        assertTrue(responseAll.isPresent());
        assertEquals("ALL", responseAll.get().countryCode());
        assertTrue(responseAll.get().permissions().contains("*"));
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
}

