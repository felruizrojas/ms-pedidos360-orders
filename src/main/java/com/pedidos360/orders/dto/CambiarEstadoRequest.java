package com.pedidos360.orders.dto;

import com.pedidos360.orders.model.EstadoPedido;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** "status" en inglés como en el enunciado; acepta también "EN_PREPARACIÓN" (con tilde). */
@Schema(description = "Cambio de estado")
public record CambiarEstadoRequest(
        @Schema(description = "Nuevo estado", example = "ACEPTADO")
        @NotNull(message = "El status es obligatorio.")
        EstadoPedido status,

        @Schema(description = "Comentario opcional (al cancelar se guarda como motivo)", example = "Cliente confirmó por teléfono")
        @Size(max = 500, message = "El comentario no puede superar los 500 caracteres.")
        String comentario) {
}
