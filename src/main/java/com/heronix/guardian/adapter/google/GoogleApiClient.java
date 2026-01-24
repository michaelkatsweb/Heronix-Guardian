package com.heronix.guardian.adapter.google;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.heronix.guardian.config.GuardianProperties;
import com.heronix.guardian.model.domain.VendorCredential;
import com.heronix.guardian.model.dto.InboundGradeDTO;
import com.heronix.guardian.model.dto.TokenizedCourseDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Low-level API client for Google Classroom REST API.
 *
 * Google Classroom API documentation: https://developers.google.com/classroom/reference/rest
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleApiClient {

    private final WebClient.Builder webClientBuilder;
    private final GuardianProperties properties;

    private static final String CLASSROOM_API_BASE = "https://classroom.googleapis.com";
    private static final String OAUTH_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v1/userinfo";

    /**
     * Get user info for connection testing.
     */
    public UserInfo getUserInfo(VendorCredential credential) {
        WebClient client = createClient(credential);

        try {
            Map<String, Object> response = WebClient.builder()
                    .baseUrl(USERINFO_URL)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + credential.getEncryptedOauthToken())
                    .build()
                    .get()
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null) {
                return new UserInfo(
                        (String) response.get("email"),
                        (String) response.get("name")
                );
            }
            return null;

        } catch (Exception e) {
            log.error("Failed to get Google user info: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Create or update a course in Google Classroom.
     */
    public void createOrUpdateCourse(VendorCredential credential, TokenizedCourseDTO course) {
        WebClient client = createClient(credential);

        Map<String, Object> courseData = Map.of(
                "name", course.getCourseName(),
                "section", course.getSection() != null ? course.getSection() : "",
                "descriptionHeading", course.getCourseCode(),
                "ownerId", "me" // The authenticated user becomes the teacher
        );

        try {
            // Google Classroom doesn't support SIS ID lookup
            // We store a mapping separately or use course aliases

            // Try to find existing course by alias
            String alias = "p:" + course.getToken(); // Project-scoped alias

            var existingCourse = client.get()
                    .uri("/v1/courses/" + alias)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .onErrorReturn(Map.of())
                    .block();

            if (existingCourse != null && existingCourse.containsKey("id")) {
                // Update existing course
                log.debug("Updating existing Google Classroom course: {}", course.getToken());
                client.patch()
                        .uri("/v1/courses/" + existingCourse.get("id"))
                        .bodyValue(courseData)
                        .retrieve()
                        .bodyToMono(Void.class)
                        .block();
            } else {
                // Create new course
                log.debug("Creating new Google Classroom course: {}", course.getToken());
                Map<String, Object> response = client.post()
                        .uri("/v1/courses")
                        .bodyValue(courseData)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();

                // Create alias for future lookups
                if (response != null && response.containsKey("id")) {
                    createCourseAlias(client, (String) response.get("id"), course.getToken());
                }
            }

        } catch (Exception e) {
            log.error("Failed to sync course {} to Google: {}", course.getToken(), e.getMessage());
            throw e;
        }
    }

    /**
     * Create a course alias for SIS-style lookups.
     */
    private void createCourseAlias(WebClient client, String courseId, String alias) {
        try {
            client.post()
                    .uri("/v1/courses/" + courseId + "/aliases")
                    .bodyValue(Map.of("alias", "p:" + alias))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
        } catch (Exception e) {
            log.warn("Failed to create course alias: {}", e.getMessage());
        }
    }

    /**
     * Add a student to a course.
     */
    public void addStudentToCourse(VendorCredential credential, String courseToken, String studentToken) {
        WebClient client = createClient(credential);

        // Google requires an email address to add students
        // We use the tokenized email format
        String studentEmail = studentToken.toLowerCase() + "@guardian.heronix.local";

        Map<String, Object> studentData = Map.of(
                "userId", studentEmail
        );

        try {
            client.post()
                    .uri("/v1/courses/p:" + courseToken + "/students")
                    .bodyValue(studentData)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            log.debug("Added student {} to Google course {}", studentToken, courseToken);

        } catch (Exception e) {
            log.error("Failed to add student to Google course: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Fetch submissions (grades) from Google Classroom.
     */
    public List<InboundGradeDTO> fetchSubmissions(VendorCredential credential, String courseToken) {
        WebClient client = createClient(credential);
        List<InboundGradeDTO> grades = new ArrayList<>();

        try {
            String courseId = courseToken != null ? "p:" + courseToken : null;

            // First, get all coursework for the course
            List<Map<String, Object>> coursework = client.get()
                    .uri("/v1/courses/" + courseId + "/courseWork")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .map(response -> (List<Map<String, Object>>) response.get("courseWork"))
                    .onErrorReturn(List.of())
                    .block();

            if (coursework == null) coursework = List.of();

            // For each coursework, get student submissions
            for (Map<String, Object> work : coursework) {
                String workId = (String) work.get("id");
                String assignmentName = (String) work.get("title");
                Double maxPoints = toDouble(work.get("maxPoints"));

                List<Map<String, Object>> submissions = client.get()
                        .uri("/v1/courses/" + courseId + "/courseWork/" + workId + "/studentSubmissions")
                        .retrieve()
                        .bodyToMono(Map.class)
                        .map(response -> (List<Map<String, Object>>) response.get("studentSubmissions"))
                        .onErrorReturn(List.of())
                        .block();

                if (submissions == null) continue;

                for (Map<String, Object> sub : submissions) {
                    try {
                        String state = (String) sub.get("state");
                        if (!"RETURNED".equals(state) && !"GRADED".equals(state)) {
                            continue; // Skip ungraded submissions
                        }

                        String userId = (String) sub.get("userId");
                        Double assignedGrade = toDouble(sub.get("assignedGrade"));

                        InboundGradeDTO grade = InboundGradeDTO.builder()
                                .studentToken(userId) // Need to map back to our token
                                .courseToken(courseToken)
                                .assignmentName(assignmentName)
                                .score(assignedGrade)
                                .maxScore(maxPoints)
                                .gradedAt(toLocalDateTime(sub.get("updateTime")))
                                .submittedAt(toLocalDateTime(sub.get("creationTime")))
                                .late("LATE".equals(sub.get("late")))
                                .vendorGradeId((String) sub.get("id"))
                                .build();

                        grades.add(grade);
                    } catch (Exception e) {
                        log.warn("Failed to parse Google submission: {}", e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to fetch Google Classroom submissions: {}", e.getMessage());
        }

        return grades;
    }

    /**
     * Refresh OAuth token.
     */
    public TokenRefreshResult refreshToken(VendorCredential credential) {
        try {
            Map<String, Object> response = WebClient.builder()
                    .baseUrl(OAUTH_TOKEN_URL)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .build()
                    .post()
                    .bodyValue(Map.of(
                            "grant_type", "refresh_token",
                            "client_id", credential.getClientId(),
                            "client_secret", credential.getEncryptedClientSecret(),
                            "refresh_token", credential.getEncryptedRefreshToken()
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
            log.error("Failed to refresh Google OAuth token: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Register push notification for course updates.
     */
    public String registerPushNotification(VendorCredential credential, String callbackUrl) {
        WebClient client = createClient(credential);

        try {
            Map<String, Object> registration = Map.of(
                    "feed", Map.of(
                            "feedType", "COURSE_ROSTER_CHANGES"
                    ),
                    "cloudPubsubTopic", Map.of(
                            "topicName", callbackUrl
                    )
            );

            Map<String, Object> response = client.post()
                    .uri("/v1/registrations")
                    .bodyValue(registration)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null) {
                return (String) response.get("registrationId");
            }

            return "registration_" + System.currentTimeMillis();

        } catch (Exception e) {
            log.error("Failed to register Google push notification: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Validate push notification token.
     */
    public boolean validatePushToken(String payload, String token, String expectedToken) {
        return expectedToken != null && expectedToken.equals(token);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private WebClient createClient(VendorCredential credential) {
        return webClientBuilder
                .baseUrl(CLASSROOM_API_BASE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + credential.getEncryptedOauthToken())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
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

    // ========================================================================
    // RESULT TYPES
    // ========================================================================

    public record UserInfo(String email, String name) {}

    public record TokenRefreshResult(String accessToken, String refreshToken, LocalDateTime expiresAt) {}
}
