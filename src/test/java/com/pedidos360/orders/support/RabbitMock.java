package com.pedidos360.orders.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** Un RabbitTemplate mockeado no completa los publisher confirms: estos helpers simulan la respuesta del broker. */
public final class RabbitMock {

    private RabbitMock() {
    }

    /** El broker confirma (ack) cada mensaje. */
    public static void confirmaTodo(RabbitTemplate rabbitTemplate) {
        responder(rabbitTemplate, new CorrelationData.Confirm(true, null));
    }

    /** El broker rechaza (nack) cada mensaje. */
    public static void rechazaTodo(RabbitTemplate rabbitTemplate) {
        responder(rabbitTemplate, new CorrelationData.Confirm(false, "simulado"));
    }

    private static void responder(RabbitTemplate rabbitTemplate, CorrelationData.Confirm confirm) {
        doAnswer(inv -> {
            inv.<CorrelationData>getArgument(4).getFuture().complete(confirm);
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class),
                any(MessagePostProcessor.class), any(CorrelationData.class));
    }
}
