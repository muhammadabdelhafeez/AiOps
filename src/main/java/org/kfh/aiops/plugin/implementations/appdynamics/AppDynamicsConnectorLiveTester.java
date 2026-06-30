package org.kfh.aiops.plugin.implementations.appdynamics;

import java.util.Map;
import org.kfh.aiops.platform.tenant.TenantContext;

/** Performs a secret-safe AppDynamics connector readiness test using saved encrypted credentials. */
public interface AppDynamicsConnectorLiveTester {
    Map<String, Object> test(TenantContext ctx, Map<String, Object> connector, Map<String, String> secrets);
}

