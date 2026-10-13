package com.example.incident.domain;

import java.util.UUID;

public record KnowledgeSearchResult(
        UUID documentId,
        UUID projectId,
        int chunkIndex,
        String content,
        String embeddingModel,
        double score
) {}
