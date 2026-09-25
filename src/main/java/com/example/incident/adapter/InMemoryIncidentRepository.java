package com.example.incident.adapter;

import com.example.incident.domain.Incident;
import com.example.incident.port.IncidentRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryIncidentRepository implements IncidentRepository {
    private final ConcurrentHashMap<UUID, Incident> store = new ConcurrentHashMap<>();
    @Override public Incident save(Incident incident) { store.put(incident.id(), incident); return incident; }
    @Override public Optional<Incident> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
}
