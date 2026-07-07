package com.openfloat.mpesa.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for event-driven architecture.
 * <p>
 * Defines exchanges, queues, and bindings for:
 * <ul>
 *   <li>Transaction completed events → ERP Connector</li>
 *   <li>Dead letter queue for failed messages</li>
 * </ul>
 * </p>
 */
@Configuration
public class RabbitMQConfig {

    // ── Exchange Names ────────────────────────────────────────────────────
    public static final String TRANSACTION_EXCHANGE = "exchange.transaction.completed";
    public static final String DLX_EXCHANGE = "exchange.dlx";

    // ── Queue Names ───────────────────────────────────────────────────────
    public static final String TRANSACTION_QUEUE = "queue.transaction.completed";
    public static final String ERP_SYNC_QUEUE = "queue.erp.sync";
    public static final String DLQ_QUEUE = "queue.dlq";

    // ── Routing Keys ──────────────────────────────────────────────────────
    public static final String TRANSACTION_ROUTING_KEY = "transaction.completed";
    public static final String ERP_ROUTING_KEY = "erp.sync";
    public static final String DLQ_ROUTING_KEY = "dlq.#";

    // ── Exchanges ─────────────────────────────────────────────────────────

    @Bean
    public TopicExchange transactionExchange() {
        return ExchangeBuilder
                .topicExchange(TRANSACTION_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return ExchangeBuilder
                .topicExchange(DLX_EXCHANGE)
                .durable(true)
                .build();
    }

    // ── Queues ────────────────────────────────────────────────────────────

    @Bean
    public Queue transactionQueue() {
        return QueueBuilder
                .durable(TRANSACTION_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "dlq.transaction")
                .build();
    }

    @Bean
    public Queue erpSyncQueue() {
        return QueueBuilder
                .durable(ERP_SYNC_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "dlq.erp")
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder
                .durable(DLQ_QUEUE)
                .build();
    }

    // ── Bindings ──────────────────────────────────────────────────────────

    @Bean
    public Binding transactionBinding(Queue transactionQueue, TopicExchange transactionExchange) {
        return BindingBuilder
                .bind(transactionQueue)
                .to(transactionExchange)
                .with(TRANSACTION_ROUTING_KEY);
    }

    @Bean
    public Binding erpSyncBinding(Queue erpSyncQueue, TopicExchange transactionExchange) {
        return BindingBuilder
                .bind(erpSyncQueue)
                .to(transactionExchange)
                .with(TRANSACTION_ROUTING_KEY);
    }

    @Bean
    public Binding dlqBinding(Queue deadLetterQueue, TopicExchange deadLetterExchange) {
        return BindingBuilder
                .bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(DLQ_ROUTING_KEY);
    }

    // ── Message Converter ─────────────────────────────────────────────────

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
