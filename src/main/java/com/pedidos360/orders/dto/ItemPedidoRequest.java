package com.pedidos360.orders.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Solo producto y cantidad: el precio y el nombre se toman siempre de catalog. */
@Schema(description = "Ítem solicitado. El precio NO se envía: orders lo toma de catalog.")
public record ItemPedidoRequest(
        @Schema(description = "Id del producto en ms-pedidos360-catalog", example = "1")
        @NotNull(message = "El productoId es obligatorio.")
        @Positive(message = "El productoId debe ser mayor que 0.")
        Long productoId,

        @Schema(description = "Unidades", example = "2")
        @NotNull(message = "La cantidad es obligatoria.")
        @Positive(message = "La cantidad debe ser mayor que 0.")
        @Max(value = 1000, message = "La cantidad no puede superar 1.000 unidades por ítem.")
        Integer cantidad) {
}
