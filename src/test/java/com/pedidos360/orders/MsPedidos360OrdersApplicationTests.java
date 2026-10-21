package com.pedidos360.orders;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Arranca sin PostgreSQL (H2), sin RabbitMQ (conexión perezosa, topología off) y sin Azure (JWKS perezoso). */
@SpringBootTest
@ActiveProfiles("test")
class MsPedidos360OrdersApplicationTests {

	@Test
	void contextLoads() {
	}

}
