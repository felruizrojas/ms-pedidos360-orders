package com.pedidos360.orders.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.pedidos360.orders.config.SecurityConfig;
import com.pedidos360.orders.config.SecurityErrorHandlers;
import com.pedidos360.orders.dto.NotificationMessage;
import com.pedidos360.orders.exception.ServicioNoDisponibleException;
import com.pedidos360.orders.messaging.rabbit.NotificationPublisher;

/** POST /api/orders/test-notification de la guía Parte 2: token, 202 y validación. Publisher mockeado. */
@WebMvcTest(NotificationTestController.class)
@Import({SecurityConfig.class, SecurityErrorHandlers.class})
class NotificationTestControllerTest {

    private static final String URL = "/api/orders/test-notification";
    private static final String BODY = """
            {"pedidoId":501,"email":"cliente@ejemplo.cl","asunto":"Pedido confirmado","mensaje":"Su pedido 501 ha sido confirmado"}
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    NotificationPublisher publisher;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void conTokenPublicaYResponde202() throws Exception {
        mvc.perform(post(URL).with(PedidoControllerTest.como("Cliente")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isAccepted())
                .andExpect(content().string("Solicitud de notificacion publicada"));

        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(publisher).enviarNotificacion(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new NotificationMessage(501L, "cliente@ejemplo.cl",
                "Pedido confirmado", "Su pedido 501 ha sido confirmado"));
    }

    @Test
    void cuerpoInvalidoResponde400SinPublicar() throws Exception {
        mvc.perform(post(URL).with(PedidoControllerTest.como("Operador")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"no-es-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles.pedidoId").exists())
                .andExpect(jsonPath("$.detalles.email").exists());

        verifyNoInteractions(publisher);
    }

    @Test
    void rabbitCaidoResponde503() throws Exception {
        doThrow(new ServicioNoDisponibleException("mensajería", null)).when(publisher).enviarNotificacion(any());

        mvc.perform(post(URL).with(PedidoControllerTest.como("Cliente")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void sinTokenResponde401SinPublicar() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(publisher);
    }

    @Test
    void sinScopeResponde403SinPublicar() throws Exception {
        mvc.perform(post(URL).with(jwt()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(publisher);
    }

    @Test
    void sinRolDeLaAplicacionResponde403SinPublicar() throws Exception {
        mvc.perform(post(URL).with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_access_as_user")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(publisher);
    }
}
