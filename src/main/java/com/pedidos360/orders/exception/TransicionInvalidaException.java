package com.pedidos360.orders.exception;

import com.pedidos360.orders.model.EstadoPedido;

import lombok.Getter;

/** Transición no permitida por la máquina de estados (409). */
@Getter
public class TransicionInvalidaException extends RuntimeException {

    private final EstadoPedido actual;
    private final EstadoPedido solicitado;

    public TransicionInvalidaException(EstadoPedido actual, EstadoPedido solicitado, String mensaje) {
        super(mensaje);
        this.actual = actual;
        this.solicitado = solicitado;
    }
}
