package com.pedidos360.orders.exception;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/** Solo para documentar en OpenAPI la forma de {@link ErrorBody} (que se arma como Map). */
@Schema(name = "ErrorBody", description = "Formato de error común a catalog y orders")
public record ErrorBodySchema(
        @Schema(example = "2026-10-20T15:04:05.123Z") String timestamp,
        @Schema(example = "409") int status,
        @Schema(example = "Conflict") String error,
        @Schema(example = "No se puede despachar un pedido que no fue aceptado (estado actual: CREADO)") String mensaje,
        @Schema(description = "Opcional: errores por campo (400) o datos del conflicto (409)") Map<String, Object> detalles) {
}
