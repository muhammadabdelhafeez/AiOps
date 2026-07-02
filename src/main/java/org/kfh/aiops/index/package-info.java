/**
 * Custom Log Index Engine (causal funnel Stage 3) — searchable telemetry store that replaces
 * Elasticsearch/OpenSearch (see .github/copilot-instructions.md §10, docs/CAUSAL_PIPELINE.md §9).
 *
 * <p>Documents are partitioned into shards by {@code {country}/{kind}/{date}/shard-NN}
 * and stored as append-only segments. Search prunes by time partition + country/environment before
 * scanning shards in parallel. Raw payloads live in object storage; the index keeps only a
 * {@code rawRef} pointer. Increment 1 provides the writer, sharded store, and a pruning+parallel
 * filtered searcher; the in-shard inverted index, retention/archive, and Settings-driven storage
 * path are follow-up increments.
 */
package org.kfh.aiops.index;
