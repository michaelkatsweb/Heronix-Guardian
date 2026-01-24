package com.heronix.guardian.adapter.google;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.heronix.guardian.adapter.VendorAdapter;
import com.heronix.guardian.model.domain.VendorCredential;
import com.heronix.guardian.model.dto.InboundGradeDTO;
import com.heronix.guardian.model.dto.TokenizedCourseDTO;
import com.heronix.guardian.model.dto.TokenizedStudentDTO;
import com.heronix.guardian.model.enums.VendorType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Adapter for Google Classroom integration.
 *
 * Google Classroom uses Google OAuth 2.0 and provides REST APIs for:
 * - Courses
 * - Students (CourseStudents)
 * - CourseWork (Assignments)
 * - StudentSubmissions (Grades)
 *
 * @see <a href="https://developers.google.com/classroom/reference/rest">Google Classroom API</a>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleClassroomAdapter implements VendorAdapter {

    private final GoogleApiClient apiClient;

    @Override
    public VendorType getVendorType() {
        return VendorType.GOOGLE_CLASSROOM;
    }

    @Override
    public ConnectionTestResult testConnection(VendorCredential credential) {
        log.info("Testing Google Classroom connection for: {}", credential.getConnectionName());

        try {
            long startTime = System.currentTimeMillis();

            // Call Google API to list courses (verifies credentials)
            var userInfo = apiClient.getUserInfo(credential);

            long responseTime = System.currentTimeMillis() - startTime;

            if (userInfo != null) {
                return ConnectionTestResult.success(
                        "Connected as: " + userInfo.email(),
                        "v1",
                        responseTime
                );
            } else {
                return ConnectionTestResult.failure("Failed to retrieve user info");
            }

        } catch (Exception e) {
            log.error("Google Classroom connection test failed: {}", e.getMessage());
            return ConnectionTestResult.failure("Connection failed: " + e.getMessage());
        }
    }

    @Override
    public VendorCredential refreshOAuthToken(VendorCredential credential) {
        log.info("Refreshing Google OAuth token for: {}", credential.getConnectionName());

        try {
            var newTokens = apiClient.refreshToken(credential);

            credential.setEncryptedOauthToken(newTokens.accessToken());
            if (newTokens.refreshToken() != null) {
                credential.setEncryptedRefreshToken(newTokens.refreshToken());
            }
            credential.setOauthExpiresAt(newTokens.expiresAt());

            log.info("Google OAuth token refreshed successfully");
            return credential;

        } catch (Exception e) {
            log.error("Failed to refresh Google OAuth token: {}", e.getMessage());
            throw new RuntimeException("OAuth token refresh failed", e);
        }
    }

    @Override
    public SyncResult pushStudents(List<TokenizedStudentDTO> students, VendorCredential credential) {
        log.info("Pushing {} students to Google Classroom: {}", students.size(), credential.getConnectionName());

        // Google Classroom doesn't have a separate user creation API
        // Students are added via course enrollments
        // This method is a no-op for Google Classroom

        log.info("Google Classroom: Students are added via enrollments, skipping direct push");
        return SyncResult.success(students.size());
    }

    @Override
    public SyncResult pushCourses(List<TokenizedCourseDTO> courses, VendorCredential credential) {
        log.info("Pushing {} courses to Google Classroom: {}", courses.size(), credential.getConnectionName());

        int processed = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (TokenizedCourseDTO course : courses) {
            try {
                apiClient.createOrUpdateCourse(credential, course);
                processed++;

            } catch (Exception e) {
                failed++;
                errors.add("Failed to sync course " + course.getToken() + ": " + e.getMessage());
                log.warn("Failed to push course {} to Google: {}", course.getToken(), e.getMessage());
            }
        }

        log.info("Google Classroom course sync complete: {} processed, {} failed", processed, failed);
        return new SyncResult(failed == 0, processed, failed, errors);
    }

    @Override
    public SyncResult pushEnrollments(List<EnrollmentPair> enrollments, VendorCredential credential) {
        log.info("Pushing {} enrollments to Google Classroom: {}", enrollments.size(), credential.getConnectionName());

        int processed = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (EnrollmentPair enrollment : enrollments) {
            try {
                apiClient.addStudentToCourse(
                        credential,
                        enrollment.courseToken(),
                        enrollment.studentToken()
                );
                processed++;

            } catch (Exception e) {
                failed++;
                errors.add("Failed to enroll " + enrollment.studentToken() +
                           " in " + enrollment.courseToken() + ": " + e.getMessage());
                log.warn("Failed to create Google enrollment: {}", e.getMessage());
            }
        }

        log.info("Google Classroom enrollment sync complete: {} processed, {} failed", processed, failed);
        return new SyncResult(failed == 0, processed, failed, errors);
    }

    @Override
    public List<InboundGradeDTO> fetchGrades(VendorCredential credential, String courseToken, Long sinceTimestamp) {
        log.info("Fetching grades from Google Classroom: {}", credential.getConnectionName());

        try {
            return apiClient.fetchSubmissions(credential, courseToken);
        } catch (Exception e) {
            log.error("Failed to fetch Google Classroom grades: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public WebhookRegistrationResult registerWebhook(VendorCredential credential, String callbackUrl, List<String> events) {
        log.info("Registering Google Classroom push notification for: {}", credential.getConnectionName());

        try {
            String registrationId = apiClient.registerPushNotification(credential, callbackUrl);
            return WebhookRegistrationResult.success(registrationId);

        } catch (Exception e) {
            log.error("Failed to register Google push notification: {}", e.getMessage());
            return WebhookRegistrationResult.failure(e.getMessage());
        }
    }

    @Override
    public boolean validateWebhookSignature(String payload, String signature, String secret) {
        // Google Classroom uses channel tokens rather than signatures
        // Validation is done by matching the channel token
        return apiClient.validatePushToken(payload, signature, secret);
    }
}
