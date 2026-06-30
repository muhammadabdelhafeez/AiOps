/**
 * Runtime Redis access for the causal funnel: resolves Redis servers from Settings
 * (config.integration_settings → infrastructure.connections[type=REDIS]), pools Lettuce
 * connections (logical DB 0 only, key-prefix isolation), and exposes a tenant-scoped health probe.
 *
 * <p>See docs/CAUSAL_PIPELINE.md §11 and .github/copilot-instructions.md §12 for the Redis rules:
 * hot state / dedup / locks / cache only, DB 0 only, key prefixes per (country, environment).
 */
package org.kfh.aiops.platform.redis;
