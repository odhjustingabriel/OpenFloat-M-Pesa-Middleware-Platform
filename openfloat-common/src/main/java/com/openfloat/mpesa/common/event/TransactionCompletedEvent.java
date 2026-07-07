package com.openfloat.mpesa.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event published when a transaction reaches a final state (SUCCESS/FAILED).
 * Consumed by the ERP Connector Service for downstream integration.
 * <p>
 * Published to RabbitMQ exchange: {@code exchange.transaction.completed}
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionCompletedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String transactionId;
    private String conversationId;
    private String originatorConversationId;
    private String transactionType;
    private BigDecimal amount;
    private String phoneNumber;
    private String accountReference;
    private String paybill;
    private String status;
    private Integer resultCode;
    private String resultDescription;
    private String reconciliationId;
    private Instant completedAt;
}
