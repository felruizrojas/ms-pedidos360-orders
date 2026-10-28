package com.pedidos360.orders.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pedidos360.orders.model.Notificacion;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    List<Notificacion> findTop20ByClienteIdOrderByFechaDescIdDesc(String clienteId);

    @Modifying
    @Query("update Notificacion n set n.leida = true where n.clienteId = :clienteId and n.leida = false")
    int marcarTodasLeidas(@Param("clienteId") String clienteId);
}
