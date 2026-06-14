package org.kfh.aiops.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.mockito.Mockito;

class UserControllerStorageStatusTest {

    @Test
    void shouldReportDatabaseBackedWhenJdbcRepositoryPresent() {
        var service = Mockito.mock(IdentityAdminService.class);
        var controller = new UserController(service);

        var status = controller.storageStatus(ctx());

        assertThat(status).containsEntry("databaseBacked", true);
        assertThat(status).containsEntry("writesEnabled", true);
        assertThat(status).containsEntry("reason", "OK");
    }

    private static TenantContext ctx() {
        return new TenantContext(UUID.randomUUID(), UUID.randomUUID(), "KW", "PROD", "test-corr", Set.of("IDENTITY_READ"));
    }

}

