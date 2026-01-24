package com.heronix.guardian.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.heronix.guardian.adapter.VendorAdapter;
import com.heronix.guardian.model.domain.VendorCredential;
import com.heronix.guardian.model.dto.GradeUpdateDTO;
import com.heronix.guardian.model.dto.InboundGradeDTO;
import com.heronix.guardian.model.dto.TokenizedCourseDTO;
import com.heronix.guardian.model.dto.TokenizedStudentDTO;
import com.heronix.guardian.model.enums.VendorType;
import com.heronix.guardian.repository.VendorCredentialRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates sync operations between Heronix SIS and third-party vendors.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncOrchestrationService {

    private final VendorCredentialRepository credentialRepository;
    private final DataTranslationService translationService;
    private final List<VendorAdapter> vendorAdapters;

    /**
     * Sync job result.
     */
    public record SyncJobResult(
            String jobId,
            boolean success,
            int recordsProcessed,
            int recordsFailed,
            String message,
            LocalDateTime startedAt,
            LocalDateTime completedAt
    ) {
        public static SyncJobResult success(String jobId, int processed, LocalDateTime started) {
            return new SyncJobResult(jobId, true, processed, 0,
                    "Sync completed successfully", started, LocalDateTime.now());
        }

        public static SyncJobResult partial(String jobId, int processed, int failed, LocalDateTime started) {
            return new SyncJobResult(jobId, processed > 0, processed, failed,
                    "Sync completed with some failures", started, LocalDateTime.now());
        }

        public static SyncJobResult failure(String jobId, String message, LocalDateTime started) {
            return new SyncJobResult(jobId, false, 0, 0,
                    message, started, LocalDateTime.now());
        }
    }

    /**
     * Push students to a vendor.
     */
    @Async
    @Transactional
    public CompletableFuture<SyncJobResult> pushStudentsAsync(
            Long vendorCredentialId,
            List<TokenizedStudentDTO> students) {

        String jobId = UUID.randomUUID().toString();
        LocalDateTime startedAt = LocalDateTime.now();

        log.info("Starting student push job {} for vendor {}", jobId, vendorCredentialId);

        try {
            VendorCredential credential = credentialRepository.findById(vendorCredentialId)
                    .orElseThrow(() -> new RuntimeException("Vendor not found: " + vendorCredentialId));

            VendorAdapter adapter = findAdapter(credential.getVendorType());
            if (adapter == null) {
                return CompletableFuture.completedFuture(
                        SyncJobResult.failure(jobId, "No adapter for " + credential.getVendorType(), startedAt));
            }

            var result = adapter.pushStudents(students, credential);

            credential.recordSyncSuccess();
            credentialRepository.save(credential);

            if (result.success()) {
                return CompletableFuture.completedFuture(
                        SyncJobResult.success(jobId, result.recordsProcessed(), startedAt));
            } else {
                return CompletableFuture.completedFuture(
                        SyncJobResult.partial(jobId, result.recordsProcessed(), result.recordsFailed(), startedAt));
            }

        } catch (Exception e) {
            log.error("Student push job {} failed: {}", jobId, e.getMessage());
            return CompletableFuture.completedFuture(
                    SyncJobResult.failure(jobId, e.getMessage(), startedAt));
        }
    }

    /**
     * Push courses to a vendor.
     */
    @Async
    @Transactional
    public CompletableFuture<SyncJobResult> pushCoursesAsync(
            Long vendorCredentialId,
            List<TokenizedCourseDTO> courses) {

        String jobId = UUID.randomUUID().toString();
        LocalDateTime startedAt = LocalDateTime.now();

        log.info("Starting course push job {} for vendor {}", jobId, vendorCredentialId);

        try {
            VendorCredential credential = credentialRepository.findById(vendorCredentialId)
                    .orElseThrow(() -> new RuntimeException("Vendor not found: " + vendorCredentialId));

            VendorAdapter adapter = findAdapter(credential.getVendorType());
            if (adapter == null) {
                return CompletableFuture.completedFuture(
                        SyncJobResult.failure(jobId, "No adapter for " + credential.getVendorType(), startedAt));
            }

            var result = adapter.pushCourses(courses, credential);

            credential.recordSyncSuccess();
            credentialRepository.save(credential);

            if (result.success()) {
                return CompletableFuture.completedFuture(
                        SyncJobResult.success(jobId, result.recordsProcessed(), startedAt));
            } else {
                return CompletableFuture.completedFuture(
                        SyncJobResult.partial(jobId, result.recordsProcessed(), result.recordsFailed(), startedAt));
            }

        } catch (Exception e) {
            log.error("Course push job {} failed: {}", jobId, e.getMessage());
            return CompletableFuture.completedFuture(
                    SyncJobResult.failure(jobId, e.getMessage(), startedAt));
        }
    }

    /**
     * Push enrollments to a vendor.
     */
    @Async
    @Transactional
    public CompletableFuture<SyncJobResult> pushEnrollmentsAsync(
            Long vendorCredentialId,
            List<VendorAdapter.EnrollmentPair> enrollments) {

        String jobId = UUID.randomUUID().toString();
        LocalDateTime startedAt = LocalDateTime.now();

        log.info("Starting enrollment push job {} for vendor {}", jobId, vendorCredentialId);

        try {
            VendorCredential credential = credentialRepository.findById(vendorCredentialId)
                    .orElseThrow(() -> new RuntimeException("Vendor not found: " + vendorCredentialId));

            VendorAdapter adapter = findAdapter(credential.getVendorType());
            if (adapter == null) {
                return CompletableFuture.completedFuture(
                        SyncJobResult.failure(jobId, "No adapter for " + credential.getVendorType(), startedAt));
            }

            var result = adapter.pushEnrollments(enrollments, credential);

            credential.recordSyncSuccess();
            credentialRepository.save(credential);

            if (result.success()) {
                return CompletableFuture.completedFuture(
                        SyncJobResult.success(jobId, result.recordsProcessed(), startedAt));
            } else {
                return CompletableFuture.completedFuture(
                        SyncJobResult.partial(jobId, result.recordsProcessed(), result.recordsFailed(), startedAt));
            }

        } catch (Exception e) {
            log.error("Enrollment push job {} failed: {}", jobId, e.getMessage());
            return CompletableFuture.completedFuture(
                    SyncJobResult.failure(jobId, e.getMessage(), startedAt));
        }
    }

    /**
     * Pull grades from a vendor.
     */
    @Transactional
    public List<GradeUpdateDTO> pullGrades(Long vendorCredentialId, String courseToken) {
        log.info("Pulling grades from vendor {} for course {}", vendorCredentialId, courseToken);

        VendorCredential credential = credentialRepository.findById(vendorCredentialId)
                .orElseThrow(() -> new RuntimeException("Vendor not found: " + vendorCredentialId));

        VendorAdapter adapter = findAdapter(credential.getVendorType());
        if (adapter == null) {
            throw new RuntimeException("No adapter for " + credential.getVendorType());
        }

        // Fetch grades from vendor (with tokens)
        List<InboundGradeDTO> inboundGrades = adapter.fetchGrades(credential, courseToken, null);

        // Translate tokens to real IDs
        List<GradeUpdateDTO> translatedGrades = translationService.translateInboundGradesBulk(
                inboundGrades, credential.getVendorType());

        log.info("Pulled and translated {} grades from vendor {}", translatedGrades.size(), vendorCredentialId);

        return translatedGrades;
    }

    /**
     * Full sync for a vendor - push all data and pull grades.
     */
    @Async
    @Transactional
    public CompletableFuture<SyncJobResult> fullSyncAsync(
            Long vendorCredentialId,
            List<TokenizedStudentDTO> students,
            List<TokenizedCourseDTO> courses,
            List<VendorAdapter.EnrollmentPair> enrollments) {

        String jobId = UUID.randomUUID().toString();
        LocalDateTime startedAt = LocalDateTime.now();

        log.info("Starting full sync job {} for vendor {}", jobId, vendorCredentialId);

        try {
            VendorCredential credential = credentialRepository.findById(vendorCredentialId)
                    .orElseThrow(() -> new RuntimeException("Vendor not found: " + vendorCredentialId));

            VendorAdapter adapter = findAdapter(credential.getVendorType());
            if (adapter == null) {
                return CompletableFuture.completedFuture(
                        SyncJobResult.failure(jobId, "No adapter for " + credential.getVendorType(), startedAt));
            }

            int totalProcessed = 0;
            int totalFailed = 0;

            // Push students
            if (students != null && !students.isEmpty()) {
                var result = adapter.pushStudents(students, credential);
                totalProcessed += result.recordsProcessed();
                totalFailed += result.recordsFailed();
            }

            // Push courses
            if (courses != null && !courses.isEmpty()) {
                var result = adapter.pushCourses(courses, credential);
                totalProcessed += result.recordsProcessed();
                totalFailed += result.recordsFailed();
            }

            // Push enrollments
            if (enrollments != null && !enrollments.isEmpty()) {
                var result = adapter.pushEnrollments(enrollments, credential);
                totalProcessed += result.recordsProcessed();
                totalFailed += result.recordsFailed();
            }

            credential.recordSyncSuccess();
            credentialRepository.save(credential);

            if (totalFailed == 0) {
                return CompletableFuture.completedFuture(
                        SyncJobResult.success(jobId, totalProcessed, startedAt));
            } else {
                return CompletableFuture.completedFuture(
                        SyncJobResult.partial(jobId, totalProcessed, totalFailed, startedAt));
            }

        } catch (Exception e) {
            log.error("Full sync job {} failed: {}", jobId, e.getMessage());
            return CompletableFuture.completedFuture(
                    SyncJobResult.failure(jobId, e.getMessage(), startedAt));
        }
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private VendorAdapter findAdapter(VendorType type) {
        return vendorAdapters.stream()
                .filter(adapter -> adapter.getVendorType() == type)
                .findFirst()
                .orElse(null);
    }
}
