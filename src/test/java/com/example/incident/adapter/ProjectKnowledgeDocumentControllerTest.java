package com.example.incident.adapter;

import com.example.incident.domain.KnowledgeDocument;
import com.example.incident.service.KnowledgeDocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ProjectKnowledgeDocumentControllerTest {
    private static final String TEST_API_KEY = "test-secret";

    @Test
    void createsTextDocumentUnderTheProjectInThePath() throws Exception {
        var service = mock(KnowledgeDocumentService.class);
        var projectId = UUID.fromString("1c0c1549-ddb4-47a2-b80b-267fca802db6");
        var document = new KnowledgeDocument(
                UUID.fromString("44a36b33-4ef3-45be-b4ea-1fab4d97010a"),
                projectId,
                "MySQL connection runbook",
                "Check whether MySQL is reachable.",
                Instant.parse("2026-10-11T00:00:00Z"),
                Instant.parse("2026-10-11T00:00:00Z")
        );
        when(service.create(projectId, document.title(), document.content())).thenReturn(document);

        mvc(service).perform(post("/api/projects/{projectId}/knowledge-documents", projectId)
                        .header("X-API-Key", TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"MySQL connection runbook", "content":"Check whether MySQL is reachable."}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(document.id().toString()))
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.title").value(document.title()));

        verify(service).create(projectId, document.title(), document.content());
    }

    @Test
    void listsDocumentsOnlyForTheProjectInThePath() throws Exception {
        var service = mock(KnowledgeDocumentService.class);
        var projectId = UUID.fromString("1c0c1549-ddb4-47a2-b80b-267fca802db6");
        var document = new KnowledgeDocument(
                UUID.fromString("44a36b33-4ef3-45be-b4ea-1fab4d97010a"),
                projectId,
                "MySQL connection runbook",
                "Check whether MySQL is reachable.",
                Instant.parse("2026-10-11T00:00:00Z"),
                Instant.parse("2026-10-11T00:00:00Z")
        );
        when(service.findByProjectId(projectId)).thenReturn(java.util.List.of(document));

        mvc(service).perform(get("/api/projects/{projectId}/knowledge-documents", projectId)
                        .header("X-API-Key", TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].projectId").value(projectId.toString()))
                .andExpect(jsonPath("$[0].title").value(document.title()));

        verify(service).findByProjectId(projectId);
    }

    private MockMvc mvc(KnowledgeDocumentService service) {
        return standaloneSetup(new ProjectKnowledgeDocumentController(service))
                .addInterceptors(new ApiKeyInterceptor(TEST_API_KEY, new ObjectMapper()))
                .build();
    }
}
