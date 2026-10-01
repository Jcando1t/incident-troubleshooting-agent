package com.example.incident.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Incident(
        UUID id,
        String title,
        String service,
        String environment,
        Severity severity,
        String description,
        IncidentStatus status,
        Instant createdAt,
        TriageResult triage,
        EvidenceBundle evidence,
        RcaResult rca,
        RemediationPlan remediation,
        List<AuditEntry> audit
) {
    public Incident withStatus(IncidentStatus value) {
        return new Incident(id, title, service, environment, severity, description, value,
                createdAt, triage, evidence, rca, remediation, audit);
    }

    public Incident withAnalysis(TriageResult newTriage, EvidenceBundle newEvidence,
                                 RcaResult newRca, RemediationPlan newRemediation) {
        return new Incident(id, title, service, environment, newTriage.severity(), description,
                IncidentStatus.AWAITING_APPROVAL, createdAt, newTriage, newEvidence,
                newRca, newRemediation, audit);
    }

    public Incident addAudit(String actor, String action, String detail) {
        var entries = new java.util.ArrayList<>(audit == null ? List.<AuditEntry>of() : audit);
        entries.add(new AuditEntry(Instant.now(), actor, action, detail));
        return new Incident(id, title, service, environment, severity, description,
                status, createdAt, triage, evidence, rca, remediation, List.copyOf(entries));
    }
}
