package org.kfh.aiops.plugin.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.kfh.aiops.platform.tenant.TenantContext;

/** Persistent connector profile store used by connector services. */
public interface ConnectorPersistenceStore {

    List<Map<String, Object>> list(TenantContext ctx);

    Optional<Map<String, Object>> find(UUID id);

    Map<String, Object> create(TenantContext ctx, String countryCode, String environment,
            String name, String pluginType, boolean enabled, Map<String, Object> fields);

    Map<String, Object> update(UUID id, Map<String, Object> fields);

    default Optional<Map<String, String>> secrets(UUID id) {
        return Optional.empty();
    }

    default void recordTestResult(UUID id, boolean pass) {
        // Optional for non-durable test stores.
    }

    default void recordTestResult(UUID id, boolean pass, Map<String, Object> result) {
        recordTestResult(id, pass);
    }

    void delete(UUID id);
}

