package com.example.incident.service;

import com.example.incident.agent.*;
import com.example.incident.domain.*;
import com.example.incident.port.IncidentRepository;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class IncidentOrchestrator {
    private final IncidentRepository repository;
    private final TriageAgent triageAgent;
    private final ContextAgent contextAgent;
    private final RcaAgent rcaAgent;
    private final RemediationAgent remediationAgent;

    public IncidentOrchestrator(IncidentRepository repository, TriageAgent triageAgent,
                                ContextAgent contextAgent, RcaAgent rcaAgent,
                                RemediationAgent remediationAgent) {
        this.repository = repository; this.triageAgent = triageAgent; this.contextAgent = contextAgent;
        this.rcaAgent = rcaAgent; this.remediationAgent = remediationAgent;
    }

    public Incident create(String title, String service, String environment,
                           Severity severity, String description) {
        var incident = new Incident(UUID.randomUUID(), title, service, environment, severity,
                description, IncidentStatus.RECEIVED, Instant.now(), null, null, null, null, List.of())
                .addAudit("api", "incident.received", title);
        repository.save(incident);
        return analyze(incident.id());
    }

    public Incident analyze(UUID id) {
        var incident = get(id).withStatus(IncidentStatus.ANALYZING)
                .addAudit("orchestrator", "analysis.started", "multi-agent workflow");
        var triage = triageAgent.execute(incident, null);
        var evidence = contextAgent.execute(incident, null);
        var rca = rcaAgent.execute(incident, evidence);
        var remediation = remediationAgent.execute(incident, rca);
        return repository.save(incident.withAnalysis(triage, evidence, rca, remediation)
                .addAudit("orchestrator", "analysis.completed", "awaiting approval"));
    }

    public Incident approve(UUID id, boolean approved, String comment) {
        var incident = get(id);
        var status = approved ? IncidentStatus.APPROVED : IncidentStatus.REJECTED;
        return repository.save(incident.withStatus(status)
                .addAudit("operator", approved ? "remediation.approved" : "remediation.rejected", comment));
    }

    public Incident get(UUID id) { return repository.findById(id).orElseThrow(); }
}
