package org.kfh.aiops.topology;

import java.util.List;

/**
 * A business application in the topology (e.g. Fund Transfer, KFHOnline). Journeys are the
 * customer-facing flows whose transaction success rate anchors an incident's business impact.
 */
public record BusinessApplication(String id, String name, String criticality, List<String> journeys) {

    public BusinessApplication {
        journeys = journeys == null ? List.of() : List.copyOf(journeys);
    }
}
