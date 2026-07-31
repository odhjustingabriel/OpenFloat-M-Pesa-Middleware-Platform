package com.openfloat.mpesa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit log for every outbound Webhook dispatch attempt made to a client application.
 * <p>
 * Every time the middleware attempts to deliver a payment notification to a client's
 * {@code callbackUrl}, a row is written here regardless of success or failure.
 * This allows Managers to:
 * <ul>
 *   <li>Inspect the exact payload and response for any delivery.</li>
 *   <li>Identify failed deliveries ({@code isSuccess = false}).</li>
 *   <li>Trigger a manual redrive via {@code POST /api/v1/webhooks/{id}/redrive}.</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "webhook_delivery_logs", indexes = {
        @Index(name = "idx_webhook_log_txn_id",  columnList = "transaction_id"),
        @Index(name = "idx_webhook_log_client",  columnList = "client_app_id"),
        @Index(name = "idx_webhook_log_success", columnList = "is_success"),
        @Index(name = "idx_webhook_log_created", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The M-Pesa transaction that triggered this webhook dispatch. May be null for test redrives. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    /** The client application that was the target of this webhook. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_app_id", nullable = false)
    private ClientApp clientApp;

    /** The Account Reference that triggered this webhook (for traceability). */
    @Column(name = "account_reference", length = 50)
    private String accountReference;

    /** The URL that was called. Stored as a snapshot to survive future URL changes. */
    @Column(name = "target_url", nullable = false)
    private String targetUrl;

    /**
     * HTTP status code received from the client.
     * Null if a network-level error occurred (e.g. connection refused / DNS failure).
     */
    @Column(name = "http_status")
    private Integer httpStatus;

    /** The full JSON payload that was sent to the client's webhook endpoint. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", nullable = false, columnDefinition = "jsonb")
    private String requestPayload;

    /** Raw response body from the client (first 2KB retained). */
    @Column(name = "response_body", length = 2000)
    private String responseBody;

    /** Error message if the HTTP call failed or returned a non-2xx status. */
    @Column(name = "error_message")
    private String errorMessage;

    /**
     * Delivery attempt number for this webhook event.
     * 1 = original attempt; increments on each redrive.
     */
    @Column(name = "attempt_number", nullable = false)
    @Builder.Default
    private Integer attemptNumber = 1;

    /** True when the client returned a 2xx response. */
    @Column(name = "is_success", nullable = false)
    @Builder.Default
    private boolean success = false;

    /** Timestamp when the delivery was confirmed successful. */
    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
