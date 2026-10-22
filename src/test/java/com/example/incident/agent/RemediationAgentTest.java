package com.example.incident.agent;

import com.example.incident.domain.Incident;
import com.example.incident.domain.IncidentStatus;
import com.example.incident.domain.RcaResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RemediationAgentTest {

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

    private RcaResult rca() {
        return new RcaResult(
                "Insufficient evidence",
                List.of(),
                List.of(),
                0.2
        );
    }

    @Test
    void highRiskStepRequiresApproval() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.chat(anyString())).thenReturn("""
                {
                  "steps": [
                    {
                      "description": "Verify metrics",
                      "command": "{{PLACEHOLDER:VERIFY_METRICS}}",
                      "rollbackCommand": "{{PLACEHOLDER:NOOP_ROLLBACK}}",
                      "risk": "LOW"
                    },
                    {
                      "description": "Rollback deployment",
                      "command": "{{PLACEHOLDER:ROLLBACK_DEPLOYMENT}}",
                      "rollbackCommand": "{{PLACEHOLDER:REAPPLY_DEPLOYMENT}}",
                      "risk": "HIGH"
                    }
                  ]
                }
                """);

        var agent = new RemediationAgent(chatModel, new ObjectMapper());

        var plan = agent.execute(incident(), rca());
        var promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatModel).chat(promptCaptor.capture());

        assertEquals(2, plan.steps().size());
        assertEquals(true, plan.requiresApproval());
        assertTrue(promptCaptor.getValue().contains(
                "command 和 rollbackCommand 的值必须且只能是一个完整占位符"));
        assertTrue(promptCaptor.getValue().contains(
                "\"command\": \"{{PLACEHOLDER:VERIFY_DATABASE_MIGRATION_STATE}}\""));
        assertTrue(promptCaptor.getValue().contains("严禁输出任何实际 shell、SQL、kubectl"));
    }

    @Test
    void unsafeCommandIsRejected() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.chat(anyString())).thenReturn("""
                {
                  "steps": [
                    {
                      "description": "Run an unsafe command",
                      "command": "kubectl delete deployment payment-service",
                      "rollbackCommand": "{{PLACEHOLDER:ROLLBACK}}",
                      "risk": "HIGH"
                    }
                  ]
                }
                """);

        var agent = new RemediationAgent(chatModel, new ObjectMapper());

        assertThrows(
                IllegalStateException.class,
                () -> agent.execute(incident(), rca())
        );
    }
}
