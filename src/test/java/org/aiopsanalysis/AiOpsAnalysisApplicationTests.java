package org.aiopsanalysis;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:aiops_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS identity\\;CREATE SCHEMA IF NOT EXISTS incident\\;CREATE SCHEMA IF NOT EXISTS cmdb\\;CREATE SCHEMA IF NOT EXISTS config\\;CREATE SCHEMA IF NOT EXISTS ops",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.batch.jdbc.initialize-schema=never",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration"
})
class AiOpsAnalysisApplicationTests {

    @Test
    void contextLoads() {
    }

}
