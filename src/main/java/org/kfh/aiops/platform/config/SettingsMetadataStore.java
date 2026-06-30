package org.kfh.aiops.platform.config;

import java.util.Map;
import org.kfh.aiops.platform.tenant.TenantContext;

/**
 * Tenant-scoped persistence for post-startup integration metadata configured from Settings.
 */
public interface SettingsMetadataStore {

    Map<String, Object> load(TenantContext ctx);

    void save(TenantContext ctx, Map<String, Object> settings);
}
