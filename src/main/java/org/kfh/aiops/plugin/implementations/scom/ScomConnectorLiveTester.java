package org.kfh.aiops.plugin.implementations.scom;

import java.util.Map;
import org.kfh.aiops.platform.tenant.TenantContext;

/** Live readiness tester for Microsoft SCOM WinRM/PowerShell connector profiles. */
public interface ScomConnectorLiveTester {

    Map<String, Object> test(TenantContext ctx, Map<String, Object> connector, Map<String, String> secrets);
}
