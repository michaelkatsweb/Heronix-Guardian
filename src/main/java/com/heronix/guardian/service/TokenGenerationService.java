package com.heronix.guardian.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.heronix.guardian.config.GuardianProperties;
import com.heronix.guardian.exception.TokenGenerationException;
import com.heronix.guardian.model.domain.GuardianToken;
import com.heronix.guardian.model.enums.TokenStatus;
import com.heronix.guardian.model.enums.TokenType;
import com.heronix.guardian.repository.GuardianTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @deprecated As of v2.0.0, use {@link SisTokenBridgeService} instead.
 *
 * This service created a duplicate token system that conflicts with
 * the existing SIS StudentTokenizationService. The refactored architecture
 * uses the SIS token system for consistency and security compliance.
 *
 * Migration:
 * - Replace: tokenGenerationService.generateToken(...)
 * - With: sisTokenBridgeService.getOrCreateToken(studentId)
 *
 * ORIGINAL DOCUMENTATION:
 * Service for generating anonymous Guardian tokens.
 *
 * Tokens follow the format: PREFIX_HASH_CHECKSUM
 * Example: STU_H7K2P9M3_X8
 *
 * - PREFIX: 3 chars identifying type (STU, TCH, CRS)
 * - HASH: 8 random chars (collision-resistant, 34^8 = 1.8 trillion combinations)
 * - CHECKSUM: 2 chars for quick validation
 */
@Deprecated(since = "2.0.0", forRemoval = true)
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenGenerationService {

    private final GuardianTokenRepository tokenRepository;
    private final GuardianProperties properties;

    private final SecureRandom secureRandom = new SecureRandom();

    // Maximum attempts to generate a unique token before failing
    private static final int MAX_GENERATION_ATTEMPTS = 100;

    /**
     * Generate a new token for an entity.
     *
     * @param tokenType   the type of entity (STUDENT, TEACHER, COURSE)
     * @param entityId    the real entity ID in Heronix SIS
     * @param vendorScope optional vendor scope (null for universal token)
     * @return the generated token
     */
    @Transactional
    public GuardianToken generateToken(TokenType tokenType, Long entityId, String vendorScope) {
        return generateToken(tokenType, entityId, vendorScope, null);
    }

    /**
     * Generate a new token for an entity with optional creator info.
     *
     * @param tokenType   the type of entity
     * @param entityId    the real entity ID
     * @param vendorScope optional vendor scope
     * @param createdBy   who created this token
     * @return the generated token
     */
    @Transactional
    public GuardianToken generateToken(TokenType tokenType, Long entityId, String vendorScope, String createdBy) {
        log.debug("Generating {} token for entity {} (vendor: {})", tokenType, entityId, vendorScope);

        // Check if active token already exists
        String entityType = tokenType.getEntityType();
        var existingToken = vendorScope != null
                ? tokenRepository.findActiveToken(entityType, entityId, vendorScope)
                : tokenRepository.findActiveUniversalToken(entityType, entityId);

        if (existingToken.isPresent()) {
            log.debug("Active token already exists for entity {}: {}", entityId, existingToken.get().getTokenValue());
            return existingToken.get();
        }

        // Generate unique token value
        String tokenValue = generateUniqueTokenValue(tokenType);
        String[] parts = parseTokenValue(tokenValue);
        String hash = parts[1];
        String checksum = parts[2];

        // Generate salt
        String salt = generateSalt();

        // Calculate expiration
        LocalDateTime expiresAt = calculateExpiration();

        // Create token entity
        GuardianToken token = GuardianToken.builder()
                .tokenValue(tokenValue)
                .tokenType(tokenType)
                .entityId(entityId)
                .entityType(entityType)
                .vendorScope(vendorScope)
                .schoolYear(getCurrentSchoolYear())
                .salt(salt)
                .checksum(checksum)
                .status(TokenStatus.ACTIVE)
                .expiresAt(expiresAt)
                .rotationCount(0)
                .usageCount(0L)
                .createdBy(createdBy)
                .build();

        token = tokenRepository.save(token);
        log.info("Generated token {} for {} entity {}", tokenValue, tokenType, entityId);

        return token;
    }

    /**
     * Get or create a token for an entity.
     */
    @Transactional
    public GuardianToken getOrCreateToken(TokenType tokenType, Long entityId, String vendorScope) {
        String entityType = tokenType.getEntityType();

        var existingToken = vendorScope != null
                ? tokenRepository.findActiveToken(entityType, entityId, vendorScope)
                : tokenRepository.findActiveUniversalToken(entityType, entityId);

        return existingToken.orElseGet(() -> generateToken(tokenType, entityId, vendorScope));
    }

    /**
     * Generate tokens for multiple entities in bulk.
     */
    @Transactional
    public List<GuardianToken> generateTokensBulk(TokenType tokenType, List<Long> entityIds, String vendorScope) {
        log.info("Generating {} tokens for {} {} entities", vendorScope, entityIds.size(), tokenType);

        List<GuardianToken> tokens = new ArrayList<>();
        for (Long entityId : entityIds) {
            tokens.add(getOrCreateToken(tokenType, entityId, vendorScope));
        }

        return tokens;
    }

    /**
     * Rotate a token - creates a new token and marks the old one as rotated.
     */
    @Transactional
    public GuardianToken rotateToken(GuardianToken oldToken, String rotatedBy) {
        log.info("Rotating token {} for entity {}", oldToken.getTokenValue(), oldToken.getEntityId());

        // Generate new token
        String newTokenValue = generateUniqueTokenValue(oldToken.getTokenType());
        String[] parts = parseTokenValue(newTokenValue);
        String checksum = parts[2];

        GuardianToken newToken = GuardianToken.builder()
                .tokenValue(newTokenValue)
                .tokenType(oldToken.getTokenType())
                .entityId(oldToken.getEntityId())
                .entityType(oldToken.getEntityType())
                .vendorScope(oldToken.getVendorScope())
                .schoolYear(getCurrentSchoolYear())
                .salt(generateSalt())
                .checksum(checksum)
                .status(TokenStatus.ACTIVE)
                .expiresAt(calculateExpiration())
                .rotationCount(oldToken.getRotationCount() + 1)
                .usageCount(0L)
                .createdBy(rotatedBy)
                .build();

        newToken = tokenRepository.save(newToken);

        // Mark old token as rotated
        oldToken.markRotated(newToken.getId());
        tokenRepository.save(oldToken);

        log.info("Rotated token {} -> {} for entity {}",
                oldToken.getTokenValue(), newToken.getTokenValue(), oldToken.getEntityId());

        return newToken;
    }

    /**
     * Generate a unique token value with collision detection.
     */
    private String generateUniqueTokenValue(TokenType tokenType) {
        String charset = properties.getToken().getHashCharset();
        int hashLength = properties.getToken().getHashLength();

        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String hash = generateRandomHash(charset, hashLength);
            String tokenWithoutChecksum = tokenType.getPrefix() + "_" + hash;
            String checksum = calculateChecksum(tokenWithoutChecksum, charset);
            String tokenValue = tokenWithoutChecksum + "_" + checksum;

            if (!tokenRepository.existsByTokenValue(tokenValue)) {
                return tokenValue;
            }

            log.debug("Token collision detected on attempt {}, regenerating...", attempt + 1);
        }

        throw new TokenGenerationException(
                "Failed to generate unique token after " + MAX_GENERATION_ATTEMPTS + " attempts");
    }

    /**
     * Generate a random hash string.
     */
    private String generateRandomHash(String charset, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(charset.charAt(secureRandom.nextInt(charset.length())));
        }
        return sb.toString();
    }

    /**
     * Calculate checksum for a token value.
     * Uses a simple CRC-like algorithm for quick validation.
     */
    private String calculateChecksum(String input, String charset) {
        int crc = 0;
        for (char c : input.toCharArray()) {
            crc = (crc + c) % (charset.length() * charset.length());
        }
        char c1 = charset.charAt(crc / charset.length());
        char c2 = charset.charAt(crc % charset.length());
        return "" + c1 + c2;
    }

    /**
     * Generate a cryptographic salt.
     */
    private String generateSalt() {
        byte[] saltBytes = new byte[32];
        secureRandom.nextBytes(saltBytes);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : saltBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Parse a token value into its components.
     * @return array of [prefix, hash, checksum]
     */
    private String[] parseTokenValue(String tokenValue) {
        String[] parts = tokenValue.split("_");
        if (parts.length != 3) {
            throw new TokenGenerationException("Invalid token format: " + tokenValue);
        }
        return parts;
    }

    /**
     * Calculate token expiration date.
     */
    private LocalDateTime calculateExpiration() {
        int expirationDays = properties.getToken().getExpirationDays();
        return LocalDateTime.now().plusDays(expirationDays);
    }

    /**
     * Get the current school year string (e.g., "2025-2026").
     */
    private String getCurrentSchoolYear() {
        LocalDateTime now = LocalDateTime.now();
        int year = now.getYear();
        int rotationMonth = properties.getToken().getRotationMonth();

        // If before rotation month (e.g., August), we're in the previous school year
        if (now.getMonthValue() < rotationMonth) {
            return (year - 1) + "-" + year;
        } else {
            return year + "-" + (year + 1);
        }
    }
}
