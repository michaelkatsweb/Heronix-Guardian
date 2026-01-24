package com.heronix.guardian.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.heronix.guardian.model.domain.DataAccessRequest;
import com.heronix.guardian.model.domain.DataAccessRequest.DataType;
import com.heronix.guardian.model.domain.DataAccessRequest.Direction;
import com.heronix.guardian.model.domain.DataAccessRequest.RequestStatus;
import com.heronix.guardian.model.enums.VendorType;

/**
 * Repository for DataAccessRequest entities.
 */
@Repository
public interface DataAccessRequestRepository extends JpaRepository<DataAccessRequest, Long> {

    /**
     * Find by request ID.
     */
    Optional<DataAccessRequest> findByRequestId(String requestId);

    /**
     * Find all pending requests.
     */
    List<DataAccessRequest> findByStatusOrderByCreatedAtDesc(RequestStatus status);

    /**
     * Find pending requests that require approval.
     */
    List<DataAccessRequest> findByStatusAndRequiresApprovalTrueOrderByCreatedAtDesc(RequestStatus status);

    /**
     * Find by vendor type.
     */
    Page<DataAccessRequest> findByVendorTypeOrderByCreatedAtDesc(VendorType vendorType, Pageable pageable);

    /**
     * Find by status with pagination.
     */
    Page<DataAccessRequest> findByStatusOrderByCreatedAtDesc(RequestStatus status, Pageable pageable);

    /**
     * Find by date range.
     */
    @Query("SELECT d FROM DataAccessRequest d WHERE d.createdAt BETWEEN :start AND :end ORDER BY d.createdAt DESC")
    List<DataAccessRequest> findByDateRange(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * Find by vendor and date range for export.
     */
    @Query("SELECT d FROM DataAccessRequest d WHERE d.vendorType = :vendor AND d.createdAt BETWEEN :start AND :end ORDER BY d.createdAt DESC")
    List<DataAccessRequest> findByVendorAndDateRange(
            @Param("vendor") VendorType vendor,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * Count by status.
     */
    long countByStatus(RequestStatus status);

    /**
     * Count pending requests requiring approval.
     */
    long countByStatusAndRequiresApprovalTrue(RequestStatus status);

    /**
     * Count by vendor type.
     */
    long countByVendorType(VendorType vendorType);

    /**
     * Count by direction.
     */
    long countByDirection(Direction direction);

    /**
     * Count requests created after a specific time.
     */
    long countByCreatedAtAfter(LocalDateTime since);

    /**
     * Recent requests (last N days).
     */
    @Query("SELECT d FROM DataAccessRequest d WHERE d.createdAt >= :since ORDER BY d.createdAt DESC")
    List<DataAccessRequest> findRecentRequests(@Param("since") LocalDateTime since);

    /**
     * Search by course name, teacher, or description.
     */
    @Query("SELECT d FROM DataAccessRequest d WHERE " +
           "LOWER(d.courseName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(d.teacherDisplayName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(d.description) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "ORDER BY d.createdAt DESC")
    Page<DataAccessRequest> searchRequests(@Param("search") String search, Pageable pageable);

    /**
     * Get statistics by vendor for dashboard.
     */
    @Query("SELECT d.vendorType, d.status, COUNT(d) FROM DataAccessRequest d " +
           "WHERE d.createdAt >= :since GROUP BY d.vendorType, d.status")
    List<Object[]> getStatsByVendorAndStatus(@Param("since") LocalDateTime since);

    /**
     * Get daily request counts for chart.
     */
    @Query("SELECT FUNCTION('DATE', d.createdAt), COUNT(d) FROM DataAccessRequest d " +
           "WHERE d.createdAt >= :since GROUP BY FUNCTION('DATE', d.createdAt) ORDER BY FUNCTION('DATE', d.createdAt)")
    List<Object[]> getDailyRequestCounts(@Param("since") LocalDateTime since);
}
