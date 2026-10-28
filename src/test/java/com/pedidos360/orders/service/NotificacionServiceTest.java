package com.pedidos360.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import com.pedidos360.orders.messaging.PedidoEstadoCambiadoEvent;
import com.pedidos360.orders.messaging.TipoEventoPedido;
import com.pedidos360.orders.model.EstadoPedido;
import com.pedidos360.orders.model.Notificacion;
import com.pedidos360.orders.repository.NotificacionRepository;

/** Qué eventos dejan un aviso en la app del cliente. */
class NotificacionServiceTest {

    private final NotificacionRepository repository = mock(NotificacionRepository.class);
    private final NotificacionService service = new NotificacionService(repository);

    private static PedidoEstadoCambiadoEvent evento(TipoEventoPedido tipo, EstadoPedido nuevo) {
        return new PedidoEstadoCambiadoEvent(tipo, 15L, "cliente-1", "Ana", "ana@pedidos360.cl", EstadoPedido.CREADO,
                nuevo, new BigDecimal("1200.00"), List.of(), Instant.parse("2026-10-20T15:00:00Z"), "req-1",
                "cliente-1", "Ana", "Cliente");
    }

    @ParameterizedTest(name = "{0} -> {1} avisa \"{2}\"")
    @CsvSource({
            "ESTADO_CAMBIADO, CANCELADO, Pedido cancelado",
            "MODIFICADO, CREADO, Pedido modificado",
            "ELIMINADO, CREADO, Pedido eliminado" })
    void cancelarEditarYEliminarAvisanAlCliente(TipoEventoPedido tipo, EstadoPedido nuevo, String titulo) {
        service.onPedidoEstadoCambiado(evento(tipo, nuevo));

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(repository).save(captor.capture());
        Notificacion n = captor.getValue();
        assertThat(n.getClienteId()).isEqualTo("cliente-1");
        assertThat(n.getPedidoId()).isEqualTo(15L);
        assertThat(n.getTitulo()).isEqualTo(titulo);
        assertThat(n.getMensaje()).contains("#15");
        assertThat(n.isLeida()).isFalse();
    }

    @ParameterizedTest(name = "{0} -> {1} no avisa en la app")
    @CsvSource({ "CREADO, CREADO", "ESTADO_CAMBIADO, ACEPTADO", "ESTADO_CAMBIADO, ENTREGADO" })
    void elRestoSoloVaPorCorreo(TipoEventoPedido tipo, EstadoPedido nuevo) {
        service.onPedidoEstadoCambiado(evento(tipo, nuevo));

        verifyNoInteractions(repository);
    }
}
