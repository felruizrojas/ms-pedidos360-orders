package com.pedidos360.orders.messaging.rabbit;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;

import com.pedidos360.orders.exception.ServicioNoDisponibleException;

/**
 * Espera el publisher confirm de un mensaje enviado con mandatory=true. Sin confirmación a tiempo, con nack
 * o devuelto por no tener cola donde enrutarlo -> ServicioNoDisponibleException (503): quien publica hace
 * rollback en vez de dar por enviado un mensaje que nunca llegó a una cola.
 */
final class ConfirmacionBroker {

    private ConfirmacionBroker() {
    }

    static void esperar(CorrelationData correlacion, Duration timeout) {
        CorrelationData.Confirm confirm;
        try {
            confirm = correlacion.getFuture().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw noDisponible("publicación interrumpida", correlacion, ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw noDisponible("RabbitMQ no confirmó el mensaje en " + timeout, correlacion, ex);
        }
        if (!confirm.ack()) {
            throw noDisponible("RabbitMQ rechazó el mensaje (nack): " + confirm.reason(), correlacion, null);
        }
        // Con mandatory=true el broker envía el basic.return antes del ack, así que aquí ya está registrado.
        ReturnedMessage devuelto = correlacion.getReturned();
        if (devuelto != null) {
            throw noDisponible("mensaje no enrutable: " + devuelto.getReplyText() + " (routingKey="
                    + devuelto.getRoutingKey() + ")", correlacion, null);
        }
    }

    private static ServicioNoDisponibleException noDisponible(String detalle, CorrelationData correlacion,
            Throwable causa) {
        return new ServicioNoDisponibleException("mensajería",
                new AmqpException(detalle + " (eventId=" + correlacion.getId() + ")", causa));
    }
}
