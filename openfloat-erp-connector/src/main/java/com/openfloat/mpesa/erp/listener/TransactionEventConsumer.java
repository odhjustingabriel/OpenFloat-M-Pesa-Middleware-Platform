package com.openfloat.mpesa.erp.listener;

import com.openfloat.mpesa.common.event.TransactionCompletedEvent;
import com.openfloat.mpesa.erp.service.ERPDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes transaction completion events from the RabbitMQ ERP synchronization queue.
 * Handled events are dispatched to the active ERP integration adapter.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final ERPDispatchService erpDispatchService;

    @RabbitListener(queues = "queue.erp.sync")
    public void onTransactionCompleted(TransactionCompletedEvent event) throws Exception {
        log.info("ERP CONNECTOR CONSUMER — Received TransactionCompletedEvent: txnId={}, status={}, amount={}",
                event.getTransactionId(), event.getStatus(), event.getAmount());

        try {
            // Process sync request to ERP system
            erpDispatchService.dispatch(event);
        } catch (Exception e) {
            log.error("ERP CONNECTOR CONSUMER — Sync processing failed for txnId={}. Error: {}", 
                    event.getTransactionId(), e.getMessage());
            // Re-throw exception so that RabbitMQ retry policy takes effect
            throw e;
        }
    }
}
