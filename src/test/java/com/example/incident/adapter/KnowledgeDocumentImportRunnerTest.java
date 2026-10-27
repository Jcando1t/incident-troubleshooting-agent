package com.example.incident.adapter;

import com.example.incident.domain.KnowledgeDocument;
import com.example.incident.domain.Project;
import com.example.incident.service.KnowledgeDocumentService;
import com.example.incident.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeDocumentImportRunnerTest {

    @Test
    void importsMarkdownFilesThenCreatesProjectAndIndexes(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("01-概述.md"), "项目概述内容");
        Files.writeString(dir.resolve("02-故障.md"), "故障内容");
        Files.writeString(dir.resolve("notes.txt"), "非 md 文件应被忽略");

        var projectService = mock(ProjectService.class);
        var knowledgeDocumentService = mock(KnowledgeDocumentService.class);
        var projectId = UUID.randomUUID();
        var now = Instant.now();

        when(projectService.findAll()).thenReturn(List.of());
        when(projectService.create("求职 Agent")).thenReturn(new Project(projectId, "求职 Agent", now));
        when(knowledgeDocumentService.findByProjectId(projectId)).thenReturn(List.of());
        when(knowledgeDocumentService.create(eq(projectId), any(), any())).thenAnswer(invocation ->
                new KnowledgeDocument(UUID.randomUUID(), projectId,
                        invocation.getArgument(1), invocation.getArgument(2), now, now));
        when(knowledgeDocumentService.reindex(eq(projectId), any())).thenAnswer(invocation ->
                new KnowledgeDocumentService.EmbeddingResult(invocation.getArgument(1), "bge-m3", 3));

        new KnowledgeDocumentImportRunner(projectService, knowledgeDocumentService,
                "求职 Agent", dir.toString()).run();

        verify(projectService).create("求职 Agent");
        verify(knowledgeDocumentService).create(projectId, "01-概述", "项目概述内容");
        verify(knowledgeDocumentService).create(projectId, "02-故障", "故障内容");
        verify(knowledgeDocumentService, times(2)).reindex(eq(projectId), any());
    }

    @Test
    void skipsFilesWhoseTitleAlreadyExists(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("01-概述.md"), "新内容");

        var projectService = mock(ProjectService.class);
        var knowledgeDocumentService = mock(KnowledgeDocumentService.class);
        var projectId = UUID.randomUUID();
        var now = Instant.now();

        when(projectService.findAll()).thenReturn(List.of(new Project(projectId, "求职 Agent", now)));
        when(knowledgeDocumentService.findByProjectId(projectId)).thenReturn(List.of(
                new KnowledgeDocument(UUID.randomUUID(), projectId, "01-概述", "旧内容", now, now)));

        new KnowledgeDocumentImportRunner(projectService, knowledgeDocumentService,
                "求职 Agent", dir.toString()).run();

        verify(projectService, never()).create(any());
        verify(knowledgeDocumentService, never()).create(any(), any(), any());
        verify(knowledgeDocumentService, never()).reindex(any(), any());
    }

    @Test
    void doesNothingWhenDirectoryIsMissing(@TempDir Path dir) {
        var projectService = mock(ProjectService.class);
        var knowledgeDocumentService = mock(KnowledgeDocumentService.class);
        var runner = new KnowledgeDocumentImportRunner(projectService, knowledgeDocumentService,
                "求职 Agent", dir.resolve("not-exist").toString());

        assertDoesNotThrow(() -> runner.run());
        verify(projectService, never()).findAll();
    }
}
