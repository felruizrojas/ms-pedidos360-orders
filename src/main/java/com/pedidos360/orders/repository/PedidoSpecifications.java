package com.pedidos360.orders.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.pedidos360.orders.model.EstadoPedido;
import com.pedidos360.orders.model.Pedido;

import jakarta.persistence.criteria.Predicate;

/** Filtros opcionales del listado (null = sin filtro). */
public final class PedidoSpecifications {

    private PedidoSpecifications() {
    }

    public static Specification<Pedido> filtrar(String clienteId, EstadoPedido estado, Instant desde, Instant hasta) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            predicados.add(cb.isFalse(root.get("eliminado")));
            if (clienteId != null) {
                predicados.add(cb.equal(root.get("clienteId"), clienteId));
            }
            if (estado != null) {
                predicados.add(cb.equal(root.get("estado"), estado));
            }
            if (desde != null) {
                predicados.add(cb.greaterThanOrEqualTo(root.get("creadoEn"), desde));
            }
            if (hasta != null) {
                predicados.add(cb.lessThanOrEqualTo(root.get("creadoEn"), hasta));
            }
            return cb.and(predicados.toArray(Predicate[]::new));
        };
    }
}
