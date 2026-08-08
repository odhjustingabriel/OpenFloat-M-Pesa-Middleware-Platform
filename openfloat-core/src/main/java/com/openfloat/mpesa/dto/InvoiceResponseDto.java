package com.openfloat.mpesa.dto;

import com.openfloat.mpesa.entity.enums.InvoiceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for invoice details.
 *
 * <p>Phase 9 — Component 3: Invoicing Engine & Payment Fulfillment</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceResponseDto {

    private UUID id;
    private String invoiceNumber;
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private BigDecimal amount;
    private BigDecimal amountPaid;
    private BigDecimal balance;
    private String currency;
    private String accountReference;
    private String description;
    private LocalDate dueDate;
    private InvoiceStatus status;
    private Instant paidAt;
    private UUID transactionId;
    private Instant createdAt;
    private Instant updatedAt;
}
