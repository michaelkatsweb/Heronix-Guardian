package com.heronix.guardian.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Configuration properties for Heronix Guardian.
 */
@Data
@ConfigurationProperties(prefix = "heronix.guardian")
public class GuardianProperties {

    /**
     * Use SIS tokenization instead of local Guardian tokens.
     * When true, SisTokenBridgeService is activated and delegates to SIS-Server.
     */
    private boolean useSisTokenization = false;

    /**
     * Gateway integration configuration
     */
    private GatewayConfig gateway = new GatewayConfig();

    /**
     * Token configuration
     */
    private TokenConfig token = new TokenConfig();

    /**
     * Encryption configuration
     */
    private EncryptionConfig encryption = new EncryptionConfig();

    /**
     * SIS integration configuration
     */
    private SisConfig sis = new SisConfig();

    /**
     * Canvas LMS configuration
     */
    private VendorConfig canvas = new VendorConfig();

    /**
     * Google Classroom configuration
     */
    private VendorConfig google = new VendorConfig();

    /**
     * Sync configuration
     */
    private SyncConfig sync = new SyncConfig();

    /**
     * Audit configuration
     */
    private AuditConfig audit = new AuditConfig();

    @Data
    public static class TokenConfig {
        /**
         * Token expiration in days (default: 365 for annual rotation)
         */
        private int expirationDays = 365;

        /**
         * Enable automatic token rotation
         */
        private boolean rotationEnabled = true;

        /**
         * Month to trigger annual rotation (1=January, 8=August for school year)
         */
        private int rotationMonth = 8;

        /**
         * Characters allowed in token hash (excludes ambiguous chars: I, O, 0, 1)
         */
        private String hashCharset = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

        /**
         * Length of the hash portion of the token
         */
        private int hashLength = 8;

        /**
         * Length of the checksum portion
         */
        private int checksumLength = 2;
    }

    @Data
    public static class EncryptionConfig {
        /**
         * Master encryption key (Base64 encoded, 256-bit AES key)
         * In production, use environment variable: GUARDIAN_MASTER_KEY
         */
        private String masterKey;

        /**
         * Algorithm for credential encryption
         */
        private String algorithm = "AES/GCM/NoPadding";

        /**
         * Key size in bits
         */
        private int keySize = 256;
    }

    @Data
    public static class SisConfig {
        /**
         * Heronix SIS API base URL
         */
        private String apiUrl = "http://localhost:9580";

        /**
         * API key for service-to-service authentication
         */
        private String apiKey;

        /**
         * Connection timeout in seconds
         */
        private int connectionTimeout = 10;

        /**
         * Read timeout in seconds
         */
        private int readTimeout = 30;
    }

    @Data
    public static class VendorConfig {
        /**
         * Default request timeout in seconds
         */
        private int timeoutSeconds = 30;

        /**
         * Maximum retry attempts for failed requests
         */
        private int retryAttempts = 3;

        /**
         * Delay between retries in milliseconds
         */
        private long retryDelayMs = 1000;

        /**
         * Enable this vendor integration
         */
        private boolean enabled = true;
    }

    @Data
    public static class SyncConfig {
        /**
         * Batch size for bulk operations
         */
        private int batchSize = 100;

        /**
         * Number of parallel threads for sync operations
         */
        private int parallelThreads = 4;

        /**
         * Enable scheduled sync jobs
         */
        private boolean scheduledEnabled = false;

        /**
         * Cron expression for scheduled sync (default: daily at 2 AM)
         */
        private String scheduleCron = "0 0 2 * * ?";
    }

    @Data
    public static class AuditConfig {
        /**
         * Audit log retention in days
         */
        private int retentionDays = 365;

        /**
         * Enable detailed audit logging
         */
        private boolean detailedLogging = true;
    }

    @Data
    public static class GatewayConfig {
        /**
         * Enable routing outbound data through SIS SecureOutboundProxyService
         */
        private boolean enabled = false;

        /**
         * Require vendors to be registered as SIS gateway devices
         */
        private boolean requireDeviceRegistration = true;
    }
}
