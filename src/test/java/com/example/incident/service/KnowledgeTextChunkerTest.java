package com.example.incident.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeTextChunkerTest {
    private final KnowledgeTextChunker chunker = new KnowledgeTextChunker();

    @Test
    void keepsShortTextInOneChunk() {
        assertEquals(java.util.List.of("MySQL connection failed"),
                chunker.split("  MySQL connection failed  "));
    }

    @Test
    void splitsLongTextIntoBoundedOverlappingChunks() {
        var content = "a".repeat(1_700);
        var chunks = chunker.split(content);

        assertTrue(chunks.size() >= 2);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.length() <= KnowledgeTextChunker.MAX_CHARS));
        assertTrue(chunks.get(0).substring(chunks.get(0).length() - KnowledgeTextChunker.OVERLAP_CHARS)
                .equals(chunks.get(1).substring(0, KnowledgeTextChunker.OVERLAP_CHARS)));
    }
}
