package org.kfh.aiops.plugin.implementations.emco;

import java.util.Map;
import org.kfh.aiops.platform.tenant.TenantContext;

/** Live readiness tester for EMCO Ping Monitor SQL Server connector profiles. */
public interface EmcoConnectorLiveTester {

    Map<String, Object> test(TenantContext ctx, Map<String, Object> connector, Map<String, String> secrets);
}

