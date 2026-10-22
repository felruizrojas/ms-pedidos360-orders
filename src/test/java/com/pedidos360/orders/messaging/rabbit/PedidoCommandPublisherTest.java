package com.pedidos360.orders.messaging.rabbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.pedidos360.orders.messaging.PedidoEstadoCambiadoEvent;
import com.pedidos360.orders.model.EstadoPedido;

/** Routing key según el estado, sobre del mensaje y propiedades AMQP. RabbitTemplate mockeado. */
class PedidoCommandPublisherTest {

    static final RabbitProperties PROPS = new RabbitProperties(true,
            new RabbitProperties.Exchanges("cmd.direct", "cmd.topic", "cmd.dead.dlx"),
            new RabbitProperties.Destinos("q.cmd.email", "q.cmd.kitchen", "q.cmd.invoice"),
            new RabbitProperties.Destinos("email.send", "kitchen.ticket", "invoice.gen"),
            new RabbitProperties.Destinos("email.#", "kitchen.#", "invoice.*"));

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final PedidoCommandPublisher publisher = new PedidoCommandPublisher(rabbitTemplate, PROPS);

    static PedidoEstadoCambiadoEvent evento(EstadoPedido anterior, EstadoPedido nuevo) {
        return new PedidoEstadoCambiadoEvent(15L, "cliente-1", "Ana", "ana@pedidos360.cl", anterior, nuevo,
                new BigDecimal("2700.00"),
                List.of(new PedidoEstadoCambiadoEvent.Item(1L, "Pan Amasado", 2, new BigDecimal("600.00"),
                        new BigDecimal("1200.00"))),
                Instant.parse("2026-10-20T15:00:00Z"), "req-123");
    }

    static Stream<Arguments> casos() {
        return Stream.of(
                Arguments.of(null, EstadoPedido.CREADO, List.of("email.send")),
                Arguments.of(EstadoPedido.CREADO, EstadoPedido.ACEPTADO, List.of("email.send", "kitchen.ticket")),
                Arguments.of(EstadoPedido.ACEPTADO, EstadoPedido.EN_PREPARACION, List.of("email.send")),
                Arguments.of(EstadoPedido.EN_PREPARACION, EstadoPedido.DESPACHADO, List.of("email.send")),
                Arguments.of(EstadoPedido.DESPACHADO, EstadoPedido.ENTREGADO, List.of("email.send", "invoice.gen")),
                Arguments.of(EstadoPedido.ACEPTADO, EstadoPedido.CANCELADO, List.of("email.send")));
    }

    @ParameterizedTest(name = "{0} -> {1} publica {2}")
    @MethodSource("casos")
    void publicaLasRoutingKeysSegunElEstado(EstadoPedido anterior, EstadoPedido nuevo, List<String> esperadas) {
        publisher.publicar(evento(anterior, nuevo));

        ArgumentCaptor<String> exchanges = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> routingKeys = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate, org.mockito.Mockito.times(esperadas.size())).convertAndSend(exchanges.capture(),
                routingKeys.capture(), any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));
        assertThat(routingKeys.getAllValues()).containsExactlyElementsOf(esperadas);
        assertThat(exchanges.getAllValues()).containsOnly("cmd.direct");
    }

    @Test
    void sobreYPropiedadesAmqp() {
        publisher.publicar(evento(EstadoPedido.CREADO, EstadoPedido.ACEPTADO));

        ArgumentCaptor<Object> sobres = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<MessagePostProcessor> mpp = ArgumentCaptor.forClass(MessagePostProcessor.class);
        ArgumentCaptor<CorrelationData> correlaciones = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate, atLeastOnce()).convertAndSend(anyString(), anyString(), sobres.capture(),
                mpp.capture(), correlaciones.capture());

        CommandEnvelope email = (CommandEnvelope) sobres.getAllValues().get(0);
        CommandEnvelope cocina = (CommandEnvelope) sobres.getAllValues().get(1);
        assertThat(email.type()).isEqualTo("OrderStatusChanged");
        assertThat(cocina.type()).isEqualTo("KitchenTicketRequested");
        assertThat(email.correlationId()).isEqualTo("req-123");
        assertThat(email.eventId()).isNotBlank().isNotEqualTo(cocina.eventId());
        assertThat(correlaciones.getAllValues().get(0).getId()).isEqualTo(email.eventId());
        assertThat(email.payload().pedidoId()).isEqualTo(15L);
        assertThat(email.payload().estadoAnterior()).isEqualTo(EstadoPedido.CREADO);
        assertThat(email.payload().estadoNuevo()).isEqualTo(EstadoPedido.ACEPTADO);
        assertThat(email.payload().items()).hasSize(1);

        Message mensaje = mpp.getAllValues().get(0).postProcessMessage(new Message(new byte[0], new MessageProperties()));
        MessageProperties props = mensaje.getMessageProperties();
        assertThat(props.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(props.getCorrelationId()).isEqualTo("req-123");
        assertThat(props.getMessageId()).isEqualTo("req-123");
        assertThat(props.getType()).isEqualTo("OrderStatusChanged");
    }

    @Test
    void alCrearElTipoEsOrderCreated() {
        publisher.publicar(evento(null, EstadoPedido.CREADO));

        ArgumentCaptor<Object> sobre = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), sobre.capture(),
                any(MessagePostProcessor.class), any(CorrelationData.class));
        assertThat(((CommandEnvelope) sobre.getValue()).type()).isEqualTo("OrderCreated");
    }

    @Test
    void elListenerNoPropagaErroresDeRabbit() {
        doThrow(new AmqpConnectException(new java.net.ConnectException("Connection refused")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class),
                        any(MessagePostProcessor.class), any(CorrelationData.class));
        PedidoCommandListener listener = new PedidoCommandListener(publisher);

        assertThatCode(() -> listener.onPedidoEstadoCambiado(evento(EstadoPedido.CREADO, EstadoPedido.ACEPTADO)))
                .doesNotThrowAnyException();
    }
}
