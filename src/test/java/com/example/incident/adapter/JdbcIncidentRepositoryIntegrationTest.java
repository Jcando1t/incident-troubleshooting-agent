package com.example.incident.adapter;

import com.example.incident.domain.Incident;
import com.example.incident.domain.IncidentStatus;
import com.example.incident.domain.KnowledgeDocument;
import com.example.incident.domain.KnowledgeDocumentChunk;
import com.example.incident.domain.Project;
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
import java.time.temporal.ChronoUnit;
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
    private JdbcProjectRepository projectRepository;
    private JdbcKnowledgeDocumentRepository knowledgeDocumentRepository;

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
        jdbcTemplate.update("DELETE FROM project_knowledge_chunks");
        jdbcTemplate.update("DELETE FROM project_knowledge_documents");
        jdbcTemplate.update("DELETE FROM incidents");
        repository = new JdbcIncidentRepository(jdbcTemplate, new ObjectMapper());
        projectRepository = new JdbcProjectRepository(jdbcTemplate);
        knowledgeDocumentRepository = new JdbcKnowledgeDocumentRepository(jdbcTemplate, new ObjectMapper());
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
    void storesProjectAndKeepsIncidentLinkedToThatProject() {
        var project = projectRepository.save(new Project(
                UUID.randomUUID(),
                "求职 Agent",
                Instant.parse("2026-10-11T00:00:00Z")
        ));
        var incident = new Incident(
                UUID.randomUUID(),
                project.id(),
                "project-scoped incident",
                "job-agent-api",
                "test",
                Severity.P2,
                "test incident",
                IncidentStatus.RECEIVED,
                Instant.parse("2026-10-11T01:00:00Z"),
                null,
                null,
                null,
                null,
                List.of()
        );

        repository.save(incident);

        assertEquals(project, projectRepository.findById(project.id()).orElseThrow());
        assertEquals(project.id(), repository.findById(incident.id()).orElseThrow().projectId());
        assertTrue(projectRepository.findAll().contains(project));
    }

    @Test
    void storesKnowledgeDocumentsSeparatelyForEachProject() {
        var projectA = projectRepository.save(new Project(UUID.randomUUID(), "Project A", Instant.now()));
        var projectB = projectRepository.save(new Project(UUID.randomUUID(), "Project B", Instant.now()));
        var documentA = knowledgeDocument(projectA.id(), "A runbook", "Only Project A can see this.");
        var documentB = knowledgeDocument(projectB.id(), "B runbook", "Only Project B can see this.");

        knowledgeDocumentRepository.create(documentA);
        knowledgeDocumentRepository.create(documentB);

        assertEquals(List.of(documentA), knowledgeDocumentRepository.findByProjectId(projectA.id()));
        assertTrue(knowledgeDocumentRepository.findByProjectIdAndId(projectB.id(), documentA.id()).isEmpty());
        assertEquals(documentB, knowledgeDocumentRepository.findByProjectIdAndId(projectB.id(), documentB.id())
                .orElseThrow());

        var updated = new KnowledgeDocument(
                documentA.id(), documentA.projectId(), "Updated runbook", "Updated text",
                documentA.createdAt(), Instant.now().plusSeconds(1).truncatedTo(ChronoUnit.MICROS));
        knowledgeDocumentRepository.update(updated);
        assertEquals(updated, knowledgeDocumentRepository.findByProjectIdAndId(projectA.id(), documentA.id())
                .orElseThrow());

        assertTrue(knowledgeDocumentRepository.deleteByProjectIdAndId(projectA.id(), documentA.id()));
        assertTrue(knowledgeDocumentRepository.findByProjectId(projectA.id()).isEmpty());
    }

    @Test
    void storesAndReadsVectorChunksOnlyWithinTheRequestedProject() {
        var projectA = projectRepository.save(new Project(UUID.randomUUID(), "Vector Project A", Instant.now()));
        var projectB = projectRepository.save(new Project(UUID.randomUUID(), "Vector Project B", Instant.now()));
        var documentA = knowledgeDocument(projectA.id(), "A", "first project data");
        var documentB = knowledgeDocument(projectB.id(), "B", "second project data");
        knowledgeDocumentRepository.create(documentA);
        knowledgeDocumentRepository.create(documentB);
        var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        var chunkA = new KnowledgeDocumentChunk(UUID.randomUUID(), documentA.id(), projectA.id(), 0,
                "first project data", "bge-m3", List.of(0.1f, 0.2f), now);
        var chunkB = new KnowledgeDocumentChunk(UUID.randomUUID(), documentB.id(), projectB.id(), 0,
                "second project data", "bge-m3", List.of(0.3f, 0.4f), now);

        knowledgeDocumentRepository.replaceChunks(documentA.id(), List.of(chunkA));
        knowledgeDocumentRepository.replaceChunks(documentB.id(), List.of(chunkB));

        assertEquals(List.of(chunkA), knowledgeDocumentRepository.findChunksByProjectId(projectA.id()));
        assertEquals(List.of(chunkB), knowledgeDocumentRepository.findChunksByProjectId(projectB.id()));
    }

    private KnowledgeDocument knowledgeDocument(UUID projectId, String title, String content) {
        var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return new KnowledgeDocument(UUID.randomUUID(), projectId, title, content, now, now);
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
                "payment-service",
                null,
                null
        );

        assertEquals(List.of(olderApproved), results);
    }

    @Test
    void findsIncidentsWithinInclusiveCreationTimeRange() {
        var before = incident("before range", "payment-service", IncidentStatus.RECEIVED,
                "2026-10-01T23:59:59Z");
        var atStart = incident("at start", "payment-service", IncidentStatus.RECEIVED,
                "2026-10-02T00:00:00Z");
        var inRange = incident("in range", "payment-service", IncidentStatus.RECEIVED,
                "2026-10-03T12:00:00Z");
        var atEnd = incident("at end", "payment-service", IncidentStatus.RECEIVED,
                "2026-10-04T00:00:00Z");
        var after = incident("after range", "payment-service", IncidentStatus.RECEIVED,
                "2026-10-04T00:00:01Z");
        repository.save(before);
        repository.save(atStart);
        repository.save(inRange);
        repository.save(atEnd);
        repository.save(after);

        var results = repository.findRecent(
                10,
                0,
                null,
                null,
                Instant.parse("2026-10-02T00:00:00Z"),
                Instant.parse("2026-10-04T00:00:00Z")
        );

        assertEquals(List.of(atEnd, inRange, atStart), results);
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
