package org.kfh.aiops.platform.audit;

import org.kfh.aiops.platform.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Temporary audit adapter until the JPA-backed identity.audit_log adapter is added. */
@Service
public class LoggingAuditService implements AuditService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingAuditService.class);

    @Override
    public void recordWrite(TenantContext ctx, String action, String entityType,
            String entityId, Object beforeState, Object afterState) {
        LOGGER.info("audit action={} entityType={} entityId={} tenantId={} userId={} countryCode={} correlationId={}",
                action, entityType, entityId, ctx.tenantId(), ctx.userId(), ctx.countryCode(), ctx.correlationId());
    }
}

