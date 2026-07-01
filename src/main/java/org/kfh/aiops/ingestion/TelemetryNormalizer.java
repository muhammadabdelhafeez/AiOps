package org.kfh.aiops.ingestion;

import java.util.Map;
import org.kfh.aiops.index.model.TelemetryDocument;
import org.kfh.aiops.platform.tenant.TenantContext;

/**
 * Maps one raw source event (a loosely-typed {@code Map<String,Object>} as delivered by a connector)
 * onto the canonical {@link TelemetryDocument}. One implementation per source system (BMC, SCOM, …).
 *
 * <p>Implementations MUST be defensive: never assume a field is present, always produce a valid
 * document (non-blank {@code id}, non-null {@code timestamp}/{@code kind}), and set
 * {@code attributes["errorCode"]} to a stable signal used for fingerprint dedup ({@link
 * IngestionService}). CMDB enrichment (application/service/journey) happens downstream — here we only
 * capture what the raw event carries.
 */
public interface TelemetryNormalizer {

    /** Canonical source-system id this normalizer handles, e.g. {@code "BMC"} or {@code "SCOM"}. */
    String sourceSystem();

    /** Map a single raw event to a canonical document. May throw on unmappable input. */
    TelemetryDocument normalize(TenantContext ctx, Map<String, Object> raw);
}
