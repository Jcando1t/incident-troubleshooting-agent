package com.example.incident.service;

import com.example.incident.agent.ContextAgent;
import com.example.incident.agent.RcaAgent;
import com.example.incident.agent.RemediationAgent;
import com.example.incident.agent.TriageAgent;
import com.example.incident.domain.Evidence;
import com.example.incident.domain.EvidenceBundle;
import com.example.incident.domain.Incident;
import com.example.incident.domain.IncidentStatus;
import com.example.incident.domain.RcaResult;
import com.example.incident.domain.RemediationPlan;
import com.example.incident.domain.RemediationStep;
import com.example.incident.domain.RiskLevel;
import com.example.incident.domain.Severity;
import com.example.incident.domain.TriageResult;
import com.example.incident.port.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IncidentOrchestratorTest {

    @Test
    void passesAnalysisContextToRemediationAndPersistsCompleteIncident() {
        var repository = mock(IncidentRepository.class);
        var triageAgent = mock(TriageAgent.class);
        var contextAgent = mock(ContextAgent.class);
        var rcaAgent = mock(RcaAgent.class);
        var remediationAgent = mock(RemediationAgent.class);

        var id = UUID.randomUUID();

        var initialIncident = new Incident(
                id,
                "Payment service errors",
                "payment-service",
                "production",
                null,
                "5xx error rate reached 18%",
                IncidentStatus.RECEIVED,
                Instant.now(),
                null,
                null,
                null,
                null,
                List.of()
        );

        var triage = new TriageResult(
                Severity.P1,
                List.of("payment-service"),
                "payment-service is affected",
                0.85
        );

        var evidence = new EvidenceBundle(
                List.of(new Evidence(
                        "test-observability",
                        "error rate elevated",
                        "test://metrics/payment-service",
                        Instant.now()
                )),
                true
        );

        var rca = new RcaResult(
                "Insufficient evidence",
                List.of("Only one observation source is available"),
                List.of("test://metrics/payment-service"),
                0.2
        );

        var remediation = new RemediationPlan(
                List.of(new RemediationStep(
                        "Verify metrics",
                        "{{PLACEHOLDER:VERIFY_METRICS}}",
                        "{{PLACEHOLDER:NOOP_ROLLBACK}}",
                        RiskLevel.LOW
                )),
                false
        );

        when(repository.findById(id))
                .thenReturn(Optional.of(initialIncident));

        when(repository.save(any(Incident.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(triageAgent.execute(
                any(Incident.class),
                (Void) isNull()
        )).thenReturn(triage);

        when(contextAgent.execute(
                any(Incident.class),
                (Void) isNull()
        )).thenReturn(evidence);

        when(rcaAgent.execute(
                any(Incident.class),
                eq(evidence)
        )).thenReturn(rca);

        when(remediationAgent.execute(
                any(Incident.class),
                eq(rca)
        )).thenReturn(remediation);

        var orchestrator = new IncidentOrchestrator(
                repository,
                triageAgent,
                contextAgent,
                rcaAgent,
                remediationAgent
        );

        var result = orchestrator.analyze(id);

        var remediationIncident = ArgumentCaptor.forClass(Incident.class);

        verify(remediationAgent).execute(
                remediationIncident.capture(),
                eq(rca)
        );

        var contextSeenByRemediation = remediationIncident.getValue();

        assertEquals(triage, contextSeenByRemediation.triage());
        assertEquals(evidence, contextSeenByRemediation.evidence());
        assertEquals(rca, contextSeenByRemediation.rca());

        assertEquals(Severity.P1, result.severity());
        assertEquals(IncidentStatus.AWAITING_APPROVAL, result.status());
        assertEquals(triage, result.triage());
        assertEquals(evidence, result.evidence());
        assertEquals(rca, result.rca());
        assertEquals(remediation, result.remediation());
    }
}