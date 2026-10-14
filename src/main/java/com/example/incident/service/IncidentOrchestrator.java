package com.example.incident.service;

import com.example.incident.agent.*;
import com.example.incident.domain.*;
import com.example.incident.port.IncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class IncidentOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(IncidentOrchestrator.class);

    private final IncidentRepository repository;
    private final TriageAgent triageAgent;
    private final ContextAgent contextAgent;
    private final RcaAgent rcaAgent;
    private final RemediationAgent remediationAgent;
    private final ProjectKnowledgeRetriever knowledgeRetriever;

    public IncidentOrchestrator(
            IncidentRepository repository,
            TriageAgent triageAgent,
            ContextAgent contextAgent,
            RcaAgent rcaAgent,
            RemediationAgent remediationAgent) {
        this(repository, triageAgent, contextAgent, rcaAgent, remediationAgent, null);
    }

    @Autowired
    public IncidentOrchestrator(
            IncidentRepository repository,
            TriageAgent triageAgent,
            ContextAgent contextAgent,
            RcaAgent rcaAgent,
            RemediationAgent remediationAgent,
            ProjectKnowledgeRetriever knowledgeRetriever) {
        this.repository = repository;
        this.triageAgent = triageAgent;
        this.contextAgent = contextAgent;
        this.rcaAgent = rcaAgent;
        this.remediationAgent = remediationAgent;
        this.knowledgeRetriever = knowledgeRetriever;
    }

    public Incident create(
            UUID projectId,
            String title,
            String service,
            String environment,
            Severity severity,
            String description) {

        var incident = new Incident(
                UUID.randomUUID(),
                projectId,
                title,
                service,
                environment,
                severity,
                description,
                IncidentStatus.RECEIVED,
                Instant.now(),
                null,
                null,
                null,
                null,
                List.of()
        ).addAudit("api", "incident.received", title);

        repository.save(incident);
        return analyze(incident.id());
    }

    public Incident analyze(UUID id) {
        var incident = get(id)
                .withStatus(IncidentStatus.ANALYZING)
                .addAudit(
                        "orchestrator",
                        "analysis.started",
                        "multi-agent workflow"
                );

        TriageResult triage = null;
        EvidenceBundle evidence = null;
        RcaResult rca = null;
        RemediationPlan remediation;
        var stage = "triage";

        try {
            triage = triageAgent.execute(incident, null);

            stage = "context collection";
            var collectedEvidence = contextAgent.execute(incident, null);
            evidence = knowledgeRetriever == null
                    ? collectedEvidence
                    : knowledgeRetriever.enrich(incident, collectedEvidence);

            stage = "root-cause analysis";
            rca = rcaAgent.execute(incident, evidence);

            var incidentWithAnalysisContext = new Incident(
                    incident.id(),
                    incident.projectId(),
                    incident.title(),
                    incident.service(),
                    incident.environment(),
                    triage.severity(),
                    incident.description(),
                    incident.status(),
                    incident.createdAt(),
                    triage,
                    evidence,
                    rca,
                    incident.remediation(),
                    incident.audit()
            );

            stage = "remediation planning and safety validation";
            remediation = remediationAgent.execute(
                    incidentWithAnalysisContext,
                    rca
            );
        } catch (RuntimeException ex) {
            log.warn("Incident analysis failed: incidentId={}, stage={}, errorType={}",
                    id, stage, ex.getClass().getSimpleName());
            var escalated = new Incident(
                    incident.id(),
                    incident.projectId(),
                    incident.title(),
                    incident.service(),
                    incident.environment(),
                    triage == null ? incident.severity() : triage.severity(),
                    incident.description(),
                    IncidentStatus.ESCALATED,
                    incident.createdAt(),
                    triage,
                    evidence,
                    rca,
                    null,
                    incident.audit()
            ).addAudit(
                    "orchestrator",
                    "analysis.failed",
                    failureDetail(stage, ex)
            );

            return repository.save(escalated);
        }

        return repository.save(
                incident.withAnalysis(
                        triage,
                        evidence,
                        rca,
                        remediation
                ).addAudit(
                        "orchestrator",
                        "analysis.completed",
                        "awaiting approval"
                )
        );
    }

    private String failureDetail(String stage, RuntimeException exception) {
        if ("remediation planning and safety validation".equals(stage)
                && exception.getMessage() != null
                && !exception.getMessage().isBlank()) {
            return exception.getMessage();
        }
        return "Analysis stopped during " + stage
                + " (" + exception.getClass().getSimpleName() + ")";
    }

    public Incident approve(UUID id, boolean approved, String comment) {
        var incident = get(id);
        if (incident.status() != IncidentStatus.AWAITING_APPROVAL) {
            throw new IncidentStateConflictException(
                    "Only incidents awaiting approval can be approved"
            );
        }

        var status = approved
                ? IncidentStatus.APPROVED
                : IncidentStatus.REJECTED;

        return repository.save(
                incident.withStatus(status)
                        .addAudit(
                                "operator",
                                approved
                                        ? "remediation.approved"
                                        : "remediation.rejected",
                                comment
                        )
        );
    }

    public Incident get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IncidentNotFoundException(
                        "Incident not found: " + id
                ));
    }

    public List<Incident> findRecent(
            int limit,
            int offset,
            IncidentStatus status,
            String service,
            Instant createdFrom,
            Instant createdTo) {
        return repository.findRecent(
                limit, offset, status, service, createdFrom, createdTo);
    }
}
