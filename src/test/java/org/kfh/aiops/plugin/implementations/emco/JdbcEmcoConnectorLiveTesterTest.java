package org.kfh.aiops.plugin.implementations.emco;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.platform.tenant.TenantContext;

class JdbcEmcoConnectorLiveTesterTest {

    @Test
    void shouldFailSafelyWhenEmcoCredentialsAreMissing() {
        var tester = new JdbcEmcoConnectorLiveTester(CircuitBreaker.ofDefaults("emcoMissingCredentialTest"),
                (jdbcUrl, properties) -> {
                    throw new AssertionError("SQL connection should not be opened when credentials are missing");
                });

        var result = tester.test(context(), connector(), Map.of());

        assertThat(result)
                .containsEntry("pass", false)
                .containsEntry("readyToCollect", false)
                .containsEntry("checkedEndpoint", "jdbc:sqlserver://emco-sql.example.com:11433;databaseName=EMCO_KFH_PROD,EMCO_CCTV_PROD;encrypt=true;trustServerCertificate=false");
        assertThat(result.toString()).doesNotContain("password", "emco-kfh-user", "emco-cctv-user");
    }

    @Test
    void shouldFailSafelyWithoutLeakingCredentialsWhenSqlServerRejectsLogin() {
        var tester = new JdbcEmcoConnectorLiveTester(CircuitBreaker.ofDefaults("emcoRejectedLoginTest"),
                JdbcEmcoConnectorLiveTesterTest::rejectLogin);

        var result = tester.test(context(), connector(), Map.of(
                "kfhUsername", "emco-kfh-user",
                "kfhPassword", "emco-kfh-password",
                "cctvUsername", "emco-cctv-user",
                "cctvPassword", "emco-cctv-password"));

        assertThat(result)
                .containsEntry("pass", false)
                .containsEntry("readyToCollect", false);
        assertThat(String.valueOf(result.get("message"))).contains("Login failed for user 'masked'");
        assertThat(result.toString()).doesNotContain("emco-kfh-user", "emco-kfh-password", "emco-cctv-user", "emco-cctv-password");
    }

    private static java.sql.Connection rejectLogin(String jdbcUrl, Properties properties) throws SQLException {
        throw new SQLException("Login failed for user 'emco-kfh-user'. Password=emco-kfh-password");
    }

    private static TenantContext context() {
        return new TenantContext(UUID.randomUUID(), UUID.randomUUID(), "KW", "PROD", "corr-emco-test", Set.of("CONNECTOR_TEST"));
    }

    private static Map<String, Object> connector() {
        return Map.ofEntries(
                Map.entry("id", UUID.randomUUID().toString()),
                Map.entry("pluginType", "EMCO"),
                Map.entry("sqlServer", "emco-sql.example.com"),
                Map.entry("sqlPort", 11433),
                Map.entry("kfhDatabase", "EMCO_KFH_PROD"),
                Map.entry("cctvDatabase", "EMCO_CCTV_PROD"),
                Map.entry("minutesBack", 60),
                Map.entry("connectionTimeoutSeconds", 30),
                Map.entry("queryTimeoutSeconds", 120),
                Map.entry("encrypt", true),
                Map.entry("trustServerCertificate", false));
    }
}

