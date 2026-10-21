package com.pedidos360.orders.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Timeouts comunes a los microservicios que orders invoca (pedidos360.services.*). */
@ConfigurationProperties("pedidos360.services")
public record DownstreamProperties(Duration connectTimeout, Duration readTimeout) {

    public DownstreamProperties {
        connectTimeout = connectTimeout != null ? connectTimeout : Duration.ofSeconds(2);
        readTimeout = readTimeout != null ? readTimeout : Duration.ofSeconds(5);
    }
}
