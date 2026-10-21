package com.pedidos360.orders.messaging.rabbit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.pedidos360.orders.messaging.PedidoEstadoCambiadoEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Publica en RabbitMQ solo DESPUÉS del commit: un pedido que no se guardó (409, error de BD) nunca
 * genera comandos. Si Rabbit falla, el pedido igual queda guardado y el error queda en el log.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PedidoCommandListener {

    private final PedidoCommandPublisher publisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPedidoEstadoCambiado(PedidoEstadoCambiadoEvent evento) {
        // TODO patrón outbox: hoy, si Rabbit no está disponible justo después del commit, el comando se
        // pierde (solo queda en el log). Con una tabla outbox escrita en la misma transacción y un
        // relay que reintente, la entrega sería al-menos-una-vez.
        // TODO Kafka: este mismo evento se publicará también en orders.events / audit.timeline
        // (report y audit) cuando se implemente esa parte.
        try {
            publisher.publicar(evento);
        } catch (RuntimeException ex) {
            log.error("No se pudo publicar en RabbitMQ el cambio del pedido {} ({} -> {}); el pedido quedó guardado",
                    evento.pedidoId(), evento.estadoAnterior(), evento.estadoNuevo(), ex);
        }
    }
}
