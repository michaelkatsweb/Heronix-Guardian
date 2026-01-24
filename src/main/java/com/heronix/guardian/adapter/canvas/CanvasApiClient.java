package com.heronix.guardian.adapter.canvas;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.heronix.guardian.config.GuardianProperties;
import com.heronix.guardian.model.domain.VendorCredential;
import com.heronix.guardian.model.dto.InboundGradeDTO;
import com.heronix.guardian.model.dto.TokenizedCourseDTO;
import com.heronix.guardian.model.dto.TokenizedStudentDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Low-level API client for Canvas LMS REST API.
 *
 * Canvas API documentation: https://canvas.instructure.com/doc/api/
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CanvasApiClient {

    private final WebClient.Builder webClientBuilder;
    private final GuardianProperties properties;

    /**
     * Get account information for connection testing.
     */
    public AccountInfo getAccountInfo(VendorCredential credential) {
        WebClient client = createClient(credential);

        try {
            Map<String, Object> response = client.get()
                    .uri("/api/v1/accounts/self")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null) {
                return new AccountInfo(
                        (String) response.get("name"),
                        (String) response.getOrDefault("uuid", "unknown")
                );
            }
            return null;

        } catch (Exception e) {
            log.error("Failed to get Canvas account info: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Create or update a user in Canvas.
     */
    public void createOrUpdateUser(VendorCredential credential, TokenizedStudentDTO student) {
        WebClient client = createClient(credential);

        // Canvas user payload
        Map<String, Object> userData = Map.of(
                "user", Map.of(
                        "name", student.getDisplayName(),
                        "short_name", student.getDisplayName(),
                        "sortable_name", student.getDisplayName()
                ),
                "pseudonym", Map.of(
                        "unique_id", student.getEmail(),
                        "sis_user_id", student.getToken(), // Use token as SIS ID
                        "send_confirmation", false
                )
        );

        try {
            // Try to find existing user by SIS ID
            var existingUser = client.get()
                    .uri("/api/v1/users/sis_user_id:" + student.getToken())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .onErrorReturn(Map.of())
                    .block();

            if (existingUser != null && existingUser.containsKey("id")) {
                // Update existing user
                log.debug("Updating existing Canvas user: {}", student.getToken());
                client.put()
                        .uri("/api/v1/users/sis_user_id:" + student.getToken())
                        .bodyValue(userData)
                        .retrieve()
                        .bodyToMono(Void.class)
                        .block();
            } else {
                // Create new user
                log.debug("Creating new Canvas user: {}", student.getToken());
                client.post()
                        .uri("/api/v1/accounts/self/users")
                        .bodyValue(userData)
                        .retrieve()
                        .bodyToMono(Void.class)
                        .block();
            }

        } catch (Exception e) {
            log.error("Failed to sync user {} to Canvas: {}", student.getToken(), e.getMessage());
            throw e;
        }
    }

    /**
     * Create or update a course in Canvas.
     */
    public void createOrUpdateCourse(VendorCredential credential, TokenizedCourseDTO course) {
        WebClient client = createClient(credential);

        Map<String, Object> courseData = Map.of(
                "course", Map.of(
                        "name", course.getCourseName(),
                        "course_code", course.getCourseCode(),
                        "sis_course_id", course.getToken(), // Use token as SIS ID
                        "term_id", course.getTerm() != null ? course.getTerm() : ""
                )
        );

        try {
            // Try to find existing course by SIS ID
            var existingCourse = client.get()
                    .uri("/api/v1/courses/sis_course_id:" + course.getToken())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .onErrorReturn(Map.of())
                    .block();

            if (existingCourse != null && existingCourse.containsKey("id")) {
                // Update existing course
                log.debug("Updating existing Canvas course: {}", course.getToken());
                client.put()
                        .uri("/api/v1/courses/sis_course_id:" + course.getToken())
                        .bodyValue(courseData)
                        .retrieve()
                        .bodyToMono(Void.class)
                        .block();
            } else {
                // Create new course
                log.debug("Creating new Canvas course: {}", course.getToken());
                client.post()
                        .uri("/api/v1/accounts/self/courses")
                        .bodyValue(courseData)
                        .retrieve()
                        .bodyToMono(Void.class)
                        .block();
            }

        } catch (Exception e) {
            log.error("Failed to sync course {} to Canvas: {}", course.getToken(), e.getMessage());
            throw e;
        }
    }

    /**
     * Create an enrollment in Canvas.
     */
    public void createEnrollment(VendorCredential credential, String courseToken, String studentToken, String role) {
        WebClient client = createClient(credential);

        String enrollmentType = switch (role.toLowerCase()) {
            case "teacher" -> "TeacherEnrollment";
            case "ta" -> "TaEnrollment";
            default -> "StudentEnrollment";
        };

        Map<String, Object> enrollmentData = Map.of(
                "enrollment", Map.of(
                        "user_id", "sis_user_id:" + studentToken,
                        "type", enrollmentType,
                        "enrollment_state", "active"
                )
        );

        try {
            client.post()
                    .uri("/api/v1/courses/sis_course_id:" + courseToken + "/enrollments")
                    .bodyValue(enrollmentData)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            log.debug("Created Canvas enrollment: {} in {}", studentToken, courseToken);

        } catch (Exception e) {
            log.error("Failed to create enrollment in Canvas: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Fetch submissions (grades) from Canvas.
     */
    public List<InboundGradeDTO> fetchSubmissions(VendorCredential credential, String courseToken, Long sinceTimestamp) {
        WebClient client = createClient(credential);
        List<InboundGradeDTO> grades = new ArrayList<>();

        try {
            String uri = courseToken != null
                    ? "/api/v1/courses/sis_course_id:" + courseToken + "/students/submissions"
                    : "/api/v1/users/self/courses";

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> submissions = (List<Map<String, Object>>) (List<?>) client.get()
                    .uri(uri + "?student_ids[]=all&include[]=assignment")
                    .retrieve()
                    .bodyToFlux(Map.class)
                    .collectList()
                    .block();

            if (submissions != null) {
                for (Map<String, Object> sub : submissions) {
                    try {
                        InboundGradeDTO grade = InboundGradeDTO.builder()
                                .studentToken(extractSisId(sub, "user"))
                                .courseToken(courseToken)
                                .assignmentName((String) getNestedValue(sub, "assignment", "name"))
                                .score(toDouble(sub.get("score")))
                                .maxScore(toDouble(getNestedValue(sub, "assignment", "points_possible")))
                                .gradedAt(toLocalDateTime(sub.get("graded_at")))
                                .submittedAt(toLocalDateTime(sub.get("submitted_at")))
                                .late((Boolean) sub.getOrDefault("late", false))
                                .missing((Boolean) sub.getOrDefault("missing", false))
                                .vendorGradeId(String.valueOf(sub.get("id")))
                                .build();

                        grades.add(grade);
                    } catch (Exception e) {
                        log.warn("Failed to parse Canvas submission: {}", e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to fetch Canvas submissions: {}", e.getMessage());
        }

        return grades;
    }

    /**
     * Refresh OAuth token.
     */
    public TokenRefreshResult refreshToken(VendorCredential credential) {
        WebClient client = WebClient.builder()
                .baseUrl(credential.getApiBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .build();

        try {
            Map<String, Object> response = client.post()
                    .uri("/login/oauth2/token")
                    .bodyValue(Map.of(
                            "grant_type", "refresh_token",
                            "client_id", credential.getClientId(),
                            "client_secret", credential.getEncryptedClientSecret(), // Should be decrypted
                            "refresh_token", credential.getEncryptedRefreshToken()   // Should be decrypted
                    ))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null) {
                String accessToken = (String) response.get("access_token");
                String refreshToken = (String) response.get("refresh_token");
                Integer expiresIn = (Integer) response.get("expires_in");

                LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(expiresIn != null ? expiresIn : 3600);

                return new TokenRefreshResult(accessToken, refreshToken, expiresAt);
            }

            throw new RuntimeException("Empty response from token refresh");

        } catch (Exception e) {
            log.error("Failed to refresh Canvas OAuth token: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Register a webhook subscription.
     */
    public String registerWebhook(VendorCredential credential, String callbackUrl, List<String> events) {
        // Canvas uses "Canvas Data Portal" for webhooks which requires separate setup
        // For now, return a placeholder - real implementation depends on Canvas setup
        log.info("Webhook registration requested for Canvas: {} -> {}", events, callbackUrl);
        return "webhook_" + System.currentTimeMillis();
    }

    /**
     * Validate webhook signature using HMAC-SHA256.
     */
    public boolean validateSignature(String payload, String signature, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);

            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computed = bytesToHex(hash);

            return computed.equalsIgnoreCase(signature);

        } catch (Exception e) {
            log.error("Failed to validate webhook signature: {}", e.getMessage());
            return false;
        }
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private WebClient createClient(VendorCredential credential) {
        String baseUrl = credential.getApiBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://canvas.instructure.com";
        }

        return webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + credential.getEncryptedOauthToken())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private String extractSisId(Map<String, Object> obj, String field) {
        Object user = obj.get(field);
        if (user instanceof Map) {
            return (String) ((Map<?, ?>) user).get("sis_user_id");
        }
        return null;
    }

    private Object getNestedValue(Map<String, Object> obj, String... keys) {
        Object current = obj;
        for (String key : keys) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(key);
            } else {
                return null;
            }
        }
        return current;
    }

    private Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof String) {
            try {
                Instant instant = Instant.parse((String) value);
                return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // ========================================================================
    // RESULT TYPES
    // ========================================================================

    public record AccountInfo(String name, String version) {}

    public record TokenRefreshResult(String accessToken, String refreshToken, LocalDateTime expiresAt) {}
}
