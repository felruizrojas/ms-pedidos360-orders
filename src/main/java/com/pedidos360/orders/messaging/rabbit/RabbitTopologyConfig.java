package com.pedidos360.orders.messaging.rabbit;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declara exchanges, colas (con DLX) y bindings. RabbitAdmin los crea al abrir la primera conexión
 * (no al arrancar), así que la app arranca aunque Rabbit no esté. Más adelante lo hará un
 * microservicio administrador y aquí se pondrá RABBIT_DECLARE_TOPOLOGY=false.
 * IMPORTANTE: quien declare las mismas colas debe usar exactamente estos argumentos.
 */
@Configuration
@ConditionalOnProperty(name = "pedidos360.rabbit.declare-topology", havingValue = "true", matchIfMissing = true)
public class RabbitTopologyConfig {

    @Bean
    public Declarables topologiaComandos(RabbitProperties props) {
        RabbitProperties.Exchanges ex = props.exchanges();
        RabbitProperties.Destinos colas = props.queues();
        RabbitProperties.Destinos rk = props.routingKeys();
        RabbitProperties.Destinos patrones = props.topicPatterns();

        DirectExchange direct = new DirectExchange(ex.direct(), true, false);
        TopicExchange topic = new TopicExchange(ex.topic(), true, false);
        DirectExchange dlx = new DirectExchange(ex.dlx(), true, false);

        Queue email = cola(colas.email(), ex.dlx(), rk.email());
        Queue kitchen = cola(colas.kitchen(), ex.dlx(), rk.kitchen());
        Queue invoice = cola(colas.invoice(), ex.dlx(), rk.invoice());
        Queue emailDlq = QueueBuilder.durable(RabbitProperties.dlq(colas.email())).build();
        Queue kitchenDlq = QueueBuilder.durable(RabbitProperties.dlq(colas.kitchen())).build();
        Queue invoiceDlq = QueueBuilder.durable(RabbitProperties.dlq(colas.invoice())).build();

        return new Declarables(
                direct, topic, dlx,
                email, kitchen, invoice, emailDlq, kitchenDlq, invoiceDlq,
                // cmd.direct: routing key exacta
                bind(email, direct, rk.email()),
                bind(kitchen, direct, rk.kitchen()),
                bind(invoice, direct, rk.invoice()),
                // cmd.topic: patrones
                bind(email, topic, patrones.email()),
                bind(kitchen, topic, patrones.kitchen()),
                bind(invoice, topic, patrones.invoice()),
                // cmd.dead.dlx -> DLQs (con la x-dead-letter-routing-key de cada cola)
                bind(emailDlq, dlx, rk.email()),
                bind(kitchenDlq, dlx, rk.kitchen()),
                bind(invoiceDlq, dlx, rk.invoice()));
    }

    private static Queue cola(String nombre, String dlx, String dlRoutingKey) {
        return QueueBuilder.durable(nombre)
                .deadLetterExchange(dlx)
                .deadLetterRoutingKey(dlRoutingKey)
                .build();
    }

    private static Binding bind(Queue queue, DirectExchange exchange, String routingKey) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }

    private static Binding bind(Queue queue, TopicExchange exchange, String pattern) {
        return BindingBuilder.bind(queue).to(exchange).with(pattern);
    }
}
