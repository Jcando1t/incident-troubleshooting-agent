package com.example.incident.adapter;

import com.example.incident.service.IncidentOrchestrator;
import com.example.incident.service.IncidentStateConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class IncidentControllerTest {

    @Test
    void returnsConflictWhenIncidentCannotBeApprovedInCurrentState() throws Exception {
        var orchestrator = mock(IncidentOrchestrator.class);
        var id = UUID.randomUUID();

        when(orchestrator.approve(eq(id), eq(true), any()))
                .thenThrow(new IncidentStateConflictException(
                        "Only incidents awaiting approval can be approved"
                ));

        MockMvc mvc = standaloneSetup(new IncidentController(orchestrator)).build();

        mvc.perform(post("/api/incidents/{id}/approval", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"comment\":\"approve\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("INCIDENT_STATE_CONFLICT"))
                .andExpect(jsonPath("$.message")
                        .value("Only incidents awaiting approval can be approved"));
    }
}
