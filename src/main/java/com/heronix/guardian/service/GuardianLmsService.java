package com.heronix.guardian.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.heronix.guardian.adapter.VendorAdapter;
import com.heronix.guardian.gateway.GuardianGatewayService;
import com.heronix.guardian.model.domain.VendorCredential;
import com.heronix.guardian.model.dto.GradeUpdateDTO;
import com.heronix.guardian.model.dto.TokenizedCourseDTO;
import com.heronix.guardian.model.dto.TokenizedStudentDTO;
import com.heronix.guardian.model.enums.VendorType;
import com.heronix.guardian.repository.VendorCredentialRepository;
import com.heronix.guardian.service.SyncOrchestrationService.SyncJobResult;

import lombok.extern.slf4j.Slf4j;

/**
 * Guardian LMS Service - Main orchestration layer for LMS operations.
 *
 * Coordinates tokenization (via SIS bridge), gateway routing, and
 * vendor-specific adapters to provide a unified LMS integration API.
 *
 * Data Flow:
 * SIS → GuardianLmsService → SisTokenBridge → Gateway → Vendor
 *
 * @author Heronix Development Team
 * @version 2.0.0
 */
@Service
@Slf4j
public class GuardianLmsService {

    private final DataTranslationService translationService;
    private final SyncOrchestrationService syncService;
    private final VendorCredentialRepository credentialRepository;
    private final List<VendorAdapter> vendorAdapters;

    @Autowired(required = false)
    private SisTokenBridgeService sisTokenBridge;

    @Autowired(required = false)
    private GuardianGatewayService gatewayService;

    public GuardianLmsService(
            DataTranslationService translationService,
            SyncOrchestrationService syncService,
            VendorCredentialRepository credentialRepository,
            List<VendorAdapter> vendorAdapters) {

        this.translationService = translationService;
        this.syncService = syncService;
        this.credentialRepository = credentialRepository;
        this.vendorAdapters = vendorAdapters;

        log.info("GUARDIAN_LMS: Initialized");
    }

    /**
     * Test connection to a configured vendor.
     */
    public VendorAdapter.ConnectionTestResult testConnection(Long vendorCredentialId) {
        VendorCredential credential = credentialRepository.findById(vendorCredentialId)
                .orElseThrow(() -> new RuntimeException("Vendor credential not found: " + vendorCredentialId));

        VendorAdapter adapter = findAdapter(credential.getVendorType());
        if (adapter == null) {
            return VendorAdapter.ConnectionTestResult.failure(
                    "No adapter found for vendor type: " + credential.getVendorType());
        }

        log.info("GUARDIAN_LMS: Testing connection to {} ({})", credential.getConnectionName(), credential.getVendorType());
        return adapter.testConnection(credential);
    }

    /**
     * Sync students to a vendor.
     *
     * Flow:
     * 1. For each studentId, tokenize via SIS bridge (or local fallback)
     * 2. Build TokenizedStudentDTO list
     * 3. If gateway enabled, log/route through gateway
     * 4. Push to vendor via sync service
     */
    public CompletableFuture<SyncJobResult> syncStudents(
            Long vendorCredentialId,
            List<StudentSyncRequest> students) {

        String jobId = UUID.randomUUID().toString();
        LocalDateTime startedAt = LocalDateTime.now();

        log.info("GUARDIAN_LMS: Starting student sync job {} ({} students) for vendor {}",
                jobId, students.size(), vendorCredentialId);

        try {
            VendorCredential credential = credentialRepository.findById(vendorCredentialId)
                    .orElseThrow(() -> new RuntimeException("Vendor not found: " + vendorCredentialId));

            VendorType vendorType = credential.getVendorType();
            List<TokenizedStudentDTO> tokenizedStudents = new ArrayList<>();

            for (StudentSyncRequest student : students) {
                TokenizedStudentDTO tokenized = tokenizeStudent(student, vendorType);
                if (tokenized != null) {
                    tokenizedStudents.add(tokenized);
                }
            }

            if (tokenizedStudents.isEmpty()) {
                return CompletableFuture.completedFuture(
                        SyncJobResult.failure(jobId, "No students tokenized successfully", startedAt));
            }

            // Route through gateway if enabled
            if (gatewayService != null) {
                logGatewayTransmission("STUDENT_SYNC", tokenizedStudents.size(), vendorCredentialId);
            }

            return syncService.pushStudentsAsync(vendorCredentialId, tokenizedStudents);

        } catch (Exception e) {
            log.error("GUARDIAN_LMS: Student sync job {} failed: {}", jobId, e.getMessage());
            return CompletableFuture.completedFuture(
                    SyncJobResult.failure(jobId, e.getMessage(), startedAt));
        }
    }

    /**
     * Sync courses to a vendor.
     */
    public CompletableFuture<SyncJobResult> syncCourses(
            Long vendorCredentialId,
            List<DataTranslationService.CourseData> courses) {

        String jobId = UUID.randomUUID().toString();
        LocalDateTime startedAt = LocalDateTime.now();

        log.info("GUARDIAN_LMS: Starting course sync job {} ({} courses) for vendor {}",
                jobId, courses.size(), vendorCredentialId);

        try {
            VendorCredential credential = credentialRepository.findById(vendorCredentialId)
                    .orElseThrow(() -> new RuntimeException("Vendor not found: " + vendorCredentialId));

            VendorType vendorType = credential.getVendorType();
            List<TokenizedCourseDTO> tokenizedCourses = new ArrayList<>();

            for (DataTranslationService.CourseData course : courses) {
                TokenizedCourseDTO tokenized = translationService.tokenizeCourse(
                        course.id(), course.courseName(), course.courseCode(),
                        course.section(), course.subject(), course.gradeLevel(),
                        course.teacherId(), course.teacherFirstName(), course.teacherLastName(),
                        course.term(), vendorType);
                tokenizedCourses.add(tokenized);
            }

            if (gatewayService != null) {
                logGatewayTransmission("COURSE_SYNC", tokenizedCourses.size(), vendorCredentialId);
            }

            return syncService.pushCoursesAsync(vendorCredentialId, tokenizedCourses);

        } catch (Exception e) {
            log.error("GUARDIAN_LMS: Course sync job {} failed: {}", jobId, e.getMessage());
            return CompletableFuture.completedFuture(
                    SyncJobResult.failure(jobId, e.getMessage(), startedAt));
        }
    }

    /**
     * Sync enrollments to a vendor.
     */
    public CompletableFuture<SyncJobResult> syncEnrollments(
            Long vendorCredentialId,
            List<EnrollmentSyncRequest> enrollments) {

        String jobId = UUID.randomUUID().toString();
        LocalDateTime startedAt = LocalDateTime.now();

        log.info("GUARDIAN_LMS: Starting enrollment sync job {} ({} enrollments) for vendor {}",
                jobId, enrollments.size(), vendorCredentialId);

        try {
            VendorCredential credential = credentialRepository.findById(vendorCredentialId)
                    .orElseThrow(() -> new RuntimeException("Vendor not found: " + vendorCredentialId));

            VendorType vendorType = credential.getVendorType();
            List<VendorAdapter.EnrollmentPair> tokenizedEnrollments = new ArrayList<>();

            for (EnrollmentSyncRequest enrollment : enrollments) {
                String studentToken = getStudentToken(enrollment.studentId(), vendorType);
                String courseToken = translationService.tokenizeCourse(
                        enrollment.courseId(), null, null, null, null, null,
                        null, null, null, null, vendorType).getToken();

                tokenizedEnrollments.add(new VendorAdapter.EnrollmentPair(
                        studentToken, courseToken, enrollment.role()));
            }

            if (gatewayService != null) {
                logGatewayTransmission("ENROLLMENT_SYNC", tokenizedEnrollments.size(), vendorCredentialId);
            }

            return syncService.pushEnrollmentsAsync(vendorCredentialId, tokenizedEnrollments);

        } catch (Exception e) {
            log.error("GUARDIAN_LMS: Enrollment sync job {} failed: {}", jobId, e.getMessage());
            return CompletableFuture.completedFuture(
                    SyncJobResult.failure(jobId, e.getMessage(), startedAt));
        }
    }

    /**
     * Pull grades from a vendor and resolve tokens to real student IDs.
     */
    public List<GradeUpdateDTO> pullGrades(Long vendorCredentialId, String courseToken) {
        log.info("GUARDIAN_LMS: Pulling grades from vendor {} for course {}", vendorCredentialId, courseToken);
        return syncService.pullGrades(vendorCredentialId, courseToken);
    }

    /**
     * Full sync: students, courses, and enrollments.
     */
    public CompletableFuture<SyncJobResult> fullSync(
            Long vendorCredentialId,
            FullSyncRequest request) {

        String jobId = UUID.randomUUID().toString();
        LocalDateTime startedAt = LocalDateTime.now();

        log.info("GUARDIAN_LMS: Starting full sync job {} for vendor {}", jobId, vendorCredentialId);

        try {
            VendorCredential credential = credentialRepository.findById(vendorCredentialId)
                    .orElseThrow(() -> new RuntimeException("Vendor not found: " + vendorCredentialId));

            VendorType vendorType = credential.getVendorType();

            // Tokenize students
            List<TokenizedStudentDTO> tokenizedStudents = new ArrayList<>();
            if (request.students() != null) {
                for (StudentSyncRequest student : request.students()) {
                    TokenizedStudentDTO tokenized = tokenizeStudent(student, vendorType);
                    if (tokenized != null) {
                        tokenizedStudents.add(tokenized);
                    }
                }
            }

            // Tokenize courses
            List<TokenizedCourseDTO> tokenizedCourses = new ArrayList<>();
            if (request.courses() != null) {
                for (DataTranslationService.CourseData course : request.courses()) {
                    tokenizedCourses.add(translationService.tokenizeCourse(
                            course.id(), course.courseName(), course.courseCode(),
                            course.section(), course.subject(), course.gradeLevel(),
                            course.teacherId(), course.teacherFirstName(), course.teacherLastName(),
                            course.term(), vendorType));
                }
            }

            // Tokenize enrollments
            List<VendorAdapter.EnrollmentPair> tokenizedEnrollments = new ArrayList<>();
            if (request.enrollments() != null) {
                for (EnrollmentSyncRequest enrollment : request.enrollments()) {
                    String studentToken = getStudentToken(enrollment.studentId(), vendorType);
                    String courseToken = translationService.tokenizeCourse(
                            enrollment.courseId(), null, null, null, null, null,
                            null, null, null, null, vendorType).getToken();
                    tokenizedEnrollments.add(new VendorAdapter.EnrollmentPair(
                            studentToken, courseToken, enrollment.role()));
                }
            }

            if (gatewayService != null) {
                int total = tokenizedStudents.size() + tokenizedCourses.size() + tokenizedEnrollments.size();
                logGatewayTransmission("FULL_SYNC", total, vendorCredentialId);
            }

            return syncService.fullSyncAsync(vendorCredentialId,
                    tokenizedStudents, tokenizedCourses, tokenizedEnrollments);

        } catch (Exception e) {
            log.error("GUARDIAN_LMS: Full sync job {} failed: {}", jobId, e.getMessage());
            return CompletableFuture.completedFuture(
                    SyncJobResult.failure(jobId, e.getMessage(), startedAt));
        }
    }

    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================

    private TokenizedStudentDTO tokenizeStudent(StudentSyncRequest student, VendorType vendorType) {
        try {
            String tokenValue;
            if (sisTokenBridge != null) {
                tokenValue = sisTokenBridge.getOrCreateToken(student.studentId(), vendorType.name());
            } else {
                // Fallback to local DataTranslationService
                TokenizedStudentDTO dto = translationService.tokenizeStudent(
                        student.studentId(), student.firstName(), student.lastName(),
                        student.gradeLevel(), vendorType);
                return dto;
            }

            // Build DTO with SIS token
            String displayName = buildDisplayName(student.firstName(), student.lastName());
            return TokenizedStudentDTO.builder()
                    .token(tokenValue)
                    .displayName(displayName)
                    .gradeLevel(student.gradeLevel())
                    .email(tokenValue.toLowerCase() + "@guardian.heronix.local")
                    .build();

        } catch (Exception e) {
            log.error("GUARDIAN_LMS: Failed to tokenize student {}: {}", student.studentId(), e.getMessage());
            return null;
        }
    }

    private String getStudentToken(Long studentId, VendorType vendorType) {
        if (sisTokenBridge != null) {
            return sisTokenBridge.getOrCreateToken(studentId, vendorType.name());
        }
        // Fallback: use local token mapping
        return translationService.tokenizeStudent(studentId, null, null, null, vendorType).getToken();
    }

    private String buildDisplayName(String firstName, String lastName) {
        if (firstName == null || firstName.isBlank()) {
            firstName = "Student";
        }
        String lastInitial = "";
        if (lastName != null && !lastName.isBlank()) {
            lastInitial = " " + lastName.charAt(0) + ".";
        }
        return firstName + lastInitial;
    }

    private void logGatewayTransmission(String type, int recordCount, Long vendorCredentialId) {
        log.info("GUARDIAN_LMS: Gateway routing {} records ({}) for vendor {}",
                recordCount, type, vendorCredentialId);
    }

    private VendorAdapter findAdapter(VendorType type) {
        return vendorAdapters.stream()
                .filter(adapter -> adapter.getVendorType() == type)
                .findFirst()
                .orElse(null);
    }

    // ========================================================================
    // REQUEST TYPES
    // ========================================================================

    public record StudentSyncRequest(
            Long studentId,
            String firstName,
            String lastName,
            String gradeLevel
    ) {}

    public record EnrollmentSyncRequest(
            Long studentId,
            Long courseId,
            String role
    ) {}

    public record FullSyncRequest(
            List<StudentSyncRequest> students,
            List<DataTranslationService.CourseData> courses,
            List<EnrollmentSyncRequest> enrollments
    ) {}
}
