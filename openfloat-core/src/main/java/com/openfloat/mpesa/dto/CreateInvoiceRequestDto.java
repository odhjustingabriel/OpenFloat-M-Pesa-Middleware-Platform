package com.openfloat.mpesa.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request DTO for creating a new invoice.
 *
 * <p>Phase 9 — Component 3: Invoicing Engine & Payment Fulfillment</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateInvoiceRequestDto {

    @NotBlank(message = "Customer phone number is required")
    @Pattern(regexp = "^254\\d{9}$", message = "Phone must be in 254XXXXXXXXX format")
    private String customerPhone;

    private String customerName;

    @Email(message = "Must be a valid email address")
    private String customerEmail;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Minimum invoice amount is KES 1.00")
    private BigDecimal amount;

    @NotNull(message = "Due date is required")
    @FutureOrPresent(message = "Due date must be today or in the future")
    private LocalDate dueDate;

    /** Optional account reference for M-Pesa payment matching. If null, one will be auto-generated. */
    private String accountReference;

    /** Free-text description or line items summary */
    private String description;
}
