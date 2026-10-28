package com.pedidos360.orders.dto;

import java.time.Instant;

import com.pedidos360.orders.model.EstadoMensaje;
import com.pedidos360.orders.model.MensajeComando;

public record MensajeComandoResponse(
        String comando,
        String type,
        EstadoMensaje estado,
        String motivo,
        String usuarioNombre,
        String usuarioRol,
        String comandoEventId,
        Instant fecha) {

    public static MensajeComandoResponse de(MensajeComando m) {
        return new MensajeComandoResponse(m.getComando(), m.getType(), m.getEstado(), m.getMotivo(),
                m.getUsuarioNombre(), m.getUsuarioRol(), m.getComandoEventId(), m.getFecha());
    }
}
