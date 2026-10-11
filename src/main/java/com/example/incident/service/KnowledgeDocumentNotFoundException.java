package com.example.incident.service;

import java.util.UUID;

public class KnowledgeDocumentNotFoundException extends RuntimeException {
    public KnowledgeDocumentNotFoundException(UUID id) {
        super("Knowledge document not found: " + id);
    }
}
