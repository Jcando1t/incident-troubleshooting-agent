package com.example.incident.agent;

import com.example.incident.domain.Evidence;
import com.example.incident.domain.Incident;
import com.example.incident.domain.IncidentStatus;
import com.example.incident.port.EvidenceSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvestigationAgentTest {

    @Test
    void choosesReadOnlyToolsAndStopsAfterEvidenceIsCollected() {
        var prometheus = source("prometheus", "up=1");
        var loki = source("loki", "database connection failed");
        var chatModel = mock(ChatModel.class);
        when(chatModel.chat(anyString()))
                .thenReturn(
                        "{\"tool\":\"prometheus\",\"reason\":\"check service health\"}",
                        "{\"tool\":\"loki\",\"reason\":\"inspect recent errors\"}",
                        "{\"tool\":\"STOP\",\"reason\":\"enough evidence\"}",
                        "指标正常，但日志显示数据库连接失败"
                );

        var agent = new InvestigationAgent(
                List.of(prometheus, loki), chatModel, new ObjectMapper(), 3);

        var result = agent.execute(incident(), null);

        assertTrue(result.complete());
        assertEquals(3, result.items().size());
        assertEquals("prometheus", result.items().get(0).source());
        assertEquals("loki", result.items().get(1).source());
        assertEquals("context-agent", result.items().get(2).source());
        verify(prometheus).collect(any(Incident.class));
        verify(loki).collect(any(Incident.class));
    }

    @Test
    void stopsSafelyWhenModelSelectsUnknownTool() {
        var prometheus = source("prometheus", "up=1");
        var chatModel = mock(ChatModel.class);
        when(chatModel.chat(anyString()))
                .thenReturn("{\"tool\":\"shell\",\"reason\":\"unsafe\"}");

        var agent = new InvestigationAgent(
                List.of(prometheus), chatModel, new ObjectMapper(), 3);

        var result = agent.execute(incident(), null);

        assertFalse(result.complete());
        assertTrue(result.items().isEmpty());
        verify(prometheus, never()).collect(any(Incident.class));
    }

    private EvidenceSource source(String name, String summary) {
        var source = mock(EvidenceSource.class);
        when(source.name()).thenReturn(name);
        when(source.collect(any(Incident.class))).thenReturn(List.of(
                new Evidence(name, summary, name + "://test", Instant.now())));
        return source;
    }

    private Incident incident() {
        return new Incident(
                UUID.randomUUID(),
                "Investigation agent test incident",
                "incident-platform",
                "local",
                null,
                "Test bounded tool selection",
                IncidentStatus.ANALYZING,
                Instant.now(),
                null,
                null,
                null,
                null,
                List.of()
        );
    }
}
