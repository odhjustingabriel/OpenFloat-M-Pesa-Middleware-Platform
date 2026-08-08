package com.openfloat.mpesa.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Represents a single beneficiary item in a bulk B2C payout request.
 *
 * <p>Phase 9 — Component 4: Bulk Payouts & Batch B2C Disbursement Engine</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkPayoutItemDto {

    @NotBlank(message = "Beneficiary phone number is required")
    @Pattern(regexp = "^(254|0)?7\\d{8}$|^(254|0)?1\\d{8}$", message = "Phone number must be a valid Kenyan mobile number")
    private String phoneNumber;

    @NotNull(message = "Disbursement amount is required")
    @DecimalMin(value = "10.00", message = "Minimum B2C payout amount is KES 10.00")
    private BigDecimal amount;

    @Builder.Default
    private String commandId = "SalaryPayment";

    @Builder.Default
    private String remarks = "Bulk Payout Disbursement";

    private String occasion;
}
