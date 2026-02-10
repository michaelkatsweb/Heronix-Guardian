package com.heronix.guardian.gateway;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.heronix.guardian.config.GuardianProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Guardian Gateway Service - Routes vendor communications through the SIS
 * SecureOutboundProxyService via REST.
 *
 * When enabled, ALL outbound data to third-party vendors passes through
 * the SIS gateway for sanitization, encryption, and audit logging.
 *
 * SECURITY: When the gateway is enabled but unreachable, transmissions
 * FAIL rather than silently bypassing. This is a deliberate security decision.
 *
 * @author Heronix Development Team
 * @version 2.0.0 - REST client implementation
 */
@Service
@ConditionalOnProperty(name = "heronix.guardian.gateway.enabled", havingValue = "true")
@Slf4j
public class GuardianGatewayService {

    private final WebClient webClient;
    private final GuardianProperties properties;

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    public GuardianGatewayService(
            WebClient.Builder webClientBuilder,
            GuardianProperties properties) {

        this.properties = properties;

        String baseUrl = properties.getSis().getApiUrl();
        String apiKey = properties.getSis().getApiKey();

        WebClient.Builder builder = webClientBuilder
                .baseUrl(baseUrl + "/api/v1/guardian-integration")
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);

        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("X-API-Key", apiKey);
        }

        this.webClient = builder.build();

        log.info("GUARDIAN_GATEWAY: Initialized - SIS URL: {}", baseUrl);
    }

    /**
     * Transmit data to a vendor through the SIS secure gateway.
     *
     * SECURITY: Returns failure (never silently bypasses) when gateway is unreachable.
     *
     * @param deviceId   the registered device ID for the vendor
     * @param data       the data to transmit
     * @param dataType   the type of data (e.g., STUDENT_RECORD)
     * @param sourceIp   the originating IP address
     * @return transmission result
     */
    public TransmissionResult transmitToVendor(
            String deviceId,
            Map<String, Object> data,
            String dataType,
            String sourceIp) {

        log.info("GUARDIAN_GATEWAY: Transmitting {} data to device {}", dataType, deviceId);

        try {
            Map<String, Object> body = Map.of(
                    "deviceId", deviceId,
                    "data", data,
                    "dataType", dataType
            );

            Map<String, Object> response = webClient.post()
                    .uri("/gateway/transmit")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block(REQUEST_TIMEOUT);

            if (response == null) {
                return TransmissionResult.failure("Empty response from SIS gateway");
            }

            boolean success = Boolean.TRUE.equals(response.get("success"));
            boolean blocked = Boolean.TRUE.equals(response.get("blocked"));
            String transmissionId = (String) response.get("transmissionId");
            String errorMessage = (String) response.get("errorMessage");

            if (success) {
                log.info("GUARDIAN_GATEWAY: Transmission {} succeeded for device {}", transmissionId, deviceId);
                return TransmissionResult.success(transmissionId);
            } else if (blocked) {
                log.warn("GUARDIAN_GATEWAY: Transmission {} blocked for device {}: {}",
                        transmissionId, deviceId, errorMessage);
                return TransmissionResult.blocked(transmissionId, errorMessage);
            } else {
                log.error("GUARDIAN_GATEWAY: Transmission {} failed for device {}: {}",
                        transmissionId, deviceId, errorMessage);
                return TransmissionResult.failure(errorMessage);
            }

        } catch (Exception e) {
            // SECURITY: Do NOT silently bypass — fail the transmission
            log.error("GUARDIAN_GATEWAY: SIS gateway unreachable for device {}: {}", deviceId, e.getMessage());
            return TransmissionResult.failure("SIS gateway unreachable: " + e.getMessage());
        }
    }

    /**
     * Get all registered gateway devices from SIS.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getRegisteredDevices() {
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/gateway/devices")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block(REQUEST_TIMEOUT);

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                Object devices = response.get("devices");
                if (devices instanceof List) {
                    return (List<Map<String, Object>>) devices;
                }
            }
        } catch (Exception e) {
            log.error("GUARDIAN_GATEWAY: Failed to fetch registered devices: {}", e.getMessage());
        }

        return List.of();
    }

    /**
     * Check if the SIS gateway is available.
     */
    public boolean isGatewayAvailable() {
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block(Duration.ofSeconds(5));

            return response != null && "UP".equals(response.get("status"));
        } catch (Exception e) {
            log.debug("GUARDIAN_GATEWAY: Health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Result of a gateway transmission attempt.
     */
    public record TransmissionResult(
            boolean success,
            boolean blocked,
            String transmissionId,
            String errorMessage
    ) {
        public static TransmissionResult success(String transmissionId) {
            return new TransmissionResult(true, false, transmissionId, null);
        }

        public static TransmissionResult blocked(String transmissionId, String reason) {
            return new TransmissionResult(false, true, transmissionId, reason);
        }

        public static TransmissionResult failure(String errorMessage) {
            return new TransmissionResult(false, false, null, errorMessage);
        }
    }
}
