package org.kfh.aiops.platform.config;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.IDN;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.stereotype.Component;

/**
 * Default bounded live tester for Redis, Kafka, and custom index storage Settings rows.
 */
@Component
public class DefaultInfrastructureConnectionTester implements InfrastructureConnectionTester {

    private static final String MASKED_SECRET = "••••••••••••";
    private static final String REDIS_DEFAULT_ACL_USER = "default";

    @Override
    public Map<String, Object> test(TenantContext ctx, String section, Map<String, Object> request) {
        var started = System.nanoTime();
        InfrastructureTestConfig config;
        try {
            config = InfrastructureTestConfig.from(request);
        } catch (RuntimeException ex) {
            // Never let payload-parsing throw a 500 to the caller; surface a clean Fail result instead.
            return result(ctx, section, InfrastructureTestConfig.empty(), "Fail", latencyMs(started),
                    "Infrastructure test failed: request payload could not be parsed.");
        }
        try {
            var message = switch (config.type()) {
                case "REDIS" -> testRedis(config);
                case "KAFKA" -> testKafka(config);
                case "INDEX_STORAGE" -> testIndexStorage(config);
                default -> throw new IllegalArgumentException("Unsupported infrastructure type '" + config.type() + "'.");
            };
            return result(ctx, section, config, "Pass", latencyMs(started), message);
        } catch (RuntimeException | IOException ex) {
            return result(ctx, section, config, "Fail", latencyMs(started), failurePrefix(config.type()) + safeMessage(ex));
        }
    }

    private static String testRedis(InfrastructureTestConfig config) throws IOException {
        config.requireEndpoint("Redis host/IP is required.");
        validateSingleHostEndpoint("Redis", config.endpoint());
        var port = config.redisPort();
        try (var socket = connectedRedisSocket(config.endpoint(), port, config.timeoutMs(), config.tlsEnabled());
             var out = new BufferedOutputStream(socket.getOutputStream());
             var in = new BufferedInputStream(socket.getInputStream())) {
            if (!config.secret().isBlank() && !MASKED_SECRET.equals(config.secret())) {
                authenticateRedis(out, in, config.username(), config.secret());
            }
            sendRedisCommand(out, "PING");
            var pong = readRedisLine(in);
            if (!pong.startsWith("+PONG")) {
                throw new IllegalStateException(describeRedisPingFailure(pong, config.tlsEnabled()));
            }
            return "Redis PING passed.";
        }
    }

    /**
     * Builds a diagnostic message for a non-PONG Redis PING reply. We surface the actual server
     * response (sanitized, truncated) so operators can distinguish auth, protected-mode, TLS, and
     * connection-closed scenarios without having to inspect packet captures.
     */
    static String describeRedisPingFailure(String pong, boolean tlsEnabled) {
        if (pong == null || pong.isEmpty()) {
            return tlsEnabled
                    ? "Redis PING failed: server returned an empty response. The TLS handshake succeeded but the server closed the connection without replying. Verify Redis is listening on TLS and that the supplied credentials are authorized."
                    : "Redis PING failed: server returned an empty response. The server may require TLS, may be in protected mode, or may have closed the connection. Enable TLS in the connector or check the Redis server log.";
        }
        var sanitized = sanitizeRedisReply(pong);
        if (pong.startsWith("-NOAUTH") || pong.startsWith("-WRONGPASS")) {
            return "Redis requires authentication. Enter the correct password, and leave username blank unless your Redis server uses ACL users. Server reply: " + sanitized;
        }
        if (pong.startsWith("-DENIED")) {
            return "Redis denied the connection (likely protected mode or bind restriction). Configure Redis to allow remote connections from the AIOps host, or bind it to a reachable interface. Server reply: " + sanitized;
        }
        if (pong.startsWith("-LOADING")) {
            return "Redis is still loading the dataset in memory and refused the PING. Retry once the server reports it is ready. Server reply: " + sanitized;
        }
        if (pong.startsWith("-MASTERDOWN") || pong.startsWith("-READONLY") || pong.startsWith("-CLUSTERDOWN")) {
            return "Redis rejected PING because of a cluster/replication state. Server reply: " + sanitized;
        }
        if (pong.startsWith("-")) {
            return "Redis PING failed with error reply: " + sanitized;
        }
        return "Redis PING failed: unexpected reply '" + sanitized + "'.";
    }

    private static String sanitizeRedisReply(String reply) {
        var cleaned = reply.replaceAll("[\\r\\n\\t]+", " ").trim();
        return cleaned.length() <= 200 ? cleaned : cleaned.substring(0, 197) + "...";
    }

    private static String testKafka(InfrastructureTestConfig config) {
        config.requireEndpoint("Kafka bootstrap servers are required.");
        validateKafkaBootstrapServers(config.endpoint());
        var props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, config.endpoint());
        props.put(AdminClientConfig.CLIENT_ID_CONFIG, firstNonBlank(config.clientId(), "kfh-aiops-settings-test"));
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, String.valueOf(config.timeoutMs()));
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, String.valueOf(config.timeoutMs()));
        props.put("security.protocol", firstNonBlank(config.protocol(), "PLAINTEXT"));
        addKafkaSecurity(props, config);
        try (var admin = AdminClient.create(props)) {
            admin.describeCluster().nodes().get(config.timeoutMs(), TimeUnit.MILLISECONDS);
            return "Kafka broker metadata probe passed.";
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka test interrupted.", ex);
        } catch (Exception ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    private static String testIndexStorage(InfrastructureTestConfig config) {
        var provider = firstNonBlank(config.provider(), "LOCAL").toUpperCase(Locale.ROOT);
        if (provider.equals("LOCAL") || provider.equals("NFS")) {
            var path = localPath(config.endpoint());
            validateLocalIndexPath(path);
            if (!Files.isDirectory(path)) {
                throw new IllegalArgumentException("Index storage path must be an existing directory.");
            }
            if (!Files.isReadable(path) || !Files.isWritable(path)) {
                throw new IllegalArgumentException("Index storage path must be readable and writable by the application process.");
            }
            return "Index storage directory is readable and writable.";
        }
        validateObjectStoragePointer(provider, config);
        return "Index storage metadata validation passed. Live cloud object-storage probe is not configured in this phase.";
    }

    private static Socket connectedSocket(String host, int port, int timeoutMs) throws IOException {
        var socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setSoTimeout(timeoutMs);
        return socket;
    }

    private static Socket connectedRedisSocket(String host, int port, int timeoutMs, boolean tlsEnabled) throws IOException {
        if (!tlsEnabled) {
            return connectedSocket(host, port, timeoutMs);
        }
        var factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        var sslSocket = (SSLSocket) factory.createSocket();
        try {
            sslSocket.connect(new InetSocketAddress(host, port), timeoutMs);
            sslSocket.setSoTimeout(timeoutMs);
            sslSocket.startHandshake();
            return sslSocket;
        } catch (IOException ex) {
            try {
                sslSocket.close();
            } catch (IOException ignored) {
                // best-effort close on failure path
            }
            throw new IOException("Redis TLS handshake failed. Verify the server presents a trusted certificate, or disable TLS if the server is plain-text.", ex);
        }
    }

    private static void addKafkaSecurity(Properties props, InfrastructureTestConfig config) {
        var protocol = firstNonBlank(config.protocol(), "PLAINTEXT").toUpperCase(Locale.ROOT);
        if (protocol.startsWith("SASL")) {
            if (config.username().isBlank() || config.secret().isBlank() || MASKED_SECRET.equals(config.secret())) {
                throw new IllegalArgumentException("Kafka SASL username and password are required for Test Connection.");
            }
            props.put(SaslConfigs.SASL_MECHANISM, firstNonBlank(config.saslMechanism(), "PLAIN"));
            props.put(SaslConfigs.SASL_JAAS_CONFIG, jaas(config.username(), config.secret()));
        }
        if ((protocol.equals("SSL") || protocol.equals("SASL_SSL")) && !config.truststorePath().isBlank()) {
            props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, config.truststorePath());
        }
    }

    private static String jaas(String username, String password) {
        return "org.apache.kafka.common.security.plain.PlainLoginModule required username=\""
                + escapeJaas(username) + "\" password=\"" + escapeJaas(password) + "\";";
    }

    private static String escapeJaas(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "");
    }

    private static void sendRedisAuth(BufferedOutputStream out, String username, String password) throws IOException {
        if (username.isBlank()) {
            sendRedisCommand(out, "AUTH", password);
        } else {
            sendRedisCommand(out, "AUTH", username, password);
        }
    }

    private static void authenticateRedis(BufferedOutputStream out, BufferedInputStream in, String username, String password) throws IOException {
        sendRedisAuth(out, username, password);
        var auth = readRedisLine(in);
        if (auth.startsWith("+OK")) {
            return;
        }
        if (shouldRetryRedisAuthWithoutUsername(username, auth)) {
            sendRedisAuth(out, "", password);
            var fallback = readRedisLine(in);
            if (fallback.startsWith("+OK")) {
                return;
            }
            throw redisAuthenticationFailure(fallback);
        }
        throw redisAuthenticationFailure(auth);
    }

    static boolean shouldRetryRedisAuthWithoutUsername(String username, String authResponse) {
        return REDIS_DEFAULT_ACL_USER.equalsIgnoreCase(firstNonBlank(username))
                && (authResponse.startsWith("-ERR wrong number of arguments")
                || authResponse.startsWith("-WRONGPASS")
                || authResponse.startsWith("-ERR AUTH"));
    }

    static IllegalStateException redisAuthenticationFailure(String response) {
        var sanitized = sanitizeRedisReply(response);
        if (response.startsWith("-WRONGPASS") || response.startsWith("-NOAUTH")) {
            return new IllegalStateException("Redis authentication failed. Verify the password, and leave username blank unless your Redis server uses ACL users. Server reply: " + sanitized);
        }
        return new IllegalStateException("Redis authentication failed. Server reply: " + sanitized);
    }

    private static void sendRedisCommand(BufferedOutputStream out, String... args) throws IOException {
        out.write(("*" + args.length + "\r\n").getBytes(StandardCharsets.UTF_8));
        for (var arg : args) {
            if (arg.contains("\r") || arg.contains("\n")) {
                throw new IllegalArgumentException("Redis command values must not contain control characters.");
            }
            var bytes = arg.getBytes(StandardCharsets.UTF_8);
            out.write(("$" + bytes.length + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(bytes);
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        out.flush();
    }

    private static String readRedisLine(BufferedInputStream in) throws IOException {
        var builder = new StringBuilder();
        int value;
        while ((value = in.read()) >= 0) {
            if (value == '\n') {
                break;
            }
            if (value != '\r') {
                builder.append((char) value);
            }
        }
        return builder.toString();
    }

    private static Path localPath(String endpoint) {
        var value = firstNonBlank(endpoint);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Index storage path is required.");
        }
        if (value.startsWith("file:")) {
            try {
                return Path.of(new URI(value));
            } catch (URISyntaxException ex) {
                throw new IllegalArgumentException("Index storage file URI is invalid.", ex);
            }
        }
        return Path.of(value);
    }

    private static void validateLocalIndexPath(Path path) {
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("Index storage path must be absolute.");
        }
        for (var segment : path) {
            if ("..".equals(segment.toString())) {
                throw new IllegalArgumentException("Index storage path must not contain traversal segments.");
            }
        }
        if (!path.normalize().equals(path)) {
            throw new IllegalArgumentException("Index storage path must not contain traversal segments.");
        }
    }

    private static void validateObjectStoragePointer(String provider, InfrastructureTestConfig config) {
        if (config.endpoint().isBlank() && config.bucket().isBlank()) {
            throw new IllegalArgumentException(provider + " index storage requires a path/URI or bucket/share.");
        }
        if (!config.endpoint().isBlank()) {
            var parsed = URI.create(config.endpoint());
            if (parsed.getScheme() == null) {
                throw new IllegalArgumentException(provider + " index storage URI must include a scheme.");
            }
            validateObjectStorageScheme(provider, parsed);
            if ("https".equalsIgnoreCase(parsed.getScheme()) && parsed.getHost() != null && !parsed.getHost().isBlank()) {
                validateHostAllowed(provider + " index storage", parsed.getHost());
            }
        }
    }

    private static void validateObjectStorageScheme(String provider, URI parsed) {
        var scheme = parsed.getScheme().toLowerCase(Locale.ROOT);
        var allowed = switch (provider) {
            case "S3" -> List.of("s3", "https");
            case "AZURE_BLOB" -> List.of("https", "abfs", "abfss", "wasb", "wasbs");
            default -> List.of("https");
        };
        if (!allowed.contains(scheme)) {
            throw new IllegalArgumentException(provider + " index storage URI scheme is not allowed.");
        }
    }

    private static void validateKafkaBootstrapServers(String endpoint) {
        Arrays.stream(endpoint.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .forEach(value -> validateSingleHostEndpoint("Kafka", bootstrapHost(value)));
    }

    private static String bootstrapHost(String value) {
        if (value.contains("://") || value.contains("/") || value.contains("@")) {
            throw new IllegalArgumentException("Kafka bootstrap servers must be host:port entries without URL syntax.");
        }
        if (value.startsWith("[")) {
            var end = value.indexOf(']');
            if (end <= 1) {
                throw new IllegalArgumentException("Kafka bootstrap IPv6 host is invalid.");
            }
            return value.substring(1, end);
        }
        var colon = value.indexOf(':');
        return colon > 0 ? value.substring(0, colon) : value;
    }

    private static void validateSingleHostEndpoint(String connectorName, String endpoint) {
        if (endpoint.contains("://") || endpoint.contains("/") || endpoint.contains("@") || endpoint.contains(",")) {
            throw new IllegalArgumentException(connectorName + " endpoint must be a host or IP address, not a URL.");
        }
        validateHostAllowed(connectorName, endpoint);
    }

    private static void validateHostAllowed(String connectorName, String host) {
        var normalized = normalizeHost(host);
        if (normalized.isBlank() || isAlwaysBlockedName(normalized) || resolvesToUnsafeAddress(normalized)) {
            throw new IllegalArgumentException(connectorName + " host is blocked by SSRF protection.");
        }
    }

    private static String normalizeHost(String host) {
        return IDN.toASCII(host == null ? "" : host.trim()).toLowerCase(Locale.ROOT);
    }

    private static boolean isAlwaysBlockedName(String host) {
        return host.equals("localhost")
                || host.endsWith(".localhost")
                || host.equals("169.254.169.254")
                || host.equals("metadata.google.internal")
                || host.equals("metadata")
                || host.equals("::1");
    }

    private static boolean resolvesToUnsafeAddress(String host) {
        try {
            for (var address : InetAddress.getAllByName(host)) {
                if (isUnsafeAddress(address)) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Infrastructure test host could not be resolved.");
        }
    }

    private static boolean isUnsafeAddress(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isMulticastAddress();
    }

    private static Map<String, Object> result(TenantContext ctx, String section, InfrastructureTestConfig config,
            String status, long latencyMs, String message) {
        var result = new LinkedHashMap<String, Object>();
        result.put("section", section);
        result.put("status", status);
        result.put("pass", "Pass".equals(status));
        result.put("latencyMs", latencyMs);
        result.put("message", message);
        result.put("checkedEndpoint", config.safeEndpoint());
        result.put("type", config.type());
        result.put("testedAt", Instant.now().toString());
        result.put("correlationId", ctx.correlationId());
        return result;
    }

    private static long latencyMs(long started) {
        return Math.max(0, Duration.ofNanos(System.nanoTime() - started).toMillis());
    }

    private static String failurePrefix(String type) {
        return switch (type) {
            case "REDIS" -> "Redis connection failed: ";
            case "KAFKA" -> "Kafka connection failed: ";
            case "INDEX_STORAGE" -> "Index storage test failed: ";
            default -> "Infrastructure test failed: ";
        };
    }

    private static String safeMessage(Exception ex) {
        var message = String.valueOf(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())
                .replaceAll("(?i)(password|credential|secret|token|authorization|username|sasl.jaas.config)\\s*[:=]\\s*[^,;\\s]+", "$1=masked")
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim();
        return message.length() <= 220 ? message : message.substring(0, 217) + "...";
    }

    private static String firstNonBlank(Object... values) {
        for (var value : values) {
            var text = value == null ? "" : String.valueOf(value).trim();
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

}
