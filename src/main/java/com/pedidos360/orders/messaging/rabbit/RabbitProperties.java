package com.pedidos360.orders.messaging.rabbit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de la topología de comandos (pedidos360.rabbit.* en application.yaml).
 * No confundir con org.springframework.boot...RabbitProperties (spring.rabbitmq.*: conexión).
 */
@ConfigurationProperties("pedidos360.rabbit")
public record RabbitProperties(
        boolean declareTopology,
        Exchanges exchanges,
        Destinos queues,
        Destinos routingKeys,
        Destinos topicPatterns,
        Duration confirmTimeout,
        Resultados resultados) {

    public record Exchanges(String direct, String topic, String dlx) {
    }

    /** Un valor por tipo de comando: email (notificación), kitchen (ticket de cocina), invoice (boleta). */
    public record Destinos(String email, String kitchen, String invoice) {
    }

    /** Vuelta notify -> orders: resultado de cada comando (procesado, fallido o en la DLQ). */
    public record Resultados(String exchange, String queue, String routingKey) {
    }

    public static String dlq(String queue) {
        return queue + ".dlq";
    }
}
