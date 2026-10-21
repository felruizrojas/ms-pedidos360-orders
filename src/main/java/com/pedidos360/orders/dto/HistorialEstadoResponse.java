package com.pedidos360.orders.dto;

import java.time.Instant;

import com.pedidos360.orders.model.EstadoPedido;

public record HistorialEstadoResponse(
        EstadoPedido estadoAnterior,
        EstadoPedido estadoNuevo,
        String usuarioId,
        String usuarioNombre,
        String usuarioRol,
        Instant fecha,
        String comentario) {
}
