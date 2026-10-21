package com.pedidos360.orders.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Nuevo pedido. El cliente (id, nombre, email) se toma del JWT.")
public record CrearPedidoRequest(
        @Schema(description = "Dirección de despacho", example = "Av. Providencia 1234, Santiago")
        @NotBlank(message = "La dirección de entrega es obligatoria.")
        @Size(max = 200, message = "La dirección de entrega no puede superar los 200 caracteres.")
        String direccionEntrega,

        @NotEmpty(message = "El pedido debe tener al menos un ítem.")
        @Size(max = 50, message = "El pedido no puede tener más de 50 ítems.")
        List<@NotNull(message = "Los ítems no pueden ser nulos.") @Valid ItemPedidoRequest> items) {
}
