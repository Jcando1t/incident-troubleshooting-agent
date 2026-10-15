package com.example.incident.adapter;

import com.example.incident.domain.Incident;
import com.example.incident.domain.IncidentStatus;
import com.example.incident.port.IncidentRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryIncidentRepository implements IncidentRepository {
    private final ConcurrentHashMap<UUID, Incident> store = new ConcurrentHashMap<>();
    @Override public Incident save(Incident incident) { store.put(incident.id(), incident); return incident; }
    @Override public Optional<Incident> findById(UUID id) { return Optional.ofNullable(store.get(id)); }

    @Override
    public Optional<Incident> findByProjectIdAndId(UUID projectId, UUID id) {
        return findById(id).filter(incident -> incident.projectId().equals(projectId));
    }

    @Override
    public List<Incident> findRecent(
            int limit,
            int offset,
            IncidentStatus status,
            String service,
            Instant createdFrom,
            Instant createdTo) {
        return findRecent(null, limit, offset, status, service, createdFrom, createdTo);
    }

    @Override
    public List<Incident> findRecent(
            UUID projectId,
            int limit,
            int offset,
            IncidentStatus status,
            String service,
            Instant createdFrom,
            Instant createdTo) {
        if (limit < 1 || offset < 0) {
            throw new IllegalArgumentException("limit must be positive and offset cannot be negative");
        }

        return store.values().stream()
                .filter(incident -> projectId == null || incident.projectId().equals(projectId))
                .filter(incident -> status == null || incident.status() == status)
                .filter(incident -> service == null || incident.service().equals(service))
                .filter(incident -> createdFrom == null
                        || !incident.createdAt().isBefore(createdFrom))
                .filter(incident -> createdTo == null
                        || !incident.createdAt().isAfter(createdTo))
                .sorted(Comparator.comparing(Incident::createdAt).reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }
}
