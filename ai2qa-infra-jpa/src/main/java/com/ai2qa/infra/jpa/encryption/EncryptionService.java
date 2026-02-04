package com.ai2qa.infra.jpa.encryption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Service for AES-256-GCM encryption of sensitive data.
 *
 * <p>Uses GCM mode for authenticated encryption with associated data (AEAD).
 */
@Service
public class EncryptionService {

    private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits recommended for GCM
    private static final int GCM_TAG_LENGTH = 128; // 128 bits authentication tag

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    public EncryptionService(
            @Value("${ai2qa.encryption.key:${ai2qa.cache.encryption-key:}}") String configuredKey
    ) {
        Optional<byte[]> resolvedKey = resolveKey(configuredKey);
        if (resolvedKey.isPresent()) {
            this.secretKey = new SecretKeySpec(resolvedKey.get(), "AES");
        } else {
            log.warn("No valid encryption key configured. Generating random key. DO NOT USE IN PRODUCTION!");
            byte[] keyBytes = new byte[32];
            new SecureRandom().nextBytes(keyBytes);
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
        }
        this.secureRandom = new SecureRandom();
    }

    private Optional<byte[]> resolveKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }

        String trimmed = key.trim();
        try {
            byte[] decoded = Base64.getDecoder().decode(trimmed);
            if (decoded.length == 32) {
                return Optional.of(decoded);
            }
        } catch (IllegalArgumentException ignored) {
            // Not base64, try raw bytes
        }

        byte[] raw = trimmed.getBytes(StandardCharsets.UTF_8);
        if (raw.length == 32) {
            return Optional.of(raw);
        }

        log.warn("Invalid encryption key length: {} bytes", raw.length);
        return Optional.empty();
    }

    /**
     * Encrypts a string value.
     *
     * @param plaintext The value to encrypt
     * @return Encrypted bytes (IV + ciphertext + auth tag)
     */
    public byte[] encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("Plaintext must not be null");
        }

        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            // Encrypt
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Combine IV + ciphertext
            ByteBuffer buffer = ByteBuffer.allocate(GCM_IV_LENGTH + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            return buffer.array();
        } catch (Exception e) {
            throw new EncryptionException("Failed to encrypt value", e);
        }
    }

    /**
     * Decrypts encrypted bytes back to a string.
     *
     * @param encrypted The encrypted bytes (IV + ciphertext + auth tag)
     * @return Decrypted plaintext
     */
    public String decrypt(byte[] encrypted) {
        if (encrypted == null) {
            throw new IllegalArgumentException("Encrypted value must not be null");
        }

        try {
            // Extract IV
            ByteBuffer buffer = ByteBuffer.wrap(encrypted);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);

            // Extract ciphertext
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            // Decrypt
            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new EncryptionException("Failed to decrypt value", e);
        }
    }

    /**
     * Exception for encryption/decryption errors.
     */
    public static class EncryptionException extends RuntimeException {
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
