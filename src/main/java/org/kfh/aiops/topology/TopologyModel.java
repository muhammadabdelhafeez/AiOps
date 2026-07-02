package org.kfh.aiops.topology;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * In-memory topology graph: assets keyed by CI, components by id, applications by id, plus a reverse
 * {@code dependents} index (components that depend on a given component) for blast-radius traversal.
 * Immutable once built.
 */
public final class TopologyModel {

    private final Map<String, Asset> assetsByCi = new LinkedHashMap<>();
    private final Map<String, Component> componentsById = new LinkedHashMap<>();
    private final Map<String, BusinessApplication> appsById = new LinkedHashMap<>();
    private final Map<String, List<String>> dependents = new LinkedHashMap<>();

    public TopologyModel(List<BusinessApplication> apps, List<Component> components, List<Asset> assets) {
        for (var a : apps) {
            appsById.put(a.id(), a);
        }
        for (var c : components) {
            componentsById.put(c.id(), c);
            dependents.computeIfAbsent(c.id(), k -> new ArrayList<>());
        }
        for (var c : components) {
            for (var dep : c.dependsOn()) {
                dependents.computeIfAbsent(dep, k -> new ArrayList<>()).add(c.id());
            }
        }
        for (var a : assets) {
            assetsByCi.put(a.matchKey(), a);
        }
    }

    /** Resolve an alert's {@code resourceId} (case-insensitive) to a bound asset. */
    public Optional<Asset> resolveAsset(String resourceId) {
        if (resourceId == null || resourceId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(assetsByCi.get(resourceId.trim().toUpperCase(Locale.ROOT)));
    }

    public Optional<Component> component(String componentId) {
        return Optional.ofNullable(componentsById.get(componentId));
    }

    public Optional<BusinessApplication> application(String applicationId) {
        return Optional.ofNullable(appsById.get(applicationId));
    }

    /** Components that (transitively) depend on {@code componentId} — the blast radius / downstream. */
    public Set<String> dependentsClosure(String componentId) {
        return bfs(componentId, dependents::get);
    }

    /** Components that {@code componentId} (transitively) depends on — its foundations / upstream. */
    public Set<String> dependencyClosure(String componentId) {
        return bfs(componentId, id -> component(id).map(Component::dependsOn).orElse(List.of()));
    }

    private Set<String> bfs(String start, java.util.function.Function<String, List<String>> edges) {
        Set<String> seen = new LinkedHashSet<>();
        if (!componentsById.containsKey(start)) {
            return seen;
        }
        var queue = new ArrayDeque<String>();
        queue.add(start);
        while (!queue.isEmpty()) {
            var cur = queue.poll();
            var next = edges.apply(cur);
            if (next == null) {
                continue;
            }
            for (var n : next) {
                if (seen.add(n)) {
                    queue.add(n);
                }
            }
        }
        return seen;
    }

    public java.util.Collection<BusinessApplication> applications() {
        return appsById.values();
    }
}
