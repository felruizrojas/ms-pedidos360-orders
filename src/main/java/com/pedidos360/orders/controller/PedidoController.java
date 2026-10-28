package com.pedidos360.orders.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pedidos360.orders.dto.ActualizarPedidoRequest;
import com.pedidos360.orders.dto.CambiarEstadoRequest;
import com.pedidos360.orders.dto.CrearPedidoRequest;
import com.pedidos360.orders.dto.HistorialEstadoResponse;
import com.pedidos360.orders.dto.PaginaResponse;
import com.pedidos360.orders.dto.PedidoResponse;
import com.pedidos360.orders.exception.ErrorBodySchema;
import com.pedidos360.orders.exception.SolicitudInvalidaException;
import com.pedidos360.orders.model.EstadoPedido;
import com.pedidos360.orders.security.UsuarioActual;
import com.pedidos360.orders.service.PedidoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Pedidos")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Validación (detalles por campo) o producto inexistente en catalog",
                content = @Content(schema = @Schema(implementation = ErrorBodySchema.class))),
        @ApiResponse(responseCode = "401", description = "Token ausente, inválido o expirado",
                content = @Content(schema = @Schema(implementation = ErrorBodySchema.class))),
        @ApiResponse(responseCode = "403", description = "Falta el scope access_as_user o el rol requerido",
                content = @Content(schema = @Schema(implementation = ErrorBodySchema.class))),
        @ApiResponse(responseCode = "404", description = "El pedido no existe o no pertenece al cliente",
                content = @Content(schema = @Schema(implementation = ErrorBodySchema.class))),
        @ApiResponse(responseCode = "409", description = "Transición inválida, stock insuficiente, pedido no editable o modificado en paralelo",
                content = @Content(schema = @Schema(implementation = ErrorBodySchema.class))),
        @ApiResponse(responseCode = "503", description = "ms-pedidos360-catalog no disponible",
                content = @Content(schema = @Schema(implementation = ErrorBodySchema.class)))
})
public class PedidoController {

    private final PedidoService pedidoService;
    private final UsuarioActual usuarioActual;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('Cliente')")
    @Operation(summary = "Crear pedido (queda en CREADO; precios desde catalog; no toca stock)")
    @ApiResponse(responseCode = "201", description = "Pedido creado")
    public PedidoResponse crear(@Valid @RequestBody CrearPedidoRequest request) {
        return pedidoService.crear(request, usuarioActual.obtener());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('Operador', 'Cliente')")
    @Operation(summary = "Obtener pedido (un Cliente solo ve los suyos)")
    public PedidoResponse obtener(@PathVariable Long id) {
        return pedidoService.obtener(id, usuarioActual.obtener());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('Operador', 'Cliente')")
    @Operation(summary = "Listar pedidos paginados, más recientes primero (un Cliente solo recibe los suyos)")
    public PaginaResponse<PedidoResponse> listar(
            @Parameter(description = "Filtra por estado", example = "CREADO")
            @RequestParam(required = false) EstadoPedido estado,
            @Parameter(description = "creadoEn >= desde. ISO-8601: 2026-10-01T00:00:00Z, 2026-10-01T00:00:00-03:00 o 2026-10-01 (UTC)")
            @RequestParam(required = false) String desde,
            @Parameter(description = "creadoEn <= hasta. Misma sintaxis; una fecha sola incluye el día completo")
            @RequestParam(required = false) String hasta,
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página (1 a 100)")
            @RequestParam(defaultValue = "20") int size) {
        return pedidoService.listar(estado, parseFecha("desde", desde, false), parseFecha("hasta", hasta, true),
                page, size, usuarioActual.obtener());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('Operador', 'Cliente')")
    @Operation(summary = "Editar ítems y/o dirección (solo en CREADO; recalcula precios desde catalog)")
    public PedidoResponse actualizar(@PathVariable Long id, @Valid @RequestBody ActualizarPedidoRequest request) {
        return pedidoService.actualizar(id, request, usuarioActual.obtener());
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('Operador', 'Cliente')")
    @Operation(summary = "Cambiar estado",
            description = "Operador: cualquier transición válida. Cliente: solo CANCELADO de su pedido en CREADO. "
                    + "ACEPTADO descuenta stock en catalog; CANCELADO desde ACEPTADO/EN_PREPARACION lo repone.")
    public PedidoResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoRequest request) {
        return pedidoService.cambiarEstado(id, request, usuarioActual.obtener());
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('Operador', 'Cliente')")
    @Operation(summary = "Historial de estados (más antiguo primero)")
    public List<HistorialEstadoResponse> historial(@PathVariable Long id) {
        return pedidoService.historial(id, usuarioActual.obtener());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('Operador')")
    @Operation(summary = "Borrado lógico (solo CREADO o CANCELADO)")
    @ApiResponse(responseCode = "204", description = "Eliminado")
    public void eliminar(@PathVariable Long id) {
        pedidoService.eliminar(id);
    }

    /** Acepta instante (Z u offset) o fecha sola (UTC; en "hasta" cubre el día completo). */
    private static Instant parseFecha(String nombre, String valor, boolean finDelDia) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(valor.trim()).toInstant();
        } catch (DateTimeParseException e) {
            try {
                LocalDate fecha = LocalDate.parse(valor.trim());
                return finDelDia
                        ? fecha.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusNanos(1)
                        : fecha.atStartOfDay().toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException e2) {
                throw new SolicitudInvalidaException(Map.of(nombre,
                        "Fecha inválida; use ISO-8601, p. ej. 2026-10-01T00:00:00Z o 2026-10-01."));
            }
        }
    }
}
