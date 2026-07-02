/**
 * Causal funnel Stage 4 (topology) — lightweight application-topology model that the correlation
 * engine walks. Models {@code BusinessApplication → Component → Asset} plus component
 * {@code DEPENDS_ON} edges, so an ingested alert's CI ({@code resourceId}) resolves to the asset →
 * component → impacted application(s), and a failing component's blast radius (its dependents) can be
 * computed.
 *
 * <p>For now the model is a hand-modelled KFH seed ({@link org.kfh.aiops.topology.TopologySeed}); the
 * full CMDB/agent-discovered, Neo4j-backed topology lands in a later phase. Keeping the interface here
 * lets the correlation engine (and the incident's blast-radius view) be built now against real logic.
 */
package org.kfh.aiops.topology;
