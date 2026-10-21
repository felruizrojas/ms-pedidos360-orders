package com.pedidos360.orders.messaging.rabbit;

import java.time.Instant;
import java.util.UUID;

import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.pedidos360.orders.messaging.PedidoEstadoCambiadoEvent;
import com.pedidos360.orders.model.EstadoPedido;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Publica en cmd.direct (mensajes persistentes):
 * <ul>
 * <li>email.send: siempre (creación y cada cambio de estado)</li>
 * <li>kitchen.ticket: al pasar a ACEPTADO</li>
 * <li>invoice.gen: al pasar a ENTREGADO</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PedidoCommandPublisher {

    public static final String TYPE_ORDER_CREATED = "OrderCreated";
    public static final String TYPE_ORDER_STATUS_CHANGED = "OrderStatusChanged";
    public static final String TYPE_KITCHEN_TICKET = "KitchenTicketRequested";
    public static final String TYPE_INVOICE = "InvoiceRequested";

    private final RabbitTemplate rabbitTemplate;
    private final RabbitProperties properties;

    public void publicar(PedidoEstadoCambiadoEvent evento) {
        PedidoComandoPayload payload = PedidoComandoPayload.de(evento);
        String correlationId = evento.correlationId() != null ? evento.correlationId() : UUID.randomUUID().toString();
        RabbitProperties.Destinos rk = properties.routingKeys();

        enviar(rk.email(), evento.estadoAnterior() == null ? TYPE_ORDER_CREATED : TYPE_ORDER_STATUS_CHANGED,
                payload, correlationId);
        if (evento.estadoNuevo() == EstadoPedido.ACEPTADO) {
            enviar(rk.kitchen(), TYPE_KITCHEN_TICKET, payload, correlationId);
        }
        if (evento.estadoNuevo() == EstadoPedido.ENTREGADO) {
            enviar(rk.invoice(), TYPE_INVOICE, payload, correlationId);
        }
    }

    private void enviar(String routingKey, String type, PedidoComandoPayload payload, String correlationId) {
        // Sin librería de tracing distribuido, el traceId es el mismo X-Request-Id que viaja por todo el flujo.
        CommandEnvelope envelope = new CommandEnvelope(type, UUID.randomUUID().toString(), Instant.now(),
                correlationId, correlationId, payload);
        rabbitTemplate.convertAndSend(properties.exchanges().direct(), routingKey, envelope, message -> {
            MessageProperties props = message.getMessageProperties();
            props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            props.setType(type);
            props.setCorrelationId(correlationId);
            props.setMessageId(correlationId);
            return message;
        }, new CorrelationData(envelope.eventId()));
        log.info("Comando {} publicado: routingKey={} pedidoId={} estado={} eventId={}",
                type, routingKey, payload.pedidoId(), payload.estadoNuevo(), envelope.eventId());
    }
}
