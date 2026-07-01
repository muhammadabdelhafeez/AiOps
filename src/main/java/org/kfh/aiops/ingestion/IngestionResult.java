package org.kfh.aiops.ingestion;

/**
 * Outcome of one {@link IngestionService#ingest} batch. Invariant:
 * {@code received == normalized + failed} and {@code normalized == indexed + duplicatesDropped}.
 *
 * @param received          raw events handed to the pipeline
 * @param normalized        events successfully mapped to a document
 * @param duplicatesDropped documents suppressed by short-window fingerprint dedup (Stage 2)
 * @param indexed           documents written to the index (Stage 3)
 * @param failed            events that could not be normalized (counted, never fatal)
 */
public record IngestionResult(int received, int normalized, int duplicatesDropped, int indexed, int failed) {

    public static IngestionResult empty() {
        return new IngestionResult(0, 0, 0, 0, 0);
    }
}
