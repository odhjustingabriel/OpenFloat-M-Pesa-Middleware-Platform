package com.openfloat.mpesa.mapper;

import com.openfloat.mpesa.common.event.TransactionCompletedEvent;
import com.openfloat.mpesa.dto.CallbackPayloadDto;
import com.openfloat.mpesa.dto.TransactionDto;
import com.openfloat.mpesa.entity.Transaction;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public TransactionDto toDto(Transaction transaction) {
        if (transaction == null) {
            return null;
        }

        return TransactionDto.builder()
                .id(transaction.getId())
                .transactionId(transaction.getTransactionId())
                .conversationId(transaction.getConversationId())
                .originatorConversationId(transaction.getOriginatorConversationId())
                .checkoutRequestId(transaction.getCheckoutRequestId())
                .merchantRequestId(transaction.getMerchantRequestId())
                .transactionType(transaction.getTransactionType())
                .phoneNumber(transaction.getPhoneNumber())
                .amount(transaction.getAmount())
                .paybill(transaction.getPaybill())
                .accountReference(transaction.getAccountReference())
                .description(transaction.getDescription())
                .status(transaction.getStatus())
                .resultCode(transaction.getResultCode())
                .resultDescription(transaction.getResultDescription())
                .reconciliationId(transaction.getReconciliationId())
                .reconciliationStatus(transaction.getReconciliationStatus())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }

    public TransactionCompletedEvent toEvent(Transaction transaction) {
        if (transaction == null) {
            return null;
        }

        return TransactionCompletedEvent.builder()
                .transactionId(transaction.getTransactionId() != null ? transaction.getTransactionId() : transaction.getId().toString())
                .conversationId(transaction.getConversationId())
                .originatorConversationId(transaction.getOriginatorConversationId())
                .transactionType(transaction.getTransactionType().name())
                .amount(transaction.getAmount())
                .phoneNumber(transaction.getPhoneNumber())
                .accountReference(transaction.getAccountReference())
                .paybill(transaction.getPaybill())
                .status(transaction.getStatus().name())
                .resultCode(transaction.getResultCode())
                .resultDescription(transaction.getResultDescription())
                .reconciliationId(transaction.getReconciliationId())
                .completedAt(transaction.getUpdatedAt() != null ? transaction.getUpdatedAt() : transaction.getCreatedAt())
                .build();
    }

    public CallbackPayloadDto toCallbackPayloadDto(Transaction transaction) {
        if (transaction == null) {
            return null;
        }

        return CallbackPayloadDto.builder()
                .id(transaction.getId())
                .transactionId(transaction.getTransactionId())
                .conversationId(transaction.getConversationId())
                .transactionType(transaction.getTransactionType().name())
                .phoneNumber(transaction.getPhoneNumber())
                .amount(transaction.getAmount())
                .paybill(transaction.getPaybill())
                .accountReference(transaction.getAccountReference())
                .status(transaction.getStatus().name())
                .resultCode(transaction.getResultCode())
                .resultDescription(transaction.getResultDescription())
                .completedAt(transaction.getUpdatedAt() != null ? transaction.getUpdatedAt() : transaction.getCreatedAt())
                .build();
    }
}
