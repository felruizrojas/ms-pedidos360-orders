package com.pedidos360.orders.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Seguimiento de la mensajería de un pedido: una fila por cada paso de cada comando (enviado por orders y
 * luego procesado, fallido o en la DLQ según responde notify). Solo se agregan filas, nunca se actualizan:
 * así el resultado puede llegar antes de que se confirme la transacción que publicó el comando.
 */
@Entity
@Table(name = "mensaje_comando", indexes = @Index(name = "idx_mensaje_comando_pedido", columnList = "pedidoId"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MensajeComando {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long pedidoId;

    /** eventId del comando publicado (el mismo en el envío y en sus resultados). */
    @Column(length = 64)
    private String comandoEventId;

    /** email, kitchen o invoice. */
    @Column(nullable = false, length = 20)
    private String comando;

    /** type del sobre: OrderCreated, KitchenTicketRequested, ... */
    @Column(length = 40)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoMensaje estado;

    @Column(length = 500)
    private String motivo;

    /** Quién hizo la acción que generó el comando, y su rol. */
    @Column(length = 150)
    private String usuarioNombre;

    @Column(length = 20)
    private String usuarioRol;

    @Column(nullable = false)
    private Instant fecha;
}
