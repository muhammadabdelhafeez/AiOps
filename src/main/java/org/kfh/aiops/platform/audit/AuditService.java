package org.kfh.aiops.platform.audit;

import org.kfh.aiops.platform.tenant.TenantContext;

/** Port for secret-safe write audit logging. */
public interface AuditService {

    void recordWrite(TenantContext ctx, String action, String entityType,
            String entityId, Object beforeState, Object afterState);
}

