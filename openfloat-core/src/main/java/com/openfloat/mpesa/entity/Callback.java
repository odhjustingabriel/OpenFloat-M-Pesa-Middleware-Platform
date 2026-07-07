package com.openfloat.mpesa.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Stores raw and processed M-Pesa callback payloads.
 * <p>
 * Raw payloads are stored as JSONB for full auditability.
 * Processed payloads contain the extracted, normalized data.
 * </p>
 */
@Entity
@Table(name = "callbacks", indexes = {
        @Index(name = "idx_cb_transaction_id", columnList = "transaction_id"),
        @Index(name = "idx_cb_received_at", columnList = "received_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Callback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Type(JsonType.class)
    @Column(name = "raw_payload", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> rawPayload;

    @Type(JsonType.class)
    @Column(name = "processed_payload", columnDefinition = "jsonb")
    private Map<String, Object> processedPayload;

    @Column(name = "callback_type", length = 20)
    private String callbackType;

    @Column(name = "received_at", nullable = false)
    @Builder.Default
    private Instant receivedAt = Instant.now();
}
