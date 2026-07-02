package org.kfh.aiops.topology;

import java.util.List;

/**
 * Outcome of resolving an alert's {@code resourceId} against the topology. When {@code mapped} is
 * false the CI is unknown to the topology (surfaced as an "unmapped CI" for CMDB gaps).
 */
public record AssetResolution(String resourceId, boolean mapped, Asset asset, Component component,
                              List<BusinessApplication> applications) {

    public static AssetResolution unmapped(String resourceId) {
        return new AssetResolution(resourceId, false, null, null, List.of());
    }
}
