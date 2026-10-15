package com.example.incident.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.incident.service.IncidentOrchestrator;
import com.example.incident.service.IncidentStateConflictException;
import com.example.incident.service.IncidentNotFoundException;
import com.example.incident.service.ProjectService;
import com.example.incident.service.ProjectNotFoundException;
import com.example.incident.domain.Incident;
import com.example.incident.domain.IncidentStatus;
import com.example.incident.domain.Project;
import com.example.incident.domain.Severity;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.List;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class IncidentControllerTest {

    private static final String TEST_API_KEY = "test-secret";

    @Test
    void listsRecentIncidentsUsingRequestedLimit() throws Exception {
        var orchestrator = mock(IncidentOrchestrator.class);
        when(orchestrator.findRecent(2, 0, null, null, null, null)).thenReturn(List.of());
        MockMvc mvc = secureMvc(orchestrator);

        mvc.perform(get("/api/incidents")
                        .header("X-API-Key", TEST_API_KEY)
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(orchestrator).findRecent(2, 0, null, null, null, null);
    }

    @Test
    void forwardsOffsetForNextPageOfIncidents() throws Exception {
        var orchestrator = mock(IncidentOrchestrator.class);
        when(orchestrator.findRecent(2, 2, null, null, null, null)).thenReturn(List.of());
        MockMvc mvc = secureMvc(orchestrator);

        mvc.perform(get("/api/incidents")
                        .header("X-API-Key", TEST_API_KEY)
                        .param("limit", "2")
                        .param("offset", "2"))
                .andExpect(status().isOk());

        verify(orchestrator).findRecent(2, 2, null, null, null, null);
    }

    @Test
    void forwardsStatusAndServiceFilters() throws Exception {
        var orchestrator = mock(IncidentOrchestrator.class);
        var statusFilter = com.example.incident.domain.IncidentStatus.APPROVED;
        when(orchestrator.findRecent(20, 0, statusFilter, "payment-service", null, null))
                .thenReturn(List.of());
        MockMvc mvc = secureMvc(orchestrator);

        mvc.perform(get("/api/incidents")
                        .header("X-API-Key", TEST_API_KEY)
                        .param("status", "APPROVED")
                        .param("service", "payment-service"))
                .andExpect(status().isOk());

        verify(orchestrator).findRecent(20, 0, statusFilter, "payment-service", null, null);
    }

    @Test
    void forwardsCreationTimeRangeFilters() throws Exception {
        var orchestrator = mock(IncidentOrchestrator.class);
        var from = Instant.parse("2026-10-01T00:00:00Z");
        var to = Instant.parse("2026-10-10T23:59:59Z");
        when(orchestrator.findRecent(20, 0, null, null, from, to))
                .thenReturn(List.of());
        MockMvc mvc = secureMvc(orchestrator);

        mvc.perform(get("/api/incidents")
                        .header("X-API-Key", TEST_API_KEY)
                        .param("createdFrom", "2026-10-01T00:00:00Z")
                        .param("createdTo", "2026-10-10T23:59:59Z"))
                .andExpect(status().isOk());

        verify(orchestrator).findRecent(20, 0, null, null, from, to);
    }

    @Test
    void forwardsProjectFilter() throws Exception {
        var orchestrator = mock(IncidentOrchestrator.class);
        var projectId = UUID.randomUUID();
        when(orchestrator.findRecent(
                projectId, 20, 0, null, null, null, null))
                .thenReturn(List.of());
        MockMvc mvc = secureMvc(orchestrator);

        mvc.perform(get("/api/incidents")
                        .header("X-API-Key", TEST_API_KEY)
                        .param("projectId", projectId.toString()))
                .andExpect(status().isOk());

        verify(orchestrator).findRecent(
                projectId, 20, 0, null, null, null, null);
    }

    @Test
    void returnsNotFoundWhenProjectFilterDoesNotExist() throws Exception {
        var orchestrator = mock(IncidentOrchestrator.class);
        var projectService = mock(ProjectService.class);
        var projectId = UUID.randomUUID();
        when(projectService.get(projectId))
                .thenThrow(new ProjectNotFoundException(projectId));
        var mvc = standaloneSetup(new IncidentController(orchestrator, projectService))
                .addInterceptors(new ApiKeyInterceptor(TEST_API_KEY, new ObjectMapper()))
                .build();

        mvc.perform(get("/api/incidents")
                        .header("X-API-Key", TEST_API_KEY)
                        .param("projectId", projectId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));

        org.mockito.Mockito.verifyNoInteractions(orchestrator);
    }

    @Test
    void rejectsCreationTimeRangeWhenStartIsAfterEnd() throws Exception {
        var orchestrator = mock(IncidentOrchestrator.class);
        MockMvc mvc = secureMvc(orchestrator);

        mvc.perform(get("/api/incidents")
                        .header("X-API-Key", TEST_API_KEY)
                        .param("createdFrom", "2026-10-10T00:00:00Z")
                        .param("createdTo", "2026-10-01T00:00:00Z"))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(orchestrator);
    }

    @Test
    void rejectsRecentIncidentLimitOutsideAllowedRange() throws Exception {
        var orchestrator = mock(IncidentOrchestrator.class);
        MockMvc mvc = secureMvc(orchestrator);

        mvc.perform(get("/api/incidents")
                        .header("X-API-Key", TEST_API_KEY)
                        .param("limit", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNegativeIncidentOffset() throws Exception {
        var orchestrator = mock(IncidentOrchestrator.class);
        MockMvc mvc = secureMvc(orchestrator);

        mvc.perform(get("/api/incidents")
                        .header("X-API-Key", TEST_API_KEY)
                        .param("offset", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundWhenIncidentDoesNotExist() throws Exception {
        var orchestrator = mock(IncidentOrchestrator.class);
        var id = UUID.randomUUID();
        when(orchestrator.get(id)).thenThrow(
                new IncidentNotFoundException("Incident not found: " + id));
        MockMvc mvc = secureMvc(orchestrator);

        mvc.perform(get("/api/incidents/{id}", id)
                        .header("X-API-Key", TEST_API_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INCIDENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Incident not found: " + id));
    }

    @Test
    void returnsConflictWhenIncidentCannotBeApprovedInCurrentState() throws Exception {
        var orchestrator = mock(IncidentOrchestrator.class);
        var id = UUID.randomUUID();

        when(orchestrator.approve(eq(id), eq(true), any()))
                .thenThrow(new IncidentStateConflictException(
                        "Only incidents awaiting approval can be approved"
                ));

        MockMvc mvc = secureMvc(orchestrator);

        mvc.perform(post("/api/incidents/{id}/approval", id)
                        .header("X-API-Key", TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"comment\":\"approve\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("INCIDENT_STATE_CONFLICT"))
                .andExpect(jsonPath("$.message")
                        .value("Only incidents awaiting approval can be approved"));
    }

    @Test
    void rejectsApiRequestWithoutApiKeyBeforeCallingOrchestrator() throws Exception {
        var orchestrator = mock(IncidentOrchestrator.class);
        MockMvc mvc = secureMvc(orchestrator);

        mvc.perform(get("/api/incidents"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        org.mockito.Mockito.verifyNoInteractions(orchestrator);
    }

    @Test
    void requiresProjectIdWhenCreatingIncident() throws Exception {
        var orchestrator = mock(IncidentOrchestrator.class);
        MockMvc mvc = secureMvc(orchestrator);

        mvc.perform(post("/api/incidents")
                        .header("X-API-Key", TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"test","service":"payment-service",
                                 "environment":"test","description":"test"}
                                """))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(orchestrator);
    }

    @Test
    void rejectsIncidentForUnregisteredProject() throws Exception {
        var orchestrator = mock(IncidentOrchestrator.class);
        var projectService = mock(ProjectService.class);
        var projectId = UUID.randomUUID();
        when(projectService.get(projectId)).thenThrow(new ProjectNotFoundException(projectId));
        MockMvc mvc = standaloneSetup(new IncidentController(orchestrator, projectService))
                .addInterceptors(new ApiKeyInterceptor(TEST_API_KEY, new ObjectMapper()))
                .build();

        mvc.perform(post("/api/incidents")
                        .header("X-API-Key", TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","title":"test","service":"payment-service",
                                 "environment":"test","description":"test"}
                                """.formatted(projectId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));

        org.mockito.Mockito.verifyNoInteractions(orchestrator);
    }

    @Test
    void createsIncidentUsingRegisteredProjectId() throws Exception {
        var orchestrator = mock(IncidentOrchestrator.class);
        var projectService = mock(ProjectService.class);
        var projectId = UUID.randomUUID();
        var incident = new Incident(
                UUID.randomUUID(),
                projectId,
                "test incident",
                "payment-service",
                "test",
                Severity.P2,
                "database timeout",
                IncidentStatus.RECEIVED,
                Instant.parse("2026-10-11T00:00:00Z"),
                null,
                null,
                null,
                null,
                List.of()
        );
        when(projectService.get(projectId)).thenReturn(
                new Project(projectId, "Payments", Instant.parse("2026-10-10T00:00:00Z")));
        when(orchestrator.create(
                projectId, "test incident", "payment-service", "test",
                Severity.P2, "database timeout"))
                .thenReturn(incident);
        MockMvc mvc = standaloneSetup(new IncidentController(orchestrator, projectService))
                .addInterceptors(new ApiKeyInterceptor(TEST_API_KEY, new ObjectMapper()))
                .build();

        mvc.perform(post("/api/incidents")
                        .header("X-API-Key", TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","title":"test incident",
                                 "service":"payment-service","environment":"test",
                                 "severity":"P2","description":"database timeout"}
                                """.formatted(projectId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.projectId").value(projectId.toString()));

        verify(orchestrator).create(
                projectId, "test incident", "payment-service", "test",
                Severity.P2, "database timeout");
    }

    private MockMvc secureMvc(IncidentOrchestrator orchestrator) {
        return standaloneSetup(new IncidentController(
                        orchestrator, mock(ProjectService.class)))
                .addInterceptors(new ApiKeyInterceptor(TEST_API_KEY, new ObjectMapper()))
                .build();
    }
}
