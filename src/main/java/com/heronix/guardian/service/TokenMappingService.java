package com.heronix.guardian.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.heronix.guardian.model.domain.GuardianToken;
import com.heronix.guardian.model.enums.TokenType;
import com.heronix.guardian.repository.GuardianTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @deprecated As of v2.0.0, use {@link SisTokenBridgeService} instead.
 *
 * This service provided bidirectional mapping for the duplicate GuardianToken
 * system. The refactored architecture uses SIS StudentTokenizationService.
 *
 * Migration:
 * - Replace: tokenMappingService.resolveTokenToEntityId(...)
 * - With: sisTokenBridgeService.resolveToken(tokenValue)
 * - Replace: tokenMappingService.getTokenForEntity(...)
 * - With: sisTokenBridgeService.getOrCreateToken(studentId)
 *
 * ORIGINAL DOCUMENTATION:
 * Service for bidirectional token-to-entity mapping.
 *
 * Provides lookups:
 * - Token -> Entity ID (for inbound data from vendors)
 * - Entity ID -> Token (for outbound data to vendors)
 */
@Deprecated(since = "2.0.0", forRemoval = true)
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenMappingService {

    private final GuardianTokenRepository tokenRepository;
    private final TokenGenerationService tokenGenerationService;
    private final TokenValidationService tokenValidationService;

    /**
     * Resolve a token to its real entity ID.
     *
     * @param tokenValue the token value (e.g., "STU_H7K2P9M3_X8")
     * @param expectedType the expected token type
     * @return the real entity ID
     * @throws IllegalArgumentException if token is invalid or wrong type
     */
    @Transactional
    public Long resolveToEntityId(String tokenValue, TokenType expectedType) {
        var result = tokenValidationService.validateToken(tokenValue);

        if (!result.valid()) {
            throw new IllegalArgumentException("Invalid token: " + result.errorMessage());
        }

        if (result.tokenType() != expectedType) {
            throw new IllegalArgumentException(
                    "Token type mismatch: expected " + expectedType + ", got " + result.tokenType());
        }

        // Record usage
        result.token().recordUsage();
        tokenRepository.save(result.token());

        return result.entityId();
    }

    /**
     * Resolve a token to its entity ID without type checking.
     */
    @Transactional
    public Optional<Long> resolveToEntityId(String tokenValue) {
        return tokenValidationService.resolveToEntityId(tokenValue);
    }

    /**
     * Get or create a token for an entity.
     *
     * @param tokenType   the type of entity
     * @param entityId    the real entity ID
     * @param vendorScope optional vendor scope (null for universal)
     * @return the token value
     */
    @Transactional
    public String getTokenForEntity(TokenType tokenType, Long entityId, String vendorScope) {
        GuardianToken token = tokenGenerationService.getOrCreateToken(tokenType, entityId, vendorScope);
        return token.getTokenValue();
    }

    /**
     * Get tokens for multiple entities in bulk.
     *
     * @param tokenType   the type of entities
     * @param entityIds   list of entity IDs
     * @param vendorScope optional vendor scope
     * @return map of entity ID -> token value
     */
    @Transactional
    public Map<Long, String> getTokensForEntities(TokenType tokenType, List<Long> entityIds, String vendorScope) {
        List<GuardianToken> tokens = tokenGenerationService.generateTokensBulk(tokenType, entityIds, vendorScope);

        return tokens.stream()
                .collect(Collectors.toMap(
                        GuardianToken::getEntityId,
                        GuardianToken::getTokenValue
                ));
    }

    /**
     * Resolve multiple tokens to entity IDs in bulk.
     *
     * @param tokenValues list of token values
     * @return map of token value -> entity ID (invalid tokens are omitted)
     */
    @Transactional
    public Map<String, Long> resolveTokensBulk(List<String> tokenValues) {
        Map<String, Long> result = new HashMap<>();

        for (String tokenValue : tokenValues) {
            try {
                var validationResult = tokenValidationService.validateToken(tokenValue);
                if (validationResult.valid()) {
                    validationResult.token().recordUsage();
                    tokenRepository.save(validationResult.token());
                    result.put(tokenValue, validationResult.entityId());
                }
            } catch (Exception e) {
                log.warn("Failed to resolve token {}: {}", tokenValue, e.getMessage());
            }
        }

        return result;
    }

    /**
     * Find the token value for an entity.
     *
     * @param tokenType   the entity type
     * @param entityId    the entity ID
     * @param vendorScope optional vendor scope
     * @return the token value if found
     */
    public Optional<String> findTokenForEntity(TokenType tokenType, Long entityId, String vendorScope) {
        String entityType = tokenType.getEntityType();

        Optional<GuardianToken> token = vendorScope != null
                ? tokenRepository.findActiveToken(entityType, entityId, vendorScope)
                : tokenRepository.findActiveUniversalToken(entityType, entityId);

        return token.map(GuardianToken::getTokenValue);
    }

    /**
     * Check if a token exists for an entity.
     */
    public boolean hasToken(TokenType tokenType, Long entityId, String vendorScope) {
        return findTokenForEntity(tokenType, entityId, vendorScope).isPresent();
    }

    /**
     * Get the full token details for an entity.
     */
    public Optional<GuardianToken> getTokenDetails(TokenType tokenType, Long entityId, String vendorScope) {
        String entityType = tokenType.getEntityType();

        return vendorScope != null
                ? tokenRepository.findActiveToken(entityType, entityId, vendorScope)
                : tokenRepository.findActiveUniversalToken(entityType, entityId);
    }
}
