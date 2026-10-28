package com.pedidos360.orders.model;

/** Estado de un comando publicado en RabbitMQ, según lo que responde notify por la cola de resultados. */
public enum EstadoMensaje {
    /** orders lo publicó y el broker lo confirmó. */
    ENVIADO,
    /** notify lo procesó (ACK): correo enviado, ticket de cocina o boleta generados. */
    PROCESADO,
    /** notify lo rechazó (NACK): dato erróneo o error al procesarlo. */
    FALLIDO,
    /** Llegó a la DLQ (rechazado o expirado sin que nadie lo consumiera). */
    EN_DLQ
}
