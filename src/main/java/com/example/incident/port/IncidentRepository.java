package com.example.incident.port;

import com.example.incident.domain.Incident;
import com.example.incident.domain.IncidentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncidentRepository {
    Incident save(Incident incident);
    Optional<Incident> findById(UUID id);
    List<Incident> findRecent(
            int limit,
            int offset,
            IncidentStatus status,
            String service,
            Instant createdFrom,
            Instant createdTo);

    default List<Incident> findRecent(
            UUID projectId,
            int limit,
            int offset,
            IncidentStatus status,
            String service,
            Instant createdFrom,
            Instant createdTo) {
        return findRecent(limit, offset, status, service, createdFrom, createdTo);
    }
}
