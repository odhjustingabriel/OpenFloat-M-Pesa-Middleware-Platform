package com.openfloat.mpesa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openfloat.mpesa.common.event.TransactionCompletedEvent;
import com.openfloat.mpesa.entity.Callback;
import com.openfloat.mpesa.entity.Transaction;
import com.openfloat.mpesa.entity.enums.TransactionStatus;
import com.openfloat.mpesa.entity.enums.TransactionType;
import com.openfloat.mpesa.event.TransactionEventPublisher;
import com.openfloat.mpesa.mapper.CallbackMapper;
import com.openfloat.mpesa.mapper.TransactionMapper;
import com.openfloat.mpesa.repository.CallbackRepository;
import com.openfloat.mpesa.repository.TransactionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"unchecked", "null", "unused"})
public class CallbackService {

    private final TransactionRepository transactionRepository;
    private final CallbackRepository callbackRepository;
    private final TransactionEventPublisher eventPublisher;
    private final TransactionMapper transactionMapper;
    private final CallbackMapper callbackMapper;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Processes STK Push final response callback.
     */
    @Transactional
    public void processStkCallback(Map<String, Object> payload) {
        log.info("Processing STK Callback payload");
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            Map<String, Object> body = (Map<String, Object>) payload.get("Body");
            Map<String, Object> stkCallback = (Map<String, Object>) body.get("stkCallback");

            String merchantRequestId = (String) stkCallback.get("MerchantRequestID");
            String checkoutRequestId = (String) stkCallback.get("CheckoutRequestID");
            Integer resultCode = (Integer) stkCallback.get("ResultCode");
            String resultDesc = (String) stkCallback.get("ResultDesc");

            // 1. Locate the corresponding transaction
            Optional<Transaction> transactionOpt = transactionRepository.findByCheckoutRequestId(checkoutRequestId);
            if (transactionOpt.isEmpty()) {
                log.warn("STK callback checkoutRequestId [{}] does not match any transaction", checkoutRequestId);
                return;
            }

            Transaction transaction = transactionOpt.get();

            // Deduplicate: check if already processed
            if (transaction.getStatus() != TransactionStatus.PENDING) {
                log.info("STK Callback for checkoutRequestId [{}] already processed. Status: {}", checkoutRequestId, transaction.getStatus());
                return;
            }

            // Extract callback details
            Map<String, Object> processedDetails = new HashMap<>();
            processedDetails.put("ResultCode", resultCode);
            processedDetails.put("ResultDesc", resultDesc);

            String mpesaReceipt = null;
            if (resultCode == 0) {
                transaction.setStatus(TransactionStatus.SUCCESS);
                Map<String, Object> metadata = (Map<String, Object>) stkCallback.get("CallbackMetadata");
                List<Map<String, Object>> items = (List<Map<String, Object>>) metadata.get("Item");

                for (Map<String, Object> item : items) {
                    String name = (String) item.get("Name");
                    Object value = item.get("Value");
                    processedDetails.put(name, value);
                    if ("MpesaReceiptNumber".equals(name)) {
                        mpesaReceipt = (String) value;
                    }
                }
                transaction.setReconciliationId("REC-" + (mpesaReceipt != null ? mpesaReceipt : UUID.randomUUID().toString().substring(0, 8).toUpperCase()));
            } else {
                transaction.setStatus(TransactionStatus.FAILED);
            }

            transaction.setResultCode(resultCode);
            transaction.setResultDescription(resultDesc);
            if (mpesaReceipt != null) {
                transaction.setTransactionId(mpesaReceipt);
            }
            transaction.setUpdatedAt(Instant.now());

            // 2. Persist updated transaction
            transaction = transactionRepository.save(transaction);

            // 3. Store raw callback payload for auditing
            Callback callback = callbackMapper.toEntity(transaction.getId(), "STK", payload, processedDetails);
            callbackRepository.save(callback);

            // 4. Publish Event to RabbitMQ
            publishTransactionCompleted(transaction);

        } catch (Exception e) {
            log.error("Failed to parse and process STK Callback: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Invalid STK callback payload structure", e);
        } finally {
            recordCallbackProcessingTime("STK", sample);
        }
    }

    /**
     * Processes C2B validation and confirmation callbacks.
     */
    @Transactional
    public Map<String, Object> processC2bCallback(String type, Map<String, Object> payload) {
        log.info("Processing C2B Callback. Type: {}", type);
        
        // Extract validation/confirmation attributes
        String transId = (String) payload.get("TransID");
        String billRef = (String) payload.get("BillRefNumber");
        String msisdn = (String) payload.get("MSISDN");
        String amountStr = (String) payload.get("TransAmount");
        String shortcode = (String) payload.get("BusinessShortCode");
        
        BigDecimal amount = new BigDecimal(amountStr != null ? amountStr : "0.0");

        if ("validation".equalsIgnoreCase(type)) {
            // Validation callback expects response returning ResultCode: 0 to accept or 1 to reject.
            log.info("Validating C2B payment for TransID: {}, Ref: {}, Amount: {}", transId, billRef, amount);
            Map<String, Object> response = new HashMap<>();
            response.put("ResultCode", "0");
            response.put("ResultDescription", "Accepted");
            return response;
        }

        // Confirmation callback: persist transaction and publish completion event
        Optional<Transaction> existingOpt = transactionRepository.findByTransactionId(transId);
        if (existingOpt.isPresent()) {
            log.info("C2B transaction [{}] already processed", transId);
            return Map.of("ResultCode", "0", "ResultDescription", "Already Processed");
        }

        Transaction transaction = Transaction.builder()
                .transactionId(transId)
                .transactionType(TransactionType.C2B)
                .phoneNumber(msisdn)
                .amount(amount)
                .paybill(shortcode)
                .accountReference(billRef)
                .status(TransactionStatus.SUCCESS)
                .resultCode(0)
                .resultDescription("C2B Payment Completed")
                .reconciliationId("REC-" + transId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        transaction = transactionRepository.save(transaction);

        // Store raw callback payload
        Callback callback = callbackMapper.toEntity(transaction.getId(), "C2B_CONFIRMATION", payload, payload);
        callbackRepository.save(callback);

        // Publish event
        publishTransactionCompleted(transaction);

        return Map.of("ResultCode", "0", "ResultDescription", "Success");
    }

    /**
     * Processes B2C callback (Result or Timeout).
     */
    @Transactional
    public void processB2cCallback(Map<String, Object> payload) {
        log.info("Processing B2C Callback");
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            Map<String, Object> result = (Map<String, Object>) payload.get("Result");
            String conversationId = (String) result.get("ConversationID");
            String originatorConversationId = (String) result.get("OriginatorConversationID");
            Integer resultCode = (Integer) result.get("ResultCode");
            String resultDesc = (String) result.get("ResultDesc");

            Optional<Transaction> transactionOpt = transactionRepository.findByConversationId(conversationId);
            if (transactionOpt.isEmpty()) {
                log.warn("B2C callback conversationId [{}] does not match any transaction", conversationId);
                return;
            }

            Transaction transaction = transactionOpt.get();

            if (transaction.getStatus() != TransactionStatus.PENDING) {
                log.info("B2C transaction [{}] already processed", transaction.getId());
                return;
            }

            Map<String, Object> processedDetails = new HashMap<>();
            processedDetails.put("ResultCode", resultCode);
            processedDetails.put("ResultDesc", resultDesc);

            String mpesaReceipt = null;
            if (resultCode == 0) {
                transaction.setStatus(TransactionStatus.SUCCESS);
                
                Map<String, Object> resultParameters = (Map<String, Object>) result.get("ResultParameters");
                if (resultParameters != null) {
                    List<Map<String, Object>> parameterList = (List<Map<String, Object>>) resultParameters.get("ResultParameter");
                    if (parameterList != null) {
                        for (Map<String, Object> param : parameterList) {
                            String name = (String) param.get("Key");
                            Object value = param.get("Value");
                            processedDetails.put(name, value);
                            if ("TransactionID".equals(name)) {
                                mpesaReceipt = (String) value;
                            }
                        }
                    }
                }
                transaction.setReconciliationId("REC-" + (mpesaReceipt != null ? mpesaReceipt : UUID.randomUUID().toString().substring(0, 8).toUpperCase()));
            } else {
                transaction.setStatus(TransactionStatus.FAILED);
            }

            transaction.setResultCode(resultCode);
            transaction.setResultDescription(resultDesc);
            if (mpesaReceipt != null) {
                transaction.setTransactionId(mpesaReceipt);
            }
            transaction.setUpdatedAt(Instant.now());

            transaction = transactionRepository.save(transaction);

            Callback callback = callbackMapper.toEntity(transaction.getId(), "B2C", payload, processedDetails);
            callbackRepository.save(callback);

            publishTransactionCompleted(transaction);

        } catch (Exception e) {
            log.error("Failed to parse and process B2C Callback: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Invalid B2C callback payload structure", e);
        } finally {
            recordCallbackProcessingTime("B2C", sample);
        }
    }

    /**
     * Processes Reversal callback (Result or Timeout).
     */
    @Transactional
    public void processReversalCallback(Map<String, Object> payload) {
        log.info("Processing Reversal Callback");
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            Map<String, Object> result = (Map<String, Object>) payload.get("Result");
            String conversationId = (String) result.get("ConversationID");
            Integer resultCode = (Integer) result.get("ResultCode");
            String resultDesc = (String) result.get("ResultDesc");

            Optional<Transaction> transactionOpt = transactionRepository.findByConversationId(conversationId);
            if (transactionOpt.isEmpty()) {
                log.warn("Reversal callback conversationId [{}] does not match any transaction", conversationId);
                return;
            }

            Transaction transaction = transactionOpt.get();

            if (transaction.getStatus() != TransactionStatus.PENDING) {
                log.info("Reversal transaction [{}] already processed", transaction.getId());
                return;
            }

            Map<String, Object> processedDetails = new HashMap<>();
            processedDetails.put("ResultCode", resultCode);
            processedDetails.put("ResultDesc", resultDesc);

            String mpesaReceipt = null;
            if (resultCode == 0) {
                transaction.setStatus(TransactionStatus.SUCCESS);
                
                Map<String, Object> resultParameters = (Map<String, Object>) result.get("ResultParameters");
                if (resultParameters != null) {
                    List<Map<String, Object>> parameterList = (List<Map<String, Object>>) resultParameters.get("ResultParameter");
                    if (parameterList != null) {
                        for (Map<String, Object> param : parameterList) {
                            String name = (String) param.get("Key");
                            Object value = param.get("Value");
                            processedDetails.put(name, value);
                            if ("TransactionID".equals(name)) {
                                mpesaReceipt = (String) value;
                            }
                        }
                    }
                }
                transaction.setReconciliationId("REC-" + (mpesaReceipt != null ? mpesaReceipt : UUID.randomUUID().toString().substring(0, 8).toUpperCase()));
            } else {
                transaction.setStatus(TransactionStatus.FAILED);
            }

            transaction.setResultCode(resultCode);
            transaction.setResultDescription(resultDesc);
            if (mpesaReceipt != null) {
                transaction.setTransactionId(mpesaReceipt);
            }
            transaction.setUpdatedAt(Instant.now());

            transaction = transactionRepository.save(transaction);

            Callback callback = callbackMapper.toEntity(transaction.getId(), "REVERSAL", payload, processedDetails);
            callbackRepository.save(callback);

            publishTransactionCompleted(transaction);

        } catch (Exception e) {
            log.error("Failed to parse and process Reversal Callback: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Invalid Reversal callback payload structure", e);
        } finally {
            recordCallbackProcessingTime("REVERSAL", sample);
        }
    }

    private void recordCallbackProcessingTime(String callbackType, Timer.Sample sample) {
        sample.stop(Timer.builder("payment.callback.processing.time")
                .description("Callback processing latency")
                .tag("callback.type", callbackType)
                .register(meterRegistry));
    }

    private void publishTransactionCompleted(Transaction transaction) {
        TransactionCompletedEvent event = transactionMapper.toEvent(transaction);
        eventPublisher.publish(event);
    }
}
