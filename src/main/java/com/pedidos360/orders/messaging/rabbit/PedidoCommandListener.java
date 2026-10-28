package com.pedidos360.orders.messaging.rabbit;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.pedidos360.orders.messaging.PedidoEstadoCambiadoEvent;

import lombok.RequiredArgsConstructor;

/**
 * Publica en RabbitMQ DENTRO de la transacción del pedido (listener síncrono, antes del commit): si Rabbit
 * está caído o no confirma, la excepción llega al servicio, el pedido hace rollback y la API responde 503.
 * Un pedido nunca queda guardado sin su comando en la cola. Si se cae un nodo del clúster, la conexión se
 * abre contra el otro de RABBIT_ADDRESSES.
 */
@Component
@RequiredArgsConstructor
public class PedidoCommandListener {

    private final PedidoCommandPublisher publisher;

    @EventListener
    public void onPedidoEstadoCambiado(PedidoEstadoCambiadoEvent evento) {
        // Caso borde: Rabbit confirma y luego el commit falla -> comando sin pedido. saveAndFlush antes de
        // publicar deja como única falla posible la del commit mismo; el patrón outbox lo cerraría del todo.
        // TODO Kafka: este mismo evento se publicará también en orders.events / audit.timeline
        // (report y audit) cuando se implemente esa parte.
        publisher.publicar(evento);
    }
}
