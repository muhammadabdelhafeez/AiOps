package org.kfh.aiops.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcSettingsMetadataStoreTest {

    @Container
    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("aiops_settings_test")
            .withUsername("test")
            .withPassword("test");

    private JdbcTemplate jdbcTemplate;
    private JdbcSettingsMetadataStore store;
    private TenantContext kwContext;
    private TenantContext bhContext;
    private TenantContext egContext;

    @BeforeEach
    void setUp() {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            org.junit.jupiter.api.Assumptions.abort("Docker is required for PostgreSQL-backed settings metadata integration tests");
        }
        var dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        jdbcTemplate = new JdbcTemplate(dataSource);
        store = new JdbcSettingsMetadataStore(jdbcTemplate, new ObjectMapper());
        kwContext = context("KW");
        bhContext = context("BH");
        egContext = context("EG");
    }

    @Test
    void shouldPersistAzureOpenAiProviderForSelectedCountriesAndReloadAfterRestart() {
        store.save(kwContext, Map.of("azureOpenAI", Map.of("integrations", List.of(Map.of(
                "id", "aoai-kw-bh",
                "name", "KW BH GPT",
                "provider", "AZURE_OPENAI",
                "purpose", "GPT",
                "countryCodes", List.of("KW", "BH"),
                "endpoint", "https://kwbh.openai.azure.com",
                "deployment", "gpt-kwbh")))));

        var persistedScopes = jdbcTemplate.queryForList(
                "select country_code from config.integration_settings where tenant_id = ? and key = ? order by country_code",
                String.class, kwContext.tenantId(), "azureOpenAI");
        assertThat(persistedScopes).containsExactly("BH", "KW");

        var restartedStore = new JdbcSettingsMetadataStore(jdbcTemplate, new ObjectMapper());
        assertThat(restartedStore.load(kwContext).toString()).contains("KW BH GPT", "https://kwbh.openai.azure.com");
        assertThat(restartedStore.load(bhContext).toString()).contains("KW BH GPT", "https://kwbh.openai.azure.com");
        assertThat(restartedStore.load(egContext).toString()).doesNotContain("KW BH GPT", "https://kwbh.openai.azure.com");
    }

    @Test
    void shouldPersistAllCountryAzureOpenAiProviderOnceAndExposeItToEveryCountry() {
        store.save(kwContext, Map.of("azureOpenAI", Map.of("integrations", List.of(Map.of(
                "id", "aoai-global",
                "name", "Global GPT",
                "provider", "AZURE_OPENAI",
                "purpose", "GPT",
                "countryCodes", List.of("ALL"),
                "endpoint", "https://global.openai.azure.com",
                "deployment", "gpt-global")))));

        var persistedScopes = jdbcTemplate.queryForList(
                "select country_code from config.integration_settings where tenant_id = ? and key = ? order by country_code",
                String.class, kwContext.tenantId(), "azureOpenAI");
        assertThat(persistedScopes).containsExactly("ALL");

        var restartedStore = new JdbcSettingsMetadataStore(jdbcTemplate, new ObjectMapper());
        assertThat(restartedStore.load(kwContext).toString()).contains("Global GPT");
        assertThat(restartedStore.load(bhContext).toString()).contains("Global GPT");
        assertThat(restartedStore.load(egContext).toString()).contains("Global GPT");
    }

    private static TenantContext context(String countryCode) {
        return new TenantContext(
                UUID.fromString("00000000-0000-4000-8000-000000000001"),
                UUID.fromString("00000000-0000-4000-8000-000000000101"),
                countryCode,
                "PROD",
                "jdbc-settings-store-test",
                Set.of("SETTINGS_READ", "SETTINGS_WRITE"));
    }
}
