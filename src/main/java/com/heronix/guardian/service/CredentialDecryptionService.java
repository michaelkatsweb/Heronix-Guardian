package com.heronix.guardian.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.heronix.guardian.config.GuardianProperties;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles encryption and decryption of vendor credentials using AES-256-GCM.
 *
 * Encrypted values are stored as Base64-encoded strings containing:
 * [12-byte IV][ciphertext][16-byte auth tag]
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CredentialDecryptionService {

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128; // bits

    private final GuardianProperties properties;
    private SecretKey secretKey;

    @PostConstruct
    void init() {
        // Prefer unified HERONIX_MASTER_KEY; fall back to legacy GUARDIAN_MASTER_KEY
        com.heronix.guardian.security.HeronixEncryptionService enc =
            com.heronix.guardian.security.HeronixEncryptionService.getInstance();

        if (!enc.isDisabled()) {
            // Derive credential key from the unified Heronix master key via PBKDF2
            try {
                String masterPassphrase = System.getenv("HERONIX_MASTER_KEY");
                if (masterPassphrase != null && !masterPassphrase.isBlank()) {
                    javax.crypto.SecretKeyFactory factory =
                        javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                    javax.crypto.spec.PBEKeySpec spec = null;
                    try {
                        spec = new javax.crypto.spec.PBEKeySpec(
                            masterPassphrase.toCharArray(),
                            "HeronixGuardian-Cred-Salt".getBytes(StandardCharsets.UTF_8),
                            100_000, 256);
                        byte[] derived = factory.generateSecret(spec).getEncoded();
                        this.secretKey = new SecretKeySpec(derived, "AES");
                        log.info("Credential encryption service initialized from HERONIX_MASTER_KEY");
                        return;
                    } finally {
                        if (spec != null) spec.clearPassword();
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to derive credential key from HERONIX_MASTER_KEY, falling back", e);
            }
        }

        // Legacy fallback: use GUARDIAN_MASTER_KEY from properties
        String masterKey = properties.getEncryption().getMasterKey();
        if (masterKey == null || masterKey.isBlank()) {
            log.error("Neither HERONIX_MASTER_KEY nor GUARDIAN_MASTER_KEY is set! Credential encryption/decryption will fail.");
            return;
        }

        byte[] keyBytes = new byte[32];
        byte[] rawBytes = masterKey.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(rawBytes, 0, keyBytes, 0, Math.min(rawBytes.length, 32));
        this.secretKey = new SecretKeySpec(keyBytes, "AES");

        log.info("Credential encryption service initialized from GUARDIAN_MASTER_KEY (legacy)");
    }

    /**
     * Decrypt an encrypted credential value.
     * Returns the plaintext string, or null if the input is null/empty.
     *
     * @throws IllegalStateException if decryption fails
     */
    public String decrypt(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            return null;
        }
        if (secretKey == null) {
            throw new IllegalStateException("Encryption key not initialized. Set GUARDIAN_MASTER_KEY.");
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedValue);

            // Extract IV from the beginning
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt credential", e);
        }
    }

    /**
     * Encrypt a plaintext credential value for storage.
     * Returns a Base64-encoded string containing [IV][ciphertext+tag].
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        if (secretKey == null) {
            throw new IllegalStateException("Encryption key not initialized. Set GUARDIAN_MASTER_KEY.");
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(buffer.array());

        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt credential", e);
        }
    }
}
