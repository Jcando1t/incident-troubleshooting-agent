package com.example.incident.adapter;

import com.example.incident.domain.KnowledgeDocument;
import com.example.incident.service.KnowledgeDocumentNotFoundException;
import com.example.incident.service.KnowledgeDocumentService;
import com.example.incident.service.ProjectNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/knowledge-documents")
@Tag(name = "项目知识资料", description = "为每个项目分别管理故障排查知识资料")
public class ProjectKnowledgeDocumentController {
    private final KnowledgeDocumentService service;

    public ProjectKnowledgeDocumentController(KnowledgeDocumentService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "添加项目知识资料", description = "当前接收纯文本资料；资料只属于 URL 中指定的项目。")
    public KnowledgeDocument create(
            @PathVariable UUID projectId,
            @Valid @RequestBody KnowledgeDocumentRequest request) {
        return service.create(projectId, request.title(), request.content());
    }

    @GetMapping
    @Operation(summary = "列出项目知识资料")
    public List<KnowledgeDocument> findByProjectId(@PathVariable UUID projectId) {
        return service.findByProjectId(projectId);
    }

    @GetMapping("/{documentId}")
    @Operation(summary = "查看一份项目知识资料")
    public KnowledgeDocument get(@PathVariable UUID projectId, @PathVariable UUID documentId) {
        return service.get(projectId, documentId);
    }

    @PutMapping("/{documentId}")
    @Operation(summary = "替换项目知识资料内容")
    public KnowledgeDocument update(
            @PathVariable UUID projectId,
            @PathVariable UUID documentId,
            @Valid @RequestBody KnowledgeDocumentRequest request) {
        return service.update(projectId, documentId, request.title(), request.content());
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "删除项目知识资料")
    public void delete(@PathVariable UUID projectId, @PathVariable UUID documentId) {
        service.delete(projectId, documentId);
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleProjectNotFound(ProjectNotFoundException exception) {
        return new ErrorResponse("PROJECT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(KnowledgeDocumentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleDocumentNotFound(KnowledgeDocumentNotFoundException exception) {
        return new ErrorResponse("KNOWLEDGE_DOCUMENT_NOT_FOUND", exception.getMessage());
    }

    public record KnowledgeDocumentRequest(
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 200_000) String content) {}

    public record ErrorResponse(String code, String message) {}
}
