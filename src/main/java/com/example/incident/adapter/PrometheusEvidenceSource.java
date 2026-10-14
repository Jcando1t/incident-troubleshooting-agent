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
import java.util.List;

@Component
public class PrometheusEvidenceSource implements EvidenceSource {
    private static final Logger log =
            LoggerFactory.getLogger(PrometheusEvidenceSource.class);

    private final RestClient client;
    private final String baseUrl;

    public PrometheusEvidenceSource(
            RestClient.Builder builder,
            @Value("${app.observability.prometheus-url}") String baseUrl) {
        this(builder, baseUrl, Duration.ofSeconds(3), Duration.ofSeconds(5));
    }

    @Autowired
    public PrometheusEvidenceSource(
            RestClient.Builder builder,
            @Value("${app.observability.prometheus-url}") String baseUrl,
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
        return "prometheus";
    }

    @Override
    public List<Evidence> collect(Incident incident) {
        if (incident == null
                || incident.service() == null
                || incident.service().isBlank()) {
            return List.of();
        }

        var service = escapePrometheusLabel(incident.service());
        var query = "up{job=\"" + service + "\"}";

        try {
            var queryUri = UriComponentsBuilder
                    .fromUriString(baseUrl)
                    .path("/api/v1/query")
                    .queryParam("query", query)
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

            var results = response.path("data").path("result");

            if (!results.isArray() || results.isEmpty()) {
                return List.of();
            }

            var firstResult = results.get(0);
            var value = firstResult.path("value");

            var currentValue = value.isArray() && value.size() > 1
                    ? value.get(1).asText()
                    : "unknown";

            var summary = "Prometheus 检测到服务 "
                    + incident.service()
                    + " 有 "
                    + results.size()
                    + " 个目标，up 指标当前值为 "
                    + currentValue;

            return List.of(new Evidence(
                    name(),
                    summary,
                    "prometheus://query?job=" + incident.service(),
                    Instant.now()
            ));
        } catch (RestClientException ex) {
            log.warn(
                    "Prometheus 查询失败，service={}: {}",
                    incident.service(),
                    ex.getMessage()
            );
            return List.of();
        }
    }

    private String escapePrometheusLabel(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
