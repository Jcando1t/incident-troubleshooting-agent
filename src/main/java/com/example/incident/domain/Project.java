package com.example.incident.domain;

import java.time.Instant;
import java.util.UUID;

public record Project(
        UUID id,
        String name,
        Instant createdAt
) {}
