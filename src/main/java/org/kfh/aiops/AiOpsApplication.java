package org.kfh.aiops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the KFH Causal AIOps Platform modular monolith.
 *
 * <p>{@code @EnableScheduling} activates all {@code @Scheduled} jobs — the ingestion pollers
 * (BMC/SCOM, gated by {@code kfh.ingestion.*.enabled}) and the index-retention purge. Without it those
 * jobs never fire.
 */
@SpringBootApplication
@EnableScheduling
public class AiOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiOpsApplication.class, args);
    }
}

