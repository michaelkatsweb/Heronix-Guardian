package com.heronix.guardian.service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.heronix.guardian.model.domain.DataAccessRequest;
import com.heronix.guardian.model.domain.DataAccessRequest.DataType;
import com.heronix.guardian.model.domain.DataAccessRequest.Direction;
import com.heronix.guardian.model.domain.DataAccessRequest.RequestStatus;
import com.heronix.guardian.model.domain.TokenMapping;
import com.heronix.guardian.model.domain.TokenMapping.EntityType;
import com.heronix.guardian.model.dto.DashboardStatsDTO;
import com.heronix.guardian.model.enums.VendorType;
import com.heronix.guardian.repository.DataAccessRequestRepository;
import com.heronix.guardian.repository.TokenMappingRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for monitoring and managing data access requests.
 *
 * Provides functionality for IT staff to:
 * - View all vendor data requests
 * - Approve/deny requests
 * - Export data for compliance
 * - Generate statistics and reports
 *
 * @author Heronix Development Team
 * @version 2.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MonitoringService {

    private final DataAccessRequestRepository requestRepository;
    private final TokenMappingRepository tokenMappingRepository;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ========================================================================
    // DASHBOARD & STATISTICS
    // ========================================================================

    /**
     * Get dashboard statistics.
     */
    public DashboardStatsDTO getDashboardStats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime startOfWeek = now.minusDays(7);
        LocalDateTime startOfMonth = now.minusDays(30);

        long pendingApprovalCount = requestRepository.countByStatusAndRequiresApprovalTrue(RequestStatus.PENDING);
        long approvedCount = requestRepository.countByStatus(RequestStatus.APPROVED);
        long transmittedCount = requestRepository.countByStatus(RequestStatus.TRANSMITTED);
        long deniedCount = requestRepository.countByStatus(RequestStatus.DENIED);

        long canvasCount = requestRepository.countByVendorType(VendorType.CANVAS);
        long googleCount = requestRepository.countByVendorType(VendorType.GOOGLE_CLASSROOM);

        long totalRequests = requestRepository.count();

        // Time-based metrics
        long requestsToday = requestRepository.countByCreatedAtAfter(startOfToday);
        long requestsThisWeek = requestRepository.countByCreatedAtAfter(startOfWeek);
        long requestsThisMonth = requestRepository.countByCreatedAtAfter(startOfMonth);

        // Token statistics
        long totalTokens = tokenMappingRepository.count();
        long studentTokens = tokenMappingRepository.countByEntityType(EntityType.STUDENT);
        long teacherTokens = tokenMappingRepository.countByEntityType(EntityType.TEACHER);
        long courseTokens = tokenMappingRepository.countByEntityType(EntityType.COURSE);

        return DashboardStatsDTO.builder()
                .totalRequests(totalRequests)
                .pendingApprovalCount(pendingApprovalCount)
                .approvedCount(approvedCount)
                .transmittedCount(transmittedCount)
                .deniedCount(deniedCount)
                .canvasRequestCount(canvasCount)
                .googleClassroomRequestCount(googleCount)
                .totalTokensGenerated(totalTokens)
                .activeStudentTokens(studentTokens)
                .activeTeacherTokens(teacherTokens)
                .activeCourseTokens(courseTokens)
                .requestsToday(requestsToday)
                .requestsThisWeek(requestsThisWeek)
                .requestsThisMonth(requestsThisMonth)
                .generatedAt(now)
                .build();
    }

    /**
     * Get recent requests for dashboard.
     */
    public List<DataAccessRequest> getRecentRequests(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return requestRepository.findRecentRequests(since);
    }

    /**
     * Get requests requiring approval.
     */
    public List<DataAccessRequest> getPendingApprovals() {
        return requestRepository.findByStatusAndRequiresApprovalTrueOrderByCreatedAtDesc(RequestStatus.PENDING);
    }

    // ========================================================================
    // REQUEST MANAGEMENT
    // ========================================================================

    /**
     * Get all requests with pagination.
     */
    public Page<DataAccessRequest> getAllRequests(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return requestRepository.findAll(pageable);
    }

    /**
     * Get requests by status.
     */
    public Page<DataAccessRequest> getRequestsByStatus(RequestStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return requestRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    /**
     * Get requests by vendor.
     */
    public Page<DataAccessRequest> getRequestsByVendor(VendorType vendorType, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return requestRepository.findByVendorTypeOrderByCreatedAtDesc(vendorType, pageable);
    }

    /**
     * Get single request with token mappings.
     */
    @Transactional(readOnly = true)
    public Optional<DataAccessRequest> getRequestDetails(String requestId) {
        Optional<DataAccessRequest> request = requestRepository.findByRequestId(requestId);
        // Force load token mappings
        request.ifPresent(r -> r.getTokenMappings().size());
        return request;
    }

    /**
     * Get token mappings for a request.
     */
    public List<TokenMapping> getTokenMappings(Long requestId) {
        return tokenMappingRepository.findByDataAccessRequestIdOrderByEntityTypeAscDisplayIdentifierAsc(requestId);
    }

    /**
     * Search requests.
     */
    public Page<DataAccessRequest> searchRequests(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return requestRepository.searchRequests(query, pageable);
    }

    // ========================================================================
    // APPROVAL WORKFLOW
    // ========================================================================

    /**
     * Approve a data access request.
     */
    @Transactional
    public DataAccessRequest approveRequest(String requestId, String approvedBy, String notes) {
        DataAccessRequest request = requestRepository.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new IllegalStateException("Request is not pending: " + request.getStatus());
        }

        request.approve(approvedBy);
        if (notes != null && !notes.isBlank()) {
            request.setNotes(notes);
        }

        log.info("MONITOR: Request {} approved by {}", requestId, approvedBy);

        return requestRepository.save(request);
    }

    /**
     * Deny a data access request.
     */
    @Transactional
    public DataAccessRequest denyRequest(String requestId, String deniedBy, String reason) {
        DataAccessRequest request = requestRepository.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new IllegalStateException("Request is not pending: " + request.getStatus());
        }

        request.deny(deniedBy, reason);

        log.warn("MONITOR: Request {} denied by {} - Reason: {}", requestId, deniedBy, reason);

        return requestRepository.save(request);
    }

    /**
     * Add notes to a request.
     */
    @Transactional
    public DataAccessRequest addNotes(String requestId, String notes, String addedBy) {
        DataAccessRequest request = requestRepository.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));

        String existingNotes = request.getNotes() != null ? request.getNotes() : "";
        String timestamp = LocalDateTime.now().format(DATE_FORMAT);
        String newNote = String.format("\n[%s - %s]: %s", timestamp, addedBy, notes);

        request.setNotes(existingNotes + newNote);

        return requestRepository.save(request);
    }

    // ========================================================================
    // REQUEST CREATION (called by Guardian services)
    // ========================================================================

    /**
     * Create a new data access request for tracking.
     */
    @Transactional
    public DataAccessRequest createRequest(
            VendorType vendorType,
            String connectionName,
            DataType dataType,
            Direction direction,
            String description,
            boolean requiresApproval) {

        DataAccessRequest request = DataAccessRequest.builder()
                .vendorType(vendorType)
                .connectionName(connectionName)
                .dataType(dataType)
                .direction(direction)
                .description(description)
                .requiresApproval(requiresApproval)
                .status(requiresApproval ? RequestStatus.PENDING : RequestStatus.APPROVED)
                .autoApproved(!requiresApproval)
                .build();

        if (!requiresApproval) {
            request.setApprovedAt(LocalDateTime.now());
            request.setApprovedBy("SYSTEM (Auto-approved)");
        }

        request = requestRepository.save(request);

        log.info("MONITOR: Created request {} for {} {} (requires approval: {})",
                request.getRequestId(), vendorType, dataType, requiresApproval);

        return request;
    }

    /**
     * Add a token mapping to a request.
     */
    @Transactional
    public TokenMapping addTokenMapping(
            Long requestId,
            String tokenValue,
            EntityType entityType,
            String displayIdentifier,
            String context) {

        DataAccessRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));

        TokenMapping mapping = TokenMapping.builder()
                .tokenValue(tokenValue)
                .entityType(entityType)
                .displayIdentifier(displayIdentifier)
                .context(context)
                .build();

        request.addTokenMapping(mapping);
        request.setRecordCount((request.getRecordCount() != null ? request.getRecordCount() : 0) + 1);

        requestRepository.save(request);

        return mapping;
    }

    // ========================================================================
    // EXPORT FUNCTIONALITY
    // ========================================================================

    /**
     * Export requests to CSV format.
     */
    public byte[] exportToCSV(LocalDateTime startDate, LocalDateTime endDate, VendorType vendorType) {
        List<DataAccessRequest> requests;

        if (vendorType != null) {
            requests = requestRepository.findByVendorAndDateRange(vendorType, startDate, endDate);
        } else {
            requests = requestRepository.findByDateRange(startDate, endDate);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            // Header
            writer.println("Request ID,Vendor,Connection,Data Type,Direction,Status,Record Count," +
                          "Course,Teacher,Grade Levels,Created At,Approved By,Transmitted At,Notes");

            // Data rows
            for (DataAccessRequest request : requests) {
                writer.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                        request.getRequestId(),
                        request.getVendorType(),
                        nullSafe(request.getConnectionName()),
                        request.getDataType(),
                        request.getDirection(),
                        request.getStatus(),
                        request.getRecordCount() != null ? request.getRecordCount() : 0,
                        nullSafe(request.getCourseName()),
                        nullSafe(request.getTeacherDisplayName()),
                        nullSafe(request.getGradeLevels()),
                        request.getCreatedAt() != null ? request.getCreatedAt().format(DATE_FORMAT) : "",
                        nullSafe(request.getApprovedBy()),
                        request.getTransmittedAt() != null ? request.getTransmittedAt().format(DATE_FORMAT) : "",
                        nullSafe(request.getNotes()).replace("\"", "'").replace("\n", " ")
                );
            }
        }

        return baos.toByteArray();
    }

    /**
     * Export token mappings for a request to CSV.
     */
    public byte[] exportTokenMappingsToCSV(Long requestId) {
        List<TokenMapping> mappings = tokenMappingRepository
                .findByDataAccessRequestIdOrderByEntityTypeAscDisplayIdentifierAsc(requestId);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            // Header
            writer.println("Token,Entity Type,Display Identifier,Context,Created At,Transmitted");

            // Data rows
            for (TokenMapping mapping : mappings) {
                writer.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%s%n",
                        mapping.getTokenValue(),
                        mapping.getEntityType(),
                        mapping.getDisplayIdentifier(),
                        nullSafe(mapping.getContext()),
                        mapping.getCreatedAt() != null ? mapping.getCreatedAt().format(DATE_FORMAT) : "",
                        mapping.getTransmitted()
                );
            }
        }

        return baos.toByteArray();
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

}
