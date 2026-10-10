package com.example.incident.adapter;

import com.example.incident.domain.Incident;
import com.example.incident.domain.IncidentStatus;
import com.example.incident.domain.Severity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class JdbcIncidentRepositoryIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("incident_platform_test")
            .withUsername("test_user")
            .withPassword("test_password");

    private JdbcTemplate jdbcTemplate;
    private JdbcIncidentRepository repository;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),
                MYSQL.getPassword()
        );
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("DELETE FROM incidents");
        repository = new JdbcIncidentRepository(jdbcTemplate, new ObjectMapper());
    }

    @Test
    void savesAndReadsIncidentFromMySql() {
        var incident = incident(
                "MySQL integration test",
                "payment-service",
                IncidentStatus.AWAITING_APPROVAL,
                "2026-10-10T10:00:00Z"
        );

        repository.save(incident);

        var loaded = repository.findById(incident.id());
        assertTrue(loaded.isPresent());
        assertEquals(incident, loaded.orElseThrow());
    }

    @Test
    void findsRecentIncidentsByStatusAndServiceWithOffset() {
        var olderApproved = incident(
                "older approved payment",
                "payment-service",
                IncidentStatus.APPROVED,
                "2026-10-08T10:00:00Z"
        );
        var newerApproved = incident(
                "newer approved payment",
                "payment-service",
                IncidentStatus.APPROVED,
                "2026-10-09T10:00:00Z"
        );
        var otherStatus = incident(
                "rejected payment",
                "payment-service",
                IncidentStatus.REJECTED,
                "2026-10-10T10:00:00Z"
        );
        repository.save(olderApproved);
        repository.save(newerApproved);
        repository.save(otherStatus);

        var results = repository.findRecent(
                10,
                1,
                IncidentStatus.APPROVED,
                "payment-service"
        );

        assertEquals(List.of(olderApproved), results);
    }

    private Incident incident(
            String title,
            String service,
            IncidentStatus status,
            String createdAt) {
        return new Incident(
                UUID.randomUUID(),
                title,
                service,
                "test",
                Severity.P2,
                "Integration test incident",
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
