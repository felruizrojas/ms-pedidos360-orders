package com.pedidos360.orders.exception;

/** Catalog no alcanzable, timeout o 5xx, o RabbitMQ sin confirmar la publicación: orders responde 503. */
public class ServicioNoDisponibleException extends RuntimeException {

    public ServicioNoDisponibleException(String servicio, Throwable cause) {
        super("Servicio de " + servicio + " no disponible", cause);
    }
}
