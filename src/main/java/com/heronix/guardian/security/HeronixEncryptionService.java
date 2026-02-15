package com.heronix.guardian.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * Heronix Unified Encryption Service — singleton utility (not Spring-managed).
 *
 * Provides a single encryption protocol so that no machine without the Heronix
 * master key can read any data — databases, exports, or cached files.
 *
 * @author Heronix Educational Systems LLC
 * @since 2026-02
 */
public final class HeronixEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int PBKDF2_ITERATIONS = 100_000;
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";

    private static final byte[] DB_SALT = "HeronixDB-AES-Salt".getBytes(StandardCharsets.UTF_8);
    private static final byte[] DATA_SALT = "HeronixData-AES-Salt".getBytes(StandardCharsets.UTF_8);

    private static final byte[] MAGIC = {'H', 'R', 'N', 'X'};
    private static final byte VERSION = 0x01;

    private static final String ENV_MASTER_KEY = "HERONIX_MASTER_KEY";
    private static final String ENV_DISABLED = "HERONIX_ENCRYPTION_DISABLED";

    private static HeronixEncryptionService INSTANCE;

    private final SecretKey dataKey;
    private final String h2FilePassword;
    private final boolean disabled;
    private final SecureRandom secureRandom = new SecureRandom();

    private HeronixEncryptionService(String passphrase) {
        if (passphrase == null || passphrase.isBlank()) {
            throw new IllegalStateException(
                "HERONIX_MASTER_KEY is empty. Set the environment variable or set HERONIX_ENCRYPTION_DISABLED=true for development.");
        }
        this.disabled = false;
        this.dataKey = deriveKey(passphrase, DATA_SALT, 256);
        SecretKey dbKey = deriveKey(passphrase, DB_SALT, 128);
        this.h2FilePassword = bytesToHex(dbKey.getEncoded());
    }

    private HeronixEncryptionService(boolean disabled) {
        this.disabled = true;
        this.dataKey = null;
        this.h2FilePassword = "";
    }

    public static synchronized void initialize() {
        if (INSTANCE != null) return;

        String disabled = System.getenv(ENV_DISABLED);
        if ("true".equalsIgnoreCase(disabled)) {
            INSTANCE = new HeronixEncryptionService(true);
            System.out.println("[HeronixEncryption] WARNING: Encryption is DISABLED (dev mode).");
            return;
        }

        String passphrase = System.getenv(ENV_MASTER_KEY);
        if (passphrase == null || passphrase.isBlank()) {
            throw new IllegalStateException(
                "\n\n" +
                "╔══════════════════════════════════════════════════════════════╗\n" +
                "║  HERONIX SECURITY ERROR                                     ║\n" +
                "╠══════════════════════════════════════════════════════════════╣\n" +
                "║  HERONIX_MASTER_KEY environment variable is not set.        ║\n" +
                "║                                                              ║\n" +
                "║  Every Heronix machine must have a master encryption key.    ║\n" +
                "║  Contact your IT administrator to set HERONIX_MASTER_KEY.    ║\n" +
                "║                                                              ║\n" +
                "║  For DEVELOPMENT ONLY, set:                                  ║\n" +
                "║    HERONIX_ENCRYPTION_DISABLED=true                          ║\n" +
                "╚══════════════════════════════════════════════════════════════╝\n");
        }

        INSTANCE = new HeronixEncryptionService(passphrase);
        System.out.println("[HeronixEncryption] Encryption initialized successfully.");
    }

    public static synchronized void initialize(String passphrase) {
        if (INSTANCE != null) return;
        INSTANCE = new HeronixEncryptionService(passphrase);
    }

    public static HeronixEncryptionService getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException(
                "HeronixEncryptionService not initialized. Call initialize() before SpringApplication.run().");
        }
        return INSTANCE;
    }

    public boolean isDisabled() {
        return disabled;
    }

    static synchronized void reset() {
        INSTANCE = null;
    }

    public byte[] encrypt(byte[] plaintext) {
        if (disabled) return plaintext;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, dataKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);

            byte[] result = new byte[GCM_IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, GCM_IV_LENGTH);
            System.arraycopy(ciphertext, 0, result, GCM_IV_LENGTH, ciphertext.length);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public byte[] decrypt(byte[] data) {
        if (disabled) return data;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(data, 0, iv, 0, GCM_IV_LENGTH);

            byte[] ciphertext = new byte[data.length - GCM_IV_LENGTH];
            System.arraycopy(data, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, dataKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed — wrong key or corrupted data", e);
        }
    }

    public String encryptToBase64(String plaintext) {
        if (disabled) return plaintext;
        byte[] encrypted = encrypt(plaintext.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public String decryptFromBase64(String base64Ciphertext) {
        if (disabled) return base64Ciphertext;
        byte[] encrypted = Base64.getDecoder().decode(base64Ciphertext);
        byte[] decrypted = decrypt(encrypted);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    public byte[] encryptFile(byte[] source, String originalName) {
        if (disabled) return source;
        try {
            byte[] nameBytes = originalName.getBytes(StandardCharsets.UTF_8);
            if (nameBytes.length > 65535) {
                throw new IllegalArgumentException("Original filename too long (max 65535 UTF-8 bytes)");
            }

            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, dataKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(source);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(MAGIC);
            out.write(VERSION);
            out.write(ByteBuffer.allocate(2).putShort((short) nameBytes.length).array());
            out.write(nameBytes);
            out.write(iv);
            out.write(ciphertext);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("File encryption failed", e);
        }
    }

    public DecryptedFile decryptFile(byte[] heronixBytes) {
        if (disabled) return new DecryptedFile("unknown", heronixBytes);
        try {
            ByteBuffer buf = ByteBuffer.wrap(heronixBytes);

            byte[] magic = new byte[4];
            buf.get(magic);
            if (magic[0] != 'H' || magic[1] != 'R' || magic[2] != 'N' || magic[3] != 'X') {
                throw new IllegalArgumentException("Not a valid .heronix file (bad magic bytes)");
            }

            byte version = buf.get();
            if (version != VERSION) {
                throw new IllegalArgumentException("Unsupported .heronix version: " + version);
            }

            short nameLen = buf.getShort();
            byte[] nameBytes = new byte[nameLen & 0xFFFF];
            buf.get(nameBytes);
            String originalName = new String(nameBytes, StandardCharsets.UTF_8);

            byte[] iv = new byte[GCM_IV_LENGTH];
            buf.get(iv);

            byte[] ciphertext = new byte[buf.remaining()];
            buf.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, dataKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] content = cipher.doFinal(ciphertext);

            return new DecryptedFile(originalName, content);
        } catch (Exception e) {
            throw new RuntimeException("File decryption failed — wrong key or corrupted .heronix file", e);
        }
    }

    public String getH2FilePassword() {
        return h2FilePassword;
    }

    public static class DecryptedFile {
        private final String originalName;
        private final byte[] content;

        public DecryptedFile(String originalName, byte[] content) {
            this.originalName = originalName;
            this.content = content;
        }

        public String getOriginalName() { return originalName; }
        public byte[] getContent() { return content; }
    }

    private static SecretKey deriveKey(String passphrase, byte[] salt, int keyLengthBits) {
        try {
            KeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, keyLengthBits);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new RuntimeException("Key derivation failed", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
