package org.kfh.aiops.platform.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.kfh.aiops.platform.exception.ServiceUnavailableException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/** Encrypts connector and integration secrets with the configured platform master key. */
@Service
public class SecretCipherService {

    private static final String PROPERTY = "kfh.security.master-key";
    private static final String FILE_PROPERTY = "kfh.security.master-key-file";
    private static final String ENV_VAR = "KFH_AIOPS_SECRET_KEY";
    private static final String FILE_ENV_VAR = "KFH_AIOPS_SECRET_KEY_FILE";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final Environment environment;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretCipherService(Environment environment) {
        this.environment = environment;
    }

    public Map<String, Object> encrypt(String plaintext) {
        try {
            var iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            var cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            var encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            var payload = new LinkedHashMap<String, Object>();
            payload.put("algorithm", ALGORITHM);
            payload.put("iv", Base64.getEncoder().encodeToString(iv));
            payload.put("ciphertext", Base64.getEncoder().encodeToString(encrypted));
            return payload;
        } catch (ServiceUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceUnavailableException("SECRET_ENCRYPTION_FAILED", "Connector secret encryption failed");
        }
    }

    public String decrypt(Map<String, Object> payload) {
        try {
            var cipher = Cipher.getInstance(ALGORITHM);
            var iv = Base64.getDecoder().decode(String.valueOf(payload.get("iv")));
            var encrypted = Base64.getDecoder().decode(String.valueOf(payload.get("ciphertext")));
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (ServiceUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceUnavailableException("SECRET_DECRYPTION_FAILED",
                    "Connector secret decryption failed. The configured platform master key does not match the key "
                            + "used when these connector credentials were saved, or the encrypted payload is corrupt. "
                            + "Restore the original stable KFH_AIOPS_SECRET_KEY/deployment secret file, or re-enter all "
                            + "credentials for this connector so they are encrypted with the current key.");
        }
    }

    public static boolean isMasterKeyConfigured(Environment environment) {
        return !resolveMasterKey(environment, false).value().isBlank();
    }

    public static String masterKeySource(Environment environment) {
        return resolveMasterKey(environment, false).source();
    }

    private SecretKeySpec key() throws Exception {
        var masterKey = resolveMasterKey(environment, true).value();
        if (masterKey.isBlank()) {
            throw new ServiceUnavailableException("SECRET_MASTER_KEY_MISSING",
                    "KFH_AIOPS_SECRET_KEY is not visible to the running backend JVM. Set it before starting the "
                            + "application in the same terminal, service, or IDE run configuration, then restart; "
                            + "or place the stable key in the configured deployment secret file "
                            + "(kfh.security.master-key-file / KFH_AIOPS_SECRET_KEY_FILE, default "
                            + "user-home .kfh-aiops/secret-key.txt) before storing or testing connector secrets.");
        }
        var digest = MessageDigest.getInstance("SHA-256").digest(masterKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }

    private static ResolvedMasterKey resolveMasterKey(Environment environment, boolean failOnConfiguredFileProblem) {
        var configured = text(environment == null ? null : environment.getProperty(PROPERTY));
        if (!configured.isBlank()) {
            return new ResolvedMasterKey(configured, "startup property kfh.security.master-key / environment KFH_AIOPS_SECRET_KEY");
        }
        var processEnv = text(System.getenv(ENV_VAR));
        if (!processEnv.isBlank()) {
            return new ResolvedMasterKey(processEnv, "process environment KFH_AIOPS_SECRET_KEY");
        }
        return resolveMasterKeyFile(environment, failOnConfiguredFileProblem);
    }

    private static ResolvedMasterKey resolveMasterKeyFile(Environment environment, boolean failOnConfiguredFileProblem) {
        var file = masterKeyFile(environment);
        if (file == null || !Files.exists(file.path())) {
            return new ResolvedMasterKey("", file == null || !file.configured()
                    ? "not configured"
                    : "deployment secret file configured but missing");
        }
        if (!Files.isRegularFile(file.path())) {
            return failOrEmpty(file, "deployment secret file configured but not a regular file",
                    failOnConfiguredFileProblem);
        }
        try {
            var value = Files.readString(file.path(), StandardCharsets.UTF_8).trim();
            if (value.isBlank()) {
                return failOrEmpty(file, "deployment secret file configured but blank", failOnConfiguredFileProblem);
            }
            return new ResolvedMasterKey(value, file.configured()
                    ? "deployment secret file kfh.security.master-key-file / KFH_AIOPS_SECRET_KEY_FILE"
                    : "default local deployment secret file user-home .kfh-aiops/secret-key.txt");
        } catch (IOException ex) {
            return failOrEmpty(file, "deployment secret file configured but unreadable", failOnConfiguredFileProblem);
        }
    }

    private static ResolvedMasterKey failOrEmpty(MasterKeyFile file, String source, boolean fail) {
        if (fail && file.configured()) {
            throw new ServiceUnavailableException("SECRET_MASTER_KEY_FILE_UNREADABLE",
                    "The configured secret master key file cannot be read or is blank. Fix the deployment secret file "
                            + "referenced by kfh.security.master-key-file / KFH_AIOPS_SECRET_KEY_FILE, then retry "
                            + "connector Save/Test without logging or returning the key value.");
        }
        return new ResolvedMasterKey("", source);
    }

    private static MasterKeyFile masterKeyFile(Environment environment) {
        var configured = text(environment == null ? null : environment.getProperty(FILE_PROPERTY));
        if (configured.isBlank()) {
            configured = text(System.getenv(FILE_ENV_VAR));
        }
        if (!configured.isBlank()) {
            return new MasterKeyFile(expandHome(configured), true);
        }
        var home = text(System.getProperty("user.home"));
        return home.isBlank() ? null : new MasterKeyFile(Path.of(home, ".kfh-aiops", "secret-key.txt"), false);
    }

    private static Path expandHome(String rawPath) {
        var value = text(rawPath);
        var home = text(System.getProperty("user.home"));
        if (home.isBlank()) {
            return Path.of(value);
        }
        if ("~".equals(value)) {
            return Path.of(home);
        }
        if (value.startsWith("~/") || value.startsWith("~\\")) {
            return Path.of(home, value.substring(2));
        }
        return Path.of(value);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private record ResolvedMasterKey(String value, String source) {
    }

    private record MasterKeyFile(Path path, boolean configured) {
    }
}

