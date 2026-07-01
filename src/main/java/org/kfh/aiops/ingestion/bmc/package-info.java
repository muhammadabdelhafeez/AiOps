/**
 * BMC Helix Events collection (causal funnel Stage 0). {@link org.kfh.aiops.ingestion.bmc.BmcHelixClient}
 * authenticates against the Helix IMS login endpoint and pulls events from the Events {@code msearch}
 * API (proven flow captured in docs/BMC_Helix_response.md), returning raw {@code _source} maps. The
 * {@link org.kfh.aiops.ingestion.bmc.BmcCollector} hands those to the shared
 * {@link org.kfh.aiops.ingestion.IngestionService} (normalize → dedup → index). Collection runs on a
 * manual endpoint (POST /api/v1/ingestion/bmc/collect-now) and an opt-in scheduled poll; credentials
 * come from configuration/environment only and are never committed.
 */
package org.kfh.aiops.ingestion.bmc;
