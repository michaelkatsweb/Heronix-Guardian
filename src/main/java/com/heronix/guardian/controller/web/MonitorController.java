package com.heronix.guardian.controller.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.heronix.guardian.model.domain.DataAccessRequest;
import com.heronix.guardian.model.domain.DataAccessRequest.RequestStatus;
import com.heronix.guardian.model.enums.VendorType;
import com.heronix.guardian.service.MonitoringService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Web Controller for the Guardian Monitoring Dashboard.
 *
 * Provides a visual interface for IT staff to:
 * - Monitor all vendor data requests
 * - View token mappings for each request
 * - Approve/deny pending requests
 * - Export data for compliance reporting
 *
 * @author Heronix Development Team
 * @version 2.0.0
 */
@Controller
@RequestMapping("/monitor")
@RequiredArgsConstructor
@Slf4j
public class MonitorController {

    private final MonitoringService monitoringService;

    // ========================================================================
    // DASHBOARD
    // ========================================================================

    /**
     * Main dashboard view.
     */
    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Model model) {
        var stats = monitoringService.getDashboardStats();
        var recentRequests = monitoringService.getRecentRequests(7);
        var pendingApprovals = monitoringService.getPendingApprovals();

        model.addAttribute("stats", stats);
        model.addAttribute("recentRequests", recentRequests);
        model.addAttribute("pendingApprovals", pendingApprovals);
        model.addAttribute("pageTitle", "Guardian Monitor - Dashboard");

        return "monitor/dashboard";
    }

    // ========================================================================
    // REQUEST LIST VIEWS
    // ========================================================================

    /**
     * View all requests with pagination and filtering.
     */
    @GetMapping("/requests")
    public String listRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) String search,
            Model model) {

        Page<DataAccessRequest> requests;

        if (search != null && !search.isBlank()) {
            requests = monitoringService.searchRequests(search, page, size);
            model.addAttribute("search", search);
        } else if (status != null && !status.isBlank()) {
            RequestStatus requestStatus = RequestStatus.valueOf(status.toUpperCase());
            requests = monitoringService.getRequestsByStatus(requestStatus, page, size);
            model.addAttribute("selectedStatus", status);
        } else if (vendor != null && !vendor.isBlank()) {
            VendorType vendorType = VendorType.valueOf(vendor.toUpperCase());
            requests = monitoringService.getRequestsByVendor(vendorType, page, size);
            model.addAttribute("selectedVendor", vendor);
        } else {
            requests = monitoringService.getAllRequests(page, size);
        }

        model.addAttribute("requests", requests);
        model.addAttribute("statuses", RequestStatus.values());
        model.addAttribute("vendors", VendorType.values());
        model.addAttribute("pageTitle", "Guardian Monitor - All Requests");

        return "monitor/requests";
    }

    /**
     * View pending approvals only.
     */
    @GetMapping("/requests/pending")
    public String pendingApprovals(Model model) {
        var pendingApprovals = monitoringService.getPendingApprovals();

        model.addAttribute("requests", pendingApprovals);
        model.addAttribute("pageTitle", "Guardian Monitor - Pending Approvals");

        return "monitor/pending";
    }

    // ========================================================================
    // REQUEST DETAILS
    // ========================================================================

    /**
     * View single request details with token mappings.
     */
    @GetMapping("/requests/{requestId}")
    public String requestDetails(@PathVariable String requestId, Model model) {
        var requestOpt = monitoringService.getRequestDetails(requestId);

        if (requestOpt.isEmpty()) {
            return "redirect:/monitor/requests?error=notfound";
        }

        DataAccessRequest request = requestOpt.get();
        var tokenMappings = monitoringService.getTokenMappings(request.getId());

        // Group token mappings by entity type for display
        var studentMappings = tokenMappings.stream()
                .filter(m -> m.getEntityType() == com.heronix.guardian.model.domain.TokenMapping.EntityType.STUDENT)
                .toList();
        var teacherMappings = tokenMappings.stream()
                .filter(m -> m.getEntityType() == com.heronix.guardian.model.domain.TokenMapping.EntityType.TEACHER)
                .toList();
        var courseMappings = tokenMappings.stream()
                .filter(m -> m.getEntityType() == com.heronix.guardian.model.domain.TokenMapping.EntityType.COURSE)
                .toList();

        model.addAttribute("request", request);
        model.addAttribute("tokenMappings", tokenMappings);
        model.addAttribute("studentMappings", studentMappings);
        model.addAttribute("teacherMappings", teacherMappings);
        model.addAttribute("courseMappings", courseMappings);
        model.addAttribute("pageTitle", "Request Details - " + requestId);

        return "monitor/details";
    }

    // ========================================================================
    // APPROVAL ACTIONS
    // ========================================================================

    /**
     * Approve a request.
     */
    @PostMapping("/requests/{requestId}/approve")
    public String approveRequest(
            @PathVariable String requestId,
            @RequestParam(required = false) String notes,
            @RequestParam(defaultValue = "IT Staff") String approvedBy,
            RedirectAttributes redirectAttributes) {

        try {
            monitoringService.approveRequest(requestId, approvedBy, notes);
            redirectAttributes.addFlashAttribute("success", "Request approved successfully");
            log.info("MONITOR-UI: Request {} approved by {}", requestId, approvedBy);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to approve: " + e.getMessage());
            log.error("MONITOR-UI: Failed to approve request {}: {}", requestId, e.getMessage());
        }

        return "redirect:/monitor/requests/" + requestId;
    }

    /**
     * Deny a request.
     */
    @PostMapping("/requests/{requestId}/deny")
    public String denyRequest(
            @PathVariable String requestId,
            @RequestParam String reason,
            @RequestParam(defaultValue = "IT Staff") String deniedBy,
            RedirectAttributes redirectAttributes) {

        try {
            monitoringService.denyRequest(requestId, deniedBy, reason);
            redirectAttributes.addFlashAttribute("success", "Request denied");
            log.warn("MONITOR-UI: Request {} denied by {} - Reason: {}", requestId, deniedBy, reason);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to deny: " + e.getMessage());
        }

        return "redirect:/monitor/requests/" + requestId;
    }

    /**
     * Add notes to a request.
     */
    @PostMapping("/requests/{requestId}/notes")
    public String addNotes(
            @PathVariable String requestId,
            @RequestParam String notes,
            @RequestParam(defaultValue = "IT Staff") String addedBy,
            RedirectAttributes redirectAttributes) {

        try {
            monitoringService.addNotes(requestId, notes, addedBy);
            redirectAttributes.addFlashAttribute("success", "Notes added");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to add notes: " + e.getMessage());
        }

        return "redirect:/monitor/requests/" + requestId;
    }

    // ========================================================================
    // EXPORT
    // ========================================================================

    /**
     * Export page.
     */
    @GetMapping("/export")
    public String exportPage(Model model) {
        model.addAttribute("vendors", VendorType.values());
        model.addAttribute("pageTitle", "Guardian Monitor - Export Data");
        return "monitor/export";
    }

    /**
     * Export requests to CSV.
     */
    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCSV(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(required = false) String vendor) {

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

    /**
     * Export token mappings for a single request.
     */
    @GetMapping("/requests/{requestId}/export")
    public ResponseEntity<byte[]> exportRequestTokens(@PathVariable String requestId) {
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
}
