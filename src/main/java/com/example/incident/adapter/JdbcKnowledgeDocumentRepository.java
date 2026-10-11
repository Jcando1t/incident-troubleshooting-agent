package com.example.incident.adapter;

import com.example.incident.domain.KnowledgeDocument;
import com.example.incident.port.KnowledgeDocumentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcKnowledgeDocumentRepository implements KnowledgeDocumentRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcKnowledgeDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public KnowledgeDocument create(KnowledgeDocument document) {
        jdbcTemplate.update(
                "INSERT INTO project_knowledge_documents " +
                        "(id, project_id, title, content, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                document.id().toString(),
                document.projectId().toString(),
                document.title(),
                document.content(),
                Timestamp.from(document.createdAt()),
                Timestamp.from(document.updatedAt())
        );
        return document;
    }

    @Override
    public KnowledgeDocument update(KnowledgeDocument document) {
        jdbcTemplate.update(
                "UPDATE project_knowledge_documents SET title = ?, content = ?, updated_at = ? " +
                        "WHERE project_id = ? AND id = ?",
                document.title(),
                document.content(),
                Timestamp.from(document.updatedAt()),
                document.projectId().toString(),
                document.id().toString()
        );
        return document;
    }

    @Override
    public List<KnowledgeDocument> findByProjectId(UUID projectId) {
        return jdbcTemplate.query(
                "SELECT id, project_id, title, content, created_at, updated_at " +
                        "FROM project_knowledge_documents WHERE project_id = ? ORDER BY updated_at DESC, id",
                (resultSet, rowNumber) -> mapDocument(
                        resultSet.getString("id"),
                        resultSet.getString("project_id"),
                        resultSet.getString("title"),
                        resultSet.getString("content"),
                        resultSet.getTimestamp("created_at"),
                        resultSet.getTimestamp("updated_at")
                ),
                projectId.toString()
        );
    }

    @Override
    public Optional<KnowledgeDocument> findByProjectIdAndId(UUID projectId, UUID documentId) {
        var results = jdbcTemplate.query(
                "SELECT id, project_id, title, content, created_at, updated_at " +
                        "FROM project_knowledge_documents WHERE project_id = ? AND id = ?",
                (resultSet, rowNumber) -> mapDocument(
                        resultSet.getString("id"),
                        resultSet.getString("project_id"),
                        resultSet.getString("title"),
                        resultSet.getString("content"),
                        resultSet.getTimestamp("created_at"),
                        resultSet.getTimestamp("updated_at")
                ),
                projectId.toString(),
                documentId.toString()
        );
        return results.stream().findFirst();
    }

    @Override
    public boolean deleteByProjectIdAndId(UUID projectId, UUID documentId) {
        return jdbcTemplate.update(
                "DELETE FROM project_knowledge_documents WHERE project_id = ? AND id = ?",
                projectId.toString(),
                documentId.toString()
        ) == 1;
    }

    private KnowledgeDocument mapDocument(
            String id,
            String projectId,
            String title,
            String content,
            Timestamp createdAt,
            Timestamp updatedAt) {
        return new KnowledgeDocument(
                UUID.fromString(id),
                UUID.fromString(projectId),
                title,
                content,
                createdAt.toInstant(),
                updatedAt.toInstant()
        );
    }
}
