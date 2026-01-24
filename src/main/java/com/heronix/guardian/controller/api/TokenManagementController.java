package com.heronix.guardian.controller.api;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.heronix.guardian.model.domain.GuardianToken;
import com.heronix.guardian.model.dto.BulkTokenRequestDTO;
import com.heronix.guardian.model.dto.TokenRequestDTO;
import com.heronix.guardian.model.dto.TokenResponseDTO;
import com.heronix.guardian.model.dto.TokenStatsDTO;
import com.heronix.guardian.model.enums.TokenStatus;
import com.heronix.guardian.model.enums.TokenType;
import com.heronix.guardian.repository.GuardianTokenRepository;
import com.heronix.guardian.service.TokenGenerationService;
import com.heronix.guardian.service.TokenMappingService;
import com.heronix.guardian.service.TokenValidationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST API for Guardian token management.
 */
@RestController
@RequestMapping("/api/v1/guardian/tokens")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Token Management", description = "APIs for managing Guardian tokens")
public class TokenManagementController {

    private final TokenGenerationService tokenGenerationService;
    private final TokenValidationService tokenValidationService;
    private final TokenMappingService tokenMappingService;
    private final GuardianTokenRepository tokenRepository;

    @PostMapping
    @Operation(summary = "Generate a new token", description = "Create an anonymous token for an entity")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Token created successfully"),
        @ApiResponse(responseCode = "200", description = "Token already exists, returning existing"),
        @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<TokenResponseDTO> generateToken(@Valid @RequestBody TokenRequestDTO request) {
        log.info("Generating token for {} entity {}", request.getTokenType(), request.getEntityId());

        GuardianToken token = tokenGenerationService.getOrCreateToken(
                request.getTokenType(),
                request.getEntityId(),
                request.getVendorScope()
        );

        TokenResponseDTO response = TokenResponseDTO.fromEntity(token);

        // Return 201 if newly created, 200 if existing
        boolean isNew = token.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(5));
        return ResponseEntity.status(isNew ? HttpStatus.CREATED : HttpStatus.OK).body(response);
    }

    @PostMapping("/bulk")
    @Operation(summary = "Generate tokens in bulk", description = "Create tokens for multiple entities")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tokens created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<Map<Long, TokenResponseDTO>> generateTokensBulk(
            @Valid @RequestBody BulkTokenRequestDTO request) {
        log.info("Generating {} tokens for {} entities",
                request.getTokenType(), request.getEntityIds().size());

        List<GuardianToken> tokens = tokenGenerationService.generateTokensBulk(
                request.getTokenType(),
                request.getEntityIds(),
                request.getVendorScope()
        );

        Map<Long, TokenResponseDTO> response = tokens.stream()
                .collect(Collectors.toMap(
                        GuardianToken::getEntityId,
                        TokenResponseDTO::fromEntity
                ));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{tokenValue}")
    @Operation(summary = "Get token details", description = "Retrieve details for a specific token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token found"),
        @ApiResponse(responseCode = "404", description = "Token not found")
    })
    public ResponseEntity<TokenResponseDTO> getToken(
            @Parameter(description = "Token value (e.g., STU_H7K2P9M3_X8)")
            @PathVariable String tokenValue) {

        var result = tokenValidationService.validateToken(tokenValue);

        if (!result.valid()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(TokenResponseDTO.fromEntity(result.token()));
    }

    @GetMapping("/validate/{tokenValue}")
    @Operation(summary = "Validate a token", description = "Check if a token is valid and active")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Validation result returned")
    })
    public ResponseEntity<Map<String, Object>> validateToken(
            @Parameter(description = "Token value to validate")
            @PathVariable String tokenValue) {

        var result = tokenValidationService.validateToken(tokenValue);

        Map<String, Object> response = new HashMap<>();
        response.put("valid", result.valid());
        response.put("tokenValue", result.tokenValue());

        if (result.valid()) {
            response.put("tokenType", result.tokenType());
            response.put("entityId", result.entityId());
        } else {
            response.put("error", result.errorMessage());
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/resolve/{tokenValue}")
    @Operation(summary = "Resolve token to entity ID", description = "Get the real entity ID for a token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token resolved"),
        @ApiResponse(responseCode = "404", description = "Token not found or invalid")
    })
    public ResponseEntity<Map<String, Object>> resolveToken(
            @Parameter(description = "Token value to resolve")
            @PathVariable String tokenValue) {

        var entityIdOpt = tokenMappingService.resolveToEntityId(tokenValue);

        if (entityIdOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("tokenValue", tokenValue);
        response.put("entityId", entityIdOpt.get());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    @Operation(summary = "Find token for entity", description = "Get the token for a specific entity")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token found"),
        @ApiResponse(responseCode = "404", description = "No token exists for this entity")
    })
    public ResponseEntity<TokenResponseDTO> findTokenForEntity(
            @Parameter(description = "Entity type (STUDENT, TEACHER, COURSE)")
            @PathVariable String entityType,
            @Parameter(description = "Entity ID")
            @PathVariable Long entityId,
            @Parameter(description = "Optional vendor scope")
            @RequestParam(required = false) String vendorScope) {

        TokenType tokenType;
        try {
            tokenType = TokenType.fromEntityType(entityType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        var tokenOpt = tokenMappingService.getTokenDetails(tokenType, entityId, vendorScope);

        if (tokenOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(TokenResponseDTO.fromEntity(tokenOpt.get()));
    }

    @DeleteMapping("/{tokenValue}")
    @Operation(summary = "Revoke a token", description = "Mark a token as revoked")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token revoked"),
        @ApiResponse(responseCode = "404", description = "Token not found")
    })
    public ResponseEntity<TokenResponseDTO> revokeToken(
            @Parameter(description = "Token value to revoke")
            @PathVariable String tokenValue) {

        var tokenOpt = tokenRepository.findByTokenValue(tokenValue);

        if (tokenOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        GuardianToken token = tokenOpt.get();
        token.revoke();
        token = tokenRepository.save(token);

        log.info("Revoked token {}", tokenValue);

        return ResponseEntity.ok(TokenResponseDTO.fromEntity(token));
    }

    @PostMapping("/{tokenValue}/rotate")
    @Operation(summary = "Rotate a token", description = "Create a new token and mark the old one as rotated")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token rotated"),
        @ApiResponse(responseCode = "404", description = "Token not found")
    })
    public ResponseEntity<TokenResponseDTO> rotateToken(
            @Parameter(description = "Token value to rotate")
            @PathVariable String tokenValue) {

        var tokenOpt = tokenRepository.findByTokenValue(tokenValue);

        if (tokenOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        GuardianToken oldToken = tokenOpt.get();

        if (oldToken.getStatus() != TokenStatus.ACTIVE) {
            return ResponseEntity.badRequest().build();
        }

        GuardianToken newToken = tokenGenerationService.rotateToken(oldToken, "api");

        return ResponseEntity.ok(TokenResponseDTO.fromEntity(newToken));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get token statistics", description = "Get overall token statistics")
    @ApiResponse(responseCode = "200", description = "Statistics returned")
    public ResponseEntity<TokenStatsDTO> getStatistics() {
        // Count by status
        List<Object[]> statusCounts = tokenRepository.countTokensByStatus();
        Map<String, Long> byStatus = new HashMap<>();
        for (Object[] row : statusCounts) {
            byStatus.put(row[0].toString(), (Long) row[1]);
        }

        // Count by type
        List<Object[]> typeCounts = tokenRepository.countActiveTokensByType();
        Map<String, Long> byType = new HashMap<>();
        for (Object[] row : typeCounts) {
            byType.put(row[0].toString(), (Long) row[1]);
        }

        // Usage stats
        Object[] usageStats = tokenRepository.getUsageStatistics();
        Long totalUsage = usageStats[0] != null ? (Long) usageStats[0] : 0L;
        LocalDateTime lastUsage = usageStats[2] != null ? (LocalDateTime) usageStats[2] : null;

        // Expiring soon
        LocalDateTime thirtyDaysFromNow = LocalDateTime.now().plusDays(30);
        long expiringSoon = tokenRepository.findTokensExpiringBefore(thirtyDaysFromNow).size();

        TokenStatsDTO stats = TokenStatsDTO.builder()
                .totalTokens(tokenRepository.count())
                .activeTokens(byStatus.getOrDefault("ACTIVE", 0L))
                .expiredTokens(byStatus.getOrDefault("EXPIRED", 0L))
                .revokedTokens(byStatus.getOrDefault("REVOKED", 0L))
                .rotatedTokens(byStatus.getOrDefault("ROTATED", 0L))
                .tokensByType(byType)
                .totalUsageCount(totalUsage)
                .lastUsageAt(lastUsage)
                .expiringWithin30Days(expiringSoon)
                .build();

        return ResponseEntity.ok(stats);
    }

    @GetMapping
    @Operation(summary = "List tokens", description = "List tokens with optional filtering")
    @ApiResponse(responseCode = "200", description = "Tokens returned")
    public ResponseEntity<List<TokenResponseDTO>> listTokens(
            @Parameter(description = "Filter by token type")
            @RequestParam(required = false) TokenType tokenType,
            @Parameter(description = "Filter by status")
            @RequestParam(required = false) TokenStatus status,
            @Parameter(description = "Filter by vendor scope")
            @RequestParam(required = false) String vendorScope,
            @Parameter(description = "Maximum results")
            @RequestParam(defaultValue = "100") int limit) {

        List<GuardianToken> tokens;

        if (tokenType != null && status != null) {
            tokens = tokenRepository.findByTokenTypeAndStatus(tokenType, status);
        } else if (vendorScope != null && status != null) {
            tokens = tokenRepository.findByVendorScopeAndStatus(vendorScope, status);
        } else if (tokenType != null) {
            tokens = tokenRepository.findByTokenTypeAndStatus(tokenType, TokenStatus.ACTIVE);
        } else {
            tokens = tokenRepository.findByTokenTypeAndStatus(TokenType.STUDENT, TokenStatus.ACTIVE);
        }

        List<TokenResponseDTO> response = tokens.stream()
                .limit(limit)
                .map(TokenResponseDTO::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }
}
