package com.example.incident.adapter;

import com.example.incident.domain.KnowledgeSearchResult;
import com.example.incident.service.KnowledgeDocumentService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectKnowledgeSearchControllerTest {
    @Test
    void searchesWithTheRequestedLimitAndReturnsMatches() {
        var service = mock(KnowledgeDocumentService.class);
        var controller = new ProjectKnowledgeSearchController(service);
        var projectId = UUID.randomUUID();
        var match = new KnowledgeSearchResult(
                UUID.randomUUID(), projectId, 0, "检查超时日志", "bge-m3", 0.82);
        when(service.searchSimilar(projectId, "调用超时", 5)).thenReturn(List.of(match));

        var results = controller.search(
                projectId,
                new ProjectKnowledgeSearchController.KnowledgeSearchRequest("调用超时", 5));

        assertEquals(List.of(match), results);
        verify(service).searchSimilar(projectId, "调用超时", 5);
    }

    @Test
    void defaultsTheResultLimitToThree() {
        var request = new ProjectKnowledgeSearchController.KnowledgeSearchRequest("调用超时", null);

        assertEquals(3, request.limit());
    }
}
