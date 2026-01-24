package com.heronix.guardian.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.heronix.guardian.config.GuardianProperties;
import com.heronix.guardian.model.domain.GuardianToken;
import com.heronix.guardian.model.enums.TokenStatus;
import com.heronix.guardian.model.enums.TokenType;
import com.heronix.guardian.repository.GuardianTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @deprecated As of v2.0.0, use {@link SisTokenBridgeService#isTokenValid(String)} instead.
 *
 * This service validated the duplicate GuardianToken system. The refactored
 * architecture uses SIS StudentTokenizationService for token validation.
 *
 * Migration:
 * - Replace: tokenValidationService.validateToken(...)
 * - With: sisTokenBridgeService.isTokenValid(tokenValue)
 */
@Deprecated(since = "2.0.0", forRemoval = true)
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenValidationService {

    private final GuardianTokenRepository tokenRepository;
    private final GuardianProperties properties;

    /**
     * Validation result containing details about the token.
     */
    public record ValidationResult(
            boolean valid,
            String tokenValue,
            TokenType tokenType,
            Long entityId,
            String errorMessage,
            GuardianToken token
    ) {
        public static ValidationResult success(GuardianToken token) {
            return new ValidationResult(true, token.getTokenValue(), token.getTokenType(),
                    token.getEntityId(), null, token);
        }

        public static ValidationResult failure(String tokenValue, String errorMessage) {
            return new ValidationResult(false, tokenValue, null, null, errorMessage, null);
        }
    }

    /**
     * Validate a token value and return the validation result.
     */
    public ValidationResult validateToken(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return ValidationResult.failure(tokenValue, "Token value is required");
        }

        // Validate format
        if (!isValidFormat(tokenValue)) {
            return ValidationResult.failure(tokenValue, "Invalid token format");
        }

        // Validate checksum
        if (!isValidChecksum(tokenValue)) {
            return ValidationResult.failure(tokenValue, "Invalid token checksum");
        }

        // Look up token in database
        Optional<GuardianToken> tokenOpt = tokenRepository.findByTokenValue(tokenValue);
        if (tokenOpt.isEmpty()) {
            return ValidationResult.failure(tokenValue, "Token not found");
        }

        GuardianToken token = tokenOpt.get();

        // Check status
        if (token.getStatus() != TokenStatus.ACTIVE) {
            return ValidationResult.failure(tokenValue, "Token is " + token.getStatus().name().toLowerCase());
        }

        // Check expiration
        if (token.getExpiresAt() != null && LocalDateTime.now().isAfter(token.getExpiresAt())) {
            return ValidationResult.failure(tokenValue, "Token has expired");
        }

        return ValidationResult.success(token);
    }

    /**
     * Check if the token format is valid.
     * Format: PREFIX_HASH_CHECKSUM (e.g., STU_H7K2P9M3_X8)
     */
    public boolean isValidFormat(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return false;
        }

        String[] parts = tokenValue.split("_");
        if (parts.length != 3) {
            return false;
        }

        String prefix = parts[0];
        String hash = parts[1];
        String checksum = parts[2];

        // Validate prefix
        try {
            TokenType.fromPrefix(prefix);
        } catch (IllegalArgumentException e) {
            return false;
        }

        // Validate hash length
        int expectedHashLength = properties.getToken().getHashLength();
        if (hash.length() != expectedHashLength) {
            return false;
        }

        // Validate checksum length
        int expectedChecksumLength = properties.getToken().getChecksumLength();
        if (checksum.length() != expectedChecksumLength) {
            return false;
        }

        // Validate characters are from allowed charset
        String charset = properties.getToken().getHashCharset();
        for (char c : hash.toCharArray()) {
            if (charset.indexOf(c) < 0) {
                return false;
            }
        }
        for (char c : checksum.toCharArray()) {
            if (charset.indexOf(c) < 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Validate the checksum portion of a token.
     */
    public boolean isValidChecksum(String tokenValue) {
        if (!isValidFormat(tokenValue)) {
            return false;
        }

        String[] parts = tokenValue.split("_");
        String prefix = parts[0];
        String hash = parts[1];
        String providedChecksum = parts[2];

        // Recalculate checksum
        String input = prefix + "_" + hash;
        String expectedChecksum = calculateChecksum(input);

        return providedChecksum.equals(expectedChecksum);
    }

    /**
     * Extract the token type from a token value without database lookup.
     */
    public Optional<TokenType> extractTokenType(String tokenValue) {
        if (!isValidFormat(tokenValue)) {
            return Optional.empty();
        }

        String prefix = tokenValue.split("_")[0];
        try {
            return Optional.of(TokenType.fromPrefix(prefix));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Resolve a token to its real entity ID.
     */
    public Optional<Long> resolveToEntityId(String tokenValue) {
        ValidationResult result = validateToken(tokenValue);
        if (result.valid()) {
            // Record usage
            result.token().recordUsage();
            tokenRepository.save(result.token());
            return Optional.of(result.entityId());
        }
        return Optional.empty();
    }

    /**
     * Resolve a token to its full entity information.
     */
    public Optional<GuardianToken> resolveToken(String tokenValue) {
        ValidationResult result = validateToken(tokenValue);
        if (result.valid()) {
            result.token().recordUsage();
            tokenRepository.save(result.token());
            return Optional.of(result.token());
        }
        return Optional.empty();
    }

    /**
     * Calculate checksum for validation.
     */
    private String calculateChecksum(String input) {
        String charset = properties.getToken().getHashCharset();
        int crc = 0;
        for (char c : input.toCharArray()) {
            crc = (crc + c) % (charset.length() * charset.length());
        }
        char c1 = charset.charAt(crc / charset.length());
        char c2 = charset.charAt(crc % charset.length());
        return "" + c1 + c2;
    }
}
