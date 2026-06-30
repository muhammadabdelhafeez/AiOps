/**
 * Causal-funnel normalization stage (docs/CAUSAL_PIPELINE.md §2): canonical event mapping,
 * enrichment, and fingerprint-based deduplication. Most of this module is still to build; the
 * {@code fingerprint} sub-package provides the Redis-backed dedup step.
 */
package org.kfh.aiops.normalization;
