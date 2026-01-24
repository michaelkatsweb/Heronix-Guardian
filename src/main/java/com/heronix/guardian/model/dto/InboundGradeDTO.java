package com.heronix.guardian.model.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Grade data received from third-party vendors.
 * Contains tokens that need to be resolved to real entity IDs.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundGradeDTO {

    /**
     * Student token from vendor (e.g., "STU_H7K2P9M3_X8")
     */
    private String studentToken;

    /**
     * Course token from vendor (e.g., "CRS_X2K9P7M4_A3")
     */
    private String courseToken;

    /**
     * Assignment token (if using tokenized assignments)
     */
    private String assignmentToken;

    /**
     * Assignment name from vendor
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
     * Percentage score (calculated)
     */
    private Double percentage;

    /**
     * Letter grade (if provided by vendor)
     */
    private String letterGrade;

    /**
     * When the grade was recorded
     */
    private LocalDateTime gradedAt;

    /**
     * When the assignment was submitted
     */
    private LocalDateTime submittedAt;

    /**
     * Whether this is late submission
     */
    private Boolean late;

    /**
     * Whether this is a missing assignment
     */
    private Boolean missing;

    /**
     * Optional comments from grader
     */
    private String comments;

    /**
     * Vendor-specific grade ID (for reference)
     */
    private String vendorGradeId;

    /**
     * Calculate percentage if not provided.
     */
    public Double getPercentage() {
        if (percentage != null) {
            return percentage;
        }
        if (score != null && maxScore != null && maxScore > 0) {
            return (score / maxScore) * 100.0;
        }
        return null;
    }
}
