package org.kfh.aiops.plugin.implementations.vrops;

import java.util.Map;
import org.kfh.aiops.platform.tenant.TenantContext;

/** Performs a secret-safe VMware vROps / Aria Operations readiness test using saved encrypted credentials. */
public interface VropsConnectorLiveTester {
    Map<String, Object> test(TenantContext ctx, Map<String, Object> connector, Map<String, String> secrets);
}

