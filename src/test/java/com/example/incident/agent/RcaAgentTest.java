package com.example.incident.agent;

import com.example.incident.domain.Evidence;
import com.example.incident.domain.EvidenceBundle;
import com.example.incident.domain.Incident;
import com.example.incident.domain.IncidentStatus;
import com.example.incident.domain.RcaResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RcaAgentTest {

    private Incident incident() {
        return new Incident(
                UUID.randomUUID(),
                "Payment service errors",
                "payment-service",
                "production",
                null,
                "5xx error rate reached 18%",
                IncidentStatus.ANALYZING,
                Instant.now(),
                null,
                null,
                null,
                null,
                List.of()
        );
    }

    private EvidenceBundle evidence() {
        return new EvidenceBundle(
                List.of(
                        new Evidence(
                                "demo-observability",
                                "error rate elevated",
                                "demo://metrics/payment-service",
                                Instant.now()
                        )
                ),
                true
        );
    }

    @Test
    void parsesRcaJsonAndFiltersUnknownEvidenceReferences() {
        var chatModel = mock(ChatModel.class);

        when(chatModel.chat(anyString())).thenReturn("""
                {
                  "primaryCause": "Insufficient evidence to confirm the root cause",
                  "contributingFactors": [
                    "5xx error rate reached 18%",
                    "Only demo observability data is available"
                  ],
                  "evidenceReferences": [
                    "demo://metrics/payment-service",
                    "fake://unknown-reference"
                  ],
                  "confidence": 0.2
                }
                """);

        var agent = new RcaAgent(chatModel, new ObjectMapper());

        RcaResult result = agent.execute(incident(), evidence());

        assertEquals(
                "Insufficient evidence to confirm the root cause",
                result.primaryCause()
        );
        assertEquals(2, result.contributingFactors().size());
        assertEquals(
                List.of("demo://metrics/payment-service"),
                result.evidenceReferences()
        );
        assertEquals(0.2, result.confidence());
    }

    @Test
    void rejectsInvalidJson() {
        var chatModel = mock(ChatModel.class);

        when(chatModel.chat(anyString()))
                .thenReturn("this is not valid json");

        var agent = new RcaAgent(chatModel, new ObjectMapper());

        assertThrows(
                IllegalStateException.class,
                () -> agent.execute(incident(), evidence())
        );
    }
}