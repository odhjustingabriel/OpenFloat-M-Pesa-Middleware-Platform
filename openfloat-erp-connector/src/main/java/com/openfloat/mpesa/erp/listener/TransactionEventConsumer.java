package com.openfloat.mpesa.erp.listener;

import com.openfloat.mpesa.common.event.TransactionCompletedEvent;
import com.openfloat.mpesa.erp.config.AmqpConfig;
import com.openfloat.mpesa.erp.service.ERPDispatchService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumes transaction completion events from the RabbitMQ ERP synchronization queue
 * and dispatches them to the active ERP integration adapter.
 *
 * <p>Retry policy is governed by Spring AMQP's stateful retry interceptor
 * (application.yml). After exhausting all attempts the message is nacked without
 * requeue, causing RabbitMQ to dead-letter it to {@code queue.erp.sync.dlq}.
 *
 * <p>A separate listener ({@link #onDeadLetterMessage}) monitors the DLQ to emit
 * structured alert logs, enabling SIEM / monitoring tools to pick up DLQ spikes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final ERPDispatchService erpDispatchService;
    private final MeterRegistry meterRegistry;

    /**
     * Primary listener on the main ERP sync queue.
     * Exceptions are propagated to trigger the AMQP retry policy.
     */
    @RabbitListener(queues = AmqpConfig.QUEUE_ERP_SYNC)
    public void onTransactionCompleted(TransactionCompletedEvent event) throws Exception {
        log.info("ERP CONSUMER — Received event: txnId={}, type={}, amount={}, status={}",
                event.getTransactionId(), event.getTransactionType(),
                event.getAmount(), event.getStatus());

        try {
            erpDispatchService.dispatch(event);
            log.info("ERP CONSUMER — Successfully dispatched txnId={}", event.getTransactionId());
        } catch (Exception e) {
            log.error("ERP CONSUMER — Dispatch failed for txnId={}. Will retry. Error: {}",
                    event.getTransactionId(), e.getMessage());
            // Re-throw so Spring AMQP retry interceptor takes effect.
            // After max-attempts the listener container nacks without requeue → DLQ.
            throw e;
        }
    }

    /**
     * Dead-letter queue monitor.
     * Emits a structured ALERT log for each message that has exhausted all retries.
     * These log lines can be picked up by Logstash / SIEM for alerting.
     *
     * <p>This listener does NOT throw — messages acknowledged here are removed from
     * the DLQ. If manual replay is needed, use the RabbitMQ management UI or a
     * dedicated replay tool to shovel messages back to {@code queue.erp.sync}.
     */
    @RabbitListener(queues = AmqpConfig.QUEUE_ERP_SYNC_DLQ)
    public void onDeadLetterMessage(
            TransactionCompletedEvent event,
            @Header(value = "x-death", required = false) Object xDeath) {

        Counter.builder("erp.dlq.messages.count")
                .description("Number of ERP synchronization messages received on the DLQ")
                .register(meterRegistry)
                .increment();

        log.error(
                "ALERT: ERP_DLQ — Transaction has exhausted all retry attempts and landed in DLQ. " +
                "MANUAL INTERVENTION REQUIRED. txnId={}, type={}, amount={}, reconciliationId={}, xDeath={}",
                event.getTransactionId(), event.getTransactionType(),
                event.getAmount(), event.getReconciliationId(), xDeath);
    }
}
