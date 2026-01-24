package com.heronix.guardian.model.dto;

import java.time.LocalDateTime;

import com.heronix.guardian.model.domain.GuardianToken;
import com.heronix.guardian.model.enums.TokenStatus;
import com.heronix.guardian.model.enums.TokenType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for token operations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenResponseDTO {

    /**
     * The anonymous token value (e.g., "STU_H7K2P9M3_X8").
     */
    private String tokenValue;

    /**
     * Type of entity this token represents.
     */
    private TokenType tokenType;

    /**
     * Status of the token.
     */
    private TokenStatus status;

    /**
     * Vendor scope (null if universal).
     */
    private String vendorScope;

    /**
     * School year the token is valid for.
     */
    private String schoolYear;

    /**
     * When the token was created.
     */
    private LocalDateTime createdAt;

    /**
     * When the token expires.
     */
    private LocalDateTime expiresAt;

    /**
     * Number of times the token has been rotated.
     */
    private Integer rotationCount;

    /**
     * Number of times the token has been used.
     */
    private Long usageCount;

    /**
     * When the token was last used.
     */
    private LocalDateTime lastUsedAt;

    /**
     * Whether the token is currently active and valid.
     */
    private boolean active;

    /**
     * Create from entity.
     */
    public static TokenResponseDTO fromEntity(GuardianToken token) {
        return TokenResponseDTO.builder()
                .tokenValue(token.getTokenValue())
                .tokenType(token.getTokenType())
                .status(token.getStatus())
                .vendorScope(token.getVendorScope())
                .schoolYear(token.getSchoolYear())
                .createdAt(token.getCreatedAt())
                .expiresAt(token.getExpiresAt())
                .rotationCount(token.getRotationCount())
                .usageCount(token.getUsageCount())
                .lastUsedAt(token.getLastUsedAt())
                .active(token.isActive())
                .build();
    }
}
