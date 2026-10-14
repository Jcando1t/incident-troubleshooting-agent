package com.example.incident.adapter;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OllamaEmbeddingModelTest {
    @Test
    void requestsAnEmbeddingFromOllamaAndReturnsTheVector() throws IOException {
        var requestBody = new java.util.concurrent.atomic.AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/embed", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var response = """
                    {"model":"bge-m3","embeddings":[[0.1,0.2,0.3]]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();

        try {
            var model = new OllamaEmbeddingModel(
                    RestClient.builder(),
                    "http://localhost:" + server.getAddress().getPort(),
                    "bge-m3"
            );

            assertEquals("bge-m3", model.modelName());
            assertEquals(List.of(0.1f, 0.2f, 0.3f), model.embed("MySQL 连接失败"));
            org.junit.jupiter.api.Assertions.assertTrue(requestBody.get().contains("MySQL 连接失败"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failsPromptlyWhenOllamaDoesNotRespondInTime() throws IOException {
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/embed", exchange -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            try {
                exchange.sendResponseHeaders(200, 0);
                exchange.close();
            } catch (IOException ignored) {
                // The client is expected to close the request after the read timeout.
            }
        });
        server.start();

        try {
            var model = new OllamaEmbeddingModel(
                    RestClient.builder(),
                    "http://localhost:" + server.getAddress().getPort(),
                    "bge-m3",
                    Duration.ofSeconds(1),
                    Duration.ofMillis(100)
            );

            assertThrows(IllegalStateException.class, () -> model.embed("timeout test"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsBlankTextWithoutCallingOllama() {
        var model = new OllamaEmbeddingModel(RestClient.builder(), "http://localhost:11434", "bge-m3");

        assertThrows(IllegalArgumentException.class, () -> model.embed("  "));
    }
}
