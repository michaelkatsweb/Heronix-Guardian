package com.heronix.guardian.model.domain;

import java.time.LocalDateTime;

import com.heronix.guardian.model.enums.VendorType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Stores encrypted credentials for third-party vendor integrations.
 *
 * All sensitive fields (API keys, OAuth tokens, secrets) are stored encrypted
 * using AES-256-GCM encryption.
 */
@Entity
@Table(name = "vendor_credentials", indexes = {
    @Index(name = "idx_vendor_type", columnList = "vendor_type"),
    @Index(name = "idx_vendor_active", columnList = "active"),
    @Index(name = "idx_vendor_campus", columnList = "campus_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Type of vendor (CANVAS, GOOGLE_CLASSROOM, etc.)
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "vendor_type", nullable = false, length = 30)
    private VendorType vendorType;

    /**
     * Friendly name for this connection (e.g., "Main Campus Canvas")
     */
    @NotBlank
    @Column(name = "connection_name", nullable = false, length = 100)
    private String connectionName;

    /**
     * API base URL for this vendor instance
     */
    @Column(name = "api_base_url", length = 255)
    private String apiBaseUrl;

    /**
     * Encrypted API key (for vendors using API key auth)
     */
    @Column(name = "encrypted_api_key", length = 1024)
    private String encryptedApiKey;

    /**
     * Encrypted OAuth access token
     */
    @Column(name = "encrypted_oauth_token", length = 2048)
    private String encryptedOauthToken;

    /**
     * Encrypted OAuth refresh token
     */
    @Column(name = "encrypted_refresh_token", length = 1024)
    private String encryptedRefreshToken;

    /**
     * When the OAuth token expires
     */
    @Column(name = "oauth_expires_at")
    private LocalDateTime oauthExpiresAt;

    /**
     * OAuth client ID
     */
    @Column(name = "client_id", length = 255)
    private String clientId;

    /**
     * Encrypted OAuth client secret
     */
    @Column(name = "encrypted_client_secret", length = 1024)
    private String encryptedClientSecret;

    /**
     * Webhook callback URL for this vendor
     */
    @Column(name = "webhook_url", length = 512)
    private String webhookUrl;

    /**
     * Encrypted webhook secret for signature verification
     */
    @Column(name = "encrypted_webhook_secret", length = 256)
    private String encryptedWebhookSecret;

    /**
     * Campus this credential is associated with (null = district-wide)
     */
    @Column(name = "campus_id")
    private Long campusId;

    /**
     * Whether this credential is active
     */
    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

    /**
     * When the last sync occurred
     */
    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    /**
     * Result of last sync (success, failed, partial)
     */
    @Column(name = "last_sync_status", length = 20)
    private String lastSyncStatus;

    /**
     * Error message from last sync (if failed)
     */
    @Column(name = "last_sync_error", length = 500)
    private String lastSyncError;

    /**
     * When this credential was created
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * When this credential was last updated
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Who created this credential
     */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Check if OAuth token needs refresh.
     */
    public boolean needsTokenRefresh() {
        if (oauthExpiresAt == null) {
            return false;
        }
        // Refresh 5 minutes before expiry
        return LocalDateTime.now().plusMinutes(5).isAfter(oauthExpiresAt);
    }

    /**
     * Check if this connection is ready for use.
     */
    public boolean isReady() {
        if (!Boolean.TRUE.equals(active)) {
            return false;
        }
        // Must have either API key or valid OAuth token
        boolean hasApiKey = encryptedApiKey != null && !encryptedApiKey.isBlank();
        boolean hasValidOauth = encryptedOauthToken != null && !needsTokenRefresh();
        return hasApiKey || hasValidOauth;
    }

    /**
     * Record a successful sync.
     */
    public void recordSyncSuccess() {
        this.lastSyncAt = LocalDateTime.now();
        this.lastSyncStatus = "SUCCESS";
        this.lastSyncError = null;
    }

    /**
     * Record a failed sync.
     */
    public void recordSyncFailure(String error) {
        this.lastSyncAt = LocalDateTime.now();
        this.lastSyncStatus = "FAILED";
        this.lastSyncError = error != null ? error.substring(0, Math.min(error.length(), 500)) : "Unknown error";
    }
}
