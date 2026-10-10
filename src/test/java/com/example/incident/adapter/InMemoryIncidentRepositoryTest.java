package com.example.incident.adapter;

import com.example.incident.domain.Incident;
import com.example.incident.domain.IncidentStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryIncidentRepositoryTest {

    @Test
    void returnsNewestIncidentsFirstAndHonorsLimit() {
        var repository = new InMemoryIncidentRepository();
        var oldest = incident("oldest", "2026-10-01T00:00:00Z");
        var middle = incident("middle", "2026-10-02T00:00:00Z");
        var newest = incident("newest", "2026-10-03T00:00:00Z");
        repository.save(oldest);
        repository.save(newest);
        repository.save(middle);

        assertEquals(List.of(newest, middle), repository.findRecent(2, 0));
    }

    @Test
    void skipsRequestedNumberOfNewestIncidents() {
        var repository = new InMemoryIncidentRepository();
        var oldest = incident("oldest", "2026-10-01T00:00:00Z");
        var middle = incident("middle", "2026-10-02T00:00:00Z");
        var newest = incident("newest", "2026-10-03T00:00:00Z");
        repository.save(oldest);
        repository.save(middle);
        repository.save(newest);

        assertEquals(List.of(middle), repository.findRecent(1, 1));
    }

    @Test
    void rejectsLimitLessThanOne() {
        var repository = new InMemoryIncidentRepository();

        assertThrows(IllegalArgumentException.class,
                () -> repository.findRecent(0, 0));
    }

    @Test
    void rejectsNegativeOffset() {
        var repository = new InMemoryIncidentRepository();

        assertThrows(IllegalArgumentException.class,
                () -> repository.findRecent(1, -1));
    }

    private Incident incident(String title, String createdAt) {
        return new Incident(
                UUID.randomUUID(),
                title,
                "incident-platform",
                "local",
                null,
                "test incident",
                IncidentStatus.RECEIVED,
                Instant.parse(createdAt),
                null,
                null,
                null,
                null,
                List.of()
        );
    }
}
