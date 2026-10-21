package com.pedidos360.orders.exception;

import lombok.Getter;

/** Catalog respondió 404 para un producto del pedido. El servicio lo convierte en un 400 con detalles. */
@Getter
public class ProductoNoEncontradoEnCatalogoException extends RuntimeException {

    private final Long productoId;

    public ProductoNoEncontradoEnCatalogoException(Long productoId) {
        super("El producto " + productoId + " no existe en el catálogo");
        this.productoId = productoId;
    }
}
