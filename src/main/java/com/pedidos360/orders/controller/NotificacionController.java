package com.pedidos360.orders.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pedidos360.orders.dto.NotificacionResponse;
import com.pedidos360.orders.security.UsuarioActual;
import com.pedidos360.orders.service.NotificacionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Avisos en la app del usuario autenticado (pedido cancelado, modificado o eliminado). */
@RestController
@RequestMapping("/api/orders/notifications")
@RequiredArgsConstructor
@Tag(name = "Notificaciones")
public class NotificacionController {

    private final NotificacionService notificacionService;
    private final UsuarioActual usuarioActual;

    @GetMapping
    @PreAuthorize("hasAnyRole('Operador', 'Cliente')")
    @Operation(summary = "Últimas 20 notificaciones del usuario (más recientes primero)")
    public List<NotificacionResponse> listar() {
        return notificacionService.listar(usuarioActual.obtener());
    }

    @PutMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('Operador', 'Cliente')")
    @Operation(summary = "Marcar todas las notificaciones del usuario como leídas")
    @ApiResponse(responseCode = "204", description = "Marcadas")
    public void marcarTodasLeidas() {
        notificacionService.marcarTodasLeidas(usuarioActual.obtener());
    }
}
