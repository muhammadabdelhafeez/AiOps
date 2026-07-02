package org.kfh.aiops.rca;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.kfh.aiops.index.IndexSearchService;
import org.kfh.aiops.index.model.IndexQuery;
import org.kfh.aiops.index.model.TelemetryDocument;
import org.kfh.aiops.index.model.TelemetryKind;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.kfh.aiops.topology.BusinessApplication;
import org.kfh.aiops.topology.Component;
import org.kfh.aiops.topology.TopologyService;
import org.springframework.stereotype.Service;

/**
 * Correlation engine (Stages 4–6): groups the window's alerts into candidate incidents by shared
 * causal path over the topology. {@link #correlate(List)} is the pure, testable core;
 * {@link #correlateWindow} pulls the window's ALERTS from the index and runs it.
 */
@Service
public class CorrelationService {

    private static final int WINDOW_FETCH = 500;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CorrelationService.class);

    private final IndexSearchService indexSearchService;
    private final TopologyService topology;

    public CorrelationService(IndexSearchService indexSearchService, TopologyService topology) {
        this.indexSearchService = indexSearchService;
        this.topology = topology;
    }

    /** Correlate a window's ALERTS read from the Custom Index. Requires {@code ALERT_READ}. */
    public CorrelationResult correlateWindow(TenantContext ctx, Instant from, Instant to) {
        ctx.requirePermission("ALERT_READ");
        long t0 = System.nanoTime();
        var query = new IndexQuery(List.of(TelemetryKind.ALERTS), ctx.countryCode(), ctx.environment(),
                from, to, null, null, null, null, null, null, null, null, 0, WINDOW_FETCH);
        var result = indexSearchService.search(ctx, query);
        var correlation = correlate(result.hits());
        log.info("[CORRELATE] window {}..{} country={} env={} -> alerts={} mapped={} unmappedCIs={} incidents={} took={}ms correlationId={}",
                from, to, ctx.countryCode(), ctx.environment(), correlation.alertsProcessed(), correlation.alertsMapped(),
                correlation.unmappedCis().size(), correlation.incidents().size(), (System.nanoTime() - t0) / 1_000_000, ctx.correlationId());
        return correlation;
    }

    /** Pure correlation: alerts → resolved components → blast-radius clusters → candidate incidents. */
    public CorrelationResult correlate(List<TelemetryDocument> alerts) {
        var list = alerts == null ? List.<TelemetryDocument>of() : alerts;

        Map<String, List<TelemetryDocument>> byComponent = new LinkedHashMap<>();
        Map<String, Component> componentById = new LinkedHashMap<>();
        Map<String, String> assetCiByComponent = new LinkedHashMap<>();
        Set<String> unmapped = new LinkedHashSet<>();
        int mapped = 0;

        for (var a : list) {
            var res = topology.resolve(a.resourceId());
            if (!res.mapped() || res.component() == null) {
                if (a.resourceId() != null && !a.resourceId().isBlank()) {
                    unmapped.add(a.resourceId());
                }
                continue;
            }
            mapped++;
            var cid = res.component().id();
            byComponent.computeIfAbsent(cid, k -> new ArrayList<>()).add(a);
            componentById.putIfAbsent(cid, res.component());
            if (res.asset() != null) {
                assetCiByComponent.putIfAbsent(cid, res.asset().ciKey());
            }
        }

        List<CorrelatedIncident> incidents = new ArrayList<>();
        for (var cluster : clusters(byComponent.keySet())) {
            incidents.add(buildIncident(cluster, byComponent, componentById, assetCiByComponent));
        }
        incidents.sort((x, y) -> {
            int c = Integer.compare(sevRank(y.severity()), sevRank(x.severity()));
            return c != 0 ? c : Integer.compare(y.alertCount(), x.alertCount());
        });
        for (var inc : incidents) {
            log.info("[CORRELATE] incident key={} severity={} rootCause={} ({}) apps={} alerts={}",
                    inc.incidentKey(), inc.severity(), inc.rootCauseComponentName(), inc.rootCauseAssetCi(),
                    inc.impactedApplications(), inc.alertCount());
        }
        if (!unmapped.isEmpty()) {
            var preview = unmapped.size() <= 20 ? new ArrayList<>(unmapped) : new ArrayList<>(unmapped).subList(0, 20);
            log.info("[CORRELATE] {} unmapped CI(s) not in topology (CMDB gap): {}", unmapped.size(), preview);
        }
        log.info("[CORRELATE] formed {} incident(s) from {} alerts ({} mapped, {} unmapped)",
                incidents.size(), list.size(), mapped, unmapped.size());
        return new CorrelationResult(incidents, new ArrayList<>(unmapped), list.size(), mapped);
    }

    /** Partition failing components into connected clusters (linked via dependency or dependents). */
    private List<Set<String>> clusters(Set<String> failing) {
        Set<String> remaining = new LinkedHashSet<>(failing);
        List<Set<String>> out = new ArrayList<>();
        while (!remaining.isEmpty()) {
            var start = remaining.iterator().next();
            remaining.remove(start);
            Set<String> cluster = new LinkedHashSet<>();
            cluster.add(start);
            var queue = new ArrayDeque<String>();
            queue.add(start);
            while (!queue.isEmpty()) {
                var c = queue.poll();
                Set<String> neighbours = new HashSet<>();
                neighbours.addAll(topology.blastRadiusComponents(c)); // self + dependents
                neighbours.addAll(topology.dependencyClosure(c));      // foundations
                neighbours.retainAll(failing);
                for (var n : neighbours) {
                    if (remaining.remove(n)) {
                        cluster.add(n);
                        queue.add(n);
                    }
                }
            }
            out.add(cluster);
        }
        return out;
    }

    /** Root cause = failing component covering the most cluster symptoms; tie-break by upstream reach. */
    private String pickRootCause(Set<String> cluster) {
        String best = null;
        int bestCover = -1;
        int bestReach = -1;
        for (var c : cluster) {
            Set<String> cover = new HashSet<>(topology.blastRadiusComponents(c));
            cover.retainAll(cluster);
            int reach = topology.blastRadiusComponents(c).size();
            if (cover.size() > bestCover || (cover.size() == bestCover && reach > bestReach)) {
                best = c;
                bestCover = cover.size();
                bestReach = reach;
            }
        }
        return best;
    }

    private CorrelatedIncident buildIncident(Set<String> cluster, Map<String, List<TelemetryDocument>> byComponent,
                                             Map<String, Component> componentById, Map<String, String> assetCiByComponent) {
        var root = pickRootCause(cluster);
        var rootComp = componentById.get(root);

        List<CorrelatedEvidence> evidence = new ArrayList<>();
        Instant started = null;
        int maxSev = 0;
        String severity = "INFO";
        for (var cid : cluster) {
            var comp = componentById.get(cid);
            for (var a : byComponent.getOrDefault(cid, List.of())) {
                evidence.add(new CorrelatedEvidence(a.resourceId(), cid, comp != null ? comp.name() : cid,
                        a.severity(), a.sourceSystem(), a.timestamp(), a.message()));
                if (a.timestamp() != null && (started == null || a.timestamp().isBefore(started))) {
                    started = a.timestamp();
                }
                if (sevRank(a.severity()) > maxSev) {
                    maxSev = sevRank(a.severity());
                    severity = norm(a.severity());
                }
            }
        }
        evidence.sort(Comparator.comparing(e -> e.timestamp() == null ? Instant.EPOCH : e.timestamp()));

        var apps = topology.impactedApplications(root).stream().map(BusinessApplication::name).collect(Collectors.toList());
        var rootName = rootComp != null ? rootComp.name() : root;
        var title = rootName + (apps.isEmpty() ? "" : " impacting " + String.join(", ", apps));

        var sample = byComponent.get(root).get(0);
        var incidentKey = (sample.countryCode() + "|" + sample.environment() + "|" + root).toUpperCase(Locale.ROOT);

        return new CorrelatedIncident(incidentKey, title, severity, started, root, rootName,
                assetCiByComponent.get(root), apps, evidence.size(), evidence);
    }

    private static int sevRank(String s) {
        return switch (s == null ? "" : s.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> 5;
            case "HIGH" -> 4;
            case "MEDIUM" -> 3;
            case "LOW" -> 2;
            case "INFO" -> 1;
            default -> 0;
        };
    }

    private static String norm(String s) {
        return s == null ? "INFO" : s.trim().toUpperCase(Locale.ROOT);
    }
}
