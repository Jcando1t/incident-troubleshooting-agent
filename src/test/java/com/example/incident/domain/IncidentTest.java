package com.example.incident.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IncidentTest {

    @Test
    void withAnalysisCopiesTriageSeverityToTopLevel() {
        var incident = new Incident(
                UUID.randomUUID(),
                "Test incident",
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

        var triage = new TriageResult(
                Severity.P0,
                List.of("payment-service"),
                "payment-service affected",
                0.9
        );

        var analyzed = incident.withAnalysis(
                triage,
                null,
                null,
                null
        );

        assertEquals(Severity.P0, analyzed.severity());
        assertEquals(Severity.P0, analyzed.triage().severity());
    }
}