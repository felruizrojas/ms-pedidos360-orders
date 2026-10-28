package com.pedidos360.orders.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Aviso en la app para el cliente dueño del pedido (cancelado, modificado o eliminado). */
@Entity
@Table(name = "notificacion", indexes = @Index(name = "idx_notificacion_cliente", columnList = "clienteId"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String clienteId;

    @Column(nullable = false)
    private Long pedidoId;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(nullable = false, length = 500)
    private String mensaje;

    @Column(nullable = false)
    private Instant fecha;

    private boolean leida;
}
