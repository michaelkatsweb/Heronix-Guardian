package com.heronix.guardian.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.heronix.guardian.model.domain.VendorCredential;
import com.heronix.guardian.model.enums.VendorType;

/**
 * Repository for VendorCredential entity.
 */
@Repository
public interface VendorCredentialRepository extends JpaRepository<VendorCredential, Long> {

    /**
     * Find all active credentials.
     */
    List<VendorCredential> findByActiveTrue();

    /**
     * Find credentials by vendor type.
     */
    List<VendorCredential> findByVendorType(VendorType vendorType);

    /**
     * Find active credentials by vendor type.
     */
    List<VendorCredential> findByVendorTypeAndActiveTrue(VendorType vendorType);

    /**
     * Find credential by connection name.
     */
    Optional<VendorCredential> findByConnectionName(String connectionName);

    /**
     * Find credentials for a specific campus.
     */
    List<VendorCredential> findByCampusIdAndActiveTrue(Long campusId);

    /**
     * Find district-wide credentials (no campus).
     */
    List<VendorCredential> findByCampusIdIsNullAndActiveTrue();

    /**
     * Find credential by vendor type and campus.
     */
    Optional<VendorCredential> findByVendorTypeAndCampusIdAndActiveTrue(VendorType vendorType, Long campusId);

    /**
     * Check if a connection name already exists.
     */
    boolean existsByConnectionName(String connectionName);
}
