package com.heronix.guardian.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Supported third-party vendor types for Guardian integration.
 */
@Getter
@RequiredArgsConstructor
public enum VendorType {

    /**
     * Canvas LMS by Instructure
     */
    CANVAS("Canvas LMS", "https://canvas.instructure.com"),

    /**
     * Google Classroom
     */
    GOOGLE_CLASSROOM("Google Classroom", "https://classroom.googleapis.com"),

    /**
     * Schoology LMS
     */
    SCHOOLOGY("Schoology", "https://api.schoology.com"),

    /**
     * Blackboard Learn
     */
    BLACKBOARD("Blackboard", "https://blackboard.com"),

    /**
     * Moodle LMS
     */
    MOODLE("Moodle", "https://moodle.org");

    /**
     * Display name for the vendor
     */
    private final String displayName;

    /**
     * Default API base URL
     */
    private final String defaultApiUrl;
}
