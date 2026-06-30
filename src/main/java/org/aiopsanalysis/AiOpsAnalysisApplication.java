package org.aiopsanalysis;

/**
 * Legacy IntelliJ launcher shim retained for old run configurations.
 *
 * <p>New run configurations should use {@code org.kfh.aiops.AiOpsApplication} directly.</p>
 */
public final class AiOpsAnalysisApplication {

    private AiOpsAnalysisApplication() {
    }

    public static void main(String[] args) {
        org.kfh.aiops.AiOpsApplication.main(args);
    }
}

