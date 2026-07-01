/**
 * Causal funnel Stage 0–3 ingestion pipeline (docs/CAUSAL_PIPELINE.md). Connectors hand raw
 * source events (BMC Helix, SCOM, …) to a {@link org.kfh.aiops.ingestion.TelemetryNormalizer},
 * which maps them onto the canonical {@link org.kfh.aiops.index.model.TelemetryDocument}. The
 * {@link org.kfh.aiops.ingestion.IngestionService} then runs normalize → fingerprint dedup
 * (Redis, Stage&nbsp;2) → index write (Stage&nbsp;3), so downstream topology/RCA/AI stages read a
 * single, deduplicated, multi-source stream.
 *
 * <p>This package deliberately does not fetch from the sources — collection (BMC REST, SCOM WinRM)
 * plugs in on top by producing the {@code List<Map<String,Object>>} raw batches this pipeline
 * consumes. Normalizers are defensive: a malformed event is counted as failed, never throws the
 * whole batch away, and Redis being down fails open (event treated as new) so alerts are never lost.
 */
package org.kfh.aiops.ingestion;
