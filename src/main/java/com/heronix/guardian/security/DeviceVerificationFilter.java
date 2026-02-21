package com.heronix.guardian.security;

import com.heronix.guardian.config.GuardianProperties;
import com.heronix.guardian.service.ParentPortalDeviceVerificationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * OncePerRequestFilter on /api/v1/parent-portal/** (except /device/*).
 * Extracts X-Device-Id and X-Public-Key-Hash headers, verifies the device
 * via ParentPortalDeviceVerificationService, and returns 403 if invalid.
 *
 * @author Heronix Educational Systems LLC
 * @since February 2026
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeviceVerificationFilter extends OncePerRequestFilter {

    private final ParentPortalDeviceVerificationService verificationService;
    private final GuardianProperties properties;

    private static final String PARENT_PORTAL_PATH = "/api/v1/parent-portal/";
    private static final String DEVICE_PATH = "/api/v1/parent-portal/device/";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        // Only filter parent portal paths
        if (!path.startsWith(PARENT_PORTAL_PATH)) {
            return true;
        }

        // Skip device registration and status endpoints
        if (path.startsWith(DEVICE_PATH)) {
            return true;
        }

        // Skip if parent portal gateway is not configured to require device registration
        GuardianProperties.ParentPortalConfig ppConfig = properties.getParentPortal();
        if (ppConfig != null && !ppConfig.isRequireDeviceRegistration()) {
            return true;
        }

        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String deviceId = request.getHeader("X-Device-Id");
        String publicKeyHash = request.getHeader("X-Public-Key-Hash");

        if (deviceId == null || deviceId.isBlank() || publicKeyHash == null || publicKeyHash.isBlank()) {
            log.warn("DEVICE_FILTER: Missing device headers for {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Device headers required\",\"code\":\"DEVICE_HEADERS_MISSING\"}");
            return;
        }

        boolean verified = verificationService.verifyDevice(deviceId, publicKeyHash);

        if (!verified) {
            log.warn("DEVICE_FILTER: Device verification failed for {} on {}",
                    deviceId.substring(0, Math.min(8, deviceId.length())), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Device not verified\",\"code\":\"DEVICE_NOT_VERIFIED\"}");
            return;
        }

        log.debug("DEVICE_FILTER: Device {} verified for {}", deviceId.substring(0, Math.min(8, deviceId.length())), request.getRequestURI());
        filterChain.doFilter(request, response);
    }
}
