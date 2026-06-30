package org.kfh.aiops.normalization.fingerprint;

import io.lettuce.core.SetArgs;
import org.kfh.aiops.platform.redis.RedisConnectionProvider;
import org.kfh.aiops.platform.redis.RedisErrors;
import org.kfh.aiops.platform.redis.RedisKeys;
import org.kfh.aiops.platform.redis.RedisSettingsResolver;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Causal funnel Stage 2 (docs/CAUSAL_PIPELINE.md §2): short-window alert deduplication via Redis
 * {@code SET key NX EX}. Keys are country/environment-scoped ({@link RedisKeys#dedup}) on DB 0.
 *
 * <p>Degraded mode (copilot-instructions §29, ARCHITECTURE degraded-mode table): if Redis is not
 * configured or unreachable, dedup is bypassed and the event is treated as new — ingestion must
 * never be blocked, and alerts must never be silently dropped, because Redis is down.
 */
@Service
public class FingerprintDedupService {

    private static final Logger log = LoggerFactory.getLogger(FingerprintDedupService.class);

    private final RedisSettingsResolver resolver;
    private final RedisConnectionProvider provider;
    private final long windowSeconds;

    public FingerprintDedupService(
            RedisSettingsResolver resolver,
            RedisConnectionProvider provider,
            @Value("${kfh.dedup.window-seconds:600}") long windowSeconds) {
        this.resolver = resolver;
        this.provider = provider;
        this.windowSeconds = windowSeconds;
    }

    /**
     * Records a fingerprint occurrence and reports whether it is new within the dedup window.
     *
     * @return {@code true} if this is the first occurrence in the window (NOT a duplicate);
     *         {@code false} if an identical fingerprint was already seen. Fails open (returns
     *         {@code true}) when Redis is unavailable so alerts are never silently dropped.
     */
    public boolean isFirstOccurrence(TenantContext ctx, String source, String resourceId, String errorCode) {
        var settings = resolver.resolve(ctx);
        if (settings.isEmpty()) {
            log.warn("Redis not configured (country={}, env={}); dedup bypassed, treating as new. correlationId={}",
                    ctx.countryCode(), ctx.environment(), ctx.correlationId());
            return true;
        }
        var key = RedisKeys.dedup(ctx.countryCode(), ctx.environment(), source, resourceId, errorCode);
        try {
            return provider.execute(settings.get(), commands -> {
                var stored = commands.set(key, ctx.correlationId(), SetArgs.Builder.nx().ex(windowSeconds));
                return "OK".equals(stored);
            });
        } catch (RuntimeException ex) {
            log.warn("Redis dedup unavailable; bypassing (treating as new). country={}, env={}, reason={}, correlationId={}",
                    ctx.countryCode(), ctx.environment(), RedisErrors.safe(ex), ctx.correlationId());
            return true;
        }
    }
}
