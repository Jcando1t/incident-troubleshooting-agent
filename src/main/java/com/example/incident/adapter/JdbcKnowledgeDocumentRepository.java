package com.example.incident.adapter;

import com.example.incident.domain.KnowledgeDocument;
import com.example.incident.domain.KnowledgeDocumentChunk;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.incident.port.KnowledgeDocumentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcKnowledgeDocumentRepository implements KnowledgeDocumentRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;


    public JdbcKnowledgeDocumentRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
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

    @Override
    @Transactional
    public void replaceChunks(UUID documentId, List<KnowledgeDocumentChunk> chunks) {
        jdbcTemplate.update(
                "DELETE FROM project_knowledge_chunks WHERE document_id = ?",
                documentId.toString());
        for (var chunk : chunks) {
            jdbcTemplate.update(
                    "INSERT INTO project_knowledge_chunks " +
                            "(id, document_id, project_id, chunk_index, content, embedding_model, " +
                            "embedding_json, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    chunk.id().toString(),
                    chunk.documentId().toString(),
                    chunk.projectId().toString(),
                    chunk.chunkIndex(),
                    chunk.content(),
                    chunk.embeddingModel(),
                    writeEmbedding(chunk.embedding()),
                    Timestamp.from(chunk.createdAt())
            );
        }
    }

    @Override
    public void deleteChunksByDocumentId(UUID documentId) {
        jdbcTemplate.update(
                "DELETE FROM project_knowledge_chunks WHERE document_id = ?",
                documentId.toString());
    }

    @Override
    public List<KnowledgeDocumentChunk> findChunksByProjectId(UUID projectId) {
        return jdbcTemplate.query(
                "SELECT id, document_id, project_id, chunk_index, content, embedding_model, " +
                        "embedding_json, created_at FROM project_knowledge_chunks " +
                        "WHERE project_id = ? ORDER BY document_id, chunk_index",
                (resultSet, rowNumber) -> new KnowledgeDocumentChunk(
                        UUID.fromString(resultSet.getString("id")),
                        UUID.fromString(resultSet.getString("document_id")),
                        UUID.fromString(resultSet.getString("project_id")),
                        resultSet.getInt("chunk_index"),
                        resultSet.getString("content"),
                        resultSet.getString("embedding_model"),
                        readEmbedding(resultSet.getString("embedding_json")),
                        resultSet.getTimestamp("created_at").toInstant()
                ),
                projectId.toString()
        );
    }

    private String writeEmbedding(List<Float> embedding) {
        try {
            return objectMapper.writeValueAsString(embedding);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize knowledge embedding", exception);
        }
    }

    private List<Float> readEmbedding(String embeddingJson) {
        try {
            return objectMapper.readValue(embeddingJson, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read knowledge embedding", exception);
        }
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
