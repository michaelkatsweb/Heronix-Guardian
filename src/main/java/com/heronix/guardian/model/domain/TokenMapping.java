package com.heronix.guardian.model.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token Mapping - Individual token-to-entity mapping for a data access request.
 *
 * This allows IT staff to see exactly which tokens were generated for which
 * entities (students, teachers, courses) in each vendor request.
 *
 * IMPORTANT: This table does NOT store actual PII. It stores:
 * - The token (e.g., STU-7A3F2E)
 * - A display identifier (e.g., "Grade 9 Student #47" or "J. Smith")
 * - The entity type
 *
 * @author Heronix Development Team
 * @version 2.0.0
 */
@Entity
@Table(name = "token_mappings", indexes = {
    @Index(name = "idx_tm_request", columnList = "request_id"),
    @Index(name = "idx_tm_token", columnList = "token_value"),
    @Index(name = "idx_tm_entity_type", columnList = "entity_type")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Parent data access request.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private DataAccessRequest dataAccessRequest;

    /**
     * The generated token value.
     */
    @Column(name = "token_value", nullable = false, length = 20)
    private String tokenValue;

    /**
     * Type of entity this token represents.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 20)
    private EntityType entityType;

    /**
     * Display identifier for the entity (NO PII).
     * Examples:
     * - "Grade 9 Student #47"
     * - "J. Smith (Teacher)"
     * - "Algebra I - Section A"
     */
    @Column(name = "display_identifier", nullable = false, length = 100)
    private String displayIdentifier;

    /**
     * Additional context (grade level, department, etc.)
     */
    @Column(name = "context", length = 200)
    private String context;

    /**
     * Internal entity ID (for audit, not displayed to IT staff).
     * This is encrypted/hashed for security.
     */
    @Column(name = "entity_ref_hash", length = 64)
    private String entityRefHash;

    /**
     * When this token was created.
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Token expiration.
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * Whether this token was actually used in transmission.
     */
    @Column(name = "transmitted")
    @Builder.Default
    private Boolean transmitted = false;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // ========================================================================
    // ENUMS
    // ========================================================================

    public enum EntityType {
        STUDENT,
        TEACHER,
        COURSE,
        SECTION,
        ASSIGNMENT,
        ENROLLMENT
    }
}
