package com.example.incident.service;

import com.example.incident.domain.KnowledgeDocument;
import com.example.incident.domain.KnowledgeDocumentChunk;
import com.example.incident.port.KnowledgeDocumentRepository;
import com.example.incident.port.ProjectRepository;
import com.example.incident.port.TextEmbeddingModel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeDocumentServiceTest {
    @Test
    void searchesOnlyTheProjectChunksAndRanksByCosineSimilarity() {
        var projectRepository = mock(ProjectRepository.class);
        var repository = mock(KnowledgeDocumentRepository.class);
        var embeddingModel = mock(TextEmbeddingModel.class);
        var projectId = UUID.randomUUID();
        var documentId = UUID.randomUUID();
        var now = Instant.parse("2026-10-12T00:00:00Z");
        var document = new KnowledgeDocument(documentId, projectId, "runbook", "text", now, now);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(
                new com.example.incident.domain.Project(projectId, "test", now)));
        when(repository.findChunksByProjectId(projectId)).thenReturn(List.of(
                new KnowledgeDocumentChunk(UUID.randomUUID(), documentId, projectId, 0,
                        "similar", "bge-m3", List.of(1.0f, 0.0f), now),
                new KnowledgeDocumentChunk(UUID.randomUUID(), documentId, projectId, 1,
                        "different", "bge-m3", List.of(0.0f, 1.0f), now)
        ));
        when(embeddingModel.modelName()).thenReturn("bge-m3");
        when(embeddingModel.embed("query")).thenReturn(List.of(1.0f, 0.0f));

        var service = new KnowledgeDocumentService(
                repository,
                new ProjectService(projectRepository),
                new KnowledgeTextChunker(),
                embeddingModel);

        var results = service.searchSimilar(projectId, "query", 1);

        assertEquals(1, results.size());
        assertEquals("similar", results.getFirst().content());
        assertEquals(1.0, results.getFirst().score());
        verify(repository).findChunksByProjectId(projectId);
    }
}
