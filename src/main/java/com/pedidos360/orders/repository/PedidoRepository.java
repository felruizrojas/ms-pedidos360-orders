package com.pedidos360.orders.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.pedidos360.orders.model.Pedido;

public interface PedidoRepository extends JpaRepository<Pedido, Long>, JpaSpecificationExecutor<Pedido> {

    /** Los pedidos con borrado lógico se comportan como inexistentes (404). */
    Optional<Pedido> findByIdAndEliminadoFalse(Long id);
}
