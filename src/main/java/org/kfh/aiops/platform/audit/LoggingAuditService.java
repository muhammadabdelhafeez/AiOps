package org.kfh.aiops.platform.audit;

import org.kfh.aiops.platform.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/** Secret-safe audit adapter backed by PostgreSQL identity.audit_log. */
@Service
public class LoggingAuditService implements AuditService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingAuditService.class);

    private final AuditActivityRepository auditActivityRepository;

    public LoggingAuditService(AuditActivityRepository auditActivityRepository) {
        this.auditActivityRepository = auditActivityRepository;
    }

    @Override
    public void recordWrite(TenantContext ctx, String action, String entityType,
            String entityId, Object beforeState, Object afterState) {
        try {
            auditActivityRepository.recordWrite(ctx, action, entityType, entityId, beforeState, afterState);
        } catch (DataAccessException ex) {
            LOGGER.warn("audit persistence write unavailable action={} entityType={} entityId={} tenantId={} countryCode={} correlationId={} errorType={}",
                    action, entityType, entityId, ctx.tenantId(), ctx.countryCode(), ctx.correlationId(), ex.getClass().getSimpleName());
        }
        LOGGER.info("audit action={} entityType={} entityId={} tenantId={} userId={} countryCode={} correlationId={}",
                action, entityType, entityId, ctx.tenantId(), ctx.userId(), ctx.countryCode(), ctx.correlationId());
    }
}

