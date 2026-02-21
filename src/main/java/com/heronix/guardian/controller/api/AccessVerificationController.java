package com.heronix.guardian.controller.api;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.heronix.guardian.config.GuardianProperties;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * REST API for verifying parent-student access relationships.
 *
 * Called by Heronix-Talk's HallPassPortalController to verify that a parent
 * has an active relationship with a student before granting access to
 * student data (hall pass analytics, etc.).
 *
 * Delegates to Heronix-SIS-Server's ParentGuardianApiController for the
 * actual relationship lookup, since Guardian does not store parent-child
 * relationship data directly.
 *
 * @author Heronix Development Team
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/v1/guardian")
@Slf4j
@Tag(name = "Access Verification", description = "APIs for verifying parent-student access relationships")
public class AccessVerificationController {

    private final GuardianProperties guardianProperties;
    private final RestTemplate restTemplate;

    public AccessVerificationController(GuardianProperties guardianProperties) {
        this.guardianProperties = guardianProperties;
        this.restTemplate = new RestTemplate();
    }

    @GetMapping("/verify-access")
    @Operation(summary = "Verify parent-student relationship",
               description = "Checks with Heronix-SIS whether a parent has an active relationship with a student")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Relationship verified"),
        @ApiResponse(responseCode = "403", description = "No active relationship found"),
        @ApiResponse(responseCode = "404", description = "Parent or student not found"),
        @ApiResponse(responseCode = "503", description = "SIS verification service unavailable")
    })
    public ResponseEntity<Map<String, Object>> verifyAccess(
            @Parameter(description = "Parent/Guardian ID") @RequestParam String parentId,
            @Parameter(description = "Student ID") @RequestParam String studentId,
            @RequestHeader(value = "X-Guardian-Token", required = false) String guardianToken) {

        log.info("Verifying parent-student access: parent={}, student={}", parentId, studentId);

        // Validate IDs are numeric to prevent path traversal / SSRF
        if (!parentId.matches("^\\d+$") || !studentId.matches("^\\d+$")) {
            log.warn("Invalid ID format: parent={}, student={}", parentId, studentId);
            return ResponseEntity.badRequest()
                    .body(Map.of("verified", false, "error", "Invalid parentId or studentId format"));
        }

        try {
            String sisUrl = guardianProperties.getSis().getApiUrl()
                    + "/api/parent-guardian/" + parentId + "/verify-student/" + studentId;

            ResponseEntity<Map> sisResponse = restTemplate.getForEntity(sisUrl, Map.class);

            if (sisResponse.getStatusCode().is2xxSuccessful() && sisResponse.getBody() != null) {
                Map<String, Object> result = new HashMap<>(sisResponse.getBody());
                result.put("verifiedBy", "heronix-guardian");
                result.put("verifiedAt", LocalDateTime.now().toString());
                return ResponseEntity.ok(result);
            }

            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("verified", false, "error", "Parent-student relationship not verified"));

        } catch (HttpClientErrorException e) {
            log.warn("SIS verification returned {}: parent={}, student={}", e.getStatusCode(), parentId, studentId);
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("verified", false, "error", "Relationship not found"));
        } catch (Exception e) {
            log.error("Failed to verify parent-student access: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("verified", false, "error", "SIS verification service unavailable"));
        }
    }
}
