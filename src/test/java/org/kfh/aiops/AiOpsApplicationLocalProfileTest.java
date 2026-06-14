package org.kfh.aiops;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

class AiOpsApplicationLocalProfileTest {

    @Test
    void shouldNotDisableDatabaseIdentityInfrastructureInLocalProfile() throws Exception {
        Properties properties = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-local.properties"));

        assertThat(properties.stringPropertyNames())
                .noneMatch(name -> name.startsWith("spring.autoconfigure.exclude"));
    }
}

