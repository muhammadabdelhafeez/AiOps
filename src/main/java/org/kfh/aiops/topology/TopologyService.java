package org.kfh.aiops.topology;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Topology queries used by the correlation engine (Stage 4): resolve an alert CI to its asset →
 * component → application(s), and compute a failing component's blast radius + impacted applications.
 * Backed by the {@link TopologySeed} for now; swap the model for CMDB/Neo4j-backed data later without
 * changing callers.
 */
@Service
public class TopologyService {

    private final TopologyModel model;
    // Off by default (unit tests keep unmapped-CI semantics); Spring turns it on via @Value (:true).
    private boolean autoMap = false;

    public TopologyService() {
        this(TopologySeed.build());
    }

    /** Test/seam constructor. */
    public TopologyService(TopologyModel model) {
        this.model = model;
    }

    @Value("${kfh.topology.auto-map:true}")
    void setAutoMap(boolean autoMap) {
        this.autoMap = autoMap;
    }

    /**
     * Resolve an alert resourceId to asset → component → applications. When the CI is unknown to the
     * seeded/CMDB topology and auto-map is on, it is auto-registered as its own single-node component
     * (stable id per CI) so real BMC/SCOM alerts still form incidents instead of being dropped as a
     * CMDB gap. Auto-mapped incidents have no dependency edges (one incident per affected CI) and no
     * impacted applications until the CI is covered by real topology.
     */
    public AssetResolution resolve(String resourceId) {
        var asset = model.resolveAsset(resourceId).orElse(null);
        if (asset == null) {
            return autoMap && resourceId != null && !resourceId.isBlank()
                    ? autoResolution(resourceId)
                    : AssetResolution.unmapped(resourceId);
        }
        var component = model.component(asset.componentId()).orElse(null);
        var apps = component == null ? List.<BusinessApplication>of() : applicationsForComponent(component.id());
        return new AssetResolution(resourceId, true, asset, component, apps);
    }

    /** Synthetic single-node topology for an unmapped CI, with a stable id so it stays the same incident. */
    private AssetResolution autoResolution(String resourceId) {
        var ci = resourceId.trim();
        var componentId = "auto:" + ci.toUpperCase(Locale.ROOT);
        var component = new Component(componentId, ci, "Asset", List.of(), List.of());
        var asset = new Asset(ci, ci, "AutoDiscovered", componentId);
        return new AssetResolution(resourceId, true, asset, component, List.of());
    }

    public Optional<Asset> resolveAsset(String resourceId) {
        return model.resolveAsset(resourceId);
    }

    public Optional<Component> componentForAsset(Asset asset) {
        return asset == null ? Optional.empty() : model.component(asset.componentId());
    }

    public List<BusinessApplication> applicationsForComponent(String componentId) {
        return model.component(componentId)
                .map(Component::applicationIds).orElse(List.of()).stream()
                .map(model::application).filter(Optional::isPresent).map(Optional::get)
                .collect(Collectors.toList());
    }

    /** Components in the blast radius of a failing component (itself + everything that depends on it). */
    public Set<String> blastRadiusComponents(String componentId) {
        Set<String> comps = new LinkedHashSet<>();
        comps.add(componentId);
        comps.addAll(model.dependentsClosure(componentId));
        return comps;
    }

    /** Applications impacted when {@code componentId} fails (union across its blast radius). */
    public Set<BusinessApplication> impactedApplications(String componentId) {
        Set<String> appIds = new LinkedHashSet<>();
        for (var comp : blastRadiusComponents(componentId)) {
            model.component(comp).ifPresent(c -> appIds.addAll(c.applicationIds()));
        }
        return appIds.stream().map(model::application).filter(Optional::isPresent).map(Optional::get)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Foundations a component (transitively) depends on — used to rank root cause (deepest failing). */
    public Set<String> dependencyClosure(String componentId) {
        return model.dependencyClosure(componentId);
    }
}
