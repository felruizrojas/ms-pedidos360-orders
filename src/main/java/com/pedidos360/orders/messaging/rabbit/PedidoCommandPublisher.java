package com.pedidos360.orders.messaging.rabbit;

import java.time.Instant;
import java.util.UUID;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.pedidos360.orders.exception.ServicioNoDisponibleException;
import com.pedidos360.orders.messaging.PedidoEstadoCambiadoEvent;
import com.pedidos360.orders.messaging.TipoEventoPedido;
import com.pedidos360.orders.model.EstadoPedido;
import com.pedidos360.orders.service.SeguimientoMensajes;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Publica en cmd.direct (mensajes persistentes):
 * <ul>
 * <li>email.send: siempre (creación, cada cambio de estado, edición y eliminación)</li>
 * <li>kitchen.ticket: al pasar a ACEPTADO</li>
 * <li>invoice.gen: al pasar a ENTREGADO</li>
 * </ul>
 * Cada mensaje espera el publisher confirm del broker: si Rabbit no está, no confirma o no hay cola donde
 * enrutar, lanza ServicioNoDisponibleException y el pedido hace rollback (503).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PedidoCommandPublisher {

    public static final String TYPE_ORDER_CREATED = "OrderCreated";
    public static final String TYPE_ORDER_STATUS_CHANGED = "OrderStatusChanged";
    public static final String TYPE_ORDER_UPDATED = "OrderUpdated";
    public static final String TYPE_ORDER_DELETED = "OrderDeleted";
    public static final String TYPE_KITCHEN_TICKET = "KitchenTicketRequested";
    public static final String TYPE_INVOICE = "InvoiceRequested";

    private final RabbitTemplate rabbitTemplate;
    private final RabbitProperties properties;
    private final SeguimientoMensajes seguimiento;

    public void publicar(PedidoEstadoCambiadoEvent evento) {
        PedidoComandoPayload payload = PedidoComandoPayload.de(evento);
        String correlationId = evento.correlationId() != null ? evento.correlationId() : UUID.randomUUID().toString();
        RabbitProperties.Destinos rk = properties.routingKeys();

        enviar("email", rk.email(), typeEmail(evento.tipo()), payload, correlationId);
        if (evento.tipo() != TipoEventoPedido.ESTADO_CAMBIADO) {
            return; // cocina y boleta solo reaccionan a transiciones de estado
        }
        if (evento.estadoNuevo() == EstadoPedido.ACEPTADO) {
            enviar("kitchen", rk.kitchen(), TYPE_KITCHEN_TICKET, payload, correlationId);
        }
        if (evento.estadoNuevo() == EstadoPedido.ENTREGADO) {
            enviar("invoice", rk.invoice(), TYPE_INVOICE, payload, correlationId);
        }
    }

    private static String typeEmail(TipoEventoPedido tipo) {
        return switch (tipo) {
            case CREADO -> TYPE_ORDER_CREATED;
            case ESTADO_CAMBIADO -> TYPE_ORDER_STATUS_CHANGED;
            case MODIFICADO -> TYPE_ORDER_UPDATED;
            case ELIMINADO -> TYPE_ORDER_DELETED;
        };
    }

    private void enviar(String comando, String routingKey, String type, PedidoComandoPayload payload,
            String correlationId) {
        // Sin librería de tracing distribuido, el traceId es el mismo X-Request-Id que viaja por todo el flujo.
        CommandEnvelope envelope = new CommandEnvelope(type, UUID.randomUUID().toString(), Instant.now(),
                correlationId, correlationId, payload);
        CorrelationData confirmacion = new CorrelationData(envelope.eventId());
        try {
            rabbitTemplate.convertAndSend(properties.exchanges().direct(), routingKey, envelope, message -> {
                MessageProperties props = message.getMessageProperties();
                props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                props.setType(type);
                props.setCorrelationId(correlationId);
                props.setMessageId(correlationId);
                return message;
            }, confirmacion);
        } catch (AmqpException ex) {
            throw new ServicioNoDisponibleException("mensajería", ex);
        }
        ConfirmacionBroker.esperar(confirmacion, properties.confirmTimeout());
        seguimiento.registrarEnviado(payload, comando, type, envelope.eventId());
        log.info("Comando {} publicado: routingKey={} pedidoId={} estado={} eventId={} por {} ({})",
                type, routingKey, payload.pedidoId(), payload.estadoNuevo(), envelope.eventId(),
                payload.usuarioNombre(), payload.usuarioRol());
    }
}
