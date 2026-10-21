package com.pedidos360.orders.client;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.pedidos360.orders.config.DownstreamProperties;
import com.pedidos360.orders.exception.AccesoRechazadoDownstreamException;
import com.pedidos360.orders.exception.ConflictoException;
import com.pedidos360.orders.exception.ServicioNoDisponibleException;
import com.pedidos360.orders.web.RequestIdFilter;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Igual que DownstreamRestClientFactory del BFF: timeouts, propagación de Authorization y X-Request-Id,
 * y traducción de errores. Diferencias: un 409 se conserva (mensaje + detalles) y cualquier 5xx o error
 * de red es 503. Un 404 no se traduce aquí: cada client lo convierte a su excepción con {@code onStatus}.
 */
@Slf4j
@Component
public class DownstreamRestClientFactory {

    private final DownstreamProperties properties;
    private final ObjectMapper objectMapper;

    public DownstreamRestClientFactory(DownstreamProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** @param servicio nombre legible para mensajes de error, p. ej. "catálogo". */
    public RestClient create(String servicio, String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    propagarCabeceras(request);
                    try {
                        return execution.execute(request, body);
                    } catch (IOException e) {
                        // conexión rechazada, timeout, DNS, etc.
                        throw new ServicioNoDisponibleException(servicio, e);
                    }
                })
                .defaultStatusHandler(HttpStatusCode::isError,
                        (request, response) -> {
                            throw traducir(servicio, response);
                        })
                .build();
    }

    private void propagarCabeceras(HttpRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            request.getHeaders().setBearerAuth(jwtAuth.getToken().getTokenValue());
        }
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId != null) {
            request.getHeaders().set(RequestIdFilter.HEADER, requestId);
        }
    }

    private RuntimeException traducir(String servicio, ClientHttpResponse response) throws IOException {
        int code = response.getStatusCode().value();
        if (code == 409) {
            Map<String, Object> body = leer(response);
            String mensaje = body.get("mensaje") instanceof String m ? m : "Conflicto en el servicio de " + servicio;
            Map<String, Object> detalles = new LinkedHashMap<>();
            if (body.get("detalles") instanceof Map<?, ?> d) {
                d.forEach((k, v) -> detalles.put(String.valueOf(k), v));
            }
            return new ConflictoException(mensaje, detalles);
        }
        if (code == 401 || code == 403) {
            return new AccesoRechazadoDownstreamException(HttpStatus.valueOf(code), servicio);
        }
        // 5xx u otro código inesperado (un 400 aquí sería un bug de orders): el detalle solo va al log
        log.error("Servicio de {} respondió {}", servicio, code);
        return new ServicioNoDisponibleException(servicio, null);
    }

    private Map<String, Object> leer(ClientHttpResponse response) {
        try {
            return objectMapper.readValue(response.getBody(), new TypeReference<>() { });
        } catch (IOException | RuntimeException e) {
            log.warn("No se pudo leer el cuerpo de error del microservicio: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
