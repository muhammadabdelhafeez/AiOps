package org.kfh.aiops.topology;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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

    public TopologyService() {
        this(TopologySeed.build());
    }

    /** Test/seam constructor. */
    public TopologyService(TopologyModel model) {
        this.model = model;
    }

    /** Resolve an alert resourceId to asset → component → applications, or an unmapped result. */
    public AssetResolution resolve(String resourceId) {
        var asset = model.resolveAsset(resourceId).orElse(null);
        if (asset == null) {
            return AssetResolution.unmapped(resourceId);
        }
        var component = model.component(asset.componentId()).orElse(null);
        var apps = component == null ? List.<BusinessApplication>of() : applicationsForComponent(component.id());
        return new AssetResolution(resourceId, true, asset, component, apps);
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
