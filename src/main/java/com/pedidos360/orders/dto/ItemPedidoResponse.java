package com.pedidos360.orders.dto;

import java.math.BigDecimal;

public record ItemPedidoResponse(
        Long id,
        Long productoId,
        String productoNombre,
        BigDecimal precioUnitario,
        Integer cantidad,
        BigDecimal subtotal) {
}
