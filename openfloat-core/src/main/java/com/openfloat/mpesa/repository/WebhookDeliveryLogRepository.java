package com.openfloat.mpesa.repository;

import com.openfloat.mpesa.entity.WebhookDeliveryLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link WebhookDeliveryLog} — audit trail of all outbound webhook dispatches.
 */
@Repository
public interface WebhookDeliveryLogRepository extends JpaRepository<WebhookDeliveryLog, UUID> {

    /** Retrieve all delivery logs for a specific client app, newest first. */
    Page<WebhookDeliveryLog> findAllByClientAppIdOrderByCreatedAtDesc(UUID clientAppId, Pageable pageable);

    /** Retrieve all delivery logs for a specific transaction. */
    List<WebhookDeliveryLog> findAllByTransactionId(UUID transactionId);

    /** Retrieve all failed deliveries for a client app (for Manager review & redrive). */
    @Query("""
            SELECT w FROM WebhookDeliveryLog w
            WHERE w.clientApp.id = :clientAppId
              AND w.success = false
            ORDER BY w.createdAt DESC
            """)
    List<WebhookDeliveryLog> findFailedByClientAppId(@Param("clientAppId") UUID clientAppId);

    /** Retrieve all failed webhook deliveries across all clients (Admin view). */
    @Query("""
            SELECT w FROM WebhookDeliveryLog w
            WHERE w.success = false
            ORDER BY w.createdAt DESC
            """)
    Page<WebhookDeliveryLog> findAllFailed(Pageable pageable);

    /** Count total delivery attempts for an account reference (to track retry depth). */
    long countByAccountReference(String accountReference);
}
