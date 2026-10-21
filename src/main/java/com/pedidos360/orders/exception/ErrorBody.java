package com.pedidos360.orders.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

/** Mismo formato que ms-pedidos360-catalog: {timestamp, status, error, mensaje} (+ detalles). */
public final class ErrorBody {

    private ErrorBody() {
    }

    public static Map<String, Object> of(HttpStatus status, String mensaje) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("mensaje", mensaje);
        return body;
    }

    public static Map<String, Object> of(HttpStatus status, String mensaje, Map<String, ?> detalles) {
        Map<String, Object> body = of(status, mensaje);
        if (detalles != null && !detalles.isEmpty()) {
            body.put("detalles", detalles);
        }
        return body;
    }
}
