package com.example.incident.service;

import com.example.incident.domain.Evidence;
import com.example.incident.domain.EvidenceBundle;
import com.example.incident.domain.Incident;
import com.example.incident.domain.IncidentStatus;
import com.example.incident.domain.KnowledgeSearchResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectKnowledgeRetrieverTest {
    @Test
    void addsRelevantProjectKnowledgeAsCitableEvidence() {
        var service = mock(KnowledgeDocumentService.class);
        var retriever = new ProjectKnowledgeRetriever(service);
        var projectId = UUID.randomUUID();
        var documentId = UUID.randomUUID();
        var incident = new Incident(UUID.randomUUID(), projectId, "Database timeout", "api", "test",
                null, "MySQL connection timeout", IncidentStatus.RECEIVED, Instant.now(),
                null, null, null, null, List.of());
        when(service.searchSimilar(projectId,
                "Database timeout\napi\nMySQL connection timeout", 3))
                .thenReturn(List.of(new KnowledgeSearchResult(
                        documentId, projectId, 2, "检查连接池", "bge-m3", 0.91)));

        var enriched = retriever.enrich(incident, new EvidenceBundle(List.of(), true));

        assertEquals(1, enriched.items().size());
        assertEquals("project-knowledge", enriched.items().getFirst().source());
        assertEquals("knowledge://projects/" + projectId + "/documents/" + documentId + "/chunks/2",
                enriched.items().getFirst().reference());
        assertEquals(true, enriched.complete());
    }

    @Test
    void keepsExistingEvidenceWhenTheProjectHasNoMatchingKnowledge() {
        var service = mock(KnowledgeDocumentService.class);
        var retriever = new ProjectKnowledgeRetriever(service);
        var projectId = UUID.randomUUID();
        var incident = new Incident(UUID.randomUUID(), projectId, "Database timeout", "api", "test",
                null, "MySQL connection timeout", IncidentStatus.RECEIVED, Instant.now(),
                null, null, null, null, List.of());
        var existing = new EvidenceBundle(List.of(new Evidence(
                "prometheus", "up=1", "prometheus://query", Instant.now())), true);
        when(service.searchSimilar(projectId,
                "Database timeout\napi\nMySQL connection timeout", 3)).thenReturn(List.of());

        var result = retriever.enrich(incident, existing);

        assertEquals(existing, result);
    }
}
