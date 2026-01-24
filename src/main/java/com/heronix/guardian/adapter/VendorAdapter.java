package com.heronix.guardian.adapter;

import java.util.List;

import com.heronix.guardian.model.domain.VendorCredential;
import com.heronix.guardian.model.dto.InboundGradeDTO;
import com.heronix.guardian.model.dto.TokenizedCourseDTO;
import com.heronix.guardian.model.dto.TokenizedStudentDTO;
import com.heronix.guardian.model.enums.VendorType;

/**
 * Interface for vendor-specific adapters.
 *
 * Each vendor (Canvas, Google Classroom, etc.) implements this interface
 * to provide standardized integration capabilities.
 */
public interface VendorAdapter {

    /**
     * Get the vendor type this adapter handles.
     */
    VendorType getVendorType();

    /**
     * Test the connection to the vendor.
     *
     * @param credential the vendor credentials
     * @return result of the connection test
     */
    ConnectionTestResult testConnection(VendorCredential credential);

    /**
     * Refresh OAuth token if needed.
     *
     * @param credential the vendor credentials
     * @return updated credentials with new token
     */
    VendorCredential refreshOAuthToken(VendorCredential credential);

    // ========================================================================
    // OUTBOUND OPERATIONS (SIS -> Vendor)
    // ========================================================================

    /**
     * Push tokenized students to the vendor.
     *
     * @param students   list of tokenized students
     * @param credential vendor credentials
     * @return sync result
     */
    SyncResult pushStudents(List<TokenizedStudentDTO> students, VendorCredential credential);

    /**
     * Push tokenized courses to the vendor.
     *
     * @param courses    list of tokenized courses
     * @param credential vendor credentials
     * @return sync result
     */
    SyncResult pushCourses(List<TokenizedCourseDTO> courses, VendorCredential credential);

    /**
     * Push enrollments (student-course associations) to the vendor.
     *
     * @param enrollments list of token pairs (student token -> course token)
     * @param credential  vendor credentials
     * @return sync result
     */
    SyncResult pushEnrollments(List<EnrollmentPair> enrollments, VendorCredential credential);

    // ========================================================================
    // INBOUND OPERATIONS (Vendor -> SIS)
    // ========================================================================

    /**
     * Fetch grades from the vendor.
     *
     * @param credential vendor credentials
     * @param courseToken optional course filter
     * @param sinceTimestamp optional timestamp to fetch only recent grades
     * @return list of grades with tokens
     */
    List<InboundGradeDTO> fetchGrades(VendorCredential credential, String courseToken, Long sinceTimestamp);

    // ========================================================================
    // WEBHOOK HANDLING
    // ========================================================================

    /**
     * Register a webhook with the vendor.
     *
     * @param credential  vendor credentials
     * @param callbackUrl URL for webhook callbacks
     * @param events      list of events to subscribe to
     * @return webhook registration result
     */
    WebhookRegistrationResult registerWebhook(VendorCredential credential, String callbackUrl, List<String> events);

    /**
     * Validate a webhook signature.
     *
     * @param payload   webhook payload
     * @param signature signature from vendor
     * @param secret    webhook secret
     * @return true if signature is valid
     */
    boolean validateWebhookSignature(String payload, String signature, String secret);

    // ========================================================================
    // RESULT TYPES
    // ========================================================================

    /**
     * Result of a connection test.
     */
    record ConnectionTestResult(
            boolean success,
            String message,
            String vendorVersion,
            long responseTimeMs
    ) {
        public static ConnectionTestResult success(String message, String version, long responseTime) {
            return new ConnectionTestResult(true, message, version, responseTime);
        }

        public static ConnectionTestResult failure(String message) {
            return new ConnectionTestResult(false, message, null, 0);
        }
    }

    /**
     * Result of a sync operation.
     */
    record SyncResult(
            boolean success,
            int recordsProcessed,
            int recordsFailed,
            List<String> errors
    ) {
        public static SyncResult success(int processed) {
            return new SyncResult(true, processed, 0, List.of());
        }

        public static SyncResult partial(int processed, int failed, List<String> errors) {
            return new SyncResult(processed > 0, processed, failed, errors);
        }

        public static SyncResult failure(String error) {
            return new SyncResult(false, 0, 0, List.of(error));
        }

        // Accessor aliases for compatibility
        public int processed() { return recordsProcessed; }
        public int failed() { return recordsFailed; }
    }

    /**
     * Student-course enrollment pair.
     */
    record EnrollmentPair(
            String studentToken,
            String courseToken,
            String role  // "student", "teacher", "ta"
    ) {}

    /**
     * Result of webhook registration.
     */
    record WebhookRegistrationResult(
            boolean success,
            String webhookId,
            String message
    ) {
        public static WebhookRegistrationResult success(String webhookId) {
            return new WebhookRegistrationResult(true, webhookId, "Webhook registered successfully");
        }

        public static WebhookRegistrationResult failure(String message) {
            return new WebhookRegistrationResult(false, null, message);
        }
    }
}
