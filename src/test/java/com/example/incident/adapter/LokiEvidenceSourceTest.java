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

class LokiEvidenceSourceTest {

    @Test
    void convertsLokiStreamsIntoEvidence() throws IOException {
        var querySeen = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/loki/api/v1/query_range", exchange -> {
            querySeen.set(exchange.getRequestURI().getQuery());
            respond(exchange, 200, """
                    {
                      "status": "success",
                      "data": {
                        "resultType": "streams",
                        "result": [
                          {
                            "stream": {
                              "service_name": "incident-platform"
                            },
                            "values": [
                              ["1710000000000000000", "INFO request completed"],
                              ["1710000001000000000", "ERROR database connection failed"]
                            ]
                          }
                        ]
                      }
                    }
                    """);
        });
        server.start();

        try {
            var source = new LokiEvidenceSource(
                    RestClient.builder(),
                    "http://localhost:" + server.getAddress().getPort()
            );

            var evidence = source.collect(incident());

            assertEquals(1, evidence.size());
            assertEquals("loki", evidence.get(0).source());
            assertTrue(evidence.get(0).summary().contains("ERROR database connection failed"));
            assertEquals(
                    "loki://query?service=incident-platform",
                    evidence.get(0).reference()
            );
            assertTrue(querySeen.get().contains("service_name"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void returnsNoEvidenceWhenLokiReturnsAnError() throws IOException {
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/loki/api/v1/query_range", exchange ->
                respond(exchange, 500, "Loki unavailable"));
        server.start();

        try {
            var source = new LokiEvidenceSource(
                    RestClient.builder(),
                    "http://localhost:" + server.getAddress().getPort()
            );

            assertTrue(source.collect(incident()).isEmpty());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void returnsNoEvidenceWhenLokiReadTimesOut() throws IOException {
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/loki/api/v1/query_range", exchange -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();

        try {
            var source = new LokiEvidenceSource(
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
                "Loki test incident",
                "incident-platform",
                "local",
                null,
                "Test Loki evidence collection",
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
