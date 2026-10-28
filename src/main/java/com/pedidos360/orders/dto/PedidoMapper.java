package com.pedidos360.orders.dto;

import com.pedidos360.orders.model.HistorialEstado;
import com.pedidos360.orders.model.ItemPedido;
import com.pedidos360.orders.model.Pedido;

public final class PedidoMapper {

    private PedidoMapper() {
    }

    public static PedidoResponse toResponse(Pedido p) {
        return toResponse(p, false);
    }

    public static PedidoResponse toResponse(Pedido p, boolean alertaMensajeria) {
        return new PedidoResponse(p.getId(), p.getClienteId(), p.getClienteNombre(), p.getClienteEmail(),
                p.getDireccionEntrega(), p.getEstado(), p.getTotal(), p.isStockDescontado(),
                p.getItems().stream().map(PedidoMapper::toResponse).toList(),
                p.getCreadoEn(), p.getAceptadoEn(), p.getEnPreparacionEn(), p.getDespachadoEn(),
                p.getEntregadoEn(), p.getCanceladoEn(), p.getMotivoCancelacion(), p.getVersion(),
                alertaMensajeria);
    }

    public static ItemPedidoResponse toResponse(ItemPedido i) {
        return new ItemPedidoResponse(i.getId(), i.getProductoId(), i.getProductoNombre(),
                i.getPrecioUnitario(), i.getCantidad(), i.getSubtotal());
    }

    public static HistorialEstadoResponse toResponse(HistorialEstado h) {
        return new HistorialEstadoResponse(h.getEstadoAnterior(), h.getEstadoNuevo(), h.getUsuarioId(),
                h.getUsuarioNombre(), h.getUsuarioRol(), h.getFecha(), h.getComentario());
    }
}
