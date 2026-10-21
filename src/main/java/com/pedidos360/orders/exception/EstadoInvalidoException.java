package com.pedidos360.orders.exception;

import java.util.Arrays;

import com.pedidos360.orders.model.EstadoPedido;

/** Valor de estado desconocido en el JSON o en el query param (400). */
public class EstadoInvalidoException extends IllegalArgumentException {

    public EstadoInvalidoException(String valor) {
        super("Estado inválido: \"" + valor + "\". Valores permitidos: "
                + Arrays.toString(EstadoPedido.values()));
    }
}
