package com.pedidos360.orders.service;

import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pedidos360.orders.dto.NotificacionResponse;
import com.pedidos360.orders.messaging.PedidoEstadoCambiadoEvent;
import com.pedidos360.orders.model.EstadoPedido;
import com.pedidos360.orders.model.Notificacion;
import com.pedidos360.orders.repository.NotificacionRepository;
import com.pedidos360.orders.security.Usuario;

import lombok.RequiredArgsConstructor;

/**
 * Avisos en la app para el cliente. Se registran con el mismo evento que se publica en RabbitMQ (listener
 * síncrono, dentro de la transacción del pedido): si la publicación falla, el aviso también hace rollback.
 */
@Service
@RequiredArgsConstructor
public class NotificacionService {

    private final NotificacionRepository repository;

    @EventListener
    public void onPedidoEstadoCambiado(PedidoEstadoCambiadoEvent e) {
        String[] texto = switch (e.tipo()) {
            case MODIFICADO -> new String[] { "Pedido modificado",
                    "Tu pedido #" + e.pedidoId() + " fue modificado. Revisa el detalle en tu correo." };
            case ELIMINADO -> new String[] { "Pedido eliminado",
                    "Tu pedido #" + e.pedidoId() + " fue eliminado por el equipo de Pedidos360." };
            case ESTADO_CAMBIADO -> e.estadoNuevo() == EstadoPedido.CANCELADO
                    ? new String[] { "Pedido cancelado", "Tu pedido #" + e.pedidoId() + " fue cancelado." }
                    : null;
            case CREADO -> null;
        };
        if (texto == null) {
            return;
        }
        repository.save(Notificacion.builder()
                .clienteId(e.clienteId())
                .pedidoId(e.pedidoId())
                .titulo(texto[0])
                .mensaje(texto[1])
                .fecha(e.fecha())
                .build());
    }

    /** Las 20 más recientes del usuario (cada uno ve solo las suyas). */
    @Transactional(readOnly = true)
    public List<NotificacionResponse> listar(Usuario usuario) {
        return repository.findTop20ByClienteIdOrderByFechaDescIdDesc(usuario.id()).stream()
                .map(NotificacionResponse::de)
                .toList();
    }

    @Transactional
    public void marcarTodasLeidas(Usuario usuario) {
        repository.marcarTodasLeidas(usuario.id());
    }
}
