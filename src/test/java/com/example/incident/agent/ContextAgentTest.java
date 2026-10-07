package com.example.incident.agent;

import com.example.incident.domain.Evidence;
import com.example.incident.domain.Incident;
import com.example.incident.domain.IncidentStatus;
import com.example.incident.port.EvidenceSource;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContextAgentTest {

    @Test
    void combinesEvidenceFromAllSourcesAndAddsContextSummary() {
        var prometheus = mock(EvidenceSource.class);
        var loki = mock(EvidenceSource.class);
        var chatModel = mock(ChatModel.class);
        var incident = incident();

        var prometheusEvidence = new Evidence(
                "prometheus",
                "up 指标为 1",
                "prometheus://query?job=incident-platform",
                Instant.now()
        );
        var lokiEvidence = new Evidence(
                "loki",
                "最近日志出现数据库连接失败",
                "loki://query?service=incident-platform",
                Instant.now()
        );

        when(prometheus.collect(any(Incident.class)))
                .thenReturn(List.of(prometheusEvidence));
        when(loki.collect(any(Incident.class)))
                .thenReturn(List.of(lokiEvidence));
        when(chatModel.chat(anyString()))
                .thenReturn("指标正常，但日志显示数据库连接失败");

        var agent = new ContextAgent(
                List.of(prometheus, loki),
                chatModel
        );

        var result = agent.execute(incident, null);

        assertTrue(result.complete());
        assertEquals(3, result.items().size());
        assertEquals("prometheus", result.items().get(0).source());
        assertEquals("loki", result.items().get(1).source());
        assertEquals("context-agent", result.items().get(2).source());

        var promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatModel).chat(promptCaptor.capture());
        assertTrue(promptCaptor.getValue().contains("up 指标为 1"));
        assertTrue(promptCaptor.getValue().contains("最近日志出现数据库连接失败"));
    }

    @Test
    void returnsIncompleteBundleWhenAllSourcesReturnNoEvidence() {
        var prometheus = mock(EvidenceSource.class);
        var loki = mock(EvidenceSource.class);
        var chatModel = mock(ChatModel.class);

        when(prometheus.collect(any(Incident.class))).thenReturn(List.of());
        when(loki.collect(any(Incident.class))).thenReturn(List.of());

        var agent = new ContextAgent(
                List.of(prometheus, loki),
                chatModel
        );

        var result = agent.execute(incident(), null);

        assertFalse(result.complete());
        assertTrue(result.items().isEmpty());
        verify(chatModel, never()).chat(anyString());
    }

    private Incident incident() {
        return new Incident(
                UUID.randomUUID(),
                "Context agent test incident",
                "incident-platform",
                "local",
                null,
                "Test evidence aggregation",
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
