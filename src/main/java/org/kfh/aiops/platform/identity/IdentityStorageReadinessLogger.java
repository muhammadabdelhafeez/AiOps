package org.kfh.aiops.platform.identity;

import java.util.Arrays;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Emits one prominent startup log line confirming database-backed identity storage is available.
 */
@Component
public class IdentityStorageReadinessLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdentityStorageReadinessLogger.class);

    private final IdentityJdbcRepository identityJdbcRepository;
    private final DataSource dataSource;
    private final Environment environment;

    public IdentityStorageReadinessLogger(IdentityJdbcRepository identityJdbcRepository,
            DataSource dataSource,
            Environment environment) {
        this.identityJdbcRepository = identityJdbcRepository;
        this.dataSource = dataSource;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logReadiness() {
        var activeProfiles = Arrays.toString(environment.getActiveProfiles());
        var repositoryType = identityJdbcRepository.getClass().getSimpleName();
        var datasource = describeDataSource();
        LOGGER.info("Identity storage ready: database-backed login user create/update/toggle/delete enabled "
                + "(activeProfiles={}, repository={}, datasource={})", activeProfiles, repositoryType, datasource);
    }

    private String describeDataSource() {
        try (var connection = dataSource.getConnection()) {
            var metadata = connection.getMetaData();
            return String.format("%s %s url=%s",
                    metadata.getDatabaseProductName(),
                    metadata.getDatabaseProductVersion(),
                    metadata.getURL());
        } catch (java.sql.SQLException ex) {
            return "unavailable(" + ex.getMessage() + ")";
        }
    }
}

