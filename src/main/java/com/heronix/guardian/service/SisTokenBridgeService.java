package com.heronix.guardian.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.heronix.guardian.config.GuardianProperties;
import com.heronix.guardian.model.domain.GuardianToken;
import com.heronix.guardian.model.enums.TokenType;

import lombok.extern.slf4j.Slf4j;

/**
 * Bridge service that connects Guardian to the SIS tokenization system over REST.
 *
 * Delegates token operations to SIS-Server's GuardianIntegrationApiController.
 * Falls back to deprecated local token services when SIS is unreachable.
 *
 * ARCHITECTURAL ALIGNMENT:
 * - Uses existing STU-XXXXXX token format from SIS
 * - Leverages existing token generation, rotation, and validation
 * - Maintains single source of truth for all student tokens
 *
 * @author Heronix Development Team
 * @version 2.0.0 - REST client implementation
 */
@Service
@ConditionalOnProperty(name = "heronix.guardian.use-sis-tokenization", havingValue = "true")
@Slf4j
public class SisTokenBridgeService {

    private final WebClient webClient;
    private final GuardianProperties properties;
    private final TokenGenerationService tokenGenerationService;
    private final TokenValidationService tokenValidationService;
    private final TokenMappingService tokenMappingService;

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    public SisTokenBridgeService(
            WebClient.Builder webClientBuilder,
            GuardianProperties properties,
            TokenGenerationService tokenGenerationService,
            TokenValidationService tokenValidationService,
            TokenMappingService tokenMappingService) {

        this.properties = properties;
        this.tokenGenerationService = tokenGenerationService;
        this.tokenValidationService = tokenValidationService;
        this.tokenMappingService = tokenMappingService;

        String baseUrl = properties.getSis().getApiUrl();
        String apiKey = properties.getSis().getApiKey();

        WebClient.Builder builder = webClientBuilder
                .baseUrl(baseUrl + "/api/v1/guardian-integration")
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);

        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("X-API-Key", apiKey);
        }

        this.webClient = builder.build();

        log.info("SIS_BRIDGE: Initialized - SIS URL: {}", baseUrl);
    }

    /**
     * Get or create a token for a student via SIS.
     * Falls back to local TokenGenerationService on connection failure.
     */
    public String getOrCreateToken(Long studentId, String vendorScope) {
        try {
            Map<String, Object> body = Map.of("studentId", studentId);

            Map<String, Object> response = webClient.post()
                    .uri("/tokens/generate")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block(REQUEST_TIMEOUT);

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                String tokenValue = (String) response.get("tokenValue");
                log.debug("SIS_BRIDGE: Token for student {}: {}", studentId, tokenValue);
                return tokenValue;
            }

            log.warn("SIS_BRIDGE: Unexpected response for student {}, falling back to local", studentId);
        } catch (WebClientResponseException e) {
            log.warn("SIS_BRIDGE: HTTP {} from SIS for token generation (student {}), falling back to local",
                    e.getStatusCode(), studentId);
        } catch (Exception e) {
            log.warn("SIS_BRIDGE: SIS unreachable for token generation (student {}): {}, falling back to local",
                    studentId, e.getMessage());
        }

        // Fallback to deprecated local token system
        GuardianToken localToken = tokenGenerationService.getOrCreateToken(
                TokenType.STUDENT, studentId, vendorScope);
        return localToken.getTokenValue();
    }

    /**
     * Validate a token via SIS.
     * Falls back to local TokenValidationService on connection failure.
     */
    public TokenValidationService.ValidationResult validateToken(String tokenValue) {
        try {
            Map<String, Object> body = Map.of("tokenValue", tokenValue);

            Map<String, Object> response = webClient.post()
                    .uri("/tokens/validate")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block(REQUEST_TIMEOUT);

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                boolean valid = Boolean.TRUE.equals(response.get("valid"));
                String reason = (String) response.get("reason");

                if (valid) {
                    return new TokenValidationService.ValidationResult(
                            true, tokenValue, null, null, null, null);
                } else {
                    return TokenValidationService.ValidationResult.failure(tokenValue, reason);
                }
            }
        } catch (Exception e) {
            log.warn("SIS_BRIDGE: SIS unreachable for token validation: {}, falling back to local", e.getMessage());
        }

        // Fallback to deprecated local validation
        return tokenValidationService.validateToken(tokenValue);
    }

    /**
     * Resolve a token to its student ID via SIS.
     * Falls back to local TokenMappingService on connection failure.
     */
    public Optional<Long> resolveToken(String tokenValue) {
        try {
            Map<String, Object> body = Map.of("tokenValue", tokenValue);

            Map<String, Object> response = webClient.post()
                    .uri("/tokens/resolve")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block(REQUEST_TIMEOUT);

            if (response != null && Boolean.TRUE.equals(response.get("resolved"))) {
                Object studentIdObj = response.get("studentId");
                if (studentIdObj instanceof Number) {
                    return Optional.of(((Number) studentIdObj).longValue());
                }
            }

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                // Successful call but token not resolved
                return Optional.empty();
            }
        } catch (Exception e) {
            log.warn("SIS_BRIDGE: SIS unreachable for token resolution: {}, falling back to local", e.getMessage());
        }

        // Fallback to deprecated local mapping
        return tokenMappingService.resolveToEntityId(tokenValue);
    }

    /**
     * Get the active token for a student via SIS.
     * Falls back to local lookup on connection failure.
     */
    public Optional<String> getActiveToken(Long studentId) {
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/tokens/student/{studentId}", studentId)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block(REQUEST_TIMEOUT);

            if (response != null && Boolean.TRUE.equals(response.get("found"))) {
                return Optional.ofNullable((String) response.get("tokenValue"));
            }

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                return Optional.empty();
            }
        } catch (Exception e) {
            log.warn("SIS_BRIDGE: SIS unreachable for active token lookup (student {}): {}, falling back to local",
                    studentId, e.getMessage());
        }

        // Fallback: try local token mapping
        return tokenMappingService.findTokenForEntity(TokenType.STUDENT, studentId, null);
    }

    /**
     * Get bulk tokenized sync data from SIS.
     * No fallback — this data only exists in SIS.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTokenizedSyncData() {
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/tokens/sync-data")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block(Duration.ofSeconds(properties.getSis().getReadTimeout()));

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                Object data = response.get("data");
                if (data instanceof List) {
                    return (List<Map<String, Object>>) data;
                }
            }
        } catch (Exception e) {
            log.error("SIS_BRIDGE: Failed to fetch tokenized sync data from SIS: {}", e.getMessage());
        }

        return List.of();
    }

    /**
     * Check if SIS tokenization service is available.
     */
    public boolean isSisAvailable() {
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block(Duration.ofSeconds(5));

            return response != null && "UP".equals(response.get("status"));
        } catch (Exception e) {
            log.debug("SIS_BRIDGE: SIS health check failed: {}", e.getMessage());
            return false;
        }
    }
}
