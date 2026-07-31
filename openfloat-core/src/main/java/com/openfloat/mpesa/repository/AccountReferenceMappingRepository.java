package com.openfloat.mpesa.repository;

import com.openfloat.mpesa.entity.AccountReferenceMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link AccountReferenceMapping} — generated Account References
 * linking M-Pesa Paybill payments to registered client applications.
 */
@Repository
public interface AccountReferenceMappingRepository extends JpaRepository<AccountReferenceMapping, UUID> {

    /** Look up a reference by its exact account reference string (e.g. ECOMM-8X92K4). */
    Optional<AccountReferenceMapping> findByAccountReference(String accountReference);

    /** Check if a reference string already exists (for uniqueness enforcement). */
    boolean existsByAccountReference(String accountReference);

    /** Retrieve all references for a specific client app. */
    List<AccountReferenceMapping> findAllByClientAppId(UUID clientAppId);

    /** Retrieve all pending references that have not yet been paid or expired. */
    List<AccountReferenceMapping> findAllByStatus(String status);

    /**
     * Retrieve all PENDING references whose TTL has elapsed.
     * Used by the scheduler to mark stale references as EXPIRED.
     */
    @Query("""
            SELECT m FROM AccountReferenceMapping m
            WHERE m.status = 'PENDING'
              AND m.expiresAt < :now
            """)
    List<AccountReferenceMapping> findExpiredPendingReferences(@Param("now") Instant now);

    /**
     * Count references by status for a given client app.
     * Useful for client app summary reporting in the staff portal.
     */
    @Query("""
            SELECT COUNT(m) FROM AccountReferenceMapping m
            WHERE m.clientApp.id = :clientAppId
              AND m.status = :status
            """)
    long countByClientAppIdAndStatus(@Param("clientAppId") UUID clientAppId,
                                     @Param("status") String status);
}
