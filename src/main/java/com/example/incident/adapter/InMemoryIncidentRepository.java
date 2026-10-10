package com.example.incident.adapter;

import com.example.incident.domain.Incident;
import com.example.incident.domain.IncidentStatus;
import com.example.incident.port.IncidentRepository;
import org.springframework.stereotype.Repository;
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
    public List<Incident> findRecent(
            int limit,
            int offset,
            IncidentStatus status,
            String service) {
        if (limit < 1 || offset < 0) {
            throw new IllegalArgumentException("limit must be positive and offset cannot be negative");
        }

        return store.values().stream()
                .filter(incident -> status == null || incident.status() == status)
                .filter(incident -> service == null || incident.service().equals(service))
                .sorted(Comparator.comparing(Incident::createdAt).reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }
}
