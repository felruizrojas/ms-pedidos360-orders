package com.pedidos360.orders.messaging.rabbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.pedidos360.orders.dto.NotificationMessage;
import com.pedidos360.orders.exception.ServicioNoDisponibleException;

/** Exchange, routing key y propiedades AMQP de la notificación de prueba. RabbitTemplate mockeado. */
class NotificationPublisherTest {

    private static final NotificationMessage MENSAJE = new NotificationMessage(501L, "cliente@ejemplo.cl",
            "Pedido confirmado", "Su pedido 501 ha sido confirmado");

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final NotificationPublisher publisher = new NotificationPublisher(rabbitTemplate, PedidoCommandPublisherTest.PROPS);

    @Test
    void publicaElMensajeTalCualEnCmdDirectEmailSend() {
        publisher.enviarNotificacion(MENSAJE);

        ArgumentCaptor<MessagePostProcessor> mpp = ArgumentCaptor.forClass(MessagePostProcessor.class);
        ArgumentCaptor<CorrelationData> correlation = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate).convertAndSend(eq("cmd.direct"), eq("email.send"), eq(MENSAJE), mpp.capture(),
                correlation.capture());

        MessageProperties props = mpp.getValue().postProcessMessage(new Message(new byte[0])).getMessageProperties();
        assertThat(props.getType()).isEqualTo(NotificationPublisher.TYPE_TEST_NOTIFICATION);
        assertThat(props.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(props.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(props.getMessageId()).isNotBlank().isEqualTo(correlation.getValue().getId());
    }

    @Test
    void rabbitCaidoSeTraduceAServicioNoDisponible() {
        doThrow(new AmqpConnectException(new java.net.ConnectException("Connection refused")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class),
                        any(MessagePostProcessor.class), any(CorrelationData.class));

        assertThatThrownBy(() -> publisher.enviarNotificacion(MENSAJE))
                .isInstanceOf(ServicioNoDisponibleException.class);
    }
}
