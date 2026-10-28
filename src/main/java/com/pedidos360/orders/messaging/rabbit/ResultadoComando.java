package com.pedidos360.orders.messaging.rabbit;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Resultado que notify publica en evt.direct (routing key resultado.comando) por cada comando de
 * q.cmd.email/kitchen/invoice: PROCESADO (ACK), FALLIDO (NACK, con el motivo) o EN_DLQ (llegó a la DLQ:
 * rechazado o expirado). DTO propio: se ignoran campos desconocidos.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResultadoComando(
        String eventId,
        String comandoEventId,
        Long pedidoId,
        String comando,
        String type,
        String resultado,
        String motivo,
        String cola,
        Instant fecha,
        String correlationId) {
}
