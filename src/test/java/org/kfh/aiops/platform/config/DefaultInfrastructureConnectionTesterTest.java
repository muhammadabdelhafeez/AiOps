package org.kfh.aiops.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.platform.tenant.TenantContext;

class DefaultInfrastructureConnectionTesterTest {

    private final DefaultInfrastructureConnectionTester tester = new DefaultInfrastructureConnectionTester();

    @Test
    void shouldBlockRedisLocalhostBeforeOpeningSocket() {
        var result = tester.test(context(), "infrastructure.connections.preview", Map.of(
                "type", "REDIS",
                "endpoint", "localhost",
                "port", 6379));

        assertThat(result)
                .containsEntry("status", "Fail")
                .containsEntry("pass", false);
        assertThat(result.get("message").toString()).contains("SSRF protection");
    }

    @Test
    void shouldBlockKafkaUrlSyntaxBeforeOpeningAdminClient() {
        var result = tester.test(context(), "infrastructure.connections.preview", Map.of(
                "type", "KAFKA",
                "endpoint", "http://localhost:9092",
                "protocol", "PLAINTEXT"));

        assertThat(result)
                .containsEntry("status", "Fail")
                .containsEntry("pass", false);
        assertThat(result.get("message").toString()).contains("host:port entries without URL syntax");
    }

    @Test
    void shouldRejectRelativeIndexStoragePath() {
        var result = tester.test(context(), "infrastructure.connections.preview", Map.of(
                "type", "INDEX_STORAGE",
                "provider", "LOCAL",
                "endpoint", "relative/path"));

        assertThat(result)
                .containsEntry("status", "Fail")
                .containsEntry("pass", false);
        assertThat(result.get("message").toString()).contains("must be absolute");
    }

    @Test
    void shouldRejectIndexStoragePathTraversalSegments() {
        var traversalPath = Path.of(System.getProperty("java.io.tmpdir")).resolve("..").resolve("aiops-index");

        var result = tester.test(context(), "infrastructure.connections.preview", Map.of(
                "type", "INDEX_STORAGE",
                "provider", "LOCAL",
                "endpoint", traversalPath.toString()));

        assertThat(result)
                .containsEntry("status", "Fail")
                .containsEntry("pass", false);
        assertThat(result.get("message").toString()).contains("traversal segments");
    }

    @Test
    void shouldValidateObjectStoragePointerWithoutResolvingBucketNames() {
        var result = tester.test(context(), "infrastructure.connections.preview", Map.of(
                "type", "INDEX_STORAGE",
                "provider", "S3",
                "endpoint", "s3://kfh-aiops-index/kw/prod"));

        assertThat(result)
                .containsEntry("status", "Pass")
                .containsEntry("pass", true);
        assertThat(result.get("message").toString()).contains("metadata validation passed");
    }

    @Test
    void shouldBlockHttpsObjectStorageMetadataEndpoint() {
        var result = tester.test(context(), "infrastructure.connections.preview", Map.of(
                "type", "INDEX_STORAGE",
                "provider", "S3",
                "endpoint", "https://169.254.169.254/latest/meta-data"));

        assertThat(result)
                .containsEntry("status", "Fail")
                .containsEntry("pass", false);
        assertThat(result.get("message").toString()).contains("SSRF protection");
    }

    @Test
    void shouldValidateFilesystemFamilyProvidersAsRealDirectory() {
        var writableDir = System.getProperty("java.io.tmpdir"); // exists + readable + writable
        for (var provider : java.util.List.of("NFS", "SMB", "PVC")) {
            var result = tester.test(context(), "infrastructure.connections.preview", Map.of(
                    "type", "INDEX_STORAGE",
                    "provider", provider,
                    "endpoint", writableDir));

            assertThat(result).as("provider %s", provider)
                    .containsEntry("status", "Pass")
                    .containsEntry("pass", true);
            assertThat(result.get("message").toString()).contains("readable and writable");
        }
    }

    @Test
    void shouldRetryRedisAuthWithoutUsernameWhenDefaultAclUserFails() {
        assertThat(DefaultInfrastructureConnectionTester.shouldRetryRedisAuthWithoutUsername(
                "default", "-ERR wrong number of arguments for 'auth' command"))
                .isTrue();
        assertThat(DefaultInfrastructureConnectionTester.shouldRetryRedisAuthWithoutUsername(
                "default", "-WRONGPASS invalid username-password pair or user is disabled."))
                .isTrue();
    }

    @Test
    void shouldNotRetryRedisAuthWithoutUsernameForNamedAclUsers() {
        assertThat(DefaultInfrastructureConnectionTester.shouldRetryRedisAuthWithoutUsername(
                "svc-aiops", "-WRONGPASS invalid username-password pair or user is disabled."))
                .isFalse();
    }

    @Test
    void shouldReturnClearRedisAuthenticationGuidanceWhenPasswordIsWrong() {
        var message = DefaultInfrastructureConnectionTester.redisAuthenticationFailure(
                "-WRONGPASS invalid username-password pair or user is disabled.").getMessage();
        assertThat(message)
                .contains("Redis authentication failed")
                .contains("leave username blank unless your Redis server uses ACL users")
                .contains("Server reply:")
                .contains("WRONGPASS");
    }

    @Test
    void shouldDescribeProtectedModeRedisPingFailureWithServerReply() {
        var message = DefaultInfrastructureConnectionTester.describeRedisPingFailure(
                "-DENIED Redis is running in protected mode because protected mode is enabled.", false);
        assertThat(message)
                .contains("protected mode")
                .contains("Server reply:")
                .contains("DENIED");
    }

    @Test
    void shouldDescribeNoAuthRedisPingFailureWithServerReply() {
        var message = DefaultInfrastructureConnectionTester.describeRedisPingFailure(
                "-NOAUTH Authentication required.", false);
        assertThat(message)
                .contains("Redis requires authentication")
                .contains("Server reply:")
                .contains("NOAUTH");
    }

    @Test
    void shouldHintAtTlsOrProtectedModeWhenRedisRepliesAreEmptyOverPlainSocket() {
        var message = DefaultInfrastructureConnectionTester.describeRedisPingFailure("", false);
        assertThat(message)
                .contains("empty response")
                .contains("TLS")
                .contains("protected mode");
    }

    @Test
    void shouldHintAtCredentialsWhenRedisRepliesAreEmptyOverTls() {
        var message = DefaultInfrastructureConnectionTester.describeRedisPingFailure("", true);
        assertThat(message)
                .contains("empty response")
                .contains("TLS handshake succeeded");
    }

    @Test
    void shouldReturnFailResultInsteadOfThrowingWhenPayloadContainsNullValues() {
        // Simulates the real UI payload where a freshly created connector draft still carries
        // "lastTest": null. Map.copyOf would throw NPE here, so the tester must defend the caller
        // and produce a clean Fail response.
        var payload = new HashMap<String, Object>();
        payload.put("type", "REDIS");
        payload.put("endpoint", "redis.example.internal");
        payload.put("port", 6379);
        payload.put("username", "");
        payload.put("secret", "");
        payload.put("lastTest", null);
        payload.put("checkedEndpoint", null);

        var result = tester.test(context(), "infrastructure.connections.preview", payload);

        assertThat(result)
                .containsEntry("status", "Fail")
                .containsEntry("pass", false);
        // Either the SSRF/hostname resolution rejects it, or it just fails to connect, but in
        // either case the response must be structured and never bubble up as HTTP 500.
        assertThat(result.get("message")).isNotNull();
    }

    private static TenantContext context() {
        return new TenantContext(UUID.fromString("00000000-0000-4000-8000-000000000001"),
                UUID.fromString("00000000-0000-4000-8000-000000000101"), "KW", "PROD", "settings-infra-test",
                Set.of("SETTINGS_READ", "SETTINGS_WRITE", "AUDIT_READ"));
    }
}
