package org.aiopsanalysis.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility for encrypting/decrypting secrets using AES-256-GCM.
 *
 * SECURITY:
 * - Master key loaded from environment variable INSIGHT_MASTER_KEY
 * - Uses AES-256-GCM for authenticated encryption
 * - Random IV for each encryption operation
 * - Encrypted values prefixed with "$enc$" for identification
 */
@Component
public class CryptoUtil {

    private static final Logger log = LoggerFactory.getLogger(CryptoUtil.class);

    private static final String ENCRYPTION_PREFIX = "$enc$";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    public CryptoUtil(@Value("${aiops.crypto.master-key:#{null}}") String masterKeyBase64) {
        this.secureRandom = new SecureRandom();

        // Try environment variable first, then config
        String keySource = System.getenv("INSIGHT_MASTER_KEY");
        if (keySource == null || keySource.isEmpty()) {
            keySource = masterKeyBase64;
        }

        if (keySource == null || keySource.isEmpty()) {
            log.warn("INSIGHT_MASTER_KEY not set - using generated key (NOT FOR PRODUCTION)");
            // Generate a random key for development (NOT FOR PRODUCTION)
            byte[] keyBytes = new byte[32];
            secureRandom.nextBytes(keyBytes);
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
        } else {
            byte[] keyBytes = Base64.getDecoder().decode(keySource);
            if (keyBytes.length != 32) {
                throw new IllegalArgumentException("Master key must be 32 bytes (256 bits)");
            }
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
            log.info("CryptoUtil initialized with provided master key");
        }
    }

    /**
     * Encrypt a plaintext value.
     *
     * @param plaintext The value to encrypt
     * @return Encrypted value with $enc$ prefix
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }

        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            // Encrypt
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Combine IV + ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);

            // Encode to Base64 and add prefix
            return ENCRYPTION_PREFIX + Base64.getEncoder().encodeToString(byteBuffer.array());

        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypt an encrypted value.
     *
     * @param encrypted The encrypted value (with $enc$ prefix)
     * @return Decrypted plaintext
     */
    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) {
            return encrypted;
        }

        if (!encrypted.startsWith(ENCRYPTION_PREFIX)) {
            log.warn("Value is not encrypted (missing prefix) - returning as-is");
            return encrypted;
        }

        try {
            // Remove prefix and decode
            String base64 = encrypted.substring(ENCRYPTION_PREFIX.length());
            byte[] decoded = Base64.getDecoder().decode(base64);

            // Extract IV and ciphertext
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            // Decrypt
            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /**
     * Check if a value is encrypted.
     */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENCRYPTION_PREFIX);
    }

    /**
     * Mask a secret value for display.
     */
    public String mask(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return "••••••••";
    }
}
