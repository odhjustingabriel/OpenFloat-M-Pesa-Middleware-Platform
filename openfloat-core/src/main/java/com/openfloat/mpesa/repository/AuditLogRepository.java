package com.openfloat.mpesa.repository;

import com.openfloat.mpesa.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for AuditLog entity with chain verification support.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByUsername(String username, Pageable pageable);

    Page<AuditLog> findByAction(String action, Pageable pageable);

    Page<AuditLog> findByTimestampBetween(Instant start, Instant end, Pageable pageable);

    /**
     * Returns the most recent audit log entry for hash chain continuation.
     */
    @Query("SELECT a FROM AuditLog a ORDER BY a.timestamp DESC LIMIT 1")
    Optional<AuditLog> findLatest();
}
