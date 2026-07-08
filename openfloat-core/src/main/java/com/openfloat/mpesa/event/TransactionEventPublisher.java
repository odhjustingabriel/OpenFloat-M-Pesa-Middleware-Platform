package com.openfloat.mpesa.event;

import com.openfloat.mpesa.common.event.TransactionCompletedEvent;
import com.openfloat.mpesa.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes transaction lifecycle events to RabbitMQ.
 * <p>
 * This is the single point through which all transaction completion events
 * flow to downstream consumers (ERP Connector, notifications, etc.).
 * Uses the topic exchange {@code exchange.transaction.completed} with
 * routing key {@code transaction.completed}.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Publishes a {@link TransactionCompletedEvent} to the transaction exchange.
     * <p>
     * Failures are caught and logged but never propagated — event publishing
     * must not break the critical transaction-processing path.
     * </p>
     *
     * @param event the completed transaction event
     */
    public void publish(TransactionCompletedEvent event) {
        if (event == null) {
            log.warn("Attempted to publish a null TransactionCompletedEvent — skipping");
            return;
        }

        try {
            log.info("Publishing TransactionCompletedEvent: txnId={}, type={}, status={}, amount={}",
                    event.getTransactionId(),
                    event.getTransactionType(),
                    event.getStatus(),
                    event.getAmount());

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.TRANSACTION_EXCHANGE,
                    RabbitMQConfig.TRANSACTION_ROUTING_KEY,
                    event
            );

            log.debug("TransactionCompletedEvent published successfully for txnId={}", event.getTransactionId());
        } catch (Exception e) {
            // Never let message-publishing failures propagate to the caller
            log.error("Failed to publish TransactionCompletedEvent for txnId={}: {}",
                    event.getTransactionId(), e.getMessage(), e);
        }
    }
}
