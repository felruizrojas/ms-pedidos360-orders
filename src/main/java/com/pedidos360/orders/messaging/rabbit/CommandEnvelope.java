package com.pedidos360.orders.messaging.rabbit;

import java.time.Instant;

/**
 * Sobre común de todos los comandos. eventId permite a los consumidores ser idempotentes;
 * correlationId es el X-Request-Id de la petición HTTP que originó el comando.
 */
public record CommandEnvelope(
        String type,
        String eventId,
        Instant timestamp,
        String traceId,
        String correlationId,
        PedidoComandoPayload payload) {
}
