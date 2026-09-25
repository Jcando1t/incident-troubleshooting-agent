package com.example.incident.port;

import com.example.incident.domain.Incident;
import java.util.Optional;
import java.util.UUID;

public interface IncidentRepository {
    Incident save(Incident incident);
    Optional<Incident> findById(UUID id);
}
