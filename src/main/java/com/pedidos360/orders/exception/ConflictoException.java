package com.pedidos360.orders.exception;

import java.util.Map;

import lombok.Getter;

/** 409 de negocio: stock insuficiente (propagado desde catalog), edición fuera de CREADO, etc. */
@Getter
public class ConflictoException extends RuntimeException {

    private final Map<String, Object> detalles;

    public ConflictoException(String mensaje) {
        this(mensaje, Map.of());
    }

    public ConflictoException(String mensaje, Map<String, Object> detalles) {
        super(mensaje);
        this.detalles = detalles;
    }
}
