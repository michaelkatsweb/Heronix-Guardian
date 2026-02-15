package com.heronix.guardian.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for HeronixEncryptionService — verifies the Unified Heronix
 * Encryption Protocol (AES-256-GCM, PBKDF2, .heronix file format).
 */
class HeronixEncryptionServiceTest {

    private static final String TEST_PASSPHRASE = "test-master-key-for-unit-tests";

    @AfterEach
    void tearDown() {
        HeronixEncryptionService.reset();
    }

    // ── Initialization ──────────────────────────────────────────────────

    @Test
    void testInitializeWithPassphrase() {
        HeronixEncryptionService.initialize(TEST_PASSPHRASE);
        HeronixEncryptionService svc = HeronixEncryptionService.getInstance();

        assertThat(svc).isNotNull();
        assertThat(svc.isDisabled()).isFalse();
    }

    @Test
    void testInitializeIdempotent() {
        HeronixEncryptionService.initialize(TEST_PASSPHRASE);
        assertThatCode(() -> HeronixEncryptionService.initialize(TEST_PASSPHRASE))
                .doesNotThrowAnyException();
    }

    @Test
    void testGetInstanceBeforeInitialize() {
        assertThatThrownBy(HeronixEncryptionService::getInstance)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not initialized");
    }

    @Test
    void testEmptyPassphraseThrows() {
        assertThatThrownBy(() -> HeronixEncryptionService.initialize(""))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── Raw encrypt / decrypt ───────────────────────────────────────────

    @Test
    void testEncryptDecryptRoundTrip() {
        HeronixEncryptionService.initialize(TEST_PASSPHRASE);
        HeronixEncryptionService svc = HeronixEncryptionService.getInstance();

        byte[] original = "Hello, Heronix!".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = svc.encrypt(original);
        byte[] decrypted = svc.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void testEncryptProducesDifferentCiphertext() {
        HeronixEncryptionService.initialize(TEST_PASSPHRASE);
        HeronixEncryptionService svc = HeronixEncryptionService.getInstance();

        byte[] data = "same data".getBytes(StandardCharsets.UTF_8);
        byte[] enc1 = svc.encrypt(data);
        byte[] enc2 = svc.encrypt(data);

        assertThat(enc1).isNotEqualTo(enc2);
    }

    @Test
    void testDecryptWithWrongKeyFails() {
        HeronixEncryptionService.initialize(TEST_PASSPHRASE);
        byte[] encrypted = HeronixEncryptionService.getInstance()
                .encrypt("secret".getBytes(StandardCharsets.UTF_8));

        HeronixEncryptionService.reset();
        HeronixEncryptionService.initialize("completely-different-key");

        assertThatThrownBy(() -> HeronixEncryptionService.getInstance().decrypt(encrypted))
                .isInstanceOf(RuntimeException.class);
    }

    // ── String (Base64) encrypt / decrypt ───────────────────────────────

    @Test
    void testEncryptToBase64RoundTrip() {
        HeronixEncryptionService.initialize(TEST_PASSPHRASE);
        HeronixEncryptionService svc = HeronixEncryptionService.getInstance();

        String original = "Student transcript data — 2026";
        String encrypted = svc.encryptToBase64(original);
        String decrypted = svc.decryptFromBase64(encrypted);

        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void testEncryptToBase64ProducesValidBase64() {
        HeronixEncryptionService.initialize(TEST_PASSPHRASE);
        HeronixEncryptionService svc = HeronixEncryptionService.getInstance();

        String encrypted = svc.encryptToBase64("test data");

        assertThatCode(() -> Base64.getDecoder().decode(encrypted))
                .doesNotThrowAnyException();
        assertThat(Base64.getDecoder().decode(encrypted)).isNotEmpty();
    }

    // ── .heronix file format ────────────────────────────────────────────

    @Test
    void testEncryptFileRoundTrip() {
        HeronixEncryptionService.initialize(TEST_PASSPHRASE);
        HeronixEncryptionService svc = HeronixEncryptionService.getInstance();

        byte[] content = "PDF file content here".getBytes(StandardCharsets.UTF_8);
        String fileName = "transcript.pdf";

        byte[] heronixBytes = svc.encryptFile(content, fileName);
        HeronixEncryptionService.DecryptedFile result = svc.decryptFile(heronixBytes);

        assertThat(result.getOriginalName()).isEqualTo(fileName);
        assertThat(result.getContent()).isEqualTo(content);
    }

    @Test
    void testHeronixFileMagicBytes() {
        HeronixEncryptionService.initialize(TEST_PASSPHRASE);
        HeronixEncryptionService svc = HeronixEncryptionService.getInstance();

        byte[] heronixBytes = svc.encryptFile("data".getBytes(StandardCharsets.UTF_8), "file.txt");

        assertThat(heronixBytes[0]).isEqualTo((byte) 'H');
        assertThat(heronixBytes[1]).isEqualTo((byte) 'R');
        assertThat(heronixBytes[2]).isEqualTo((byte) 'N');
        assertThat(heronixBytes[3]).isEqualTo((byte) 'X');
        assertThat(heronixBytes[4]).isEqualTo((byte) 0x01);
    }

    @Test
    void testDecryptFileBadMagicThrows() {
        HeronixEncryptionService.initialize(TEST_PASSPHRASE);
        HeronixEncryptionService svc = HeronixEncryptionService.getInstance();

        byte[] garbage = "This is not a heronix file".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> svc.decryptFile(garbage))
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("bad magic");
    }

    // ── H2 file password ────────────────────────────────────────────────

    @Test
    void testH2FilePasswordDeterministic() {
        HeronixEncryptionService.initialize(TEST_PASSPHRASE);
        String pw1 = HeronixEncryptionService.getInstance().getH2FilePassword();

        HeronixEncryptionService.reset();
        HeronixEncryptionService.initialize(TEST_PASSPHRASE);
        String pw2 = HeronixEncryptionService.getInstance().getH2FilePassword();

        assertThat(pw1).isNotEmpty();
        assertThat(pw1).isEqualTo(pw2);
    }
}
