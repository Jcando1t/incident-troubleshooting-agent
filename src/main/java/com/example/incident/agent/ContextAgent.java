package com.example.incident.agent;

import com.example.incident.domain.*;
import com.example.incident.port.EvidenceSource;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ContextAgent implements IncidentAgent<Void, EvidenceBundle> {
    private final List<EvidenceSource> sources;

    public ContextAgent(List<EvidenceSource> sources) { this.sources = sources; }

    @Override public String name() { return "context-agent"; }

    @Override
    public EvidenceBundle execute(Incident incident, Void ignored) {
        var evidence = sources.stream().flatMap(s -> s.collect(incident).stream()).toList();
        return new EvidenceBundle(evidence, !evidence.isEmpty());
    }
}
