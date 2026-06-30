package org.kfh.aiops.plugin.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.platform.security.SecretCipherService;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcConnectorPersistenceStoreTest {

    @Test
    void shouldPreserveEncryptedSecretsWhenOnlySecretMetadataIsPersisted() {
        var jdbcTemplate = mock(JdbcTemplate.class);
        var store = new JdbcConnectorPersistenceStore(jdbcTemplate, new ObjectMapper(), mock(SecretCipherService.class));
        var tenantId = UUID.randomUUID();
        var persisted = persistedConnector(tenantId);
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<Map<String, Object>>>any(),
                any(), any(), any(), any(), any(), any())).thenReturn(List.of(persisted));

        store.create(ctx(tenantId), "KW", "PROD", "BMC Helix KW PROD", "BMC", true,
                Map.of("pluginType", "BMC", "secretsMask", "configured"));

        var sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), any(), any(), any());
        var secretUpsert = sqlCaptor.getValue();
        assertTrue(secretUpsert.contains("current_secrets.secret_enc || EXCLUDED.secret_enc"));
        assertFalse(secretUpsert.contains("secret_enc = EXCLUDED.secret_enc, updated_at = now()"));
    }

    @Test
    void shouldEncryptSubmittedCredentialPayloadWhenCreatingConnector() {
        var jdbcTemplate = mock(JdbcTemplate.class);
        var cipher = mock(SecretCipherService.class);
        var store = new JdbcConnectorPersistenceStore(jdbcTemplate, new ObjectMapper(), cipher);
        var tenantId = UUID.randomUUID();
        var persisted = persistedConnector(tenantId);
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<Map<String, Object>>>any(),
                any(), any(), any(), any(), any(), any())).thenReturn(List.of(persisted));
        when(cipher.encrypt("appd-user")).thenReturn(Map.of("ciphertext", "encrypted-user"));
        when(cipher.encrypt("appd-password")).thenReturn(Map.of("ciphertext", "encrypted-password"));

        store.create(ctx(tenantId), "KW", "PROD", "AppDynamics KW PROD", "APPDYNAMICS", true,
                Map.of("pluginType", "APPDYNAMICS",
                        "secretsPlain", Map.of("username", "appd-user", "password", "appd-password")));

        var sqlCaptor = ArgumentCaptor.forClass(String.class);
        var secretPayloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), any(), any(), secretPayloadCaptor.capture());
        assertTrue(sqlCaptor.getValue().contains("config.connector_secrets"));
        assertTrue(secretPayloadCaptor.getValue().contains("encrypted-user"));
        assertTrue(secretPayloadCaptor.getValue().contains("encrypted-password"));
        assertFalse(secretPayloadCaptor.getValue().contains("appd-password"));
        verify(cipher).encrypt("appd-user");
        verify(cipher).encrypt("appd-password");
    }

    @Test
    void shouldRotateSubmittedCredentialWithoutDecryptingExistingEncryptedPayload() {
        var jdbcTemplate = mock(JdbcTemplate.class);
        var cipher = mock(SecretCipherService.class);
        var store = new JdbcConnectorPersistenceStore(jdbcTemplate, new ObjectMapper(), cipher);
        var tenantId = UUID.randomUUID();
        var connectorId = UUID.randomUUID();
        var existing = new LinkedHashMap<>(persistedConnector(tenantId));
        existing.put("id", connectorId.toString());
        existing.put("connectorId", connectorId.toString());
        when(jdbcTemplate.query(contains("FROM config.connectors"), ArgumentMatchers.<RowMapper<Map<String, Object>>>any(),
                any())).thenReturn(List.of(existing));
        when(jdbcTemplate.query(contains("UPDATE config.connectors"), ArgumentMatchers.<RowMapper<Map<String, Object>>>any(),
                any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of(existing));
        when(jdbcTemplate.query(contains("SELECT secret_enc"), ArgumentMatchers.<RowMapper<Map<String, Object>>>any(),
                any())).thenReturn(List.of(Map.of("accessKey", Map.of("ciphertext", "old-encrypted-access-key"))));
        when(cipher.encrypt("rotated-access-secret")).thenReturn(Map.of("ciphertext", "new-encrypted-secret"));

        store.update(connectorId, Map.of("pluginType", "BMC", "baseUrl", "https://kfh-itom.onbmc.com",
                "secretsPlain", Map.of("accessSecretKey", "rotated-access-secret")));

        var secretPayloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(contains("config.connector_secrets"), any(), any(), secretPayloadCaptor.capture());
        assertTrue(secretPayloadCaptor.getValue().contains("old-encrypted-access-key"));
        assertTrue(secretPayloadCaptor.getValue().contains("new-encrypted-secret"));
        assertFalse(secretPayloadCaptor.getValue().contains("rotated-access-secret"));
        verify(cipher).encrypt("rotated-access-secret");
        verify(cipher, never()).decrypt(any());
    }

    private static TenantContext ctx(UUID tenantId) {
        return new TenantContext(tenantId, UUID.randomUUID(), "KW", "PROD", "test-corr", Set.of());
    }

    private static Map<String, Object> persistedConnector(UUID tenantId) {
        return Map.ofEntries(
                Map.entry("id", UUID.randomUUID().toString()),
                Map.entry("connectorId", UUID.randomUUID().toString()),
                Map.entry("tenantId", tenantId.toString()),
                Map.entry("type", "BMC"),
                Map.entry("pluginType", "BMC"),
                Map.entry("name", "BMC Helix KW PROD"),
                Map.entry("enabled", true),
                Map.entry("countryCode", "KW"),
                Map.entry("environment", "PROD"),
                Map.entry("secretsMask", "configured"));
    }
}

