package com.pedidos360.orders.client;

/** Ítem del body de /stock/decrease y /stock/increase de catalog. */
public record StockItem(Long productoId, Integer cantidad) {
}
