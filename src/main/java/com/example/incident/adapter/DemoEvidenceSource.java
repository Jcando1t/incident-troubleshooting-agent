package com.example.incident.adapter;

import com.example.incident.domain.Evidence;
import com.example.incident.domain.Incident;
import com.example.incident.port.EvidenceSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(
        name = "app.observability.demo-enabled",
        havingValue = "true"
)
public class DemoEvidenceSource implements EvidenceSource {

    @Override
    public String name() {
        return "demo-observability";
    }

    @Override
    public List<Evidence> collect(Incident incident) {
        return List.of(new Evidence(
                name(),
                "error rate elevated; replace with Prometheus/Loki adapter",
                "demo://metrics/" + incident.service(),
                Instant.now()
        ));
    }
}