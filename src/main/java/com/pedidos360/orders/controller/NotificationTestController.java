package com.pedidos360.orders.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pedidos360.orders.dto.NotificationMessage;
import com.pedidos360.orders.messaging.rabbit.NotificationPublisher;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Endpoint de la guía Parte 2 para probar desde Postman. A diferencia de la guía exige el mismo JWT que el
 * resto de /api/orders/** (scope access_as_user) y un rol de la aplicación.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Notificación de prueba")
public class NotificationTestController {

    private final NotificationPublisher publisher;

    @PostMapping("/test-notification")
    @PreAuthorize("hasAnyRole('Admin', 'Operador', 'Cliente')")
    @Operation(summary = "Publicar una notificación de prueba en cmd.direct / email.send")
    @ApiResponse(responseCode = "202", description = "Publicación solicitada (no significa que el correo se procesó)")
    public ResponseEntity<String> enviarNotificacion(@Valid @RequestBody NotificationMessage mensaje) {
        publisher.enviarNotificacion(mensaje);
        return ResponseEntity.accepted().body("Solicitud de notificacion publicada");
    }
}
