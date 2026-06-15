package org.kfh.aiops.platform.audit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.dao.DataAccessResourceFailureException;

class LoggingAuditServiceTest {

    @Test
    void shouldNotFailWriteActionWhenAuditPersistenceIsUnavailable() {
        var repository = mock(AuditActivityRepository.class);
        var service = new LoggingAuditService(repository);
        var ctx = new TenantContext(UUID.randomUUID(), UUID.randomUUID(), "KW", "PROD", "corr-audit-degraded", Set.of("IDENTITY_WRITE"));
        doThrow(new DataAccessResourceFailureException("audit table unavailable"))
                .when(repository).recordWrite(ctx, "USER_UPDATED", "Identity", "user-1", null, Map.of("field", "value"));

        assertThatCode(() -> service.recordWrite(ctx, "USER_UPDATED", "Identity", "user-1", null, Map.of("field", "value")))
                .doesNotThrowAnyException();
    }
}

