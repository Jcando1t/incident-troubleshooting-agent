package com.example.incident.adapter;

import com.example.incident.domain.Project;
import com.example.incident.service.ProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ProjectControllerTest {
    private static final String TEST_API_KEY = "test-secret";

    @Test
    void createsProjectAndReturnsItsStableId() throws Exception {
        var projectService = mock(ProjectService.class);
        var project = new Project(
                UUID.fromString("52e50924-a446-44f9-84f9-3ee9c4746a18"),
                "求职 Agent",
                Instant.parse("2026-10-11T00:00:00Z")
        );
        when(projectService.create("求职 Agent")).thenReturn(project);

        mvc(projectService).perform(post("/api/projects")
                        .header("X-API-Key", TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"求职 Agent\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(project.id().toString()))
                .andExpect(jsonPath("$.name").value("求职 Agent"));

        verify(projectService).create("求职 Agent");
    }

    @Test
    void listsRegisteredProjects() throws Exception {
        var projectService = mock(ProjectService.class);
        var project = new Project(
                UUID.fromString("52e50924-a446-44f9-84f9-3ee9c4746a18"),
                "求职 Agent",
                Instant.parse("2026-10-11T00:00:00Z")
        );
        when(projectService.findAll()).thenReturn(List.of(project));

        mvc(projectService).perform(get("/api/projects")
                        .header("X-API-Key", TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(project.id().toString()))
                .andExpect(jsonPath("$[0].name").value("求职 Agent"));
    }

    private MockMvc mvc(ProjectService projectService) {
        return standaloneSetup(new ProjectController(projectService))
                .addInterceptors(new ApiKeyInterceptor(TEST_API_KEY, new ObjectMapper()))
                .build();
    }
}
