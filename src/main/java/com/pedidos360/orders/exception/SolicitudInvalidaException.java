package com.pedidos360.orders.exception;

import java.util.Map;

import lombok.Getter;

/** 400 detectado en el servicio (no por Bean Validation), p. ej. un producto que no existe en catalog. */
@Getter
public class SolicitudInvalidaException extends RuntimeException {

    private final Map<String, String> detalles;

    public SolicitudInvalidaException(Map<String, String> detalles) {
        super("Error de validación");
        this.detalles = detalles;
    }
}
