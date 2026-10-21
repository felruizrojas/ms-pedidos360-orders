package com.pedidos360.orders.support;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;

/** Catálogo simulado sobre HTTP real; registra la última petición recibida. */
public final class FakeCatalogServer {

    public record Received(String method, String path, String authorization, String requestId, String body) { }

    private final HttpServer server;
    private volatile int status = 200;
    private volatile String responseBody = "[]";
    private volatile long delayMillis = 0;
    private final AtomicReference<Received> last = new AtomicReference<>();

    public FakeCatalogServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            last.set(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("X-Request-Id"), body));
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
                if (bytes.length > 0) {
                    exchange.getResponseBody().write(bytes);
                }
            } catch (IOException ignored) {
                // el cliente ya cerró (timeout)
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool()); // un handler dormido no bloquea al siguiente
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void respond(int status, String body) {
        this.status = status;
        this.responseBody = body;
        this.delayMillis = 0;
        this.last.set(null);
    }

    public void delay(long millis) {
        this.delayMillis = millis;
    }

    public Received last() {
        return last.get();
    }
}
