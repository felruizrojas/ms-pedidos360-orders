package com.pedidos360.orders.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.pedidos360.orders.exception.ProductoNoEncontradoEnCatalogoException;

/** Cliente de ms-pedidos360-catalog (precios al crear/editar y stock al aceptar/cancelar). */
@Component
public class CatalogClient {

    private static final String PRODUCTS = "/api/catalog/products";

    private final RestClient restClient;

    public CatalogClient(DownstreamRestClientFactory factory,
            @Value("${pedidos360.services.catalog.base-url}") String baseUrl) {
        this.restClient = factory.create("catálogo", baseUrl);
    }

    public ProductoCatalogo buscarProducto(Long id) {
        return restClient.get().uri(PRODUCTS + "/{id}", id)
                .retrieve()
                .onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(), (req, res) -> {
                    throw new ProductoNoEncontradoEnCatalogoException(id);
                })
                .body(ProductoCatalogo.class);
    }

    /** Todo o nada (lo garantiza catalog). 409 de catalog -> ConflictoException con su mensaje y detalles. */
    public void descontarStock(List<StockItem> items) {
        restClient.post().uri(PRODUCTS + "/stock/decrease")
                .body(new StockRequest(items))
                .retrieve()
                .toBodilessEntity();
    }

    public void reponerStock(List<StockItem> items) {
        restClient.post().uri(PRODUCTS + "/stock/increase")
                .body(new StockRequest(items))
                .retrieve()
                .toBodilessEntity();
    }

    record StockRequest(List<StockItem> items) {
    }
}
