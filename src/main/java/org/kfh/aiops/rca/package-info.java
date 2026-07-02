/**
 * Causal funnel Stages 4–6 (correlation / RCA). Turns the deduplicated, indexed alerts into a small
 * set of correlated incidents: each alert's CI is resolved via {@link org.kfh.aiops.topology}, failing
 * components are grouped by shared causal path (blast radius), the most-upstream failing component is
 * picked as the candidate root cause, and impacted applications + evidence are attached.
 *
 * <p>This is deterministic candidate correlation (docs/CAUSAL_PIPELINE §8A). The AI-led confirmation
 * + narrative (Stage 7–8) and the deterministic incident lifecycle build on this output later.
 */
package org.kfh.aiops.rca;
