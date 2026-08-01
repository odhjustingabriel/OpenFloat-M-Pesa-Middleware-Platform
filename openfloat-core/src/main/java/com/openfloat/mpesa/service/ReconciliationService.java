package com.openfloat.mpesa.service;

import com.openfloat.mpesa.common.exception.ResourceNotFoundException;
import com.openfloat.mpesa.dto.ReconciliationOverrideRequestDto;
import com.openfloat.mpesa.entity.AccountReferenceMapping;
import com.openfloat.mpesa.entity.Transaction;
import com.openfloat.mpesa.entity.enums.ReconciliationStatus;
import com.openfloat.mpesa.entity.enums.TransactionStatus;
import com.openfloat.mpesa.repository.AccountReferenceMappingRepository;
import com.openfloat.mpesa.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service handling manual reconciliation overrides by Managers, matching unmatched
 * payments against generated Account References, and updating status flags.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class ReconciliationService {

    private final TransactionRepository transactionRepository;
    private final AccountReferenceMappingRepository mappingRepository;
    private final WebhookDispatcherService webhookDispatcherService;

    /**
     * Executes a manual reconciliation override by a Manager.
     * Links a transaction to an Account Reference, marks reconciliation as MATCHED,
     * and triggers a webhook dispatch to the mapped client application.
     */
    @Transactional
    public Map<String, Object> executeManualOverride(ReconciliationOverrideRequestDto dto, String managerUsername) {
        String refCode = dto.getAccountReference().trim().toUpperCase();

        AccountReferenceMapping mapping = mappingRepository.findByAccountReference(refCode)
                .orElseThrow(() -> new ResourceNotFoundException("Account reference mapping not found: " + refCode));

        Transaction transaction;
        if (dto.getTransactionId() != null) {
            transaction = transactionRepository.findById(dto.getTransactionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with ID: " + dto.getTransactionId()));
        } else {
            Optional<Transaction> txOpt = transactionRepository.findByAccountReference(refCode);
            if (txOpt.isEmpty()) {
                throw new ResourceNotFoundException("No transaction found matching account reference: " + refCode);
            }
            transaction = txOpt.get();
        }

        // Update transaction reconciliation status
        transaction.setReconciliationStatus(ReconciliationStatus.MATCHED);
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setResultDescription("Manually reconciled by " + managerUsername + ". Reason: " + dto.getReason());
        transaction.setUpdatedAt(Instant.now());
        Transaction savedTx = transactionRepository.save(transaction);

        // Update mapping status
        mapping.setStatus("PAID");
        mapping.setPaidAt(Instant.now());
        mapping.setTransaction(savedTx);
        mappingRepository.save(mapping);

        log.info("Manual reconciliation override executed by '{}' for Transaction ID={} and AccountReference='{}'",
                managerUsername, savedTx.getId(), refCode);

        // Dispatch Webhook to client system
        webhookDispatcherService.dispatchWebhookForTransaction(savedTx);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Transaction successfully reconciled and webhook dispatched to " + mapping.getClientApp().getClientName());
        response.put("transactionId", savedTx.getId());
        response.put("accountReference", refCode);
        response.put("reconciledBy", managerUsername);
        response.put("timestamp", Instant.now().toString());

        return response;
    }
}
