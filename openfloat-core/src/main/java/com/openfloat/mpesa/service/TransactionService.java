package com.openfloat.mpesa.service;

import com.openfloat.mpesa.common.exception.ResourceNotFoundException;
import com.openfloat.mpesa.dto.TransactionDto;
import com.openfloat.mpesa.dto.TransactionSearchCriteria;
import com.openfloat.mpesa.entity.Callback;
import com.openfloat.mpesa.entity.Transaction;
import com.openfloat.mpesa.entity.enums.TransactionStatus;
import com.openfloat.mpesa.mapper.TransactionMapper;
import com.openfloat.mpesa.repository.CallbackRepository;
import com.openfloat.mpesa.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CallbackRepository callbackRepository;
    private final TransactionMapper transactionMapper;

    /**
     * Retrieves a transaction by its internal UUID and includes the associated callback details.
     */
    public TransactionDto getTransaction(UUID id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id));

        TransactionDto dto = transactionMapper.toDto(transaction);

        // Fetch callbacks associated with this transaction and embed in DTO
        List<Callback> callbacks = callbackRepository.findByTransactionId(id);
        if (!callbacks.isEmpty()) {
            // Use the processed payload of the latest callback, or raw payload as fallback
            Callback latestCallback = callbacks.get(callbacks.size() - 1);
            if (latestCallback.getProcessedPayload() != null) {
                dto.setCallbackPayload(latestCallback.getProcessedPayload());
            } else {
                dto.setCallbackPayload(latestCallback.getRawPayload());
            }
        }

        return dto;
    }

    /**
     * Searches transactions with filtering, pagination, and sorting.
     */
    public Page<TransactionDto> searchTransactions(TransactionSearchCriteria criteria) {
        Sort sort = Sort.by(Sort.Direction.fromString(criteria.getSortDir()), criteria.getSortBy());
        Pageable pageable = PageRequest.of(criteria.getPage(), criteria.getSize(), sort);

        Page<Transaction> page = transactionRepository.searchTransactions(
                criteria.getStartDate(),
                criteria.getEndDate(),
                criteria.getPaybill(),
                criteria.getStatus(),
                criteria.getTransactionType(),
                criteria.getReconciliationStatus(),
                pageable
        );

        // Convert page entities to DTOs
        List<TransactionDto> dtos = page.getContent().stream()
                .map(transactionMapper::toDto)
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    @Transactional
    public Transaction save(Transaction transaction) {
        log.debug("Saving transaction: Type={}, Amount={}", transaction.getTransactionType(), transaction.getAmount());
        return transactionRepository.save(transaction);
    }

    @Transactional
    public void updateStatus(UUID id, TransactionStatus status, Integer resultCode, String resultDesc, String transactionId) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id));

        transaction.setStatus(status);
        transaction.setResultCode(resultCode);
        transaction.setResultDescription(resultDesc);
        if (transactionId != null) {
            transaction.setTransactionId(transactionId);
        }
        
        transactionRepository.save(transaction);
        log.info("Updated transaction {} status to {}", id, status);
    }
}
