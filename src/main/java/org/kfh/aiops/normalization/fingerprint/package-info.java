/**
 * Fingerprint deduplication — causal funnel Stage 2 (docs/CAUSAL_PIPELINE.md §2): short-window
 * Redis {@code SET NX EX} dedup of normalized events, country/environment-scoped on DB 0.
 */
package org.kfh.aiops.normalization.fingerprint;
