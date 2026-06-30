package org.kfh.aiops.platform.redis;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds and caches Lettuce connections to runtime Redis servers resolved from Settings.
 * One pooled {@link StatefulRedisConnection} per distinct server (host/port/tls/user); the password
 * is never part of the cache key. Always connects to logical DB 0 (copilot-instructions §12).
 */
@Component
public class RedisConnectionProvider implements AutoCloseable {

    private final Map<String, Pooled> connections = new ConcurrentHashMap<>();
    private final Duration commandTimeout;
    private final Duration connectTimeout;

    public RedisConnectionProvider(
            @Value("${kfh.redis.command-timeout-ms:2000}") long commandTimeoutMs,
            @Value("${kfh.redis.connect-timeout-ms:3000}") long connectTimeoutMs) {
        this.commandTimeout = Duration.ofMillis(commandTimeoutMs);
        this.connectTimeout = Duration.ofMillis(connectTimeoutMs);
    }

    /** Run a synchronous command block against the resolved Redis server. */
    public <T> T execute(RedisConnectionSettings settings, Function<RedisCommands<String, String>, T> action) {
        var pooled = connections.computeIfAbsent(settings.cacheKey(), key -> connect(settings));
        return action.apply(pooled.connection().sync());
    }

    private Pooled connect(RedisConnectionSettings settings) {
        var uri = RedisURI.builder()
                .withHost(settings.host())
                .withPort(settings.port())
                .withSsl(settings.tlsEnabled())
                .withDatabase(0)
                .withTimeout(commandTimeout)
                .build();
        if (!settings.password().isBlank()) {
            if (!settings.username().isBlank()) {
                uri.setUsername(settings.username());
            }
            uri.setPassword(settings.password().toCharArray());
        }
        var client = RedisClient.create(uri);
        client.setOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(connectTimeout).build())
                .build());
        return new Pooled(client, client.connect());
    }

    @Override
    @PreDestroy
    public void close() {
        connections.values().forEach(Pooled::shutdown);
        connections.clear();
    }

    private record Pooled(RedisClient client, StatefulRedisConnection<String, String> connection) {
        void shutdown() {
            try {
                connection.close();
            } finally {
                client.shutdown();
            }
        }
    }
}
