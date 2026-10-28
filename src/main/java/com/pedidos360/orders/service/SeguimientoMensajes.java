package com.pedidos360.orders.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pedidos360.orders.dto.MensajeComandoResponse;
import com.pedidos360.orders.messaging.rabbit.PedidoComandoPayload;
import com.pedidos360.orders.messaging.rabbit.ResultadoComando;
import com.pedidos360.orders.model.EstadoMensaje;
import com.pedidos360.orders.model.MensajeComando;
import com.pedidos360.orders.repository.MensajeComandoRepository;

import lombok.RequiredArgsConstructor;

/** Registra el ida y vuelta de cada comando por pedido; lo ve el operador en el dashboard. */
@Service
@RequiredArgsConstructor
public class SeguimientoMensajes {

    private static final int MAX_MOTIVO = 500;

    private final MensajeComandoRepository repository;

    /** Dentro de la transacción del pedido: si el pedido hace rollback, el registro también. */
    public void registrarEnviado(PedidoComandoPayload payload, String comando, String type, String eventId) {
        repository.save(MensajeComando.builder()
                .pedidoId(payload.pedidoId())
                .comandoEventId(eventId)
                .comando(comando)
                .type(type)
                .estado(EstadoMensaje.ENVIADO)
                .usuarioNombre(payload.usuarioNombre())
                .usuarioRol(payload.usuarioRol())
                .fecha(Instant.now())
                .build());
    }

    @Transactional
    public void registrarResultado(ResultadoComando r) {
        repository.save(MensajeComando.builder()
                .pedidoId(r.pedidoId())
                .comandoEventId(r.comandoEventId())
                .comando(r.comando() == null ? "desconocido" : r.comando())
                .type(r.type())
                .estado(EstadoMensaje.valueOf(r.resultado()))
                .motivo(recortar(r.motivo()))
                .fecha(r.fecha() == null ? Instant.now() : r.fecha())
                .build());
    }

    @Transactional(readOnly = true)
    public List<MensajeComandoResponse> listar(Long pedidoId) {
        return repository.findByPedidoIdOrderByFechaAscIdAsc(pedidoId).stream()
                .map(MensajeComandoResponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public Set<Long> pedidosConFallas(Collection<Long> ids) {
        return ids.isEmpty() ? Set.of() : repository.pedidosConFallas(ids);
    }

    private static String recortar(String texto) {
        return texto == null || texto.length() <= MAX_MOTIVO ? texto : texto.substring(0, MAX_MOTIVO - 3) + "...";
    }
}
