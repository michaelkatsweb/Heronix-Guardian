package com.heronix.guardian.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.heronix.guardian.model.domain.GuardianToken;
import com.heronix.guardian.model.enums.TokenStatus;
import com.heronix.guardian.model.enums.TokenType;

/**
 * Repository for GuardianToken entity.
 */
@Repository
public interface GuardianTokenRepository extends JpaRepository<GuardianToken, Long> {

    /**
     * Find token by its value.
     */
    Optional<GuardianToken> findByTokenValue(String tokenValue);

    /**
     * Check if a token value already exists.
     */
    boolean existsByTokenValue(String tokenValue);

    /**
     * Find active token for an entity.
     */
    @Query("SELECT t FROM GuardianToken t WHERE t.entityType = :entityType " +
           "AND t.entityId = :entityId AND t.status = 'ACTIVE' " +
           "AND (t.vendorScope IS NULL OR t.vendorScope = :vendorScope)")
    Optional<GuardianToken> findActiveToken(
            @Param("entityType") String entityType,
            @Param("entityId") Long entityId,
            @Param("vendorScope") String vendorScope);

    /**
     * Find active universal token for an entity (no vendor scope).
     */
    @Query("SELECT t FROM GuardianToken t WHERE t.entityType = :entityType " +
           "AND t.entityId = :entityId AND t.status = 'ACTIVE' " +
           "AND t.vendorScope IS NULL")
    Optional<GuardianToken> findActiveUniversalToken(
            @Param("entityType") String entityType,
            @Param("entityId") Long entityId);

    /**
     * Find all tokens for an entity (including inactive).
     */
    List<GuardianToken> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, Long entityId);

    /**
     * Find all active tokens for a school year.
     */
    @Query("SELECT t FROM GuardianToken t WHERE t.schoolYear = :schoolYear " +
           "AND t.status = 'ACTIVE'")
    List<GuardianToken> findActiveTokensBySchoolYear(@Param("schoolYear") String schoolYear);

    /**
     * Find tokens expiring before a given date.
     */
    @Query("SELECT t FROM GuardianToken t WHERE t.expiresAt <= :expirationDate " +
           "AND t.status = 'ACTIVE'")
    List<GuardianToken> findTokensExpiringBefore(@Param("expirationDate") LocalDateTime expirationDate);

    /**
     * Find all active tokens by type.
     */
    List<GuardianToken> findByTokenTypeAndStatus(TokenType tokenType, TokenStatus status);

    /**
     * Find tokens by vendor scope.
     */
    List<GuardianToken> findByVendorScopeAndStatus(String vendorScope, TokenStatus status);

    /**
     * Count active tokens by type.
     */
    @Query("SELECT t.tokenType, COUNT(t) FROM GuardianToken t " +
           "WHERE t.status = 'ACTIVE' GROUP BY t.tokenType")
    List<Object[]> countActiveTokensByType();

    /**
     * Count tokens by status.
     */
    @Query("SELECT t.status, COUNT(t) FROM GuardianToken t GROUP BY t.status")
    List<Object[]> countTokensByStatus();

    /**
     * Bulk update status for expired tokens.
     */
    @Modifying
    @Query("UPDATE GuardianToken t SET t.status = 'EXPIRED', t.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE t.expiresAt <= CURRENT_TIMESTAMP AND t.status = 'ACTIVE'")
    int expireOldTokens();

    /**
     * Find tokens that need rotation (approaching expiration).
     */
    @Query("SELECT t FROM GuardianToken t WHERE t.status = 'ACTIVE' " +
           "AND t.expiresAt BETWEEN CURRENT_TIMESTAMP AND :warningDate")
    List<GuardianToken> findTokensNeedingRotation(@Param("warningDate") LocalDateTime warningDate);

    /**
     * Find all active tokens for multiple entities.
     */
    @Query("SELECT t FROM GuardianToken t WHERE t.entityType = :entityType " +
           "AND t.entityId IN :entityIds AND t.status = 'ACTIVE'")
    List<GuardianToken> findActiveTokensForEntities(
            @Param("entityType") String entityType,
            @Param("entityIds") List<Long> entityIds);

    /**
     * Get usage statistics.
     */
    @Query("SELECT SUM(t.usageCount), COUNT(t), MAX(t.lastUsedAt) " +
           "FROM GuardianToken t WHERE t.status = 'ACTIVE'")
    Object[] getUsageStatistics();

    /**
     * Find most used tokens.
     */
    @Query("SELECT t FROM GuardianToken t WHERE t.status = 'ACTIVE' " +
           "ORDER BY t.usageCount DESC")
    List<GuardianToken> findMostUsedTokens(org.springframework.data.domain.Pageable pageable);

    /**
     * Delete old rotated/revoked tokens for cleanup.
     */
    @Modifying
    @Query("DELETE FROM GuardianToken t WHERE t.status IN ('ROTATED', 'REVOKED') " +
           "AND t.updatedAt < :cutoffDate")
    int cleanupOldTokens(@Param("cutoffDate") LocalDateTime cutoffDate);
}
