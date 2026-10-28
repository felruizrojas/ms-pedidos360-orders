package com.pedidos360.orders.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pedidos360.orders.client.CatalogClient;
import com.pedidos360.orders.client.ProductoCatalogo;
import com.pedidos360.orders.client.StockItem;
import com.pedidos360.orders.dto.ActualizarPedidoRequest;
import com.pedidos360.orders.dto.CambiarEstadoRequest;
import com.pedidos360.orders.dto.CrearPedidoRequest;
import com.pedidos360.orders.dto.HistorialEstadoResponse;
import com.pedidos360.orders.dto.ItemPedidoRequest;
import com.pedidos360.orders.dto.PaginaResponse;
import com.pedidos360.orders.dto.PedidoMapper;
import com.pedidos360.orders.dto.PedidoResponse;
import com.pedidos360.orders.exception.ConflictoException;
import com.pedidos360.orders.exception.PedidoNotFoundException;
import com.pedidos360.orders.exception.ProductoNoEncontradoEnCatalogoException;
import com.pedidos360.orders.exception.SolicitudInvalidaException;
import com.pedidos360.orders.messaging.PedidoEstadoCambiadoEvent;
import com.pedidos360.orders.messaging.TipoEventoPedido;
import com.pedidos360.orders.model.EstadoPedido;
import com.pedidos360.orders.model.HistorialEstado;
import com.pedidos360.orders.model.ItemPedido;
import com.pedidos360.orders.model.MaquinaEstadosPedido;
import com.pedidos360.orders.model.Pedido;
import com.pedidos360.orders.repository.HistorialEstadoRepository;
import com.pedidos360.orders.repository.PedidoRepository;
import com.pedidos360.orders.repository.PedidoSpecifications;
import com.pedidos360.orders.security.Usuario;
import com.pedidos360.orders.web.RequestIdFilter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reglas de negocio de pedidos. La autorización por rol la hace @PreAuthorize en el controller;
 * aquí se verifica el dueño: un usuario que es solo Cliente ve/modifica únicamente sus pedidos
 * (los ajenos responden 404, para no revelar que existen).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PedidoService {

    public static final int TAMANO_MAXIMO = 100;

    private final PedidoRepository pedidoRepository;
    private final HistorialEstadoRepository historialRepository;
    private final CatalogClient catalogClient;
    private final ApplicationEventPublisher eventPublisher;
    private final MaquinaEstadosPedido maquina = new MaquinaEstadosPedido();

    @Transactional
    public PedidoResponse crear(CrearPedidoRequest request, Usuario usuario) {
        List<ItemPedido> items = resolverItems(request.items());
        Instant ahora = Instant.now();

        Pedido pedido = Pedido.builder()
                .clienteId(usuario.id())
                .clienteNombre(usuario.nombre())
                .clienteEmail(usuario.email())
                .direccionEntrega(request.direccionEntrega().trim())
                .estado(EstadoPedido.CREADO)
                .creadoEn(ahora)
                .total(BigDecimal.ZERO)
                .build();
        pedido.reemplazarItems(items);
        pedido.agregarHistorial(historial(null, EstadoPedido.CREADO, usuario, ahora, null));

        // saveAndFlush: un error de BD aparece antes de publicar. Si Rabbit falla, rollback y 503.
        Pedido guardado = pedidoRepository.saveAndFlush(pedido);
        publicar(guardado, TipoEventoPedido.CREADO, null, ahora);
        return PedidoMapper.toResponse(guardado);
    }

    @Transactional(readOnly = true)
    public PedidoResponse obtener(Long id, Usuario usuario) {
        return PedidoMapper.toResponse(obtenerVisible(id, usuario));
    }

    @Transactional(readOnly = true)
    public PaginaResponse<PedidoResponse> listar(EstadoPedido estado, Instant desde, Instant hasta,
            int pagina, int tamano, Usuario usuario) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new SolicitudInvalidaException(Map.of("desde", "'desde' no puede ser posterior a 'hasta'."));
        }
        String clienteId = usuario.esSoloCliente() ? usuario.id() : null;
        Pageable pageable = PageRequest.of(Math.max(pagina, 0), Math.clamp(tamano, 1, TAMANO_MAXIMO),
                Sort.by(Sort.Direction.DESC, "creadoEn").and(Sort.by(Sort.Direction.DESC, "id")));
        return PaginaResponse.de(
                pedidoRepository.findAll(PedidoSpecifications.filtrar(clienteId, estado, desde, hasta), pageable),
                PedidoMapper::toResponse);
    }

    @Transactional
    public PedidoResponse actualizar(Long id, ActualizarPedidoRequest request, Usuario usuario) {
        if (request.direccionEntrega() == null && request.items() == null) {
            throw new SolicitudInvalidaException(Map.of("pedido", "Debe indicar direccionEntrega y/o items."));
        }
        Pedido pedido = obtenerVisible(id, usuario);
        if (pedido.getEstado() != EstadoPedido.CREADO) {
            throw new ConflictoException("Solo se puede editar un pedido en estado CREADO (estado actual: "
                    + pedido.getEstado() + ")");
        }
        if (request.direccionEntrega() != null) {
            pedido.setDireccionEntrega(request.direccionEntrega().trim());
        }
        if (request.items() != null) {
            pedido.reemplazarItems(resolverItems(request.items()));
        }
        // Mismo patrón que crear: si Rabbit falla, rollback y 503 (la edición no queda sin su aviso).
        Pedido guardado = pedidoRepository.saveAndFlush(pedido);
        publicar(guardado, TipoEventoPedido.MODIFICADO, guardado.getEstado(), Instant.now());
        return PedidoMapper.toResponse(guardado);
    }

    /**
     * Orden: 1) validar la transición, 2) llamar a catalog (descontar/reponer), 3) guardar,
     * 4) publicar en Rabbit (PedidoCommandListener, dentro de la transacción), 5) si el guardado o la
     * publicación fallan después de descontar/reponer, compensar en catalog. Un 409 nunca publica nada.
     */
    @Transactional
    public PedidoResponse cambiarEstado(Long id, CambiarEstadoRequest request, Usuario usuario) {
        Pedido pedido = obtenerVisible(id, usuario);
        EstadoPedido anterior = pedido.getEstado();
        EstadoPedido nuevo = request.status();

        if (usuario.esSoloCliente()) {
            validarCancelacionDeCliente(anterior, nuevo);
        }
        maquina.validar(anterior, nuevo);

        // TODO idempotencia: si orders cae entre el descuento en catalog y el commit, un reintento
        // descontaría dos veces. Solución: enviar una clave (p. ej. "pedido-{id}-aceptar") y que catalog
        // registre las operaciones ya aplicadas.
        List<StockItem> stock = itemsStock(pedido);
        boolean descontado = false;
        boolean repuesto = false;
        if (nuevo == EstadoPedido.ACEPTADO) {
            catalogClient.descontarStock(stock); // 409 si no alcanza: el pedido sigue en CREADO
            pedido.setStockDescontado(true);
            descontado = true;
        } else if (nuevo == EstadoPedido.CANCELADO && pedido.isStockDescontado()) {
            catalogClient.reponerStock(stock);
            pedido.setStockDescontado(false);
            repuesto = true;
        }

        Instant ahora = Instant.now();
        pedido.aplicarEstado(nuevo, ahora);
        if (nuevo == EstadoPedido.CANCELADO) {
            pedido.setMotivoCancelacion(request.comentario());
        }
        pedido.agregarHistorial(historial(anterior, nuevo, usuario, ahora, request.comentario()));

        Pedido guardado;
        try {
            // saveAndFlush: un conflicto de @Version (u otro error de BD) aparece aquí y no en el commit,
            // a tiempo para compensar el stock. Rabbit caído (503) también hace rollback y compensa.
            guardado = pedidoRepository.saveAndFlush(pedido);
            publicar(guardado, TipoEventoPedido.ESTADO_CAMBIADO, anterior, ahora);
        } catch (RuntimeException ex) {
            compensar(pedido.getId(), stock, descontado, repuesto, ex);
            throw ex;
        }
        return PedidoMapper.toResponse(guardado);
    }

    @Transactional(readOnly = true)
    public List<HistorialEstadoResponse> historial(Long id, Usuario usuario) {
        Pedido pedido = obtenerVisible(id, usuario);
        return historialRepository.findByPedidoIdOrderByFechaAscIdAsc(pedido.getId()).stream()
                .map(PedidoMapper::toResponse)
                .toList();
    }

    /**
     * Borrado lógico (la fila queda con eliminado = true); solo pedidos que no comprometen stock (CREADO o
     * CANCELADO). Publica OrderDeleted: el cliente recibe el correo y el aviso en la app.
     */
    @Transactional
    public void eliminar(Long id) {
        Pedido pedido = pedidoRepository.findByIdAndEliminadoFalse(id)
                .orElseThrow(() -> new PedidoNotFoundException(id));
        if (pedido.getEstado() != EstadoPedido.CREADO && pedido.getEstado() != EstadoPedido.CANCELADO) {
            throw new ConflictoException("Solo se pueden eliminar pedidos en estado CREADO o CANCELADO (estado actual: "
                    + pedido.getEstado() + ")");
        }
        pedido.setEliminado(true);
        Pedido guardado = pedidoRepository.saveAndFlush(pedido);
        publicar(guardado, TipoEventoPedido.ELIMINADO, guardado.getEstado(), Instant.now());
    }

    private Pedido obtenerVisible(Long id, Usuario usuario) {
        return pedidoRepository.findByIdAndEliminadoFalse(id)
                .filter(p -> usuario.esPersonal() || p.getClienteId().equals(usuario.id()))
                .orElseThrow(() -> new PedidoNotFoundException(id));
    }

    private static void validarCancelacionDeCliente(EstadoPedido actual, EstadoPedido nuevo) {
        if (nuevo != EstadoPedido.CANCELADO) {
            throw new AccessDeniedException("Un cliente solo puede cancelar sus pedidos");
        }
        if (actual != EstadoPedido.CREADO) {
            throw new ConflictoException("Un cliente solo puede cancelar su pedido mientras está en CREADO (estado actual: "
                    + actual + ")");
        }
    }

    /**
     * Precio y nombre SIEMPRE desde catalog (nunca del cliente). Un producto repetido en el request
     * se agrupa en un solo ítem. Productos inexistentes -> 400 con un detalle por posición.
     */
    private List<ItemPedido> resolverItems(List<ItemPedidoRequest> solicitados) {
        Map<Long, ProductoCatalogo> productos = new HashMap<>();
        Map<String, String> errores = new LinkedHashMap<>();
        for (int i = 0; i < solicitados.size(); i++) {
            Long productoId = solicitados.get(i).productoId();
            if (productos.containsKey(productoId)) {
                continue;
            }
            try {
                productos.put(productoId, catalogClient.buscarProducto(productoId));
            } catch (ProductoNoEncontradoEnCatalogoException e) {
                productos.put(productoId, null);
                errores.put("items[" + i + "].productoId", e.getMessage());
            }
        }
        if (!errores.isEmpty()) {
            throw new SolicitudInvalidaException(errores);
        }

        Map<Long, Integer> cantidades = new LinkedHashMap<>();
        solicitados.forEach(s -> cantidades.merge(s.productoId(), s.cantidad(), Integer::sum));

        List<ItemPedido> items = new ArrayList<>();
        cantidades.forEach((productoId, cantidad) -> {
            ProductoCatalogo producto = productos.get(productoId);
            BigDecimal precio = producto.precio().setScale(2, RoundingMode.HALF_UP);
            items.add(ItemPedido.builder()
                    .productoId(productoId)
                    .productoNombre(producto.nombre())
                    .precioUnitario(precio)
                    .cantidad(cantidad)
                    .subtotal(precio.multiply(BigDecimal.valueOf(cantidad)))
                    .build());
        });
        return items;
    }

    private static List<StockItem> itemsStock(Pedido pedido) {
        return pedido.getItems().stream()
                .map(i -> new StockItem(i.getProductoId(), i.getCantidad()))
                .toList();
    }

    private void compensar(Long pedidoId, List<StockItem> stock, boolean descontado, boolean repuesto,
            RuntimeException causa) {
        if (descontado) {
            log.error("No se pudo guardar o publicar el pedido {} después de descontar stock; se intenta reponer",
                    pedidoId, causa);
            try {
                catalogClient.reponerStock(stock);
                log.warn("Compensación OK: stock del pedido {} repuesto en catalog", pedidoId);
            } catch (RuntimeException e) {
                log.error("COMPENSACIÓN FALLIDA: el stock del pedido {} quedó descontado en catalog sin que el "
                        + "pedido quedara ACEPTADO; requiere corrección manual. Ítems: {}", pedidoId, stock, e);
            }
        } else if (repuesto) {
            log.error("No se pudo guardar o publicar la cancelación del pedido {} después de reponer stock; "
                    + "se intenta volver a descontar", pedidoId, causa);
            try {
                catalogClient.descontarStock(stock);
                log.warn("Compensación OK: stock del pedido {} descontado nuevamente en catalog", pedidoId);
            } catch (RuntimeException e) {
                log.error("COMPENSACIÓN FALLIDA: el stock del pedido {} quedó repuesto en catalog sin que el pedido "
                        + "quedara CANCELADO; requiere corrección manual. Ítems: {}", pedidoId, stock, e);
            }
        }
    }

    private static HistorialEstado historial(EstadoPedido anterior, EstadoPedido nuevo, Usuario usuario,
            Instant fecha, String comentario) {
        return HistorialEstado.builder()
                .estadoAnterior(anterior)
                .estadoNuevo(nuevo)
                .usuarioId(usuario.id())
                .usuarioNombre(usuario.nombre())
                .usuarioRol(usuario.rolPrincipal())
                .fecha(fecha)
                .comentario(comentario)
                .build();
    }

    private void publicar(Pedido pedido, TipoEventoPedido tipo, EstadoPedido anterior, Instant fecha) {
        eventPublisher.publishEvent(
                PedidoEstadoCambiadoEvent.de(pedido, tipo, anterior, fecha, MDC.get(RequestIdFilter.MDC_KEY)));
    }
}
