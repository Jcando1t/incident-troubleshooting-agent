package com.example.incident.adapter;

import com.example.incident.port.TextEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

@Component
public class OllamaEmbeddingModel implements TextEmbeddingModel {
    private final RestClient restClient;
    private final String model;

    public OllamaEmbeddingModel(
            RestClient.Builder restClientBuilder,
            @Value("${app.rag.embedding.base-url}") String baseUrl,
            @Value("${app.rag.embedding.model}") String model) {
        this(restClientBuilder, baseUrl, model, Duration.ofSeconds(3), Duration.ofSeconds(15));
    }

    @Autowired
    public OllamaEmbeddingModel(
            RestClient.Builder restClientBuilder,
            @Value("${app.rag.embedding.base-url}") String baseUrl,
            @Value("${app.rag.embedding.model}") String model,
            @Value("${app.rag.embedding.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.rag.embedding.read-timeout:15s}") Duration readTimeout) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.model = model;
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public List<Float> embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text to embed cannot be blank");
        }

        try {
            var response = restClient.post()
                    .uri("/api/embed")
                    .body(new EmbedRequest(model, text))
                    .retrieve()
                    .body(EmbedResponse.class);

            if (response == null || response.embeddings() == null || response.embeddings().isEmpty()
                    || response.embeddings().getFirst() == null || response.embeddings().getFirst().isEmpty()) {
                throw new IllegalStateException("Ollama returned no embedding vector");
            }
            return response.embeddings().getFirst();
        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "Could not get an embedding from Ollama. Check that Ollama is running and model '"
                            + model + "' is installed.",
                    exception
            );
        }
    }

    private record EmbedRequest(String model, String input) {}
    private record EmbedResponse(List<List<Float>> embeddings) {}
}
