package com.heronix.guardian.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tokenized course data sent to third-party vendors.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenizedCourseDTO {

    /**
     * Anonymous token for the course (e.g., "CRS_X2K9P7M4_A3")
     */
    private String token;

    /**
     * Course name (safe to share)
     */
    private String courseName;

    /**
     * Course code (e.g., "MATH101")
     */
    private String courseCode;

    /**
     * Section identifier
     */
    private String section;

    /**
     * Subject area (e.g., "Mathematics", "English")
     */
    private String subject;

    /**
     * Grade level(s) this course is for
     */
    private String gradeLevel;

    /**
     * Tokenized teacher identifier
     */
    private String teacherToken;

    /**
     * Teacher display name (first name + last initial)
     */
    private String teacherDisplayName;

    /**
     * Term/semester (e.g., "Fall 2025", "Spring 2026")
     */
    private String term;
}
