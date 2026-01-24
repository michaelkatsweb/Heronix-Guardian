package com.heronix.guardian.model.dto;

import java.time.LocalDateTime;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Statistics about Guardian tokens.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenStatsDTO {

    /**
     * Total number of tokens.
     */
    private long totalTokens;

    /**
     * Number of active tokens.
     */
    private long activeTokens;

    /**
     * Number of expired tokens.
     */
    private long expiredTokens;

    /**
     * Number of revoked tokens.
     */
    private long revokedTokens;

    /**
     * Number of rotated tokens.
     */
    private long rotatedTokens;

    /**
     * Breakdown by token type.
     */
    private Map<String, Long> tokensByType;

    /**
     * Total usage count across all tokens.
     */
    private long totalUsageCount;

    /**
     * Last usage timestamp.
     */
    private LocalDateTime lastUsageAt;

    /**
     * Tokens expiring in the next 30 days.
     */
    private long expiringWithin30Days;
}
