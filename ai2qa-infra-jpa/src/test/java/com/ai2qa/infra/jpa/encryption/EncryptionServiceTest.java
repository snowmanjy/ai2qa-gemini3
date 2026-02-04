package com.ai2qa.infra.jpa.encryption;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for EncryptionService.
 */
class EncryptionServiceTest {

    // 32-byte key base64 encoded
    private static final String TEST_KEY = Base64.getEncoder().encodeToString(
            "test-encryption-key-32-bytes!!!!".getBytes()  // Exactly 32 bytes
    );

    @Test
    void shouldEncryptAndDecryptString() {
        // Given
        EncryptionService service = new EncryptionService(TEST_KEY);
        String plaintext = "This is a secret message";

        // When
        byte[] encrypted = service.encrypt(plaintext);
        String decrypted = service.decrypt(encrypted);

        // Then
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void shouldProduceDifferentCiphertextForSamePlaintext() {
        // GCM uses random IV, so same plaintext produces different ciphertext
        EncryptionService service = new EncryptionService(TEST_KEY);
        String plaintext = "Same message";

        // When
        byte[] encrypted1 = service.encrypt(plaintext);
        byte[] encrypted2 = service.encrypt(plaintext);

        // Then - Encrypted data should be different (due to random IV)
        assertThat(encrypted1).isNotEqualTo(encrypted2);

        // But both should decrypt to the same plaintext
        assertThat(service.decrypt(encrypted1)).isEqualTo(plaintext);
        assertThat(service.decrypt(encrypted2)).isEqualTo(plaintext);
    }

    @Test
    void shouldHandleNullPlaintext() {
        EncryptionService service = new EncryptionService(TEST_KEY);

        assertThatThrownBy(() -> service.encrypt(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldHandleNullCiphertext() {
        EncryptionService service = new EncryptionService(TEST_KEY);

        assertThatThrownBy(() -> service.decrypt(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldHandleEmptyString() {
        EncryptionService service = new EncryptionService(TEST_KEY);
        String plaintext = "";

        // When
        byte[] encrypted = service.encrypt(plaintext);
        String decrypted = service.decrypt(encrypted);

        // Then
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void shouldHandleUnicodeCharacters() {
        EncryptionService service = new EncryptionService(TEST_KEY);
        String plaintext = "Hello 世界 🌍 مرحبا";

        // When
        byte[] encrypted = service.encrypt(plaintext);
        String decrypted = service.decrypt(encrypted);

        // Then
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void shouldHandleLongStrings() {
        EncryptionService service = new EncryptionService(TEST_KEY);
        String plaintext = "A".repeat(10000);

        // When
        byte[] encrypted = service.encrypt(plaintext);
        String decrypted = service.decrypt(encrypted);

        // Then
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void shouldFailDecryptionWithWrongKey() {
        // Given - Encrypt with one key
        EncryptionService service1 = new EncryptionService(TEST_KEY);
        byte[] encrypted = service1.encrypt("secret");

        // Different 32-byte key
        String differentKey = Base64.getEncoder().encodeToString(
                "different-key-32-bytes-long!!!!!".getBytes()  // Exactly 32 bytes
        );
        EncryptionService service2 = new EncryptionService(differentKey);

        // When/Then - Should fail with authentication error
        assertThatThrownBy(() -> service2.decrypt(encrypted))
                .isInstanceOf(EncryptionService.EncryptionException.class);
    }

    @Test
    void shouldFailDecryptionWithTamperedData() {
        EncryptionService service = new EncryptionService(TEST_KEY);
        byte[] encrypted = service.encrypt("secret message");

        // Tamper with the ciphertext
        encrypted[encrypted.length - 1] ^= 0xFF;

        // When/Then - Should fail authentication
        assertThatThrownBy(() -> service.decrypt(encrypted))
                .isInstanceOf(EncryptionService.EncryptionException.class);
    }

    @Test
    void shouldFallbackToRandomKeyWhenInvalidLength() {
        String shortKey = Base64.getEncoder().encodeToString(
                "short".getBytes()
        );

        EncryptionService service = new EncryptionService(shortKey);

        byte[] encrypted = service.encrypt("test");
        String decrypted = service.decrypt(encrypted);

        assertThat(decrypted).isEqualTo("test");
    }

    @Test
    void shouldGenerateRandomKeyWhenNoneProvided() {
        // Given - No key provided (development mode)
        EncryptionService service = new EncryptionService(null);

        // When
        byte[] encrypted = service.encrypt("test");
        String decrypted = service.decrypt(encrypted);

        // Then - Should still work (but logs a warning)
        assertThat(decrypted).isEqualTo("test");
    }

    @Test
    void shouldGenerateRandomKeyWhenBlankProvided() {
        EncryptionService service = new EncryptionService("   ");

        byte[] encrypted = service.encrypt("test");
        String decrypted = service.decrypt(encrypted);

        assertThat(decrypted).isEqualTo("test");
    }

    @Test
    void shouldIncludeIvInOutput() {
        EncryptionService service = new EncryptionService(TEST_KEY);
        String plaintext = "test";

        // When
        byte[] encrypted = service.encrypt(plaintext);

        // Then - Output should include IV (12 bytes) + ciphertext + auth tag (16 bytes)
        // Minimum size: 12 (IV) + plaintext.length + 16 (auth tag)
        assertThat(encrypted.length).isGreaterThan(12 + 16);
    }
}
