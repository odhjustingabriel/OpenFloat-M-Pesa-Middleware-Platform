package com.openfloat.mpesa.service;

import com.openfloat.mpesa.dto.BulkPayoutItemDto;
import com.openfloat.mpesa.dto.BulkPayoutResultDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BulkPayoutService — Batch B2C Disbursements & CSV Parsing")
class BulkPayoutServiceTest {

    @Mock
    private B2CService b2cService;

    private BulkPayoutService bulkPayoutService;

    @BeforeEach
    void setUp() {
        bulkPayoutService = new BulkPayoutService(b2cService);
    }

    @Test
    @DisplayName("processBulkPayouts() initiates disbursements and returns correct batch summary")
    void processBulkPayouts_validItems_returnsSuccessSummary() {
        UUID txn1 = UUID.randomUUID();
        UUID txn2 = UUID.randomUUID();

        when(b2cService.initiateDisbursement(eq("254712345678"), eq(new BigDecimal("1500.00")), anyString(), anyString(), any()))
                .thenReturn(txn1);
        when(b2cService.initiateDisbursement(eq("254798765432"), eq(new BigDecimal("2500.00")), anyString(), anyString(), any()))
                .thenReturn(txn2);

        List<BulkPayoutItemDto> items = List.of(
                BulkPayoutItemDto.builder()
                        .phoneNumber("0712345678")
                        .amount(new BigDecimal("1500.00"))
                        .commandId("SalaryPayment")
                        .remarks("Monthly Salary")
                        .build(),
                BulkPayoutItemDto.builder()
                        .phoneNumber("254798765432")
                        .amount(new BigDecimal("2500.00"))
                        .commandId("SalaryPayment")
                        .remarks("Monthly Salary")
                        .build()
        );

        BulkPayoutResultDto result = bulkPayoutService.processBulkPayouts(items);

        assertThat(result).isNotNull();
        assertThat(result.getBatchId()).startsWith("BATCH-");
        assertThat(result.getTotalRecords()).isEqualTo(2);
        assertThat(result.getSuccessfulCount()).isEqualTo(2);
        assertThat(result.getFailedCount()).isEqualTo(0);
        assertThat(result.getTotalAmount()).isEqualByComparingTo("4000.00");
        assertThat(result.getItemResults()).hasSize(2);
        assertThat(result.getItemResults().get(0).getTransactionId()).isEqualTo(txn1);
        assertThat(result.getItemResults().get(1).getTransactionId()).isEqualTo(txn2);
    }

    @Test
    @DisplayName("processBulkPayouts() flags items with invalid amounts or phone numbers as failed")
    void processBulkPayouts_invalidRecords_flagsAsFailed() {
        List<BulkPayoutItemDto> items = List.of(
                BulkPayoutItemDto.builder()
                        .phoneNumber("")
                        .amount(new BigDecimal("500.00"))
                        .build(),
                BulkPayoutItemDto.builder()
                        .phoneNumber("0712345678")
                        .amount(new BigDecimal("5.00")) // Less than KES 10 minimum
                        .build()
        );

        BulkPayoutResultDto result = bulkPayoutService.processBulkPayouts(items);

        assertThat(result.getSuccessfulCount()).isEqualTo(0);
        assertThat(result.getFailedCount()).isEqualTo(2);
        assertThat(result.getItemResults().get(0).getErrorMessage()).contains("Phone number is required");
        assertThat(result.getItemResults().get(1).getErrorMessage()).contains("Amount must be at least KES 10.00");
    }

    @Test
    @DisplayName("processCsvBulkPayouts() parses CSV stream with header correctly and dispatches B2C payouts")
    void processCsvBulkPayouts_validCsvWithHeader_parsesAndDispatches() {
        String csvContent = "phoneNumber,amount,commandId,remarks,occasion\n" +
                "0712345678,1000.00,SalaryPayment,Staff Bonus,Holiday\n" +
                "254798765432,2000.00,PromotionPayment,Promo Reward,\n";

        InputStream stream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

        when(b2cService.initiateDisbursement(eq("254712345678"), eq(new BigDecimal("1000.00")), eq("SalaryPayment"), eq("Staff Bonus"), eq("Holiday")))
                .thenReturn(UUID.randomUUID());
        when(b2cService.initiateDisbursement(eq("254798765432"), eq(new BigDecimal("2000.00")), eq("PromotionPayment"), eq("Promo Reward"), any()))
                .thenReturn(UUID.randomUUID());

        BulkPayoutResultDto result = bulkPayoutService.processCsvBulkPayouts(stream);

        assertThat(result).isNotNull();
        assertThat(result.getTotalRecords()).isEqualTo(2);
        assertThat(result.getSuccessfulCount()).isEqualTo(2);
        assertThat(result.getTotalAmount()).isEqualByComparingTo("3000.00");
    }
}
