package com.heronix.guardian.adapter.canvas;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.heronix.guardian.adapter.VendorAdapter;
import com.heronix.guardian.model.domain.VendorCredential;
import com.heronix.guardian.model.dto.InboundGradeDTO;
import com.heronix.guardian.model.dto.TokenizedCourseDTO;
import com.heronix.guardian.model.dto.TokenizedStudentDTO;
import com.heronix.guardian.model.enums.VendorType;
import com.heronix.guardian.service.CredentialDecryptionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Adapter for Canvas LMS integration.
 *
 * Canvas LMS uses OAuth 2.0 for authentication and provides REST APIs for:
 * - Users (students, teachers)
 * - Courses
 * - Enrollments
 * - Assignments
 * - Submissions/Grades
 *
 * @see <a href="https://canvas.instructure.com/doc/api/">Canvas API Documentation</a>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CanvasAdapter implements VendorAdapter {

    private final CanvasApiClient apiClient;
    private final CredentialDecryptionService decryptionService;

    @Override
    public VendorType getVendorType() {
        return VendorType.CANVAS;
    }

    @Override
    public ConnectionTestResult testConnection(VendorCredential credential) {
        log.info("Testing Canvas connection for: {}", credential.getConnectionName());

        try {
            long startTime = System.currentTimeMillis();

            // Call Canvas API to get account info
            var accountInfo = apiClient.getAccountInfo(credential);

            long responseTime = System.currentTimeMillis() - startTime;

            if (accountInfo != null) {
                return ConnectionTestResult.success(
                        "Connected to Canvas instance: " + accountInfo.name(),
                        accountInfo.version(),
                        responseTime
                );
            } else {
                return ConnectionTestResult.failure("Failed to retrieve account info");
            }

        } catch (Exception e) {
            log.error("Canvas connection test failed: {}", e.getMessage());
            return ConnectionTestResult.failure("Connection failed: " + e.getMessage());
        }
    }

    @Override
    public VendorCredential refreshOAuthToken(VendorCredential credential) {
        log.info("Refreshing Canvas OAuth token for: {}", credential.getConnectionName());

        try {
            var newTokens = apiClient.refreshToken(credential);

            credential.setEncryptedOauthToken(decryptionService.encrypt(newTokens.accessToken()));
            if (newTokens.refreshToken() != null) {
                credential.setEncryptedRefreshToken(decryptionService.encrypt(newTokens.refreshToken()));
            }
            credential.setOauthExpiresAt(newTokens.expiresAt());

            log.info("Canvas OAuth token refreshed successfully");
            return credential;

        } catch (Exception e) {
            log.error("Failed to refresh Canvas OAuth token: {}", e.getMessage());
            throw new RuntimeException("OAuth token refresh failed", e);
        }
    }

    @Override
    public SyncResult pushStudents(List<TokenizedStudentDTO> students, VendorCredential credential) {
        log.info("Pushing {} students to Canvas: {}", students.size(), credential.getConnectionName());

        int processed = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (TokenizedStudentDTO student : students) {
            try {
                // Create or update user in Canvas
                // Uses token as SIS User ID for de-duplication
                apiClient.createOrUpdateUser(credential, student);
                processed++;

            } catch (Exception e) {
                failed++;
                errors.add("Failed to sync student " + student.getToken() + ": " + e.getMessage());
                log.warn("Failed to push student {} to Canvas: {}", student.getToken(), e.getMessage());
            }
        }

        log.info("Canvas student sync complete: {} processed, {} failed", processed, failed);
        return new SyncResult(failed == 0, processed, failed, errors);
    }

    @Override
    public SyncResult pushCourses(List<TokenizedCourseDTO> courses, VendorCredential credential) {
        log.info("Pushing {} courses to Canvas: {}", courses.size(), credential.getConnectionName());

        int processed = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (TokenizedCourseDTO course : courses) {
            try {
                // Create or update course in Canvas
                // Uses token as SIS Course ID
                apiClient.createOrUpdateCourse(credential, course);
                processed++;

            } catch (Exception e) {
                failed++;
                errors.add("Failed to sync course " + course.getToken() + ": " + e.getMessage());
                log.warn("Failed to push course {} to Canvas: {}", course.getToken(), e.getMessage());
            }
        }

        log.info("Canvas course sync complete: {} processed, {} failed", processed, failed);
        return new SyncResult(failed == 0, processed, failed, errors);
    }

    @Override
    public SyncResult pushEnrollments(List<EnrollmentPair> enrollments, VendorCredential credential) {
        log.info("Pushing {} enrollments to Canvas: {}", enrollments.size(), credential.getConnectionName());

        int processed = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (EnrollmentPair enrollment : enrollments) {
            try {
                // Create enrollment in Canvas
                apiClient.createEnrollment(
                        credential,
                        enrollment.courseToken(),
                        enrollment.studentToken(),
                        enrollment.role()
                );
                processed++;

            } catch (Exception e) {
                failed++;
                errors.add("Failed to enroll " + enrollment.studentToken() +
                           " in " + enrollment.courseToken() + ": " + e.getMessage());
                log.warn("Failed to create Canvas enrollment: {}", e.getMessage());
            }
        }

        log.info("Canvas enrollment sync complete: {} processed, {} failed", processed, failed);
        return new SyncResult(failed == 0, processed, failed, errors);
    }

    @Override
    public List<InboundGradeDTO> fetchGrades(VendorCredential credential, String courseToken, Long sinceTimestamp) {
        log.info("Fetching grades from Canvas: {}", credential.getConnectionName());

        try {
            return apiClient.fetchSubmissions(credential, courseToken, sinceTimestamp);
        } catch (Exception e) {
            log.error("Failed to fetch Canvas grades: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public WebhookRegistrationResult registerWebhook(VendorCredential credential, String callbackUrl, List<String> events) {
        log.info("Registering Canvas webhook for: {}", credential.getConnectionName());

        try {
            String webhookId = apiClient.registerWebhook(credential, callbackUrl, events);
            return WebhookRegistrationResult.success(webhookId);

        } catch (Exception e) {
            log.error("Failed to register Canvas webhook: {}", e.getMessage());
            return WebhookRegistrationResult.failure(e.getMessage());
        }
    }

    @Override
    public boolean validateWebhookSignature(String payload, String signature, String secret) {
        // Canvas uses HMAC-SHA256 for webhook signatures
        return apiClient.validateSignature(payload, signature, secret);
    }
}
