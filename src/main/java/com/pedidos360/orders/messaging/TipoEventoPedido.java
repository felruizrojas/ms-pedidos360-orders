package com.pedidos360.orders.messaging;

/** Qué le pasó al pedido; define el "type" del comando que se publica en email.send. */
public enum TipoEventoPedido {
    /** Pedido nuevo (estadoAnterior = null). */
    CREADO,
    /** Transición de estado (incluye la cancelación). */
    ESTADO_CAMBIADO,
    /** Edición de ítems y/o dirección (el estado no cambia). */
    MODIFICADO,
    /** Borrado lógico por un Operador (el estado no cambia). */
    ELIMINADO
}
