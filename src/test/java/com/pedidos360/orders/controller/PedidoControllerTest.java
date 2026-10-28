package com.pedidos360.orders.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.pedidos360.orders.config.SecurityConfig;
import com.pedidos360.orders.config.SecurityErrorHandlers;
import com.pedidos360.orders.dto.CambiarEstadoRequest;
import com.pedidos360.orders.dto.CrearPedidoRequest;
import com.pedidos360.orders.dto.PaginaResponse;
import com.pedidos360.orders.dto.PedidoResponse;
import com.pedidos360.orders.exception.ConflictoException;
import com.pedidos360.orders.exception.PedidoNotFoundException;
import com.pedidos360.orders.exception.ServicioNoDisponibleException;
import com.pedidos360.orders.exception.TransicionInvalidaException;
import com.pedidos360.orders.model.EstadoPedido;
import com.pedidos360.orders.security.Usuario;
import com.pedidos360.orders.security.UsuarioActual;
import com.pedidos360.orders.service.PedidoService;

/** Seguridad (token, scope, roles), validación y traducción de errores del controller. Servicio mockeado. */
@WebMvcTest(PedidoController.class)
@Import({SecurityConfig.class, SecurityErrorHandlers.class, UsuarioActual.class})
class PedidoControllerTest {

    private static final String URL = "/api/orders";
    private static final String NUEVO = "{\"direccionEntrega\":\"Av. Siempre Viva 742\","
            + "\"items\":[{\"productoId\":1,\"cantidad\":2}]}";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PedidoService pedidoService;

    @MockitoBean
    JwtDecoder jwtDecoder;

    /** JWT con scope y un rol, con los claims que lee UsuarioActual. */
    static RequestPostProcessor como(String rol) {
        return jwt().jwt(j -> j.subject("sub-1").claim("oid", "oid-" + rol)
                        .claim("name", rol + " Test").claim("preferred_username", rol.toLowerCase() + "@pedidos360.cl"))
                .authorities(new SimpleGrantedAuthority("SCOPE_access_as_user"),
                        new SimpleGrantedAuthority("ROLE_" + rol));
    }

    static PedidoResponse respuesta(EstadoPedido estado) {
        return new PedidoResponse(15L, "oid-Cliente", "Cliente Test", "cliente@pedidos360.cl", "Av. Siempre Viva 742",
                estado, new BigDecimal("1200.00"), false, List.of(), Instant.parse("2026-10-20T15:00:00Z"),
                null, null, null, null, null, null, 0L);
    }

    // ---------- autenticación / autorización ----------

    @Test
    void sinTokenDevuelve401ConErrorBody() throws Exception {
        mvc.perform(get(URL + "/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
        verifyNoInteractions(pedidoService);
    }

    @Test
    void sinScopeDevuelve403AunqueTengaRol() throws Exception {
        mvc.perform(get(URL + "/1").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Operador"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void adminNoEsActorDePedidos() throws Exception {
        mvc.perform(get(URL + "/1").with(como("Admin"))).andExpect(status().isForbidden());
        mvc.perform(get(URL).with(como("Admin"))).andExpect(status().isForbidden());
        mvc.perform(put(URL + "/1/status").with(como("Admin")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACEPTADO\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(pedidoService);
    }

    @Test
    void conScopePeroSinRolDevuelve403() throws Exception {
        mvc.perform(get(URL + "/1").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_access_as_user"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(SecurityErrorHandlers.MENSAJE_403));
        verifyNoInteractions(pedidoService);
    }

    @Test
    void adminYOperadorNoPuedenCrearPedidos() throws Exception {
        mvc.perform(post(URL).with(como("Admin")).contentType(MediaType.APPLICATION_JSON).content(NUEVO))
                .andExpect(status().isForbidden());
        mvc.perform(post(URL).with(como("Operador")).contentType(MediaType.APPLICATION_JSON).content(NUEVO))
                .andExpect(status().isForbidden());
        verifyNoInteractions(pedidoService);
    }

    @Test
    void clienteNoPuedeEliminar() throws Exception {
        mvc.perform(delete(URL + "/15").with(como("Cliente"))).andExpect(status().isForbidden());
        mvc.perform(delete(URL + "/15").with(como("Admin"))).andExpect(status().isForbidden());
        verifyNoInteractions(pedidoService);
    }

    @Test
    void operadorEliminaCon204() throws Exception {
        mvc.perform(delete(URL + "/15").with(como("Operador"))).andExpect(status().isNoContent());
        verify(pedidoService).eliminar(15L);
    }

    @Test
    void operadorEditaPedido() throws Exception {
        when(pedidoService.actualizar(eq(15L), any(), any())).thenReturn(respuesta(EstadoPedido.CREADO));

        mvc.perform(put(URL + "/15").with(como("Operador")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direccionEntrega\":\"Otra\"}"))
                .andExpect(status().isOk());
        verify(pedidoService).actualizar(eq(15L), any(), any());
    }

    // ---------- crear ----------

    @Test
    void clienteCreaPedidoCon201YElUsuarioSaleDelJwt() throws Exception {
        when(pedidoService.crear(any(), any())).thenReturn(respuesta(EstadoPedido.CREADO));

        mvc.perform(post(URL).with(como("Cliente")).contentType(MediaType.APPLICATION_JSON).content(NUEVO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(15))
                .andExpect(jsonPath("$.estado").value("CREADO"));

        ArgumentCaptor<Usuario> usuario = ArgumentCaptor.forClass(Usuario.class);
        ArgumentCaptor<CrearPedidoRequest> req = ArgumentCaptor.forClass(CrearPedidoRequest.class);
        verify(pedidoService).crear(req.capture(), usuario.capture());
        assertThat(usuario.getValue().id()).isEqualTo("oid-Cliente");
        assertThat(usuario.getValue().nombre()).isEqualTo("Cliente Test");
        assertThat(usuario.getValue().email()).isEqualTo("cliente@pedidos360.cl");
        assertThat(usuario.getValue().roles()).containsExactly("Cliente");
        assertThat(req.getValue().items()).hasSize(1);
    }

    @Test
    void elClaimEmailTienePrioridadSobrePreferredUsername() throws Exception {
        when(pedidoService.crear(any(), any())).thenReturn(respuesta(EstadoPedido.CREADO));

        mvc.perform(post(URL).with(jwt().jwt(j -> j.subject("sub-1").claim("oid", "oid-Cliente")
                                        .claim("preferred_username", "usuario3@contoso.onmicrosoft.com")
                                        .claim("email", "correo.real@contoso.cl"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_access_as_user"),
                                        new SimpleGrantedAuthority("ROLE_Cliente")))
                        .contentType(MediaType.APPLICATION_JSON).content(NUEVO))
                .andExpect(status().isCreated());

        ArgumentCaptor<Usuario> usuario = ArgumentCaptor.forClass(Usuario.class);
        verify(pedidoService).crear(any(), usuario.capture());
        assertThat(usuario.getValue().email()).isEqualTo("correo.real@contoso.cl");
    }

    @Test
    void crearSinItemsNiDireccionDevuelve400ConDetalles() throws Exception {
        mvc.perform(post(URL).with(como("Cliente")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direccionEntrega\":\"  \",\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Error de validación"))
                .andExpect(jsonPath("$.detalles.direccionEntrega").exists())
                .andExpect(jsonPath("$.detalles.items").exists());
        verifyNoInteractions(pedidoService);
    }

    @Test
    void crearConCantidadCeroDevuelve400() throws Exception {
        mvc.perform(post(URL).with(como("Cliente")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direccionEntrega\":\"Dir\",\"items\":[{\"productoId\":1,\"cantidad\":0}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles['items[0].cantidad']").value("La cantidad debe ser mayor que 0."));
    }

    @Test
    void cuerpoIlegibleDevuelve400() throws Exception {
        mvc.perform(post(URL).with(como("Cliente")).contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
    }

    @Test
    void catalogCaidoDevuelve503() throws Exception {
        when(pedidoService.crear(any(), any())).thenThrow(new ServicioNoDisponibleException("catálogo", null));

        mvc.perform(post(URL).with(como("Cliente")).contentType(MediaType.APPLICATION_JSON).content(NUEVO))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value("Servicio de catálogo no disponible"));
    }

    // ---------- obtener / listar ----------

    @Test
    void clienteNoVePedidoAjeno404() throws Exception {
        when(pedidoService.obtener(eq(99L), any())).thenThrow(new PedidoNotFoundException(99L));

        mvc.perform(get(URL + "/99").with(como("Cliente")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensaje").value("Pedido no encontrado con id: 99"));
    }

    @Test
    void idNoNumericoDevuelve400() throws Exception {
        mvc.perform(get(URL + "/abc").with(como("Operador"))).andExpect(status().isBadRequest());
    }

    @Test
    void listarDevuelveLaFormaPaginadaEstable() throws Exception {
        when(pedidoService.listar(any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(new PaginaResponse<>(List.of(respuesta(EstadoPedido.CREADO)), 0, 20, 1, 1));

        mvc.perform(get(URL).with(como("Operador")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].id").value(15))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamano").value(20))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(1));
    }

    @Test
    void listarAceptaEstadoConTildeYFechasIso() throws Exception {
        when(pedidoService.listar(any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(new PaginaResponse<>(List.of(), 1, 5, 0, 0));

        mvc.perform(get(URL).with(como("Operador"))
                        .param("estado", "EN_PREPARACIÓN")
                        .param("desde", "2026-10-01T00:00:00-03:00")
                        .param("hasta", "2026-10-31")
                        .param("page", "1").param("size", "5"))
                .andExpect(status().isOk());

        verify(pedidoService).listar(eq(EstadoPedido.EN_PREPARACION), eq(Instant.parse("2026-10-01T03:00:00Z")),
                eq(Instant.parse("2026-10-31T23:59:59.999999999Z")), eq(1), eq(5), any());
    }

    @Test
    void listarConFechaInvalidaDevuelve400() throws Exception {
        mvc.perform(get(URL).with(como("Operador")).param("desde", "ayer"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles.desde").exists());
    }

    @Test
    void listarConEstadoDesconocidoDevuelve400() throws Exception {
        mvc.perform(get(URL).with(como("Operador")).param("estado", "VOLANDO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("VOLANDO")));
    }

    // ---------- estado ----------

    @Test
    void despacharSinAceptarDevuelve409() throws Exception {
        when(pedidoService.cambiarEstado(eq(15L), any(), any())).thenThrow(new TransicionInvalidaException(
                EstadoPedido.CREADO, EstadoPedido.DESPACHADO,
                "No se puede despachar un pedido que no fue aceptado (estado actual: CREADO)"));

        mvc.perform(put(URL + "/15/status").with(como("Operador")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DESPACHADO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("no fue aceptado")))
                .andExpect(jsonPath("$.detalles.estadoActual").value("CREADO"))
                .andExpect(jsonPath("$.detalles.estadoSolicitado").value("DESPACHADO"));
    }

    @Test
    void stockInsuficienteDevuelve409ConDetallesDeCatalog() throws Exception {
        when(pedidoService.cambiarEstado(eq(15L), any(), any())).thenThrow(new ConflictoException(
                "Stock insuficiente para \"Pan Amasado\" (id 1): solicitado 5, disponible 2",
                java.util.Map.of("productoId", 1, "solicitado", 5, "disponible", 2)));

        mvc.perform(put(URL + "/15/status").with(como("Operador")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACEPTADO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detalles.disponible").value(2));
    }

    @Test
    void statusConTildeSeAceptaComoAlias() throws Exception {
        when(pedidoService.cambiarEstado(anyLong(), any(), any())).thenReturn(respuesta(EstadoPedido.EN_PREPARACION));

        mvc.perform(put(URL + "/15/status").with(como("Operador")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EN_PREPARACIÓN\",\"comentario\":\"al horno\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_PREPARACION"));

        verify(pedidoService).cambiarEstado(eq(15L),
                eq(new CambiarEstadoRequest(EstadoPedido.EN_PREPARACION, "al horno")), any());
    }

    @Test
    void statusDesconocidoDevuelve400ConDetalle() throws Exception {
        mvc.perform(put(URL + "/15/status").with(como("Operador")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"VOLANDO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles.status").exists());
        verifyNoInteractions(pedidoService);
    }

    @Test
    void statusAusenteDevuelve400() throws Exception {
        mvc.perform(put(URL + "/15/status").with(como("Operador")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comentario\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles.status").exists());
    }

    @Test
    void clientePuedeLlegarAlEndpointDeStatus() throws Exception {
        // el controller lo deja pasar; la regla "solo CANCELADO en CREADO" la aplica el servicio
        when(pedidoService.cambiarEstado(anyLong(), any(), any())).thenReturn(respuesta(EstadoPedido.CANCELADO));

        mvc.perform(put(URL + "/15/status").with(como("Cliente")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELADO\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void accessDeniedDelServicioDevuelve403() throws Exception {
        when(pedidoService.cambiarEstado(anyLong(), any(), any()))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("solo cancelar"));

        mvc.perform(put(URL + "/15/status").with(como("Cliente")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACEPTADO\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void historialResponde200() throws Exception {
        when(pedidoService.historial(eq(15L), any())).thenReturn(List.of());
        mvc.perform(get(URL + "/15/history").with(como("Cliente"))).andExpect(status().isOk());
    }

    @Test
    void requestIdSeDevuelveEnLaRespuesta() throws Exception {
        when(pedidoService.historial(eq(15L), any())).thenReturn(List.of());
        mvc.perform(get(URL + "/15/history").with(como("Operador")).header("X-Request-Id", "req-abc"))
                .andExpect(header().string("X-Request-Id", "req-abc"));
    }

    @Test
    void editarConDireccionEnBlancoDevuelve400() throws Exception {
        mvc.perform(put(URL + "/15").with(como("Cliente")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direccionEntrega\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles.direccionEntrega").exists());
        verify(pedidoService, org.mockito.Mockito.never()).actualizar(anyLong(), any(), any());
    }
}
