package com.pedidos360.orders.messaging.rabbit;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * Converter JSON con Jackson 3 (el mismo JsonMapper de Spring Boot: fechas ISO-8601) y un RabbitTemplate
 * con mandatory=true + publisher confirms/returns (spring.rabbitmq.publisher-confirm-type=correlated,
 * publisher-returns=true). Los publishers esperan cada confirm (ConfirmacionBroker) y fallan si no llega;
 * estos callbacks solo dejan el detalle en el log.
 */
@Slf4j
@Configuration
public class RabbitConfig {

    @Bean
    public MessageConverter rabbitMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter rabbitMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(rabbitMessageConverter);
        template.setMandatory(true);
        template.setConfirmCallback((correlation, ack, cause) -> {
            if (!ack) {
                log.error("RabbitMQ no confirmó el mensaje eventId={}: {}",
                        correlation != null ? correlation.getId() : "?", cause);
            }
        });
        template.setReturnsCallback(returned -> log.error(
                "Mensaje no enrutable (ninguna cola enlazada): exchange={} routingKey={} replyCode={} replyText={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyCode(), returned.getReplyText()));
        return template;
    }
}
