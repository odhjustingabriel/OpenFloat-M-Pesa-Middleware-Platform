package com.openfloat.mpesa.listener;

import com.openfloat.mpesa.common.event.TransactionCompletedEvent;
import com.openfloat.mpesa.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Internal listener within {@code openfloat-core} that reacts to transaction
 * completion events on the primary transaction queue.
 * <p>
 * Use cases:
 * <ul>
 *   <li>Updating dashboard metrics / cache</li>
 *   <li>Triggering internal notifications</li>
 *   <li>Logging structured events for observability</li>
 * </ul>
 * <p>
 * Note: The ERP Connector service has its own dedicated listener bound
 * to the {@code queue.erp.sync} queue via the same topic exchange.
 * </p>
 */
@Slf4j
@Component
public class TransactionEventListener {

    @RabbitListener(queues = RabbitMQConfig.TRANSACTION_QUEUE)
    public void onTransactionCompleted(TransactionCompletedEvent event) {
        log.info("CORE EVENT RECEIVED — TransactionCompletedEvent: txnId={}, type={}, status={}, amount={}, phone={}",
                event.getTransactionId(),
                event.getTransactionType(),
                event.getStatus(),
                event.getAmount(),
                event.getPhoneNumber());

        try {
            // ─── Internal processing ───────────────────────────────────────
            // This is the hook for core-side side-effects after a transaction
            // reaches a terminal state. Examples:
            //   • Increment real-time counters (Redis)
            //   • Send WebSocket push to staff portal dashboards
            //   • Trigger internal email/SMS notifications

            if ("SUCCESS".equalsIgnoreCase(event.getStatus())) {
                log.info("Successful transaction processed internally: txnId={}, receipt={}",
                        event.getTransactionId(), event.getReconciliationId());
            } else if ("FAILED".equalsIgnoreCase(event.getStatus())) {
                log.warn("Failed transaction received: txnId={}, resultCode={}, resultDesc={}",
                        event.getTransactionId(), event.getResultCode(), event.getResultDescription());
            }

        } catch (Exception e) {
            log.error("Error processing TransactionCompletedEvent internally for txnId={}: {}",
                    event.getTransactionId(), e.getMessage(), e);
            // Don't re-throw — a failure here should not nack the message and
            // cause it to loop. Log and investigate.
        }
    }
}
