package org.kfh.aiops.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.kfh.aiops.ingestion.bmc.BmcHelixClient;
import org.kfh.aiops.ingestion.bmc.BmcProperties;
import org.kfh.aiops.ingestion.scom.ScomProperties;
import org.kfh.aiops.ingestion.scom.ScomWinRmClient;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.kfh.aiops.plugin.service.ConnectorPersistenceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Connector-instance-driven ingestion bridge — the piece that makes "enable a connector in the UI"
 * actually start pulling real alerts. On each tick it lists the ENABLED connectors from the durable
 * store and, for any connector whose per-connector schedule ({@code attributes.intervalMin}, minutes)
 * is due, builds a per-connector client from the connector's stored config + decrypted secrets and
 * runs one collection cycle through the shared pipeline (normalize → dedup → index). The resulting
 * alerts land in the Custom Index under the connector's {@code (country, environment)} scope, which is
 * exactly what {@code AlertService.list} reads back.
 *
 * <p>Gated by {@code kfh.ingestion.bridge.enabled} (default true). A no-op when no durable store is
 * available (no PostgreSQL). Each connector is isolated: a failure logs and never blocks the others.
 */
@Component
@ConditionalOnProperty(prefix = "kfh.ingestion.bridge", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ConnectorIngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ConnectorIngestionScheduler.class);
    private static final int DEFAULT_INTERVAL_MIN = 15;
    private static final String DEFAULT_TENANT = "00000000-0000-4000-8000-000000000001";

    private final ObjectProvider<ConnectorPersistenceStore> storeProvider;
    private final ObjectMapper objectMapper;
    private final IngestionService ingestionService;
    private final BmcNormalizer bmcNormalizer;
    private final ScomNormalizer scomNormalizer;
    private final Map<UUID, Instant> lastRun = new ConcurrentHashMap<>();

    public ConnectorIngestionScheduler(ObjectProvider<ConnectorPersistenceStore> storeProvider,
            ObjectMapper objectMapper, IngestionService ingestionService,
            BmcNormalizer bmcNormalizer, ScomNormalizer scomNormalizer) {
        this.storeProvider = storeProvider;
        this.objectMapper = objectMapper;
        this.ingestionService = ingestionService;
        this.bmcNormalizer = bmcNormalizer;
        this.scomNormalizer = scomNormalizer;
    }

    @Scheduled(fixedDelayString = "${kfh.ingestion.bridge.tick-ms:60000}",
            initialDelayString = "${kfh.ingestion.bridge.initial-delay-ms:30000}")
    public void tick() {
        var store = storeProvider.getIfAvailable();
        if (store == null) {
            return; // no durable store (no DB) — nothing to drive
        }
        List<Map<String, Object>> connectors;
        try {
            connectors = store.listEnabled();
        } catch (RuntimeException ex) {
            log.warn("[BRIDGE] could not list enabled connectors: {}", ex.getMessage());
            return;
        }
        var now = Instant.now();
        for (var connector : connectors) {
            var id = connectorId(connector);
            if (id == null) {
                continue;
            }
            var intervalMin = intervalMinutes(connector);
            var previous = lastRun.get(id);
            if (previous != null && Duration.between(previous, now).toMinutes() < intervalMin) {
                continue; // not due yet
            }
            lastRun.put(id, now);
            try {
                collectOne(store, connector);
            } catch (RuntimeException ex) {
                log.error("[BRIDGE] collection failed for connector {} ({}): {}",
                        connector.get("name"), id, ex.getMessage());
            }
        }
    }

    private void collectOne(ConnectorPersistenceStore store, Map<String, Object> connector) {
        var id = connectorId(connector);
        var pluginType = str(connector.get("pluginType")).toUpperCase(Locale.ROOT);
        var country = strOr(connector.get("countryCode"), "KW");
        var environment = strOr(connector.get("environment"), "PROD");
        var tenantId = strOr(connector.get("tenantId"), DEFAULT_TENANT);
        var secrets = safeSecrets(store, id);
        var ctx = systemContext(tenantId, country, environment);

        switch (pluginType) {
            case "BMC" -> {
                var props = bmcProperties(connector, secrets, country, environment, tenantId);
                if (!props.isConfigured()) {
                    log.info("[BRIDGE] BMC connector {} not fully configured (base-url/access-key); skipping", id);
                    return;
                }
                long t0 = System.nanoTime();
                var raw = new BmcHelixClient(props, objectMapper).fetchRawEvents(props.getMinutesBack(), props.getMaxEvents());
                var result = ingestionService.ingest(ctx, bmcNormalizer, raw);
                log.info("[BRIDGE] BMC connector {} country={} fetched={} indexed={} duplicates={} took={}ms",
                        id, country, raw.size(), result.indexed(), result.duplicatesDropped(),
                        (System.nanoTime() - t0) / 1_000_000);
            }
            case "SCOM" -> {
                var props = scomProperties(connector, secrets, country, environment, tenantId);
                if (!props.isConfigured()) {
                    log.info("[BRIDGE] SCOM connector {} not fully configured (management-server/username/password); skipping", id);
                    return;
                }
                long t0 = System.nanoTime();
                var raw = new ScomWinRmClient(props, objectMapper).fetchRawEvents(props.getHoursBack());
                var result = ingestionService.ingest(ctx, scomNormalizer, raw);
                log.info("[BRIDGE] SCOM connector {} country={} fetched={} indexed={} duplicates={} took={}ms",
                        id, country, raw.size(), result.indexed(), result.duplicatesDropped(),
                        (System.nanoTime() - t0) / 1_000_000);
            }
            default -> log.debug("[BRIDGE] connector {} type {} has no bridge collector yet; skipping", id, pluginType);
        }
    }

    private BmcProperties bmcProperties(Map<String, Object> c, Map<String, String> secrets,
            String country, String environment, String tenantId) {
        var p = new BmcProperties();
        p.setEnabled(true);
        p.setBaseUrl(str(firstNonBlank(c.get("baseUrl"), c.get("endpointUrl"))));
        p.setAccessKey(secrets.getOrDefault("accessKey", ""));
        p.setAccessSecretKey(secrets.getOrDefault("accessSecretKey", ""));
        if (hasText(c.get("loginEndpoint"))) {
            p.setLoginEndpoint(str(c.get("loginEndpoint")));
        }
        if (hasText(c.get("eventsEndpoint"))) {
            p.setEventsEndpoint(str(c.get("eventsEndpoint")));
        }
        p.setMinutesBack(intOr(c.get("minutesBack"), 30));
        p.setMaxEvents(intOr(c.get("maxEvents"), 500));
        p.setTenantId(tenantId);
        p.setCountryCode(country);
        p.setEnvironment(environment);
        return p;
    }

    private ScomProperties scomProperties(Map<String, Object> c, Map<String, String> secrets,
            String country, String environment, String tenantId) {
        var p = new ScomProperties();
        p.setEnabled(true);
        p.setManagementServer(str(c.get("managementServer")));
        p.setUsername(secrets.getOrDefault("username", ""));
        p.setPassword(secrets.getOrDefault("password", ""));
        if (hasText(c.get("domain"))) {
            p.setDomain(str(c.get("domain")));
        }
        p.setHoursBack(intOr(firstNonBlank(c.get("hoursBack"), c.get("hours")), 1));
        p.setWinrmPort(intOr(c.get("winrmPort"), 5986));
        p.setUseHttps(!Boolean.FALSE.equals(c.get("useHttps")));
        p.setVerifySsl(!Boolean.FALSE.equals(c.get("verifySsl")));
        p.setAuthMethod(strOr(c.get("authMethod"), "Kerberos"));
        p.setTenantId(tenantId);
        p.setCountryCode(country);
        p.setEnvironment(environment);
        return p;
    }

    private TenantContext systemContext(String tenantId, String country, String environment) {
        var tenant = safeUuid(tenantId);
        return new TenantContext(tenant, tenant, country, environment,
                "connector-ingestion-bridge", Set.of("ALERT_INGEST"));
    }

    private Map<String, String> safeSecrets(ConnectorPersistenceStore store, UUID id) {
        try {
            return store.secrets(id).orElseGet(Map::of);
        } catch (RuntimeException ex) {
            log.warn("[BRIDGE] could not read secrets for connector {}: {}", id, ex.getMessage());
            return Map.of();
        }
    }

    private static UUID connectorId(Map<String, Object> c) {
        var raw = c.get("id");
        if (raw == null) {
            raw = c.get("connectorId");
        }
        return raw == null ? null : safeUuid(String.valueOf(raw));
    }

    private static UUID safeUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ex) {
            return UUID.fromString(DEFAULT_TENANT);
        }
    }

    private static int intervalMinutes(Map<String, Object> c) {
        int v = intOr(firstNonBlank(c.get("intervalMin"), c.get("pollIntervalMin")), DEFAULT_INTERVAL_MIN);
        return v < 1 ? DEFAULT_INTERVAL_MIN : v;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String strOr(Object v, String def) {
        var s = str(v).trim();
        return s.isBlank() ? def : s;
    }

    private static boolean hasText(Object v) {
        return v != null && !String.valueOf(v).isBlank();
    }

    private static Object firstNonBlank(Object a, Object b) {
        return hasText(a) ? a : b;
    }

    private static int intOr(Object v, int def) {
        if (v == null) {
            return def;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (RuntimeException ex) {
            return def;
        }
    }
}
