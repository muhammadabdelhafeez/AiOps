package org.kfh.aiops.plugin.implementations.bmc;

import java.util.Map;
import org.kfh.aiops.platform.tenant.TenantContext;

/** Performs a live, secret-safe communication test for a saved BMC Helix connector. */
public interface BmcConnectorLiveTester {

    Map<String, Object> test(TenantContext ctx, Map<String, Object> connector, Map<String, String> secrets);
}

