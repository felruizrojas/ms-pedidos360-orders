package com.pedidos360.orders.messaging.rabbit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.pedidos360.orders.messaging.PedidoEstadoCambiadoEvent;
import com.pedidos360.orders.model.EstadoPedido;

/** Payload igual para email.send, kitchen.ticket e invoice.gen (cada consumidor usa lo que necesita). */
public record PedidoComandoPayload(
        Long pedidoId,
        String clienteId,
        String clienteNombre,
        String clienteEmail,
        EstadoPedido estadoAnterior,
        EstadoPedido estadoNuevo,
        BigDecimal total,
        List<PedidoEstadoCambiadoEvent.Item> items,
        Instant fecha) {

    public static PedidoComandoPayload de(PedidoEstadoCambiadoEvent e) {
        return new PedidoComandoPayload(e.pedidoId(), e.clienteId(), e.clienteNombre(), e.clienteEmail(),
                e.estadoAnterior(), e.estadoNuevo(), e.total(), e.items(), e.fecha());
    }
}
