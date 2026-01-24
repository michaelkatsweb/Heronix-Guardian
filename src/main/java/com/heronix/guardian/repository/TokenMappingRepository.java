package com.heronix.guardian.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.heronix.guardian.model.domain.TokenMapping;
import com.heronix.guardian.model.domain.TokenMapping.EntityType;

/**
 * Repository for TokenMapping entities.
 */
@Repository
public interface TokenMappingRepository extends JpaRepository<TokenMapping, Long> {

    /**
     * Find by token value.
     */
    Optional<TokenMapping> findByTokenValue(String tokenValue);

    /**
     * Find all mappings for a request.
     */
    List<TokenMapping> findByDataAccessRequestIdOrderByEntityTypeAscDisplayIdentifierAsc(Long requestId);

    /**
     * Find by entity type for a request.
     */
    List<TokenMapping> findByDataAccessRequestIdAndEntityType(Long requestId, EntityType entityType);

    /**
     * Count by entity type for a request.
     */
    long countByDataAccessRequestIdAndEntityType(Long requestId, EntityType entityType);

    /**
     * Count total by entity type (across all requests).
     */
    long countByEntityType(EntityType entityType);

    /**
     * Find transmitted mappings for a request.
     */
    List<TokenMapping> findByDataAccessRequestIdAndTransmittedTrue(Long requestId);

    /**
     * Search by display identifier.
     */
    @Query("SELECT t FROM TokenMapping t WHERE " +
           "LOWER(t.displayIdentifier) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "ORDER BY t.createdAt DESC")
    List<TokenMapping> searchByDisplayIdentifier(@Param("search") String search);
}
