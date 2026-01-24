package com.heronix.guardian.model.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.heronix.guardian.model.enums.VendorType;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Access Request - Tracks all vendor data requests for monitoring and approval.
 *
 * This entity captures every request from third-party vendors (Canvas, Google Classroom)
 * for student, teacher, or course data. IT staff can review, approve/deny, and export
 * these requests for compliance purposes.
 *
 * @author Heronix Development Team
 * @version 2.0.0
 */
@Entity
@Table(name = "data_access_requests", indexes = {
    @Index(name = "idx_dar_vendor", columnList = "vendor_type"),
    @Index(name = "idx_dar_status", columnList = "status"),
    @Index(name = "idx_dar_created", columnList = "created_at"),
    @Index(name = "idx_dar_approved", columnList = "approved_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataAccessRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique request identifier for tracking.
     */
    @Column(name = "request_id", nullable = false, unique = true, length = 36)
    private String requestId;

    /**
     * The vendor requesting data.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "vendor_type", nullable = false, length = 30)
    private VendorType vendorType;

    /**
     * Connection name (e.g., "Main Campus Canvas")
     */
    @Column(name = "connection_name", length = 100)
    private String connectionName;

    /**
     * Type of data requested.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 20)
    private DataType dataType;

    /**
     * Request status.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private RequestStatus status = RequestStatus.PENDING;

    /**
     * Direction of data flow.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private Direction direction;

    /**
     * Number of records requested/transmitted.
     */
    @Column(name = "record_count")
    private Integer recordCount;

    /**
     * Brief description of what's being requested.
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * Specific course name/code if applicable.
     */
    @Column(name = "course_name", length = 200)
    private String courseName;

    /**
     * Specific teacher name if applicable (display name only, no PII).
     */
    @Column(name = "teacher_display_name", length = 100)
    private String teacherDisplayName;

    /**
     * Grade level(s) involved.
     */
    @Column(name = "grade_levels", length = 50)
    private String gradeLevels;

    /**
     * School year.
     */
    @Column(name = "school_year", length = 9)
    private String schoolYear;

    /**
     * Individual token mappings for this request.
     */
    @OneToMany(mappedBy = "dataAccessRequest", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TokenMapping> tokenMappings = new ArrayList<>();

    /**
     * IP address of the request source.
     */
    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    /**
     * Request timestamp.
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * When the request was reviewed.
     */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /**
     * Who reviewed the request.
     */
    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    /**
     * When the request was approved (if approved).
     */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /**
     * Who approved the request.
     */
    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    /**
     * When the data was actually transmitted.
     */
    @Column(name = "transmitted_at")
    private LocalDateTime transmittedAt;

    /**
     * Rejection/denial reason if applicable.
     */
    @Column(name = "denial_reason", length = 500)
    private String denialReason;

    /**
     * Notes from IT staff.
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * Whether this request requires manual approval.
     */
    @Column(name = "requires_approval")
    @Builder.Default
    private Boolean requiresApproval = false;

    /**
     * Auto-approved based on rules.
     */
    @Column(name = "auto_approved")
    @Builder.Default
    private Boolean autoApproved = false;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (requestId == null) {
            requestId = java.util.UUID.randomUUID().toString();
        }
    }

    /**
     * Add a token mapping to this request.
     */
    public void addTokenMapping(TokenMapping mapping) {
        tokenMappings.add(mapping);
        mapping.setDataAccessRequest(this);
    }

    /**
     * Approve this request.
     */
    public void approve(String approvedBy) {
        this.status = RequestStatus.APPROVED;
        this.approvedAt = LocalDateTime.now();
        this.approvedBy = approvedBy;
        this.reviewedAt = LocalDateTime.now();
        this.reviewedBy = approvedBy;
    }

    /**
     * Deny this request.
     */
    public void deny(String deniedBy, String reason) {
        this.status = RequestStatus.DENIED;
        this.denialReason = reason;
        this.reviewedAt = LocalDateTime.now();
        this.reviewedBy = deniedBy;
    }

    /**
     * Mark as transmitted.
     */
    public void markTransmitted() {
        this.status = RequestStatus.TRANSMITTED;
        this.transmittedAt = LocalDateTime.now();
    }

    // ========================================================================
    // ENUMS
    // ========================================================================

    public enum DataType {
        STUDENT_ROSTER,
        TEACHER_ROSTER,
        COURSE_LIST,
        ENROLLMENTS,
        GRADES,
        ASSIGNMENTS,
        ATTENDANCE,
        MIXED
    }

    public enum RequestStatus {
        PENDING,      // Awaiting review
        APPROVED,     // Approved, ready to transmit
        DENIED,       // Denied by IT staff
        TRANSMITTED,  // Data was sent
        FAILED,       // Transmission failed
        EXPIRED       // Request expired without action
    }

    public enum Direction {
        OUTBOUND,     // SIS -> Vendor
        INBOUND       // Vendor -> SIS
    }
}
