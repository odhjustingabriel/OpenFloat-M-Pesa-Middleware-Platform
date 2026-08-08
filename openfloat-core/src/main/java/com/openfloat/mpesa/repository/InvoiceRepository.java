package com.openfloat.mpesa.repository;

import com.openfloat.mpesa.entity.Invoice;
import com.openfloat.mpesa.entity.enums.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the Invoice entity.
 *
 * <p>Phase 9 — Component 3: Invoicing Engine & Payment Fulfillment</p>
 */
@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    Optional<Invoice> findByAccountReference(String accountReference);

    Page<Invoice> findByStatus(InvoiceStatus status, Pageable pageable);

    Page<Invoice> findByCustomerPhone(String customerPhone, Pageable pageable);

    /**
     * Find all unpaid invoices linked to a specific account reference.
     * Used for automatic payment matching when C2B/STK payments arrive.
     */
    List<Invoice> findByAccountReferenceAndStatusIn(String accountReference, List<InvoiceStatus> statuses);

    /**
     * Multi-criteria invoice search with optional filters.
     */
    @Query("""
            SELECT i FROM Invoice i
            WHERE (:status IS NULL OR i.status = :status)
              AND (:customerPhone IS NULL OR i.customerPhone = :customerPhone)
              AND (:dueDateFrom IS NULL OR i.dueDate >= :dueDateFrom)
              AND (:dueDateTo IS NULL OR i.dueDate <= :dueDateTo)
            ORDER BY i.createdAt DESC
            """)
    Page<Invoice> searchInvoices(
            @Param("status") InvoiceStatus status,
            @Param("customerPhone") String customerPhone,
            @Param("dueDateFrom") LocalDate dueDateFrom,
            @Param("dueDateTo") LocalDate dueDateTo,
            Pageable pageable
    );

    /**
     * Find overdue invoices that are still UNPAID past their due date.
     * Used by scheduled tasks to transition status to OVERDUE.
     */
    @Query("""
            SELECT i FROM Invoice i
            WHERE i.status = 'UNPAID'
              AND i.dueDate < :today
            """)
    List<Invoice> findOverdueInvoices(@Param("today") LocalDate today);
}
