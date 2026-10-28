package com.pedidos360.orders.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.pedidos360.orders.client.CatalogClient;
import com.pedidos360.orders.client.ProductoCatalogo;
import com.pedidos360.orders.client.StockItem;
import com.pedidos360.orders.exception.ConflictoException;
import com.pedidos360.orders.support.RabbitMock;

/**
 * Flujo real (controller + seguridad + servicio + JPA sobre H2 + listener de Rabbit en la transacción) con
 * catalog y RabbitTemplate mockeados (confirma cada mensaje): no requiere PostgreSQL, RabbitMQ ni Azure.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PedidoFlujoIntegrationTest {

    private static final String URL = "/api/orders";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    CatalogClient catalogClient;

    @MockitoBean
    RabbitTemplate rabbitTemplate;

    @Autowired
    com.pedidos360.orders.messaging.rabbit.ResultadoComandoListener resultadoListener;

    @BeforeEach
    void catalogConProductos() {
        RabbitMock.confirmaTodo(rabbitTemplate);
        when(catalogClient.buscarProducto(1L)).thenReturn(new ProductoCatalogo(1L, "Pan Amasado", new BigDecimal("600"), 80));
        when(catalogClient.buscarProducto(4L)).thenReturn(new ProductoCatalogo(4L, "Café Americano", new BigDecimal("1500"), 60));
    }

    static RequestPostProcessor usuario(String oid, String rol) {
        return jwt().jwt(j -> j.claim("oid", oid).claim("name", "Usuario " + oid)
                        .claim("preferred_username", oid + "@pedidos360.cl"))
                .authorities(new SimpleGrantedAuthority("SCOPE_access_as_user"), new SimpleGrantedAuthority("ROLE_" + rol));
    }

    static RequestPostProcessor operador() {
        return usuario("operador-1", "Operador");
    }

    private long crearComo(String clienteOid) throws Exception {
        String body = mvc.perform(post(URL).with(usuario(clienteOid, "Cliente")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direccionEntrega\":\"Av. Siempre Viva 742\",\"items\":["
                                + "{\"productoId\":1,\"cantidad\":2,\"precio\":1},{\"productoId\":4,\"cantidad\":1}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private org.springframework.test.web.servlet.ResultActions cambiar(long id, String estado) throws Exception {
        return mvc.perform(put(URL + "/" + id + "/status").with(operador()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + estado + "\"}"));
    }

    private void verificarPublicados(String routingKey, int veces) {
        verify(rabbitTemplate, times(veces)).convertAndSend(eq("cmd.direct"), eq(routingKey), any(Object.class),
                any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    @Test
    void flujoCompletoCreadoHastaEntregado() throws Exception {
        long id = crearComo("cliente-flujo");

        mvc.perform(get(URL + "/" + id).with(usuario("cliente-flujo", "Cliente")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CREADO"))
                .andExpect(jsonPath("$.total").value(2700.00)) // 2 x 600 + 1500: el precio enviado (1) se ignora
                .andExpect(jsonPath("$.clienteNombre").value("Usuario cliente-flujo"));

        cambiar(id, "ACEPTADO").andExpect(status().isOk())
                .andExpect(jsonPath("$.stockDescontado").value(true))
                .andExpect(jsonPath("$.aceptadoEn").isNotEmpty());
        verify(catalogClient).descontarStock(List.of(new StockItem(1L, 2), new StockItem(4L, 1)));

        cambiar(id, "EN_PREPARACIÓN").andExpect(status().isOk());
        cambiar(id, "DESPACHADO").andExpect(status().isOk());
        cambiar(id, "ENTREGADO").andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENTREGADO"))
                .andExpect(jsonPath("$.entregadoEn").isNotEmpty());

        mvc.perform(get(URL + "/" + id + "/history").with(usuario("cliente-flujo", "Cliente")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].estadoAnterior").doesNotExist())
                .andExpect(jsonPath("$[0].estadoNuevo").value("CREADO"))
                .andExpect(jsonPath("$[1].usuarioRol").value("Operador"))
                .andExpect(jsonPath("$[4].estadoNuevo").value("ENTREGADO"));

        // creación + 4 cambios -> 5 emails; 1 ticket de cocina (ACEPTADO); 1 boleta (ENTREGADO)
        verificarPublicados("email.send", 5);
        verificarPublicados("kitchen.ticket", 1);
        verificarPublicados("invoice.gen", 1);

        cambiar(id, "CANCELADO").andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("estado final")));
    }

    @Test
    void despacharSinAceptarDa409YNoPublicaElCambio() throws Exception {
        long id = crearComo("cliente-409");

        cambiar(id, "DESPACHADO")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje")
                        .value("No se puede despachar un pedido que no fue aceptado (estado actual: CREADO)"));

        verificarPublicados("email.send", 1); // solo el de creación
        mvc.perform(get(URL + "/" + id).with(operador())).andExpect(jsonPath("$.estado").value("CREADO"));
    }

    @Test
    void aceptarSinStockDa409YElPedidoSigueEnCreado() throws Exception {
        long id = crearComo("cliente-stock");
        doThrow(new ConflictoException("Stock insuficiente para \"Pan Amasado\" (id 1): solicitado 2, disponible 1",
                Map.of("productoId", 1, "solicitado", 2, "disponible", 1)))
                .when(catalogClient).descontarStock(anyList());

        cambiar(id, "ACEPTADO")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.startsWith("Stock insuficiente")))
                .andExpect(jsonPath("$.detalles.disponible").value(1));

        mvc.perform(get(URL + "/" + id).with(operador()))
                .andExpect(jsonPath("$.estado").value("CREADO"))
                .andExpect(jsonPath("$.stockDescontado").value(false));
        verificarPublicados("kitchen.ticket", 0);
    }

    @Test
    void rabbitCaidoAlCrearDa503YNoGuardaElPedido() throws Exception {
        doThrow(new AmqpConnectException(new java.net.ConnectException("Connection refused")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class),
                        any(MessagePostProcessor.class), any(CorrelationData.class));

        mvc.perform(post(URL).with(usuario("cliente-sin-rabbit", "Cliente")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direccionEntrega\":\"Dir\",\"items\":[{\"productoId\":1,\"cantidad\":1}]}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value("Servicio de mensajería no disponible"));

        mvc.perform(get(URL).with(usuario("cliente-sin-rabbit", "Cliente")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(0));
    }

    @Test
    void rabbitCaidoAlAceptarDa503ReponeStockYSigueEnCreado() throws Exception {
        long id = crearComo("cliente-acepta-sin-rabbit");
        RabbitMock.rechazaTodo(rabbitTemplate);

        cambiar(id, "ACEPTADO").andExpect(status().isServiceUnavailable());

        List<StockItem> items = List.of(new StockItem(1L, 2), new StockItem(4L, 1));
        verify(catalogClient).descontarStock(items);
        verify(catalogClient).reponerStock(items);
        mvc.perform(get(URL + "/" + id).with(operador()))
                .andExpect(jsonPath("$.estado").value("CREADO"))
                .andExpect(jsonPath("$.stockDescontado").value(false));
    }

    @Test
    void cancelarDesdeAceptadoReponeStock() throws Exception {
        long id = crearComo("cliente-cancela");
        cambiar(id, "ACEPTADO").andExpect(status().isOk());

        mvc.perform(put(URL + "/" + id + "/status").with(operador()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELADO\",\"comentario\":\"Sin repartidor\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockDescontado").value(false))
                .andExpect(jsonPath("$.motivoCancelacion").value("Sin repartidor"));

        verify(catalogClient).reponerStock(List.of(new StockItem(1L, 2), new StockItem(4L, 1)));
    }

    @Test
    void clienteNoVeNiListaPedidosAjenos() throws Exception {
        long ajeno = crearComo("cliente-a");
        long propio = crearComo("cliente-b");

        mvc.perform(get(URL + "/" + ajeno).with(usuario("cliente-b", "Cliente"))).andExpect(status().isNotFound());
        mvc.perform(get(URL + "/" + ajeno + "/history").with(usuario("cliente-b", "Cliente")))
                .andExpect(status().isNotFound());
        mvc.perform(put(URL + "/" + ajeno + "/status").with(usuario("cliente-b", "Cliente"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELADO\"}"))
                .andExpect(status().isNotFound());

        String lista = mvc.perform(get(URL).with(usuario("cliente-b", "Cliente")).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(0))
                .andReturn().getResponse().getContentAsString();
        List<String> clientes = JsonPath.read(lista, "$.contenido[*].clienteId");
        List<Number> ids = JsonPath.read(lista, "$.contenido[*].id");
        assertThat(clientes).containsOnly("cliente-b");
        assertThat(ids).extracting(Number::longValue).contains(propio).doesNotContain(ajeno);

        // el operador ve ambos
        String todos = mvc.perform(get(URL).with(operador()).param("size", "100"))
                .andReturn().getResponse().getContentAsString();
        List<Number> todosIds = JsonPath.read(todos, "$.contenido[*].id");
        assertThat(todosIds).extracting(Number::longValue).contains(propio, ajeno);
    }

    @Test
    void clienteCancelaSuPedidoEnCreadoPeroNoPuedeAceptarlo() throws Exception {
        long id = crearComo("cliente-c");

        mvc.perform(put(URL + "/" + id + "/status").with(usuario("cliente-c", "Cliente"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACEPTADO\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put(URL + "/" + id + "/status").with(usuario("cliente-c", "Cliente"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELADO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"));
        verify(catalogClient, never()).reponerStock(anyList());
    }

    @Test
    void editarSoloEnCreadoYRecalculaPrecios() throws Exception {
        long id = crearComo("cliente-edita");

        mvc.perform(put(URL + "/" + id).with(usuario("cliente-edita", "Cliente")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productoId\":4,\"cantidad\":3}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.total").value(4500.00))
                .andExpect(jsonPath("$.direccionEntrega").value("Av. Siempre Viva 742"));

        cambiar(id, "ACEPTADO").andExpect(status().isOk());
        mvc.perform(put(URL + "/" + id).with(operador()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direccionEntrega\":\"Otra 123\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void productoInexistenteAlCrearDa400() throws Exception {
        when(catalogClient.buscarProducto(99L))
                .thenThrow(new com.pedidos360.orders.exception.ProductoNoEncontradoEnCatalogoException(99L));

        mvc.perform(post(URL).with(usuario("cliente-x", "Cliente")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direccionEntrega\":\"Dir\",\"items\":[{\"productoId\":99,\"cantidad\":1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles['items[0].productoId']").value("El producto 99 no existe en el catálogo"));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class),
                any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    @Test
    void operadorEliminaLogicamenteYLuegoEs404() throws Exception {
        long id = crearComo("cliente-borra");
        mvc.perform(delete(URL + "/" + id).with(operador())).andExpect(status().isNoContent());
        mvc.perform(get(URL + "/" + id).with(operador())).andExpect(status().isNotFound());
    }

    @Test
    void canceladoDesapareceDelListadoYAvisaAlCliente() throws Exception {
        long id = crearComo("cliente-cancela");
        mvc.perform(put(URL + "/" + id + "/status").with(usuario("cliente-cancela", "Cliente"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELADO\",\"comentario\":\"Ya no\"}"))
                .andExpect(status().isOk());

        mvc.perform(get(URL).with(usuario("cliente-cancela", "Cliente")))
                .andExpect(jsonPath("$.contenido.length()").value(0));
        String todos = mvc.perform(get(URL).with(operador()).param("size", "100"))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(todos, "$.contenido[*].id")).doesNotContain((int) id);
        mvc.perform(get(URL + "/notifications").with(usuario("cliente-cancela", "Cliente")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pedidoId").value(id))
                .andExpect(jsonPath("$[0].titulo").value("Pedido cancelado"))
                .andExpect(jsonPath("$[0].leida").value(false));
    }

    @Test
    void eliminarYEditarPublicanEnRabbitYAvisanEnLaApp() throws Exception {
        long id = crearComo("cliente-avisos");
        mvc.perform(put(URL + "/" + id).with(operador()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direccionEntrega\":\"Otra 123\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete(URL + "/" + id).with(operador())).andExpect(status().isNoContent());

        verificarPublicados("email.send", 3); // creado + modificado + eliminado
        mvc.perform(get(URL + "/notifications").with(usuario("cliente-avisos", "Cliente")))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].titulo").value("Pedido eliminado"))
                .andExpect(jsonPath("$[1].titulo").value("Pedido modificado"));

        mvc.perform(put(URL + "/notifications/read").with(usuario("cliente-avisos", "Cliente")))
                .andExpect(status().isNoContent());
        mvc.perform(get(URL + "/notifications").with(usuario("cliente-avisos", "Cliente")))
                .andExpect(jsonPath("$[0].leida").value(true))
                .andExpect(jsonPath("$[1].leida").value(true));
        // Cada usuario ve solo sus avisos.
        mvc.perform(get(URL + "/notifications").with(usuario("otro-cliente", "Cliente")))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void laVueltaDeNotifyQuedaEnLaMensajeriaDelPedidoConElActor() throws Exception {
        long id = crearComo("cliente-msg");

        String enviados = mvc.perform(get(URL + "/" + id + "/messages").with(operador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].comando").value("email"))
                .andExpect(jsonPath("$[0].type").value("OrderCreated"))
                .andExpect(jsonPath("$[0].estado").value("ENVIADO"))
                .andExpect(jsonPath("$[0].usuarioRol").value("Cliente"))
                .andExpect(jsonPath("$[0].usuarioNombre").value("Usuario cliente-msg"))
                .andReturn().getResponse().getContentAsString();
        String eventId = JsonPath.read(enviados, "$[0].comandoEventId");

        // notify rechazó el comando y llegó a la DLQ: su resultado vuelve por Rabbit a q.orders.resultados.
        String resultado = "{\"eventId\":\"r-1\",\"comandoEventId\":\"" + eventId + "\",\"pedidoId\":" + id
                + ",\"comando\":\"email\",\"type\":\"OrderCreated\",\"resultado\":\"EN_DLQ\","
                + "\"motivo\":\"El pedido no trae clienteEmail\",\"cola\":\"q.cmd.email\",\"fecha\":\"2030-01-01T00:00:00Z\"}";
        resultadoListener.onResultado(new org.springframework.amqp.core.Message(
                resultado.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                new org.springframework.amqp.core.MessageProperties()));

        mvc.perform(get(URL + "/" + id + "/messages").with(operador()))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].estado").value("EN_DLQ"))
                .andExpect(jsonPath("$[1].motivo").value("El pedido no trae clienteEmail"))
                .andExpect(jsonPath("$[1].comandoEventId").value(eventId));
        mvc.perform(get(URL + "/" + id).with(operador())).andExpect(jsonPath("$.alertaMensajeria").value(true));
        String lista = mvc.perform(get(URL).with(operador()).param("size", "100"))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Boolean>>read(lista, "$.contenido[?(@.id == " + id + ")].alertaMensajeria"))
                .containsExactly(true);
        // La mensajería es del dashboard del operador.
        mvc.perform(get(URL + "/" + id + "/messages").with(usuario("cliente-msg", "Cliente")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unResultadoIlegibleSeDescartaSinRomperNada() {
        resultadoListener.onResultado(new org.springframework.amqp.core.Message(
                "{esto no es json".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                new org.springframework.amqp.core.MessageProperties()));
    }

    @Test
    void healthEsPublico() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
