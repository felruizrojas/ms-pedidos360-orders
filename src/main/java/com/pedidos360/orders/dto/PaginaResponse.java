package com.pedidos360.orders.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Forma estable del listado (no se serializa Page de Spring, cuyo JSON no es un contrato). */
public record PaginaResponse<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas) {

    public static <E, T> PaginaResponse<T> de(Page<E> page, Function<E, T> mapper) {
        return new PaginaResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
