package com.example.incident.port;

import com.example.incident.domain.Incident;
import com.example.incident.domain.Evidence;
import java.util.List;

public interface EvidenceSource {
    String name();
    List<Evidence> collect(Incident incident);
}
