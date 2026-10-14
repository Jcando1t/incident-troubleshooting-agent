package com.example.incident.adapter;

import com.example.incident.domain.Evidence;
import com.example.incident.domain.Incident;
import com.example.incident.port.EvidenceSource;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class LokiEvidenceSource implements EvidenceSource {
    private static final Logger log =
            LoggerFactory.getLogger(LokiEvidenceSource.class);

    private static final int QUERY_LIMIT = 20;
    private static final int MAX_SUMMARY_LENGTH = 4_000;
    private static final int MAX_LINE_LENGTH = 500;

    private final RestClient client;
    private final String baseUrl;

    public LokiEvidenceSource(
            RestClient.Builder builder,
            @Value("${app.observability.loki-url}") String baseUrl) {
        this(builder, baseUrl, Duration.ofSeconds(3), Duration.ofSeconds(5));
    }

    @Autowired
    public LokiEvidenceSource(
            RestClient.Builder builder,
            @Value("${app.observability.loki-url}") String baseUrl,
            @Value("${app.observability.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.observability.read-timeout:5s}") Duration readTimeout) {

        var httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();

        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        this.baseUrl = baseUrl;
        this.client = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String name() {
        return "loki";
    }

    @Override
    public List<Evidence> collect(Incident incident) {
        if (incident == null
                || incident.service() == null
                || incident.service().isBlank()) {
            return List.of();
        }

        var service = incident.service();
        var query = "{service_name=\"" + escapeLogqlLabel(service) + "\"}";

        try {
            var queryUri = UriComponentsBuilder
                    .fromUriString(baseUrl)
                    .path("/loki/api/v1/query_range")
                    .queryParam("query", query)
                    .queryParam("limit", QUERY_LIMIT)
                    .build()
                    .encode()
                    .toUri();

            JsonNode response = client.get()
                    .uri(queryUri)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null
                    || !"success".equals(response.path("status").asText())) {
                return List.of();
            }

            var streams = response.path("data").path("result");
            if (!streams.isArray() || streams.isEmpty()) {
                return List.of();
            }

            var lines = extractLogLines(streams);
            if (lines.isEmpty()) {
                return List.of();
            }

            var summary = "Loki 查询到服务 "
                    + service
                    + " 最近 "
                    + lines.size()
                    + " 条日志：\n"
                    + truncate(String.join("\n", lines), MAX_SUMMARY_LENGTH);

            return List.of(new Evidence(
                    name(),
                    summary,
                    "loki://query?service=" + service,
                    Instant.now()
            ));
        } catch (RestClientException ex) {
            log.warn(
                    "Loki 查询失败，service={}: {}",
                    service,
                    ex.getMessage()
            );
            return List.of();
        }
    }

    private List<String> extractLogLines(JsonNode streams) {
        var lines = new ArrayList<String>();

        for (JsonNode stream : streams) {
            var values = stream.path("values");
            if (!values.isArray()) {
                continue;
            }

            for (JsonNode value : values) {
                if (!value.isArray() || value.size() < 2) {
                    continue;
                }

                lines.add(truncate(value.get(1).asText(), MAX_LINE_LENGTH));
                if (lines.size() >= QUERY_LIMIT) {
                    return lines;
                }
            }
        }

        return lines;
    }

    private String escapeLogqlLabel(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
