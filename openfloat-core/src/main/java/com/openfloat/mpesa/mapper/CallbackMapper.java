package com.openfloat.mpesa.mapper;

import com.openfloat.mpesa.entity.Callback;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class CallbackMapper {

    public Callback toEntity(UUID transactionId, String callbackType, Map<String, Object> rawPayload, Map<String, Object> processedPayload) {
        if (transactionId == null) {
            return null;
        }

        return Callback.builder()
                .transactionId(transactionId)
                .callbackType(callbackType)
                .rawPayload(rawPayload)
                .processedPayload(processedPayload)
                .receivedAt(Instant.now())
                .build();
    }
}
