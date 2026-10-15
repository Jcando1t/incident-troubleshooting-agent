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
    private static final UUID DEFAULT_PROJECT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void returnsNewestIncidentsFirstAndHonorsLimit() {
        var repository = new InMemoryIncidentRepository();
        var oldest = incident("oldest", "2026-10-01T00:00:00Z");
        var middle = incident("middle", "2026-10-02T00:00:00Z");
        var newest = incident("newest", "2026-10-03T00:00:00Z");
        repository.save(oldest);
        repository.save(newest);
        repository.save(middle);

        assertEquals(List.of(newest, middle), repository.findRecent(2, 0, null, null, null, null));
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

        assertEquals(List.of(middle), repository.findRecent(1, 1, null, null, null, null));
    }

    @Test
    void rejectsLimitLessThanOne() {
        var repository = new InMemoryIncidentRepository();

        assertThrows(IllegalArgumentException.class,
                () -> repository.findRecent(0, 0, null, null, null, null));
    }

    @Test
    void rejectsNegativeOffset() {
        var repository = new InMemoryIncidentRepository();

        assertThrows(IllegalArgumentException.class,
                () -> repository.findRecent(1, -1, null, null, null, null));
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
                10, 0, IncidentStatus.APPROVED, "payment-service", null, null));
    }

    @Test
    void filtersIncidentsByProject() {
        var repository = new InMemoryIncidentRepository();
        var firstProject = UUID.randomUUID();
        var secondProject = UUID.randomUUID();
        var firstIncident = incident(firstProject, "first project", "2026-10-03T00:00:00Z");
        var secondIncident = incident(secondProject, "second project", "2026-10-04T00:00:00Z");
        repository.save(firstIncident);
        repository.save(secondIncident);

        assertEquals(
                List.of(firstIncident),
                repository.findRecent(firstProject, 10, 0, null, null, null, null)
        );
    }

    @Test
    void findsIncidentOnlyWhenProjectMatches() {
        var repository = new InMemoryIncidentRepository();
        var projectId = UUID.randomUUID();
        var otherProjectId = UUID.randomUUID();
        var incident = incident(projectId, "scoped incident", "2026-10-03T00:00:00Z");
        repository.save(incident);

        assertEquals(java.util.Optional.of(incident),
                repository.findByProjectIdAndId(projectId, incident.id()));
        assertEquals(java.util.Optional.empty(),
                repository.findByProjectIdAndId(otherProjectId, incident.id()));
    }

    @Test
    void filtersByInclusiveCreationTimeRange() {
        var repository = new InMemoryIncidentRepository();
        var before = incident("before", "2026-10-01T23:59:59Z");
        var atStart = incident("at start", "2026-10-02T00:00:00Z");
        var between = incident("between", "2026-10-03T12:00:00Z");
        var atEnd = incident("at end", "2026-10-04T00:00:00Z");
        var after = incident("after", "2026-10-04T00:00:01Z");
        repository.save(before);
        repository.save(atStart);
        repository.save(between);
        repository.save(atEnd);
        repository.save(after);

        assertEquals(
                List.of(atEnd, between, atStart),
                repository.findRecent(
                        10,
                        0,
                        null,
                        null,
                        Instant.parse("2026-10-02T00:00:00Z"),
                        Instant.parse("2026-10-04T00:00:00Z")
                )
        );
    }

    private Incident incident(String title, String createdAt) {
        return incident(DEFAULT_PROJECT_ID, title, createdAt);
    }

    private Incident incident(UUID projectId, String title, String createdAt) {
        return incident(projectId, title, createdAt, IncidentStatus.RECEIVED, "incident-platform");
    }

    private Incident incident(
            String title,
            String createdAt,
            IncidentStatus status,
            String service) {
        return incident(DEFAULT_PROJECT_ID, title, createdAt, status, service);
    }

    private Incident incident(
            UUID projectId,
            String title,
            String createdAt,
            IncidentStatus status,
            String service) {
        return new Incident(
                UUID.randomUUID(),
                projectId,
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
