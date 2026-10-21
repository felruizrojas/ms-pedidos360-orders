package com.pedidos360.orders.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import com.pedidos360.orders.model.EstadoPedido;

/** ?estado=EN_PREPARACIÓN (con tilde o en minúsculas) también funciona en el query param. */
@Component
public class EstadoPedidoConverter implements Converter<String, EstadoPedido> {

    @Override
    public EstadoPedido convert(String source) {
        return source.isBlank() ? null : EstadoPedido.desde(source);
    }
}
