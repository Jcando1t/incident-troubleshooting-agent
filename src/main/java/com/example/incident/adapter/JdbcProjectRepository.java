package com.example.incident.adapter;

import com.example.incident.domain.Project;
import com.example.incident.port.ProjectRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcProjectRepository implements ProjectRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcProjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Project save(Project project) {
        jdbcTemplate.update(
                "INSERT INTO projects (id, name, created_at) VALUES (?, ?, ?)",
                project.id().toString(),
                project.name(),
                Timestamp.from(project.createdAt())
        );
        return project;
    }

    @Override
    public Optional<Project> findById(UUID id) {
        var results = jdbcTemplate.query(
                "SELECT id, name, created_at FROM projects WHERE id = ?",
                (resultSet, rowNumber) -> mapProject(
                        resultSet.getString("id"),
                        resultSet.getString("name"),
                        resultSet.getTimestamp("created_at")
                ),
                id.toString()
        );
        return results.stream().findFirst();
    }

    @Override
    public List<Project> findAll() {
        return jdbcTemplate.query(
                "SELECT id, name, created_at FROM projects ORDER BY name, id",
                (resultSet, rowNumber) -> mapProject(
                        resultSet.getString("id"),
                        resultSet.getString("name"),
                        resultSet.getTimestamp("created_at")
                )
        );
    }

    @Override
    public boolean deleteIfEmpty(UUID id) {
        return jdbcTemplate.update("""
                DELETE FROM projects
                WHERE id = ?
                  AND NOT EXISTS (SELECT 1 FROM incidents WHERE project_id = ?)
                  AND NOT EXISTS (SELECT 1 FROM project_knowledge_documents WHERE project_id = ?)
                  AND NOT EXISTS (SELECT 1 FROM project_knowledge_chunks WHERE project_id = ?)
                """,
                id.toString(), id.toString(), id.toString(), id.toString()) == 1;
    }

    private Project mapProject(String id, String name, Timestamp createdAt) {
        return new Project(UUID.fromString(id), name, createdAt.toInstant());
    }
}
