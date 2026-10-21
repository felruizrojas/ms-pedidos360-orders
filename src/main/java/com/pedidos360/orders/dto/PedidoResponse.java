package com.pedidos360.orders.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.pedidos360.orders.model.EstadoPedido;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Pedido (timestamps ISO-8601 UTC; null si el pedido no pasó por ese estado)")
public record PedidoResponse(
        @Schema(example = "15") Long id,
        @Schema(example = "4f6c2a1e-1111-2222-3333-444455556666") String clienteId,
        @Schema(example = "María Pérez") String clienteNombre,
        @Schema(example = "maria@contoso.cl") String clienteEmail,
        @Schema(example = "Av. Providencia 1234, Santiago") String direccionEntrega,
        @Schema(example = "CREADO") EstadoPedido estado,
        @Schema(example = "3100.00") BigDecimal total,
        boolean stockDescontado,
        List<ItemPedidoResponse> items,
        Instant creadoEn,
        Instant aceptadoEn,
        Instant enPreparacionEn,
        Instant despachadoEn,
        Instant entregadoEn,
        Instant canceladoEn,
        String motivoCancelacion,
        Long version) {
}
