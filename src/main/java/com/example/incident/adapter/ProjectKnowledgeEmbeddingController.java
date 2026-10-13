package com.example.incident.adapter;

import com.example.incident.service.KnowledgeDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/knowledge-documents/{documentId}/embedding")
@Tag(name = "项目知识资料", description = "为项目资料生成语义向量")
public class ProjectKnowledgeEmbeddingController {
    private final KnowledgeDocumentService documentService;

    public ProjectKnowledgeEmbeddingController(
            KnowledgeDocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/index")
    @Operation(summary = "为项目资料生成并保存向量", description = "按文本分段，调用 Ollama 生成向量并保存到 MySQL；重复调用会替换旧分段。")
    public EmbeddingIndexResponse index(
            @PathVariable UUID projectId,
            @PathVariable UUID documentId) {
        var result = documentService.reindex(projectId, documentId);
        return new EmbeddingIndexResponse(
                result.documentId(),
                result.model(),
                result.chunks()
        );
    }

    public record EmbeddingIndexResponse(UUID documentId, String model, int chunks) {}
}
