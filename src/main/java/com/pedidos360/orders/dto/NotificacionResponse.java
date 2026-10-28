package com.pedidos360.orders.dto;

import java.time.Instant;

import com.pedidos360.orders.model.Notificacion;

public record NotificacionResponse(
        Long id,
        Long pedidoId,
        String titulo,
        String mensaje,
        Instant fecha,
        boolean leida) {

    public static NotificacionResponse de(Notificacion n) {
        return new NotificacionResponse(n.getId(), n.getPedidoId(), n.getTitulo(), n.getMensaje(), n.getFecha(),
                n.isLeida());
    }
}
