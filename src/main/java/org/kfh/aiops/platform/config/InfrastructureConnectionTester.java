package org.kfh.aiops.platform.config;

import java.util.Map;
import org.kfh.aiops.platform.tenant.TenantContext;

/**
 * Bounded, secret-safe live tester for Settings infrastructure metadata rows.
 */
public interface InfrastructureConnectionTester {

    Map<String, Object> test(TenantContext ctx, String section, Map<String, Object> request);
}

