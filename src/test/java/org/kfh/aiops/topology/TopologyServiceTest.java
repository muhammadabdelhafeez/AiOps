package org.kfh.aiops.topology;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class TopologyServiceTest {

    private final TopologyService topology = new TopologyService();

    @Test
    void resolvesAlertCiToAssetComponentAndApplicationsCaseInsensitively() {
        var r = topology.resolve("san-storage-02");

        assertThat(r.mapped()).isTrue();
        assertThat(r.asset().componentId()).isEqualTo("san-storage");
        assertThat(r.component().name()).isEqualTo("SAN Storage");
        assertThat(appNames(r.applications())).contains("Fund Transfer");
    }

    @Test
    void reportsUnmappedCiForUnknownResource() {
        var r = topology.resolve("WHO-KNOWS-99");

        assertThat(r.mapped()).isFalse();
        assertThat(r.asset()).isNull();
        assertThat(r.applications()).isEmpty();
    }

    @Test
    void autoMapsUnknownCiToSyntheticSingleNodeComponentWhenEnabled() {
        var auto = new TopologyService();
        auto.setAutoMap(true);

        var r = auto.resolve("prod-oracle-07");

        assertThat(r.mapped()).isTrue();
        assertThat(r.component().name()).isEqualTo("prod-oracle-07");
        assertThat(r.component().id()).isEqualTo("auto:PROD-ORACLE-07"); // stable id → same incident across cycles
        assertThat(r.asset().ciKey()).isEqualTo("prod-oracle-07");
        assertThat(r.applications()).isEmpty();
        assertThat(auto.blastRadiusComponents(r.component().id())).containsExactly("auto:PROD-ORACLE-07");
    }

    @Test
    void blastRadiusOfStorageReachesGatewayAndChannels() {
        var comps = topology.blastRadiusComponents("san-storage");

        assertThat(comps).contains("san-storage", "oracle-core", "svc-transfer", "api-gw", "mob-app", "web-portal");
    }

    @Test
    void sharedRootCauseImpactsMultipleApplications() {
        var apps = appNames(topology.impactedApplications("san-storage").stream().collect(Collectors.toList()));

        assertThat(apps).contains("Fund Transfer", "KFHOnline", "WAMD");
    }

    @Test
    void dependencyClosureWalksTowardFoundations() {
        var foundations = topology.dependencyClosure("svc-transfer");

        assertThat(foundations).contains("oracle-core", "san-storage");
    }

    @Test
    void serviceLevelFailureImpactsItsApplication() {
        var apps = appNames(topology.impactedApplications("svc-transfer").stream().collect(Collectors.toList()));

        assertThat(apps).contains("Fund Transfer");
    }

    private static java.util.List<String> appNames(java.util.List<BusinessApplication> apps) {
        return apps.stream().map(BusinessApplication::name).collect(Collectors.toList());
    }
}
