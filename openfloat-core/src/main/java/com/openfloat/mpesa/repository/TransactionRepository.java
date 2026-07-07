package com.openfloat.mpesa.repository;

import com.openfloat.mpesa.entity.Transaction;
import com.openfloat.mpesa.entity.enums.ReconciliationStatus;
import com.openfloat.mpesa.entity.enums.TransactionStatus;
import com.openfloat.mpesa.entity.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Transaction entity with custom search queries.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByTransactionId(String transactionId);

    Optional<Transaction> findByConversationId(String conversationId);

    Optional<Transaction> findByCheckoutRequestId(String checkoutRequestId);

    Optional<Transaction> findByMerchantRequestId(String merchantRequestId);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    /**
     * Multi-criteria transaction search with optional filters.
     * All null parameters are treated as "match any".
     */
    @Query("""
            SELECT t FROM Transaction t
            WHERE (:startDate IS NULL OR t.createdAt >= :startDate)
              AND (:endDate IS NULL OR t.createdAt <= :endDate)
              AND (:paybill IS NULL OR t.paybill = :paybill)
              AND (:status IS NULL OR t.status = :status)
              AND (:transactionType IS NULL OR t.transactionType = :transactionType)
              AND (:reconciliationStatus IS NULL OR t.reconciliationStatus = :reconciliationStatus)
            """)
    Page<Transaction> searchTransactions(
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("paybill") String paybill,
            @Param("status") TransactionStatus status,
            @Param("transactionType") TransactionType transactionType,
            @Param("reconciliationStatus") ReconciliationStatus reconciliationStatus,
            Pageable pageable
    );

    Page<Transaction> findByStatus(TransactionStatus status, Pageable pageable);

    Page<Transaction> findByPaybill(String paybill, Pageable pageable);

    long countByStatus(TransactionStatus status);
}
