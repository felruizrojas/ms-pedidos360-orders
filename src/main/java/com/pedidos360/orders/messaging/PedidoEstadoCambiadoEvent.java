package com.pedidos360.orders.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.pedidos360.orders.model.EstadoPedido;
import com.pedidos360.orders.model.Pedido;

/**
 * Evento de dominio (ApplicationEvent de Spring vía publishEvent) emitido al crear un pedido
 * (estadoAnterior = null) y en cada cambio de estado. Es una foto inmutable tomada dentro de la
 * transacción: los listeners no tocan entidades JPA. Lo consume la mensajería
 * (hoy RabbitMQ; más adelante también Kafka).
 */
public record PedidoEstadoCambiadoEvent(
        Long pedidoId,
        String clienteId,
        String clienteNombre,
        String clienteEmail,
        EstadoPedido estadoAnterior,
        EstadoPedido estadoNuevo,
        BigDecimal total,
        List<Item> items,
        Instant fecha,
        String correlationId) {

    public record Item(Long productoId, String productoNombre, Integer cantidad,
            BigDecimal precioUnitario, BigDecimal subtotal) {
    }

    public static PedidoEstadoCambiadoEvent de(Pedido p, EstadoPedido anterior, Instant fecha, String correlationId) {
        List<Item> items = p.getItems().stream()
                .map(i -> new Item(i.getProductoId(), i.getProductoNombre(), i.getCantidad(),
                        i.getPrecioUnitario(), i.getSubtotal()))
                .toList();
        return new PedidoEstadoCambiadoEvent(p.getId(), p.getClienteId(), p.getClienteNombre(),
                p.getClienteEmail(), anterior, p.getEstado(), p.getTotal(), items, fecha, correlationId);
    }
}
