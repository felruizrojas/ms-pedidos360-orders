package com.pedidos360.orders.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Campos opcionales: lo que venga en null no se modifica (al menos uno debe venir). */
@Schema(description = "Edición de un pedido en CREADO. Si vienen ítems, reemplazan a los actuales y se recalculan los precios.")
public record ActualizarPedidoRequest(
        @Schema(example = "Los Leones 456, Providencia")
        @Size(max = 200, message = "La dirección de entrega no puede superar los 200 caracteres.")
        @Pattern(regexp = "(?s).*\\S.*", message = "La dirección de entrega no puede estar vacía.")
        String direccionEntrega,

        @Size(min = 1, max = 50, message = "El pedido debe tener entre 1 y 50 ítems.")
        List<@NotNull(message = "Los ítems no pueden ser nulos.") @Valid ItemPedidoRequest> items) {
}
