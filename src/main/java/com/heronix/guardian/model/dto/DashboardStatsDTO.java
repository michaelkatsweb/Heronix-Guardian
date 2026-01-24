package com.heronix.guardian.model.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dashboard statistics DTO for the Guardian Monitor.
 *
 * Contains aggregated metrics displayed on the monitoring dashboard.
 *
 * @author Heronix Development Team
 * @version 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDTO {

    // Request counts by status
    private long totalRequests;
    private long pendingApprovalCount;
    private long approvedCount;
    private long deniedCount;
    private long transmittedCount;

    // Request counts by vendor
    private long canvasRequestCount;
    private long googleClassroomRequestCount;

    // Token statistics
    private long totalTokensGenerated;
    private long activeStudentTokens;
    private long activeTeacherTokens;
    private long activeCourseTokens;

    // Time-based metrics
    private long requestsToday;
    private long requestsThisWeek;
    private long requestsThisMonth;

    // Data volume
    private long totalStudentsShared;
    private long totalCoursesShared;

    // Timestamp
    private LocalDateTime generatedAt;

    /**
     * Create a builder with default values and timestamp.
     */
    public static DashboardStatsDTOBuilder withDefaults() {
        return DashboardStatsDTO.builder()
                .totalRequests(0)
                .pendingApprovalCount(0)
                .approvedCount(0)
                .deniedCount(0)
                .transmittedCount(0)
                .canvasRequestCount(0)
                .googleClassroomRequestCount(0)
                .totalTokensGenerated(0)
                .activeStudentTokens(0)
                .activeTeacherTokens(0)
                .activeCourseTokens(0)
                .requestsToday(0)
                .requestsThisWeek(0)
                .requestsThisMonth(0)
                .totalStudentsShared(0)
                .totalCoursesShared(0)
                .generatedAt(LocalDateTime.now());
    }
}
