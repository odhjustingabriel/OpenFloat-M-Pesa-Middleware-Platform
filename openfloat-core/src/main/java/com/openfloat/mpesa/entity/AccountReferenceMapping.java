package com.openfloat.mpesa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a middleware-generated, unique Account Reference that links a
 * customer's upcoming M-Pesa payment to a specific {@link ClientApp}.
 * <p>
 * Flow:
 * <ol>
 *   <li>A client system calls {@code POST /api/v1/references/generate}.</li>
 *   <li>The middleware mints a reference (e.g. {@code ECOMM-8X92K4}) and stores it here.</li>
 *   <li>The client instructs their customer to pay to the shared Paybill using this reference.</li>
 *   <li>When M-Pesa sends a C2B confirmation callback, the middleware looks up the reference,
 *       marks it {@code PAID}, and dispatches a Webhook to the client's {@code callbackUrl}.</li>
 * </ol>
 * </p>
 */
@Entity
@Table(name = "account_reference_mappings", indexes = {
        @Index(name = "idx_acct_ref_reference", columnList = "account_reference", unique = true),
        @Index(name = "idx_acct_ref_client",    columnList = "client_app_id"),
        @Index(name = "idx_acct_ref_status",    columnList = "status"),
        @Index(name = "idx_acct_ref_expires",   columnList = "expires_at"),
        @Index(name = "idx_acct_ref_created",   columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountReferenceMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The unique, human-readable Account Reference displayed to the customer.
     * Format: {@code {PREFIX}-{RANDOM_ALPHANUMERIC}} e.g. {@code ECOMM-8X92K4}.
     */
    @Column(name = "account_reference", nullable = false, unique = true, length = 50)
    private String accountReference;

    /**
     * The client application that requested this reference.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_app_id", nullable = false)
    private ClientApp clientApp;

    /**
     * Snapshot of the client's callback URL at the time this reference was generated.
     * Stored separately so future URL changes on the ClientApp do not affect in-flight references.
     */
    @Column(name = "callback_url", nullable = false)
    private String callbackUrl;

    /**
     * Optional expected payment amount. If provided, the middleware will flag
     * discrepancies when the actual payment differs from this value.
     * Null means any amount is accepted.
     */
    @Column(name = "requested_amount", precision = 15, scale = 2)
    private BigDecimal requestedAmount;

    /** ISO 4217 currency code. Defaults to KES. */
    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "KES";

    /** Optional description or order ID from the client system for traceability. */
    @Column(name = "description")
    private String description;

    /**
     * Lifecycle status of this reference.
     * {@code PENDING}   — awaiting payment.
     * {@code PAID}      — payment received and webhook dispatched.
     * {@code EXPIRED}   — TTL elapsed without payment.
     * {@code CANCELLED} — manually cancelled by a Manager.
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    /** When this reference expires. Unpaid references past this timestamp are treated as EXPIRED. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Timestamp when payment was confirmed. */
    @Column(name = "paid_at")
    private Instant paidAt;

    /** The transaction that settled this reference (set after M-Pesa C2B confirmation). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
