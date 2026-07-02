package org.kfh.aiops.rca;

import java.time.Instant;

/**
 * One alert that contributed to a correlated incident, tagged with the topology component it resolved
 * to. Kept small (no raw payload) so a compact EvidencePack can be built from it later.
 */
public record CorrelatedEvidence(String resourceId, String componentId, String componentName,
                                 String severity, String source, Instant timestamp, String message) {
}
