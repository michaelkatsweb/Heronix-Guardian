package com.heronix.guardian.model.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Grade update to be sent to Heronix SIS after token resolution.
 * Contains real entity IDs (not tokens).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GradeUpdateDTO {

    /**
     * Real student ID in Heronix SIS
     */
    private Long studentId;

    /**
     * Real course ID in Heronix SIS
     */
    private Long courseId;

    /**
     * Assignment ID in Heronix SIS (if mapped)
     */
    private Long assignmentId;

    /**
     * Assignment name
     */
    private String assignmentName;

    /**
     * Score achieved
     */
    private Double score;

    /**
     * Maximum possible score
     */
    private Double maxScore;

    /**
     * Percentage score
     */
    private Double percentage;

    /**
     * Letter grade
     */
    private String letterGrade;

    /**
     * When the grade was recorded
     */
    private LocalDateTime gradedAt;

    /**
     * Whether this is late
     */
    private Boolean late;

    /**
     * Whether this is missing
     */
    private Boolean missing;

    /**
     * Comments
     */
    private String comments;

    /**
     * Source vendor
     */
    private String sourceVendor;

    /**
     * Vendor's grade ID for reference
     */
    private String vendorGradeId;
}
