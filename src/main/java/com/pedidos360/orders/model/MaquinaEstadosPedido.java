package com.pedidos360.orders.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import com.pedidos360.orders.exception.TransicionInvalidaException;

/**
 * Transiciones válidas del pedido. Java puro (sin Spring) para poder probarla de forma aislada.
 * <pre>
 * CREADO         -> ACEPTADO | CANCELADO
 * ACEPTADO       -> EN_PREPARACION | CANCELADO
 * EN_PREPARACION -> DESPACHADO | CANCELADO
 * DESPACHADO     -> ENTREGADO
 * ENTREGADO, CANCELADO: terminales
 * </pre>
 */
public final class MaquinaEstadosPedido {

    private static final Map<EstadoPedido, EnumSet<EstadoPedido>> TRANSICIONES = new EnumMap<>(EstadoPedido.class);

    static {
        TRANSICIONES.put(EstadoPedido.CREADO, EnumSet.of(EstadoPedido.ACEPTADO, EstadoPedido.CANCELADO));
        TRANSICIONES.put(EstadoPedido.ACEPTADO, EnumSet.of(EstadoPedido.EN_PREPARACION, EstadoPedido.CANCELADO));
        TRANSICIONES.put(EstadoPedido.EN_PREPARACION, EnumSet.of(EstadoPedido.DESPACHADO, EstadoPedido.CANCELADO));
        TRANSICIONES.put(EstadoPedido.DESPACHADO, EnumSet.of(EstadoPedido.ENTREGADO));
        TRANSICIONES.put(EstadoPedido.ENTREGADO, EnumSet.noneOf(EstadoPedido.class));
        TRANSICIONES.put(EstadoPedido.CANCELADO, EnumSet.noneOf(EstadoPedido.class));
    }

    public boolean esValida(EstadoPedido actual, EstadoPedido nuevo) {
        return actual != null && nuevo != null && TRANSICIONES.get(actual).contains(nuevo);
    }

    public EnumSet<EstadoPedido> siguientes(EstadoPedido actual) {
        return EnumSet.copyOf(TRANSICIONES.get(actual));
    }

    /** @throws TransicionInvalidaException con un mensaje pensado para mostrarse tal cual en el front. */
    public void validar(EstadoPedido actual, EstadoPedido nuevo) {
        if (esValida(actual, nuevo)) {
            return;
        }
        throw new TransicionInvalidaException(actual, nuevo, mensaje(actual, nuevo));
    }

    private String mensaje(EstadoPedido actual, EstadoPedido nuevo) {
        if (actual.esTerminal()) {
            return "El pedido está " + actual + " (estado final) y ya no admite cambios de estado";
        }
        if (actual == nuevo) {
            return "El pedido ya está en estado " + actual;
        }
        if (nuevo == EstadoPedido.DESPACHADO && actual == EstadoPedido.CREADO) {
            return "No se puede despachar un pedido que no fue aceptado (estado actual: CREADO)";
        }
        return "Transición inválida: " + actual + " → " + nuevo + ". Desde " + actual
                + " solo se permite: " + TRANSICIONES.get(actual);
    }
}
