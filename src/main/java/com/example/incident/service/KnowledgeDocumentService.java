package com.example.incident.service;

import com.example.incident.domain.KnowledgeDocument;
import com.example.incident.domain.KnowledgeDocumentChunk;
import com.example.incident.domain.KnowledgeSearchResult;
import com.example.incident.port.TextEmbeddingModel;
import com.example.incident.port.KnowledgeDocumentRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class KnowledgeDocumentService {
    private final KnowledgeDocumentRepository repository;
    private final ProjectService projectService;
    private final KnowledgeTextChunker chunker;
    private final TextEmbeddingModel embeddingModel;

    public KnowledgeDocumentService(
            KnowledgeDocumentRepository repository,
            ProjectService projectService,
            KnowledgeTextChunker chunker,
            TextEmbeddingModel embeddingModel) {
        this.repository = repository;
        this.projectService = projectService;
        this.chunker = chunker;
        this.embeddingModel = embeddingModel;
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
        var document = repository.update(new KnowledgeDocument(
                existing.id(),
                existing.projectId(),
                title.trim(),
                content,
                existing.createdAt(),
                Instant.now()
        ));
        repository.deleteChunksByDocumentId(documentId);
        return document;
    }

    public void delete(UUID projectId, UUID documentId) {
        projectService.get(projectId);
        if (!repository.deleteByProjectIdAndId(projectId, documentId)) {
            throw new KnowledgeDocumentNotFoundException(documentId);
        }
    }

    public EmbeddingResult reindex(UUID projectId, UUID documentId) {
        var document = get(projectId, documentId);
        reindex(document);
        return new EmbeddingResult(document.id(), embeddingModel.modelName(),
                chunker.split(document.title() + "\n" + document.content()).size());
    }

    public List<KnowledgeSearchResult> searchSimilar(UUID projectId, String query, int limit) {
        projectService.get(projectId);
        var chunks = repository.findChunksByProjectId(projectId);
        if (chunks.isEmpty()) {
            return List.of();
        }

        var queryEmbedding = embeddingModel.embed(query);
        return chunks.stream()
                .filter(chunk -> chunk.embeddingModel().equals(embeddingModel.modelName()))
                .map(chunk -> new KnowledgeSearchResult(
                        chunk.documentId(),
                        chunk.projectId(),
                        chunk.chunkIndex(),
                        chunk.content(),
                        chunk.embeddingModel(),
                        cosineSimilarity(queryEmbedding, chunk.embedding())
                ))
                .sorted(java.util.Comparator.comparingDouble(KnowledgeSearchResult::score).reversed())
                .limit(limit)
                .toList();
    }

    private double cosineSimilarity(List<Float> left, List<Float> right) {
        if (left.size() != right.size() || left.isEmpty()) {
            return -1;
        }
        double dotProduct = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (var index = 0; index < left.size(); index++) {
            var leftValue = left.get(index);
            var rightValue = right.get(index);
            dotProduct += leftValue * rightValue;
            leftNorm += leftValue * leftValue;
            rightNorm += rightValue * rightValue;
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return -1;
        }
        return dotProduct / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private void reindex(KnowledgeDocument document) {
        var texts = chunker.split(document.title() + "\n" + document.content());
        var chunks = new java.util.ArrayList<KnowledgeDocumentChunk>();
        for (var index = 0; index < texts.size(); index++) {
            var text = texts.get(index);
            chunks.add(new KnowledgeDocumentChunk(
                    UUID.randomUUID(),
                    document.id(),
                    document.projectId(),
                    index,
                    text,
                    embeddingModel.modelName(),
                    embeddingModel.embed(text),
                    Instant.now()
            ));
        }
        repository.replaceChunks(document.id(), chunks);
    }

    public record EmbeddingResult(UUID documentId, String model, int chunks) {}
}
