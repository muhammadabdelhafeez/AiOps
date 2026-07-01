package org.kfh.aiops.index.model;

import java.util.List;

/**
 * Paged search response. {@code total} is the full match count (pre-pagination); {@code hits} is the
 * requested page, newest first. Never hand a full result set to AI — build a compact EvidencePack
 * instead (§10 rule).
 */
public record IndexSearchResult(
        int total,
        int page,
        int size,
        long tookMs,
        List<TelemetryDocument> hits) {

    public IndexSearchResult {
        hits = hits == null ? List.of() : List.copyOf(hits);
    }
}
