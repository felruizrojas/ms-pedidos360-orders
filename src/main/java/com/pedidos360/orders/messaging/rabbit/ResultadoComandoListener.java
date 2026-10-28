package com.pedidos360.orders.messaging.rabbit;

import java.nio.charset.StandardCharsets;

import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.pedidos360.orders.service.SeguimientoMensajes;
import com.pedidos360.orders.web.RequestIdFilter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * Vuelta notify -> orders: consume q.orders.resultados y guarda el resultado de cada comando por pedido.
 * Recibe el cuerpo crudo y lo lee con su propio DTO (el __TypeId__ de notify nombra una clase que aquí no
 * existe). Nunca lanza: un resultado ilegible se registra en el log y se descarta (ACK automático), para no
 * dejarlo dando vueltas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResultadoComandoListener {

    private final JsonMapper jsonMapper;
    private final SeguimientoMensajes seguimiento;

    @RabbitListener(id = "resultados", queues = "${pedidos360.rabbit.resultados.queue}")
    public void onResultado(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            ResultadoComando r = jsonMapper.readValue(body, ResultadoComando.class);
            MDC.put(RequestIdFilter.MDC_KEY, r.correlationId());
            seguimiento.registrarResultado(r);
            log.info("Resultado de {} recibido: pedidoId={} type={} resultado={} motivo={}", r.comando(),
                    r.pedidoId(), r.type(), r.resultado(), r.motivo());
        } catch (RuntimeException ex) {
            log.error("Resultado de comando ilegible o inválido (se descarta): {} body={}", ex.getMessage(), body, ex);
        } finally {
            MDC.remove(RequestIdFilter.MDC_KEY);
        }
    }
}
