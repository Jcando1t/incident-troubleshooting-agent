package com.example.incident.domain;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeDocument(
        UUID id,
        UUID projectId,
        String title,
        String content,
        Instant createdAt,
        Instant updatedAt
) {}
