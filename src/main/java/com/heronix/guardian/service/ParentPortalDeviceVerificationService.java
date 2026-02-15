package com.heronix.guardian.service;

import com.heronix.guardian.config.GuardianProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verifies parent portal devices against SIS-Server's device registry.
 * Calls SIS at /api/v1/guardian-integration/devices/verify via WebClient.
 * Caches verification results for 5 minutes per device ID.
 *
 * @author Heronix Educational Systems LLC
 * @since February 2026
 */
@Service
@Slf4j
public class ParentPortalDeviceVerificationService {

    private final WebClient webClient;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

    private final ConcurrentHashMap<String, CachedVerification> verificationCache = new ConcurrentHashMap<>();

    public ParentPortalDeviceVerificationService(
            WebClient.Builder webClientBuilder,
            GuardianProperties properties) {

        String baseUrl = properties.getSis().getApiUrl();
        String apiKey = properties.getSis().getApiKey();

        WebClient.Builder builder = webClientBuilder
                .baseUrl(baseUrl + "/api/v1/guardian-integration")
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);

        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("X-API-Key", apiKey);
        }

        this.webClient = builder.build();
        log.info("PARENT_PORTAL_VERIFY: Initialized - SIS URL: {}", baseUrl);
    }

    /**
     * Verify a parent portal device. Returns true if the device is registered and ACTIVE.
     *
     * @param deviceId      the device fingerprint (SHA-256)
     * @param publicKeyHash the hash of the device's public key
     * @return true if verified
     */
    public boolean verifyDevice(String deviceId, String publicKeyHash) {
        if (deviceId == null || publicKeyHash == null) {
            return false;
        }

        // Check cache first
        String cacheKey = deviceId + ":" + publicKeyHash;
        CachedVerification cached = verificationCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("PARENT_PORTAL_VERIFY: Cache hit for device {}", deviceId.substring(0, 8));
            return cached.verified;
        }

        // Call SIS to verify
        try {
            Map<String, Object> body = Map.of(
                    "deviceId", deviceId,
                    "publicKeyHash", publicKeyHash
            );

            Map<String, Object> response = webClient.post()
                    .uri("/devices/verify")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block(REQUEST_TIMEOUT);

            if (response == null) {
                log.warn("PARENT_PORTAL_VERIFY: Empty response from SIS for device {}", deviceId.substring(0, 8));
                return false;
            }

            boolean verified = Boolean.TRUE.equals(response.get("verified"));

            // Cache the result
            verificationCache.put(cacheKey, new CachedVerification(verified, System.currentTimeMillis()));

            if (verified) {
                log.debug("PARENT_PORTAL_VERIFY: Device {} verified", deviceId.substring(0, 8));
            } else {
                String reason = (String) response.get("reason");
                log.warn("PARENT_PORTAL_VERIFY: Device {} verification failed: {}",
                        deviceId.substring(0, 8), reason);
            }

            return verified;

        } catch (Exception e) {
            log.error("PARENT_PORTAL_VERIFY: SIS verification call failed for device {}: {}",
                    deviceId.substring(0, 8), e.getMessage());
            return false;
        }
    }

    /**
     * Invalidate the cache entry for a device (e.g., after revocation).
     */
    public void invalidateCache(String deviceId) {
        verificationCache.entrySet().removeIf(entry -> entry.getKey().startsWith(deviceId + ":"));
    }

    /**
     * Clear the entire verification cache.
     */
    public void clearCache() {
        verificationCache.clear();
    }

    private record CachedVerification(boolean verified, long timestamp) {
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }
}
