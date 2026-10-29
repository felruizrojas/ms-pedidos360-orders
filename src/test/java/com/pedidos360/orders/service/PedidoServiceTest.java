package com.pedidos360.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

import com.pedidos360.orders.client.CatalogClient;
import com.pedidos360.orders.client.ProductoCatalogo;
import com.pedidos360.orders.client.StockItem;
import com.pedidos360.orders.dto.ActualizarPedidoRequest;
import com.pedidos360.orders.dto.CambiarEstadoRequest;
import com.pedidos360.orders.dto.CrearPedidoRequest;
import com.pedidos360.orders.dto.ItemPedidoRequest;
import com.pedidos360.orders.dto.PedidoResponse;
import com.pedidos360.orders.exception.ConflictoException;
import com.pedidos360.orders.exception.PedidoNotFoundException;
import com.pedidos360.orders.exception.ProductoNoEncontradoEnCatalogoException;
import com.pedidos360.orders.exception.ServicioNoDisponibleException;
import com.pedidos360.orders.exception.SolicitudInvalidaException;
import com.pedidos360.orders.exception.TransicionInvalidaException;
import com.pedidos360.orders.messaging.PedidoEstadoCambiadoEvent;
import com.pedidos360.orders.messaging.TipoEventoPedido;
import com.pedidos360.orders.model.EstadoPedido;
import com.pedidos360.orders.model.Pedido;
import com.pedidos360.orders.repository.HistorialEstadoRepository;
import com.pedidos360.orders.repository.PedidoRepository;
import com.pedidos360.orders.support.TestData;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    PedidoRepository pedidoRepository;

    @Mock
    HistorialEstadoRepository historialRepository;

    @Mock
    CatalogClient catalogClient;

    @Mock
    ApplicationEventPublisher eventPublisher;

    PedidoService service;

    @BeforeEach
    void setUp() {
        service = new PedidoService(pedidoRepository, historialRepository, catalogClient, eventPublisher);
    }

    private void guardarDevuelveElMismo() {
        when(pedidoRepository.saveAndFlush(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Pedido existente(EstadoPedido estado) {
        Pedido pedido = TestData.pedido(10L, estado);
        when(pedidoRepository.findById(10L)).thenReturn(Optional.of(pedido));
        return pedido;
    }

    private PedidoEstadoCambiadoEvent eventoPublicado() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return (PedidoEstadoCambiadoEvent) captor.getValue();
    }

    // ---------- crear ----------

    @Test
    void crearTomaNombreYPrecioDeCatalogYNoDelCliente() {
        when(catalogClient.buscarProducto(1L)).thenReturn(new ProductoCatalogo(1L, "Pan Amasado", new BigDecimal("600"), 80));
        when(catalogClient.buscarProducto(4L)).thenReturn(new ProductoCatalogo(4L, "Café Americano", new BigDecimal("1500"), 60));
        guardarDevuelveElMismo();

        // el producto 1 viene dos veces: se agrupa en un solo ítem de 3 unidades
        PedidoResponse r = service.crear(new CrearPedidoRequest(" Av. Siempre Viva 742 ", List.of(
                new ItemPedidoRequest(1L, 2), new ItemPedidoRequest(4L, 1), new ItemPedidoRequest(1L, 1))),
                TestData.cliente());

        assertThat(r.estado()).isEqualTo(EstadoPedido.CREADO);
        assertThat(r.clienteId()).isEqualTo(TestData.CLIENTE_ID);
        assertThat(r.direccionEntrega()).isEqualTo("Av. Siempre Viva 742");
        assertThat(r.items()).hasSize(2);
        assertThat(r.items().get(0).productoNombre()).isEqualTo("Pan Amasado");
        assertThat(r.items().get(0).precioUnitario()).isEqualByComparingTo("600");
        assertThat(r.items().get(0).cantidad()).isEqualTo(3);
        assertThat(r.items().get(0).subtotal()).isEqualByComparingTo("1800");
        assertThat(r.total()).isEqualByComparingTo("3300");
        assertThat(r.stockDescontado()).isFalse();

        // crear no toca el stock
        verify(catalogClient, never()).descontarStock(anyList());
        PedidoEstadoCambiadoEvent evento = eventoPublicado();
        assertThat(evento.estadoAnterior()).isNull();
        assertThat(evento.estadoNuevo()).isEqualTo(EstadoPedido.CREADO);
    }

    @Test
    void crearConProductoInexistenteLanza400ConDetalleDeLaPosicion() {
        when(catalogClient.buscarProducto(1L)).thenReturn(new ProductoCatalogo(1L, "Pan", new BigDecimal("600"), 1));
        when(catalogClient.buscarProducto(99L)).thenThrow(new ProductoNoEncontradoEnCatalogoException(99L));

        assertThatThrownBy(() -> service.crear(new CrearPedidoRequest("Dir", List.of(
                new ItemPedidoRequest(1L, 1), new ItemPedidoRequest(99L, 1))), TestData.cliente()))
                .isInstanceOf(SolicitudInvalidaException.class)
                .satisfies(e -> assertThat(((SolicitudInvalidaException) e).getDetalles())
                        .containsKey("items[1].productoId"));
        verify(pedidoRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    // ---------- aceptar / stock ----------

    @Test
    void aceptarDescuentaTodoElStockEnUnaLlamadaYPublica() {
        Pedido pedido = existente(EstadoPedido.CREADO);
        guardarDevuelveElMismo();

        PedidoResponse r = service.cambiarEstado(10L, new CambiarEstadoRequest(EstadoPedido.ACEPTADO, null),
                TestData.operador());

        verify(catalogClient).descontarStock(List.of(new StockItem(1L, 2), new StockItem(4L, 1)));
        assertThat(r.estado()).isEqualTo(EstadoPedido.ACEPTADO);
        assertThat(r.aceptadoEn()).isNotNull();
        assertThat(pedido.isStockDescontado()).isTrue();
        assertThat(pedido.getHistorial()).last()
                .satisfies(h -> {
                    assertThat(h.getEstadoAnterior()).isEqualTo(EstadoPedido.CREADO);
                    assertThat(h.getEstadoNuevo()).isEqualTo(EstadoPedido.ACEPTADO);
                    assertThat(h.getUsuarioRol()).isEqualTo("Operador");
                });
        assertThat(eventoPublicado().estadoNuevo()).isEqualTo(EstadoPedido.ACEPTADO);
    }

    @Test
    void aceptarSinStockLanza409YElPedidoQuedaEnCreadoSinPublicar() {
        Pedido pedido = existente(EstadoPedido.CREADO);
        doThrow(new ConflictoException("Stock insuficiente para \"Pan Amasado\"", Map.of("disponible", 1)))
                .when(catalogClient).descontarStock(anyList());

        assertThatThrownBy(() -> service.cambiarEstado(10L, new CambiarEstadoRequest(EstadoPedido.ACEPTADO, null),
                TestData.operador()))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("Stock insuficiente");

        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CREADO);
        assertThat(pedido.isStockDescontado()).isFalse();
        assertThat(pedido.getAceptadoEn()).isNull();
        verify(pedidoRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void cancelarDesdeAceptadoReponeElStock() {
        Pedido pedido = existente(EstadoPedido.ACEPTADO);
        guardarDevuelveElMismo();

        PedidoResponse r = service.cambiarEstado(10L,
                new CambiarEstadoRequest(EstadoPedido.CANCELADO, "Cliente se arrepintió"), TestData.operador());

        verify(catalogClient).reponerStock(List.of(new StockItem(1L, 2), new StockItem(4L, 1)));
        assertThat(pedido.isStockDescontado()).isFalse();
        assertThat(r.estado()).isEqualTo(EstadoPedido.CANCELADO);
        assertThat(r.canceladoEn()).isNotNull();
        assertThat(r.motivoCancelacion()).isEqualTo("Cliente se arrepintió");
        assertThat(eventoPublicado().estadoAnterior()).isEqualTo(EstadoPedido.ACEPTADO);
    }

    @Test
    void cancelarDesdeCreadoNoTocaElStock() {
        existente(EstadoPedido.CREADO);
        guardarDevuelveElMismo();

        service.cambiarEstado(10L, new CambiarEstadoRequest(EstadoPedido.CANCELADO, null), TestData.operador());

        verifyNoInteractions(catalogClient);
    }

    @Test
    void transicionInvalidaNoLlamaACatalogNiGuardaNiPublica() {
        Pedido pedido = existente(EstadoPedido.CREADO);

        assertThatThrownBy(() -> service.cambiarEstado(10L,
                new CambiarEstadoRequest(EstadoPedido.DESPACHADO, null), TestData.operador()))
                .isInstanceOf(TransicionInvalidaException.class)
                .hasMessageContaining("no fue aceptado");

        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CREADO);
        verifyNoInteractions(catalogClient, eventPublisher);
        verify(pedidoRepository, never()).saveAndFlush(any());
    }

    @Test
    void siFallaElGuardadoDespuesDeDescontarSeReponeElStock() {
        existente(EstadoPedido.CREADO);
        when(pedidoRepository.saveAndFlush(any(Pedido.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Pedido.class, 10L));

        assertThatThrownBy(() -> service.cambiarEstado(10L,
                new CambiarEstadoRequest(EstadoPedido.ACEPTADO, null), TestData.operador()))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        List<StockItem> items = List.of(new StockItem(1L, 2), new StockItem(4L, 1));
        verify(catalogClient).descontarStock(items);
        verify(catalogClient).reponerStock(items);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void siRabbitFallaAlAceptarSeReponeElStockYSePropaga503() {
        existente(EstadoPedido.CREADO);
        guardarDevuelveElMismo();
        doThrow(new ServicioNoDisponibleException("mensajería", new RuntimeException("Connection refused")))
                .when(eventPublisher).publishEvent(any(Object.class));

        assertThatThrownBy(() -> service.cambiarEstado(10L,
                new CambiarEstadoRequest(EstadoPedido.ACEPTADO, null), TestData.operador()))
                .isInstanceOf(ServicioNoDisponibleException.class);

        List<StockItem> items = List.of(new StockItem(1L, 2), new StockItem(4L, 1));
        verify(catalogClient).descontarStock(items);
        verify(catalogClient).reponerStock(items);
    }

    @Test
    void siRabbitFallaAlCancelarSeVuelveADescontarElStock() {
        existente(EstadoPedido.ACEPTADO);
        guardarDevuelveElMismo();
        doThrow(new ServicioNoDisponibleException("mensajería", new RuntimeException("Connection refused")))
                .when(eventPublisher).publishEvent(any(Object.class));

        assertThatThrownBy(() -> service.cambiarEstado(10L,
                new CambiarEstadoRequest(EstadoPedido.CANCELADO, null), TestData.operador()))
                .isInstanceOf(ServicioNoDisponibleException.class);

        List<StockItem> items = List.of(new StockItem(1L, 2), new StockItem(4L, 1));
        verify(catalogClient).reponerStock(items);
        verify(catalogClient).descontarStock(items);
    }

    // ---------- dueño / cliente ----------

    @Test
    void clienteNoVePedidosAjenos() {
        existente(EstadoPedido.CREADO);

        assertThatThrownBy(() -> service.obtener(10L, TestData.otroCliente()))
                .isInstanceOf(PedidoNotFoundException.class);
    }

    @Test
    void clienteNoPuedeAceptarNiSiquieraSuPedido() {
        existente(EstadoPedido.CREADO);

        assertThatThrownBy(() -> service.cambiarEstado(10L,
                new CambiarEstadoRequest(EstadoPedido.ACEPTADO, null), TestData.cliente()))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(catalogClient, eventPublisher);
    }

    @Test
    void clienteCancelaSuPedidoEnCreado() {
        existente(EstadoPedido.CREADO);
        guardarDevuelveElMismo();

        PedidoResponse r = service.cambiarEstado(10L, new CambiarEstadoRequest(EstadoPedido.CANCELADO, null),
                TestData.cliente());

        assertThat(r.estado()).isEqualTo(EstadoPedido.CANCELADO);
    }

    @Test
    void clienteNoPuedeCancelarUnPedidoYaAceptado() {
        existente(EstadoPedido.ACEPTADO);

        assertThatThrownBy(() -> service.cambiarEstado(10L,
                new CambiarEstadoRequest(EstadoPedido.CANCELADO, null), TestData.cliente()))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("CREADO");
        verifyNoInteractions(catalogClient, eventPublisher);
    }

    // ---------- editar / eliminar ----------

    @Test
    void editarFueraDeCreadoLanza409() {
        existente(EstadoPedido.ACEPTADO);

        assertThatThrownBy(() -> service.actualizar(10L, new ActualizarPedidoRequest("Nueva dir", null),
                TestData.operador()))
                .isInstanceOf(ConflictoException.class);
    }

    @Test
    void editarItemsRecalculaPreciosDesdeCatalog() {
        existente(EstadoPedido.CREADO);
        guardarDevuelveElMismo();
        when(catalogClient.buscarProducto(1L)).thenReturn(new ProductoCatalogo(1L, "Pan Amasado", new BigDecimal("650"), 80));

        PedidoResponse r = service.actualizar(10L,
                new ActualizarPedidoRequest(null, List.of(new ItemPedidoRequest(1L, 4))), TestData.cliente());

        assertThat(r.items()).hasSize(1);
        assertThat(r.total()).isEqualByComparingTo("2600");
        assertThat(r.direccionEntrega()).isEqualTo("Av. Siempre Viva 742");
        PedidoEstadoCambiadoEvent evento = eventoPublicado();
        assertThat(evento.tipo()).isEqualTo(TipoEventoPedido.MODIFICADO);
        assertThat(evento.estadoNuevo()).isEqualTo(EstadoPedido.CREADO);
    }

    @Test
    void eliminarPedidoAceptadoLanza409() {
        existente(EstadoPedido.ACEPTADO);

        assertThatThrownBy(() -> service.eliminar(10L, TestData.operador())).isInstanceOf(ConflictoException.class);
    }

    @Test
    void eliminarPedidoCanceladoEsBorradoLogico() {
        Pedido pedido = existente(EstadoPedido.CANCELADO);
        guardarDevuelveElMismo();

        service.eliminar(10L, TestData.operador());

        assertThat(pedido.isEliminado()).isTrue();
        PedidoEstadoCambiadoEvent evento = eventoPublicado();
        assertThat(evento.tipo()).isEqualTo(TipoEventoPedido.ELIMINADO);
        assertThat(evento.clienteEmail()).isEqualTo(pedido.getClienteEmail());
    }

    @Test
    void sinRabbitElBorradoNoSeConfirma() {
        existente(EstadoPedido.CREADO);
        guardarDevuelveElMismo();
        doThrow(new ServicioNoDisponibleException("mensajería", new RuntimeException("caído")))
                .when(eventPublisher).publishEvent(any(Object.class));

        // La excepción hace rollback de la transacción: el pedido no queda eliminado en la BD.
        assertThatThrownBy(() -> service.eliminar(10L, TestData.operador())).isInstanceOf(ServicioNoDisponibleException.class);
    }
}
