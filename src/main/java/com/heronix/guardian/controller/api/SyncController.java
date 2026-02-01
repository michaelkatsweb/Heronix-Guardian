package com.heronix.guardian.controller.api;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.heronix.guardian.adapter.VendorAdapter;
import com.heronix.guardian.model.dto.GradeUpdateDTO;
import com.heronix.guardian.model.dto.TokenizedCourseDTO;
import com.heronix.guardian.model.dto.TokenizedStudentDTO;
import com.heronix.guardian.service.SyncOrchestrationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST API for sync operations.
 */
@RestController
@RequestMapping("/api/v1/guardian/sync")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Sync Operations", description = "APIs for syncing data with vendors")
public class SyncController {

    private final SyncOrchestrationService syncService;

    @PostMapping("/{vendorId}/students")
    @Operation(summary = "Push students", description = "Push tokenized students to a vendor")
    @ApiResponse(responseCode = "202", description = "Sync job started")
    public ResponseEntity<SyncJobResponse> pushStudents(
            @PathVariable Long vendorId,
            @RequestBody List<TokenizedStudentDTO> students) {

        log.info("Starting student sync to vendor {} with {} students", vendorId, students.size());

        CompletableFuture<SyncOrchestrationService.SyncJobResult> future =
                syncService.pushStudentsAsync(vendorId, students);

        // Return immediately with job ID
        String jobId = UUID.randomUUID().toString();
        return ResponseEntity.accepted().body(new SyncJobResponse(
                jobId, "STARTED", "Student sync job started", students.size(), 0));
    }

    @PostMapping("/{vendorId}/courses")
    @Operation(summary = "Push courses", description = "Push tokenized courses to a vendor")
    @ApiResponse(responseCode = "202", description = "Sync job started")
    public ResponseEntity<SyncJobResponse> pushCourses(
            @PathVariable Long vendorId,
            @RequestBody List<TokenizedCourseDTO> courses) {

        log.info("Starting course sync to vendor {} with {} courses", vendorId, courses.size());

        CompletableFuture<SyncOrchestrationService.SyncJobResult> future =
                syncService.pushCoursesAsync(vendorId, courses);

        String jobId = UUID.randomUUID().toString();
        return ResponseEntity.accepted().body(new SyncJobResponse(
                jobId, "STARTED", "Course sync job started", courses.size(), 0));
    }

    @PostMapping("/{vendorId}/enrollments")
    @Operation(summary = "Push enrollments", description = "Push enrollments to a vendor")
    @ApiResponse(responseCode = "202", description = "Sync job started")
    public ResponseEntity<SyncJobResponse> pushEnrollments(
            @PathVariable Long vendorId,
            @RequestBody List<EnrollmentRequest> enrollments) {

        log.info("Starting enrollment sync to vendor {} with {} enrollments", vendorId, enrollments.size());

        List<VendorAdapter.EnrollmentPair> pairs = enrollments.stream()
                .map(e -> new VendorAdapter.EnrollmentPair(e.studentToken, e.courseToken, e.role))
                .toList();

        CompletableFuture<SyncOrchestrationService.SyncJobResult> future =
                syncService.pushEnrollmentsAsync(vendorId, pairs);

        String jobId = UUID.randomUUID().toString();
        return ResponseEntity.accepted().body(new SyncJobResponse(
                jobId, "STARTED", "Enrollment sync job started", enrollments.size(), 0));
    }

    @GetMapping("/{vendorId}/grades")
    @Operation(summary = "Pull grades", description = "Pull grades from a vendor")
    @ApiResponse(responseCode = "200", description = "Grades returned")
    public ResponseEntity<List<GradeUpdateDTO>> pullGrades(
            @PathVariable Long vendorId,
            @RequestParam(required = false) String courseToken) {

        log.info("Pulling grades from vendor {} for course {}", vendorId, courseToken);

        List<GradeUpdateDTO> grades = syncService.pullGrades(vendorId, courseToken);

        return ResponseEntity.ok(grades);
    }

    @PostMapping("/{vendorId}/full")
    @Operation(summary = "Full sync", description = "Perform a full bidirectional sync")
    @ApiResponse(responseCode = "202", description = "Sync job started")
    public ResponseEntity<SyncJobResponse> fullSync(
            @PathVariable Long vendorId,
            @RequestBody FullSyncRequest request) {

        log.info("Starting full sync to vendor {}", vendorId);

        List<VendorAdapter.EnrollmentPair> pairs = request.enrollments != null
                ? request.enrollments.stream()
                        .map(e -> new VendorAdapter.EnrollmentPair(e.studentToken, e.courseToken, e.role))
                        .toList()
                : List.of();

        CompletableFuture<SyncOrchestrationService.SyncJobResult> future =
                syncService.fullSyncAsync(vendorId, request.students, request.courses, pairs);

        String jobId = UUID.randomUUID().toString();
        int totalRecords = (request.students != null ? request.students.size() : 0) +
                           (request.courses != null ? request.courses.size() : 0) +
                           (request.enrollments != null ? request.enrollments.size() : 0);

        return ResponseEntity.accepted().body(new SyncJobResponse(
                jobId, "STARTED", "Full sync job started", totalRecords, 0));
    }

    // ========================================================================
    // REQUEST/RESPONSE TYPES
    // ========================================================================

    public record EnrollmentRequest(
            String studentToken,
            String courseToken,
            String role
    ) {}

    public record FullSyncRequest(
            List<TokenizedStudentDTO> students,
            List<TokenizedCourseDTO> courses,
            List<EnrollmentRequest> enrollments
    ) {}

    public record SyncJobResponse(
            String jobId,
            String status,
            String message,
            int recordsTotal,
            int recordsProcessed
    ) {}
}
