package com.pedidos360.orders.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Notificación de prueba de la guía Parte 2 (mismo JSON que NotificationMessage del profe). */
@Schema(description = "Notificación de prueba")
public record NotificationMessage(
        @Schema(example = "501")
        @NotNull(message = "El pedidoId es obligatorio.")
        Long pedidoId,

        @Schema(example = "cliente@ejemplo.cl")
        @NotBlank(message = "El email es obligatorio.")
        @Email(message = "El email no es válido.")
        String email,

        @Schema(example = "Pedido confirmado")
        @NotBlank(message = "El asunto es obligatorio.")
        String asunto,

        @Schema(example = "Su pedido 501 ha sido confirmado")
        @NotBlank(message = "El mensaje es obligatorio.")
        String mensaje) {
}
