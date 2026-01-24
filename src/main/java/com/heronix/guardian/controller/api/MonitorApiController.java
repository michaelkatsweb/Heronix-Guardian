package com.heronix.guardian.controller.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.heronix.guardian.model.domain.DataAccessRequest;
import com.heronix.guardian.model.domain.DataAccessRequest.RequestStatus;
import com.heronix.guardian.model.domain.TokenMapping;
import com.heronix.guardian.model.dto.DashboardStatsDTO;
import com.heronix.guardian.model.enums.VendorType;
import com.heronix.guardian.service.MonitoringService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST API Controller for Guardian Monitor Desktop Application.
 *
 * Provides endpoints for:
 * - Dashboard statistics
 * - Request listing and filtering
 * - Request approval/denial
 * - Token mapping retrieval
 * - Data export
 *
 * @author Heronix Development Team
 * @version 2.0.0
 */
@RestController
@RequestMapping("/api/v1/monitor")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Monitor API", description = "Endpoints for Guardian Monitor Desktop Application")
public class MonitorApiController {

    private final MonitoringService monitoringService;

    // ========================================================================
    // DASHBOARD
    // ========================================================================

    @GetMapping("/dashboard/stats")
    @Operation(summary = "Get dashboard statistics")
    public ResponseEntity<DashboardStatsDTO> getDashboardStats() {
        log.debug("API: Getting dashboard stats");
        var stats = monitoringService.getDashboardStats();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/dashboard/recent")
    @Operation(summary = "Get recent requests for dashboard")
    public ResponseEntity<List<DataAccessRequest>> getRecentRequests(
            @RequestParam(defaultValue = "7") int days) {
        log.debug("API: Getting recent requests for {} days", days);
        var requests = monitoringService.getRecentRequests(days);
        return ResponseEntity.ok(requests);
    }

    // ========================================================================
    // REQUEST LISTING
    // ========================================================================

    @GetMapping("/requests")
    @Operation(summary = "Get all requests with pagination and filtering")
    public ResponseEntity<Page<DataAccessRequest>> getRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) String search) {

        log.debug("API: Getting requests - page={}, size={}, status={}, vendor={}, search={}",
                page, size, status, vendor, search);

        Page<DataAccessRequest> requests;

        if (search != null && !search.isBlank()) {
            requests = monitoringService.searchRequests(search, page, size);
        } else if (status != null && !status.isBlank()) {
            RequestStatus requestStatus = RequestStatus.valueOf(status.toUpperCase());
            requests = monitoringService.getRequestsByStatus(requestStatus, page, size);
        } else if (vendor != null && !vendor.isBlank()) {
            VendorType vendorType = VendorType.valueOf(vendor.toUpperCase());
            requests = monitoringService.getRequestsByVendor(vendorType, page, size);
        } else {
            requests = monitoringService.getAllRequests(page, size);
        }

        return ResponseEntity.ok(requests);
    }

    @GetMapping("/requests/pending")
    @Operation(summary = "Get all pending approval requests")
    public ResponseEntity<List<DataAccessRequest>> getPendingApprovals() {
        log.debug("API: Getting pending approvals");
        var requests = monitoringService.getPendingApprovals();
        return ResponseEntity.ok(requests);
    }

    // ========================================================================
    // REQUEST DETAILS
    // ========================================================================

    @GetMapping("/requests/{requestId}")
    @Operation(summary = "Get single request details")
    public ResponseEntity<DataAccessRequest> getRequestDetails(
            @PathVariable String requestId) {
        log.debug("API: Getting request details for {}", requestId);

        return monitoringService.getRequestDetails(requestId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/requests/{requestId}/tokens")
    @Operation(summary = "Get token mappings for a request")
    public ResponseEntity<List<TokenMapping>> getTokenMappings(
            @PathVariable String requestId) {
        log.debug("API: Getting token mappings for request {}", requestId);

        var requestOpt = monitoringService.getRequestDetails(requestId);
        if (requestOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var mappings = monitoringService.getTokenMappings(requestOpt.get().getId());
        return ResponseEntity.ok(mappings);
    }

    // ========================================================================
    // APPROVAL ACTIONS
    // ========================================================================

    @PostMapping("/requests/{requestId}/approve")
    @Operation(summary = "Approve a pending request")
    public ResponseEntity<Map<String, Object>> approveRequest(
            @PathVariable String requestId,
            @RequestBody ApprovalRequest approvalRequest) {

        log.info("API: Approving request {} by {}", requestId, approvalRequest.approvedBy());

        try {
            monitoringService.approveRequest(
                    requestId,
                    approvalRequest.approvedBy(),
                    approvalRequest.notes()
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Request approved successfully",
                    "requestId", requestId
            ));
        } catch (Exception e) {
            log.error("API: Failed to approve request {}: {}", requestId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to approve: " + e.getMessage(),
                    "requestId", requestId
            ));
        }
    }

    @PostMapping("/requests/{requestId}/deny")
    @Operation(summary = "Deny a pending request")
    public ResponseEntity<Map<String, Object>> denyRequest(
            @PathVariable String requestId,
            @RequestBody DenialRequest denialRequest) {

        log.warn("API: Denying request {} by {} - Reason: {}",
                requestId, denialRequest.deniedBy(), denialRequest.reason());

        try {
            monitoringService.denyRequest(
                    requestId,
                    denialRequest.deniedBy(),
                    denialRequest.reason()
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Request denied",
                    "requestId", requestId
            ));
        } catch (Exception e) {
            log.error("API: Failed to deny request {}: {}", requestId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to deny: " + e.getMessage(),
                    "requestId", requestId
            ));
        }
    }

    @PostMapping("/requests/{requestId}/notes")
    @Operation(summary = "Add notes to a request")
    public ResponseEntity<Map<String, Object>> addNotes(
            @PathVariable String requestId,
            @RequestBody NotesRequest notesRequest) {

        log.debug("API: Adding notes to request {}", requestId);

        try {
            monitoringService.addNotes(
                    requestId,
                    notesRequest.notes(),
                    notesRequest.addedBy()
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Notes added",
                    "requestId", requestId
            ));
        } catch (Exception e) {
            log.error("API: Failed to add notes to request {}: {}", requestId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to add notes: " + e.getMessage(),
                    "requestId", requestId
            ));
        }
    }

    // ========================================================================
    // EXPORT
    // ========================================================================

    @GetMapping("/export/csv")
    @Operation(summary = "Export requests to CSV")
    public ResponseEntity<byte[]> exportCSV(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(required = false) String vendor) {

        log.info("API: Exporting requests to CSV - {} to {}, vendor: {}",
                startDate, endDate, vendor);

        LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
        LocalDateTime end = LocalDate.parse(endDate).atTime(23, 59, 59);

        VendorType vendorType = vendor != null && !vendor.isBlank()
                ? VendorType.valueOf(vendor.toUpperCase())
                : null;

        byte[] csvData = monitoringService.exportToCSV(start, end, vendorType);

        String filename = String.format("guardian-requests-%s-to-%s.csv", startDate, endDate);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvData);
    }

    @GetMapping("/requests/{requestId}/export/tokens")
    @Operation(summary = "Export token mappings for a request to CSV")
    public ResponseEntity<byte[]> exportRequestTokens(@PathVariable String requestId) {
        log.info("API: Exporting tokens for request {}", requestId);

        var requestOpt = monitoringService.getRequestDetails(requestId);

        if (requestOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        byte[] csvData = monitoringService.exportTokenMappingsToCSV(requestOpt.get().getId());

        String filename = String.format("tokens-%s.csv", requestId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvData);
    }

    // ========================================================================
    // REQUEST/RESPONSE RECORDS
    // ========================================================================

    public record ApprovalRequest(
            String approvedBy,
            String notes
    ) {}

    public record DenialRequest(
            String deniedBy,
            String reason
    ) {}

    public record NotesRequest(
            String notes,
            String addedBy
    ) {}
}
