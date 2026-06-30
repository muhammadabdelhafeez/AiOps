package org.kfh.aiops.ai.azureopenai;

import java.util.Map;
import org.kfh.aiops.platform.tenant.TenantContext;

/**
 * Tests Azure OpenAI connection settings using a compact, secret-safe request payload.
 */
public interface AzureOpenAiConnectionTester {

    Map<String, Object> test(TenantContext ctx, Map<String, Object> request);
}
