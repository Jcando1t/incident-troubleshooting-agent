package com.example.incident.service;

import com.example.incident.domain.KnowledgeDocument;
import com.example.incident.port.KnowledgeDocumentRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class KnowledgeDocumentService {
    private final KnowledgeDocumentRepository repository;
    private final ProjectService projectService;

    public KnowledgeDocumentService(
            KnowledgeDocumentRepository repository,
            ProjectService projectService) {
        this.repository = repository;
        this.projectService = projectService;
    }

    public KnowledgeDocument create(UUID projectId, String title, String content) {
        projectService.get(projectId);
        var now = Instant.now();
        return repository.create(new KnowledgeDocument(
                UUID.randomUUID(), projectId, title.trim(), content, now, now));
    }

    public List<KnowledgeDocument> findByProjectId(UUID projectId) {
        projectService.get(projectId);
        return repository.findByProjectId(projectId);
    }

    public KnowledgeDocument get(UUID projectId, UUID documentId) {
        projectService.get(projectId);
        return repository.findByProjectIdAndId(projectId, documentId)
                .orElseThrow(() -> new KnowledgeDocumentNotFoundException(documentId));
    }

    public KnowledgeDocument update(UUID projectId, UUID documentId, String title, String content) {
        var existing = get(projectId, documentId);
        return repository.update(new KnowledgeDocument(
                existing.id(),
                existing.projectId(),
                title.trim(),
                content,
                existing.createdAt(),
                Instant.now()
        ));
    }

    public void delete(UUID projectId, UUID documentId) {
        projectService.get(projectId);
        if (!repository.deleteByProjectIdAndId(projectId, documentId)) {
            throw new KnowledgeDocumentNotFoundException(documentId);
        }
    }
}
