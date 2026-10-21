package com.pedidos360.orders.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.pedidos360.orders.config.SecurityErrorHandlers;

import lombok.extern.slf4j.Slf4j;

/** Mismo formato que catalog ({timestamp, status, error, mensaje, detalles?}) para que el BFF lo lea sin cambios. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errores.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                errores.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, ErrorBody.of(HttpStatus.BAD_REQUEST, "Error de validación", errores));
    }

    @ExceptionHandler(SolicitudInvalidaException.class)
    public ResponseEntity<Map<String, Object>> handleSolicitudInvalida(SolicitudInvalidaException ex) {
        return build(HttpStatus.BAD_REQUEST,
                ErrorBody.of(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.getDetalles()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException ex) {
        EstadoInvalidoException estado = buscarCausa(ex, EstadoInvalidoException.class);
        if (estado != null) {
            return build(HttpStatus.BAD_REQUEST, ErrorBody.of(HttpStatus.BAD_REQUEST, "Error de validación",
                    Map.of("status", estado.getMessage())));
        }
        return build(HttpStatus.BAD_REQUEST,
                ErrorBody.of(HttpStatus.BAD_REQUEST, "Cuerpo de la solicitud ausente o con formato inválido"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        EstadoInvalidoException estado = buscarCausa(ex, EstadoInvalidoException.class);
        String mensaje = estado != null ? estado.getMessage() : "Parámetro inválido: " + ex.getName();
        return build(HttpStatus.BAD_REQUEST, ErrorBody.of(HttpStatus.BAD_REQUEST, mensaje));
    }

    @ExceptionHandler(PedidoNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(PedidoNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ErrorBody.of(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ErrorBody.of(HttpStatus.NOT_FOUND, "Recurso no encontrado"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ErrorBody.of(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                ErrorBody.of(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no soportado"));
    }

    @ExceptionHandler(TransicionInvalidaException.class)
    public ResponseEntity<Map<String, Object>> handleTransicion(TransicionInvalidaException ex) {
        Map<String, Object> detalles = new LinkedHashMap<>();
        detalles.put("estadoActual", ex.getActual());
        detalles.put("estadoSolicitado", ex.getSolicitado());
        return build(HttpStatus.CONFLICT, ErrorBody.of(HttpStatus.CONFLICT, ex.getMessage(), detalles));
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<Map<String, Object>> handleConflicto(ConflictoException ex) {
        return build(HttpStatus.CONFLICT, ErrorBody.of(HttpStatus.CONFLICT, ex.getMessage(), ex.getDetalles()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return build(HttpStatus.CONFLICT, ErrorBody.of(HttpStatus.CONFLICT,
                "El pedido fue modificado por otra operación al mismo tiempo; vuelva a cargarlo e intente nuevamente"));
    }

    @ExceptionHandler(ServicioNoDisponibleException.class)
    public ResponseEntity<Map<String, Object>> handleServicioNoDisponible(ServicioNoDisponibleException ex) {
        log.error("{}", ex.getMessage(), ex.getCause());
        return build(HttpStatus.SERVICE_UNAVAILABLE, ErrorBody.of(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage()));
    }

    @ExceptionHandler(AccesoRechazadoDownstreamException.class)
    public ResponseEntity<Map<String, Object>> handleAccesoRechazado(AccesoRechazadoDownstreamException ex) {
        log.warn("Microservicio respondió {}: {}", ex.getStatus().value(), ex.getMessage());
        return build(ex.getStatus(), ErrorBody.of(ex.getStatus(), ex.getMessage()));
    }

    /** @PreAuthorize y la regla "un cliente solo cancela" lanzan esto dentro del controller/servicio. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, ErrorBody.of(HttpStatus.FORBIDDEN, SecurityErrorHandlers.MENSAJE_403));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, ErrorBody.of(HttpStatus.UNAUTHORIZED, SecurityErrorHandlers.MENSAJE_401));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Error no controlado", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorBody.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor"));
    }

    private static ResponseEntity<Map<String, Object>> build(HttpStatus status, Map<String, Object> body) {
        return ResponseEntity.status(status).body(body);
    }

    private static <T extends Throwable> T buscarCausa(Throwable ex, Class<T> tipo) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (tipo.isInstance(t)) {
                return tipo.cast(t);
            }
        }
        return null;
    }
}
