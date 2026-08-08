package com.openfloat.mpesa.service;

import com.openfloat.mpesa.common.util.PhoneNumberUtils;
import com.openfloat.mpesa.dto.BulkPayoutItemDto;
import com.openfloat.mpesa.dto.BulkPayoutResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for batch processing and CSV upload parsing of B2C mobile money disbursements.
 *
 * <p>Phase 9 — Component 4: Bulk Payouts & Batch B2C Disbursement Engine</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkPayoutService {

    private final B2CService b2cService;

    /**
     * Executes batch B2C disbursements from a list of payout items.
     *
     * @param items List of beneficiary payout records
     * @return Execution summary with per-item status
     */
    public BulkPayoutResultDto processBulkPayouts(List<BulkPayoutItemDto> items) {
        String batchId = "BATCH-" + System.currentTimeMillis();
        log.info("Starting bulk payout batch [{}], total items: {}", batchId, items != null ? items.size() : 0);

        if (items == null || items.isEmpty()) {
            return BulkPayoutResultDto.builder()
                    .batchId(batchId)
                    .totalRecords(0)
                    .successfulCount(0)
                    .failedCount(0)
                    .totalAmount(BigDecimal.ZERO)
                    .processedAt(Instant.now())
                    .itemResults(List.of())
                    .build();
        }

        List<BulkPayoutResultDto.ItemResult> itemResults = new ArrayList<>();
        int successfulCount = 0;
        int failedCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        int row = 1;
        for (BulkPayoutItemDto item : items) {
            BulkPayoutResultDto.ItemResult result = processItem(row++, item);
            itemResults.add(result);

            if (result.isSuccess()) {
                successfulCount++;
                totalAmount = totalAmount.add(item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO);
            } else {
                failedCount++;
            }
        }

        log.info("Completed bulk payout batch [{}]: total={}, succeeded={}, failed={}, totalAmount=KES {}",
                batchId, items.size(), successfulCount, failedCount, totalAmount);

        return BulkPayoutResultDto.builder()
                .batchId(batchId)
                .totalRecords(items.size())
                .successfulCount(successfulCount)
                .failedCount(failedCount)
                .totalAmount(totalAmount)
                .processedAt(Instant.now())
                .itemResults(itemResults)
                .build();
    }

    /**
     * Parses a CSV file containing beneficiary disbursement records and dispatches B2C payouts.
     * CSV Format: {@code phoneNumber,amount,commandId,remarks,occasion}
     * Header row is optional and automatically skipped if detected.
     *
     * @param csvInputStream Stream of the uploaded CSV file
     * @return Execution summary with per-item status
     */
    public BulkPayoutResultDto processCsvBulkPayouts(InputStream csvInputStream) {
        List<BulkPayoutItemDto> items = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvInputStream, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] tokens = line.split(",");
                if (tokens.length < 2) {
                    log.warn("Skipping invalid CSV line {}: insufficient fields", lineNumber);
                    continue;
                }

                String phoneToken = tokens[0].trim();
                // Check if header line
                if (lineNumber == 1 && (phoneToken.equalsIgnoreCase("phone") || phoneToken.equalsIgnoreCase("phoneNumber") || phoneToken.equalsIgnoreCase("msisdn"))) {
                    log.debug("Skipping CSV header line");
                    continue;
                }

                try {
                    BigDecimal amount = new BigDecimal(tokens[1].trim());
                    String commandId = tokens.length > 2 && !tokens[2].isBlank() ? tokens[2].trim() : "SalaryPayment";
                    String remarks = tokens.length > 3 && !tokens[3].isBlank() ? tokens[3].trim() : "Bulk Payout";
                    String occasion = tokens.length > 4 && !tokens[4].isBlank() ? tokens[4].trim() : null;

                    BulkPayoutItemDto item = BulkPayoutItemDto.builder()
                            .phoneNumber(phoneToken)
                            .amount(amount)
                            .commandId(commandId)
                            .remarks(remarks)
                            .occasion(occasion)
                            .build();

                    items.add(item);
                } catch (Exception e) {
                    log.warn("Failed to parse CSV line {}: {}", lineNumber, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to read CSV stream for bulk payouts: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Failed to read bulk payout CSV file", e);
        }

        return processBulkPayouts(items);
    }

    private BulkPayoutResultDto.ItemResult processItem(int rowNumber, BulkPayoutItemDto item) {
        if (item == null) {
            return BulkPayoutResultDto.ItemResult.builder()
                    .rowNumber(rowNumber)
                    .success(false)
                    .errorMessage("Null item record")
                    .build();
        }

        if (item.getPhoneNumber() == null || item.getPhoneNumber().isBlank()) {
            return BulkPayoutResultDto.ItemResult.builder()
                    .rowNumber(rowNumber)
                    .amount(item.getAmount())
                    .success(false)
                    .errorMessage("Phone number is required")
                    .build();
        }

        if (item.getAmount() == null || item.getAmount().compareTo(BigDecimal.TEN) < 0) {
            return BulkPayoutResultDto.ItemResult.builder()
                    .rowNumber(rowNumber)
                    .phoneNumber(item.getPhoneNumber())
                    .amount(item.getAmount())
                    .success(false)
                    .errorMessage("Amount must be at least KES 10.00")
                    .build();
        }

        try {
            String normalizedPhone = PhoneNumberUtils.normalize(item.getPhoneNumber());
            String commandId = item.getCommandId() != null ? item.getCommandId() : "SalaryPayment";
            String remarks = item.getRemarks() != null ? item.getRemarks() : "Bulk Payout";

            UUID txnId = b2cService.initiateDisbursement(normalizedPhone, item.getAmount(), commandId, remarks, item.getOccasion());

            return BulkPayoutResultDto.ItemResult.builder()
                    .rowNumber(rowNumber)
                    .phoneNumber(normalizedPhone)
                    .amount(item.getAmount())
                    .success(true)
                    .transactionId(txnId)
                    .build();
        } catch (Exception e) {
            log.error("Bulk payout failed for row {} (phone: {}): {}", rowNumber, item.getPhoneNumber(), e.getMessage());
            return BulkPayoutResultDto.ItemResult.builder()
                    .rowNumber(rowNumber)
                    .phoneNumber(item.getPhoneNumber())
                    .amount(item.getAmount())
                    .success(false)
                    .errorMessage("Disbursement failed: " + e.getMessage())
                    .build();
        }
    }
}
