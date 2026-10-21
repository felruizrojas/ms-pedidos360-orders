package com.pedidos360.orders.model;

import java.text.Normalizer;
import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;

import com.pedidos360.orders.exception.EstadoInvalidoException;

/**
 * Estados del pedido. En el JSON se aceptan también "EN_PREPARACIÓN" (con tilde, como lo escribe
 * el enunciado) y minúsculas; en la respuesta siempre sale el nombre canónico (EN_PREPARACION).
 */
public enum EstadoPedido {
    CREADO,
    ACEPTADO,
    EN_PREPARACION,
    DESPACHADO,
    ENTREGADO,
    CANCELADO;

    @JsonCreator
    public static EstadoPedido desde(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new EstadoInvalidoException(valor);
        }
        // quita tildes: "EN_PREPARACIÓN" -> "EN_PREPARACION"
        String normalizado = Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase()
                .replace(' ', '_');
        return Arrays.stream(values())
                .filter(e -> e.name().equals(normalizado))
                .findFirst()
                .orElseThrow(() -> new EstadoInvalidoException(valor));
    }

    public boolean esTerminal() {
        return this == ENTREGADO || this == CANCELADO;
    }
}
