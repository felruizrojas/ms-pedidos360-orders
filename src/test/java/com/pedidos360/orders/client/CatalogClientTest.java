package com.pedidos360.orders.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.pedidos360.orders.config.DownstreamProperties;
import com.pedidos360.orders.exception.ConflictoException;
import com.pedidos360.orders.exception.ProductoNoEncontradoEnCatalogoException;
import com.pedidos360.orders.exception.ServicioNoDisponibleException;
import com.pedidos360.orders.support.FakeCatalogServer;
import com.pedidos360.orders.web.RequestIdFilter;

import tools.jackson.databind.json.JsonMapper;

/** CatalogClient contra un catalog falso sobre HTTP real (como FakeCatalogServer del BFF). */
class CatalogClientTest {

    private static final FakeCatalogServer CATALOG = new FakeCatalogServer();
    private static final DownstreamProperties TIMEOUTS =
            new DownstreamProperties(Duration.ofMillis(500), Duration.ofMillis(1500));
    private static final List<StockItem> ITEMS = List.of(new StockItem(1L, 2), new StockItem(4L, 1));

    private CatalogClient client;

    @BeforeEach
    void setUp() {
        client = clientPara(CATALOG.baseUrl());
        CATALOG.respond(204, "");
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    private static CatalogClient clientPara(String baseUrl) {
        return new CatalogClient(new DownstreamRestClientFactory(TIMEOUTS, JsonMapper.builder().build()), baseUrl);
    }

    @Test
    void buscarProductoMapeaNombreYPrecio() {
        CATALOG.respond(200, "{\"id\":1,\"nombre\":\"Pan Amasado\",\"descripcion\":\"x\",\"precio\":600,\"stock\":80}");

        ProductoCatalogo producto = client.buscarProducto(1L);

        assertThat(producto.nombre()).isEqualTo("Pan Amasado");
        assertThat(producto.precio()).isEqualByComparingTo("600");
        assertThat(CATALOG.last().path()).isEqualTo("/api/catalog/products/1");
    }

    @Test
    void productoInexistenteLanzaExcepcionDeDominio() {
        CATALOG.respond(404, "{\"status\":404,\"mensaje\":\"Producto no encontrado con id: 99\"}");

        assertThatThrownBy(() -> client.buscarProducto(99L))
                .isInstanceOf(ProductoNoEncontradoEnCatalogoException.class);
    }

    @Test
    void descontarEnviaTodosLosItemsEnUnSoloPost() {
        client.descontarStock(ITEMS);

        assertThat(CATALOG.last().method()).isEqualTo("POST");
        assertThat(CATALOG.last().path()).isEqualTo("/api/catalog/products/stock/decrease");
        assertThat(CATALOG.last().body())
                .isEqualTo("{\"items\":[{\"productoId\":1,\"cantidad\":2},{\"productoId\":4,\"cantidad\":1}]}");
    }

    @Test
    void reponerUsaElEndpointDeIncrease() {
        client.reponerStock(ITEMS);

        assertThat(CATALOG.last().path()).isEqualTo("/api/catalog/products/stock/increase");
    }

    @Test
    void conflictoDeCatalogSePropagaConMensajeYDetalles() {
        CATALOG.respond(409, "{\"status\":409,\"error\":\"Conflict\","
                + "\"mensaje\":\"Stock insuficiente para \\\"Pan Amasado\\\" (id 1): solicitado 2, disponible 1\","
                + "\"detalles\":{\"productoId\":1,\"solicitado\":2,\"disponible\":1}}");

        assertThatThrownBy(() -> client.descontarStock(ITEMS))
                .isInstanceOf(ConflictoException.class)
                .hasMessage("Stock insuficiente para \"Pan Amasado\" (id 1): solicitado 2, disponible 1")
                .satisfies(e -> assertThat(((ConflictoException) e).getDetalles())
                        .containsEntry("disponible", 1)
                        .containsEntry("productoId", 1));
    }

    @Test
    void error5xxDeCatalogEs503() {
        CATALOG.respond(500, "{\"mensaje\":\"NullPointerException\"}");

        assertThatThrownBy(() -> client.descontarStock(ITEMS))
                .isInstanceOf(ServicioNoDisponibleException.class)
                .hasMessage("Servicio de catálogo no disponible");
    }

    @Test
    void catalogCaidoEs503() {
        CatalogClient caido = clientPara("http://127.0.0.1:1");

        assertThatThrownBy(() -> caido.buscarProducto(1L))
                .isInstanceOf(ServicioNoDisponibleException.class)
                .hasMessage("Servicio de catálogo no disponible");
    }

    @Test
    void timeoutDeCatalogEs503() {
        CATALOG.respond(200, "{\"id\":1,\"nombre\":\"Pan\",\"precio\":600,\"stock\":1}");
        CATALOG.delay(3000);

        assertThatThrownBy(() -> client.buscarProducto(1L)).isInstanceOf(ServicioNoDisponibleException.class);
    }

    @Test
    void propagaAuthorizationYRequestId() {
        Jwt jwt = Jwt.withTokenValue("token-del-usuario").header("alg", "none").subject("u").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        MDC.put(RequestIdFilter.MDC_KEY, "req-777");

        client.descontarStock(ITEMS);

        assertThat(CATALOG.last().authorization()).isEqualTo("Bearer token-del-usuario");
        assertThat(CATALOG.last().requestId()).isEqualTo("req-777");
    }
}
