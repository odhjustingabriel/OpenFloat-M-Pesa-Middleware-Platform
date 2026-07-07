package com.openfloat.mpesa.service;

import com.openfloat.mpesa.common.util.PhoneNumberUtils;
import com.openfloat.mpesa.dto.StkPushRequestDto;
import com.openfloat.mpesa.dto.StkPushResponseDto;
import com.openfloat.mpesa.entity.Transaction;
import com.openfloat.mpesa.entity.enums.ReconciliationStatus;
import com.openfloat.mpesa.entity.enums.TransactionStatus;
import com.openfloat.mpesa.entity.enums.TransactionType;
import com.openfloat.mpesa.integration.mpesa.DarajaClient;
import com.openfloat.mpesa.integration.mpesa.DarajaConfig;
import com.openfloat.mpesa.integration.mpesa.dto.StkPushRequest;
import com.openfloat.mpesa.integration.mpesa.dto.StkPushResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StkPushService {

    private final DarajaClient darajaClient;
    private final DarajaConfig darajaConfig;
    private final TransactionService transactionService;
    private final IdempotencyService idempotencyService;

    @Transactional
    public StkPushResponseDto initiateStkPush(StkPushRequestDto requestDto) {
        // 1. Normalize Phone Number
        String normalizedPhone = PhoneNumberUtils.normalize(requestDto.getMsisdn());

        // 2. Generate and Verify Idempotency
        String idempotencyKey = idempotencyService.generateIdempotencyKey(
                normalizedPhone,
                requestDto.getAmount().toString(),
                requestDto.getPaybill(),
                requestDto.getAccountRef()
        );
        idempotencyService.checkIdempotency(idempotencyKey);

        // 3. Create initial Transaction Record in Pending state
        Transaction transaction = Transaction.builder()
                .transactionType(TransactionType.STK_PUSH)
                .phoneNumber(normalizedPhone)
                .amount(requestDto.getAmount())
                .paybill(requestDto.getPaybill())
                .accountReference(requestDto.getAccountRef())
                .description(requestDto.getDescription())
                .status(TransactionStatus.PENDING)
                .reconciliationStatus(ReconciliationStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .build();

        transaction = transactionService.save(transaction);
        idempotencyService.saveIdempotencyKey(idempotencyKey);

        // 4. Build Safaricom Daraja API STK Push Parameters
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String passwordSource = darajaConfig.getShortcode() + darajaConfig.getPasskey() + timestamp;
        String password = Base64.getEncoder().encodeToString(passwordSource.getBytes(StandardCharsets.UTF_8));
        String callbackUrl = darajaConfig.getCallbackBaseUrl() + "/api/v1/mpesa/callbacks/stk";

        StkPushRequest stkRequest = StkPushRequest.builder()
                .businessShortCode(darajaConfig.getShortcode())
                .password(password)
                .timestamp(timestamp)
                .transactionType("CustomerPayBillOnline")
                .amount(requestDto.getAmount().intValue())
                .partyA(normalizedPhone)
                .partyB(darajaConfig.getShortcode())
                .phoneNumber(normalizedPhone)
                .callbackUrl(callbackUrl)
                .accountReference(requestDto.getAccountRef())
                .transactionDesc(requestDto.getDescription())
                .build();

        try {
            // 5. Send Request to Daraja
            StkPushResponse stkResponse = darajaClient.initiateStkPush(stkRequest);

            // 6. Update Transaction details with Daraja API Request Identifiers
            transaction.setMerchantRequestId(stkResponse.getMerchantRequestId());
            transaction.setCheckoutRequestId(stkResponse.getCheckoutRequestId());
            transaction.setResultDescription(stkResponse.getResponseDescription());
            
            // Check if Daraja API rejected the request immediately
            if (!"0".equals(stkResponse.getResponseCode())) {
                transaction.setStatus(TransactionStatus.FAILED);
                transaction.setResultCode(Integer.parseInt(stkResponse.getResponseCode()));
            }

            transactionService.save(transaction);

            return StkPushResponseDto.builder()
                    .transactionId(transaction.getId())
                    .status(transaction.getStatus().name())
                    .build();

        } catch (Exception e) {
            log.error("STK Push API invocation failed for transaction: {}", transaction.getId(), e);
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setResultDescription("Failed to initiate STK Push: " + e.getMessage());
            transactionService.save(transaction);
            throw e;
        }
    }
}
