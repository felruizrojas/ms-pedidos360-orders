package com.pedidos360.orders.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pedidos360.orders.model.HistorialEstado;

public interface HistorialEstadoRepository extends JpaRepository<HistorialEstado, Long> {

    List<HistorialEstado> findByPedidoIdOrderByFechaAscIdAsc(Long pedidoId);
}
