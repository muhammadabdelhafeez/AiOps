package org.kfh.aiops.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kfh.aiops.platform.exception.ServiceUnavailableException;
import org.springframework.mock.env.MockEnvironment;

class SecretCipherServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldEncryptAndDecryptConnectorSecretWithoutPlaintextInPayload() {
        var cipher = new SecretCipherService(new MockEnvironment().withProperty("kfh.security.master-key", "unit-master-key"));

        var payload = cipher.encrypt("plain-secret-value");
        var decrypted = cipher.decrypt(payload);

        assertThat(decrypted).isEqualTo("plain-secret-value");
        assertThat(payload.toString()).doesNotContain("plain-secret-value");
    }

    @Test
    void shouldReadMasterKeyFromDeploymentSecretFileWhenEnvironmentVariableIsNotVisible() throws Exception {
        var secretFile = tempDir.resolve("kfh-aiops-secret-key.txt");
        Files.writeString(secretFile, "file-backed-master-key\n");
        var environment = new MockEnvironment().withProperty("kfh.security.master-key-file", secretFile.toString());
        var cipher = new SecretCipherService(environment);

        var payload = cipher.encrypt("plain-secret-value");
        var decrypted = cipher.decrypt(payload);

        assertThat(decrypted).isEqualTo("plain-secret-value");
        assertThat(SecretCipherService.isMasterKeyConfigured(environment)).isTrue();
        assertThat(SecretCipherService.masterKeySource(environment)).contains("deployment secret file");
        assertThat(payload.toString()).doesNotContain("plain-secret-value", "file-backed-master-key");
    }

    @Test
    void shouldExplainRecoveryWhenSavedSecretWasEncryptedWithDifferentMasterKey() {
        var oldCipher = new SecretCipherService(new MockEnvironment().withProperty("kfh.security.master-key", "old-master-key"));
        var newCipher = new SecretCipherService(new MockEnvironment().withProperty("kfh.security.master-key", "new-master-key"));
        var payload = oldCipher.encrypt("plain-secret-value");

        assertThatThrownBy(() -> newCipher.decrypt(payload))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("configured platform master key does not match")
                .hasMessageContaining("Restore the original stable KFH_AIOPS_SECRET_KEY")
                .hasMessageContaining("re-enter all credentials");
    }

    @Test
    void shouldRequireMasterKeyBeforeEncryptingConnectorSecrets() {
        var cipher = new SecretCipherService(new MockEnvironment()
                .withProperty("kfh.security.master-key-file", tempDir.resolve("missing-secret-key.txt").toString()));

        assertThatThrownBy(() -> cipher.encrypt("plain-secret-value"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("KFH_AIOPS_SECRET_KEY")
                .hasMessageContaining("running backend JVM")
                .hasMessageContaining("restart")
                .hasMessageContaining("deployment secret file");
    }
}

