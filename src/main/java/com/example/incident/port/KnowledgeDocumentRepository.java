package com.example.incident.port;

import com.example.incident.domain.KnowledgeDocument;
import com.example.incident.domain.KnowledgeDocumentChunk;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KnowledgeDocumentRepository {
    KnowledgeDocument create(KnowledgeDocument document);
    KnowledgeDocument update(KnowledgeDocument document);
    List<KnowledgeDocument> findByProjectId(UUID projectId);
    Optional<KnowledgeDocument> findByProjectIdAndId(UUID projectId, UUID documentId);
    boolean deleteByProjectIdAndId(UUID projectId, UUID documentId);
    void replaceChunks(UUID documentId, List<KnowledgeDocumentChunk> chunks);
    void deleteChunksByDocumentId(UUID documentId);
    List<KnowledgeDocumentChunk> findChunksByProjectId(UUID projectId);
}
