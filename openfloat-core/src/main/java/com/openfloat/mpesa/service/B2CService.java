package com.openfloat.mpesa.service;

import com.openfloat.mpesa.common.util.PhoneNumberUtils;
import com.openfloat.mpesa.entity.Transaction;
import com.openfloat.mpesa.entity.enums.ReconciliationStatus;
import com.openfloat.mpesa.entity.enums.TransactionStatus;
import com.openfloat.mpesa.entity.enums.TransactionType;
import com.openfloat.mpesa.integration.mpesa.DarajaClient;
import com.openfloat.mpesa.integration.mpesa.DarajaConfig;
import com.openfloat.mpesa.integration.mpesa.dto.B2CRequest;
import com.openfloat.mpesa.integration.mpesa.dto.B2CResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class B2CService {

    private final DarajaClient darajaClient;
    private final DarajaConfig darajaConfig;
    private final TransactionService transactionService;
    private final IdempotencyService idempotencyService;

    @Transactional
    public UUID initiateDisbursement(String msisdn, BigDecimal amount, String commandId, String remarks, String occasion) {
        String normalizedPhone = PhoneNumberUtils.normalize(msisdn);

        // 1. Generate and Verify Idempotency
        String idempotencyKey = idempotencyService.generateIdempotencyKey(
                normalizedPhone,
                amount.toString(),
                commandId,
                remarks
        );
        idempotencyService.checkIdempotency(idempotencyKey);

        // 2. Persist initial Transaction in Pending state
        Transaction transaction = Transaction.builder()
                .transactionType(TransactionType.B2C)
                .phoneNumber(normalizedPhone)
                .amount(amount)
                .paybill(darajaConfig.getShortcode()) // Initiated from our shortcode/paybill
                .accountReference("B2C_" + commandId)
                .description(remarks)
                .status(TransactionStatus.PENDING)
                .reconciliationStatus(ReconciliationStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .build();

        transaction = transactionService.save(transaction);
        idempotencyService.saveIdempotencyKey(idempotencyKey);

        // 3. Build B2C Request Parameters
        String callbackBase = darajaConfig.getCallbackBaseUrl();
        B2CRequest b2cRequest = B2CRequest.builder()
                .initiatorName("OpenFloatInitiator") // Sandbox default or configured value
                .securityCredential("SandboxSecurityCredential") // Stub/Sandbox config
                .commandId(commandId != null ? commandId : "PromotionPayment")
                .amount(amount.intValue())
                .partyA(darajaConfig.getShortcode())
                .partyB(normalizedPhone)
                .remarks(remarks)
                .queueTimeOutUrl(callbackBase + "/api/v1/mpesa/callbacks/b2c?type=timeout")
                .resultUrl(callbackBase + "/api/v1/mpesa/callbacks/b2c?type=result")
                .occasion(occasion)
                .build();

        try {
            // 4. Send request to Safaricom Daraja B2C endpoint
            B2CResponse response = darajaClient.initiateB2cPayment(b2cRequest);

            // 5. Update transaction with conversation identifiers
            transaction.setConversationId(response.getConversationId());
            transaction.setOriginatorConversationId(response.getOriginatorConversationId());
            transaction.setResultDescription(response.getResponseDescription());

            if (!"0".equals(response.getResponseCode())) {
                transaction.setStatus(TransactionStatus.FAILED);
                transaction.setResultCode(Integer.parseInt(response.getResponseCode()));
            }

            transactionService.save(transaction);
            return transaction.getId();

        } catch (Exception e) {
            log.error("B2C disbursement failed for transaction: {}", transaction.getId(), e);
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setResultDescription("Failed to initiate B2C disbursement: " + e.getMessage());
            transactionService.save(transaction);
            throw e;
        }
    }
}
