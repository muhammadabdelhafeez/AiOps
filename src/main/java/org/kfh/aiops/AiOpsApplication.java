package org.kfh.aiops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the KFH Causal AIOps Platform modular monolith.
 */
@SpringBootApplication
public class AiOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiOpsApplication.class, args);
    }
}

