package com.pedidos360.orders.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import com.pedidos360.orders.model.EstadoPedido;
import com.pedidos360.orders.model.ItemPedido;
import com.pedidos360.orders.model.Pedido;
import com.pedidos360.orders.security.Usuario;

public final class TestData {

    public static final String CLIENTE_ID = "cliente-1";
    public static final String OTRO_CLIENTE_ID = "cliente-2";

    private TestData() {
    }

    public static Usuario cliente() {
        return new Usuario(CLIENTE_ID, "Ana Cliente", "ana@pedidos360.cl", Set.of(Usuario.CLIENTE));
    }

    public static Usuario otroCliente() {
        return new Usuario(OTRO_CLIENTE_ID, "Beto Cliente", "beto@pedidos360.cl", Set.of(Usuario.CLIENTE));
    }

    public static Usuario operador() {
        return new Usuario("operador-1", "Olga Operadora", "olga@pedidos360.cl", Set.of(Usuario.OPERADOR));
    }

    /** Pedido de CLIENTE_ID con 2 x Pan Amasado ($600) y 1 x Café ($1500). */
    public static Pedido pedido(Long id, EstadoPedido estado) {
        Pedido pedido = Pedido.builder()
                .id(id)
                .clienteId(CLIENTE_ID)
                .clienteNombre("Ana Cliente")
                .clienteEmail("ana@pedidos360.cl")
                .direccionEntrega("Av. Siempre Viva 742")
                .estado(estado)
                .creadoEn(Instant.now())
                .total(BigDecimal.ZERO)
                .stockDescontado(estado == EstadoPedido.ACEPTADO || estado == EstadoPedido.EN_PREPARACION)
                .version(0L)
                .build();
        pedido.agregarItem(item(1L, "Pan Amasado", "600.00", 2));
        pedido.agregarItem(item(4L, "Café Americano", "1500.00", 1));
        pedido.recalcularTotal();
        return pedido;
    }

    private static ItemPedido item(Long productoId, String nombre, String precio, int cantidad) {
        BigDecimal p = new BigDecimal(precio);
        return ItemPedido.builder()
                .productoId(productoId)
                .productoNombre(nombre)
                .precioUnitario(p)
                .cantidad(cantidad)
                .subtotal(p.multiply(BigDecimal.valueOf(cantidad)))
                .build();
    }
}
