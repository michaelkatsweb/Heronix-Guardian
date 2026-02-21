package com.heronix.guardian.controller.api;

import com.heronix.guardian.config.GuardianProperties;
import com.heronix.guardian.service.ParentPortalDeviceVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Parent Portal Gateway Controller
 *
 * Reverse proxy for parent portal requests. Routes through Guardian (port 9989)
 * to SIS-Server (port 9590) after device verification.
 *
 * Also exposes device registration and status endpoints that don't require
 * an active device (they're used to register/check a new device).
 *
 * Base path: /api/v1/parent-portal
 *
 * @author Heronix Educational Systems LLC
 * @since February 2026
 */
@RestController
@RequestMapping("/api/v1/parent-portal")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Parent Portal Gateway", description = "Gateway endpoints for Parent Portal device registration and data proxy")
public class ParentPortalGatewayController {

    private final ParentPortalDeviceVerificationService verificationService;
    private final GuardianProperties properties;
    private final WebClient.Builder webClientBuilder;

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    // ========================================================================
    // DEVICE REGISTRATION & STATUS (no device verification required)
    // ========================================================================

    @Operation(summary = "Register device", description = "Register a parent portal device with SIS")
    @PostMapping("/device/register")
    public ResponseEntity<Map<String, Object>> registerDevice(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {

        String deviceId = (String) request.get("deviceId");
        String parentEmail = (String) request.get("parentEmail");
        log.info("PARENT_PORTAL_GW: Device registration request from {} ({})",
                parentEmail, deviceId != null ? deviceId.substring(0, Math.min(8, deviceId.length())) : "null");

        try {
            // Forward registration to SIS
            Map<String, Object> sisResponse = callSis(
                    HttpMethod.POST,
                    "/api/v1/guardian-integration/devices/register-parent-portal",
                    request
            );

            return ResponseEntity.ok(sisResponse);

        } catch (WebClientResponseException e) {
            log.error("PARENT_PORTAL_GW: SIS returned error during registration: {}", e.getStatusCode());
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("success", false, "message", "Registration failed: " + e.getMessage()));
        } catch (Exception e) {
            log.error("PARENT_PORTAL_GW: Registration error", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("success", false, "message", "Guardian cannot reach SIS server"));
        }
    }

    @Operation(summary = "Check device status", description = "Check registration status of a parent portal device")
    @PostMapping("/device/status")
    public ResponseEntity<Map<String, Object>> checkDeviceStatus(
            @RequestBody Map<String, Object> request) {

        String deviceId = (String) request.get("deviceId");
        String publicKeyHash = (String) request.get("publicKeyHash");

        log.debug("PARENT_PORTAL_GW: Status check for device {}",
                deviceId != null ? deviceId.substring(0, Math.min(8, deviceId.length())) : "null");

        try {
            Map<String, Object> sisResponse = callSis(
                    HttpMethod.POST,
                    "/api/v1/guardian-integration/devices/verify",
                    request
            );

            // Extract status from SIS verify response
            Map<String, Object> response = new HashMap<>();
            if (Boolean.TRUE.equals(sisResponse.get("verified"))) {
                response.put("status", "ACTIVE");
            } else {
                String reason = (String) sisResponse.get("reason");
                if ("PENDING_APPROVAL".equals(reason)) {
                    response.put("status", "PENDING_APPROVAL");
                } else if ("UNREGISTERED".equals(reason)) {
                    response.put("status", "UNREGISTERED");
                } else {
                    response.put("status", reason != null ? reason : "UNKNOWN");
                }
            }
            response.put("success", true);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("PARENT_PORTAL_GW: Status check error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("success", false, "message", "Cannot reach SIS server"));
        }
    }

    // ========================================================================
    // DATA PROXY (device verification required — handled by DeviceVerificationFilter)
    // ========================================================================

    @Operation(summary = "Proxy GET", description = "Proxy GET requests to SIS parent-portal API")
    @GetMapping("/{**path}")
    public ResponseEntity<Map<String, Object>> proxyGet(
            @PathVariable String path,
            HttpServletRequest httpRequest) {

        return proxyToSis(HttpMethod.GET, path, null, httpRequest);
    }

    @Operation(summary = "Proxy POST", description = "Proxy POST requests to SIS parent-portal API")
    @PostMapping("/{**path}")
    public ResponseEntity<Map<String, Object>> proxyPost(
            @PathVariable String path,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest httpRequest) {

        // Don't proxy device/* endpoints (they're handled above)
        if (path.startsWith("device/")) {
            return ResponseEntity.notFound().build();
        }

        return proxyToSis(HttpMethod.POST, path, body, httpRequest);
    }

    @Operation(summary = "Proxy PUT", description = "Proxy PUT requests to SIS parent-portal API")
    @PutMapping("/{**path}")
    public ResponseEntity<Map<String, Object>> proxyPut(
            @PathVariable String path,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest httpRequest) {

        return proxyToSis(HttpMethod.PUT, path, body, httpRequest);
    }

    // ========================================================================
    // INTERNAL PROXY LOGIC
    // ========================================================================

    private ResponseEntity<Map<String, Object>> proxyToSis(
            HttpMethod method, String path, Map<String, Object> body, HttpServletRequest httpRequest) {

        String sisPath = "/api/parent-portal/" + path;
        log.debug("PARENT_PORTAL_GW: Proxying {} {} -> SIS {}", method, path, sisPath);

        try {
            Map<String, Object> sisResponse = callSis(method, sisPath, body, httpRequest);
            return ResponseEntity.ok(sisResponse);

        } catch (WebClientResponseException e) {
            log.warn("PARENT_PORTAL_GW: SIS error for {} {}: {} {}",
                    method, path, e.getStatusCode(), e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "SIS error: " + e.getStatusCode());
            return ResponseEntity.status(e.getStatusCode()).body(error);

        } catch (Exception e) {
            log.error("PARENT_PORTAL_GW: Proxy error for {} {}: {}", method, path, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("success", false, "error", "Guardian cannot reach SIS server"));
        }
    }

    private Map<String, Object> callSis(HttpMethod method, String path, Object body) {
        return callSis(method, path, body, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callSis(HttpMethod method, String path, Object body, HttpServletRequest httpRequest) {
        String sisBaseUrl = properties.getSis().getApiUrl();
        String apiKey = properties.getSis().getApiKey();

        WebClient.Builder builder = webClientBuilder.clone().baseUrl(sisBaseUrl);

        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("X-API-Key", apiKey);
        }

        // Mark request as guardian-verified for SIS-side filter
        builder.defaultHeader("X-Guardian-Verified", "true");

        // Forward authorization header if present
        if (httpRequest != null) {
            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader != null) {
                builder.defaultHeader("Authorization", authHeader);
            }
        }

        WebClient client = builder.build();

        WebClient.RequestBodySpec requestSpec = client.method(method)
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON);

        WebClient.ResponseSpec responseSpec;
        if (body != null && (method == HttpMethod.POST || method == HttpMethod.PUT)) {
            responseSpec = requestSpec.bodyValue(body).retrieve();
        } else {
            responseSpec = requestSpec.retrieve();
        }

        Map<String, Object> result = responseSpec
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block(REQUEST_TIMEOUT);

        return result != null ? result : Map.of();
    }
}
