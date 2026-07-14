package com.openfloat.mpesa.erp.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AMQP topology configuration for the ERP Connector.
 *
 * <p>Queue layout:
 * <pre>
 *   exchange.transaction.completed
 *        │
 *        └─► queue.erp.sync  (x-dead-letter-exchange → exchange.transaction.dlx)
 *                                                              │
 *                                         exchange.transaction.dlx
 *                                                   │
 *                                                   └─► queue.erp.sync.dlq
 * </pre>
 *
 * <p>Retry strategy is handled by Spring AMQP's stateful retry interceptor
 * (configured in application.yml): 5 attempts with 1m → 5m → 25m backoff.
 * After exhausting retries the message is nacked without requeue, causing
 * RabbitMQ to route it to the dead-letter exchange.
 */
@Configuration
@SuppressWarnings("null")
public class AmqpConfig {

    // ── Exchange names ────────────────────────────────────────────────────

    public static final String EXCHANGE_TRANSACTION_COMPLETED = "exchange.transaction.completed";
    public static final String EXCHANGE_TRANSACTION_DLX       = "exchange.transaction.dlx";

    // ── Queue names ───────────────────────────────────────────────────────

    public static final String QUEUE_ERP_SYNC     = "queue.erp.sync";
    public static final String QUEUE_ERP_SYNC_DLQ = "queue.erp.sync.dlq";

    // ── Routing keys ──────────────────────────────────────────────────────

    public static final String ROUTING_KEY_ERP_SYNC     = "erp.sync";
    public static final String ROUTING_KEY_ERP_SYNC_DLQ = "erp.sync.dlq";

    // ── Exchanges ─────────────────────────────────────────────────────────

    /**
     * Main exchange that openfloat-core publishes completed transaction events to.
     * Must match the exchange declared in openfloat-core's RabbitMQ config.
     */
    @Bean
    public DirectExchange transactionCompletedExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_TRANSACTION_COMPLETED)
                .durable(true)
                .build();
    }

    /**
     * Dead-letter exchange. Failed messages (after max retry attempts) are
     * routed here by RabbitMQ's dead-lettering mechanism.
     */
    @Bean
    public DirectExchange transactionDlxExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_TRANSACTION_DLX)
                .durable(true)
                .build();
    }

    // ── Queues ────────────────────────────────────────────────────────────

    /**
     * Primary ERP sync queue. Declares the dead-letter exchange so that
     * nacked (non-requeued) messages are automatically forwarded to the DLQ.
     */
    @Bean
    public Queue erpSyncQueue() {
        return QueueBuilder.durable(QUEUE_ERP_SYNC)
                .withArgument("x-dead-letter-exchange", EXCHANGE_TRANSACTION_DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_ERP_SYNC_DLQ)
                .build();
    }

    /**
     * Dead-letter queue. Messages that have exhausted all retry attempts land here.
     * Operations teams can inspect and manually replay messages from this queue.
     */
    @Bean
    public Queue erpSyncDlqQueue() {
        return QueueBuilder.durable(QUEUE_ERP_SYNC_DLQ).build();
    }

    // ── Bindings ──────────────────────────────────────────────────────────

    /** Binds the ERP sync queue to the main exchange. */
    @Bean
    public Binding erpSyncBinding(Queue erpSyncQueue, DirectExchange transactionCompletedExchange) {
        return BindingBuilder.bind(erpSyncQueue)
                .to(transactionCompletedExchange)
                .with(ROUTING_KEY_ERP_SYNC);
    }

    /** Binds the DLQ to the DLX exchange. */
    @Bean
    public Binding erpSyncDlqBinding(Queue erpSyncDlqQueue, DirectExchange transactionDlxExchange) {
        return BindingBuilder.bind(erpSyncDlqQueue)
                .to(transactionDlxExchange)
                .with(ROUTING_KEY_ERP_SYNC_DLQ);
    }

    // ── Messaging infrastructure ──────────────────────────────────────────

    /**
     * JSON message converter. Must match the converter used in openfloat-core
     * to ensure proper serialization/deserialization of {@code TransactionCompletedEvent}.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
