package com.openfloat.mpesa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallbackPayloadDto {
    private UUID id;
    private String transactionId;
    private String conversationId;
    private String transactionType;
    private String phoneNumber;
    private BigDecimal amount;
    private String paybill;
    private String accountReference;
    private String status;
    private Integer resultCode;
    private String resultDescription;
    private Instant completedAt;
}
