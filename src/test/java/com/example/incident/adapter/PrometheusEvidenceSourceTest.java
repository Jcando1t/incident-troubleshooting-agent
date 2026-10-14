package com.example.incident.adapter;

import com.example.incident.domain.Incident;
import com.example.incident.domain.IncidentStatus;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrometheusEvidenceSourceTest {

    @Test
    void convertsPrometheusResultIntoEvidence() throws IOException {
        var querySeen = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/v1/query", exchange -> {
            querySeen.set(exchange.getRequestURI().getQuery());
            respond(exchange, 200, """
                    {
                      "status": "success",
                      "data": {
                        "resultType": "vector",
                        "result": [
                          {
                            "metric": {
                              "job": "incident-platform"
                            },
                            "value": ["1710000000", "1"]
                          },
                          {
                            "metric": {
                              "job": "incident-platform"
                            },
                            "value": ["1710000000", "1"]
                          }
                        ]
                      }
                    }
                    """);
        });
        server.start();

        try {
            var source = new PrometheusEvidenceSource(
                    RestClient.builder(),
                    "http://localhost:" + server.getAddress().getPort()
            );

            var evidence = source.collect(incident());

            assertEquals(1, evidence.size());
            assertEquals("prometheus", evidence.get(0).source());
            assertTrue(evidence.get(0).summary().contains("2 个目标"));
            assertTrue(evidence.get(0).summary().contains("当前值为 1"));
            assertEquals(
                    "prometheus://query?job=incident-platform",
                    evidence.get(0).reference()
            );
            assertTrue(querySeen.get().contains("up"));
            assertTrue(querySeen.get().contains("job"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void returnsNoEvidenceWhenPrometheusReturnsAnError() throws IOException {
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/v1/query", exchange ->
                respond(exchange, 500, "Prometheus unavailable"));
        server.start();

        try {
            var source = new PrometheusEvidenceSource(
                    RestClient.builder(),
                    "http://localhost:" + server.getAddress().getPort()
            );

            assertTrue(source.collect(incident()).isEmpty());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void returnsNoEvidenceWhenPrometheusReadTimesOut() throws IOException {
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/v1/query", exchange -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();

        try {
            var source = new PrometheusEvidenceSource(
                    RestClient.builder(),
                    "http://localhost:" + server.getAddress().getPort(),
                    Duration.ofSeconds(1),
                    Duration.ofMillis(100)
            );

            assertTrue(source.collect(incident()).isEmpty());
        } finally {
            server.stop(0);
        }
    }

    private Incident incident() {
        return new Incident(
                UUID.randomUUID(),
                "Prometheus test incident",
                "incident-platform",
                "local",
                null,
                "Test Prometheus evidence collection",
                IncidentStatus.ANALYZING,
                Instant.now(),
                null,
                null,
                null,
                null,
                List.of()
        );
    }

    private static void respond(
            com.sun.net.httpserver.HttpExchange exchange,
            int status,
            String body
    ) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
