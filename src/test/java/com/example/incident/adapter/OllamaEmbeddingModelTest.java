package com.example.incident.adapter;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class OllamaEmbeddingModelTest {
    @Test
    void requestsAnEmbeddingFromOllamaAndReturnsTheVector() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var model = new OllamaEmbeddingModel(builder, "http://localhost:11434", "bge-m3");

        server.expect(requestTo("http://localhost:11434/api/embed"))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"model":"bge-m3","input":"MySQL 连接失败"}
                        """))
                .andRespond(withSuccess("""
                        {"model":"bge-m3","embeddings":[[0.1,0.2,0.3]]}
                        """, MediaType.APPLICATION_JSON));

        assertEquals("bge-m3", model.modelName());
        assertEquals(List.of(0.1f, 0.2f, 0.3f), model.embed("MySQL 连接失败"));
        server.verify();
    }

    @Test
    void rejectsBlankTextWithoutCallingOllama() {
        var model = new OllamaEmbeddingModel(RestClient.builder(), "http://localhost:11434", "bge-m3");

        assertThrows(IllegalArgumentException.class, () -> model.embed("  "));
    }
}
