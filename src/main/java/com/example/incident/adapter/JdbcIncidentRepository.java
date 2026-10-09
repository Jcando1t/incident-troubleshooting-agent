package com.example.incident.adapter;

import com.example.incident.domain.AuditEntry;
import com.example.incident.domain.EvidenceBundle;
import com.example.incident.domain.Incident;
import com.example.incident.domain.IncidentStatus;
import com.example.incident.domain.RcaResult;
import com.example.incident.domain.RemediationPlan;
import com.example.incident.domain.Severity;
import com.example.incident.domain.TriageResult;
import com.example.incident.port.IncidentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Primary
public class JdbcIncidentRepository implements IncidentRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcIncidentRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Incident save(Incident incident) {
        var sql = """
                INSERT INTO incidents (
                    id, title, service, environment, severity, description,
                    status, created_at, triage_json, evidence_json,
                    rca_json, remediation_json, audit_json
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    title = VALUES(title),
                    service = VALUES(service),
                    environment = VALUES(environment),
                    severity = VALUES(severity),
                    description = VALUES(description),
                    status = VALUES(status),
                    created_at = VALUES(created_at),
                    triage_json = VALUES(triage_json),
                    evidence_json = VALUES(evidence_json),
                    rca_json = VALUES(rca_json),
                    remediation_json = VALUES(remediation_json),
                    audit_json = VALUES(audit_json)
                """;

        jdbcTemplate.update(
                sql,
                incident.id().toString(),
                incident.title(),
                incident.service(),
                incident.environment(),
                incident.severity() == null
                        ? null
                        : incident.severity().name(),
                incident.description(),
                incident.status().name(),
                Timestamp.from(incident.createdAt()),
                writeJson(incident.triage()),
                writeJson(incident.evidence()),
                writeJson(incident.rca()),
                writeJson(incident.remediation()),
                writeJson(incident.audit())
        );

        return incident;
    }

    @Override
    public Optional<Incident> findById(UUID id) {
        var sql = """
                SELECT id, title, service, environment, severity, description,
                       status, created_at, triage_json, evidence_json,
                       rca_json, remediation_json, audit_json
                FROM incidents
                WHERE id = ?
                """;

        return jdbcTemplate.query(
                sql,
                resultSet -> {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }

                    return Optional.of(new Incident(
                            UUID.fromString(resultSet.getString("id")),
                            resultSet.getString("title"),
                            resultSet.getString("service"),
                            resultSet.getString("environment"),
                            readSeverity(resultSet.getString("severity")),
                            resultSet.getString("description"),
                            IncidentStatus.valueOf(
                                    resultSet.getString("status")),
                            resultSet.getTimestamp("created_at").toInstant(),
                            readJson(
                                    resultSet.getString("triage_json"),
                                    TriageResult.class
                            ),
                            readJson(
                                    resultSet.getString("evidence_json"),
                                    EvidenceBundle.class
                            ),
                            readJson(
                                    resultSet.getString("rca_json"),
                                    RcaResult.class
                            ),
                            readJson(
                                    resultSet.getString("remediation_json"),
                                    RemediationPlan.class
                            ),
                            readAudit(resultSet.getString("audit_json"))
                    ));
                },
                id.toString()
        );
    }

    @Override
    public List<Incident> findRecent(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }

        var sql = """
                SELECT id, title, service, environment, severity, description,
                       status, created_at, triage_json, evidence_json,
                       rca_json, remediation_json, audit_json
                FROM incidents
                ORDER BY created_at DESC
                LIMIT ?
                """;

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> new Incident(
                        UUID.fromString(resultSet.getString("id")),
                        resultSet.getString("title"),
                        resultSet.getString("service"),
                        resultSet.getString("environment"),
                        readSeverity(resultSet.getString("severity")),
                        resultSet.getString("description"),
                        IncidentStatus.valueOf(
                                resultSet.getString("status")),
                        resultSet.getTimestamp("created_at").toInstant(),
                        readJson(
                                resultSet.getString("triage_json"),
                                TriageResult.class
                        ),
                        readJson(
                                resultSet.getString("evidence_json"),
                                EvidenceBundle.class
                        ),
                        readJson(
                                resultSet.getString("rca_json"),
                                RcaResult.class
                        ),
                        readJson(
                                resultSet.getString("remediation_json"),
                                RemediationPlan.class
                        ),
                        readAudit(resultSet.getString("audit_json"))
                ),
                limit
        );
    }


    private Severity readSeverity(String value) {
        return value == null ? null : Severity.valueOf(value);
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "无法把 Incident 字段转换为 JSON",
                    ex
            );
        }
    }

    private <T> T readJson(String value, Class<T> type) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            return null;
        }

        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "无法从数据库 JSON 恢复 " + type.getSimpleName(),
                    ex
            );
        }
    }

    private List<AuditEntry> readAudit(String value) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            return List.of();
        }

        try {
            return objectMapper.readValue(
                    value,
                    new TypeReference<List<AuditEntry>>() {}
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "无法从数据库恢复审计记录",
                    ex
            );
        }
    }
}
