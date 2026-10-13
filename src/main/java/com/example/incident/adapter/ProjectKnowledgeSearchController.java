package com.example.incident.adapter;

import com.example.incident.domain.KnowledgeSearchResult;
import com.example.incident.service.KnowledgeDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/knowledge-search")
@Tag(name = "项目知识资料", description = "预览指定项目的知识库检索结果")
public class ProjectKnowledgeSearchController {
    private final KnowledgeDocumentService documentService;

    public ProjectKnowledgeSearchController(KnowledgeDocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    @Operation(summary = "预览项目知识检索", description = "只检索指定项目的知识片段，不创建故障事件，也不会触发完整分析流程。")
    public List<KnowledgeSearchResult> search(
            @PathVariable UUID projectId,
            @Valid @RequestBody KnowledgeSearchRequest request) {
        return documentService.searchSimilar(projectId, request.query(), request.limit());
    }

    public record KnowledgeSearchRequest(
            @NotBlank String query,
            @Min(1) @Max(10) Integer limit) {
        public KnowledgeSearchRequest {
            if (limit == null) {
                limit = 3;
            }
        }
    }
}
