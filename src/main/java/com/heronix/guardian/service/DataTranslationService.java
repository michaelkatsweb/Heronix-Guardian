package com.heronix.guardian.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.heronix.guardian.model.domain.GuardianToken;
import com.heronix.guardian.model.dto.GradeUpdateDTO;
import com.heronix.guardian.model.dto.InboundGradeDTO;
import com.heronix.guardian.model.dto.TokenizedCourseDTO;
import com.heronix.guardian.model.dto.TokenizedStudentDTO;
import com.heronix.guardian.model.enums.TokenType;
import com.heronix.guardian.model.enums.VendorType;

import lombok.extern.slf4j.Slf4j;

/**
 * Service for translating data between real entities and tokenized forms.
 *
 * OUTBOUND (to vendors): Real data -> Tokenized data
 * INBOUND (from vendors): Tokenized data -> Real IDs
 *
 * When SisTokenBridgeService is available, uses SIS for token generation
 * and resolution. Falls back to deprecated local services otherwise.
 */
@Service
@Slf4j
public class DataTranslationService {

    private final TokenMappingService tokenMappingService;
    private final TokenGenerationService tokenGenerationService;

    @Autowired(required = false)
    private SisTokenBridgeService sisTokenBridge;

    public DataTranslationService(
            TokenMappingService tokenMappingService,
            TokenGenerationService tokenGenerationService) {
        this.tokenMappingService = tokenMappingService;
        this.tokenGenerationService = tokenGenerationService;
    }

    // ========================================================================
    // OUTBOUND TRANSLATION (Real Data -> Tokenized)
    // ========================================================================

    /**
     * Tokenize student data for sending to a vendor.
     *
     * INPUT (from SIS):
     * {
     *   "id": 12345,
     *   "firstName": "Emily",
     *   "lastName": "Rodriguez",
     *   "dateOfBirth": "2010-03-15",
     *   "email": "emily.r@school.edu",
     *   "gradeLevel": "9",
     *   "homeAddress": "123 Main St"
     * }
     *
     * OUTPUT (to vendor):
     * {
     *   "token": "STU_H7K2P9M3_X8",
     *   "displayName": "Emily R.",
     *   "gradeLevel": "9"
     * }
     */
    @Transactional
    public TokenizedStudentDTO tokenizeStudent(
            Long studentId,
            String firstName,
            String lastName,
            String gradeLevel,
            VendorType vendor) {

        String vendorScope = vendor != null ? vendor.name() : null;
        String tokenValue;

        // Use SIS bridge when available, fall back to local
        if (sisTokenBridge != null) {
            tokenValue = sisTokenBridge.getOrCreateToken(studentId, vendorScope);
        } else {
            GuardianToken token = tokenGenerationService.getOrCreateToken(
                    TokenType.STUDENT, studentId, vendorScope);
            tokenValue = token.getTokenValue();
        }

        // Generate display name (first name + last initial)
        String displayName = generateDisplayName(firstName, lastName);

        return TokenizedStudentDTO.builder()
                .token(tokenValue)
                .displayName(displayName)
                .gradeLevel(gradeLevel)
                .email(tokenValue.toLowerCase() + "@guardian.heronix.local")
                .build();
    }

    /**
     * Tokenize multiple students in bulk.
     */
    @Transactional
    public List<TokenizedStudentDTO> tokenizeStudentsBulk(
            List<StudentData> students,
            VendorType vendor) {

        String vendorScope = vendor != null ? vendor.name() : null;
        List<Long> studentIds = students.stream().map(StudentData::id).toList();

        // Get tokens for all students
        Map<Long, String> tokenMap = tokenMappingService.getTokensForEntities(
                TokenType.STUDENT, studentIds, vendorScope);

        // Build tokenized list
        List<TokenizedStudentDTO> result = new ArrayList<>();
        for (StudentData student : students) {
            String tokenValue = tokenMap.get(student.id());
            if (tokenValue != null) {
                result.add(TokenizedStudentDTO.builder()
                        .token(tokenValue)
                        .displayName(generateDisplayName(student.firstName(), student.lastName()))
                        .gradeLevel(student.gradeLevel())
                        .email(tokenValue.toLowerCase() + "@guardian.heronix.local")
                        .build());
            }
        }

        return result;
    }

    /**
     * Tokenize course data for sending to a vendor.
     */
    @Transactional
    public TokenizedCourseDTO tokenizeCourse(
            Long courseId,
            String courseName,
            String courseCode,
            String section,
            String subject,
            String gradeLevel,
            Long teacherId,
            String teacherFirstName,
            String teacherLastName,
            String term,
            VendorType vendor) {

        String vendorScope = vendor != null ? vendor.name() : null;

        // Get course token
        GuardianToken courseToken = tokenGenerationService.getOrCreateToken(
                TokenType.COURSE, courseId, vendorScope);

        // Get teacher token
        GuardianToken teacherToken = null;
        String teacherDisplayName = null;
        if (teacherId != null) {
            teacherToken = tokenGenerationService.getOrCreateToken(
                    TokenType.TEACHER, teacherId, vendorScope);
            teacherDisplayName = generateDisplayName(teacherFirstName, teacherLastName);
        }

        return TokenizedCourseDTO.builder()
                .token(courseToken.getTokenValue())
                .courseName(courseName)
                .courseCode(courseCode)
                .section(section)
                .subject(subject)
                .gradeLevel(gradeLevel)
                .teacherToken(teacherToken != null ? teacherToken.getTokenValue() : null)
                .teacherDisplayName(teacherDisplayName)
                .term(term)
                .build();
    }

    // ========================================================================
    // INBOUND TRANSLATION (Tokenized -> Real IDs)
    // ========================================================================

    /**
     * Translate inbound grade from vendor to real entity IDs.
     */
    @Transactional
    public Optional<GradeUpdateDTO> translateInboundGrade(InboundGradeDTO inboundGrade, VendorType vendor) {
        try {
            // Resolve student token (SIS bridge or local)
            Long studentId;
            if (sisTokenBridge != null) {
                studentId = sisTokenBridge.resolveToken(inboundGrade.getStudentToken())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Cannot resolve student token: " + inboundGrade.getStudentToken()));
            } else {
                studentId = tokenMappingService.resolveToEntityId(
                        inboundGrade.getStudentToken(), TokenType.STUDENT);
            }

            // Resolve course token (local only — SIS bridge handles students)
            Long courseId = tokenMappingService.resolveToEntityId(
                    inboundGrade.getCourseToken(), TokenType.COURSE);

            // Resolve assignment token if present
            Long assignmentId = null;
            if (inboundGrade.getAssignmentToken() != null) {
                try {
                    assignmentId = tokenMappingService.resolveToEntityId(
                            inboundGrade.getAssignmentToken(), TokenType.ASSIGNMENT);
                } catch (Exception e) {
                    log.debug("Assignment token not found: {}", inboundGrade.getAssignmentToken());
                }
            }

            return Optional.of(GradeUpdateDTO.builder()
                    .studentId(studentId)
                    .courseId(courseId)
                    .assignmentId(assignmentId)
                    .assignmentName(inboundGrade.getAssignmentName())
                    .score(inboundGrade.getScore())
                    .maxScore(inboundGrade.getMaxScore())
                    .percentage(inboundGrade.getPercentage())
                    .letterGrade(inboundGrade.getLetterGrade())
                    .gradedAt(inboundGrade.getGradedAt())
                    .late(inboundGrade.getLate())
                    .missing(inboundGrade.getMissing())
                    .comments(inboundGrade.getComments())
                    .sourceVendor(vendor.name())
                    .vendorGradeId(inboundGrade.getVendorGradeId())
                    .build());

        } catch (Exception e) {
            log.error("Failed to translate inbound grade: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Translate multiple inbound grades in bulk.
     */
    @Transactional
    public List<GradeUpdateDTO> translateInboundGradesBulk(
            List<InboundGradeDTO> inboundGrades,
            VendorType vendor) {

        List<GradeUpdateDTO> results = new ArrayList<>();

        for (InboundGradeDTO grade : inboundGrades) {
            translateInboundGrade(grade, vendor).ifPresent(results::add);
        }

        log.info("Translated {}/{} grades from {}", results.size(), inboundGrades.size(), vendor);
        return results;
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Generate display name from first and last name.
     * Format: "FirstName L." (first name + last initial)
     */
    private String generateDisplayName(String firstName, String lastName) {
        if (firstName == null || firstName.isBlank()) {
            firstName = "Student";
        }

        String lastInitial = "";
        if (lastName != null && !lastName.isBlank()) {
            lastInitial = " " + lastName.charAt(0) + ".";
        }

        return firstName + lastInitial;
    }

    // ========================================================================
    // RECORD TYPES FOR BULK OPERATIONS
    // ========================================================================

    /**
     * Simple record for student data in bulk operations.
     */
    public record StudentData(
            Long id,
            String firstName,
            String lastName,
            String gradeLevel
    ) {}

    /**
     * Simple record for course data in bulk operations.
     */
    public record CourseData(
            Long id,
            String courseName,
            String courseCode,
            String section,
            String subject,
            String gradeLevel,
            Long teacherId,
            String teacherFirstName,
            String teacherLastName,
            String term
    ) {}
}
