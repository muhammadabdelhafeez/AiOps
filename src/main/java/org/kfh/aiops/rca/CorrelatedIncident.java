package org.kfh.aiops.rca;

import java.time.Instant;
import java.util.List;

/**
 * A correlated incident: one candidate root-cause component/asset, the applications it impacts, and the
 * contributing alerts (evidence). {@code incidentKey} = {@code country|env|rootComponent} is the
 * continuity key so the same ongoing fault maps to the same incident across cycles.
 */
public record CorrelatedIncident(
        String incidentKey,
        String title,
        String severity,
        Instant started,
        String rootCauseComponentId,
        String rootCauseComponentName,
        String rootCauseAssetCi,
        List<String> impactedApplications,
        int alertCount,
        List<CorrelatedEvidence> evidence) {

    public CorrelatedIncident {
        impactedApplications = impactedApplications == null ? List.of() : List.copyOf(impactedApplications);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
