package org.kfh.aiops.platform.config;

import java.util.Map;
import org.kfh.aiops.platform.tenant.TenantContext;

/**
 * Tests Neo4j connectivity for Settings-managed topology graph metadata.
 */
public interface Neo4jConnectionTester {

    Map<String, Object> test(TenantContext ctx, String section, Map<String, Object> request);
}

