package com.pedidos360.orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MsPedidos360OrdersApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsPedidos360OrdersApplication.class, args);
	}

}
