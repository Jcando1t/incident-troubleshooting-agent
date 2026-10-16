package com.example.incident.service;

import com.example.incident.domain.Incident;
import com.example.incident.domain.Evidence;
import com.example.incident.domain.EvidenceBundle;
import com.example.incident.domain.KnowledgeSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class ProjectKnowledgeRetriever {
    private static final int DEFAULT_LIMIT = 3;
    private static final Logger logger = LoggerFactory.getLogger(ProjectKnowledgeRetriever.class);
    private final KnowledgeDocumentService knowledgeDocumentService;

    public ProjectKnowledgeRetriever(KnowledgeDocumentService knowledgeDocumentService) {
        this.knowledgeDocumentService = knowledgeDocumentService;
    }

    public List<KnowledgeSearchResult> retrieve(Incident incident) {
        var query = incident.title() + "\n" + incident.service() + "\n" + incident.description();
        try {
            return knowledgeDocumentService.searchSimilar(incident.projectId(), query, DEFAULT_LIMIT)
                    .stream()
                    .filter(result -> result.score() >= 0.35)
                    .toList();
        } catch (RuntimeException exception) {
            throw new KnowledgeSearchException(
                    "Project knowledge search is unavailable for incident " + incident.id(), exception);
        }
    }

    public EvidenceBundle enrich(Incident incident, EvidenceBundle existingEvidence) {
        var bundle = existingEvidence == null ? new EvidenceBundle(List.of(), false) : existingEvidence;
        List<KnowledgeSearchResult> results;
        try {
            results = retrieve(incident);
        } catch (KnowledgeSearchException exception) {
            logger.warn("Project knowledge retrieval unavailable for incident {}: {}",
                    incident.id(), exception.getMessage());
            return bundle;
        }
        if (results.isEmpty()) {
            return bundle;
        }

        var enrichedItems = new ArrayList<>(bundle.items() == null ? List.of() : bundle.items());
        for (var result : results) {
            enrichedItems.add(new Evidence(
                    "project-knowledge",
                    "相似度=" + String.format(java.util.Locale.ROOT, "%.3f", result.score())
                            + "；资料内容=" + result.content(),
                    "knowledge://projects/" + result.projectId()
                            + "/documents/" + result.documentId()
                            + "/chunks/" + result.chunkIndex(),
                    Instant.now()
            ));
        }
        return new EvidenceBundle(
                List.copyOf(enrichedItems),
                bundle.complete(),
                bundle.investigationTrace());
    }
}
