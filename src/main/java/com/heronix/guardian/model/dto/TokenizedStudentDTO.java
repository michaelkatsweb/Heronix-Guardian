package com.heronix.guardian.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tokenized student data sent to third-party vendors.
 *
 * This DTO contains ONLY anonymized information:
 * - Anonymous token instead of real ID
 * - Display name (first name + last initial) instead of full name
 * - Grade level
 *
 * NEVER includes:
 * - Full legal name
 * - Date of birth
 * - Address
 * - Parent/guardian info
 * - Medical information
 * - SSN or other PII
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenizedStudentDTO {

    /**
     * Anonymous token (e.g., "STU_H7K2P9M3_X8")
     * Used as the unique identifier in vendor systems.
     */
    private String token;

    /**
     * Display name for the student (e.g., "Emily R.")
     * Only contains first name and last initial.
     */
    private String displayName;

    /**
     * Grade level (e.g., "9", "10", "11", "12")
     */
    private String gradeLevel;

    /**
     * Optional section identifier (for class assignment)
     */
    private String sectionId;

    /**
     * Email - only if vendor requires for login (uses tokenized email domain)
     * Format: token@guardian.heronix.local
     */
    private String email;

    /**
     * Create a minimal tokenized student.
     */
    public static TokenizedStudentDTO minimal(String token, String displayName) {
        return TokenizedStudentDTO.builder()
                .token(token)
                .displayName(displayName)
                .build();
    }

    /**
     * Create a tokenized student with grade level.
     */
    public static TokenizedStudentDTO withGradeLevel(String token, String displayName, String gradeLevel) {
        return TokenizedStudentDTO.builder()
                .token(token)
                .displayName(displayName)
                .gradeLevel(gradeLevel)
                .build();
    }
}
