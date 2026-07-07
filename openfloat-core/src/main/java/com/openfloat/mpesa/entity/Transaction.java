package com.openfloat.mpesa.entity;

import com.openfloat.mpesa.entity.enums.ReconciliationStatus;
import com.openfloat.mpesa.entity.enums.TransactionStatus;
import com.openfloat.mpesa.entity.enums.TransactionType;
import com.openfloat.mpesa.security.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Core transaction entity representing all M-Pesa transaction types.
 * <p>
 * Sensitive fields (phone_number, account_reference) are encrypted at rest
 * using AES-256-GCM via the {@link EncryptedStringConverter}.
 * </p>
 * <p>
 * Idempotency is enforced via unique constraints on conversation_id,
 * checkout_request_id, and merchant_request_id.
 * </p>
 */
@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_txn_conversation_id", columnList = "conversation_id"),
        @Index(name = "idx_txn_checkout_request_id", columnList = "checkout_request_id"),
        @Index(name = "idx_txn_merchant_request_id", columnList = "merchant_request_id"),
        @Index(name = "idx_txn_status", columnList = "status"),
        @Index(name = "idx_txn_paybill", columnList = "paybill"),
        @Index(name = "idx_txn_created_at", columnList = "created_at"),
        @Index(name = "idx_txn_idempotency_key", columnList = "idempotency_key", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_id", unique = true)
    private String transactionId;

    @Column(name = "conversation_id")
    private String conversationId;

    @Column(name = "originator_conversation_id")
    private String originatorConversationId;

    @Column(name = "checkout_request_id")
    private String checkoutRequestId;

    @Column(name = "merchant_request_id")
    private String merchantRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "paybill", nullable = false, length = 20)
    private String paybill;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "account_reference")
    private String accountReference;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(name = "result_code")
    private Integer resultCode;

    @Column(name = "result_description")
    private String resultDescription;

    @Column(name = "reconciliation_id")
    private String reconciliationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status", length = 20)
    @Builder.Default
    private ReconciliationStatus reconciliationStatus = ReconciliationStatus.PENDING;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
