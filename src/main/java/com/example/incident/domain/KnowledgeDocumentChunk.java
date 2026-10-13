package com.example.incident.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KnowledgeDocumentChunk(
        UUID id,
        UUID documentId,
        UUID projectId,
        int chunkIndex,
        String content,
        String embeddingModel,
        List<Float> embedding,
        Instant createdAt
) {}
