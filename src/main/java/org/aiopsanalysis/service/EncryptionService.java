package org.aiopsanalysis.service;

import org.aiopsanalysis.config.CryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for encrypting and decrypting connector secrets.
 * Uses AES-256-GCM encryption via CryptoUtil.
 *
 * SECURITY:
 * - Never logs plaintext secrets
 * - Encrypts all sensitive fields before storage
 * - Decrypts only when needed for connector operations
 */
@Service
public class EncryptionService {

    private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);

    private final CryptoUtil cryptoUtil;

    // Fields that should be encrypted
    private static final String[] SENSITIVE_FIELDS = {
            "password", "apiKey", "token", "clientSecret", "accessSecretKey", "secret"
    };

    public EncryptionService(CryptoUtil cryptoUtil) {
        this.cryptoUtil = cryptoUtil;
    }

    /**
     * Encrypt all sensitive fields in the secrets map.
     *
     * @param secrets Map of secret key-value pairs (plaintext)
     * @return Map with sensitive values encrypted
     */
    public Map<String, Object> encryptSecrets(Map<String, String> secrets) {
        if (secrets == null || secrets.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Object> encrypted = new HashMap<>();

        for (Map.Entry<String, String> entry : secrets.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (value == null || value.isEmpty()) {
                continue;
            }

            if (isSensitiveField(key)) {
                // Encrypt sensitive fields
                String encryptedValue = cryptoUtil.encrypt(value);
                encrypted.put(key + "Enc", encryptedValue);
                log.debug("Encrypted field: {}", key);
            } else {
                // Keep non-sensitive fields as-is
                encrypted.put(key, value);
            }
        }

        return encrypted;
    }

    /**
     * Decrypt all encrypted fields in the secrets map.
     *
     * @param encryptedSecrets Map with encrypted values (from DB)
     * @return Map with decrypted values
     */
    public Map<String, Object> decryptSecrets(Map<String, Object> encryptedSecrets) {
        if (encryptedSecrets == null || encryptedSecrets.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Object> decrypted = new HashMap<>();

        for (Map.Entry<String, Object> entry : encryptedSecrets.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value == null) {
                continue;
            }

            String stringValue = value.toString();

            if (key.endsWith("Enc") && cryptoUtil.isEncrypted(stringValue)) {
                // Decrypt encrypted fields and remove Enc suffix
                String originalKey = key.substring(0, key.length() - 3);
                String decryptedValue = cryptoUtil.decrypt(stringValue);
                decrypted.put(originalKey, decryptedValue);
                log.debug("Decrypted field: {}", originalKey);
            } else if (cryptoUtil.isEncrypted(stringValue)) {
                // Decrypt but keep original key
                String decryptedValue = cryptoUtil.decrypt(stringValue);
                decrypted.put(key, decryptedValue);
            } else {
                // Keep non-encrypted fields as-is
                decrypted.put(key, stringValue);
            }
        }

        return decrypted;
    }

    /**
     * Create a masked version of secrets for display/logging.
     *
     * @param secrets Map of secrets
     * @return Map with sensitive values masked
     */
    public Map<String, String> maskSecrets(Map<String, Object> secrets) {
        if (secrets == null || secrets.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, String> masked = new HashMap<>();

        for (Map.Entry<String, Object> entry : secrets.entrySet()) {
            String key = entry.getKey();

            // Remove Enc suffix for display
            String displayKey = key.endsWith("Enc") ? key.substring(0, key.length() - 3) : key;

            if (isSensitiveField(displayKey) || key.endsWith("Enc")) {
                masked.put(displayKey, "••••••••");
            } else {
                masked.put(displayKey, entry.getValue() != null ? entry.getValue().toString() : "");
            }
        }

        return masked;
    }

    /**
     * Decrypt a single secret field.
     *
     * @param encryptedValue Encrypted value string
     * @return Decrypted plaintext value
     */
    public String decryptSingleSecret(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isEmpty()) {
            return encryptedValue;
        }
        return cryptoUtil.decrypt(encryptedValue);
    }

    /**
     * Encrypt a single secret value.
     *
     * @param plainValue Plaintext value
     * @return Encrypted value string
     */
    public String encryptSingleSecret(String plainValue) {
        if (plainValue == null || plainValue.isEmpty()) {
            return plainValue;
        }
        return cryptoUtil.encrypt(plainValue);
    }

    /**
     * Check if a field name represents a sensitive field.
     */
    private boolean isSensitiveField(String fieldName) {
        if (fieldName == null) return false;

        String lowerField = fieldName.toLowerCase();
        for (String sensitive : SENSITIVE_FIELDS) {
            if (lowerField.contains(sensitive.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a value is encrypted.
     */
    public boolean isEncrypted(String value) {
        return cryptoUtil.isEncrypted(value);
    }
}
