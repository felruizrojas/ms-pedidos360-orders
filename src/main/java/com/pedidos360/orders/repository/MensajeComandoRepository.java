package com.pedidos360.orders.repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pedidos360.orders.model.MensajeComando;

public interface MensajeComandoRepository extends JpaRepository<MensajeComando, Long> {

    List<MensajeComando> findByPedidoIdOrderByFechaAscIdAsc(Long pedidoId);

    /** De los pedidos indicados, los que tienen algún comando fallido o en la DLQ. */
    @Query("select distinct m.pedidoId from MensajeComando m where m.pedidoId in :ids and m.estado in "
            + "(com.pedidos360.orders.model.EstadoMensaje.FALLIDO, com.pedidos360.orders.model.EstadoMensaje.EN_DLQ)")
    Set<Long> pedidosConFallas(@Param("ids") Collection<Long> ids);
}
