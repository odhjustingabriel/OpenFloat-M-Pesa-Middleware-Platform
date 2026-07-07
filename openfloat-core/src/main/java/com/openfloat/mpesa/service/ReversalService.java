package com.openfloat.mpesa.service;

import com.openfloat.mpesa.common.exception.ResourceNotFoundException;
import com.openfloat.mpesa.entity.Transaction;
import com.openfloat.mpesa.entity.enums.ReconciliationStatus;
import com.openfloat.mpesa.entity.enums.TransactionStatus;
import com.openfloat.mpesa.entity.enums.TransactionType;
import com.openfloat.mpesa.integration.mpesa.DarajaClient;
import com.openfloat.mpesa.integration.mpesa.DarajaConfig;
import com.openfloat.mpesa.integration.mpesa.dto.ReversalRequest;
import com.openfloat.mpesa.integration.mpesa.dto.ReversalResponse;
import com.openfloat.mpesa.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReversalService {

    private final DarajaClient darajaClient;
    private final DarajaConfig darajaConfig;
    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final IdempotencyService idempotencyService;

    @Transactional
    public UUID initiateReversal(String transactionIdToReverse, String remarks) {
        // 1. Verify original transaction exists
        Transaction originalTxn = transactionRepository.findByTransactionId(transactionIdToReverse)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction to reverse not found with Transaction ID: " + transactionIdToReverse));

        // 2. Generate and Verify Idempotency
        String idempotencyKey = idempotencyService.generateIdempotencyKey(
                transactionIdToReverse,
                "REVERSAL",
                remarks
        );
        idempotencyService.checkIdempotency(idempotencyKey);

        // 3. Create initial Reversal Transaction in Pending state
        Transaction reversalTxn = Transaction.builder()
                .transactionType(TransactionType.REVERSAL)
                .phoneNumber(originalTxn.getPhoneNumber())
                .amount(originalTxn.getAmount())
                .paybill(originalTxn.getPaybill())
                .accountReference("REV_" + originalTxn.getTransactionId())
                .description(remarks)
                .status(TransactionStatus.PENDING)
                .reconciliationStatus(ReconciliationStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .build();

        reversalTxn = transactionService.save(reversalTxn);
        idempotencyService.saveIdempotencyKey(idempotencyKey);

        // 4. Build Reversal Request Parameters
        String callbackBase = darajaConfig.getCallbackBaseUrl();
        ReversalRequest reversalRequest = ReversalRequest.builder()
                .initiator("OpenFloatInitiator")
                .securityCredential("SandboxSecurityCredential")
                .commandId("TransactionReversal")
                .transactionId(transactionIdToReverse)
                .amount(originalTxn.getAmount().intValue())
                .receiver(originalTxn.getPaybill()) // Typically paybill/shortcode receives reversal
                .receiverIdType("1") // 1 for Shortcode, 4 for MSISDN
                .queueTimeOutUrl(callbackBase + "/api/v1/mpesa/callbacks/reversal?type=timeout")
                .resultUrl(callbackBase + "/api/v1/mpesa/callbacks/reversal?type=result")
                .remarks(remarks)
                .occasion("Erroneous transaction")
                .build();

        try {
            // 5. Send Reversal Request to Safaricom Daraja Reversal endpoint
            ReversalResponse response = darajaClient.initiateReversal(reversalRequest);

            // 6. Update Reversal transaction with conversation identifiers
            reversalTxn.setConversationId(response.getConversationId());
            reversalTxn.setOriginatorConversationId(response.getOriginatorConversationId());
            reversalTxn.setResultDescription(response.getResponseDescription());

            if (!"0".equals(response.getResponseCode())) {
                reversalTxn.setStatus(TransactionStatus.FAILED);
                reversalTxn.setResultCode(Integer.parseInt(response.getResponseCode()));
            }

            transactionService.save(reversalTxn);
            return reversalTxn.getId();

        } catch (Exception e) {
            log.error("Reversal request failed for transaction: {}", reversalTxn.getId(), e);
            reversalTxn.setStatus(TransactionStatus.FAILED);
            reversalTxn.setResultDescription("Failed to initiate reversal: " + e.getMessage());
            transactionService.save(reversalTxn);
            throw e;
        }
    }
}
