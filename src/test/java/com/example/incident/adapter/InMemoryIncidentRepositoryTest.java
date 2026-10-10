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

        assertEquals(List.of(newest, middle), repository.findRecent(2, 0, null, null));
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

        assertEquals(List.of(middle), repository.findRecent(1, 1, null, null));
    }

    @Test
    void rejectsLimitLessThanOne() {
        var repository = new InMemoryIncidentRepository();

        assertThrows(IllegalArgumentException.class,
                () -> repository.findRecent(0, 0, null, null));
    }

    @Test
    void rejectsNegativeOffset() {
        var repository = new InMemoryIncidentRepository();

        assertThrows(IllegalArgumentException.class,
                () -> repository.findRecent(1, -1, null, null));
    }

    @Test
    void filtersIncidentsByStatusAndService() {
        var repository = new InMemoryIncidentRepository();
        var matching = incident("approved payment", "2026-10-03T00:00:00Z",
                IncidentStatus.APPROVED, "payment-service");
        repository.save(matching);
        repository.save(incident("rejected payment", "2026-10-02T00:00:00Z",
                IncidentStatus.REJECTED, "payment-service"));
        repository.save(incident("approved search", "2026-10-01T00:00:00Z",
                IncidentStatus.APPROVED, "search-service"));

        assertEquals(List.of(matching), repository.findRecent(
                10, 0, IncidentStatus.APPROVED, "payment-service"));
    }

    private Incident incident(String title, String createdAt) {
        return incident(title, createdAt, IncidentStatus.RECEIVED, "incident-platform");
    }

    private Incident incident(
            String title,
            String createdAt,
            IncidentStatus status,
            String service) {
        return new Incident(
                UUID.randomUUID(),
                title,
                service,
                "local",
                null,
                "test incident",
                status,
                Instant.parse(createdAt),
                null,
                null,
                null,
                null,
                List.of()
        );
    }
}
