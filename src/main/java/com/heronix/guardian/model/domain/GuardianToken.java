package com.heronix.guardian.model.domain;

import java.time.LocalDateTime;

import com.heronix.guardian.model.enums.TokenStatus;
import com.heronix.guardian.model.enums.TokenType;

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
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @deprecated As of v2.0.0, use SIS {@code StudentToken} instead.
 *
 * This entity duplicated the existing SIS token system. The refactored
 * architecture uses the SIS StudentToken entity exclusively to maintain
 * a single source of truth for all student tokens.
 *
 * Migration:
 * - Use SIS StudentToken entity
 * - Use SisTokenBridgeService for token operations
 *
 * ORIGINAL DOCUMENTATION:
 * Guardian Token entity for anonymizing student/teacher/course data
 * when integrating with third-party vendors.
 *
 * Token format: PREFIX_HASH_CHECKSUM
 * Example: STU_H7K2P9M3_X8
 *
 * - PREFIX: 3 chars identifying type (STU, TCH, CRS)
 * - HASH: 8 random chars (collision-resistant)
 * - CHECKSUM: 2 chars for validation
 */
@Deprecated(since = "2.0.0", forRemoval = true)
@Entity
@Table(name = "guardian_tokens", indexes = {
    @Index(name = "idx_guardian_token_value", columnList = "token_value", unique = true),
    @Index(name = "idx_guardian_entity", columnList = "entity_type, entity_id"),
    @Index(name = "idx_guardian_active", columnList = "status"),
    @Index(name = "idx_guardian_vendor", columnList = "vendor_scope"),
    @Index(name = "idx_guardian_school_year", columnList = "school_year"),
    @Index(name = "idx_guardian_expires", columnList = "expires_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuardianToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The anonymous token value sent to vendors.
     * Format: PREFIX_HASH_CHECKSUM (e.g., STU_H7K2P9M3_X8)
     */
    @NotBlank
    @Size(min = 14, max = 16)
    @Column(name = "token_value", nullable = false, unique = true, length = 16)
    private String tokenValue;

    /**
     * Type of entity this token represents.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false, length = 15)
    private TokenType tokenType;

    /**
     * ID of the real entity in Heronix SIS.
     */
    @NotNull
    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /**
     * Entity type string for queries (denormalized from tokenType).
     */
    @NotBlank
    @Column(name = "entity_type", nullable = false, length = 20)
    private String entityType;

    /**
     * Optional vendor scope. If null, token is universal.
     * If set (e.g., "CANVAS"), token is vendor-specific.
     */
    @Column(name = "vendor_scope", length = 30)
    private String vendorScope;

    /**
     * School year this token is valid for (e.g., "2025-2026").
     */
    @NotBlank
    @Column(name = "school_year", nullable = false, length = 9)
    private String schoolYear;

    /**
     * Cryptographic salt used in token generation.
     * Stored for verification purposes.
     */
    @NotBlank
    @Column(name = "salt", nullable = false, length = 64)
    private String salt;

    /**
     * Checksum portion of the token for quick validation.
     */
    @NotBlank
    @Column(name = "checksum", nullable = false, length = 2)
    private String checksum;

    /**
     * Token status.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    @Builder.Default
    private TokenStatus status = TokenStatus.ACTIVE;

    /**
     * When this token was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * When this token was last updated.
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * When this token expires.
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * Number of times this token has been rotated.
     */
    @Column(name = "rotation_count")
    @Builder.Default
    private Integer rotationCount = 0;

    /**
     * When this token was last used in a transmission.
     */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    /**
     * Total number of times this token has been used.
     */
    @Column(name = "usage_count")
    @Builder.Default
    private Long usageCount = 0L;

    /**
     * If rotated, the ID of the replacement token.
     */
    @Column(name = "replaced_by_id")
    private Long replacedById;

    /**
     * Who created this token.
     */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.entityType == null && this.tokenType != null) {
            this.entityType = this.tokenType.getEntityType();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Check if this token is currently active.
     */
    public boolean isActive() {
        if (status != TokenStatus.ACTIVE) {
            return false;
        }
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) {
            return false;
        }
        return true;
    }

    /**
     * Record a usage of this token.
     */
    public void recordUsage() {
        this.lastUsedAt = LocalDateTime.now();
        this.usageCount = (this.usageCount == null ? 0L : this.usageCount) + 1;
    }

    /**
     * Mark this token as revoked.
     */
    public void revoke() {
        this.status = TokenStatus.REVOKED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Mark this token as rotated and replaced.
     */
    public void markRotated(Long newTokenId) {
        this.status = TokenStatus.ROTATED;
        this.replacedById = newTokenId;
        this.updatedAt = LocalDateTime.now();
    }
}
