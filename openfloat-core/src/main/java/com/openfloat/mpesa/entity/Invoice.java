package com.openfloat.mpesa.entity;

import com.openfloat.mpesa.entity.enums.InvoiceStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Represents a customer invoice that can be linked to an Account Reference
 * for automatic payment fulfillment when C2B or STK Push payments arrive.
 *
 * <p>Phase 9 — Component 3: Invoicing Engine & Payment Fulfillment</p>
 */
@Entity
@Table(name = "invoices", indexes = {
        @Index(name = "idx_inv_number",      columnList = "invoice_number", unique = true),
        @Index(name = "idx_inv_status",      columnList = "status"),
        @Index(name = "idx_inv_due_date",    columnList = "due_date"),
        @Index(name = "idx_inv_customer",    columnList = "customer_phone"),
        @Index(name = "idx_inv_acct_ref",    columnList = "account_reference"),
        @Index(name = "idx_inv_created_at",  columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Unique invoice number, e.g. INV-2026-00042 */
    @Column(name = "invoice_number", nullable = false, unique = true, length = 50)
    private String invoiceNumber;

    /** Customer name or identifier for display purposes */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** Customer phone number in E.164 format (254XXXXXXXXX) */
    @Column(name = "customer_phone", nullable = false, length = 20)
    private String customerPhone;

    /** Customer email address for notification purposes */
    @Column(name = "customer_email", length = 200)
    private String customerEmail;

    /** Total amount due on this invoice */
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /** Amount paid so far against this invoice */
    @Column(name = "amount_paid", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal amountPaid = BigDecimal.ZERO;

    /** ISO 4217 currency code. Defaults to KES */
    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "KES";

    /** Account Reference that customers use when paying via M-Pesa */
    @Column(name = "account_reference", length = 50)
    private String accountReference;

    /** Free-text description or line items summary */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Invoice due date */
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    /** Invoice lifecycle status */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.UNPAID;

    /** Timestamp when invoice was fully paid */
    @Column(name = "paid_at")
    private Instant paidAt;

    /** The transaction that settled or last contributed to this invoice */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * Returns the outstanding balance on this invoice.
     */
    public BigDecimal getBalance() {
        return amount.subtract(amountPaid);
    }
}
