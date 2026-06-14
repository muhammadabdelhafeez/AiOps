package org.kfh.aiops.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;
import javax.sql.DataSource;

@ExtendWith(OutputCaptureExtension.class)
class IdentityStorageReadinessLoggerTest {

    @Test
    void shouldLogReadyWhenIdentityJdbcRepositoryPresent(CapturedOutput output) throws Exception {
        var env = new MockEnvironment();
        var dataSource = dataSource();

        new IdentityStorageReadinessLogger(Mockito.mock(IdentityJdbcRepository.class), dataSource, env).logReadiness();

        assertThat(output).contains("Identity storage ready");
    }

    private static DataSource dataSource() throws Exception {
        var dataSource = Mockito.mock(DataSource.class);
        var connection = Mockito.mock(Connection.class);
        var metadata = Mockito.mock(DatabaseMetaData.class);
        Mockito.when(dataSource.getConnection()).thenReturn(connection);
        Mockito.when(connection.getMetaData()).thenReturn(metadata);
        Mockito.when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        Mockito.when(metadata.getDatabaseProductVersion()).thenReturn("test");
        Mockito.when(metadata.getURL()).thenReturn("jdbc:postgresql://localhost:5432/Kfh_AiOps");
        return dataSource;
    }
}

