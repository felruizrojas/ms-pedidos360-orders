package com.pedidos360.orders.messaging.rabbit;

import java.util.UUID;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.pedidos360.orders.dto.NotificationMessage;
import com.pedidos360.orders.exception.ServicioNoDisponibleException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Notificación de prueba de la guía Parte 2: publica el NotificationMessage tal cual (sin el sobre de
 * los comandos) en cmd.direct con routing key email.send. La propiedad AMQP type=TestNotification le
 * indica a notify que no es un comando y que solo debe registrarlo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

    public static final String TYPE_TEST_NOTIFICATION = "TestNotification";

    private final RabbitTemplate rabbitTemplate;
    private final RabbitProperties properties;

    public void enviarNotificacion(NotificationMessage mensaje) {
        String messageId = UUID.randomUUID().toString();
        CorrelationData confirmacion = new CorrelationData(messageId);
        try {
            rabbitTemplate.convertAndSend(properties.exchanges().direct(), properties.routingKeys().email(), mensaje,
                    message -> {
                        MessageProperties props = message.getMessageProperties();
                        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                        props.setType(TYPE_TEST_NOTIFICATION);
                        props.setMessageId(messageId);
                        return message;
                    }, confirmacion);
        } catch (AmqpException ex) {
            throw new ServicioNoDisponibleException("mensajería", ex);
        }
        ConfirmacionBroker.esperar(confirmacion, properties.confirmTimeout());
        log.info("Publicación confirmada por RabbitMQ para el pedido {} (messageId={})", mensaje.pedidoId(), messageId);
    }
}
