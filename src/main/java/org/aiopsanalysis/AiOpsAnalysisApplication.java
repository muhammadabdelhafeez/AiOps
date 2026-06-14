package org.aiopsanalysis;

import org.kfh.aiops.AiOpsApplication;
import org.springframework.boot.SpringApplication;

/**
 * Backward-compatible launcher for existing IDE run configurations.
 *
 * <p>The canonical Spring Boot entry point is {@link AiOpsApplication}. This
 * class intentionally has no Spring annotations and no component scanning so
 * backend implementation remains under {@code org.kfh.aiops}.</p>
 */
public final class AiOpsAnalysisApplication {

    private AiOpsAnalysisApplication() {
    }

    public static void main(String[] args) {
        SpringApplication.run(AiOpsApplication.class, args);
    }
}
