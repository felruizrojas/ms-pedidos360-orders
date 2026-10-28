package com.pedidos360.orders.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.pedidos360.orders.model.EstadoPedido;
import com.pedidos360.orders.model.Pedido;
import com.pedidos360.orders.security.Usuario;

/**
 * Evento de dominio (ApplicationEvent de Spring vía publishEvent) emitido al crear un pedido
 * (estadoAnterior = null), en cada cambio de estado, al editarlo y al eliminarlo (ver {@link TipoEventoPedido}).
 * Es una foto inmutable tomada dentro de la transacción: los listeners no tocan entidades JPA. Lo consume la
 * mensajería (hoy RabbitMQ; más adelante también Kafka).
 */
public record PedidoEstadoCambiadoEvent(
        TipoEventoPedido tipo,
        Long pedidoId,
        String clienteId,
        String clienteNombre,
        String clienteEmail,
        EstadoPedido estadoAnterior,
        EstadoPedido estadoNuevo,
        BigDecimal total,
        List<Item> items,
        Instant fecha,
        String correlationId,
        String usuarioId,
        String usuarioNombre,
        String usuarioRol) {

    public record Item(Long productoId, String productoNombre, Integer cantidad,
            BigDecimal precioUnitario, BigDecimal subtotal) {
    }

    /** usuario: quien hizo la acción (Cliente u Operador); viaja en el mensaje con su rol. */
    public static PedidoEstadoCambiadoEvent de(Pedido p, TipoEventoPedido tipo, EstadoPedido anterior, Usuario usuario,
            Instant fecha, String correlationId) {
        List<Item> items = p.getItems().stream()
                .map(i -> new Item(i.getProductoId(), i.getProductoNombre(), i.getCantidad(),
                        i.getPrecioUnitario(), i.getSubtotal()))
                .toList();
        return new PedidoEstadoCambiadoEvent(tipo, p.getId(), p.getClienteId(), p.getClienteNombre(),
                p.getClienteEmail(), anterior, p.getEstado(), p.getTotal(), items, fecha, correlationId,
                usuario.id(), usuario.nombre(), usuario.rolPrincipal());
    }
}
