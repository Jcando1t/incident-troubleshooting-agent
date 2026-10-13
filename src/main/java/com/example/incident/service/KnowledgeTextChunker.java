package com.example.incident.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class KnowledgeTextChunker {
    static final int MAX_CHARS = 800;
    static final int OVERLAP_CHARS = 100;

    public List<String> split(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        var normalized = content.trim();
        var chunks = new ArrayList<String>();
        var start = 0;
        while (start < normalized.length()) {
            var end = Math.min(start + MAX_CHARS, normalized.length());
            if (end < normalized.length()) {
                var lineBreak = normalized.lastIndexOf('\n', end);
                if (lineBreak > start + MAX_CHARS / 2) {
                    end = lineBreak;
                }
            }
            chunks.add(normalized.substring(start, end).trim());
            if (end == normalized.length()) {
                break;
            }
            start = Math.max(end - OVERLAP_CHARS, start + 1);
        }
        return List.copyOf(chunks);
    }
}
