package com.openfloat.mpesa.dto;

import com.openfloat.mpesa.entity.enums.ReconciliationStatus;
import com.openfloat.mpesa.entity.enums.TransactionStatus;
import com.openfloat.mpesa.entity.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDto {

    private UUID id;
    private String transactionId;
    private String conversationId;
    private String originatorConversationId;
    private String checkoutRequestId;
    private String merchantRequestId;
    private TransactionType transactionType;
    private String phoneNumber;
    private BigDecimal amount;
    private String paybill;
    private String accountReference;
    private String description;
    private TransactionStatus status;
    private Integer resultCode;
    private String resultDescription;
    private String reconciliationId;
    private ReconciliationStatus reconciliationStatus;
    private Instant createdAt;
    private Instant updatedAt;
    
    // Associated callback payload details
    private Map<String, Object> callbackPayload;
}
