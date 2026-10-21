package com.pedidos360.orders.client;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Lo que orders necesita de GET /api/catalog/products/{id}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductoCatalogo(Long id, String nombre, BigDecimal precio, Integer stock) {
}
