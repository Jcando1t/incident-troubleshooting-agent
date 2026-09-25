package com.example.incident.agent;

import com.example.incident.domain.*;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class TriageAgent implements IncidentAgent<Void, TriageResult> {
    @Override public String name() { return "triage-agent"; }

    @Override
    public TriageResult execute(Incident incident, Void ignored) {
        var severity = incident.severity() == null ? Severity.P2 : incident.severity();
        return new TriageResult(severity, List.of(incident.service()),
                "service-local; impact requires confirmation from metrics", 0.86);
    }
}
