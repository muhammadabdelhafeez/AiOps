package org.kfh.aiops.rca;

import java.util.List;

/**
 * Outcome of one correlation pass: the incidents formed, the CIs that couldn't be mapped to the
 * topology (CMDB gaps to fix), and how many alerts were processed vs mapped.
 */
public record CorrelationResult(List<CorrelatedIncident> incidents, List<String> unmappedCis,
                                int alertsProcessed, int alertsMapped) {

    public CorrelationResult {
        incidents = incidents == null ? List.of() : List.copyOf(incidents);
        unmappedCis = unmappedCis == null ? List.of() : List.copyOf(unmappedCis);
    }
}
